package com.kgs.calendar.ui.editor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * The typed values of the open editor, keyed by a draft ID per editor session. Only that ID goes
 * into the saved-state Bundle, which a long description or notes text could overflow.
 *
 * The live draft survives activity recreation with the ViewModel that owns this store. With
 * [files], every change is also written to a private file shortly afterwards (on IO, at most once
 * per [writeDelayMillis]) so that the draft outlives process death. A draft ends when its editor
 * is saved or discarded, never when the composition goes away during a rotation.
 *
 * All methods except the file writes run on the main thread.
 */
class EditorDraftStore(
    private val files: EditorDraftFiles? = null,
    private val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val writeDelayMillis: Long = DRAFT_WRITE_DELAY_MILLIS,
) {
    private val drafts = HashMap<String, HashMap<String, Any?>>()
    private val discarded = HashSet<String>()
    private val lock = Any()
    private val writeLock = Any()

    /** Draft contents to write, by draft ID; null deletes the file. Guarded by [lock]. */
    private val pendingWrites = LinkedHashMap<String, Map<String, Any?>?>()
    private var writeScheduled = false

    /** Starts an empty draft for a new editor session. */
    fun newDraft(): String = UUID.randomUUID().toString().also { drafts[it] = HashMap() }

    /**
     * The values of [draftId]: the live draft, else the one persisted before the process died,
     * else none, so that the editor falls back to the stored item's values.
     */
    fun values(draftId: String): Map<String, Any?> = liveDraft(draftId) ?: emptyMap()

    fun put(draftId: String, name: String, value: Any?) {
        val draft = liveDraft(draftId) ?: return
        if (draft.containsKey(name) && draft[name] == value) return
        draft[name] = value
        enqueueWrite(draftId, HashMap(draft))
    }

    /** Ends [draftId] after its editor was saved or closed, in memory and on disk. */
    fun discard(draftId: String) {
        drafts.remove(draftId)
        discarded += draftId
        enqueueWrite(draftId, null)
    }

    /** Writes pending changes now, e.g. when the app goes to the background. */
    fun flush() {
        if (files == null) return
        ioScope.launch { writePending() }
    }

    /** Deletes the files of drafts that were never saved or discarded, e.g. after a crash. */
    fun deleteStaleDrafts(maxAgeMillis: Long = STALE_DRAFT_MAX_AGE_MILLIS, nowMillis: Long = System.currentTimeMillis()) {
        val files = files ?: return
        ioScope.launch { files.deleteOlderThan(nowMillis - maxAgeMillis) }
    }

    private fun liveDraft(draftId: String): HashMap<String, Any?>? {
        if (draftId in discarded) return null
        return drafts.getOrPut(draftId) { HashMap(files?.read(draftId).orEmpty()) }
    }

    private fun enqueueWrite(draftId: String, values: Map<String, Any?>?) {
        if (files == null) return
        synchronized(lock) {
            pendingWrites[draftId] = values
            if (writeScheduled) return
            writeScheduled = true
        }
        ioScope.launch {
            delay(writeDelayMillis)
            writePending()
        }
    }

    private fun writePending() {
        val files = files ?: return
        synchronized(writeLock) {
            val batch = synchronized(lock) {
                // Changes made from here on need a new write.
                writeScheduled = false
                LinkedHashMap(pendingWrites).also { pendingWrites.clear() }
            }
            batch.forEach { (draftId, values) ->
                if (values == null) files.delete(draftId) else files.write(draftId, values)
            }
        }
    }

    companion object {
        const val DRAFT_WRITE_DELAY_MILLIS = 400L
        const val STALE_DRAFT_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}

/**
 * One small JSON file per editor draft in a private [directory]. Values are strings, numbers,
 * booleans, null, lists and string-keyed maps of those; a stored null stays distinct from a
 * missing value.
 */
class EditorDraftFiles(private val directory: File) {
    fun read(draftId: String): Map<String, Any?>? {
        val file = fileFor(draftId) ?: return null
        if (!file.isFile) return null
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            JSONObject(file.readText()).getJSONObject(VALUES).fromJson() as Map<String, Any?>
        }.getOrNull()
    }

    fun write(draftId: String, values: Map<String, Any?>) {
        val file = fileFor(draftId) ?: return
        runCatching {
            directory.mkdirs()
            val temporary = File(directory, "${file.name}.tmp")
            temporary.writeText(JSONObject().put(VALUES, values.toJson()).toString())
            if (!temporary.renameTo(file)) {
                file.delete()
                temporary.renameTo(file)
            }
        }
    }

    fun delete(draftId: String) {
        fileFor(draftId)?.delete()
    }

    fun deleteOlderThan(cutoffMillis: Long) {
        directory.listFiles()
            ?.filter { it.isFile && it.lastModified() < cutoffMillis }
            ?.forEach { it.delete() }
    }

    private fun fileFor(draftId: String): File? =
        draftId.takeIf { id -> id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '-' } }
            ?.let { File(directory, "$it.json") }

    private companion object {
        const val VALUES = "values"
    }
}

private fun Any?.toJson(): Any = when (this) {
    null -> JSONObject.NULL
    is String, is Boolean, is Int, is Long, is Double -> this
    is Map<*, *> -> JSONObject().also { json -> forEach { (key, value) -> json.put(key as String, value.toJson()) } }
    is Collection<*> -> JSONArray().also { json -> forEach { json.put(it.toJson()) } }
    else -> error("Unsupported draft value: ${this::class.java.simpleName}")
}

private fun Any?.fromJson(): Any? = when (this) {
    null, JSONObject.NULL -> null
    is JSONObject -> keys().asSequence().associateWith { opt(it).fromJson() }
    is JSONArray -> (0 until length()).map { opt(it).fromJson() }
    else -> this
}
