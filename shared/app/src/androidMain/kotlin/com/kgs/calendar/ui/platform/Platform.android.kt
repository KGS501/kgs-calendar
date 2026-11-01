package com.kgs.calendar.ui.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kgs.calendar.ui.LocationAnchor
import com.kgs.calendar.ui.LocationSuggestion
import com.kgs.calendar.ui.cleanCalendarDisplayText

@Composable
internal actual fun rememberPlatformServices(): PlatformServices {
    val context = LocalContext.current
    return remember(context) { AndroidPlatformServices(context) }
}

internal actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

@Composable
internal actual fun is24HourClock(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

private class AndroidPlatformServices(private val context: Context) : PlatformServices {
    override fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    override fun openMap(suggestion: LocationSuggestion) {
        val latLng = "${suggestion.latitude},${suggestion.longitude}"
        val encodedLabel = Uri.encode(suggestion.primaryName.ifBlank { suggestion.displayName })
        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${suggestion.latitude},${suggestion.longitude}?q=$latLng($encodedLabel)"))
        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(openStreetMapUrl(suggestion)))
        runCatching { context.startActivity(mapIntent) }
            .recoverCatching { context.startActivity(fallbackIntent) }
    }

    override fun openMapSearch(query: String) {
        val cleaned = query.cleanCalendarDisplayText()
        if (cleaned.isBlank()) return
        val encodedQuery = Uri.encode(cleaned)
        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedQuery"))
        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(openStreetMapSearchUrl(cleaned)))
        runCatching { context.startActivity(mapIntent) }
            .recoverCatching { context.startActivity(fallbackIntent) }
    }

    override fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override fun lastKnownLocation(): LocationAnchor? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { LocationAnchor(it.latitude, it.longitude) }
    }
}

@Composable
internal actual fun rememberLocationPermissionRequest(onResult: (Boolean) -> Unit): (() -> Unit)? {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return remember(launcher) { { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) } }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
