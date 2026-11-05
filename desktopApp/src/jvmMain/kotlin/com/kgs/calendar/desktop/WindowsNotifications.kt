package com.kgs.calendar.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.awt.image.BufferedImage
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.concurrent.thread

/**
 * Reminders as real Windows notifications. The tray icon's balloon is shown for a moment or not at
 * all: under Do not disturb, which Windows also turns on by itself during presentations and in full
 * screen, it is lost without a trace. These stay in the notification centre, and a click opens the app.
 *
 * Windows offers them through its runtime only, which PowerShell can reach and Java can't. The app is
 * announced in the user's registry, as the installer can't do it, so they show its name and icon.
 */
class WindowsNotifications private constructor(exe: String, private val icon: BufferedImage, private val iconFile: File) {
    init {
        register(exe)
    }

    /** Shows the notification, or calls [onFailed] (on another thread) if Windows wouldn't. */
    fun show(title: String, body: String, onFailed: () -> Unit) {
        thread(name = "kgs-notification", isDaemon = true) {
            val shown = runCatching {
                val script = ToastScript.replace("{XML}", toastXml(title, body))
                val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
                val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
                process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0
            }.getOrDefault(false)
            if (!shown) onFailed()
        }
    }

    private fun register(exe: String) {
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

    companion object {
        private const val AppId = "KGS501.KGSCalendar"
        private const val Protocol = "kgs-calendar"
        private const val AppKey = "Software\\Classes\\AppUserModelId\\$AppId"
        private const val ProtocolKey = "Software\\Classes\\$Protocol"
        private const val TIMEOUT_SECONDS = 30L

        private val ToastScript = """
            ${'$'}ErrorActionPreference = 'Stop'
            [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
            [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom, ContentType = WindowsRuntime] | Out-Null
            ${'$'}xml = New-Object Windows.Data.Xml.Dom.XmlDocument
            ${'$'}xml.LoadXml('{XML}')
            ${'$'}toast = [Windows.UI.Notifications.ToastNotification]::new(${'$'}xml)
            [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('$AppId').Show(${'$'}toast)
        """.trimIndent()

        /** Null when not running as the packaged app, which is what a click on a notification starts. */
        fun forThisApp(icon: BufferedImage, iconFile: File): WindowsNotifications? =
            System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }?.let { WindowsNotifications(it, icon, iconFile) }

        /** The notification's XML; it has no single quotes left, as it goes into a PowerShell string. */
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
