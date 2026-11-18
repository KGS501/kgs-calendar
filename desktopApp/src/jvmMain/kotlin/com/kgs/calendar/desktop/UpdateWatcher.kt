package com.kgs.calendar.desktop

import com.kgs.calendar.ui.platform.WindowsPackage
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Makes way for an update, or for uninstalling. Windows' installer can't replace or remove the app's
 * files while it runs, and it doesn't manage to close it (an app in the notification area has no
 * window to ask). So the app watches for Windows Installer working on KGS Calendar itself, a package
 * of it or (as Settings > Apps does) its installed product: then it quits ([onUpdate]) and starts again
 * once the installer is done (or was cancelled), in its new version; not after uninstalling.
 *
 * A package is told by the upgrade code inside it, not by its name: a download can be called anything
 * (GitHub turns the spaces into dots), and if the app stays open the update fails on its locked files.
 */
object UpdateWatcher {
    @Volatile private var started = false

    fun start(onUpdate: () -> Unit) {
        // The Store updates its package itself, and the app's files aren't in the way there.
        if (started || !System.getProperty("os.name").orEmpty().startsWith("Windows") || WindowsPackage.isStoreApp) return
        started = true
        thread(name = "kgs-update-watch", isDaemon = true) {
            while (true) {
                Thread.sleep(POLL_MS)
                val installer = runCatching { findInstaller() }.getOrNull() ?: continue
                restartAfter(installer)
                onUpdate()
                return@thread
            }
        }
    }

    /** A Windows Installer process (msiexec) working on KGS Calendar, if there is one. */
    private fun findInstaller(): ProcessHandle? {
        val installers = ProcessHandle.allProcesses()
            .filter { it.info().command().orElse("").endsWith("msiexec.exe", ignoreCase = true) }
            .toList()
        if (installers.isEmpty()) return null
        val products = installedProducts()
        return installers.firstOrNull { process ->
            val commandLine = commandLine(process.pid()) ?: return@firstOrNull false
            products.any { commandLine.contains(it, ignoreCase = true) } ||
                PackagePath.findAll(commandLine).any { isOurPackage(it.groupValues[1] + it.groupValues[2]) }
        }
    }

    /** Going by the name only where the package can't be read, such as a path relative to the installer. */
    private fun isOurPackage(path: String): Boolean =
        runCatching { upgradeCodeOf(path) }.getOrNull()?.equals(UPGRADE_CODE, ignoreCase = true)
            ?: path.contains("kgs calendar", ignoreCase = true)

    /** The upgrade code in the package at [path], or null if it can't be read. */
    private fun upgradeCodeOf(path: String): String? {
        val database = IntByReference()
        if (Msi.INSTANCE.MsiOpenDatabaseW(WString(path), null, database) != 0) return null
        val view = IntByReference()
        val record = IntByReference()
        try {
            if (Msi.INSTANCE.MsiDatabaseOpenViewW(database.value, WString(UPGRADE_CODE_QUERY), view) != 0) return null
            if (Msi.INSTANCE.MsiViewExecute(view.value, 0) != 0 || Msi.INSTANCE.MsiViewFetch(view.value, record) != 0) return null
            val value = CharArray(GUID_LENGTH + 1)
            val length = IntByReference(value.size)
            if (Msi.INSTANCE.MsiRecordGetStringW(record.value, 1, value, length) != 0) return null
            return String(value, 0, length.value)
        } finally {
            listOf(record.value, view.value, database.value).filter { it != 0 }.forEach(Msi.INSTANCE::MsiCloseHandle)
        }
    }

    /** The product codes (lower case, in braces) of the installed KGS Calendar, from its fixed upgrade code. */
    private fun installedProducts(): List<String> = runCatching {
        buildList {
            var index = 0
            while (true) {
                val code = CharArray(39)
                if (Msi.INSTANCE.MsiEnumRelatedProductsW(WString(UPGRADE_CODE), 0, index++, code) != 0) break
                add(String(code, 0, 38).lowercase())
            }
        }
    }.getOrDefault(emptyList())

