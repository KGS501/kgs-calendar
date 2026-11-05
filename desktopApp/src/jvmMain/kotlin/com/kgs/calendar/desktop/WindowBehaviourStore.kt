package com.kgs.calendar.desktop

import com.kgs.calendar.ui.platform.DesktopWindowBehaviour
import com.kgs.calendar.ui.platform.WindowsPackage
import java.io.File
import java.util.Properties

/** Keeps how the window behaves (see [DesktopWindowBehaviour]) between launches. */
class WindowBehaviourStore(private val file: File) {
    fun restore(): DesktopWindowBehaviour {
        val values = Properties().apply { if (file.isFile) runCatching { file.inputStream().use(::load) } }
        return DesktopWindowBehaviour(
            closeToTray = values.getProperty("closeToTray") != "false",
            loginStart = if (WindowsPackage.isStoreApp) WindowsStoreLoginStart() else WindowsLoginStart.forThisApp(),
            onChanged = ::save,
        )
    }

    private fun save(behaviour: DesktopWindowBehaviour) {
        val values = Properties()
        values["closeToTray"] = behaviour.closeToTray.toString()
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { values.store(it, null) }
        }
    }
}
