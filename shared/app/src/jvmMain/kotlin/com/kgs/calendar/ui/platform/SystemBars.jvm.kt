package com.kgs.calendar.ui.platform

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import java.util.Locale

/** The system language as it was at startup, before the app applied its own language setting. */
private val startupLocale: Locale = Locale.getDefault()

@Composable
internal actual fun TransparentNavigationBarEffect() = Unit

/** Receives the app's background and text colors, so the window's title bar can match the app. */
val LocalWindowChrome = staticCompositionLocalOf<(background: Color, content: Color, dark: Boolean) -> Unit> { { _, _, _ -> } }

@Composable
internal actual fun SystemBarAppearanceEffect(darkPalette: Boolean) {
    val chrome = LocalWindowChrome.current
    val background = MaterialTheme.colorScheme.background
    val content = MaterialTheme.colorScheme.onBackground
    SideEffect { chrome(background, content, darkPalette) }
}

@Composable
internal actual fun systemLocale(): Locale = startupLocale

@Composable
internal actual fun ProvidePlatformLocale(locale: Locale, content: @Composable () -> Unit) = content()

@Composable
internal actual fun isSoftKeyboardVisible(): Boolean = false
