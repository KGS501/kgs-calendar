package com.kgs.calendar.ui.platform

import androidx.compose.runtime.Composable
import java.util.Locale

/** Keeps the system navigation bar transparent while a sheet is shown (Android). */
@Composable
internal expect fun TransparentNavigationBarEffect()

/** Gives the system bars dark icons on a light palette and light icons on a dark one (Android). */
@Composable
internal expect fun SystemBarAppearanceEffect(darkPalette: Boolean)

/** The language the system is set to, used when the app follows the system language. */
@Composable
internal expect fun systemLocale(): Locale

/** Applies the app's language to platform text such as date pickers and system dialogs. */
@Composable
internal expect fun ProvidePlatformLocale(locale: Locale, content: @Composable () -> Unit)

/** Whether the on-screen keyboard is shown; a desktop keyboard is never on screen. */
@Composable
internal expect fun isSoftKeyboardVisible(): Boolean
