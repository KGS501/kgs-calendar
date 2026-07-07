package com.kgs.calendar.ui.layout

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

class TimedCalendarLayoutTest {
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
    fun timedPlacementClipsEventsToVisibleDay() {
        val day = LocalDate.of(2026, 7, 7)
        val event = event(
            start = millis(day.minusDays(1), LocalTime.of(23, 0)),
            end = millis(day, LocalTime.of(1, 30)),
        )

        val placement = event.timedPlacementOn(day, hourHeightDp = 60f)

        assertEquals(TimedPlacement(topDp = 0f, heightDp = 90f, startMinute = 0, endMinute = 90), placement)
    }

    @Test
    fun timedTaskWithOnlyDueTimeGetsDefaultThirtyMinuteDuration() {
        val day = LocalDate.of(2026, 7, 7)
        val task = task(start = null, due = millis(day, LocalTime.of(10, 0)), startHasTime = false, dueHasTime = true)

        val placement = task.timedPlacementOn(day, hourHeightDp = 60f)

        assertEquals(TimedPlacement(topDp = 570f, heightDp = 30f, startMinute = 570, endMinute = 600), placement)
    }

    @Test
    fun timedLayoutGroupsOverlappingItemsIntoLanes() {
        val day = LocalDate.of(2026, 7, 7)
        val eventA = event("a", millis(day, LocalTime.of(9, 0)), millis(day, LocalTime.of(10, 0)))
        val eventB = event("b", millis(day, LocalTime.of(9, 30)), millis(day, LocalTime.of(10, 30)))
        val eventC = event("c", millis(day, LocalTime.of(11, 0)), millis(day, LocalTime.of(12, 0)))
        val task = task(
            start = millis(day, LocalTime.of(9, 15)),
            due = millis(day, LocalTime.of(9, 45)),
            startHasTime = true,
            dueHasTime = true,
        )

        val layout = layoutTimedItemsForDay(day, hourHeightDp = 60f, events = listOf(eventA, eventB, eventC), tasks = listOf(task))

        assertEquals(listOf(0, 1, 2, 0), layout.map { it.lane })
        assertEquals(listOf(3, 3, 3, 1), layout.map { it.laneCount })
    }

    private fun millis(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun event(
        uid: String = "event",
        start: Long,
        end: Long,
    ): EventEntity =
        EventEntity(
            uid = uid,
            collectionHref = "collection",
            resourceHref = "event-$uid-$start",
            title = uid,
            description = null,
            location = null,
            startsAtMillis = start,
            endsAtMillis = end,
            allDay = false,
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
