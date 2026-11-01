package com.kgs.calendar.ui.layout

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.ui.model.isFullDayTaskOn
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
    fun aTimedTaskOverSeveralDaysFillsItsMiddleDaysAsAFullDayItem() {
        val first = LocalDate.of(2026, 10, 22)
        val task = task(
            start = millis(first, LocalTime.of(9, 0)),
            due = millis(first.plusDays(4), LocalTime.of(17, 0)),
            startHasTime = true,
            dueHasTime = true,
        )

        // First and last day: the timed part. Middle days: a full-day item, not a 24-hour block.
        assertEquals(540, task.timedPlacementOn(first, hourHeightDp = 60f)!!.startMinute)
        assertEquals(17 * 60, task.timedPlacementOn(first.plusDays(4), hourHeightDp = 60f)!!.endMinute)
        (1..3).forEach { offset ->
            val middle = first.plusDays(offset.toLong())
            assertEquals(null, task.timedPlacementOn(middle, hourHeightDp = 60f))
            assertEquals(true, task.isFullDayTaskOn(middle))
        }
        assertEquals(false, task.isFullDayTaskOn(first))
    }

    @Test
    fun timedTaskWithOnlyDueTimeGetsDefaultThirtyMinuteDuration() {
        val day = LocalDate.of(2026, 7, 7)
        val task = task(start = null, due = millis(day, LocalTime.of(10, 0)), startHasTime = false, dueHasTime = true)

        val placement = task.timedPlacementOn(day, hourHeightDp = 60f)

        assertEquals(TimedPlacement(topDp = 570f, heightDp = 30f, startMinute = 570, endMinute = 600), placement)
    }

    @Test
    fun nearZeroDurationProviderEventGetsAReadableMinimumVisualDuration() {
        val day = LocalDate.of(2026, 7, 7)
        val start = millis(day, LocalTime.of(8, 15))
        val event = event(start = start, end = start + 1L)

        val placement = event.timedPlacementOn(day, hourHeightDp = 60f)

        assertEquals(
            TimedPlacement(topDp = 495f, heightDp = 15f, startMinute = 495, endMinute = 510),
            placement,
        )
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

    @Test
    fun eventsFollowTheClockOnTheDaysTheClocksChange() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        // 25 October 2026 has 25 hours (03:00 goes back to 02:00); 29 March 2026 has 23.
        listOf(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 3, 29)).forEach { day ->
            val event = event(start = millis(day, LocalTime.of(10, 0)), end = millis(day, LocalTime.of(11, 30)))
            val task = task(start = null, due = millis(day, LocalTime.of(18, 0)), startHasTime = false, dueHasTime = true)
            val lateEvening = event("late", millis(day, LocalTime.of(23, 0)), millis(day.plusDays(1), LocalTime.of(1, 0)))

            assertEquals(
                "$day",
                TimedPlacement(topDp = 600f, heightDp = 90f, startMinute = 600, endMinute = 690),
                event.timedPlacementOn(day, hourHeightDp = 60f),
            )
            assertEquals("$day", 17 * 60 + 30, task.timedPlacementOn(day, hourHeightDp = 60f)!!.startMinute)
            assertEquals("$day", 23 * 60 to 24 * 60, lateEvening.timedPlacementOn(day, hourHeightDp = 60f)!!.let { it.startMinute to it.endMinute })
        }
    }

    @Test
    fun anEventInTheRepeatedHourKeepsAPositiveHeight() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        val day = LocalDate.of(2026, 10, 25)
        // 02:40 summer time to 02:10 winter time: 30 minutes later, yet an earlier clock time.
        val start = java.time.ZonedDateTime.of(day, LocalTime.of(2, 40), ZoneId.of("Europe/Berlin")).withEarlierOffsetAtOverlap()
        val end = java.time.ZonedDateTime.of(day, LocalTime.of(2, 10), ZoneId.of("Europe/Berlin")).withLaterOffsetAtOverlap()
        val event = event(start = start.toInstant().toEpochMilli(), end = end.toInstant().toEpochMilli())

        assertEquals(160 to 190, event.timedPlacementOn(day, hourHeightDp = 60f)!!.let { it.startMinute to it.endMinute })
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
