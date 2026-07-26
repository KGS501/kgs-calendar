package com.kgs.calendar.ui.timeline

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.DetailSheet
import com.kgs.calendar.ui.TimelineView
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.time.CalendarTimeSnapshot
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TimelineOverdueTasksInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 7, 17)
    private val zone = ZoneId.systemDefault()

    @Test
    fun currentDaySummaryOpensOnlyOverdueTasksAndNavigatesToDetail() {
        val overdueByDue = task("overdue-due.ics", "Past due", dueDate = today.minusDays(1))
        val overdueByStart = task("overdue-start.ics", "Past start", startDate = today.minusDays(2))
        val dueToday = task("today.ics", "Due today", startDate = today.minusDays(4), dueDate = today)
        val completed = task("completed.ics", "Completed", dueDate = today.minusDays(3), isCompleted = true)
        val cancelled = task("cancelled.ics", "Cancelled", dueDate = today.minusDays(3), status = "CANCELLED")
        val openedDetail = AtomicReference<DetailSheet?>()

        setTimeline(
            selectedDate = today,
            selectedView = CalendarViewMode.Day,
            tasks = listOf(overdueByDue, overdueByStart, dueToday, completed, cancelled),
            onDetail = openedDetail::set,
        )

        composeRule.onAllNodesWithTag("timeline-overdue-summary").assertCountEquals(1)
        composeRule.onNodeWithTag("timeline-overdue-summary").performClick()
        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithTag("timeline-overdue-summary").fetchSemanticsNodes().isNotEmpty() &&
                runCatching { composeRule.onNodeWithText(overdueByDue.title).fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithText(overdueByStart.title).fetchSemanticsNode()
        composeRule.onNodeWithText(dueToday.title).assertDoesNotExist()
        composeRule.onNodeWithText(completed.title).assertDoesNotExist()
        composeRule.onNodeWithText(cancelled.title).assertDoesNotExist()

        composeRule.onNodeWithText(overdueByDue.title).performClick()
        composeRule.waitForIdle()

        assertEquals(DetailSheet.Task(overdueByDue), openedDetail.get())
        val minimizeLabel = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.minimize_overdue_tasks)
        composeRule.onNodeWithText(minimizeLabel).assertExists()

        composeRule.onNodeWithTag("timeline-overdue-dismiss-layer").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(overdueByStart.title).assertDoesNotExist()
        composeRule.onNodeWithText(minimizeLabel).assertDoesNotExist()
    }

    @Test
    fun weekViewRendersOneSummaryOnlyInTodaysColumn() {
        setTimeline(
            selectedDate = today,
            selectedView = CalendarViewMode.ThreeDay,
            tasks = listOf(task("overdue.ics", "Overdue", dueDate = today.minusDays(1))),
            weekViewEnabled = true,
        )

        composeRule.onAllNodesWithTag("timeline-overdue-summary").assertCountEquals(1)
    }

    @Test
    fun summaryIsAbsentWhenTodayIsOffscreen() {
        setTimeline(
            selectedDate = today.plusDays(14),
            selectedView = CalendarViewMode.Day,
            tasks = listOf(task("overdue.ics", "Overdue", dueDate = today.minusDays(1))),
        )

        composeRule.onNodeWithTag("timeline-overdue-summary").assertDoesNotExist()
    }

    @Test
    fun tappingDayHeaderWithoutDraftSwitchesToDayViewWithoutCrashing() {
        setTimeline(
            selectedDate = today,
            selectedView = CalendarViewMode.ThreeDay,
            tasks = listOf(task("overdue.ics", "Overdue", dueDate = today.minusDays(1))),
        )

        composeRule.onNodeWithText(today.dayOfMonth.toString()).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("timeline-overdue-summary").assertCountEquals(1)
    }

    @Test
    fun overdueTaskDragsIntoTimedGridAndClosesPanel() {
        val overdue = task("overdue-drag.ics", "A very long overdue task title", dueDate = today.minusDays(1))
        val moved = AtomicReference<Pair<String, LocalDate>?>(null)
        setTimeline(
            selectedDate = today,
            selectedView = CalendarViewMode.Day,
            tasks = listOf(overdue),
            onTaskMoved = { href, date -> moved.set(href to date) },
        )

        composeRule.onNodeWithTag("timeline-overdue-summary").performClick()
        val card = composeRule.onNodeWithTag("timeline-overdue-task-${overdue.resourceHref}")
        card.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, 280.dp.toPx()), delayMillis = 300)
        }
        card.performTouchInput { up() }
        composeRule.waitUntil(timeoutMillis = 2_000) { moved.get() != null }

        assertEquals(overdue.resourceHref to today, moved.get())
        val minimizeLabel = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.minimize_overdue_tasks)
        composeRule.onNodeWithText(minimizeLabel).assertDoesNotExist()
    }

    private fun setTimeline(
        selectedDate: LocalDate,
        selectedView: CalendarViewMode,
        tasks: List<TaskEntity>,
        weekViewEnabled: Boolean = false,
        onDetail: (DetailSheet) -> Unit = {},
        onTaskMoved: (String, LocalDate) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            var hourHeightDp by remember { mutableFloatStateOf(60f) }
            var overdueTasksExpanded by remember { mutableStateOf(false) }
            val scroll = rememberScrollState()
            CompositionLocalProvider(
                LocalCalendarTimeSnapshot provides CalendarTimeSnapshot(today, LocalTime.NOON),
            ) {
                KgsCalendarTheme(
                    themeMode = AppThemeMode.KgsBlue,
                    darkTheme = false,
                    priorityAnimationsEnabled = false,
                ) {
                    TimelineView(
                        state = CalendarUiState(
                            selectedDate = selectedDate,
                            selectedView = selectedView,
                            scheduledOpenTasks = tasks,
                            priorityAnimationsEnabled = false,
                            weekViewEnabled = weekViewEnabled,
                        ),
                        selectedView = selectedView,
                        onDateSelected = {},
                        onViewSelected = {},
                        onMultiDayCountChanged = {},
                        onTaskStatusChanged = { _, _ -> },
                        onEventMoved = { _, _, _, _, _ -> },
                        onTaskMoved = { href, _, date, _, _ -> onTaskMoved(href, date) },
                        onEventMovedAllDay = { _, _, _ -> },
                        onTaskMovedAllDay = { _, _, _ -> },
                        onSlotSelected = { _, _ -> },
                        onAllDaySlotSelected = {},
                        draftEvent = null,
                        onDraftEventChanged = {},
                        onDraftInteraction = {},
                        onDraftTap = {},
                        timelineBottomInset = 0.dp,
                        onDetail = onDetail,
                        overdueTasksExpanded = overdueTasksExpanded,
                        onOverdueTasksExpandedChange = { overdueTasksExpanded = it },
                        timeScroll = scroll,
                        hourHeightDp = hourHeightDp,
                        onHourHeightChange = { hourHeightDp = it },
                        initialTimeScrollApplied = true,
                        onInitialTimeScrollApplied = {},
                        monthMorphDay = selectedDate,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun task(
        resourceHref: String,
        title: String,
        dueDate: LocalDate? = null,
        startDate: LocalDate? = null,
        isCompleted: Boolean = false,
        status: String? = null,
    ): TaskEntity =
        TaskEntity(
            uid = resourceHref,
            collectionHref = "local",
            resourceHref = resourceHref,
            title = title,
            notes = null,
            dueAtMillis = dueDate?.atTime(18, 0)?.atZone(zone)?.toInstant()?.toEpochMilli(),
            startAtMillis = startDate?.atTime(9, 0)?.atZone(zone)?.toInstant()?.toEpochMilli(),
            completedAtMillis = null,
            isCompleted = isCompleted,
            status = status,
            priority = 5,
            color = 0xFF0F766E.toInt(),
        )
}
