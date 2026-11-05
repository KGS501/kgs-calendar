package com.kgs.calendar.desktop

import com.kgs.calendar.ui.platform.WindowsPackage
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.concurrent.thread

/**
 * Reminders as real Windows notifications. The tray icon's balloon is shown for a moment or not at
 * all: under Do not disturb, which Windows also turns on by itself during presentations and in full
 * screen, it is lost without a trace. These stay in the notification centre, and a click opens the app.
 *
 * The Store package announces the app to Windows by itself. The installer's app does it in the
 * user's registry, as the installer can't, so its notifications show its name and icon.
 */
class WindowsNotifications private constructor(private val appId: String) {
    /** Shows the notification, or calls [onFailed] if Windows wouldn't. */
    fun show(title: String, body: String, onFailed: () -> Unit) {
        thread(name = "kgs-notification", isDaemon = true) {
            runCatching { WinRt.showToast(appId, toastXml(title, body)) }.onFailure {
                println("KGS notification not shown: $it")
                onFailed()
            }
        }
    }

    companion object {
        private const val AppId = "KGS501.KGSCalendar"
        private const val Protocol = "kgs-calendar"
        private const val AppKey = "Software\\Classes\\AppUserModelId\\$AppId"
        private const val ProtocolKey = "Software\\Classes\\$Protocol"

        /** Null when not running as the packaged app, which is what a click on a notification starts. */
        fun forThisApp(icon: () -> BufferedImage, iconFile: File): WindowsNotifications? {
            WindowsPackage.appUserModelId?.let { return WindowsNotifications(it) }
            val exe = System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile } ?: return null
            register(exe, icon(), iconFile)
            return WindowsNotifications(AppId)
        }

        private fun register(exe: String, icon: BufferedImage, iconFile: File) {
            runCatching {
                iconFile.parentFile?.mkdirs()
                ImageIO.write(icon, "png", iconFile)
                val root = WinReg.HKEY_CURRENT_USER
                Advapi32Util.registryCreateKey(root, AppKey)
                Advapi32Util.registrySetStringValue(root, AppKey, "DisplayName", "KGS Calendar")
                Advapi32Util.registrySetStringValue(root, AppKey, "IconUri", iconFile.absolutePath)
                // A click on a notification starts the app, which then just shows the running one.
                Advapi32Util.registryCreateKey(root, "$ProtocolKey\\shell\\open\\command")
                Advapi32Util.registrySetStringValue(root, ProtocolKey, "", "URL:KGS Calendar")
                Advapi32Util.registrySetStringValue(root, ProtocolKey, "URL Protocol", "")
                Advapi32Util.registrySetStringValue(root, "$ProtocolKey\\shell\\open\\command", "", "\"$exe\" \"%1\"")
            }
        }

        internal fun toastXml(title: String, body: String): String {
            val lines = listOf(title, body).filter { it.isNotBlank() }.joinToString("") { "<text>${escaped(it)}</text>" }
            return "<toast activationType=\"protocol\" launch=\"$Protocol://open\"><visual><binding template=\"ToastGeneric\">$lines</binding></visual></toast>"
        }

        private fun escaped(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
