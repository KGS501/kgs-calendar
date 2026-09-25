package com.kgs.calendar.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.kgs.calendar.domain.source.CollectionVisibility
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

class SettingsStoreCollectionVisibilityTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun hiddenItemTypesRoundTripAcrossStoreInstances() = runBlocking {
        val file = File(tempFolder.root, "settings.preferences_pb")
        val (firstScope, firstStore) = store(file)
        firstStore.setCollectionHiddenInViews("hidden", hidden = true)
        firstStore.setCollectionEventsHidden("shared", hidden = true)
        firstStore.setCollectionTasksHidden("team", hidden = true)
        firstStore.setCollectionTasksHidden("other", hidden = true)
        firstStore.setCollectionTasksHidden("other", hidden = false)
        firstScope.cancel()
        scopes.remove(firstScope)

        val (_, reopened) = store(file)

        assertEquals(
            CollectionVisibility(
                hiddenCollectionHrefs = setOf("hidden"),
                eventsHiddenIn = setOf("shared"),
                tasksHiddenIn = setOf("team"),
            ),
            reopened.collectionVisibility.first(),
        )
        assertEquals(setOf("hidden"), reopened.hiddenCollectionHrefs.first())
    }

    @Test
    fun settingsWrittenBeforeTypeHidingOnlyHideWholeCalendars() = runBlocking {
        val file = File(tempFolder.root, "legacy.preferences_pb")
        val (_, store, dataStore) = storeWithDataStore(file)
        dataStore.edit { it[stringSetPreferencesKey("hidden_collection_hrefs")] = setOf("old") }

        assertEquals(
            CollectionVisibility(hiddenCollectionHrefs = setOf("old")),
            store.collectionVisibility.first(),
        )
        assertEquals(setOf("old"), store.hiddenCollectionHrefs.first())
    }

    @Test
    fun hidingATypeDoesNotChangeTheWholeCalendarSetting() = runBlocking {
        val (_, store) = store(File(tempFolder.root, "independent.preferences_pb"))
        store.setCollectionTasksHidden("shared", hidden = true)
        store.setCollectionEventsHidden("team", hidden = true)
        store.setCollectionEventsHidden("team", hidden = false)

        val visibility = store.collectionVisibility.first()
        assertEquals(emptySet<String>(), store.hiddenCollectionHrefs.first())
        assertEquals(emptySet<String>(), visibility.eventsHiddenIn)
        assertEquals(setOf("shared"), visibility.tasksHiddenIn)
    }

    @Test
    fun hidingTheLastVisibleItemTypeHidesTheWholeCalendar() = runBlocking {
        val (_, store) = store(File(tempFolder.root, "both.preferences_pb"))
        store.setCollectionTasksHidden("shared", hidden = true)
        store.setCollectionEventsHidden("shared", hidden = true)

        assertEquals(
            CollectionVisibility(hiddenCollectionHrefs = setOf("shared")),
            store.collectionVisibility.first(),
        )

        store.setCollectionHiddenInViews("shared", hidden = false)
        assertEquals(CollectionVisibility(), store.collectionVisibility.first())
    }

    private fun store(file: File): Pair<CoroutineScope, SettingsStore> =
        storeWithDataStore(file).let { (scope, store, _) -> scope to store }

    private fun storeWithDataStore(file: File): Triple<CoroutineScope, SettingsStore, DataStore<Preferences>> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also(scopes::add)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        return Triple(scope, SettingsStore(dataStore), dataStore)
    }
}
