package com.kgs.calendar.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.MainActivity
import com.kgs.calendar.R
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.domain.model.CalendarViewMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The drawer must switch views against the current view, not the one of the first composition:
 * leaving the Agenda for the view the app started in used to leave the drawer closed on the Agenda.
 */
class DrawerViewSwitchInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settingsStore: SettingsStore get() = KgsCalendarApplication.graph(context).settingsStore

    @Before
    fun startInMultipleDays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        runBlocking {
            settingsStore.setWelcomeCompleted(true)
            settingsStore.markExactAlarmPromptShown()
            settingsStore.setSelectedView(CalendarViewMode.ThreeDay)
        }
    }

    @Test
    fun drawerLeavesTheAgendaForTheViewTheAppStartedIn() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val menu = context.getString(R.string.menu)
            composeRule.waitUntil(timeoutMillis = 30_000) {
                composeRule.onAllNodesWithContentDescription(menu).fetchSemanticsNodes().isNotEmpty()
            }
            val multipleDays = context.getString(
                CalendarViewMode.ThreeDay.labelRes(runBlocking { settingsStore.weekViewEnabled.first() }),
            )
            repeat(2) {
                selectInDrawer(context.getString(R.string.agenda))
                awaitSelectedView(CalendarViewMode.Agenda)
                selectInDrawer(multipleDays)
                awaitSelectedView(CalendarViewMode.ThreeDay)
            }
        }
    }

    private fun selectInDrawer(label: String) {
        composeRule.onNodeWithContentDescription(context.getString(R.string.menu)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()
    }

    private fun awaitSelectedView(viewMode: CalendarViewMode) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { settingsStore.selectedView.first() } == viewMode
        }
    }
}
