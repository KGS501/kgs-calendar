package com.kgs.calendar.ui.editor

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
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
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** The values of a draft at [revision]. */
class DraftRevision(val revision: Long, val values: Map<String, Any?>)

/**
 * What the saved-state Bundle keeps of a draft: its [revision], and its [values] unless the draft
 * was too large for the Bundle and only its file holds them.
 */
internal data class SavedDraft(val revision: Long, val values: Map<String, Any?>?)

/**
 * The typed values of the open editor, keyed by a draft ID per editor session. Every change bumps
 * the draft's revision, which only grows. A draft ends when its editor is saved or discarded,
 * never when the composition goes away during a rotation.
 *
 * What survives:
 * - Rotation always keeps everything: the live draft stays in the ViewModel that owns this store.
 * - Small drafts survive process death exactly like other saved state: the saved-state Bundle
 *   holds their values up to [BUNDLE_DRAFT_MAX_CHARS] (see [saved]).
 * - Large drafts keep only their ID and revision in the Bundle and rely on their file. With
 *   [files], every change is written on IO shortly afterwards (at most once per
 *   [writeDelayMillis]), and [flush] on `ON_STOP` schedules that write at once. They survive
 *   process death unless the process is killed before that write completes, a window of
 *   milliseconds.
 *
 * On restore the live draft wins, else whichever of the Bundle's and the file's values has the
 * higher revision, else none, so that the editor falls back to the stored item's values.
 *
 * All methods run on the main thread. Draft files are only serialized, written and read on IO.
 */
