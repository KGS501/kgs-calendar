package com.kgs.calendar.desktop

import com.kgs.calendar.ui.desktop.PaneLayoutPreferences
import java.io.File
import java.util.Properties

/** Keeps the sizes of the sidebar, the pinned task pane and the settings' columns between launches. */
class PaneLayoutStore(private val file: File) {
    fun restore(): PaneLayoutPreferences {
        val values = Properties().apply { if (file.isFile) runCatching { file.inputStream().use(::load) } }
        fun float(key: String, default: Float) = values.getProperty(key)?.toFloatOrNull() ?: default
        return PaneLayoutPreferences(
            sidebarWidthDp = float("sidebarWidth", PaneLayoutPreferences.DefaultSidebarWidthDp),
            tasksPaneWidthDp = float("tasksPaneWidth", PaneLayoutPreferences.DefaultTasksPaneWidthDp),
            tasksPanePinned = values.getProperty("tasksPanePinned") == "true",
            settingsListFraction = float("settingsListFraction", PaneLayoutPreferences.DefaultSettingsListFraction),
            sheetPosition = float("sheetPosition", PaneLayoutPreferences.DefaultSheetPosition),
            yearRowHeightDp = float("yearRowHeight", PaneLayoutPreferences.DefaultYearRowHeightDp),
            monthRowScale = float("monthRowScale", 1f),
            onChanged = ::save,
        )
    }

    private fun save(layout: PaneLayoutPreferences) {
        val values = Properties()
        values["sidebarWidth"] = layout.sidebarWidthDp.toString()
        values["tasksPaneWidth"] = layout.tasksPaneWidthDp.toString()
        values["tasksPanePinned"] = layout.tasksPanePinned.toString()
        values["settingsListFraction"] = layout.settingsListFraction.toString()
        values["sheetPosition"] = layout.sheetPosition.toString()
        values["yearRowHeight"] = layout.yearRowHeightDp.toString()
        values["monthRowScale"] = layout.monthRowScale.toString()
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { values.store(it, null) }
        }
    }
}
