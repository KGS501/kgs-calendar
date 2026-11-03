package com.kgs.calendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kgs.calendar.data.settings.AppThemeMode

@Composable
fun KgsCalendarTheme(
    themeMode: AppThemeMode = AppThemeMode.KgsBlue,
    darkTheme: Boolean = isSystemInDarkTheme(),
    priorityAnimationsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dynamicColors = if (themeMode == AppThemeMode.SystemDynamic) platformDynamicColorScheme(darkTheme) else null
    SharedCalendarTheme(themeMode, darkTheme, priorityAnimationsEnabled, dynamicColors, content)
}

/** The system's wallpaper colours (Android 12+), or null where there are none. */
@Composable
expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?


/** The text styles for the platform's UI font: Material's defaults on Android (Roboto). */
expect fun platformTypography(): androidx.compose.material3.Typography
