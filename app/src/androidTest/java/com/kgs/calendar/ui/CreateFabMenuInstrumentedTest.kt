package com.kgs.calendar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import org.junit.Rule
import org.junit.Test

class CreateFabMenuInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun plusBecomesCloseAsOneContinuousGlyph() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            var expanded by remember { mutableStateOf(false) }
            KgsCalendarTheme(
                themeMode = AppThemeMode.KgsBlue,
                darkTheme = false,
                priorityAnimationsEnabled = false,
            ) {
                CreateFabMenu(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onCreateTask = {},
                    onCreateEvent = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Create").performClick()
        composeRule.mainClock.advanceTimeBy(75)

        composeRule.onAllNodesWithContentDescription("Create").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Close").assertCountEquals(1)
    }
}
