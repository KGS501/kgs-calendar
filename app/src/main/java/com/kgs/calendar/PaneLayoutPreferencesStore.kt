package com.kgs.calendar

import android.content.Context
import com.kgs.calendar.ui.desktop.PaneLayoutPreferences

/**
 * Keeps where the user put the bottom sheets on a large screen and how the settings' two columns
 * are shared, between launches.
 */
internal fun Context.paneLayoutPreferences(): PaneLayoutPreferences {
    val prefs = getSharedPreferences("pane_layout", Context.MODE_PRIVATE)
    return PaneLayoutPreferences(
        settingsListFraction = prefs.getFloat("settings_list_fraction", PaneLayoutPreferences.DefaultSettingsListFraction),
        sheetPosition = prefs.getFloat("sheet_position", PaneLayoutPreferences.DefaultSheetPosition),
        onChanged = { layout ->
            prefs.edit()
                .putFloat("settings_list_fraction", layout.settingsListFraction)
                .putFloat("sheet_position", layout.sheetPosition)
                .apply()
        },
    )
}
