package com.kgs.calendar.desktop

import com.kgs.calendar.ui.platform.DesktopLoginStart
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

/**
 * Opens the app at sign-in through the list Windows itself keeps, for this user only. The entry
 * appears in Settings > Apps > Startup and in Task Manager, and turning it off there shows in the
 * app, so nothing is stored on the app's side.
 */
class WindowsLoginStart private constructor(exe: String) : DesktopLoginStart {
    private val command = "\"$exe\""

    /** Only an entry for this copy counts: one for a copy that has moved wouldn't start this one. */
    override fun isEnabled(): Boolean = runCatching {
        Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RunKey, EntryName) &&
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, RunKey, EntryName).equals(command, ignoreCase = true) &&
            !isTurnedOffInWindows()
    }.getOrDefault(false)

    override fun setEnabled(value: Boolean) {
        // Turning it on here also overrides having turned it off in Windows.
        forgetWindowsSwitch()
        if (value) {
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RunKey, EntryName, command)
        } else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RunKey, EntryName)) {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RunKey, EntryName)
        }
    }

    /** Windows' switch for an entry: its first byte is odd while the user has turned the entry off. */
    private fun isTurnedOffInWindows(): Boolean =
        Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, ApprovedKey, EntryName) &&
            Advapi32Util.registryGetBinaryValue(WinReg.HKEY_CURRENT_USER, ApprovedKey, EntryName).firstOrNull()?.toInt()?.and(1) == 1

    private fun forgetWindowsSwitch() {
        if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, ApprovedKey, EntryName)) {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, ApprovedKey, EntryName)
        }
    }

    companion object {
        private const val RunKey = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        private const val ApprovedKey = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run"
        private const val EntryName = "KGS Calendar"

        /** Null when not running as the packaged app: Windows should then have nothing to start. */
        fun forThisApp(): WindowsLoginStart? =
            System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }?.let(::WindowsLoginStart)
    }
}
