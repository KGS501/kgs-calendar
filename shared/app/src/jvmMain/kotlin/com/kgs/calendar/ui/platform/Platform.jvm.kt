package com.kgs.calendar.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.kgs.calendar.ui.LocationAnchor
import com.kgs.calendar.ui.LocationSuggestion
import com.kgs.calendar.ui.cleanCalendarDisplayText
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.net.URI
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
internal actual fun rememberPlatformServices(): PlatformServices = remember { DesktopPlatformServices }

internal actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

/** Windows' short time format decides; Java reports it through the default locale's pattern. */
@Composable
internal actual fun is24HourClock(): Boolean = remember {
    val pattern = (DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()) as? SimpleDateFormat)?.toPattern()
    pattern == null || 'H' in pattern || 'k' in pattern
}

private object DesktopPlatformServices : PlatformServices {
    override fun openUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            } else {
                ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
            }
        }
    }

    override fun openMap(suggestion: LocationSuggestion) = openUrl(openStreetMapUrl(suggestion))

    override fun openMapSearch(query: String) {
        val cleaned = query.cleanCalendarDisplayText()
        if (cleaned.isNotBlank()) openUrl(openStreetMapSearchUrl(cleaned))
    }

    override fun lastKnownLocation(): LocationAnchor? = null

    override fun hasLocationPermission(): Boolean = false
}

@Composable
internal actual fun rememberLocationPermissionRequest(onResult: (Boolean) -> Unit): (() -> Unit)? = null
