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

class SettingsStoreHapticFeedbackTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun hapticFeedbackIsOnByDefaultAndStaysOffOnceTurnedOff() = runBlocking {
        val file = File(tempFolder.root, "haptics.preferences_pb")
        val (firstScope, firstStore) = store(file)
        assertEquals(true, firstStore.hapticFeedbackEnabled.first())

        firstStore.setHapticFeedbackEnabled(false)
        firstScope.cancel()
        scopes.remove(firstScope)

        val (_, reopened) = store(file)
        assertEquals(false, reopened.hapticFeedbackEnabled.first())
        reopened.setHapticFeedbackEnabled(true)
        assertEquals(true, reopened.hapticFeedbackEnabled.first())
    }

    private fun store(file: File): Pair<CoroutineScope, SettingsStore> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also(scopes::add)
        return scope to SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }
}
