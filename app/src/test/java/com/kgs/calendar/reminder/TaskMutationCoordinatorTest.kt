package com.kgs.calendar.reminder

import com.kgs.calendar.domain.model.CalendarOccurrenceId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskMutationCoordinatorTest {
    private val statusWrites = mutableListOf<Pair<String, String>>()
    private val cancelledOccurrences = mutableListOf<CalendarOccurrenceId.Task>()
    private val cancelledResources = mutableListOf<String>()
    private var pushCount = 0
    private var rescheduleCount = 0
    private var widgetUpdateCount = 0

    private val reconciler = object : TaskNotificationReconciler {
        override suspend fun cancelOccurrence(occurrenceId: CalendarOccurrenceId.Task) {
            cancelledOccurrences += occurrenceId
        }

        override suspend fun cancelResource(resourceHref: String) {
            cancelledResources += resourceHref
        }
    }

    private val coordinator = TaskMutationCoordinator(
        persistStatus = { resourceHref, status -> statusWrites += resourceHref to status },
        pushPendingChanges = { pushCount++ },
        notificationReconciler = reconciler,
        rescheduleReminders = { rescheduleCount++ },
        updateWidgets = { widgetUpdateCount++ },
        nowMillis = { 123L },
    )

    @Test
    fun completedAndCancelledStatusesCancelThenReconcile() = runTest {
        coordinator.setStatus("tasks/42.ics", "COMPLETED")
        coordinator.setStatus("tasks/42.ics", "CANCELLED")

        assertEquals(
            listOf("tasks/42.ics" to "COMPLETED", "tasks/42.ics" to "CANCELLED"),
            statusWrites,
        )
        assertEquals(listOf("tasks/42.ics", "tasks/42.ics"), cancelledResources)
        assertEquals(2, pushCount)
        assertEquals(2, rescheduleCount)
        assertEquals(2, widgetUpdateCount)
    }

    @Test
    fun occurrenceMutationCancelsOnlyThatOccurrence() = runTest {
        val occurrence = CalendarOccurrenceId.Task("tasks/42.ics", 1_700_000_000_000)

        coordinator.setStatus("tasks/42.ics", "COMPLETED", occurrence)

        assertEquals(listOf(occurrence), cancelledOccurrences)
        assertTrue(cancelledResources.isEmpty())
    }

    @Test
    fun openStatusDoesNotCancelPostedNotification() = runTest {
        coordinator.setStatus("tasks/42.ics", "IN-PROCESS")

        assertTrue(cancelledOccurrences.isEmpty())
        assertTrue(cancelledResources.isEmpty())
    }

    @Test
    fun customTaskEditUsesTheSameStatusSideEffectsWithoutASecondStatusWrite() = runTest {
        var editCount = 0

        coordinator.mutateStatus("tasks/42.ics", "CANCELLED") { editCount++ }

        assertEquals(1, editCount)
        assertTrue(statusWrites.isEmpty())
        assertEquals(listOf("tasks/42.ics"), cancelledResources)
        assertEquals(1, pushCount)
        assertEquals(1, rescheduleCount)
        assertEquals(1, widgetUpdateCount)
    }
}
