package com.kgs.calendar.widget.update

import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.recurrence.occurrenceAt
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.model.TaskStatus
import com.kgs.calendar.domain.task.isRecurring
import com.kgs.calendar.domain.task.occurrenceIdOrNull
import com.kgs.calendar.reminder.TaskMutationCoordinator

/**
 * Toggles a task from a widget's status circle between open and completed. A recurring task
 * changes only the occurrence the row showed ([occurrenceMillis]), through the same
 * occurrence-aware path the calendar uses; the rest of the series stays as it is.
 */
internal class WidgetTaskToggle(
    private val taskByResource: suspend (String) -> TaskEntity?,
    /** The occurrence a task list shows for a recurring master; used when the tap carried none. */
    private val currentOccurrence: (TaskEntity) -> TaskEntity?,
    private val coordinator: TaskMutationCoordinator,
) {
    suspend fun toggle(resourceHref: String, occurrenceMillis: Long?) {
        val task = taskByResource(resourceHref) ?: return
        val occurrenceId = when {
            !task.isRecurring || (task.startAtMillis == null && task.dueAtMillis == null) -> null
            occurrenceMillis != null -> CalendarOccurrenceId.Task(task.resourceHref, occurrenceMillis)
            // A row rendered before occurrences were passed along: use the one the list shows now,
            // and never fall back to changing the whole series.
            else -> currentOccurrence(task)?.occurrenceIdOrNull() ?: return
        }
        val shown = occurrenceId?.let { task.occurrenceAt(it.recurrenceIdMillis) } ?: task
        val nextStatus = if (shown.isCompleted || shown.status.equals(TaskStatus.Completed.value, ignoreCase = true)) {
            TaskStatus.NeedsAction.value
        } else {
            TaskStatus.Completed.value
        }
        coordinator.setStatus(task.resourceHref, nextStatus, occurrenceId)
    }
}
