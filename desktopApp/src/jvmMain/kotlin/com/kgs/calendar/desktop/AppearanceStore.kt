package com.kgs.calendar.desktop

import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppThemeMode
import java.io.File
import java.util.Properties

/**
 * The app's look as last used, read in an instant at startup: the window's startup screen shows in the
 * user's colours before the settings themselves are loaded. The settings stay the truth; this file only
 * follows them.
 */
class AppearanceStore(private val file: File) {
    fun restore(): Pair<AppThemeMode, AppColorMode> {
        val values = Properties().apply { if (file.isFile) runCatching { file.inputStream().use(::load) } }
        val theme = AppThemeMode.entries.firstOrNull { it.name == values.getProperty("theme") } ?: AppThemeMode.KgsBlue
        val color = AppColorMode.entries.firstOrNull { it.name == values.getProperty("color") } ?: AppColorMode.Auto
        return theme to color
    }

    fun save(theme: AppThemeMode, color: AppColorMode) {
        if (restore() == theme to color) return
        val values = Properties()
        values["theme"] = theme.name
        values["color"] = color.name
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { values.store(it, null) }
        }
    }
}
