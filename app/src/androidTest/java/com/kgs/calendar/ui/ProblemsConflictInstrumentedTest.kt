package com.kgs.calendar.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.kgs.calendar.R
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProblemsConflictInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aConflictOffersBothVersionsAndOtherProblemsDoNot() {
        val choices = mutableListOf<Pair<String, Boolean>>()
        composeRule.setContent {
            KgsCalendarTheme {
                ProblemsPage(
                    problems = listOf(
                        ProblemItem("conflict", "Event: Planning", "Changed elsewhere", conflictResourceHref = "/cal/planning.ics"),
                        ProblemItem("other", "Event: Lunch", "Something else"),
                    ),
                    onEventClick = {},
                    onTaskClick = {},
                    onClose = {},
                    onResolveConflict = { href, keepLocal -> choices += href to keepLocal },
                )
            }
        }
        val keepMine = context.getString(R.string.sync_conflict_keep_mine)
        val useOther = context.getString(R.string.sync_conflict_use_server)

        // Only the conflict carries the two choices.
        composeRule.onAllNodesWithText(keepMine).assertCountEquals(1)
        composeRule.onNodeWithText(keepMine).performClick()
        composeRule.onNodeWithText(useOther).performClick()

        assertEquals(listOf("/cal/planning.ics" to true, "/cal/planning.ics" to false), choices)
    }
}
