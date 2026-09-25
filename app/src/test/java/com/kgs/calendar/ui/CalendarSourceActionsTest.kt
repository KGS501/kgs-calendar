package com.kgs.calendar.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kgs.calendar.data.RepositoryHarness
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.sync.SourceCalendarMutationCoordinator
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarSourceActionsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val harness = RepositoryHarness()
    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val transient = CalendarTransientState()

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        harness.close()
    }

    @Test
    fun failedReadOnlySubscriptionIsReportedAsProblemWithoutLeavingASource() = runTest {
        val feedPath = "/feeds/login.ics"
        harness.server.setFeed(feedPath, "<!DOCTYPE html><html><body>Login</body></html>", contentType = "text/html")

        actions(this).addReadOnlyCalendar(harness.server.url(feedPath))

        // Waits in wall-clock time: the repository finishes on Room's and OkHttp's threads, and the first
        // Room/OkHttp use in a fresh test JVM is slow on a loaded machine.
        val message = withContext(Dispatchers.Default) {
            withTimeout(30_000) { transient.message.first { it != null } }
        }!!
        assertTrue(message, message.startsWith("Adding the read-only calendar failed. Source \""))
        assertTrue(message, message.endsWith("URL returned a web page instead of an iCalendar feed."))
        assertTrue(message.isProblemMessage())
        assertTrue(harness.database.accountDao().getAll().none { it.sourceType == SourceType.ReadOnlyUrl })
    }

    private fun actions(scope: CoroutineScope) = CalendarSourceActions(
        scope = scope,
        repository = harness.repository,
        settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) {
                File(tempFolder.root, "settings.preferences_pb")
            },
        ),
        sourceCalendarMutationCoordinator = SourceCalendarMutationCoordinator(
            includeDisabledProviderCalendars = { false },
            fullRefresh = {},
            reconcileLocalState = {},
        ),
        reminderRescheduler = ReminderRescheduler {},
        appLifecycleSignals = object : AppLifecycleSignals {
            override val processForegroundedAt = emptyFlow<Long>()

            override fun registerAndroidCalendarObserverIfPermitted() = Unit
        },
        strings = UiStrings { "string-$it" },
        transient = transient,
    )
}
