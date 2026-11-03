package com.kgs.calendar.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.graphics.ImageBitmap
import com.kgs.calendar.time.AppLocale
import com.kgs.calendar.ui.LocationAnchor
import com.kgs.calendar.ui.LocationSuggestion

/**
 * What the UI needs from the platform. On Android this is the `Context` itself (and
 * [LocalPlatformContext] is `LocalContext`), so the Android code reads as before.
 */
expect abstract class PlatformContext

expect val LocalPlatformContext: ProvidableCompositionLocal<PlatformContext>

/** A picked or created document: an Android content URI or an iOS file URL, as text. */
data class PlatformUri(val value: String)

expect fun PlatformContext.openUrl(url: String)

expect fun PlatformContext.openMapLocation(suggestion: LocationSuggestion)

/** Opens the maps app on a search for [query] (already cleaned of calendar markup). */
expect fun PlatformContext.openMapQuery(query: String)

/** The device's last known position, when location access was granted. */
expect fun PlatformContext.lastKnownLocationAnchor(): LocationAnchor?

expect fun PlatformContext.hasLocationPermission(): Boolean

/** Read and write access to the device's own calendars (Android's calendar provider). */
expect fun PlatformContext.hasDeviceCalendarPermissions(): Boolean

/** The app's version name, as shown in About. */
expect fun PlatformContext.appVersionName(): String

/** Asks for coarse location; [onResult] gets whether it was granted. */
@Composable
expect fun rememberLocationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit

/** Asks for device calendar access; [onResult] gets whether both read and write were granted. */
@Composable
expect fun rememberDeviceCalendarPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit

/** Lets the user create a document of [mimeType]; the launcher takes the suggested file name. */
@Composable
expect fun rememberCreateDocumentLauncher(mimeType: String, onResult: (PlatformUri?) -> Unit): (String) -> Unit

/** Lets the user pick a photo. */
@Composable
expect fun rememberPickImageLauncher(onResult: (PlatformUri?) -> Unit): () -> Unit

/**
 * Provides the platform pieces that follow the app's language (Android re-localises its Context
 * and Configuration; iOS needs nothing).
 */
@Composable
expect fun ProvidePlatformLocale(locale: AppLocale, content: @Composable () -> Unit)

/** Status and navigation bar icons dark or light to match the app's palette. */
@Composable
expect fun PlatformSystemBarsEffect(darkPalette: Boolean)

/** Decodes a PNG or JPEG. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

/** The system's 24-hour clock preference. */
expect fun PlatformContext.is24HourFormat(): Boolean

/** The system's language (or the app language chosen in Android's per-app settings). */
expect fun PlatformContext.systemLocale(): AppLocale

/** The best date pattern for [skeleton] (e.g. "EEEdMMM") in [locale], as Android's DateFormat. */
expect fun bestDateTimePattern(locale: AppLocale, skeleton: String): String

/** Keeps the system navigation bar transparent while a sheet is open (Android). */
@Composable
expect fun TransparentNavigationBarEffect()

/** As android.graphics.Color.colorToHSV: hue 0..360, saturation and value 0..1. */
fun colorToHsv(argb: Int, hsv: FloatArray) {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    var hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    if (hue < 0f) hue += 360f
    hsv[0] = hue
    hsv[1] = if (max == 0f) 0f else delta / max
    hsv[2] = max
}

/** Decodes the image file at [path]. */
expect fun decodeImageFile(path: String): ImageBitmap?
