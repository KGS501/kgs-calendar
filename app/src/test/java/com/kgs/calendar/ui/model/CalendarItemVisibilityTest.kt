package com.kgs.calendar.ui.model

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

    private fun event(start: Long, end: Long, allDay: Boolean): EventEntity =
        EventEntity(
            uid = "event",
            collectionHref = "collection",
            resourceHref = "event-$start",
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
