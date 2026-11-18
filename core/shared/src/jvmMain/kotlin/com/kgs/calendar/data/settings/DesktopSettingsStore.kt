package com.kgs.calendar.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
import java.io.File

/** The desktop app's settings in [file], a preferences DataStore like on Android and iOS. */
fun desktopSettingsStore(file: File): SettingsStore {
    file.parentFile?.mkdirs()
    return SettingsStore(PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() }))
}
