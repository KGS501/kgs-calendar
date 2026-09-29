package com.kgs.calendar.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreSyncIntervalTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun intervalDefaultsToFifteenMinutesAndSurvivesReopening() = runBlocking {
        val file = File(folder.root, "sync.preferences_pb")
        suspend fun useStore(action: suspend (SettingsStore) -> Unit) {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            try { action(SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { file })) }
            finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
        useStore { store ->
            assertEquals(15, store.syncIntervalMinutes.first())
            store.setSyncIntervalMinutes(60)
        }
        useStore { assertEquals(60, it.syncIntervalMinutes.first()) }
    }
}
