package com.kgs.calendar.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

class KgsWidgetImageProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") {
            throw FileNotFoundException("Widget images are read-only.")
        }
        // RemoteViews image URIs are opened by the launcher process; keep the provider
        // exported, but constrain reads to hashed PNGs inside the canonical cache root.
        val segments = uri.pathSegments
        if (
            segments.size != 2 ||
            !segments[0].matches(DAY_DIRECTORY_PATTERN) ||
            !segments[1].matches(IMAGE_FILE_PATTERN)
        ) {
            throw FileNotFoundException("Invalid widget image path.")
        }
        val appContext = context ?: throw FileNotFoundException("Provider context is unavailable.")
        val root = File(appContext.filesDir, CACHE_DIRECTORY).canonicalFile
        val image = File(File(root, segments[0]), segments[1]).canonicalFile
        if (
            !image.path.startsWith(root.path + File.separator) ||
            !image.isFile
        ) {
            throw FileNotFoundException("Widget image does not exist.")
        }
        return ParcelFileDescriptor.open(image, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private companion object {
        const val CACHE_DIRECTORY = "widget_images"
        val DAY_DIRECTORY_PATTERN = Regex("""day_\d+""")
        val IMAGE_FILE_PATTERN = Regex("""[0-9a-f]{64}\.png""")
    }
}
