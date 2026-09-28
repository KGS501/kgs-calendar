package com.kgs.calendar.ui.month

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.CalendarShell
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.time.CalendarTimeSnapshot
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Regression for #28: in the aligned week view the timeline pager rests on the week's first day.
 * Choosing a month in the drop-down selected the 1st, and the pager's late settle on that week's
 * Monday then selected the Monday, which moved the panel back to the previous month.
 */
class CalendarShellMonthOverviewWeekViewInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstMonthSwipeInWeekViewStaysOnTheChosenMonth() {
        // Sunday: its week (7-13 September) does not contain 1 October's week anchor (28 September).
        val start = LocalDate.of(2026, 9, 13)
        val selections = Collections.synchronizedList(mutableListOf<LocalDate>())
        var currentDate = start
        composeRule.setContent {
            var selectedDate by remember { mutableStateOf(start) }
            val portraitConfiguration = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = 400
                screenHeightDp = 900
            }
            CompositionLocalProvider(
                LocalConfiguration provides portraitConfiguration,
                LocalCalendarTimeSnapshot provides CalendarTimeSnapshot(start, LocalTime.NOON),
            ) {
                KgsCalendarTheme(
                    themeMode = AppThemeMode.KgsBlue,
                    darkTheme = false,
                    priorityAnimationsEnabled = false,
                ) {
                    CalendarShell(
                        state = CalendarUiState(
                            selectedDate = selectedDate,
                            selectedView = CalendarViewMode.ThreeDay,
                            firstDayOfWeek = DayOfWeek.MONDAY,
                            weekViewEnabled = true,
                            fullWeekSwipeEnabled = true,
                            priorityAnimationsEnabled = false,
                        ),
                        onMenu = {},
                        onDateSelected = { date ->
                            selections += date
                            currentDate = date
                            selectedDate = date
                        },
                        onViewSelected = {},
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

        composeRule.onNodeWithTag("calendar-toolbar-month").performClick()
        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onNodeWithTag("calendar-month-overview-container")
                .fetchSemanticsNode().boundsInRoot.height > 100f
        }
        composeRule.waitForIdle()

        // Thursday 1 October: week anchor Monday 28 September.
        swipeMonth(horizontalFraction = -0.7f)
        awaitTimelineSettled()
        assertEquals(listOf(LocalDate.of(2026, 10, 1)), selections.toList())
        assertEquals(LocalDate.of(2026, 10, 1), currentDate)

        // Sunday 1 November: week anchor Monday 26 October.
        swipeMonth(horizontalFraction = -0.7f)
        awaitTimelineSettled()
        assertEquals(LocalDate.of(2026, 11, 1), currentDate)

        // Back to Thursday 1 October.
        swipeMonth(horizontalFraction = 0.7f)
        awaitTimelineSettled()
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 10, 1),
            ),
            selections.toList(),
        )
    }

    private fun swipeMonth(horizontalFraction: Float) {
        composeRule.onNodeWithTag("month-overview-grid", useUnmergedTree = true).performTouchInput {
            val start = Offset(width * 0.5f, height * 0.6f)
            swipe(start, Offset(start.x + width * horizontalFraction, start.y), durationMillis = 180)
        }
        composeRule.waitForIdle()
    }

    private fun awaitTimelineSettled() {
        // The month settle and the timeline's week move are each well under a second.
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
    }
}
