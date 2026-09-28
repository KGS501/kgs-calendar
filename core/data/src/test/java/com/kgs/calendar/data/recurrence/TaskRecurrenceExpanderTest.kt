package com.kgs.calendar.data.recurrence

import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.ical.TaskRecurrenceOverride
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.task.occurrenceIdOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskRecurrenceExpanderTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val expander = TaskRecurrenceExpander(RecurrenceExpander(zone))

    @Test
    fun expandsRecurringTaskUsingStartAndDueOffsets() {
        val start = LocalDate.of(2026, 6, 1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val due = LocalDate.of(2026, 6, 1).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val task = TaskEntity(
            uid = "task-repeat",
            collectionHref = "/tasks/",
            resourceHref = "/tasks/task-repeat.ics",
            title = "Repeat",
            notes = null,
            dueAtMillis = due,
            startAtMillis = start,
            completedAtMillis = null,
            isCompleted = false,
            priority = null,
            recurrenceRule = "FREQ=DAILY;COUNT=3",
            timezoneId = zone.id,
            color = 0xff176b5d.toInt(),
        )
        val rangeStart = LocalDate.of(2026, 6, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEnd = LocalDate.of(2026, 6, 5).atStartOfDay(zone).toInstant().toEpochMilli()

        val occurrences = expander.expand(task, rangeStart, rangeEnd)

        assertEquals(3, occurrences.size)
        assertEquals(60L * 60L * 1000L, occurrences[2].dueAtMillis!! - occurrences[2].startAtMillis!!)
    }

    @Test
    fun movedTaskOverrideKeepsOriginalRecurrenceIdentity() {
        val start = LocalDate.of(2026, 7, 9).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val due = start + 60 * 60 * 1000L
        val originalSecondStart = LocalDate.of(2026, 7, 10).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val movedStart = LocalDate.of(2026, 7, 10).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val task = TaskEntity(
            uid = "task-repeat",
            collectionHref = "/tasks/",
            resourceHref = "/tasks/task-repeat.ics",
            title = "Repeat",
            notes = null,
            dueAtMillis = due,
            startAtMillis = start,
            completedAtMillis = null,
            isCompleted = false,
            priority = null,
            recurrenceRule = "FREQ=DAILY;COUNT=2",
            timezoneId = zone.id,
            color = 0xff176b5d.toInt(),
        )
        val movedTask = task.copy(startAtMillis = movedStart, dueAtMillis = movedStart + 60 * 60 * 1000L)
        val withOverride = task.copy(
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeTasks(
                listOf(TaskRecurrenceOverride.fromTask(originalSecondStart, movedTask)),
            ),
        )
        val rangeStart = LocalDate.of(2026, 7, 9).atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEnd = LocalDate.of(2026, 7, 12).atStartOfDay(zone).toInstant().toEpochMilli()

        val occurrences = expander.expandWithIdentity(withOverride, rangeStart, rangeEnd)

        val moved = occurrences.single { it.item.startAtMillis == movedStart }
        assertEquals(CalendarOccurrenceId.Task(task.resourceHref, originalSecondStart), moved.occurrenceId)
    }

    @Test
    fun completedOverrideDoesNotCompleteTheRecurringSeries() {
        val start = LocalDate.of(2026, 8, 18).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val secondStart = LocalDate.of(2026, 8, 19).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val task = TaskEntity(
            uid = "task-repeat",
            collectionHref = "/tasks/",
            resourceHref = "/tasks/task-repeat.ics",
            title = "Repeat",
            notes = null,
            dueAtMillis = start + 60 * 60 * 1000L,
            startAtMillis = start,
            completedAtMillis = null,
            isCompleted = false,
            status = "NEEDS-ACTION",
            priority = null,
            recurrenceRule = "FREQ=DAILY;COUNT=3",
            timezoneId = zone.id,
            color = 0xff176b5d.toInt(),
        )
        val completedSecond = task.copy(
            startAtMillis = secondStart,
            dueAtMillis = secondStart + 60 * 60 * 1000L,
            completedAtMillis = secondStart + 30 * 60 * 1000L,
            isCompleted = true,
            status = "COMPLETED",
            recurrenceRule = null,
        )
        val withOverride = task.copy(
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeTasks(
                listOf(TaskRecurrenceOverride.fromTask(secondStart, completedSecond)),
            ),
        )
        val rangeStart = LocalDate.of(2026, 8, 18).atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEnd = LocalDate.of(2026, 8, 22).atStartOfDay(zone).toInstant().toEpochMilli()

        val occurrences = expander.expand(withOverride, rangeStart, rangeEnd)

        assertEquals(listOf(false, true, false), occurrences.map { it.isCompleted })
        assertEquals(listOf("NEEDS-ACTION", "COMPLETED", "NEEDS-ACTION"), occurrences.map { it.status })
        assertEquals(false, withOverride.isCompleted)
    }

    @Test
    fun cancelledOverrideRemainsVisibleAsCancelledOccurrence() {
        val start = LocalDate.of(2026, 8, 18).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val secondStart = LocalDate.of(2026, 8, 19).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val task = TaskEntity(
            uid = "task-repeat",
            collectionHref = "/tasks/",
            resourceHref = "/tasks/task-repeat.ics",
            title = "Repeat",
            notes = null,
            dueAtMillis = start + 60 * 60 * 1000L,
            startAtMillis = start,
            completedAtMillis = null,
            isCompleted = false,
            status = "NEEDS-ACTION",
            priority = null,
            recurrenceRule = "FREQ=DAILY;COUNT=3",
            timezoneId = zone.id,
            color = 0xff176b5d.toInt(),
        )
        val cancelledSecond = task.copy(
            startAtMillis = secondStart,
            dueAtMillis = secondStart + 60 * 60 * 1000L,
            status = "CANCELLED",
            recurrenceRule = null,
        )
        val withOverride = task.copy(
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeTasks(
                listOf(TaskRecurrenceOverride.fromTask(secondStart, cancelledSecond)),
            ),
        )
        val rangeStart = LocalDate.of(2026, 8, 18).atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEnd = LocalDate.of(2026, 8, 22).atStartOfDay(zone).toInstant().toEpochMilli()

        val occurrences = expander.expand(withOverride, rangeStart, rangeEnd)

        assertEquals(3, occurrences.size)
        assertEquals(
            listOf("NEEDS-ACTION", "CANCELLED", "NEEDS-ACTION"),
            occurrences.map { it.status },
        )
    }

    @Test
    fun inactiveOverridesExposeCompletedAndCancelledOccurrencesForTheTaskSidebar() {
        val start = LocalDate.of(2026, 8, 18).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val secondStart = LocalDate.of(2026, 8, 19).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val thirdStart = LocalDate.of(2026, 8, 20).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val master = TaskEntity(
            uid = "task-repeat",
            collectionHref = "/tasks/",
            resourceHref = "/tasks/task-repeat.ics",
            title = "Repeat",
            notes = null,
            dueAtMillis = start + 60 * 60 * 1000L,
            startAtMillis = start,
            completedAtMillis = null,
            isCompleted = false,
            status = "NEEDS-ACTION",
            priority = null,
            recurrenceRule = "FREQ=DAILY;COUNT=4",
            timezoneId = zone.id,
            color = 0xff176b5d.toInt(),
        )
        val cancelled = master.copy(
            startAtMillis = secondStart,
            dueAtMillis = secondStart + 60 * 60 * 1000L,
            status = "CANCELLED",
        )
        val completed = master.copy(
            startAtMillis = thirdStart,
            dueAtMillis = thirdStart + 60 * 60 * 1000L,
            completedAtMillis = thirdStart + 30 * 60 * 1000L,
            isCompleted = true,
            status = "COMPLETED",
        )
        val open = master.copy(status = "IN-PROCESS")
        val withOverrides = master.copy(
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeTasks(
                listOf(
                    TaskRecurrenceOverride.fromTask(secondStart, cancelled),
                    TaskRecurrenceOverride.fromTask(thirdStart, completed),
                    TaskRecurrenceOverride.fromTask(start, open),
                ),
            ),
        )

        val inactive = expander.inactiveOverrides(withOverrides)

        assertEquals(listOf("CANCELLED", "COMPLETED"), inactive.map { it.status })
        assertEquals(listOf(secondStart, thirdStart), inactive.map { it.startAtMillis })
    }
    @Test
    fun currentOpenOccurrenceIsTheOneDueTodayAndMovesOnOnceItIsDone() {
        val today = LocalDate.of(2026, 9, 25)
        val master = dailyTask(firstDay = today.minusDays(5))

        val current = expander.currentOpenOccurrence(master, startOfDay(today))!!
        assertEquals(at(today, 9), current.startAtMillis)
        assertEquals(CalendarOccurrenceId.Task(master.resourceHref, at(today, 9)), current.occurrenceIdOrNull())

        val todayDone = master.withClosedOccurrences(at(today, 9))
        val next = expander.currentOpenOccurrence(todayDone, startOfDay(today))!!
        assertEquals(at(today.plusDays(1), 9), next.startAtMillis)
        assertEquals(CalendarOccurrenceId.Task(master.resourceHref, at(today.plusDays(1), 9)), next.occurrenceIdOrNull())
        assertFalse(next.isCompleted)
        assertTrue(todayDone.occurrenceAt(at(today, 9)).isCompleted)
        assertFalse(todayDone.occurrenceAt(at(today.plusDays(1), 9)).isCompleted)
    }

    @Test
    fun currentOpenOccurrenceFindsTheNextOccurrenceOfASparseSeries() {
        val today = LocalDate.of(2026, 9, 25)
        val master = dailyTask(firstDay = LocalDate.of(2026, 3, 1), rule = "FREQ=YEARLY")

        assertEquals(at(LocalDate.of(2027, 3, 1), 9), expander.currentOpenOccurrence(master, startOfDay(today))?.startAtMillis)
    }

    @Test
    fun endedSeriesFallsBackToItsEarliestOpenOccurrenceAndAFinishedOneHasNone() {
        val today = LocalDate.of(2026, 9, 25)
        val firstDay = LocalDate.of(2026, 9, 10)
        val master = dailyTask(firstDay = firstDay, rule = "FREQ=DAILY;COUNT=3")

        val firstDone = master.withClosedOccurrences(at(firstDay, 9))
        assertEquals(at(firstDay.plusDays(1), 9), expander.currentOpenOccurrence(firstDone, startOfDay(today))?.startAtMillis)

        val allDone = master.withClosedOccurrences(at(firstDay, 9), at(firstDay.plusDays(1), 9), at(firstDay.plusDays(2), 9))
        assertNull(expander.currentOpenOccurrence(allDone, startOfDay(today)))
    }

    @Test
    fun withCurrentOccurrencesOnlyReplacesOpenDatedSeries() {
        val today = LocalDate.of(2026, 9, 25)
        val single = dailyTask(firstDay = today.minusDays(2), rule = null).copy(resourceHref = "/tasks/single.ics")
        val undated = dailyTask(firstDay = today).copy(resourceHref = "/tasks/undated.ics", startAtMillis = null, dueAtMillis = null)
        val closedSeries = dailyTask(firstDay = today.minusDays(2)).copy(resourceHref = "/tasks/closed.ics", isCompleted = true)
        val finished = dailyTask(firstDay = LocalDate.of(2026, 9, 1), rule = "FREQ=DAILY;COUNT=1")
            .let { it.copy(resourceHref = "/tasks/finished.ics").withClosedOccurrences(it.startAtMillis!!) }
        val open = dailyTask(firstDay = today.minusDays(2))

        val listed = expander.withCurrentOccurrences(listOf(single, undated, closedSeries, finished, open), startOfDay(today))

        assertEquals(listOf(single, undated, closedSeries), listed.take(3))
        assertEquals(listOf(open.resourceHref), listed.drop(3).map { it.resourceHref })
        assertEquals(at(today, 9), listed.last().startAtMillis)
    }

    @Test
    fun missedOpenOccurrencesAreTheOpenOnesDueBeforeTodayAndLeaveOnceClosed() {
        val today = LocalDate.of(2026, 9, 25)
        val master = dailyTask(firstDay = today.minusDays(3))

        val missed = expander.missedOpenOccurrences(master, startOfDay(today))
        assertEquals((3 downTo 1).map { at(today.minusDays(it.toLong()), 9) }, missed.map { it.startAtMillis })
        assertEquals(
            missed.map { CalendarOccurrenceId.Task(master.resourceHref, it.startAtMillis!!) },
            missed.map { it.occurrenceIdOrNull() },
        )

        val middleDone = master.withClosedOccurrences(at(today.minusDays(2), 9))
        assertEquals(
            listOf(at(today.minusDays(3), 9), at(today.minusDays(1), 9)),
            expander.missedOpenOccurrences(middleDone, startOfDay(today)).map { it.startAtMillis },
        )
        // The task list still shows today's occurrence.
        assertEquals(at(today, 9), expander.currentOpenOccurrence(middleDone, startOfDay(today))?.startAtMillis)

        val firstCancelled = middleDone.copy(
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeTasks(
                RecurrenceOverrideCodec.decodeTasks(middleDone.recurrenceOverridesJson) +
                    TaskRecurrenceOverride.fromTask(
                        at(today.minusDays(3), 9),
                        master.occurrenceAt(at(today.minusDays(3), 9)).copy(status = "CANCELLED"),
                    ),
            ),
        )
        assertEquals(
            listOf(at(today.minusDays(1), 9)),
            expander.missedOpenOccurrences(firstCancelled, startOfDay(today)).map { it.startAtMillis },
        )
    }

    @Test
    fun missedOpenOccurrencesKeepOnlyTheMostRecentOnesOfASeriesMissedForYears() {
        val today = LocalDate.of(2026, 9, 25)
        val master = dailyTask(firstDay = today.minusYears(3))

        val missed = expander.missedOpenOccurrences(master, startOfDay(today))

        assertEquals(TaskRecurrenceExpander.MAX_MISSED_OCCURRENCES_PER_SERIES, missed.size)
        assertEquals(at(today.minusDays(30), 9), missed.first().startAtMillis)
        assertEquals(at(today.minusDays(1), 9), missed.last().startAtMillis)
    }

    @Test
    fun missedOpenOccurrencesFindAnOldMissOfASeriesOtherwiseDone() {
        val today = LocalDate.of(2026, 9, 25)
        val firstDay = today.minusYears(4)
        val master = dailyTask(firstDay = firstDay, rule = "FREQ=YEARLY")
        val allButFirstDone = master.withClosedOccurrences(*(1L..3L).map { at(firstDay.plusYears(it), 9) }.toLongArray())

        assertEquals(
            listOf(at(firstDay, 9)),
            expander.missedOpenOccurrences(allButFirstDone, startOfDay(today)).map { it.startAtMillis },
        )
    }

    @Test
    fun onlyOpenDatedSeriesHaveMissedOccurrences() {
        val today = LocalDate.of(2026, 9, 25)
        val single = dailyTask(firstDay = today.minusDays(2), rule = null).copy(resourceHref = "/tasks/single.ics")
        val undated = dailyTask(firstDay = today).copy(resourceHref = "/tasks/undated.ics", startAtMillis = null, dueAtMillis = null)
        val closedSeries = dailyTask(firstDay = today.minusDays(2)).copy(resourceHref = "/tasks/closed.ics", isCompleted = true)
        val future = dailyTask(firstDay = today).copy(resourceHref = "/tasks/future.ics")
        val open = dailyTask(firstDay = today.minusDays(2))

        val missed = expander.missedOccurrences(listOf(single, undated, closedSeries, future, open), startOfDay(today))

        assertEquals(listOf(open.resourceHref, open.resourceHref), missed.map { it.resourceHref })
        assertEquals(listOf(at(today.minusDays(2), 9), at(today.minusDays(1), 9)), missed.map { it.startAtMillis })
    }

    private fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun at(date: LocalDate, hour: Int): Long = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun dailyTask(firstDay: LocalDate, rule: String? = "FREQ=DAILY") = TaskEntity(
        uid = "task-daily",
        collectionHref = "/tasks/",
        resourceHref = "/tasks/task-daily.ics",
        title = "Daily",
        notes = null,
        dueAtMillis = at(firstDay, 10),
        startAtMillis = at(firstDay, 9),
        startHasTime = true,
        dueHasTime = true,
        completedAtMillis = null,
        isCompleted = false,
        status = "NEEDS-ACTION",
        priority = null,
        recurrenceRule = rule,
        timezoneId = zone.id,
        color = 0,
    )

    private fun TaskEntity.withClosedOccurrences(vararg recurrenceIds: Long): TaskEntity = copy(
        recurrenceOverridesJson = RecurrenceOverrideCodec.encodeTasks(
            recurrenceIds.map { recurrenceId ->
                val occurrence = occurrenceAt(recurrenceId)
                TaskRecurrenceOverride.fromTask(
                    recurrenceId,
                    occurrence.copy(isCompleted = true, status = "COMPLETED", completedAtMillis = recurrenceId),
                )
            },
        ),
    )
}
