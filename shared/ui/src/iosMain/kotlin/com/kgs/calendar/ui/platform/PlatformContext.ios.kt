package com.kgs.calendar.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.kgs.calendar.time.AppLocale
import com.kgs.calendar.data.provider.IosEventStore
import com.kgs.calendar.platform.toByteArray
import com.kgs.calendar.ui.LocationAnchor
import com.kgs.calendar.ui.LocationSuggestion
import kotlinx.cinterop.useContents
import org.jetbrains.skia.Image
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSFileManager
import platform.Foundation.NSItemProvider
import platform.Foundation.NSLocale
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.currentLocale
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual abstract class PlatformContext

/** The iOS app has one context: the application. */
object IosPlatformContext : PlatformContext()

actual val LocalPlatformContext: ProvidableCompositionLocal<PlatformContext> = staticCompositionLocalOf { IosPlatformContext }

private fun open(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
}

actual fun PlatformContext.openUrl(url: String) = open(url)

private fun mapsUrl(vararg query: Pair<String, String>): String {
    val components = NSURLComponents(string = "https://maps.apple.com/")
    components.queryItems = query.map { (name, value) -> NSURLQueryItem(name = name, value = value) }
    return components.URL?.absoluteString ?: "https://maps.apple.com/"
}

actual fun PlatformContext.openMapLocation(suggestion: LocationSuggestion) {
    open(mapsUrl("ll" to "${suggestion.latitude},${suggestion.longitude}", "q" to suggestion.primaryName.ifBlank { suggestion.displayName }))
}

actual fun PlatformContext.openMapQuery(query: String) {
    open(mapsUrl("q" to query))
}

private val locationManager by lazy { CLLocationManager() }

actual fun PlatformContext.hasLocationPermission(): Boolean {
    val status = locationManager.authorizationStatus
    return status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways
}

actual fun PlatformContext.lastKnownLocationAnchor(): LocationAnchor? {
    if (!hasLocationPermission()) return null
    val location = locationManager.location ?: return null
    return location.coordinate.useContents { LocationAnchor(latitude, longitude) }
}

actual fun PlatformContext.hasDeviceCalendarPermissions(): Boolean = IosEventStore.hasFullAccess()

actual fun PlatformContext.appVersionName(): String =
    NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: ""

private class LocationAuthorizationDelegate(private val onResult: (Boolean) -> Unit) : NSObject(), CLLocationManagerDelegateProtocol {
    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        val status = manager.authorizationStatus
        if (status != kCLAuthorizationStatusNotDetermined) {
            onResult(status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways)
        }
    }
}

@Composable
actual fun rememberLocationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val delegate = remember { LocationAuthorizationDelegate { granted -> callback.value(granted) } }
    DisposableEffect(delegate) {
        onDispose { if (locationManager.delegate === delegate) locationManager.delegate = null }
    }
    return remember(delegate) {
        {
            locationManager.delegate = delegate
            if (locationManager.authorizationStatus == kCLAuthorizationStatusNotDetermined) {
                locationManager.requestWhenInUseAuthorization()
            } else {
                delegate.locationManagerDidChangeAuthorization(locationManager)
            }
        }
    }
}

@Composable
actual fun rememberDeviceCalendarPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    return remember {
        {
            IosEventStore.requestAccess { granted -> dispatch_async(dispatch_get_main_queue()) { callback.value(granted) } }
        }
    }
}

/**
 * iOS writes exports to a temporary file first; [IosDocumentExport] then offers it to Files or
 * another app once it is written.
 */
@Composable
actual fun rememberCreateDocumentLauncher(mimeType: String, onResult: (PlatformUri?) -> Unit): (String) -> Unit {
    val callback = rememberUpdatedState(onResult)
    return remember(mimeType) {
        { name ->
            val directory = NSTemporaryDirectory() + "exports/"
            NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
            callback.value(PlatformUri(directory + name.replace('/', '-')))
        }
    }
}

private class ImagePickerDelegate(private val onResult: (PlatformUri?) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        if (provider == null) {
            onResult(null)
            return
        }
        loadImage(provider)
    }

    private fun loadImage(provider: NSItemProvider) {
        provider.loadFileRepresentationForTypeIdentifier(UTTypeImage.identifier) { url, _ ->
            // The file only exists during this callback, so it is copied first.
            val copy = url?.let { source ->
                val target = NSTemporaryDirectory() + "picked-" + (source.lastPathComponent ?: "image")
                NSFileManager.defaultManager.removeItemAtPath(target, null)
                if (NSFileManager.defaultManager.copyItemAtPath(source.path!!, toPath = target, error = null)) target else null
            }
            dispatch_async(dispatch_get_main_queue()) { onResult(copy?.let(::PlatformUri)) }
        }
    }
}

@Composable
actual fun rememberPickImageLauncher(onResult: (PlatformUri?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val delegate = remember { ImagePickerDelegate { uri -> callback.value(uri) } }
    return remember(delegate) {
        {
            val configuration = PHPickerConfiguration()
            configuration.filter = PHPickerFilter.imagesFilter
            configuration.selectionLimit = 1
            val picker = PHPickerViewController(configuration)
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/** The view controller currently on top, to present system sheets from. */
fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}

@Composable
actual fun ProvidePlatformLocale(locale: AppLocale, content: @Composable () -> Unit) = content()

@Composable
actual fun PlatformSystemBarsEffect(darkPalette: Boolean) {
    IosStatusBar.dark = !darkPalette
}

/** The status bar style the host view controller reports; set by the app's palette. */
object IosStatusBar {
    var dark: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            onChange?.invoke()
        }
    var onChange: (() -> Unit)? = null
}

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

actual fun PlatformContext.is24HourFormat(): Boolean {
    val format = NSDateFormatter.dateFormatFromTemplate("j", options = 0u, locale = NSLocale.currentLocale) ?: return true
    return !format.contains('a')
}

actual fun PlatformContext.systemLocale(): AppLocale = com.kgs.calendar.time.defaultAppLocale()

private val dateTimePatterns = com.kgs.calendar.platform.LockedMap<String, String>()

actual fun bestDateTimePattern(locale: AppLocale, skeleton: String): String =
    dateTimePatterns.getOrPut("${locale.toLanguageTag()}|$skeleton") {
        NSDateFormatter.dateFormatFromTemplate(skeleton, options = 0u, locale = platform.Foundation.NSLocale(localeIdentifier = locale.toLanguageTag())) ?: skeleton
    }

@Composable
actual fun TransparentNavigationBarEffect() = Unit

actual fun decodeImageFile(path: String): ImageBitmap? {
    val data = NSData.create(contentsOfFile = path) ?: return null
    return decodeImageBitmap(data.toByteArray())
}
