package com.kgs.calendar.domain.task

import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRulesTest {
    @Test
    fun effectiveStatusUppercasesTheRawStatusAndFallsBackToTheCompletedFlag() {
        assertEquals("IN-PROCESS", task(status = "in-process").effectiveStatus())
        assertEquals("X-CUSTOM", task(status = "x-custom").effectiveStatus())
        assertEquals("COMPLETED", task(isCompleted = true).effectiveStatus())
        assertEquals("NEEDS-ACTION", task().effectiveStatus())
        assertEquals(TaskStatus.Other("x-custom"), task(status = "x-custom").taskStatus)
    }

    @Test
    fun rruleOrRdatesMakeATaskRecurring() {
        assertFalse(task().isRecurring)
        assertFalse(task().copy(recurrenceRule = " ", rDatesCsv = "").isRecurring)
        assertTrue(task().copy(recurrenceRule = "FREQ=WEEKLY").isRecurring)
        assertTrue(task().copy(rDatesCsv = "1790000000000,1790172800000").isRecurring)
    }

    @Test
    fun inactiveAndOpenDisagreeOnlyForACompletedStatusWithoutTheFlag() {
        val statusOnlyCompleted = task(status = "COMPLETED", isCompleted = false)

        assertFalse(statusOnlyCompleted.isInactive())
        assertFalse(statusOnlyCompleted.isOpen())

        listOf(
            task(),
            task(status = "IN-PROCESS"),
            task(status = "cancelled"),
            task(isCompleted = true),
            task(status = "NEEDS-ACTION", isCompleted = true),
            task(status = "X-CUSTOM"),
        ).forEach { task ->
            assertEquals(task.toString(), task.isInactive(), !task.isOpen())
        }
    }

    @Test
    fun openTaskRequiresNoCompletedFlagAndNoClosingStatus() {
        assertTrue(isOpenTask(isCompleted = false, status = null))
        assertTrue(isOpenTask(isCompleted = false, status = "X-CUSTOM"))
        assertFalse(isOpenTask(isCompleted = true, status = null))
        assertFalse(isOpenTask(isCompleted = false, status = "Cancelled"))
    }

    @Test
    fun statusSortRankOrdersInProgressFirstAndUnknownLast() {
        assertEquals(
            listOf(0, 1, 2, 3, 4, 1, 2),
            listOf(
                task(status = "IN-PROCESS"),
                task(status = "needs-action"),
                task(status = "COMPLETED"),
                task(status = "CANCELLED"),
                task(status = "X-CUSTOM"),
                task(),
                task(isCompleted = true),
            ).map { it.statusSortRank() },
        )
    }

    @Test
    fun priorityIntensityScalesFromNineToOne() {
        assertEquals(1f, taskPriorityIntensity(1), 0f)
        assertEquals(0.5f, taskPriorityIntensity(5), 0f)
        assertEquals(0f, taskPriorityIntensity(9), 0f)
        assertEquals(0f, taskPriorityIntensity(null), 0f)
        assertEquals(1f, taskPriorityIntensity(0), 0f)
        assertEquals(0f, taskPriorityIntensity(12), 0f)
    }

    @Test
    fun displayColorPrefersManualThenPriorityThenCollection() {
        assertEquals(0xFFD93025.toInt(), priorityColorArgb(1))
        assertEquals(0xFFD93025.toInt(), priorityColorArgb(-3))
        assertEquals(0xFF2E7D32.toInt(), priorityColorArgb(42))
        assertEquals(COLLECTION_COLOR, task(priority = 1).displayColor(TaskColorMode.Collection))
        assertEquals(priorityColorArgb(3), task(priority = 3).displayColor(TaskColorMode.Priority))
        assertEquals(COLLECTION_COLOR, task(priority = null).displayColor(TaskColorMode.Priority))
        assertEquals(0x123456, task(priority = 1, manualColor = 0x123456).displayColor(TaskColorMode.Priority))
    }

    private fun task(
        status: String? = null,
        isCompleted: Boolean = false,
        priority: Int? = null,
        manualColor: Int? = null,
    ) = TaskEntity(
        uid = "uid",
        collectionHref = "/cal/",
        resourceHref = "/cal/uid.ics",
        title = "Task",
        notes = null,
        dueAtMillis = null,
        startAtMillis = null,
        completedAtMillis = null,
        isCompleted = isCompleted,
        status = status,
        priority = priority,
        color = COLLECTION_COLOR,
        manualColor = manualColor,
    )

    private companion object {
        const val COLLECTION_COLOR = 0x00AA00
    }
}
