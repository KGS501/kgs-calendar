package com.kgs.calendar.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath

/** Settings in `kgs_settings.preferences_pb` under [directory] (Application Support). */
fun iosSettingsStore(directory: String): SettingsStore =
    SettingsStore(PreferenceDataStoreFactory.createWithPath(produceFile = { "$directory/kgs_settings.preferences_pb".toPath() }))
