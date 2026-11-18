package com.kgs.calendar.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import kotlin.concurrent.thread

/**
 * Lets Windows close the app when it has to: shutting down, restarting, signing out, or an installer
 * updating it (through the Restart Manager). A hidden window of the app's own answers Windows, saves
 * what the app keeps between runs ([saveState]) and ends the app at once; after an update Windows
 * starts it again.
 *
 * The app's usual way out isn't used here: it waits for the window's thread, which Java holds up
 * while Windows ends the session, so the app hung on and Windows listed it as blocking shutdown.
 */
object SessionEnd {
    @Volatile private var procedure: WinUser.WindowProc? = null

    fun install(saveState: () -> Unit) {
        if (procedure != null || !System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        runCatching { Restart.INSTANCE.RegisterApplicationRestart(WString(""), RESTART_ONLY_FOR_UPDATES) }
        thread(name = "kgs-session", isDaemon = true) {
            runCatching {
                val user32 = User32.INSTANCE
                val module = Kernel32.INSTANCE.GetModuleHandle(null)
                var ending = false
                // An object, not a lambda: JNA calls it through its interface method.
                val windowProcedure = object : WinUser.WindowProc {
                    override fun callback(hwnd: WinDef.HWND?, message: Int, wParam: WinDef.WPARAM?, lParam: WinDef.LPARAM?): WinDef.LRESULT =
                        when (message) {
                            WM_QUERYENDSESSION -> WinDef.LRESULT(1)
                            WM_ENDSESSION -> {
                                if (wParam != null && wParam.toLong() != 0L && !ending) {
                                    ending = true
                                    endNow(saveState)
                                }
                                WinDef.LRESULT(0)
                            }
                            else -> user32.DefWindowProc(hwnd, message, wParam, lParam)
                        }
                }
                procedure = windowProcedure
                val windowClass = WinUser.WNDCLASSEX().apply {
                    hInstance = module
                    lpszClassName = CLASS_NAME
                    lpfnWndProc = windowProcedure
                }
                user32.RegisterClassEx(windowClass)
                // A top-level window that is never shown: Windows asks top-level windows to close.
                user32.CreateWindowEx(0, CLASS_NAME, "KGS Calendar", 0, 0, 0, 0, 0, null, null, module, null)
                    ?: return@runCatching
                val message = WinUser.MSG()
                while (user32.GetMessage(message, null, 0, 0) > 0) {
                    user32.TranslateMessage(message)
                    user32.DispatchMessage(message)
                }
            }.onFailure { println("KGS session end handling unavailable: $it") }
        }
    }

    /**
     * Windows ends the session as soon as this window has answered, cutting off whatever still
     * runs, so the app saves first, for a moment at most, and then stops on the spot. The database
     * and settings write each change whole, so an interrupted write loses nothing already saved.
     */
    private fun endNow(saveState: () -> Unit) {
        val saving = thread(name = "kgs-session-save", isDaemon = true) { runCatching(saveState) }
        saving.join(SAVE_TIMEOUT_MS)
        ClassDataArchive.stopBuilding()
        Runtime.getRuntime().halt(0)
    }

    @Suppress("FunctionName")
    private interface Restart : Library {
        fun RegisterApplicationRestart(commandLine: WString, flags: Int): Int

        companion object {
            val INSTANCE: Restart = Native.load("kernel32", Restart::class.java)
        }
    }

    private const val CLASS_NAME = "KgsCalendarSessionWindow"
    private const val SAVE_TIMEOUT_MS = 1_000L
    private const val WM_QUERYENDSESSION = 0x0011
    private const val WM_ENDSESSION = 0x0016

    /** Restarted after an update only: not after a crash, a hang or a reboot. */
    private const val RESTART_ONLY_FOR_UPDATES = 1 or 2 or 8
}
