package com.kgs.calendar.ui

import androidx.compose.ui.text.style.TextDecoration
import com.kgs.calendar.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskCancellationPresentationTest {
    @Test
    fun cancellationStrikesAllTaskCardTextWhileCompletionDoesNot() {
        assertEquals(TextDecoration.LineThrough, task(status = "cancelled").cardTextDecoration())
        assertNull(task(status = "COMPLETED", completed = true).cardTextDecoration())
        assertNull(task(status = "IN-PROCESS").cardTextDecoration())
    }

    private fun task(status: String, completed: Boolean = false): TaskEntity = TaskEntity(
        uid = "task",
        collectionHref = "/tasks/",
        resourceHref = "/tasks/task.ics",
        title = "Task",
        notes = null,
        dueAtMillis = 1L,
        startAtMillis = 0L,
        completedAtMillis = if (completed) 1L else null,
        isCompleted = completed,
        status = status,
        priority = null,
        color = 0,
    )
}
