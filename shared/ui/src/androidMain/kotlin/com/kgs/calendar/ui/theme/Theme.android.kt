package com.kgs.calendar.ui.theme

import android.content.Context
import android.os.Build
import android.util.TypedValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.kgs.calendar.data.settings.AppThemeMode

@Composable
actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return (if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
        .withSystemAccent(context, darkTheme)
}

private fun ColorScheme.withSystemAccent(context: Context, darkTheme: Boolean): ColorScheme {
    // Android hands us a fairly muted accent; nudge the saturation up a touch so the app's accent
    // reads as a real colour rather than a washed-out grey-tint.
    val accent = (context.systemAccentColor(darkTheme) ?: primary).forThemeContrast(darkTheme).saturate(1.18f)
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

private fun Context.systemAccentColor(darkTheme: Boolean): Color? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val preferred = if (darkTheme) "system_accent1_200" else "system_accent1_600"
        val fallback = if (darkTheme) "system_accent1_300" else "system_accent1_500"
        listOf(preferred, fallback).forEach { name ->
            val id = resources.getIdentifier(name, "color", "android")
            if (id != 0) {
                val systemColor = runCatching { Color(getColor(id)) }.getOrNull()
                if (systemColor != null) return systemColor
            }
        }
    }
    val typedValue = TypedValue()
    return if (theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)) {
        Color(typedValue.data)
    } else {
        null
    }
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

actual fun platformTypography(): androidx.compose.material3.Typography = androidx.compose.material3.Typography()
