package com.kgs.calendar.ui.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.kgs.calendar.time.AppLocale
import com.kgs.calendar.ui.LocationAnchor
import com.kgs.calendar.ui.LocationSuggestion

actual typealias PlatformContext = Context

actual val LocalPlatformContext: ProvidableCompositionLocal<PlatformContext> get() = LocalContext

private fun Context.startViewing(url: String): Boolean =
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

actual fun PlatformContext.openUrl(url: String) {
    startViewing(url)
}

actual fun PlatformContext.openMapLocation(suggestion: LocationSuggestion) {
    val latLng = "${suggestion.latitude},${suggestion.longitude}"
    val encodedLabel = Uri.encode(suggestion.primaryName.ifBlank { suggestion.displayName })
    if (!startViewing("geo:${suggestion.latitude},${suggestion.longitude}?q=$latLng($encodedLabel)")) {
        startViewing("https://www.openstreetmap.org/?mlat=${suggestion.latitude}&mlon=${suggestion.longitude}#map=16/${suggestion.latitude}/${suggestion.longitude}")
    }
}

actual fun PlatformContext.openMapQuery(query: String) {
    val encodedQuery = Uri.encode(query)
    if (!startViewing("geo:0,0?q=$encodedQuery")) {
        startViewing("https://www.openstreetmap.org/search?query=$encodedQuery")
    }
}

@SuppressLint("MissingPermission")
actual fun PlatformContext.lastKnownLocationAnchor(): LocationAnchor? {
    val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    ).mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
        ?.let { LocationAnchor(it.latitude, it.longitude) }
}

actual fun PlatformContext.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

actual fun PlatformContext.hasDeviceCalendarPermissions(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

actual fun PlatformContext.appVersionName(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

@Composable
actual fun rememberLocationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return remember(launcher) { { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) } }
}

@Composable
actual fun rememberDeviceCalendarPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val readGranted = grants[Manifest.permission.READ_CALENDAR] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val writeGranted = grants[Manifest.permission.WRITE_CALENDAR] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        onResult(readGranted && writeGranted)
    }
    return remember(launcher) { { launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) } }
}

@Composable
actual fun rememberCreateDocumentLauncher(mimeType: String, onResult: (PlatformUri?) -> Unit): (String) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        onResult(uri?.let { PlatformUri(it.toString()) })
    }
    return remember(launcher) { { name -> launcher.launch(name) } }
}

@Composable
actual fun rememberPickImageLauncher(onResult: (PlatformUri?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        onResult(uri?.let { PlatformUri(it.toString()) })
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

@Composable
actual fun ProvidePlatformLocale(locale: AppLocale, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val configuration = LocalConfiguration.current
    val localizedContext = remember(baseContext, locale) { baseContext.withAppLocale(locale) }
    val localizedConfiguration = remember(configuration, locale) { Configuration(configuration).apply { setLocale(locale) } }
    androidx.compose.runtime.DisposableEffect(locale) {
        val previousLocale = java.util.Locale.getDefault()
        java.util.Locale.setDefault(locale)
        onDispose { java.util.Locale.setDefault(previousLocale) }
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
        content = content,
    )
}

fun Context.withAppLocale(locale: AppLocale): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    val localizedContext = createConfigurationContext(config)
    return object : ContextWrapper(this) {
        override fun getAssets(): AssetManager = localizedContext.assets
        override fun getResources(): Resources = localizedContext.resources
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
actual fun PlatformSystemBarsEffect(darkPalette: Boolean) {
    val view = LocalView.current
    SideEffect {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkPalette
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkPalette
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
        }
    }
}

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

actual fun PlatformContext.is24HourFormat(): Boolean = android.text.format.DateFormat.is24HourFormat(this)

actual fun PlatformContext.systemLocale(): AppLocale =
    resources.configuration.locales[0] ?: java.util.Locale.getDefault()

actual fun bestDateTimePattern(locale: AppLocale, skeleton: String): String =
    android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)

@Composable
actual fun TransparentNavigationBarEffect() {
    val view = LocalView.current
    val navigationBarColor = Color.Transparent.toArgb()
    androidx.compose.runtime.DisposableEffect(view, navigationBarColor) {
        val window = view.context.findActivity()?.window
        @Suppress("DEPRECATION")
        val previousColor = window?.navigationBarColor
        @Suppress("DEPRECATION")
        window?.navigationBarColor = navigationBarColor
        onDispose {
            @Suppress("DEPRECATION")
            if (previousColor != null) window.navigationBarColor = previousColor
        }
    }
}

actual fun decodeImageFile(path: String): ImageBitmap? = BitmapFactory.decodeFile(path)?.asImageBitmap()
