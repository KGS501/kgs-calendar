package com.kgs.calendar.ui.platform

import androidx.compose.runtime.Composable
import java.util.Locale

/** The system language as it was at startup, before the app applied its own language setting. */
private val startupLocale: Locale = Locale.getDefault()

@Composable
internal actual fun TransparentNavigationBarEffect() = Unit

@Composable
internal actual fun SystemBarAppearanceEffect(darkPalette: Boolean) = Unit

@Composable
internal actual fun systemLocale(): Locale = startupLocale

@Composable
internal actual fun ProvidePlatformLocale(locale: Locale, content: @Composable () -> Unit) = content()

@Composable
internal actual fun isSoftKeyboardVisible(): Boolean = false
