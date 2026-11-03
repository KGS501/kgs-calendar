package com.kgs.calendar.ios

import com.kgs.calendar.ui.platform.topViewController
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerViewController

/** Offers a written export to Files (or any app that takes it), like Android's "Save to" picker. */
object IosDocumentExport {
    fun offer(path: String) {
        val url = NSURL.fileURLWithPath(path)
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}
