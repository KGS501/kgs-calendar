package com.kgs.calendar.ui

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import kotlin.coroutines.cancellation.CancellationException
import com.kgs.calendar.data.remote.isOnlySyncConflicts
import com.kgs.calendar.data.remote.isConnectionFailure
import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.model.EventEditPayload
import com.kgs.calendar.domain.model.TaskEditPayload
import com.kgs.calendar.domain.task.occurrenceIdOrNull
import com.kgs.calendar.reminder.TaskMutationCoordinator
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Event and task edits: save, push pending changes, then refresh widgets and reminders. */
/** What the editor does once its save is stored on this device. */
data class EditorSaveOutcome(
    val collectionHref: String?,
    val kind: HiddenSaveKind,
    /** Forget the converted item as well, as after creating. */
    val finishCreation: Boolean,
)

sealed interface EditorSaveState {
    data object Idle : EditorSaveState
    data object Saving : EditorSaveState
    data class Saved(val outcome: EditorSaveOutcome) : EditorSaveState

    /** Nothing was stored: the editor stays open with its draft, and the message says why. */
    data object Failed : EditorSaveState
}

class CalendarEditActions constructor(
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
            message.value = null
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
            repository.deleteEvent(eventUid, moveToTrash = false)
        }
    }

    fun convertTaskToEvent(taskUid: String, payload: EventEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.createEvent(payload)
            repository.deleteTask(taskUid, moveToTrash = false)
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
            message.value = null
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
            taskMutationCoordinator.setStatus(task.resourceHref, status, task.occurrenceIdOrNull())
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

    /** Settles an upload conflict with this device's version ([keepLocal]) or the server's. */
    fun resolveConflict(resourceHref: String, keepLocal: Boolean) {
        scope.launch {
            runCatching {
                if (keepLocal) repository.resolveConflictKeepingLocal(resourceHref) else repository.resolveConflictUsingServer(resourceHref)
            }.onSuccess {
                message.value = null
                widgetRefresher.updateAll()
            }.onFailure {
                message.value = it.message ?: "Could not save changes."
            }
            runCatching { reminderRescheduler.reschedule() }
        }
    }

    private fun runEdit(rescheduleReminders: Boolean = false, block: suspend () -> Unit) {
        val tracked = takeTrackedSave()
        scope.launch {
            val startedAt = nowMillis()
            val failure = commitLocally(tracked, block)
            if (failure == null) {
                // Saved on this device either way. Offline or in conflict, the change waits on its
                // item, which shows it among the problems; a notice would only repeat that.
                try {
                    repository.pushPendingChangesCreatedSince(startedAt)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    if (!error.isConnectionFailure() && !error.isOnlySyncConflicts()) {
                        message.value = "Saved on this device, but the upload failed: ${error.message}"
                    }
                }
                runCatching { widgetRefresher.updateAll() }
            }
            if (rescheduleReminders) {
                runCatching { reminderRescheduler.reschedule() }
            }
        }
    }

    private fun runTaskStatusMutation(mutation: suspend () -> Unit) {
        val tracked = takeTrackedSave()
        scope.launch { commitLocally(tracked, mutation) }
    }

    /** Runs the local part of an edit and settles its tracked save. Returns the failure, if any. */
    private suspend fun commitLocally(tracked: EditorSaveOutcome?, commit: suspend () -> Unit): Throwable? {
        val failure = try {
            commit()
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            error
        }
        message.value = failure?.let { it.message ?: "Could not save changes." }
        if (tracked != null) editorSave.value = if (failure == null) EditorSaveState.Saved(tracked) else EditorSaveState.Failed
        return failure
    }

    /**
     * The editor's save in progress. The sheet closes, and its draft goes, only on [EditorSaveState.Saved].
     * This lives here because a save must outlive the composition: rotating while saving still ends it.
     */
    val editorSave = MutableStateFlow<EditorSaveState>(EditorSaveState.Idle)
    private var saveToTrack: EditorSaveOutcome? = null

    /** Runs [save], one edit of this class, for the editor. Ignored while an earlier save is running. */
    fun trackEditorSave(outcome: EditorSaveOutcome, save: () -> Unit) {
        if (editorSave.value is EditorSaveState.Saving) return
        editorSave.value = EditorSaveState.Saving
        saveToTrack = outcome
        save()
        saveToTrack = null
    }

    private fun takeTrackedSave(): EditorSaveOutcome? = saveToTrack.also { saveToTrack = null }

    fun editorSaveHandled() {
        editorSave.value = EditorSaveState.Idle
    }

    private fun effectiveTaskStatus(resourceHref: String, payload: TaskEditPayload): String {
        payload.status?.takeIf { it.isNotBlank() }?.let { return it }
        val existing = currentState().allTasks.firstOrNull { it.resourceHref == resourceHref }
        existing?.status?.takeIf { payload.isCompleted == existing.isCompleted }?.let { return it }
        return if (payload.isCompleted) "COMPLETED" else "NEEDS-ACTION"
    }
}
