package com.kgs.calendar.reminder

import com.kgs.calendar.domain.model.CalendarOccurrenceId

interface TaskNotificationReconciler {
    suspend fun cancelOccurrence(occurrenceId: CalendarOccurrenceId.Task)
    suspend fun cancelResource(resourceHref: String)
}

fun String.clearsTaskNotifications(): Boolean =
    equals("COMPLETED", ignoreCase = true) || equals("CANCELLED", ignoreCase = true)

class TaskMutationCoordinator(
    private val persistStatus: suspend (resourceHref: String, status: String) -> Unit,
    private val pushPendingChanges: suspend (startedAtMillis: Long) -> Unit,
    private val notificationReconciler: TaskNotificationReconciler,
    private val rescheduleReminders: suspend () -> Unit,
    private val updateWidgets: suspend () -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun setStatus(
        resourceHref: String,
        status: String,
        occurrenceId: CalendarOccurrenceId.Task? = null,
    ) = mutateStatus(resourceHref, status, occurrenceId) {
        persistStatus(resourceHref, status)
    }

    suspend fun mutateStatus(
        resourceHref: String,
        status: String,
        occurrenceId: CalendarOccurrenceId.Task? = null,
        mutation: suspend () -> Unit,
    ) {
        val startedAtMillis = nowMillis()
        mutation()

        val failures = mutableListOf<Throwable>()
        if (status.clearsTaskNotifications()) {
            runCatching {
                if (occurrenceId == null) {
                    notificationReconciler.cancelResource(resourceHref)
                } else {
                    notificationReconciler.cancelOccurrence(occurrenceId)
                }
            }.exceptionOrNull()?.let(failures::add)
        }
        runCatching { pushPendingChanges(startedAtMillis) }.exceptionOrNull()?.let(failures::add)
        runCatching { rescheduleReminders() }.exceptionOrNull()?.let(failures::add)
        runCatching { updateWidgets() }.exceptionOrNull()?.let(failures::add)
        failures.firstOrNull()?.let { throw it }
    }
}
