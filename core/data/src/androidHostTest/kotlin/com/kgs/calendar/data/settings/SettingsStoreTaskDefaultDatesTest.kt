package com.kgs.calendar.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreTaskDefaultDatesTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun newTasksHaveNoDateByDefault() = runBlocking {
        val store = store("fresh") {}
        assertEquals(TaskDefaultDates.None, store.defaultTaskDates.first())
        assertEquals(false, store.defaultTaskHasTime.first())
    }

    @Test
    fun theOldDateOnlyChoiceBecomesAStartDate() = runBlocking {
        val store = store("dateOnly") { it[booleanPreferencesKey("default_task_has_date")] = true }
        assertEquals(TaskDefaultDates.Start, store.defaultTaskDates.first())
        assertEquals(false, store.defaultTaskHasTime.first())
    }

    @Test
    fun theOldDateAndTimeChoiceBecomesStartAndDueWithTimes() = runBlocking {
        val store = store("dateTime") {
            it[booleanPreferencesKey("default_task_has_date")] = true
            it[booleanPreferencesKey("default_task_has_time")] = true
        }
        assertEquals(TaskDefaultDates.StartAndDue, store.defaultTaskDates.first())
        assertEquals(true, store.defaultTaskHasTime.first())
    }

    @Test
    fun aChosenDateSetWinsOverTheOldFlags() = runBlocking {
        val store = store("chosen") { it[booleanPreferencesKey("default_task_has_date")] = true }
        store.setDefaultTaskDates(TaskDefaultDates.Due)
        store.setDefaultTaskHasTime(true)
        assertEquals(TaskDefaultDates.Due, store.defaultTaskDates.first())
        assertEquals(true, store.defaultTaskHasTime.first())
    }

    private suspend fun store(
        name: String,
        legacy: (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ): SettingsStore {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also(scopes::add)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tempFolder.root, "$name.preferences_pb") }
        dataStore.edit(legacy)
        return SettingsStore(dataStore)
    }
}
