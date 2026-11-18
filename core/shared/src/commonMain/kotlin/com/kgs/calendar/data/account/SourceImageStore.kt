package com.kgs.calendar.data.account

import com.kgs.calendar.time.nowMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

/** The directory the images live in, by file name. */
interface SourceImageFiles {
    /** Names and modification times (epoch millis) of the files in the directory. */
    fun list(): List<Pair<String, Long>>
    fun pathOf(name: String): String
    /** Writes [bytes] to [name] through a temporary file, replacing it. */
    fun writeReplacing(name: String, bytes: ByteArray): Boolean
    fun rename(from: String, to: String): Boolean
    fun delete(name: String)
    fun lastModified(name: String): Long?
    /** Creates [name] when missing and sets its modification time. */
    fun touch(name: String, millis: Long)
}

/** Center-crops and scales an image to a square PNG of at most [maxSizePx]; null when undecodable. */
fun interface SquareImageEncoder {
    fun encodeSquarePng(bytes: ByteArray, maxSizePx: Int): ByteArray?
}

/**
 * Small square images for calendar sources and sidebar groups, stored as PNG files. Every image
 * is center-cropped and scaled down to [MAX_SIZE_PX] before it is written, so callers may pass
 * photos or full-size avatars. Without [files] it keeps nothing.
 */
class SourceImageStore(
    private val files: SourceImageFiles?,
    private val encoder: SquareImageEncoder,
) {
    private var versionCounter = 0L
    private val state = MutableStateFlow(scan())

    /** Every stored image by key. */
    val images: StateFlow<Map<String, SourceImage>> = state.asStateFlow()

    /** Stores [bytes] as the image of [key]. Returns false when they are not a decodable image. */
    fun save(key: String, bytes: ByteArray): Boolean {
        val files = files ?: return false
        val png = encoder.encodeSquarePng(bytes, MAX_SIZE_PX) ?: return false
        val name = imageName(key)
        if (!files.writeReplacing(name, png)) return false
        // File times are too coarse to tell two saves apart, so the version is a fresh stamp.
        state.update { it + (key to SourceImage(files.pathOf(name), nextVersion())) }
        return true
    }

    /** Gives the image of [from] the key [to], unless [to] already has one; [from] is removed. */
    fun move(from: String, to: String) {
        val files = files ?: return
        if (from !in state.value) return
        if (to !in state.value && files.rename(imageName(from), imageName(to))) {
            state.update { it - from + (to to SourceImage(files.pathOf(imageName(to)), nextVersion())) }
        }
        delete(from)
    }

    fun delete(key: String) {
        val files = files ?: return
        files.delete(imageName(key))
        files.delete(checkedName(key))
        state.update { it - key }
    }

    /** Whether a source image should be fetched again: never checked, or [intervalMillis] ago. */
    fun isRefreshDue(key: String, nowMillis: Long, intervalMillis: Long): Boolean {
        val files = files ?: return false
        val checkedAt = files.lastModified(checkedName(key)) ?: return true
        return nowMillis - checkedAt >= intervalMillis || nowMillis < checkedAt
    }

    fun markChecked(key: String, nowMillis: Long) {
        files?.touch(checkedName(key), nowMillis)
    }

    private fun nextVersion(): Long = nowMillis() * 1000 + (versionCounter++ % 1000)

    private fun scan(): Map<String, SourceImage> =
        files?.list().orEmpty()
            .filter { (name, _) -> name.endsWith(IMAGE_SUFFIX) }
            .mapNotNull { (name, modified) ->
                val key = decodeKey(name.removeSuffix(IMAGE_SUFFIX)) ?: return@mapNotNull null
                key to SourceImage(files!!.pathOf(name), modified)
            }
            .toMap()

    companion object {
        const val MAX_SIZE_PX = 256
        private const val IMAGE_SUFFIX = ".png"
        private const val CHECKED_SUFFIX = ".checked"

        /** A store that keeps nothing (tests, previews). */
        val None: SourceImageStore get() = SourceImageStore(null) { _, _ -> null }

        private fun imageName(key: String) = encodeKey(key) + IMAGE_SUFFIX
        private fun checkedName(key: String) = encodeKey(key) + CHECKED_SUFFIX

        // Keys contain account hrefs and domains; hex keeps every key a safe, reversible file name.
        private fun encodeKey(key: String): String =
            key.encodeToByteArray().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

        private fun decodeKey(name: String): String? =
            runCatching {
                require(name.length % 2 == 0)
                ByteArray(name.length / 2) { index -> name.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
                    .decodeToString()
            }.getOrNull()
    }
}
