package com.kgs.calendar.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/** Windows' accent colour, as chosen in Settings → Personalization → Colors. */
@Composable
actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme? {
    val accent = remember { windowsAccentColor() } ?: return null
    return (if (darkTheme) darkColorScheme() else lightColorScheme()).withSystemAccent(accent, darkTheme)
}

/** Roboto, the Android app's font, with Windows-like subpixel smoothing (see AppFont.kt). */
actual fun platformTypography(): Typography = Typography().run {
    fun androidx.compose.ui.text.TextStyle.desktop() = copy(fontFamily = RobotoFontFamily, platformStyle = DesktopPlatformTextStyle)
    Typography(
        displayLarge = displayLarge.desktop(),
        displayMedium = displayMedium.desktop(),
        displaySmall = displaySmall.desktop(),
        headlineLarge = headlineLarge.desktop(),
        headlineMedium = headlineMedium.desktop(),
        headlineSmall = headlineSmall.desktop(),
        titleLarge = titleLarge.desktop(),
        titleMedium = titleMedium.desktop(),
        titleSmall = titleSmall.desktop(),
        bodyLarge = bodyLarge.desktop(),
        bodyMedium = bodyMedium.desktop(),
        bodySmall = bodySmall.desktop(),
        labelLarge = labelLarge.desktop(),
        labelMedium = labelMedium.desktop(),
        labelSmall = labelSmall.desktop(),
    )
}

/** The DWM accent colour is stored as 0xAABBGGRR. */
private fun windowsAccentColor(): Color? = runCatching {
    val abgr = Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\DWM", "AccentColor")
    Color(red = abgr and 0xFF, green = (abgr shr 8) and 0xFF, blue = (abgr shr 16) and 0xFF)
}.getOrNull()

/** As Android's system palette: the accent, a touch more vivid, on the app's neutral surfaces. */
private fun ColorScheme.withSystemAccent(systemAccent: Color, darkTheme: Boolean): ColorScheme {
    val accent = systemAccent.forThemeContrast(darkTheme).saturate(1.18f)
    return copy(
        primary = accent,
        onPrimary = if (accent.luminance() > 0.55f) Color(0xFF1C1A18) else Color.White,
        secondary = accent,
        tertiary = accent,
        background = if (darkTheme) Color(0xFF181816) else accent.blend(Color.White, 0.86f),
        surface = if (darkTheme) Color(0xFF22211F) else Color.White,
        surfaceVariant = if (darkTheme) Color(0xFF302E2B) else accent.blend(Color.White, 0.95f),
        onSurfaceVariant = if (darkTheme) onSurfaceVariant.blend(Color.White, 0.2f) else onSurfaceVariant,
        outline = if (darkTheme) Color(0xFF5E5650) else accent.blend(Color.White, 0.88f),
    )
}

private fun Color.forThemeContrast(darkTheme: Boolean): Color = when {
    !darkTheme && luminance() > 0.45f -> blend(Color(0xFF1C1A18), 0.34f)
    darkTheme && luminance() < 0.5f -> blend(Color.White, 0.36f)
    else -> this
}

private fun Color.saturate(factor: Float): Color {
    val gray = 0.299f * red + 0.587f * green + 0.114f * blue
    fun ch(c: Float) = (gray + (c - gray) * factor).coerceIn(0f, 1f)
    return Color(ch(red), ch(green), ch(blue), alpha)
}

private fun Color.blend(target: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(red + (target.red - red) * t, green + (target.green - green) * t, blue + (target.blue - blue) * t, alpha)
}
