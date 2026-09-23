package com.kgs.calendar.ui

import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.model.EventEditPayload
import com.kgs.calendar.domain.model.TaskEditPayload
import com.kgs.calendar.reminder.TaskMutationCoordinator
import com.kgs.calendar.ui.model.occurrenceStartForEdit
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Event and task edits: save, push pending changes, then refresh widgets and reminders. */
class CalendarEditActions internal constructor(
    private val scope: CoroutineScope,
    private val repository: CalendarRepository,
    private val taskMutationCoordinator: TaskMutationCoordinator,
    private val widgetRefresher: WidgetRefresher,
    private val reminderRescheduler: ReminderRescheduler,
    private val message: MutableStateFlow<String?>,
    private val currentState: () -> CalendarUiState,
) {
    fun createEvent(payload: EventEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.createEvent(payload)
        }
    }

    fun updateEvent(uid: String, payload: EventEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.updateEvent(uid, payload)
        }
    }

    fun updateEventOccurrence(uid: String, occurrenceStartMillis: Long, payload: EventEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.updateEventOccurrence(uid, occurrenceStartMillis, payload)
        }
    }

    fun updateEventFollowing(uid: String, occurrenceStartMillis: Long, payload: EventEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.updateEventFollowing(uid, occurrenceStartMillis, payload)
        }
    }

    fun updateEventManualColor(uid: String, manualColor: Int?) {
        runEdit {
            repository.updateEventManualColor(uid, manualColor)
            message.value = "Event color updated."
        }
    }

    fun moveTimedEvent(uid: String, occurrenceStartMillis: Long, date: LocalDate, start: LocalTime, end: LocalTime) {
        runEdit(rescheduleReminders = true) {
            repository.moveTimedEvent(uid, occurrenceStartMillis, date, start, end)
        }
    }

    fun moveAllDayEvent(uid: String, occurrenceStartMillis: Long, date: LocalDate) {
        runEdit(rescheduleReminders = true) {
            repository.moveAllDayEvent(uid, occurrenceStartMillis, date)
        }
    }

    fun copyEventTo(uid: String, collectionHref: String) {
        runEdit(rescheduleReminders = true) {
            repository.copyEventTo(uid, collectionHref)
        }
    }

    fun setEventParticipation(uid: String, partstat: String) {
        val state = currentState()
        val attendeeEmails = state.accounts.map { it.username } + listOfNotNull(state.account?.username)
        runEdit(rescheduleReminders = true) {
            repository.setEventParticipation(uid, attendeeEmails, partstat)
        }
    }

    fun createTask(payload: TaskEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.createTask(payload)
        }
    }

    fun convertEventToTask(eventUid: String, payload: TaskEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.createTask(payload)
            repository.deleteEvent(eventUid)
        }
    }

    fun convertTaskToEvent(taskUid: String, payload: EventEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.createEvent(payload)
            repository.deleteTask(taskUid)
        }
    }

    fun updateTask(resourceHref: String, payload: TaskEditPayload) {
        runTaskStatusMutation {
            taskMutationCoordinator.mutateStatus(resourceHref, effectiveTaskStatus(resourceHref, payload)) {
                repository.updateTask(resourceHref, payload)
            }
        }
    }

    fun updateTaskOccurrence(resourceHref: String, occurrenceStartMillis: Long, payload: TaskEditPayload) {
        runTaskStatusMutation {
            taskMutationCoordinator.mutateStatus(
                resourceHref = resourceHref,
                status = effectiveTaskStatus(resourceHref, payload),
                occurrenceId = CalendarOccurrenceId.Task(resourceHref, occurrenceStartMillis),
            ) {
                repository.updateTaskOccurrence(resourceHref, occurrenceStartMillis, payload)
            }
        }
    }

    fun updateTaskFollowing(resourceHref: String, occurrenceStartMillis: Long, payload: TaskEditPayload) {
        runTaskStatusMutation {
            taskMutationCoordinator.mutateStatus(resourceHref, effectiveTaskStatus(resourceHref, payload)) {
                repository.updateTaskFollowing(resourceHref, occurrenceStartMillis, payload)
            }
        }
    }

    fun updateTaskManualColor(uid: String, manualColor: Int?) {
        runEdit {
            repository.updateTaskManualColor(uid, manualColor)
            message.value = "Task color updated."
        }
    }

    fun setTaskCompleted(resourceHref: String, completed: Boolean) {
        runTaskStatusMutation {
            taskMutationCoordinator.setStatus(
                resourceHref,
                if (completed) "COMPLETED" else "NEEDS-ACTION",
            )
        }
    }

    fun setTaskStatus(task: TaskEntity, status: String) {
        runTaskStatusMutation {
            val occurrenceId = if (task.recurrenceRule.isNullOrBlank() && task.rDatesCsv.isNullOrBlank()) {
                null
            } else {
                CalendarOccurrenceId.Task(task.resourceHref, task.occurrenceStartForEdit())
            }
            taskMutationCoordinator.setStatus(task.resourceHref, status, occurrenceId)
        }
    }

    fun setTaskPriority(uid: String, priority: Int) {
        runEdit(rescheduleReminders = true) {
            repository.setTaskPriority(uid, priority)
        }
    }

    fun setTaskProgress(uid: String, progress: Int) {
        runEdit(rescheduleReminders = true) {
            repository.setTaskProgress(uid, progress)
        }
    }

    fun moveTimedTask(uid: String, occurrenceStartMillis: Long, date: LocalDate, start: LocalTime, end: LocalTime) {
        runEdit(rescheduleReminders = true) {
            repository.moveTimedTask(uid, occurrenceStartMillis, date, start, end)
        }
    }

    fun moveAllDayTask(uid: String, occurrenceStartMillis: Long, date: LocalDate) {
        runEdit(rescheduleReminders = true) {
            repository.moveAllDayTask(uid, occurrenceStartMillis, date)
        }
    }

    fun copyTaskTo(uid: String, collectionHref: String) {
        runEdit(rescheduleReminders = true) {
            repository.copyTaskTo(uid, collectionHref)
        }
    }

    fun deleteTask(uid: String) {
        runEdit(rescheduleReminders = true) {
            repository.deleteTask(uid)
        }
    }

    fun deleteEvent(uid: String) {
        runEdit(rescheduleReminders = true) {
            repository.deleteEvent(uid)
        }
    }

    fun deleteEventOccurrence(uid: String, occurrenceStartMillis: Long) {
        runEdit(rescheduleReminders = true) {
            repository.deleteEventOccurrence(uid, occurrenceStartMillis)
        }
    }

    fun deleteEventFollowing(uid: String, occurrenceStartMillis: Long) {
        runEdit(rescheduleReminders = true) {
            repository.deleteEventFollowing(uid, occurrenceStartMillis)
        }
    }

    private fun runEdit(rescheduleReminders: Boolean = false, block: suspend () -> Unit) {
        scope.launch {
            val startedAt = System.currentTimeMillis()
            runCatching {
                block()
                repository.pushPendingChangesCreatedSince(startedAt)
            }.onSuccess {
                message.value = null
                widgetRefresher.updateAll()
            }.onFailure {
                message.value = it.message ?: "Could not save changes."
            }
            if (rescheduleReminders) {
                runCatching { reminderRescheduler.reschedule() }
            }
        }
    }

    private fun runTaskStatusMutation(mutation: suspend () -> Unit) {
        scope.launch {
            runCatching { mutation() }
                .onSuccess { message.value = null }
                .onFailure { message.value = it.message ?: "Could not save changes." }
        }
    }

    private fun effectiveTaskStatus(resourceHref: String, payload: TaskEditPayload): String {
        payload.status?.takeIf { it.isNotBlank() }?.let { return it }
        val existing = currentState().allTasks.firstOrNull { it.resourceHref == resourceHref }
        existing?.status?.takeIf { payload.isCompleted == existing.isCompleted }?.let { return it }
        return if (payload.isCompleted) "COMPLETED" else "NEEDS-ACTION"
    }
}
