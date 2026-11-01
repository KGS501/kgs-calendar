package com.kgs.calendar.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference

/**
 * Colors the native Windows 11 title bar and border like the app, so the window reads as one
 * surface while keeping Windows' own caption buttons, snap layouts and accessibility. Earlier
 * Windows versions ignore the attributes and keep their default title bar.
 */
object WindowsChrome {
    private interface DwmApi : Library {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: IntByReference, size: Int): Int
    }

    private val dwm: DwmApi? = runCatching { Native.load("dwmapi", DwmApi::class.java) }.getOrNull()

    fun apply(window: java.awt.Window, background: Color, content: Color, dark: Boolean) {
        val api = dwm ?: return
        val pointer = runCatching { Native.getWindowPointer(window) }.getOrNull() ?: return
        val hwnd = WinDef.HWND(pointer)
        fun set(attribute: Int, value: Int) {
            api.DwmSetWindowAttribute(hwnd, attribute, IntByReference(value), Int.SIZE_BYTES)
        }
        set(DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0)
        set(DWMWA_CAPTION_COLOR, background.colorRef())
        set(DWMWA_BORDER_COLOR, background.colorRef())
        set(DWMWA_TEXT_COLOR, content.colorRef())
    }

    /** COLORREF is 0x00BBGGRR. */
    private fun Color.colorRef(): Int {
        val argb = toArgb()
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return (blue shl 16) or (green shl 8) or red
    }

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36
}
