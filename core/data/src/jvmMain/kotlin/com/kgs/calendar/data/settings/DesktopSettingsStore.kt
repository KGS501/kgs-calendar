package com.kgs.calendar.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
import java.io.File

/** The settings, stored in [file] (a preferences DataStore, like on Android). */
fun SettingsStore(file: File): SettingsStore {
    file.parentFile?.mkdirs()
    return SettingsStore(PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() }))
}
