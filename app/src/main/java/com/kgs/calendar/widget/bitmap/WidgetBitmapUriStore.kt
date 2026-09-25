package com.kgs.calendar.widget.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.kgs.calendar.widget.update.WidgetPerformanceMonitor
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class WidgetBitmapUriStore(private val context: Context) {
    private data class UpdateGeneration(
        var active: Boolean = false,
        var initialized: Boolean = false,
        var previousNames: Set<String> = emptySet(),
        val currentNames: MutableSet<String> = mutableSetOf(),
    )

    private val widgetLocks = ConcurrentHashMap<Int, Any>()
    private val generations = ConcurrentHashMap<Int, UpdateGeneration>()

    fun getIfPresent(
        appWidgetId: Int,
        cacheKey: String,
    ): Uri? = synchronized(widgetLock(appWidgetId)) {
        val directory = widgetDirectory(appWidgetId)
        val target = targetFile(directory, cacheKey)
        val generation = activeGeneration(appWidgetId, directory)
        if (!target.isFile) {
            WidgetPerformanceMonitor.current()?.recordCacheMiss()
            return@synchronized null
        }
        generation.currentNames += target.name
        WidgetPerformanceMonitor.current()?.recordCacheHit()
        imageUri(directory, target)
    }

    fun put(
        appWidgetId: Int,
        cacheKey: String,
        bitmap: Bitmap,
    ): Uri = synchronized(widgetLock(appWidgetId)) {
        val directory = widgetDirectory(appWidgetId)
        val target = targetFile(directory, cacheKey)
        val generation = activeGeneration(appWidgetId, directory)
        if (!target.exists()) {
            val temporary = File.createTempFile("${target.name}.", ".tmp", directory)
            try {
                temporary.outputStream().buffered().use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw IOException("Could not encode widget image.")
                    }
                }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
            } finally {
                if (temporary.exists()) temporary.delete()
            }
            WidgetPerformanceMonitor.current()?.recordBitmapEncoded(target.length())
            WidgetPerformanceMonitor.current()?.recordFileWrite()
        }
        generation.currentNames += target.name
        imageUri(directory, target)
    }

    fun endUpdate(appWidgetId: Int) {
        synchronized(widgetLock(appWidgetId)) {
            val generation = generations[appWidgetId] ?: return
            if (!generation.active) return
            val directory = widgetDirectory(appWidgetId)
            val protectedNames = generation.previousNames + generation.currentNames
            val imageFiles = directory.listFiles { file -> file.isFile && file.extension == "png" }
                .orEmpty()
            val entries = imageFiles.map { file ->
                WidgetImageCacheEntry(
                    name = file.name,
                    lastModifiedMillis = file.lastModified(),
                    bytes = file.length(),
                )
            }
            val evictions = selectWidgetImageCacheEvictions(
                entries = entries,
                protectedNames = protectedNames,
                maxFiles = MAX_FILES_PER_WIDGET,
                maxBytes = MAX_BYTES_PER_WIDGET,
            ).toSet()
            imageFiles.forEach { file ->
                if (file.name in evictions) file.delete()
            }
            directory.listFiles { file -> file.isFile && file.extension == "tmp" }
                ?.forEach(File::delete)
            generation.previousNames = generation.currentNames.toSet()
            generation.currentNames.clear()
            generation.active = false
        }
    }

    private fun activeGeneration(appWidgetId: Int, directory: File): UpdateGeneration {
        val generation = cachedWidgetValue(generations[appWidgetId]) {
            UpdateGeneration().also { created -> generations[appWidgetId] = created }
        }
        if (!generation.initialized) {
            generation.previousNames = directory.listFiles { file -> file.isFile && file.extension == "png" }
                .orEmpty()
                .mapTo(mutableSetOf()) { file -> file.name }
            generation.initialized = true
        }
        if (!generation.active) {
            generation.currentNames.clear()
            generation.active = true
        }
        return generation
    }

    private fun widgetDirectory(appWidgetId: Int): File =
        File(
            context.filesDir,
            "$CACHE_DIRECTORY/day_${appWidgetId.coerceAtLeast(0)}",
        ).apply {
            if (!exists() && !mkdirs()) {
                throw IOException("Could not create widget image cache directory.")
            }
        }

    private fun targetFile(directory: File, cacheKey: String): File =
        File(directory, "${saltedCacheKey(cacheKey).sha256Hex()}.png")

    private fun imageUri(directory: File, target: File): Uri =
        Uri.Builder()
            .scheme("content")
            .authority("${context.packageName}.widget.images")
            .appendPath(directory.name)
            .appendPath(target.name)
            .build()

    private fun widgetLock(appWidgetId: Int): Any =
        widgetLocks.computeIfAbsent(appWidgetId.coerceAtLeast(0)) { Any() }

    private fun saltedCacheKey(cacheKey: String): String {
        val preferences = context.getSharedPreferences("kgs_widget_image_cache", Context.MODE_PRIVATE)
        val salt = preferences.getString("salt", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("salt", it).apply()
        }
        return "$salt|$cacheKey"
    }

    private companion object {
        const val CACHE_DIRECTORY = "widget_images"
        const val MAX_FILES_PER_WIDGET = 2_048
        const val MAX_BYTES_PER_WIDGET = 128L * 1024L * 1024L
    }
}

private fun String.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
