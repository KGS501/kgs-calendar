package com.kgs.calendar.ui.model

import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.task.isOpen
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

/** Returns active overdue tasks in deterministic popover order. */
internal fun orderedOverdueTasks(
    tasks: Iterable<TaskEntity>,
    today: LocalDate,
    zoneId: ZoneId,
): List<TaskEntity> =
    tasks
        .filter { it.isOverdueTask(today, zoneId) }
        .sortedWith(overdueTaskComparator)

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
