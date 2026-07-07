package com.kgs.calendar.widget

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

internal object KgsWidgetBitmapUriStore {
    private const val CACHE_DIRECTORY = "widget_images"
    private const val MAX_FILES_PER_WIDGET = 320

    @Synchronized
    fun put(
        context: Context,
        appWidgetId: Int,
        cacheKey: String,
        bitmap: Bitmap,
    ): Uri {
        val widgetDirectory = File(
            context.filesDir,
            "$CACHE_DIRECTORY/day_${appWidgetId.coerceAtLeast(0)}",
        ).apply {
            if (!exists() && !mkdirs()) {
                throw IOException("Could not create widget image cache directory.")
            }
        }
        val target = File(widgetDirectory, "${saltedCacheKey(context, cacheKey).sha256Hex()}.png")
        if (!target.exists()) {
            val temporary = File(widgetDirectory, "${target.name}.tmp")
            temporary.outputStream().buffered().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException("Could not encode widget image.")
                }
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } else {
            target.setLastModified(System.currentTimeMillis())
        }
        prune(widgetDirectory, target)
        return Uri.Builder()
            .scheme("content")
            .authority("${context.packageName}.widget.images")
            .appendPath(widgetDirectory.name)
            .appendPath(target.name)
            .build()
    }

    private fun prune(directory: File, retained: File) {
        val files = directory.listFiles { file -> file.isFile && file.extension == "png" }
            .orEmpty()
        if (files.size > MAX_FILES_PER_WIDGET) {
            files.sortedByDescending(File::lastModified)
                .drop(MAX_FILES_PER_WIDGET)
                .forEach { file ->
                    if (file != retained) {
                        file.delete()
                    }
                }
        }
        directory.listFiles { file -> file.isFile && file.extension == "tmp" }
            ?.forEach(File::delete)
    }

    private fun saltedCacheKey(context: Context, cacheKey: String): String {
        val preferences = context.getSharedPreferences("kgs_widget_image_cache", Context.MODE_PRIVATE)
        val salt = preferences.getString("salt", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("salt", it).apply()
        }
        return "$salt|$cacheKey"
    }
}

private fun String.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
