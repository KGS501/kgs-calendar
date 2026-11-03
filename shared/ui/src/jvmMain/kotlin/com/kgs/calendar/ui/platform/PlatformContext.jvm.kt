package com.kgs.calendar.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.kgs.calendar.time.AppLocale
import com.kgs.calendar.ui.LocationAnchor
import com.kgs.calendar.ui.LocationSuggestion
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

actual abstract class PlatformContext

/** The desktop app has one context: the application. */
object DesktopPlatformContext : PlatformContext()

actual val LocalPlatformContext: ProvidableCompositionLocal<PlatformContext> = staticCompositionLocalOf { DesktopPlatformContext }

/** The desktop app's version, set by the app at startup. */
var DesktopAppVersion: String = ""

/** The system language as it was at startup, before the app applied its own language setting. */
private val startupLocale: Locale = Locale.getDefault()

actual fun PlatformContext.openUrl(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
        }
    }
}

/** Windows has no maps app to hand a place to; OpenStreetMap opens in the browser. */
actual fun PlatformContext.openMapLocation(suggestion: LocationSuggestion) = openUrl(
    "https://www.openstreetmap.org/?mlat=${suggestion.latitude}&mlon=${suggestion.longitude}" +
        "#map=16/${suggestion.latitude}/${suggestion.longitude}",
)

actual fun PlatformContext.openMapQuery(query: String) {
    if (query.isBlank()) return
    openUrl("https://www.openstreetmap.org/search?query=" + URLEncoder.encode(query, Charsets.UTF_8).replace("+", "%20"))
}

actual fun PlatformContext.lastKnownLocationAnchor(): LocationAnchor? = null

actual fun PlatformContext.hasLocationPermission(): Boolean = false

actual fun PlatformContext.hasDeviceCalendarPermissions(): Boolean = false

actual fun PlatformContext.appVersionName(): String = DesktopAppVersion

/** The desktop app doesn't use the location; nothing to ask. */
@Composable
actual fun rememberLocationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    return remember { { callback.value(false) } }
}

@Composable
actual fun rememberDeviceCalendarPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    return remember { { callback.value(false) } }
}

/** Windows' save dialog; the suggested name's extension is added when the user leaves it out. */
@Composable
actual fun rememberCreateDocumentLauncher(mimeType: String, onResult: (PlatformUri?) -> Unit): (String) -> Unit {
    val callback = rememberUpdatedState(onResult)
    return remember(mimeType) {
        { name ->
            val dialog = FileDialog(null as Frame?, "", FileDialog.SAVE).apply { file = name }
            dialog.isVisible = true
            val chosen = dialog.file?.let { File(dialog.directory, it) }
            val withExtension = chosen?.let { file ->
                val extension = name.substringAfterLast('.', "")
                if (extension.isNotEmpty() && !file.name.contains('.')) File(file.path + "." + extension) else file
            }
            callback.value(withExtension?.let { PlatformUri(it.absolutePath) })
        }
    }
}

@Composable
actual fun rememberPickImageLauncher(onResult: (PlatformUri?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    return remember {
        {
            val dialog = FileDialog(null as Frame?, "", FileDialog.LOAD).apply { file = "*.png;*.jpg;*.jpeg;*.gif;*.bmp;*.webp" }
            dialog.isVisible = true
            callback.value(dialog.file?.let { PlatformUri(File(dialog.directory, it).absolutePath) })
        }
    }
}

@Composable
actual fun ProvidePlatformLocale(locale: AppLocale, content: @Composable () -> Unit) = content()

/** Receives the app's palette, so the window's title bar can match it (set by the desktop app). */
var DesktopWindowChrome: (darkPalette: Boolean) -> Unit = {}

@Composable
actual fun PlatformSystemBarsEffect(darkPalette: Boolean) {
    androidx.compose.runtime.SideEffect { DesktopWindowChrome(darkPalette) }
}

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

/** Windows' short time format decides; Java reports it through the default locale's pattern. */
actual fun PlatformContext.is24HourFormat(): Boolean {
    val pattern = (DateFormat.getTimeInstance(DateFormat.SHORT, startupLocale) as? SimpleDateFormat)?.toPattern()
    return pattern == null || 'H' in pattern || 'k' in pattern
}

actual fun PlatformContext.systemLocale(): AppLocale = startupLocale

/**
 * The JDK has no date skeletons, so the app's few skeletons are spelled out for the common
 * conventions: US English, other English and German, and a day-first default.
 */
actual fun bestDateTimePattern(locale: AppLocale, skeleton: String): String {
    val withYear = skeleton.contains('y')
    val weekday = skeleton.contains('E')
    val month = when {
        skeleton.contains("MMMM") -> "MMMM"
        skeleton.contains("MMM") -> "MMM"
        else -> "MM"
    }
    return when {
        locale.language == "en" && (locale.country.isEmpty() || locale.country == "US") ->
            (if (weekday) "EEE, " else "") + "$month d" + (if (withYear) ", y" else "")
        locale.language == "de" ->
            (if (weekday) "EEE, " else "") + "d. $month" + (if (withYear) " y" else "")
        else ->
            (if (weekday) "EEE " else "") + "d $month" + (if (withYear) " y" else "")
    }
}

@Composable
actual fun TransparentNavigationBarEffect() = Unit

actual fun decodeImageFile(path: String): ImageBitmap? =
    runCatching { decodeImageBitmap(File(path).readBytes()) }.getOrNull()
