package com.kgs.calendar.importing

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import java.io.ByteArrayOutputStream

internal fun Intent.icsAttachmentUri(): Uri? = when (action) {
    Intent.ACTION_VIEW -> data
    Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
        ?: clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.uri
    else -> null
}?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT || it.scheme == ContentResolver.SCHEME_FILE }

internal fun ContentResolver.readIcsAttachment(uri: Uri): String =
    requireNotNull(openInputStream(uri)).use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_ICS_BYTES) { "Calendar file is too large" }
            output.write(buffer, 0, count)
        }
        output.toString(Charsets.UTF_8.name())
    }

private const val MAX_ICS_BYTES = 2 * 1024 * 1024