class EditorDraftStore(
    private val files: EditorDraftFiles? = null,
    private val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val writeDelayMillis: Long = DRAFT_WRITE_DELAY_MILLIS,
) {
    private class Draft(val values: HashMap<String, Any?>, var revision: Long)

    private val drafts = HashMap<String, Draft>()
    private val discarded = HashSet<String>()

    /** Drafts from the saved-state Bundle that no editor has asked for yet. */
    private val bundled = HashMap<String, SavedDraft>()

    /** Drafts read from their files on IO before the restored editor asks for them. */
    private val preloaded = ConcurrentHashMap<String, DraftRevision>()
    private val restoredDraftsRead = CompletableDeferred<Unit>().apply { if (files == null) complete(Unit) }
    private val lock = Any()

    /** Serializes every file operation: writes, deletes and the stale draft clean-up. */
    private val fileLock = ReentrantLock()
    private var staleDraftsChecked = false

    /** Draft revisions to write, by draft ID; null deletes the file. Guarded by [lock]. */
    private val pendingWrites = LinkedHashMap<String, DraftRevision?>()
    private var writeScheduled = false

    /** Starts an empty draft for a new editor session. */
    fun newDraft(): String = UUID.randomUUID().toString().also { drafts[it] = Draft(HashMap(), 0) }

    /**
     * The values of [draftId]: the live draft, else the newer of the ones restored from the Bundle
     * and from the file, else none, so that the editor falls back to the stored item's values.
     */
    fun values(draftId: String): Map<String, Any?> = liveDraft(draftId)?.values ?: emptyMap()

    fun put(draftId: String, name: String, value: Any?) {
        val draft = liveDraft(draftId) ?: return
        if (draft.values.containsKey(name) && draft.values[name] == value) return
        draft.values[name] = value
        draft.revision++
        enqueueWrite(draftId, DraftRevision(draft.revision, HashMap(draft.values)))
    }

    /** Ends [draftId] after its editor was saved or closed, in memory and on disk. */
    fun discard(draftId: String) {
        drafts.remove(draftId)
        bundled.remove(draftId)
        preloaded.remove(draftId)
        discarded += draftId
        enqueueWrite(draftId, null)
    }

    /** What the saved-state Bundle keeps of [draftId]: its revision, and its values if they are small. */
    internal fun saved(draftId: String): SavedDraft {
        val draft = drafts[draftId] ?: return bundled[draftId] ?: SavedDraft(0, null)
        val small = draft.values.estimatedBundleChars() <= BUNDLE_DRAFT_MAX_CHARS
        return SavedDraft(draft.revision, if (small) HashMap(draft.values) else null)
    }

    /** Offers the Bundle's [saved] state of [draftId] after the activity was recreated; a live draft ignores it. */
    internal fun restore(draftId: String, saved: SavedDraft) {
        if (draftId in drafts || draftId in discarded) return
        bundled[draftId] = saved
    }

    /**
     * Schedules the write of the latest revision of every draft on IO at once, e.g. when the app
     * goes to the background and may be killed, and returns without waiting for it.
     */
    fun flush() {
        if (files == null) return
        if (synchronized(lock) { pendingWrites.isEmpty() }) return
        ioScope.launch { writePending() }
    }

    /**
     * Called once the shell has restored its saved state, or found none, with the draft IDs that
     * state refers to. The referenced drafts are read on IO first; [awaitRestoredDrafts] waits for
     * them, so that the editor restored after process death finds them in memory. Then, once per
     * store, the files of drafts that were never saved or discarded (e.g. after a crash) are
     * deleted when their last write is older than [STALE_DRAFT_MAX_AGE_MILLIS]. Live, referenced
     * and pending drafts are kept.
     */
    fun onShellRestored(referencedDraftIds: Set<String>, nowMillis: Long = System.currentTimeMillis()) {
        val files = files ?: return
        if (staleDraftsChecked) return
        staleDraftsChecked = true
        val keep = drafts.keys + referencedDraftIds
        val toRead = referencedDraftIds.filter { it !in drafts && it !in discarded }
        ioScope.launch {
            try {
                toRead.forEach { draftId -> files.read(draftId)?.let { preloaded[draftId] = it } }
            } finally {
                restoredDraftsRead.complete(Unit)
            }
            fileLock.withLock {
                val pending = synchronized(lock) { pendingWrites.keys.toSet() }
                files.deleteOlderThan(nowMillis - STALE_DRAFT_MAX_AGE_MILLIS, keep = keep + pending)
            }
        }
    }

    /** Waits until the drafts referenced in [onShellRestored] have been read from their files. */
    suspend fun awaitRestoredDrafts() {
        restoredDraftsRead.await()
    }

    /**
     * Whether [values] of [draftId] would find its restored values: the draft is live or ended, or
     * the files of the restored drafts have been read. Until then an editor must not ask for it.
     */
    fun canRestore(draftId: String): Boolean =
        draftId in drafts || draftId in discarded || restoredDraftsRead.isCompleted

    private fun liveDraft(draftId: String): Draft? {
        if (draftId in discarded) return null
        drafts[draftId]?.let { return it }
        val fromBundle = bundled.remove(draftId)
        val fromFile = preloaded.remove(draftId)
        val bundleRevision = fromBundle?.revision ?: 0
        val fileRevision = fromFile?.revision ?: 0
        val values = if (fromBundle?.values != null && bundleRevision >= fileRevision) {
            fromBundle.values
        } else {
            fromFile?.values.orEmpty()
        }
        // Later changes are numbered above every revision seen, so that they win the next restore.
        return Draft(HashMap(values), maxOf(bundleRevision, fileRevision)).also { drafts[draftId] = it }
    }

    private fun enqueueWrite(draftId: String, draft: DraftRevision?) {
        if (files == null) return
        synchronized(lock) {
            pendingWrites[draftId] = draft
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
        fileLock.withLock {
            val batch = synchronized(lock) {
                // Changes made from here on need a new write.
                writeScheduled = false
                LinkedHashMap(pendingWrites).also { pendingWrites.clear() }
            }
            batch.forEach { (draftId, draft) ->
                val persisted = if (draft == null) files.delete(draftId) else files.write(draftId, draft)
                // A failed revision stays pending for the next write or flush unless a newer one replaced it.
                if (!persisted) {
                    synchronized(lock) { if (!pendingWrites.containsKey(draftId)) pendingWrites[draftId] = draft }
                }
            }
        }
    }

    companion object {
        const val DRAFT_WRITE_DELAY_MILLIS = 400L
        const val STALE_DRAFT_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000

        /**
         * The saved state of the whole activity travels in one binder transaction of about 1 MB,
         * shared with every other saved value; 64 K characters (128 KB as UTF-16) leave room.
         */
        const val BUNDLE_DRAFT_MAX_CHARS = 64 * 1024
    }
}

/** A fixed allowance per value, key and list item on top of the string lengths. */
private const val BUNDLE_FIELD_OVERHEAD_CHARS = 16

/** A cheap estimate of the characters [this] draft value takes in the saved-state Bundle. */
private fun Any?.estimatedBundleChars(): Long = BUNDLE_FIELD_OVERHEAD_CHARS + when (this) {
    is String -> length.toLong()
    is Map<*, *> -> entries.sumOf { (key, value) -> ((key as? String)?.length ?: 0) + value.estimatedBundleChars() }
    is Collection<*> -> sumOf { it.estimatedBundleChars() }
    else -> 0L
}

/**
 * One small JSON file per editor draft in a private [directory], holding the draft's revision and
 * values. Values are strings, numbers, booleans, null, lists and string-keyed maps of those; a
 * stored null stays distinct from a missing value. Files written before revisions existed read as
 * revision 0. A file is replaced by writing a temporary file and renaming it over the old one, so
 * a failed write leaves the previous revision intact.
 */
class EditorDraftFiles(
    private val directory: File,
    private val writeFile: (File, String) -> Unit = ::writeSynced,
    private val replaceFile: (source: File, target: File) -> Unit = ::replaceAtomically,
) {
    fun read(draftId: String): DraftRevision? {
        val file = fileFor(draftId) ?: return null
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText())
            @Suppress("UNCHECKED_CAST")
            DraftRevision(json.optLong(REVISION, 0L), json.getJSONObject(VALUES).fromJson() as Map<String, Any?>)
        }.getOrNull()
    }

    /** Whether [draft] is now the stored revision of [draftId]. */
    fun write(draftId: String, draft: DraftRevision): Boolean {
        val file = fileFor(draftId) ?: return false
        val temporary = File(directory, "${file.name}$TEMPORARY_SUFFIX")
        return try {
            directory.mkdirs()
            writeFile(temporary, JSONObject().put(REVISION, draft.revision).put(VALUES, draft.values.toJson()).toString())
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
        const val REVISION = "revision"
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
