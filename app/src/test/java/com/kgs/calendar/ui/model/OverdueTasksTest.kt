package com.kgs.calendar.ui.model

import com.kgs.calendar.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class OverdueTasksTest {
    private val today = LocalDate.of(2026, 7, 10)
    private val zoneId = ZoneOffset.UTC

    @Test
    fun unfinishedTaskWithPastDueDayIsOverdue() {
        val task = task(dueAtMillis = millis(today.minusDays(1), LocalTime.of(23, 59)))

        assertTrue(task.isOverdueTask(today, zoneId))
    }

    @Test
    fun dueDateTakesPrecedenceOverStartDate() {
        val dueToday = task(
            resourceHref = "due-today.ics",
            startAtMillis = millis(today.minusDays(3)),
            dueAtMillis = millis(today),
        )
        val dueInPast = task(
            resourceHref = "due-past.ics",
            startAtMillis = millis(today.plusDays(2)),
            dueAtMillis = millis(today.minusDays(1)),
        )

        assertFalse(dueToday.isOverdueTask(today, zoneId))
        assertTrue(dueInPast.isOverdueTask(today, zoneId))
    }

    @Test
    fun startDateIsUsedWhenDueDateIsMissing() {
        assertTrue(
            task(startAtMillis = millis(today.minusDays(1))).isOverdueTask(today, zoneId),
        )
        assertFalse(
            task(startAtMillis = millis(today)).isOverdueTask(today, zoneId),
        )
    }

    @Test
    fun todayFutureAndUndatedTasksAreNotOverdue() {
        assertFalse(task(dueAtMillis = millis(today)).isOverdueTask(today, zoneId))
        assertFalse(task(dueAtMillis = millis(today.plusDays(1))).isOverdueTask(today, zoneId))
        assertFalse(task().isOverdueTask(today, zoneId))
    }

    @Test
    fun completedAndCancelledTasksAreNotOverdue() {
        val past = millis(today.minusDays(1))

        assertFalse(task(dueAtMillis = past, isCompleted = true).isOverdueTask(today, zoneId))
        assertFalse(task(dueAtMillis = past, status = "COMPLETED").isOverdueTask(today, zoneId))
        assertFalse(task(dueAtMillis = past, status = "cancelled").isOverdueTask(today, zoneId))
    }

    @Test
    fun highestOverduePriorityUsesLowestValidIcalNumber() {
        val tasks = listOf(
            task(resourceHref = "normal.ics", priority = 5),
            task(resourceHref = "highest.ics", priority = 1),
            task(resourceHref = "invalid.ics", priority = 0),
            task(resourceHref = "none.ics"),
        )

        assertEquals(1, tasks.highestOverduePriority())
    }

    @Test
    fun explicitZoneControlsTheEffectiveLocalDay() {
        val nearUtcMidnight = Instant.parse("2026-07-10T00:30:00Z").toEpochMilli()
        val task = task(dueAtMillis = nearUtcMidnight)

        assertFalse(task.isOverdueTask(today, ZoneOffset.UTC))
        assertTrue(task.isOverdueTask(today, ZoneId.of("America/Los_Angeles")))
    }

    @Test
    fun overdueTasksAreOrderedByEffectiveTimeThenStableTaskIdentity() {
        val oldest = task(
            resourceHref = "oldest.ics",
            title = "Oldest",
            startAtMillis = millis(today.minusDays(5), LocalTime.of(8, 0)),
        )
        val alphaA = task(
            resourceHref = "alpha-a.ics",
            title = "Alpha",
            dueAtMillis = millis(today.minusDays(4), LocalTime.of(9, 0)),
        )
        val alphaB = task(
            resourceHref = "alpha-b.ics",
            title = "alpha",
            dueAtMillis = millis(today.minusDays(4), LocalTime.of(9, 0)),
        )
        val dueWinsOverEarlierStart = task(
            resourceHref = "due-wins.ics",
            title = "Later effective deadline",
            startAtMillis = millis(today.minusDays(8)),
            dueAtMillis = millis(today.minusDays(2)),
        )
        val notOverdue = task(resourceHref = "today.ics", dueAtMillis = millis(today))
        val cancelled = task(
            resourceHref = "cancelled.ics",
            dueAtMillis = millis(today.minusDays(9)),
            status = "CANCELLED",
        )

        val result = orderedOverdueTasks(
            tasks = listOf(dueWinsOverEarlierStart, alphaB, notOverdue, oldest, cancelled, alphaA),
            today = today,
            zoneId = zoneId,
        )

        assertEquals(
            listOf("oldest.ics", "alpha-a.ics", "alpha-b.ics", "due-wins.ics"),
            result.map { it.resourceHref },
        )
    }

    @Test
    fun everyMissedOccurrenceOfARecurringTaskIsItsOwnEntryWhileSingleTasksAppearOnce() {
        val series = task(resourceHref = "daily.ics", title = "Water plants").copy(recurrenceRule = "FREQ=DAILY")
        val missed = (3L downTo 1L).map { days ->
            series.copy(dueAtMillis = millis(today.minusDays(days), LocalTime.of(9, 0)))
        }
        val next = series.copy(dueAtMillis = millis(today, LocalTime.of(9, 0)))
        val single = task(resourceHref = "single.ics", title = "Single", dueAtMillis = millis(today.minusDays(2)))

        // The task list's entry of an ended series is its earliest missed occurrence, so it arrives twice.
        val result = orderedOverdueTasks(
            tasks = listOf(single, next, missed.first()) + missed,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(
            listOf(
                "daily.ics" to today.minusDays(3),
                "single.ics" to today.minusDays(2),
                "daily.ics" to today.minusDays(2),
                "daily.ics" to today.minusDays(1),
            ),
            result.map { it.resourceHref to Instant.ofEpochMilli(it.dueAtMillis!!).atZone(zoneId).toLocalDate() },
        )
        assertEquals(result.size, result.map { it.overdueEntryKey() }.toSet().size)
        assertEquals("single.ics", single.overdueEntryKey())
    }

    private fun millis(
        date: LocalDate,
        time: LocalTime = LocalTime.MIDNIGHT,
    ): Long = date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()

    private fun task(
        resourceHref: String = "task.ics",
        title: String = "Task",
        dueAtMillis: Long? = null,
        startAtMillis: Long? = null,
        isCompleted: Boolean = false,
        status: String? = null,
        priority: Int? = null,
    ): TaskEntity =
        TaskEntity(
            uid = "uid-$resourceHref",
            collectionHref = "collection",
            resourceHref = resourceHref,
            title = title,
            notes = null,
            dueAtMillis = dueAtMillis,
            dueHasTime = true,
            startAtMillis = startAtMillis,
            startHasTime = true,
            completedAtMillis = null,
            isCompleted = isCompleted,
            status = status,
            priority = priority,
            color = 0xFF336699.toInt(),
        )
}
