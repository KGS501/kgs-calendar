package com.kgs.calendar.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

class EditorRecreationInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun skipStartupPrompts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val settingsStore = KgsCalendarApplication.graph(context).settingsStore
        runBlocking {
            settingsStore.setWelcomeCompleted(true)
            settingsStore.markExactAlarmPromptShown()
        }
    }

    @Test
    fun eventEditorAndTypedTitleSurviveActivityRecreation() {
        val title = "Rotation draft ${System.currentTimeMillis()}"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val create = context.getString(R.string.create)
            composeRule.waitUntil(timeoutMillis = 30_000) {
                composeRule.onAllNodesWithContentDescription(create).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription(create).performClick()
            composeRule.onAllNodesWithText(context.getString(R.string.event)).onFirst().performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText(context.getString(R.string.new_event)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(title)
            composeRule.waitForIdle()

            scenario.recreate()

            composeRule.waitUntil(timeoutMillis = 30_000) {
                composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithText(context.getString(R.string.new_event)).onFirst().assertExists()
            composeRule.onAllNodesWithText(title).onFirst().assertExists()
        }
    }
}
