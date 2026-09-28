package com.kgs.calendar.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.MainActivity
import com.kgs.calendar.R
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CalendarInsertInstrumentedTest {
    @get:Rule val composeRule = createEmptyComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun skipPrompts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        runBlocking {
            val graph = KgsCalendarApplication.graph(context)
            graph.settingsStore.setWelcomeCompleted(true)
            graph.settingsStore.markExactAlarmPromptShown()
            graph.repository.ensureLocalCalendar()
        }
    }

    @Test
    fun coldAndWarmInsertOpenPrefilledDraftAndSurviveRotation() {
        fun insert(title: String) = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, "Exported booking details")
            .putExtra(CalendarContract.Events.EVENT_LOCATION, "Berlin")
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 1791806400000L)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, 1791810000000L)
        fun assertTitle(title: String) {
            composeRule.waitUntil(30_000) {
                composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithText(context.getString(R.string.new_event)).onFirst().assertExists()
        }
        ActivityScenario.launch<MainActivity>(insert("External booking one")).use { scenario ->
            assertTitle("External booking one")
            scenario.recreate()
            assertTitle("External booking one")
            scenario.onActivity { it.startActivity(insert("External booking two")) }
            assertTitle("External booking two")
        }
    }
}
