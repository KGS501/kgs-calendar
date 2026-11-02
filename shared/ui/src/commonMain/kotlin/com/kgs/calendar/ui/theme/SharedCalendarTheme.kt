package com.kgs.calendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
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

/** The original palettes and motion tokens, used by Android and the iOS host. */
@Composable
fun SharedCalendarTheme(
    themeMode: AppThemeMode = AppThemeMode.KgsBlue,
    darkTheme: Boolean = isSystemInDarkTheme(),
    priorityAnimationsEnabled: Boolean = true,
    dynamicColors: ColorScheme? = null,
    content: @Composable () -> Unit,
) {
    val colors = calendarColorScheme(themeMode, darkTheme, dynamicColors)
    val tokens = remember(colors, themeMode, priorityAnimationsEnabled) {
        CalendarUiTokens.forTheme(colors, themeMode, priorityAnimationsEnabled)
    }
    val fontFamily = LocalAppFontFamily.current
    val typography = remember(fontFamily) { Typography().withFontFamily(fontFamily) }
    MaterialTheme(colorScheme = colors, typography = typography) {
        CompositionLocalProvider(LocalCalendarUiTokens provides tokens, content = content)
    }
}

/**
 * The app's typeface where the platform's own is not the one the app is designed with: the desktop
 * brings Roboto, the font of the Android app. Null keeps the platform's default font.
 */
val LocalAppFontFamily = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.text.font.FontFamily?> { null }

private fun Typography.withFontFamily(family: androidx.compose.ui.text.font.FontFamily?): Typography {
    if (family == null) return this
    return Typography(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )
}

fun calendarColorScheme(themeMode: AppThemeMode, darkTheme: Boolean, dynamicColors: ColorScheme? = null): ColorScheme =
    when (themeMode) {
        AppThemeMode.SystemDynamic -> dynamicColors ?: LightColors
        AppThemeMode.KgsWarm -> if (darkTheme) WarmDarkColors else WarmColors
        AppThemeMode.KgsFresh -> if (darkTheme) FreshDarkColors else FreshLightColors
        AppThemeMode.KgsBlue -> if (darkTheme) BlueDarkColors else BlueLightColors
    }.withNeutralPopupSurfaces(darkTheme)

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
