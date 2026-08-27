package com.kgs.calendar.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.domain.model.CalendarRange
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.calendar.toMonthPage
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.time.CalendarTimeSnapshot
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class CalendarShellAgendaNavigationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 8, 23)
    private val loadedRange = CalendarRange(LocalDate.of(2025, 8, 1), LocalDate.of(2028, 9, 1))

    @Test
    fun selectingJanuaryMonthPillMovesAgendaToJanuaryFirst() {
        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    CalendarUiState(
                        selectedDate = today,
                        selectedView = CalendarViewMode.Agenda,
                        events = listOf(
                            event(LocalDate.of(2025, 12, 1), "December item"),
                            event(LocalDate.of(2026, 1, 1), "January item"),
                            event(LocalDate.of(2026, 6, 12), "June item"),
                            event(today, "Today item"),
                        ),
                        visibleRange = loadedRange,
                        loadedDataRange = loadedRange,
                        requestedDataRange = loadedRange,
                        priorityAnimationsEnabled = false,
                        showCalendarWeeks = true,
                    ),
                )
            }
            CompositionLocalProvider(
                LocalCalendarTimeSnapshot provides CalendarTimeSnapshot(today, LocalTime.NOON),
            ) {
                KgsCalendarTheme(
                    themeMode = AppThemeMode.KgsBlue,
                    darkTheme = false,
                    priorityAnimationsEnabled = false,
                ) {
                    CalendarShell(
                        state = state,
                        onMenu = {},
                        onDateSelected = { state = state.copy(selectedDate = it) },
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
        composeRule.waitUntil(timeoutMillis = 4_000) { headerShows(today, "August") }

        composeRule.onNodeWithTag("calendar-toolbar-month").performClick()
        composeRule.onNodeWithTag("month-overview-strip", useUnmergedTree = true)
            .performScrollToIndex(YearMonth.of(2026, 1).toMonthPage())
        composeRule.onNodeWithTag("month-overview-month-2026-01", useUnmergedTree = true)
            .performClick()

        composeRule.waitUntil(timeoutMillis = 4_000) {
            headerShows(LocalDate.of(2026, 1, 1), "January")
        }
        composeRule.onNodeWithText("January item").assertIsDisplayed()
    }

    private fun headerShows(date: LocalDate, month: String): Boolean {
        val day = composeRule.onAllNodes(
            hasText(date.dayOfMonth.toString()) and hasAnyAncestor(hasTestTag("agenda-header-day")),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()
        val displayedMonth = composeRule.onAllNodes(
            hasText(month) and hasAnyAncestor(hasTestTag("agenda-header-month")),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()
        return day && displayedMonth
    }

    private fun event(date: LocalDate, title: String): EventEntity {
        val zone = ZoneId.systemDefault()
        val start = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        return EventEntity(
            uid = title,
            collectionHref = "/events/",
            resourceHref = "/events/${title.replace(' ', '-')}.ics",
            title = title,
            description = null,
            location = null,
            startsAtMillis = start,
            endsAtMillis = start + 60L * 60L * 1000L,
            allDay = false,
            recurrenceRule = null,
            isRecurring = false,
            timezoneId = zone.id,
            color = 0,
        )
    }
}
