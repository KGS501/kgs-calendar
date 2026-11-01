package com.kgs.calendar.data.account

import com.kgs.calendar.data.platform.squarePng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/** A stored source image; [version] changes whenever the file is replaced. */
data class SourceImage(val path: String, val version: Long)

/** Keys of [SourceImageStore] images. */
object SourceImageKeys {
    /** The image a source provides itself: a Nextcloud avatar or an iCalendar feed's IMAGE. */
    fun account(accountId: String): String = "account:$accountId"

    /** An image the user picked for a source; it is shown instead of the one the source provides. */
    fun accountCustom(accountId: String): String = "account-custom:$accountId"

    /** An image the user picked for a sidebar group. */
    fun group(groupKey: String): String = "group:$groupKey"
}

/**
 * Small square images for calendar sources and sidebar groups, stored as PNG files in
 * [directory]. Every image is center-cropped and scaled down to [MAX_SIZE_PX] before it is
 * written, so callers may pass photos or full-size avatars. A null [directory] keeps nothing.
 */
class SourceImageStore(private val directory: File?) {
    private val state = MutableStateFlow(scan())

    /** Every stored image by key. */
    val images: StateFlow<Map<String, SourceImage>> = state.asStateFlow()

    /** Stores [bytes] as the image of [key]. Returns false when they are not a decodable image. */
    fun save(key: String, bytes: ByteArray): Boolean {
        val dir = directory ?: return false
        val png = squarePng(bytes, MAX_SIZE_PX) ?: return false
        dir.mkdirs()
        val target = fileFor(key)
        val temporary = File(dir, target.name + ".tmp")
        try {
            temporary.writeBytes(png)
            if (!temporary.renameTo(target)) {
                target.delete()
                if (!temporary.renameTo(target)) return false
            }
        } finally {
            temporary.delete()
        }
        // File times are too coarse to tell two saves apart, so the version is a fresh stamp.
        state.update { it + (key to SourceImage(target.absolutePath, System.nanoTime())) }
        return true
    }

    /** Gives the image of [from] the key [to], unless [to] already has one; [from] is removed. */
    fun move(from: String, to: String) {
        if (directory == null || from !in state.value) return
        if (to !in state.value && fileFor(from).renameTo(fileFor(to))) {
            state.update { it - from + (to to SourceImage(fileFor(to).absolutePath, System.nanoTime())) }
        }
        delete(from)
    }

    fun delete(key: String) {
        if (directory == null) return
        fileFor(key).delete()
        checkedMarker(key).delete()
        state.update { it - key }
    }

    /** Whether a source image should be fetched again: never checked, or [intervalMillis] ago. */
    fun isRefreshDue(key: String, nowMillis: Long, intervalMillis: Long): Boolean {
        if (directory == null) return false
        val checkedAt = checkedMarker(key).takeIf { it.exists() }?.lastModified() ?: return true
        return nowMillis - checkedAt >= intervalMillis || nowMillis < checkedAt
    }

    fun markChecked(key: String, nowMillis: Long) {
        val dir = directory ?: return
        dir.mkdirs()
        val marker = checkedMarker(key)
        if (!marker.exists()) marker.createNewFile()
        marker.setLastModified(nowMillis)
    }

    private fun scan(): Map<String, SourceImage> =
        directory?.listFiles { file -> file.isFile && file.name.endsWith(IMAGE_SUFFIX) }
            .orEmpty()
            .mapNotNull { file ->
                val key = decodeKey(file.name.removeSuffix(IMAGE_SUFFIX)) ?: return@mapNotNull null
                key to SourceImage(file.absolutePath, file.lastModified())
            }
            .toMap()

    private fun fileFor(key: String): File = File(directory, encodeKey(key) + IMAGE_SUFFIX)

    private fun checkedMarker(key: String): File = File(directory, encodeKey(key) + CHECKED_SUFFIX)

    companion object {
        const val MAX_SIZE_PX = 256
        private const val IMAGE_SUFFIX = ".png"
        private const val CHECKED_SUFFIX = ".checked"

        // Keys contain account hrefs and domains; hex keeps every key a safe, reversible file name.
        private fun encodeKey(key: String): String =
            key.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }

        private fun decodeKey(name: String): String? =
            runCatching {
                require(name.length % 2 == 0)
                ByteArray(name.length / 2) { index -> name.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
                    .toString(Charsets.UTF_8)
            }.getOrNull()
    }
}
