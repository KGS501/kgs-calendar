package com.kgs.calendar.ui.theme

import android.content.Context
import android.os.Build
import android.util.TypedValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.kgs.calendar.data.settings.AppThemeMode

private val LightColors: ColorScheme = lightColorScheme(
    primary = KgsPrimary,
    onPrimary = KgsOnPrimary,
    secondary = Color(0xFF2F5E9E),
    onSecondary = Color.White,
    tertiary = Color(0xFFB64E42),
    background = Color(0xFFEEF6FF),
    onBackground = KgsText,
    surface = KgsSurface,
    onSurface = KgsText,
    surfaceVariant = Color(0xFFF1F4F8),
    onSurfaceVariant = KgsMuted,
    outline = KgsOutline,
)

private val BlueLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF2563A8),
    onPrimary = Color.White,
    secondary = Color(0xFF4FA7BD),
    onSecondary = Color.White,
    tertiary = Color(0xFF7C6ED6),
    background = Color(0xFFEDF6FF),
    onBackground = Color(0xFF17202A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17202A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF526173),
    outline = Color(0xFFD7E0E9),
)

private val BlueDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF9BCAFF),
    onPrimary = Color(0xFF003257),
    secondary = Color(0xFF89D4E5),
    onSecondary = Color(0xFF003640),
    tertiary = Color(0xFFC8BFFF),
    background = Color(0xFF101923),
    onBackground = Color(0xFFE6EEF7),
    surface = Color(0xFF172230),
    onSurface = Color(0xFFE6EEF7),
    surfaceVariant = Color(0xFF243444),
    onSurfaceVariant = Color(0xFFD6E0EC),
    outline = Color(0xFF41566B),
)

private val WarmColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF9E572B),
    onPrimary = Color.White,
    secondary = Color(0xFF56B0A2),
    onSecondary = Color.White,
    tertiary = Color(0xFFB184C2),
    background = Color(0xFFFFEFE8),
    onBackground = Color(0xFF31231F),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF31231F),
    surfaceVariant = Color(0xFFF8E6DF),
    onSurfaceVariant = Color(0xFF675750),
    outline = Color(0xFFEAD6CE),
)

private val WarmDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB68B),
    onPrimary = Color(0xFF552600),
    secondary = Color(0xFF84D8CA),
    onSecondary = Color(0xFF003731),
    tertiary = Color(0xFFD8B0E6),
    background = Color(0xFF211815),
    onBackground = Color(0xFFF5DED5),
    surface = Color(0xFF2B211E),
    onSurface = Color(0xFFF5DED5),
    surfaceVariant = Color(0xFF3B2C27),
    onSurfaceVariant = Color(0xFFEFDAD1),
    outline = Color(0xFF6B534B),
)

private val FreshLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0E7C66),
    onPrimary = Color.White,
    secondary = Color(0xFF3D7AA8),
    onSecondary = Color.White,
    tertiary = Color(0xFFE29D3E),
    background = Color(0xFFECF8F4),
    onBackground = Color(0xFF17221F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17221F),
    surfaceVariant = Color(0xFFF0F6F4),
    onSurfaceVariant = Color(0xFF53635E),
    outline = Color(0xFFD5E2DE),
)

private val FreshDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF76DCC4),
    onPrimary = Color(0xFF00382D),
    secondary = Color(0xFFA7D0F0),
    onSecondary = Color(0xFF07324F),
    tertiary = Color(0xFFFFC37C),
    background = Color(0xFF101C19),
    onBackground = Color(0xFFE2F1EC),
    surface = Color(0xFF172622),
    onSurface = Color(0xFFE2F1EC),
    surfaceVariant = Color(0xFF233832),
    onSurfaceVariant = Color(0xFFD6E7E1),
    outline = Color(0xFF3D5A52),
)

@Composable
fun KgsCalendarTheme(
    themeMode: AppThemeMode = AppThemeMode.KgsBlue,
    darkTheme: Boolean = isSystemInDarkTheme(),
    priorityAnimationsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        themeMode == AppThemeMode.SystemDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            (if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
                .withSystemAccent(context, darkTheme)
        themeMode == AppThemeMode.KgsWarm -> if (darkTheme) WarmDarkColors else WarmColors
        themeMode == AppThemeMode.KgsFresh -> if (darkTheme) FreshDarkColors else FreshLightColors
        themeMode == AppThemeMode.KgsBlue -> if (darkTheme) BlueDarkColors else BlueLightColors
        else -> LightColors
    }.withNeutralPopupSurfaces(darkTheme)
    val calendarUiTokens = remember(colors, themeMode, priorityAnimationsEnabled) {
        CalendarUiTokens.forTheme(colors, themeMode, priorityAnimationsEnabled)
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
    ) {
        CompositionLocalProvider(LocalCalendarUiTokens provides calendarUiTokens, content = content)
    }
}

private fun ColorScheme.withNeutralPopupSurfaces(darkTheme: Boolean): ColorScheme {
    val popupSurface = if (darkTheme) Color(0xFF202124) else Color.White
    val raisedSurface = if (darkTheme) Color(0xFF303134) else Color(0xFFF1F3F4)
    return copy(
        surfaceContainerLowest = popupSurface,
        surfaceContainerLow = popupSurface,
        surfaceContainer = popupSurface,
        surfaceContainerHigh = popupSurface,
        surfaceContainerHighest = raisedSurface,
    )
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