    /**
     * Starts the app again once [installer] has ended. The app runs inside its launcher's job, which
     * ends every process started from it when the app quits; Windows' own process service (WMI)
     * starts the waiting helper outside of it. Returns once the helper runs.
     */
    private fun restartAfter(installer: ProcessHandle) {
        val app = System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile } ?: return
        val quotedApp = "'" + app.replace("'", "''") + "'"
        val waiter = "Wait-Process -Id ${installer.pid()} -ErrorAction SilentlyContinue; Start-Sleep -Seconds 2; " +
            "if (Test-Path -LiteralPath $quotedApp) { Start-Process -FilePath $quotedApp }"
        val encoded = Base64.getEncoder().encodeToString(waiter.toByteArray(Charsets.UTF_16LE))
        val launch = "Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = " +
            "'powershell.exe -NoProfile -NonInteractive -WindowStyle Hidden -EncodedCommand $encoded' } | Out-Null"
        runCatching {
            ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", launch)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor(HELPER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    /** The command line of another process of this user (Windows 8.1 and later), or null. */
    private fun commandLine(pid: Long): String? {
        val handle = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid.toInt()) ?: return null
        try {
            val buffer = Memory(BUFFER_SIZE.toLong())
            val needed = IntByReference()
            if (NtDll.INSTANCE.NtQueryInformationProcess(handle, PROCESS_COMMAND_LINE_INFORMATION, buffer, BUFFER_SIZE, needed) != 0) return null
            // A UNICODE_STRING: its length in bytes, then (aligned) the pointer to the characters.
            val length = buffer.getShort(0).toInt() and 0xFFFF
            val characters = buffer.getPointer(Native.POINTER_SIZE.toLong()) ?: return null
            return String(characters.getCharArray(0, length / 2))
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle)
        }
    }

    @Suppress("FunctionName")
    private interface Msi : Library {
        fun MsiEnumRelatedProductsW(upgradeCode: WString, reserved: Int, index: Int, productCode: CharArray): Int

        fun MsiOpenDatabaseW(path: WString, persist: Pointer?, database: IntByReference): Int

        fun MsiDatabaseOpenViewW(database: Int, query: WString, view: IntByReference): Int

        fun MsiViewExecute(view: Int, record: Int): Int

        fun MsiViewFetch(view: Int, record: IntByReference): Int

        fun MsiRecordGetStringW(record: Int, field: Int, value: CharArray, length: IntByReference): Int

        fun MsiCloseHandle(handle: Int): Int

        companion object {
            val INSTANCE: Msi = Native.load("msi", Msi::class.java)
        }
    }

    @Suppress("FunctionName")
    private interface NtDll : Library {
        fun NtQueryInformationProcess(process: WinNT.HANDLE, informationClass: Int, information: Memory, length: Int, returnLength: IntByReference): Int

        companion object {
            val INSTANCE: NtDll = Native.load("ntdll", NtDll::class.java)
        }
    }

    /** The installer's upgrade code (desktopApp/build.gradle.kts), the same for every version. */
    private const val UPGRADE_CODE = "{E37AE039-A4F6-46A7-A7FD-14F524E68795}"
    private const val UPGRADE_CODE_QUERY = "SELECT Value FROM Property WHERE Property='UpgradeCode'"
    private const val GUID_LENGTH = 38

    /** A package in msiexec's command line, quoted or without spaces. */
    private val PackagePath = Regex("""\"([^"]+\.msi)\"|(?<![^\s])([^\s"]+\.msi)""", RegexOption.IGNORE_CASE)

    private const val POLL_MS = 2_500L
    private const val HELPER_TIMEOUT_SECONDS = 15L
    private const val BUFFER_SIZE = 8192
    private const val PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
    private const val PROCESS_COMMAND_LINE_INFORMATION = 60
}
