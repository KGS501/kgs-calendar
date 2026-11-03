package com.kgs.calendar.data.settings

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

private val Context.dataStore by preferencesDataStore(name = "kgs_settings")

/** The app's settings in the `kgs_settings` DataStore. */
fun SettingsStore(context: Context): SettingsStore = SettingsStore(context.dataStore)
