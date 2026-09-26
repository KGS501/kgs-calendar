package com.kgs.calendar.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

class SettingsStoreLargeScreenMultiDayCountTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun largeScreenCountsDefaultToMoreDaysAndIgnoreThePhoneValues() = runBlocking {
        val (_, store) = store(File(tempFolder.root, "defaults.preferences_pb"))
        store.setPortraitMultiDayCount(2)
        store.setLandscapeMultiDayCount(7)

        assertEquals(4, store.largePortraitMultiDayCount.first())
        assertEquals(5, store.largeLandscapeMultiDayCount.first())
    }

    @Test
    fun largeScreenCountsPersistSeparatelyAndAreClamped() = runBlocking {
        val file = File(tempFolder.root, "large.preferences_pb")
        val (firstScope, firstStore) = store(file)
        firstStore.setLargePortraitMultiDayCount(6)
        firstStore.setLargeLandscapeMultiDayCount(42)
        firstScope.cancel()
        scopes.remove(firstScope)

        val (_, reopened) = store(file)
        assertEquals(6, reopened.largePortraitMultiDayCount.first())
        assertEquals(7, reopened.largeLandscapeMultiDayCount.first())
        assertEquals(3, reopened.portraitMultiDayCount.first())
        assertEquals(3, reopened.landscapeMultiDayCount.first())
    }

    private fun store(file: File): Pair<CoroutineScope, SettingsStore> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also(scopes::add)
        return scope to SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }
}
