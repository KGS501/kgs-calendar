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
    val dynamicColors = if (themeMode == AppThemeMode.SystemDynamic) systemDynamicColorScheme(darkTheme) else null
    SharedCalendarTheme(themeMode, darkTheme, priorityAnimationsEnabled, dynamicColors, content)
}

/** The system's own colors (Android's wallpaper colors, Windows' accent color), or null where there are none. */
@Composable
internal expect fun systemDynamicColorScheme(darkTheme: Boolean): ColorScheme?

internal fun ColorScheme.withSystemAccent(systemAccent: Color?, darkTheme: Boolean): ColorScheme {
    // The system hands us a fairly muted accent; nudge the saturation up a touch so the app's accent
    // reads as a real colour rather than a washed-out grey-tint.
    val accent = (systemAccent ?: primary).forThemeContrast(darkTheme).saturate(1.18f)
    return copy(
        primary = accent,
        onPrimary = if (accent.luminance() > 0.55f) Color(0xFF1C1A18) else Color.White,
        secondary = accent,
        tertiary = accent,
        background = if (darkTheme) Color(0xFF181816) else accent.blend(Color.White, 0.86f),
        surface = if (darkTheme) Color(0xFF22211F) else Color.White,
        surfaceVariant = if (darkTheme) Color(0xFF302E2B) else accent.blend(Color.White, 0.95f),
        // Lift greyed/secondary text in dark mode so it doesn't read as too dim.
        onSurfaceVariant = if (darkTheme) onSurfaceVariant.blend(Color.White, 0.2f) else onSurfaceVariant,
        outline = if (darkTheme) Color(0xFF5E5650) else accent.blend(Color.White, 0.88f),
    )
}

private fun Color.forThemeContrast(darkTheme: Boolean): Color = when {
    !darkTheme && luminance() > 0.45f -> blend(Color(0xFF1C1A18), 0.34f)
    darkTheme && luminance() < 0.5f -> blend(Color.White, 0.36f)
    else -> this
}

/** Scales saturation around the perceived grey. factor > 1 makes the colour more vivid. */
private fun Color.saturate(factor: Float): Color {
    val gray = 0.299f * red + 0.587f * green + 0.114f * blue
    fun ch(c: Float) = (gray + (c - gray) * factor).coerceIn(0f, 1f)
    return Color(ch(red), ch(green), ch(blue), alpha)
}

private fun Color.blend(target: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * t,
        green = green + (target.green - green) * t,
        blue = blue + (target.blue - blue) * t,
        alpha = alpha,
    )
}
