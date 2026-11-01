package com.kgs.calendar.ui.platform

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.isImeVisible
import androidx.core.view.WindowCompat
import java.util.Locale

@Composable
internal actual fun TransparentNavigationBarEffect() {
    val view = LocalView.current
    val navigationBarColor = Color.Transparent.toArgb()
    DisposableEffect(view, navigationBarColor) {
        val window = view.context.findActivity()?.window
        val previousColor = window?.navigationBarColor
        window?.navigationBarColor = navigationBarColor
        onDispose {
            if (previousColor != null) {
                window.navigationBarColor = previousColor
            }
        }
    }
}

@Composable
internal actual fun SystemBarAppearanceEffect(darkPalette: Boolean) {
    val view = LocalView.current
    SideEffect {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkPalette
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkPalette
            window.navigationBarColor = Color.Transparent.toArgb()
        }
    }
}

@Composable
internal actual fun systemLocale(): Locale =
    LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()

@Composable
internal actual fun ProvidePlatformLocale(locale: Locale, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val configuration = LocalConfiguration.current
    val localizedContext = remember(baseContext, locale) { baseContext.withAppLocale(locale) }
    val localizedConfiguration = remember(configuration, locale) {
        Configuration(configuration).apply { setLocale(locale) }
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
        content = content,
    )
}

internal fun Context.withAppLocale(locale: Locale): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    val localizedContext = createConfigurationContext(config)
    return object : ContextWrapper(this) {
        override fun getAssets(): AssetManager = localizedContext.assets
        override fun getResources(): Resources = localizedContext.resources
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal actual fun isSoftKeyboardVisible(): Boolean =
    androidx.compose.foundation.layout.WindowInsets.isImeVisible
