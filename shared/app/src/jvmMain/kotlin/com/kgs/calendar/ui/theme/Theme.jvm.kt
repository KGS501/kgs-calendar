package com.kgs.calendar.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/** Windows' accent color, as chosen in Settings → Personalization → Colors. */
@Composable
internal actual fun systemDynamicColorScheme(darkTheme: Boolean): ColorScheme? {
    val accent = remember { windowsAccentColor() }
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.withSystemAccent(accent, darkTheme)
}

/** The DWM accent color is stored as 0xAABBGGRR. */
private fun windowsAccentColor(): Color? = runCatching {
    val abgr = Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\DWM", "AccentColor")
    Color(red = abgr and 0xFF, green = (abgr shr 8) and 0xFF, blue = (abgr shr 16) and 0xFF)
}.getOrNull()
