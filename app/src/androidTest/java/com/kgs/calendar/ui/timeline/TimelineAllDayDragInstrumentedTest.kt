package com.kgs.calendar.ui.timeline

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.DayHeaderHeight
import com.kgs.calendar.ui.TimelineView
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimelineAllDayDragInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val date = LocalDate.of(2026, 7, 10)
    private val zone = ZoneId.systemDefault()

    @Test
    fun eventHoverReservesEmptyAllDayLaneAndCommitsDisplayedTarget() {
        val event = event(
            resourceHref = "events/timed.ics",
            title = "Timed event",
            startHour = 9,
        )
        val dropped = AtomicReference<Pair<String, LocalDate>?>()
        setTimeline(
            events = listOf(event),
            tasks = emptyList(),
            onEventMovedAllDay = { href, targetDate -> dropped.set(href to targetDate) },
        )

        val card = composeRule.onNodeWithTag("timeline-timed-event-${event.resourceHref}")
        val targetRootY = with(composeRule.density) { (DayHeaderHeight + 8.dp).toPx() }
        val deltaY = targetRootY - card.fetchSemanticsNode().boundsInRoot.center.y
        card.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, deltaY), delayMillis = 300)
        }

        composeRule.onNodeWithTag("timeline-all-day-reservation-$date-0").fetchSemanticsNode()
        card.performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals(event.resourceHref to date, dropped.get())
    }

    @Test
    fun taskHoverUsesNextLaneAndReservationCollapsesWhenPointerLeaves() {
        val occupied = event(
            resourceHref = "events/all-day.ics",
            title = "Occupied lane",
            startHour = 0,
            allDay = true,
        )
        val task = task(resourceHref = "tasks/timed.ics", title = "Timed task", startHour = 10)
        val dropped = AtomicReference<Pair<String, LocalDate>?>()
        setTimeline(
            events = listOf(occupied),
            tasks = listOf(task),
            onTaskMovedAllDay = { href, targetDate -> dropped.set(href to targetDate) },
        )

        val card = composeRule.onNodeWithTag("timeline-timed-task-${task.resourceHref}")
        val targetRootY = with(composeRule.density) { (DayHeaderHeight + 8.dp).toPx() }
        val deltaY = targetRootY - card.fetchSemanticsNode().boundsInRoot.center.y
        card.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, deltaY), delayMillis = 300)
        }
        composeRule.onNodeWithTag("timeline-all-day-reservation-$date-1").fetchSemanticsNode()

        val leaveDistance = with(composeRule.density) { 80.dp.toPx() }
        card.performTouchInput { moveBy(Offset(0f, leaveDistance), delayMillis = 180) }
        composeRule.onAllNodesWithTag("timeline-all-day-reservation-$date-1").assertCountEquals(0)

        card.performTouchInput { moveBy(Offset(0f, -leaveDistance), delayMillis = 180) }
        composeRule.onNodeWithTag("timeline-all-day-reservation-$date-1").fetchSemanticsNode()
        card.performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals(task.resourceHref to date, dropped.get())
    }

    @Test
    fun timedEventDragOverlayKeepsTitleAndLocationInCardLayout() {
        val event = event(
            resourceHref = "events/located.ics",
            title = "Located event",
            startHour = 9,
            location = "Conference room",
        )
        setTimeline(events = listOf(event), tasks = emptyList())

        val card = composeRule.onNodeWithTag("timeline-timed-event-${event.resourceHref}")
        card.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, 20f), delayMillis = 180)
        }

        composeRule.onNodeWithTag("timeline-drag-overlay-title").fetchSemanticsNode()
        composeRule.onNodeWithTag("timeline-drag-overlay-location").fetchSemanticsNode()
        val overlayBounds = composeRule.onNodeWithTag("timeline-drag-overlay").fetchSemanticsNode().boundsInRoot
        val titleBounds = composeRule.onNodeWithTag("timeline-drag-overlay-title").fetchSemanticsNode().boundsInRoot
        assertTrue(titleBounds.center.y < overlayBounds.center.y)

        card.performTouchInput { cancel() }
    }

    @Test
    fun droppedTimedEventStaysAtOptimisticTargetUntilStateCommits() {
        val event = event(
            resourceHref = "events/optimistic.ics",
            title = "Optimistic event",
            startHour = 9,
        )
        val dropped = AtomicReference<LocalDate?>()
        setTimeline(
            events = listOf(event),
            tasks = emptyList(),
            onEventMoved = { _, targetDate -> dropped.set(targetDate) },
        )

        val card = composeRule.onNodeWithTag("timeline-timed-event-${event.resourceHref}")
        card.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, 80f), delayMillis = 220)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(date, dropped.get())
        composeRule.onNodeWithTag("timeline-drag-overlay").fetchSemanticsNode()
    }

    @Test
    fun timedDragPreservesTheFingerOffsetInsideTheCard() {
        val event = event(
            resourceHref = "events/anchored.ics",
            title = "Anchored event",
            startHour = 9,
        )
        setTimeline(events = listOf(event), tasks = emptyList())

        val card = composeRule.onNodeWithTag("timeline-timed-event-${event.resourceHref}")
        val sourceBounds = card.fetchSemanticsNode().boundsInRoot
        card.performTouchInput {
            val nearBottom = Offset(center.x, sourceBounds.height - 8f)
            down(nearBottom)
            advanceEventTime(700)
            moveBy(Offset(0f, 1f), delayMillis = 180)
        }
        composeRule.waitForIdle()

        val overlayTop = composeRule.onNodeWithTag("timeline-drag-overlay").fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "Drag overlay should retain its grab offset: source=${sourceBounds.top}, overlay=$overlayTop",
            abs(overlayTop - sourceBounds.top) <= 3f,
        )

        card.performTouchInput { cancel() }
    }

    private fun setTimeline(
        events: List<EventEntity>,
        tasks: List<TaskEntity>,
        onEventMoved: (String, LocalDate) -> Unit = { _, _ -> },
        onEventMovedAllDay: (String, LocalDate) -> Unit = { _, _ -> },
        onTaskMovedAllDay: (String, LocalDate) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            var hourHeightDp by remember { mutableFloatStateOf(60f) }
            val scroll = rememberScrollState()
            KgsCalendarTheme(
                themeMode = AppThemeMode.KgsBlue,
                darkTheme = false,
                priorityAnimationsEnabled = false,
            ) {
                TimelineView(
                    state = CalendarUiState(
                        selectedDate = date,
                        selectedView = CalendarViewMode.Day,
                        events = events,
                        datedTasks = tasks,
                        priorityAnimationsEnabled = false,
                    ),
                    selectedView = CalendarViewMode.Day,
                    onDateSelected = {},
                    onViewSelected = {},
                    onMultiDayCountChanged = {},
                    onTaskStatusChanged = { _, _ -> },
                    onEventMoved = { href, _, targetDate, _, _ -> onEventMoved(href, targetDate) },
                    onTaskMoved = { _, _, _, _, _ -> },
                    onEventMovedAllDay = { href, _, targetDate -> onEventMovedAllDay(href, targetDate) },
                    onTaskMovedAllDay = { href, _, targetDate -> onTaskMovedAllDay(href, targetDate) },
                    onSlotSelected = { _, _ -> },
                    onAllDaySlotSelected = {},
                    draftEvent = null,
                    onDraftEventChanged = {},
                    onDraftInteraction = {},
                    onDraftTap = {},
                    timelineBottomInset = 0.dp,
                    onDetail = {},
                    timeScroll = scroll,
                    hourHeightDp = hourHeightDp,
                    onHourHeightChange = { hourHeightDp = it },
                    initialTimeScrollApplied = true,
                    onInitialTimeScrollApplied = {},
                    monthMorphDay = date,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun event(
        resourceHref: String,
        title: String,
        startHour: Int,
        allDay: Boolean = false,
        location: String? = null,
    ): EventEntity {
        val start = date.atTime(startHour, 0).atZone(zone).toInstant().toEpochMilli()
        val end = if (allDay) {
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            date.atTime(startHour + 1, 0).atZone(zone).toInstant().toEpochMilli()
        }
        return EventEntity(
            uid = resourceHref,
            collectionHref = "local",
            resourceHref = resourceHref,
            title = title,
            description = null,
            location = location,
            startsAtMillis = start,
            endsAtMillis = end,
            allDay = allDay,
            recurrenceRule = null,
            isRecurring = false,
            color = 0xFF2563A8.toInt(),
        )
    }

    private fun task(resourceHref: String, title: String, startHour: Int): TaskEntity {
        val start = date.atTime(startHour, 0).atZone(zone).toInstant().toEpochMilli()
        val end = date.atTime(startHour + 1, 0).atZone(zone).toInstant().toEpochMilli()
        return TaskEntity(
            uid = resourceHref,
            collectionHref = "local",
            resourceHref = resourceHref,
            title = title,
            notes = null,
            dueAtMillis = end,
            startAtMillis = start,
            completedAtMillis = null,
            isCompleted = false,
            priority = 5,
            color = 0xFF0F766E.toInt(),
        )
    }
}
