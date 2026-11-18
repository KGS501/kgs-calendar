package com.kgs.calendar.desktop

import com.kgs.calendar.ui.platform.DesktopLoginStart
import java.awt.Desktop
import java.net.URI

/**
 * Opens the Store app at sign-in through the startup task in its package manifest. A Store app can't
 * use the list in the registry, as [WindowsLoginStart] does: what it writes there stays in its own
 * private copy of the registry. As there, Windows keeps the state and shows it under Apps > Startup.
 */
internal class WindowsStoreLoginStart : DesktopLoginStart {
    override fun isEnabled(): Boolean = runCatching {
        WinRt.startupTaskState(TaskId).let { it == WinRt.StartupTaskEnabled || it == WinRt.StartupTaskEnabledByPolicy }
    }.getOrDefault(false)

    override fun setEnabled(value: Boolean) {
        if (!value) {
            WinRt.disableStartupTask(TaskId)
        } else if (WinRt.enableStartupTask(TaskId) == WinRt.StartupTaskDisabledByUser) {
            // Only the user can undo turning it off in Windows' settings, so take them there.
            runCatching { Desktop.getDesktop().browse(URI("ms-settings:startupapps")) }
        }
    }

    private companion object {
        /** The TaskId in desktopApp/msix/AppxManifest.xml. */
        const val TaskId = "KgsCalendarStartup"
    }
}
