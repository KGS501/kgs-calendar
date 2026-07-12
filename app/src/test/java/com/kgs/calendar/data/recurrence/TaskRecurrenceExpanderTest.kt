package com.kgs.calendar.data.recurrence

import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.ical.TaskRecurrenceOverride
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import org.junit.Assert.assertEquals
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
}
