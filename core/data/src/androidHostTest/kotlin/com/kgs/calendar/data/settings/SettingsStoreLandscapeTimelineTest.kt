package com.kgs.calendar.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreLandscapeTimelineTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun landscapeAllDayVisibilitySurvivesReopeningInBothStates() = runBlocking {
        val file = File(tempFolder.root, "landscape.preferences_pb")
        val (firstScope, firstStore) = store(file)
        assertEquals(false, firstStore.landscapeTimelineCompact.first())

        firstStore.setLandscapeTimelineCompact(true)
        // Wait until the first DataStore has released the file before opening it again.
        firstScope.coroutineContext[Job]!!.cancelAndJoin()
        scopes.remove(firstScope)

        val (secondScope, reopened) = store(file)
        assertEquals(true, reopened.landscapeTimelineCompact.first())
        reopened.setLandscapeTimelineCompact(false)
        secondScope.coroutineContext[Job]!!.cancelAndJoin()
        scopes.remove(secondScope)
        val (_, reopenedAgain) = store(file)
        assertEquals(false, reopenedAgain.landscapeTimelineCompact.first())
    }

    private fun store(file: File): Pair<CoroutineScope, SettingsStore> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also(scopes::add)
        return scope to SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }
}
