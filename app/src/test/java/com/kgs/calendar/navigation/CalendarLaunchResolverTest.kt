package com.kgs.calendar.navigation

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarOccurrenceEnvelope
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.model.CalendarViewMode
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarLaunchResolverTest {
    private val zoneId = ZoneId.of("Europe/Berlin")
    private val recurrenceId = LocalDate.of(2026, 7, 10).atTime(9, 0).atZone(zoneId).toInstant().toEpochMilli()
    private val movedStart = LocalDate.of(2026, 7, 10).atTime(15, 0).atZone(zoneId).toInstant().toEpochMilli()
    private val master = task(startAtMillis = recurrenceId)
    private val moved = task(startAtMillis = movedStart)
    private val resolver = CalendarLaunchResolver(
        eventByResource = { null },
        taskByResource = { resourceHref -> master.takeIf { it.resourceHref == resourceHref } },
        expandEvents = { _, _, _ -> emptyList<CalendarOccurrenceEnvelope<EventEntity>>() },
        expandTasks = { _, _, _ ->
            listOf(CalendarOccurrenceEnvelope(CalendarOccurrenceId.Task(master.resourceHref, recurrenceId), moved))
        },
        zoneId = zoneId,
    )

    @Test
    fun resolvesExactMovedTaskOccurrence() = runTest {
        val target = CalendarLaunchTarget(
            action = CalendarLaunchAction.OpenOccurrence,
            occurrence = CalendarOccurrenceId.Task(master.resourceHref, recurrenceId),
        )

        val resolution = resolver.resolve(target)

        assertEquals(moved, resolution?.task)
        assertEquals(LocalDate.of(2026, 7, 10), resolution?.date)
        assertEquals(CalendarViewMode.Day, resolution?.viewMode)
    }

    @Test
    fun missingOccurrenceDoesNotFallBackToMaster() = runTest {
        val target = CalendarLaunchTarget(
            action = CalendarLaunchAction.OpenOccurrence,
            occurrence = CalendarOccurrenceId.Task(master.resourceHref, recurrenceId + 1),
        )

        assertNull(resolver.resolve(target))
    }

    private fun task(startAtMillis: Long) = TaskEntity(
        uid = "task-42",
        collectionHref = "tasks/",
        resourceHref = "tasks/42.ics",
        title = "Task",
        notes = null,
        dueAtMillis = startAtMillis + 60 * 60 * 1000L,
        startAtMillis = startAtMillis,
        completedAtMillis = null,
        isCompleted = false,
        priority = null,
        recurrenceRule = "FREQ=DAILY",
        color = 0,
    )
}
