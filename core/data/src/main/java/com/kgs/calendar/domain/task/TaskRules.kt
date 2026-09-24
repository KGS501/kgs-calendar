package com.kgs.calendar.domain.task

import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.domain.model.TaskStatus

val TaskEntity.taskStatus: TaskStatus?
    get() = TaskStatus.from(status)

/** Normalised effective status, falling back to the legacy isCompleted boolean. */
fun TaskEntity.effectiveStatus(): String = status?.uppercase()
    ?: if (isCompleted) TaskStatus.Completed.value else TaskStatus.NeedsAction.value

/**
 * A task is "inactive" when it's done OR cancelled — both should be greyed out and have
 * their priority animation suppressed. A COMPLETED status alone does not count while
 * isCompleted is false; see [isOpen] for the stricter check.
 */
fun TaskEntity.isInactive(): Boolean = isCompleted || effectiveStatus() == TaskStatus.Cancelled.value

/** Open unless isCompleted is set or the status is COMPLETED or CANCELLED. */
fun TaskEntity.isOpen(): Boolean = isOpenTask(isCompleted, status)

internal fun isOpenTask(isCompleted: Boolean, status: String?): Boolean =
    !isCompleted && TaskStatus.from(status)?.closesTask != true

/** Sort weight for the "Status" sort: in progress first, then open, completed, cancelled, others. */
fun TaskEntity.statusSortRank(): Int = when (effectiveStatus()) {
    TaskStatus.InProcess.value -> 0
    TaskStatus.NeedsAction.value -> 1
    TaskStatus.Completed.value -> 2
    TaskStatus.Cancelled.value -> 3
    else -> 4
}

/** 1 for PRIORITY 1 (highest), 0 for PRIORITY 9 and for tasks without priority. */
fun taskPriorityIntensity(priority: Int?): Float {
    val value = priority?.coerceIn(1, 9) ?: 9
    return ((9 - value) / 8f).coerceIn(0f, 1f)
}

/** ARGB colour of an iCalendar PRIORITY; values outside 1..9 are clamped. */
fun priorityColorArgb(priority: Int): Int = when (priority.coerceIn(1, 9)) {
    1 -> 0xFFD93025.toInt()
    2 -> 0xFFE7602A.toInt()
    3 -> 0xFFF29900.toInt()
    4 -> 0xFFF8C542.toInt()
    5 -> 0xFFFFD84D.toInt()
    6 -> 0xFF55A8F5.toInt()
    7 -> 0xFF2E8FD8.toInt()
    8 -> 0xFF20A386.toInt()
    else -> 0xFF2E7D32.toInt()
}

fun TaskEntity.displayColor(mode: TaskColorMode): Int =
    manualColor ?: when (mode) {
        TaskColorMode.Collection -> color
        TaskColorMode.Priority -> priority?.let(::priorityColorArgb) ?: color
    }
