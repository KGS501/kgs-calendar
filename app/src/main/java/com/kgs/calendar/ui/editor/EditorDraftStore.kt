package com.kgs.calendar.ui.editor

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The typed values of the open editor, keyed by a draft ID per editor session. Only that ID goes
 * into the saved-state Bundle, which a long description or notes text could overflow.
 *
 * The live draft survives activity recreation with the ViewModel that owns this store. With
 * [files], every change is also written to a private file shortly afterwards (on IO, at most once
 * per [writeDelayMillis]) so that the draft outlives process death; [flush] writes at once. A
 * draft ends when its editor is saved or discarded, never when the composition goes away during a
 * rotation.
 *
 * All methods run on the main thread; the debounced file writes run on IO.
 */
class EditorDraftStore(
    private val files: EditorDraftFiles? = null,
    private val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val writeDelayMillis: Long = DRAFT_WRITE_DELAY_MILLIS,
) {
    private val drafts = HashMap<String, HashMap<String, Any?>>()
    private val discarded = HashSet<String>()

    /** Drafts read from their files on IO before the restored editor asks for them. */
    private val preloaded = ConcurrentHashMap<String, Map<String, Any?>>()
    private val lock = Any()

    /** Serializes every file operation: writes, deletes and the stale draft clean-up. */
    private val fileLock = ReentrantLock()
    private var staleDraftsChecked = false

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
        preloaded.remove(draftId)
        discarded += draftId
        enqueueWrite(draftId, null)
    }

    /**
     * Writes the latest revision of every draft before returning, e.g. when the app goes to the
     * background and may be killed. Like `SharedPreferences` in `onStop`, this first waits for a
     * write already running; draft files are small, but after [FLUSH_WAIT_MILLIS] the flush falls
     * back to writing on IO instead of blocking longer.
     */
    fun flush() {
        if (files == null) return
        if (fileLock.tryLock(FLUSH_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
            try {
                writePendingLocked()
            } finally {
                fileLock.unlock()
            }
        } else {
            ioScope.launch { writePending() }
        }
    }

    /**
     * Called once the shell has restored its saved state, or found none, with the draft IDs that
     * state refers to. Only then, and once per store, the files of drafts that were never saved or
     * discarded (e.g. after a crash) are deleted when their last write is older than
     * [STALE_DRAFT_MAX_AGE_MILLIS]. Live, referenced and pending drafts are kept. The referenced
     * drafts are read first, so that the editor restored after process death finds them in memory.
     */
    fun onShellRestored(referencedDraftIds: Set<String>, nowMillis: Long = System.currentTimeMillis()) {
        val files = files ?: return
        if (staleDraftsChecked) return
        staleDraftsChecked = true
        val keep = drafts.keys + referencedDraftIds
        val toRead = referencedDraftIds.filter { it !in drafts && it !in discarded }
        ioScope.launch {
            toRead.forEach { draftId -> files.read(draftId)?.let { preloaded[draftId] = it } }
            fileLock.withLock {
                val pending = synchronized(lock) { pendingWrites.keys.toSet() }
                files.deleteOlderThan(nowMillis - STALE_DRAFT_MAX_AGE_MILLIS, keep = keep + pending)
            }
        }
    }

    private fun liveDraft(draftId: String): HashMap<String, Any?>? {
        if (draftId in discarded) return null
        return drafts.getOrPut(draftId) { HashMap(preloaded.remove(draftId) ?: files?.read(draftId).orEmpty()) }
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
        fileLock.withLock { writePendingLocked() }
    }

    private fun writePendingLocked() {
        val files = files ?: return
        val batch = synchronized(lock) {
            // Changes made from here on need a new write.
            writeScheduled = false
            LinkedHashMap(pendingWrites).also { pendingWrites.clear() }
        }
        batch.forEach { (draftId, values) ->
            val persisted = if (values == null) files.delete(draftId) else files.write(draftId, values)
            // A failed revision stays pending for the next write or flush unless a newer one replaced it.
            if (!persisted) {
                synchronized(lock) { if (!pendingWrites.containsKey(draftId)) pendingWrites[draftId] = values }
            }
        }
    }

    companion object {
        const val DRAFT_WRITE_DELAY_MILLIS = 400L
        const val FLUSH_WAIT_MILLIS = 2_000L
        const val STALE_DRAFT_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}

/**
 * One small JSON file per editor draft in a private [directory]. Values are strings, numbers,
 * booleans, null, lists and string-keyed maps of those; a stored null stays distinct from a
 * missing value. A file is replaced by writing a temporary file and renaming it over the old one,
 * so a failed write leaves the previous revision intact.
 */
class EditorDraftFiles(
    private val directory: File,
    private val writeFile: (File, String) -> Unit = ::writeSynced,
    private val replaceFile: (source: File, target: File) -> Unit = ::replaceAtomically,
) {
    fun read(draftId: String): Map<String, Any?>? {
        val file = fileFor(draftId) ?: return null
        if (!file.isFile) return null
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            JSONObject(file.readText()).getJSONObject(VALUES).fromJson() as Map<String, Any?>
        }.getOrNull()
    }

    /** Whether [values] are now the stored revision of [draftId]. */
    fun write(draftId: String, values: Map<String, Any?>): Boolean {
        val file = fileFor(draftId) ?: return false
        val temporary = File(directory, "${file.name}$TEMPORARY_SUFFIX")
        return try {
            directory.mkdirs()
            writeFile(temporary, JSONObject().put(VALUES, values.toJson()).toString())
            replaceFile(temporary, file)
            true
        } catch (error: Exception) {
            Log.w(TAG, "Failed to write editor draft $draftId", error)
            temporary.delete()
            false
        }
    }

    /** Whether no file of [draftId] is left. */
    fun delete(draftId: String): Boolean {
        val file = fileFor(draftId) ?: return true
        if (file.delete() || !file.exists()) return true
        Log.w(TAG, "Failed to delete editor draft $draftId")
        return false
    }

    /** Deletes the files, including leftover temporary ones, of drafts not in [keep] last written before [cutoffMillis]. */
    fun deleteOlderThan(cutoffMillis: Long, keep: Set<String>) {
        directory.listFiles()
            ?.filter { it.isFile && it.name.substringBefore('.') !in keep && it.lastModified() < cutoffMillis }
            ?.forEach { it.delete() }
    }

    private fun fileFor(draftId: String): File? =
        draftId.takeIf { id -> id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '-' } }
            ?.let { File(directory, "$it.json") }

    private companion object {
        const val VALUES = "values"
        const val TEMPORARY_SUFFIX = ".tmp"
        const val TAG = "KgsEditorDraft"
    }
}

private fun writeSynced(file: File, text: String) {
    FileOutputStream(file).use { output ->
        output.write(text.toByteArray())
        output.fd.sync()
    }
}

/** Replaces [target] in one step; on failure it keeps its previous contents. */
private fun replaceAtomically(source: File, target: File) {
    if (!source.renameTo(target)) throw IOException("Failed to rename ${source.name} to ${target.name}")
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
