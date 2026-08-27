package com.kgs.calendar.ui.month

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
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
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CalendarShellMonthOverviewDismissInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun monthOverviewSwipeUpDismissesWithoutChangingTheSelectedDate() {
        val july = LocalDate.of(2026, 7, 16)
        val selectedDate = AtomicReference(july)
        composeRule.setContent {
            val portraitConfiguration = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = 400
                screenHeightDp = 900
            }
            CompositionLocalProvider(
                LocalConfiguration provides portraitConfiguration,
                LocalCalendarTimeSnapshot provides CalendarTimeSnapshot(july, LocalTime.NOON),
            ) {
                KgsCalendarTheme(
                    themeMode = AppThemeMode.KgsBlue,
                    darkTheme = false,
                    priorityAnimationsEnabled = false,
                ) {
                    CalendarShell(
                        state = CalendarUiState(
                            selectedDate = july,
                            selectedView = CalendarViewMode.ThreeDay,
                            priorityAnimationsEnabled = false,
                        ),
                        onMenu = {},
                        onDateSelected = selectedDate::set,
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

        val overviewHeightBeforeDismiss = composeRule
            .onNodeWithTag("calendar-month-overview-container")
            .fetchSemanticsNode().boundsInRoot.height
        composeRule.onNodeWithTag("calendar-month-overview-container").performTouchInput {
            swipe(
                start = Offset(width * 0.5f, height * 0.95f),
                end = Offset(width * 0.5f, height * 0.05f),
                durationMillis = 220,
            )
        }
        composeRule.waitForIdle()
        val overviewHeightAfterDismiss = composeRule
            .onNodeWithTag("calendar-month-overview-container")
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "Month overview did not close: before=$overviewHeightBeforeDismiss, after=$overviewHeightAfterDismiss",
            overviewHeightAfterDismiss <= 1f,
        )
        assertEquals(july, selectedDate.get())
    }
}
