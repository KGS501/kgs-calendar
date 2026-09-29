package com.kgs.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TimelineDayAlignmentInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val date = LocalDate.of(2026, 7, 1)
    private val state = mutableStateOf(CalendarUiState(
        selectedDate = date, selectedView = CalendarViewMode.Day, initialDataLoaded = true,
    ))
    private val generation = mutableIntStateOf(0)
    private val width = mutableStateOf(360.dp)

    @Test
    fun freshDayAndFirstMonthNavigationStayAlignedAfterEveryRestart() {
        showShell()
        repeat(3) {
            assertAligned()
            composeRule.runOnIdle { state.value = state.value.copy(selectedView = CalendarViewMode.Month) }
            composeRule.waitForIdle()
            val dayCell = composeRule.onAllNodesWithText("5", useUnmergedTree = true)
                .fetchSemanticsNodes().first { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
            composeRule.onRoot().performTouchInput { click(dayCell.boundsInRoot.center) }
            assertAligned()
            composeRule.runOnIdle { assertEquals(date.plusDays(4), state.value.selectedDate) }
            composeRule.runOnIdle { generation.intValue++ }
        }
        assertAligned()
    }

    @Test
    fun dayStaysAlignedWhenViewportIsResizedAndWhenReturningFromMultipleDays() {
        showShell()
        assertAligned()
        composeRule.runOnIdle { width.value = 220.dp }
        assertAligned()
        composeRule.runOnIdle { width.value = 360.dp }
        assertAligned()
        composeRule.runOnIdle { state.value = state.value.copy(selectedView = CalendarViewMode.ThreeDay) }
        composeRule.waitForIdle()
        composeRule.onNode(hasText("2") and hasAnyAncestor(hasTestTag("timeline-gesture-surface")),
            useUnmergedTree = true).performClick()
        assertAligned()
        composeRule.runOnIdle { assertEquals(date.plusDays(1), state.value.selectedDate) }
    }

    private fun assertAligned() {
        composeRule.waitForIdle()
        assertEquals("The selected day must start at the viewport edge", 0,
            composeRule.onNodeWithTag("timeline-gesture-surface").fetchSemanticsNode()
                .config[TimelineSelectedDayOffsetPxSemanticsKey])
    }

    private fun showShell() {
        composeRule.setContent {
            KgsCalendarTheme(themeMode = AppThemeMode.KgsBlue, darkTheme = false, priorityAnimationsEnabled = false) {
                Box(Modifier.width(width.value).fillMaxHeight()) {
                    key(generation.intValue) {
                        CalendarShell(
                            state = state.value,
                            onMenu = {},
                            onDateSelected = { state.value = state.value.copy(selectedDate = it) },
                            onViewSelected = { state.value = state.value.copy(selectedView = it) },
                            onMultiDayCountChanged = {},
                            onToday = {},
                            onSearch = {},
                            onTasks = {},
                            onTaskStatusChanged = { _, _ -> },
                            onEventMoved = { _, _, _, _, _ -> },
                            onTaskMoved = { _, _, _, _, _ -> },
                            onEventMovedAllDay = { _, _, _ -> },
                            onTaskMovedAllDay = { _, _, _ -> },
                            onSlotSelected = { _, _ -> },
                            onAllDaySlotSelected = {},
                            draftEvent = null,
                            onDraftEventChanged = {},
                            onDraftInteraction = {},
                            onDraftTap = {},
                            timelineBottomInset = 0.dp,
                            onDetail = {},
                            overdueTasksExpanded = false,
                            onOverdueTasksExpandedChange = {},
                        )
                    }
                }
            }
        }
    }
}
