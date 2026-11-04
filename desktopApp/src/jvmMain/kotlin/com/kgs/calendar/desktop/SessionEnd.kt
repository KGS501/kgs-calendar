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
 * Lets Windows close the app when it has to: an installer updating it (through the Restart Manager)
 * or the user signing out. Java's own windows don't answer that request, so a running app (even one
 * only in the notification area) would hold its files and make the update fail. A hidden window of
 * the app's own answers instead and has the app save and quit ([onEnd]); after an update Windows
 * starts it again.
 */
object SessionEnd {
    @Volatile private var procedure: WinUser.WindowProc? = null

    fun install(onEnd: () -> Unit) {
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
                                    onEnd()
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

    @Suppress("FunctionName")
    private interface Restart : Library {
        fun RegisterApplicationRestart(commandLine: WString, flags: Int): Int

        companion object {
            val INSTANCE: Restart = Native.load("kernel32", Restart::class.java)
        }
    }

    private const val CLASS_NAME = "KgsCalendarSessionWindow"
    private const val WM_QUERYENDSESSION = 0x0011
    private const val WM_ENDSESSION = 0x0016

    /** Restarted after an update only: not after a crash, a hang or a reboot. */
    private const val RESTART_ONLY_FOR_UPDATES = 1 or 2 or 8
}
