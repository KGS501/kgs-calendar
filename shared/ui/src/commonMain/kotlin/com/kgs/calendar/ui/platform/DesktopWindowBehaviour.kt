package com.kgs.calendar.ui.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Opening the app when the user signs in, which the operating system itself keeps track of. */
interface DesktopLoginStart {
    fun isEnabled(): Boolean

    fun setEnabled(value: Boolean)
}

/** How the desktop app's window behaves; the desktop keeps it between launches. */
class DesktopWindowBehaviour(
    closeToTray: Boolean = true,
    private val loginStart: DesktopLoginStart? = null,
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

    /** Whether this copy of the app can open at sign-in at all. */
    val canOpenAtLogin: Boolean get() = loginStart != null

    /** As the system has it, so it also follows changes made in the system's own settings. */
    var openAtLogin by mutableStateOf(loginStart?.isEnabled() == true)
        private set

    fun updateOpenAtLogin(value: Boolean) {
        val system = loginStart ?: return
        // If the system refuses, the switch goes back to showing what is really set.
        runCatching { system.setEnabled(value) }
        refreshOpenAtLogin()
    }

    fun refreshOpenAtLogin() {
        openAtLogin = loginStart?.isEnabled() == true
    }
}

/** The window's behaviour where the app has a desktop window; null elsewhere. */
val LocalDesktopWindowBehaviour = staticCompositionLocalOf<DesktopWindowBehaviour?> { null }
