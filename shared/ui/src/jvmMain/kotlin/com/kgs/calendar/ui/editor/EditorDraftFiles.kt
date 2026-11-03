package com.kgs.calendar.ui.editor

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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
) : DraftFiles {
    override fun read(draftId: String): DraftRevision? {
        val file = fileFor(draftId) ?: return null
        if (!file.isFile) return null
        return runCatching { decodeDraft(file.readText()) }.getOrNull()
    }

    /** Whether [draft] is now the stored revision of [draftId]. */
    override fun write(draftId: String, draft: DraftRevision): Boolean {
        val file = fileFor(draftId) ?: return false
        val temporary = File(directory, "${file.name}$TEMPORARY_SUFFIX")
        return try {
            directory.mkdirs()
            writeFile(temporary, encodeDraft(draft))
            replaceFile(temporary, file)
            true
        } catch (error: Exception) {
            System.err.println("$TAG: Failed to write editor draft $draftId: $error")
            temporary.delete()
            false
        }
    }

    /** Whether no file of [draftId] is left. */
    override fun delete(draftId: String): Boolean {
        val file = fileFor(draftId) ?: return true
        if (file.delete() || !file.exists()) return true
        System.err.println("$TAG: Failed to delete editor draft $draftId")
        return false
    }

    /** Deletes the files, including leftover temporary ones, of drafts not in [keep] last written before [cutoffMillis]. */
    override fun deleteOlderThan(cutoffMillis: Long, keep: Set<String>) {
        directory.listFiles()
            ?.filter { it.isFile && it.name.substringBefore('.') !in keep && it.lastModified() < cutoffMillis }
            ?.forEach { it.delete() }
    }

    private fun fileFor(draftId: String): File? =
        draftId.takeIf(::isValidDraftFileId)
            ?.let { File(directory, "$it.json") }

    private companion object {
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

