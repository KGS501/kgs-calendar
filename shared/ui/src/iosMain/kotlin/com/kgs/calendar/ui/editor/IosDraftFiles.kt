package com.kgs.calendar.ui.editor

import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

/** Draft files in [directory]; writes are atomic, so a failed write keeps the previous revision. */
class IosDraftFiles(private val directory: String) : DraftFiles {
    private val files = NSFileManager.defaultManager

    private fun pathFor(draftId: String): String? =
        draftId.takeIf(::isValidDraftFileId)?.let { "$directory/$it.json" }

    override fun read(draftId: String): DraftRevision? {
        val path = pathFor(draftId) ?: return null
        val text = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: return null
        return decodeDraft(text)
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    override fun write(draftId: String, draft: DraftRevision): Boolean {
        val path = pathFor(draftId) ?: return false
        files.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
        return (NSString.create(string = encodeDraft(draft)))
            .writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    override fun delete(draftId: String): Boolean {
        val path = pathFor(draftId) ?: return true
        if (!files.fileExistsAtPath(path)) return true
        return files.removeItemAtPath(path, null)
    }

    override fun deleteOlderThan(cutoffMillis: Long, keep: Set<String>) {
        val names = files.contentsOfDirectoryAtPath(directory, null)?.filterIsInstance<String>() ?: return
        for (name in names) {
            if (name.substringBefore('.') in keep) continue
            val path = "$directory/$name"
            val modified = (files.attributesOfItemAtPath(path, null)?.get(NSFileModificationDate) as? NSDate)
                ?.timeIntervalSince1970?.times(1000)?.toLong() ?: continue
            if (modified < cutoffMillis) files.removeItemAtPath(path, null)
        }
    }
}
