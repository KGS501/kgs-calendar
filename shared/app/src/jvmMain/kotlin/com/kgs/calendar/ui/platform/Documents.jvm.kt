package com.kgs.calendar.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** The desktop app's version, set by the app at startup. */
var DesktopAppVersion: String = ""

@Composable
internal actual fun rememberCreateDocument(mimeType: String, onResult: (String?) -> Unit): (suggestedName: String) -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember(mimeType) {
        { name ->
            val dialog = FileDialog(null as Frame?, "", FileDialog.SAVE).apply { file = name }
            dialog.isVisible = true
            val chosen = dialog.file?.let { File(dialog.directory, it) }
            val withExtension = chosen?.let { file ->
                val extension = name.substringAfterLast('.', "")
                if (extension.isNotEmpty() && !file.name.contains('.')) File(file.path + "." + extension) else file
            }
            currentOnResult.value(withExtension?.absolutePath)
        }
    }
}

@Composable
internal actual fun rememberPickImage(onResult: (String?) -> Unit): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember {
        {
            val dialog = FileDialog(null as Frame?, "", FileDialog.LOAD).apply { file = "*.png;*.jpg;*.jpeg;*.gif;*.bmp;*.webp" }
            dialog.isVisible = true
            currentOnResult.value(dialog.file?.let { File(dialog.directory, it).absolutePath })
        }
    }
}

@Composable
internal actual fun rememberCalendarPermissionRequest(onResult: (Boolean) -> Unit): (() -> Unit)? = null

@Composable
internal actual fun hasCalendarPermission(): Boolean = false

@Composable
internal actual fun appVersionName(): String = DesktopAppVersion
