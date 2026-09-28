package com.kgs.calendar.ui.model

import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.task.isOpen
import com.kgs.calendar.domain.task.occurrenceIdOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The due date is authoritative; start is only a fallback for tasks without a due date. */
internal fun TaskEntity.effectiveOverdueAtMillis(): Long? = dueAtMillis ?: startAtMillis

internal fun TaskEntity.isOverdueTask(
    today: LocalDate,
    zoneId: ZoneId,
): Boolean {
    if (!isOpen()) return false
    val effectiveAtMillis = effectiveOverdueAtMillis() ?: return false
    val effectiveDate = Instant.ofEpochMilli(effectiveAtMillis).atZone(zoneId).toLocalDate()
    return effectiveDate.isBefore(today)
}

/**
 * Returns active overdue tasks in deterministic popover order. [tasks] may hold the same missed
 * occurrence of a recurring task twice (an ended series is also listed as that occurrence); each
 * entry appears once.
 */
internal fun orderedOverdueTasks(
    tasks: Iterable<TaskEntity>,
    today: LocalDate,
    zoneId: ZoneId,
): List<TaskEntity> =
    tasks
        .filter { it.isOverdueTask(today, zoneId) }
        .distinctBy { it.overdueEntryKey() }
        .sortedWith(overdueTaskComparator)

/**
 * Identifies an entry of the overdue list: a single task by its resource, an occurrence of a
 * recurring task by its resource and RECURRENCE-ID, since several missed occurrences are listed.
 */
internal fun TaskEntity.overdueEntryKey(): String =
    occurrenceIdOrNull()?.let { "${it.resourceHref}@${it.recurrenceIdMillis}" } ?: resourceHref

private val overdueTaskComparator =
    compareBy<TaskEntity> { it.effectiveOverdueAtMillis() ?: Long.MAX_VALUE }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        .thenBy { it.title }
        .thenBy { it.collectionHref }
        .thenBy { it.resourceHref }
        .thenBy { it.uid }

internal fun Iterable<TaskEntity>.highestOverduePriority(): Int? =
    mapNotNull { it.priority?.takeIf { priority -> priority in 1..9 } }
        .minOrNull()
