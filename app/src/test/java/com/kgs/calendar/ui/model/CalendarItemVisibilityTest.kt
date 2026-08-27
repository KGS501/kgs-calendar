package com.kgs.calendar.ui.model

import com.kgs.calendar.data.ical.EventRecurrenceOverride
import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.ical.TaskRecurrenceOverride
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

class CalendarItemVisibilityTest {
    private val originalTimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun allDayEventEndDateIsInclusiveOfPreviousMidnight() {
        val event = event(
            start = millis(LocalDate.of(2026, 7, 7), LocalTime.MIDNIGHT),
            end = millis(LocalDate.of(2026, 7, 9), LocalTime.MIDNIGHT),
            allDay = true,
        )

        assertEquals(LocalDate.of(2026, 7, 8), event.endDateInclusive())
        assertEquals(listOf(LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 8)), event.visibleAgendaDates())
        assertTrue(event.occursOn(LocalDate.of(2026, 7, 8)))
        assertFalse(event.occursOn(LocalDate.of(2026, 7, 9)))
    }

    @Test
    fun agendaKeepsRecurringOccurrencesOnTheirOwnDates() {
        val firstDate = LocalDate.of(2026, 7, 7)
        val secondDate = LocalDate.of(2026, 7, 14)
        val resourceHref = "recurring-event.ics"
        val first = event(
            start = millis(firstDate, LocalTime.of(9, 0)),
            end = millis(firstDate, LocalTime.of(10, 0)),
            allDay = false,
            resourceHref = resourceHref,
        )
        val second = event(
            start = millis(secondDate, LocalTime.of(9, 0)),
            end = millis(secondDate, LocalTime.of(10, 0)),
            allDay = false,
            resourceHref = resourceHref,
        )

        val spans = agendaEventDateSpans(listOf(first, second), emptyList())

        assertEquals(listOf(firstDate, secondDate), spans.map { it.startDate })
        assertEquals(listOf(first.startsAtMillis, second.startsAtMillis), spans.map { it.event.startsAtMillis })
    }

    @Test
    fun agendaPreservesMultiDaySpanGrouping() {
        val startDate = LocalDate.of(2026, 7, 7)
        val event = event(
            start = millis(startDate, LocalTime.MIDNIGHT),
            end = millis(startDate.plusDays(3), LocalTime.MIDNIGHT),
            allDay = true,
        )

        val span = agendaEventDateSpans(listOf(event), emptyList()).single()

        assertEquals(startDate, span.startDate)
        assertEquals(startDate.plusDays(2), span.endDate)
    }

    @Test
    fun agendaSplitsAMultiDaySpanOnlyWhereAnotherItemOverlaps() {
        val startDate = LocalDate.of(2026, 7, 7)
        val multiDay = event(
            start = millis(startDate, LocalTime.MIDNIGHT),
            end = millis(startDate.plusDays(5), LocalTime.MIDNIGHT),
            allDay = true,
            resourceHref = "multi-day.ics",
        )
        val overlapping = event(
            start = millis(startDate.plusDays(2), LocalTime.of(9, 0)),
            end = millis(startDate.plusDays(2), LocalTime.of(10, 0)),
            allDay = false,
            resourceHref = "overlap.ics",
        )

        val spans = agendaEventDateSpans(listOf(multiDay, overlapping), emptyList())
            .filter { it.event.resourceHref == multiDay.resourceHref }

        assertEquals(
            listOf(
                startDate to startDate.plusDays(1),
                startDate.plusDays(2) to startDate.plusDays(2),
                startDate.plusDays(3) to startDate.plusDays(4),
            ),
            spans.map { it.startDate to it.endDate },
        )
    }

    @Test
    fun timedMultiDayMiddleDateBecomesAllDayTopItemOnlyForInteriorDays() {
        val event = event(
            start = millis(LocalDate.of(2026, 7, 7), LocalTime.of(22, 0)),
            end = millis(LocalDate.of(2026, 7, 10), LocalTime.of(8, 0)),
            allDay = false,
        )

        assertEquals(LocalDate.of(2026, 7, 8), event.allDayTopStartDate())
        assertEquals(LocalDate.of(2026, 7, 9), event.allDayTopEndDate())
        assertFalse(event.isAllDayTopItemOn(LocalDate.of(2026, 7, 7)))
        assertTrue(event.isAllDayTopItemOn(LocalDate.of(2026, 7, 8)))
    }

    @Test
    fun allDayContinuationDependsOnTheRealEventEndNotTheVisibleViewport() {
        val event = event(
            start = millis(LocalDate.of(2026, 7, 7), LocalTime.MIDNIGHT),
            end = millis(LocalDate.of(2026, 7, 10), LocalTime.MIDNIGHT),
            allDay = true,
        )

        assertTrue(event.continuesAllDayTopItemAfter(LocalDate.of(2026, 7, 8)))
        assertFalse(event.continuesAllDayTopItemAfter(LocalDate.of(2026, 7, 9)))
    }

    @Test
    fun movedRecurringEventUsesOriginalOccurrenceStartForFurtherEdits() {
        val originalStart = millis(LocalDate.of(2026, 7, 17), LocalTime.of(12, 30))
        val movedStart = millis(LocalDate.of(2026, 7, 17), LocalTime.of(13, 0))
        val moved = event(
            start = movedStart,
            end = movedStart + 30 * 60 * 1000L,
            allDay = false,
        ).copy(
            recurrenceRule = "FREQ=WEEKLY",
            isRecurring = true,
        )
        val occurrence = moved.copy(
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeEvents(
                listOf(EventRecurrenceOverride.fromEvent(originalStart, moved)),
            ),
        )

        assertEquals(originalStart, occurrence.occurrenceStartForEdit())
    }

    @Test
    fun untouchedRecurringEventUsesVisibleStartForEdits() {
        val start = millis(LocalDate.of(2026, 7, 24), LocalTime.of(12, 30))
        val occurrence = event(
            start = start,
            end = start + 30 * 60 * 1000L,
            allDay = false,
        ).copy(
            recurrenceRule = "FREQ=WEEKLY",
            isRecurring = true,
        )

        assertEquals(start, occurrence.occurrenceStartForEdit())
    }

    @Test
    fun movedRecurringTaskUsesOriginalOccurrenceStartForStatusChanges() {
        val originalStart = millis(LocalDate.of(2026, 7, 24), LocalTime.of(9, 0))
        val movedStart = millis(LocalDate.of(2026, 7, 24), LocalTime.of(15, 0))
        val moved = task(
            start = movedStart,
            due = movedStart + 60 * 60 * 1000L,
            startHasTime = true,
            dueHasTime = true,
        ).copy(recurrenceRule = "FREQ=WEEKLY")
        val occurrence = moved.copy(
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeTasks(
                listOf(TaskRecurrenceOverride.fromTask(originalStart, moved)),
            ),
        )

        assertEquals(originalStart, occurrence.occurrenceStartForEdit())
    }

    @Test
    fun taskVisibleDatesUsesStartToDueRangeWithReverseGuard() {
        val task = task(
            start = millis(LocalDate.of(2026, 7, 7), LocalTime.MIDNIGHT),
            due = millis(LocalDate.of(2026, 7, 9), LocalTime.MIDNIGHT),
            startHasTime = false,
            dueHasTime = false,
        )
        val reverseTask = task(
            start = millis(LocalDate.of(2026, 7, 9), LocalTime.MIDNIGHT),
            due = millis(LocalDate.of(2026, 7, 7), LocalTime.MIDNIGHT),
            startHasTime = false,
            dueHasTime = false,
        )

        assertEquals(
            listOf(LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 9)),
            task.visibleDates(),
        )
        assertEquals(listOf(LocalDate.of(2026, 7, 9)), reverseTask.visibleDates())
        assertTrue(task.isFullDayTaskOn(LocalDate.of(2026, 7, 8)))
    }

    private fun millis(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun event(
        start: Long,
        end: Long,
        allDay: Boolean,
        resourceHref: String = "event-$start",
    ): EventEntity =
        EventEntity(
            uid = "event",
            collectionHref = "collection",
            resourceHref = resourceHref,
            title = "Event",
            description = null,
            location = null,
            startsAtMillis = start,
            endsAtMillis = end,
            allDay = allDay,
            recurrenceRule = null,
            isRecurring = false,
            color = 0xFF336699.toInt(),
        )

    private fun task(
        start: Long?,
        due: Long?,
        startHasTime: Boolean,
        dueHasTime: Boolean,
    ): TaskEntity =
        TaskEntity(
            uid = "task",
            collectionHref = "collection",
            resourceHref = "task-${start ?: 0}-${due ?: 0}",
            title = "Task",
            notes = null,
            dueAtMillis = due,
            dueHasTime = dueHasTime,
            startAtMillis = start,
            startHasTime = startHasTime,
            completedAtMillis = null,
            isCompleted = false,
            priority = null,
            color = 0xFF336699.toInt(),
        )
}
