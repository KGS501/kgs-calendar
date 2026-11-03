package com.kgs.calendar.ui.editor

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.platform.KgsLock
import com.kgs.calendar.platform.withLock
import com.kgs.calendar.platform.LockedMap
import com.kgs.calendar.platform.randomUuidString
import kotlinx.coroutines.IO
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kgs.calendar.json.JSONArray
import com.kgs.calendar.json.JSONObject

/** The values of a draft at [revision]. */
class DraftRevision(val revision: Long, val values: Map<String, Any?>)

/**
 * What the saved-state Bundle keeps of a draft: its [revision], and its [values] unless the draft
 * was too large for the Bundle and only its file holds them.
 */
data class SavedDraft(val revision: Long, val values: Map<String, Any?>?)

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
 *   process death only on a best-effort basis: if the process is killed before that write has
 *   succeeded, the newest changes are lost. The window is usually milliseconds, but IO
 *   contention or a failing disk can make it longer.
 *
 * On restore the live draft wins, else whichever of the Bundle's and the file's values has the
 * higher revision, else none, so that the editor falls back to the stored item's values.
 *
 * All methods run on the main thread. Draft files are only serialized, written and read on IO.
 */
class EditorDraftStore(
    private val files: DraftFiles? = null,
    private val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val writeDelayMillis: Long = DRAFT_WRITE_DELAY_MILLIS,
) {
    private class Draft(val values: HashMap<String, Any?>, var revision: Long)

    private val drafts = HashMap<String, Draft>()
    private val discarded = HashSet<String>()

    /** Drafts from the saved-state Bundle that no editor has asked for yet. */
    private val bundled = HashMap<String, SavedDraft>()

    /** Drafts read from their files on IO before the restored editor asks for them. */
    private val preloaded = LockedMap<String, DraftRevision>()
    private val restoredDraftsRead = CompletableDeferred<Unit>().apply { if (files == null) complete(Unit) }
    private val lock = KgsLock()

    /** Serializes every file operation: writes, deletes and the stale draft clean-up. */
    private val fileLock = KgsLock()
    private var staleDraftsChecked = false

    /** Draft revisions to write, by draft ID; null deletes the file. Guarded by [lock]. */
    private val pendingWrites = LinkedHashMap<String, DraftRevision?>()
    private var writeScheduled = false

    /** Starts an empty draft for a new editor session. */
    fun newDraft(): String = randomUuidString().also { drafts[it] = Draft(HashMap(), 0) }

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
    fun saved(draftId: String): SavedDraft {
        val draft = drafts[draftId] ?: return bundled[draftId] ?: SavedDraft(0, null)
        val small = draft.values.estimatedBundleChars() <= BUNDLE_DRAFT_MAX_CHARS
        return SavedDraft(draft.revision, if (small) HashMap(draft.values) else null)
    }

    /** Offers the Bundle's [saved] state of [draftId] after the activity was recreated; a live draft ignores it. */
    fun restore(draftId: String, saved: SavedDraft) {
        if (draftId in drafts || draftId in discarded) return
        bundled[draftId] = saved
    }

    /**
     * Schedules the write of the latest revision of every draft on IO at once, e.g. when the app
     * goes to the background and may be killed, and returns without waiting for it.
     */
    fun flush() {
        if (files == null) return
        if (lock.withLock { pendingWrites.isEmpty() }) return
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
    fun onShellRestored(referencedDraftIds: Set<String>, nowMillis: Long = nowMillis()) {
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
                val pending = lock.withLock { pendingWrites.keys.toSet() }
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
        lock.withLock {
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
            val batch = lock.withLock {
                // Changes made from here on need a new write.
                writeScheduled = false
                LinkedHashMap(pendingWrites).also { pendingWrites.clear() }
            }
            batch.forEach { (draftId, draft) ->
                val persisted = if (draft == null) files.delete(draftId) else files.write(draftId, draft)
                // A failed revision stays pending for the next write or flush unless a newer one replaced it.
                if (!persisted) {
                    lock.withLock { if (!pendingWrites.containsKey(draftId)) pendingWrites[draftId] = draft }
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
 * Where drafts are kept between process starts: one small JSON document per draft holding its
 * revision and values (see [encodeDraft]). Android keeps files in no-backup storage; iOS in
 * Application Support.
 */
interface DraftFiles {
    fun read(draftId: String): DraftRevision?

    /** Whether [draft] is now the stored revision of [draftId]. */
    fun write(draftId: String, draft: DraftRevision): Boolean

    /** Whether no file of [draftId] is left. */
    fun delete(draftId: String): Boolean

    /** Deletes the files, including leftover temporary ones, of drafts not in [keep] last written before [cutoffMillis]. */
    fun deleteOlderThan(cutoffMillis: Long, keep: Set<String>)
}

/** Draft IDs that are safe file names. */
fun isValidDraftFileId(draftId: String): Boolean = draftId.isNotEmpty() && draftId.all { it.isLetterOrDigit() || it == '-' }

/**
 * A draft's file contents. Values are strings, numbers, booleans, null, lists and string-keyed
 * maps of those; a stored null stays distinct from a missing value.
 */
fun encodeDraft(draft: DraftRevision): String =
    JSONObject().put(DRAFT_REVISION, draft.revision).put(DRAFT_VALUES, draft.values.toJson()).toString()

/** Files written before revisions existed read as revision 0; unreadable text as null. */
fun decodeDraft(text: String): DraftRevision? = runCatching {
    val json = JSONObject(text)
    @Suppress("UNCHECKED_CAST")
    DraftRevision(json.optLong(DRAFT_REVISION, 0L), json.getJSONObject(DRAFT_VALUES).fromJson() as Map<String, Any?>)
}.getOrNull()

private const val DRAFT_REVISION = "revision"
private const val DRAFT_VALUES = "values"

private fun Any?.toJson(): Any = when (this) {
    null -> JSONObject.NULL
    is String, is Boolean, is Int, is Long, is Double -> this
    is Map<*, *> -> JSONObject().also { json -> forEach { (key, value) -> json.put(key as String, value.toJson()) } }
    is Collection<*> -> JSONArray().also { json -> forEach { json.put(it.toJson()) } }
    else -> error("Unsupported draft value: ${this::class.simpleName}")
}

private fun Any?.fromJson(): Any? = when (this) {
    null, JSONObject.NULL -> null
    is JSONObject -> keys().asSequence().associateWith { opt(it).fromJson() }
    is JSONArray -> (0 until length()).map { opt(it).fromJson() }
    else -> this
}
