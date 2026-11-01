package com.kgs.calendar.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.kgs.calendar.ui.LocationAnchor
import com.kgs.calendar.ui.LocationSuggestion
import java.io.ByteArrayOutputStream
import java.util.Locale

/** What the screens ask of the operating system: opening links and maps, and the device's location. */
internal interface PlatformServices {
    /** Opens [url] in the browser or the app that handles it. */
    fun openUrl(url: String)

    /** Shows [suggestion] in a maps app, or on OpenStreetMap when there is none. */
    fun openMap(suggestion: LocationSuggestion)

    /** Searches for [query] in a maps app, or on OpenStreetMap when there is none. */
    fun openMapSearch(query: String)

    /** The device's last known position, used to rank nearby places first; null when unknown or not permitted. */
    fun lastKnownLocation(): LocationAnchor?

    /** Whether the app may read the device's location. */
    fun hasLocationPermission(): Boolean
}

/**
 * Asks the user for their approximate location and reports whether it was granted. Null where
 * the platform has no location to offer, such as the desktop.
 */
@Composable
internal expect fun rememberLocationPermissionRequest(onResult: (Boolean) -> Unit): (() -> Unit)?

/** The platform's services for the current window. */
@Composable
internal expect fun rememberPlatformServices(): PlatformServices

/** Decodes a PNG, JPEG or WebP image; null when [bytes] are not a decodable image. */
internal expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

/** Whether the system shows times on a 24-hour clock. */
@Composable
internal expect fun is24HourClock(): Boolean

/**
 * Decodes `%XX` escapes as UTF-8, like Android's `Uri.decode`: a plus sign stays a plus sign.
 * Invalid escapes are kept as they are.
 */
internal fun String.percentDecode(): String {
    if ('%' !in this) return this
    val bytes = ByteArrayOutputStream(length)
    var index = 0
    while (index < length) {
        val char = this[index]
        if (char == '%' && index + 2 <= lastIndex) {
            val value = substring(index + 1, index + 3).toIntOrNull(16)
            if (value != null) {
                bytes.write(value)
                index += 3
                continue
            }
        }
        bytes.write(char.toString().toByteArray(Charsets.UTF_8))
        index++
    }
    return bytes.toString(Charsets.UTF_8.name())
}

/** A bounded cache that drops its least recently used entries once their total [sizeOf] exceeds [maxSize]. */
internal class LruCache<K : Any, V : Any>(
    private val maxSize: Int,
    private val sizeOf: (V) -> Int = { 1 },
) {
    private val entries = LinkedHashMap<K, V>(16, 0.75f, true)
    private var size = 0

    @Synchronized
    fun get(key: K): V? = entries[key]

    @Synchronized
    fun put(key: K, value: V) {
        entries.put(key, value)?.let { size -= sizeOf(it) }
        size += sizeOf(value)
        val iterator = entries.entries.iterator()
        while (size > maxSize && iterator.hasNext()) {
            val eldest = iterator.next()
            if (eldest.key == key) continue
            size -= sizeOf(eldest.value)
            iterator.remove()
        }
    }
}

/**
 * The locale's pattern for a date [skeleton] such as "EEEdMMM", like Android's
 * `DateFormat.getBestDateTimePattern`.
 */
internal expect fun bestDateTimePattern(locale: Locale, skeleton: String): String

/** Converts an ARGB color to hue (0..360), saturation and value (0..1), like Android's `Color.colorToHSV`. */
internal fun colorToHsv(argb: Int, hsv: FloatArray) {
    val red = ((argb shr 16) and 0xFF) / 255f
    val green = ((argb shr 8) and 0xFF) / 255f
    val blue = (argb and 0xFF) / 255f
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == red -> 60f * (((green - blue) / delta) % 6f)
        max == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    hsv[0] = if (hue < 0f) hue + 360f else hue
    hsv[1] = if (max == 0f) 0f else delta / max
    hsv[2] = max
}

/** Minimal logging that ends up in Logcat on Android and on the console on the desktop. */
internal object Log {
    private fun logger(tag: String) = java.util.logging.Logger.getLogger(tag)

    fun isDebugEnabled(tag: String): Boolean = logger(tag).isLoggable(java.util.logging.Level.FINE)

    fun d(tag: String, message: String) = logger(tag).fine(message)

    fun w(tag: String, message: String, error: Throwable? = null) =
        logger(tag).log(java.util.logging.Level.WARNING, message, error)
}
