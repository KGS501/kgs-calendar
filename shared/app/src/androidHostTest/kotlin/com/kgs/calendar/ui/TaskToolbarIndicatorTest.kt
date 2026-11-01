package com.kgs.calendar.ui

import com.kgs.calendar.data.local.entity.TaskEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskToolbarIndicatorTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 8, 22)

    @Test
    fun futureAndUndatedTasksDoNotShowTheIndicator() {
        assertFalse(
            hasTaskToolbarAttention(
                tasks = listOf(task("future", today.plusDays(1)), task("inbox", null)),
                today = today,
                zoneId = zone,
            ),
        )
    }

    @Test
    fun activeTaskTodayOrOverdueShowsTheIndicator() {
        assertTrue(hasTaskToolbarAttention(listOf(task("today", today)), today, zone))
        assertTrue(hasTaskToolbarAttention(listOf(task("overdue", today.minusDays(1))), today, zone))
    }

    @Test
    fun completedAndCancelledTasksDoNotShowTheIndicator() {
        assertFalse(
            hasTaskToolbarAttention(
                tasks = listOf(
                    task("completed", today, completed = true, status = "COMPLETED"),
                    task("cancelled", today.minusDays(1), status = "CANCELLED"),
                ),
                today = today,
                zoneId = zone,
            ),
        )
    }

    private fun task(
        id: String,
        date: LocalDate?,
        completed: Boolean = false,
        status: String = "NEEDS-ACTION",
    ): TaskEntity {
        val due = date?.atTime(12, 0)?.atZone(zone)?.toInstant()?.toEpochMilli()
        return TaskEntity(
            uid = id,
            collectionHref = "/tasks/",
            resourceHref = "/tasks/$id.ics",
            title = id,
            notes = null,
            dueAtMillis = due,
            startAtMillis = null,
            completedAtMillis = null,
            isCompleted = completed,
            status = status,
            priority = null,
            color = 0,
        )
    }
}
