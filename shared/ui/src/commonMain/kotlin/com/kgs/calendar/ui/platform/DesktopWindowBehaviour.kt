package com.kgs.calendar.ui.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** How the desktop app's window behaves; the desktop keeps it between launches. */
class DesktopWindowBehaviour(
    closeToTray: Boolean = true,
    private val onChanged: (DesktopWindowBehaviour) -> Unit = {},
) {
    /**
     * Whether closing the window keeps the app running in the notification area (so reminders still
     * appear and it opens again at once), rather than quitting it.
     */
    var closeToTray by mutableStateOf(closeToTray)
        private set

    fun updateCloseToTray(value: Boolean) {
        if (value == closeToTray) return
        closeToTray = value
        onChanged(this)
    }
}

/** The window's behaviour where the app has a desktop window; null elsewhere. */
val LocalDesktopWindowBehaviour = staticCompositionLocalOf<DesktopWindowBehaviour?> { null }
