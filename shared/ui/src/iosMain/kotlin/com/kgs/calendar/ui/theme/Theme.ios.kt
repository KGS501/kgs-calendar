package com.kgs.calendar.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em

/** iOS has no wallpaper palette; "system" colours fall back to the app's palette. */
@Composable
actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme? = null

/**
 * Material's letter spacing is tuned for Roboto; with SF it makes text run wider than on Android.
 * Skia also skips SF's size-specific tracking that UIKit applies, so the styles get Apple's
 * tracking instead (in em, so it still fits text that sets its own font size).
 */
actual fun platformTypography(): Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.sfTracking(),
        displayMedium = displayMedium.sfTracking(),
        displaySmall = displaySmall.sfTracking(),
        headlineLarge = headlineLarge.sfTracking(),
        headlineMedium = headlineMedium.sfTracking(),
        headlineSmall = headlineSmall.sfTracking(),
        titleLarge = titleLarge.sfTracking(),
        titleMedium = titleMedium.sfTracking(),
        titleSmall = titleSmall.sfTracking(),
        bodyLarge = bodyLarge.sfTracking(),
        bodyMedium = bodyMedium.sfTracking(),
        bodySmall = bodySmall.sfTracking(),
        labelLarge = labelLarge.sfTracking(),
        labelMedium = labelMedium.sfTracking(),
        labelSmall = labelSmall.sfTracking(),
    )
}

/** Apple's SF Pro tracking table (points at size), as a fraction of the size. */
private fun TextStyle.sfTracking(): TextStyle {
    val size = fontSize.value
    val points = when {
        size <= 11f -> 0.06f
        size <= 12f -> 0f
        size <= 13f -> -0.08f
        size <= 14f -> -0.15f
        size <= 15f -> -0.23f
        size <= 16f -> -0.31f
        size <= 17f -> -0.43f
        size <= 20f -> -0.45f
        size <= 22f -> -0.26f
        size <= 24f -> 0.07f
        else -> 0.36f
    }
    return copy(letterSpacing = (points / size).em)
}
