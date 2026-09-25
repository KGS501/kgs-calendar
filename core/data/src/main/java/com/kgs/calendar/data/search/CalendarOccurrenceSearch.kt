package com.kgs.calendar.data.search

import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.recurrence.RecurrenceExpander
import com.kgs.calendar.data.recurrence.TaskRecurrenceExpander
import java.time.ZoneId
import java.util.Locale

enum class CalendarSearchMode {
    Text,
    TextAndLabels,
    LabelsOnly,
}

/**
 * Searches calendar masters and materializes matching recurrence series into
 * the occurrences that belong to the caller's loaded recurrence window.
 */
class CalendarOccurrenceSearch(
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val eventExpander = RecurrenceExpander(zoneId)
    private val taskExpander = TaskRecurrenceExpander(eventExpander)

    fun events(
        masters: List<EventEntity>,
        query: String,
        mode: CalendarSearchMode,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        cancellationCheck: () -> Unit = {},
    ): List<EventEntity> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isEmpty()) return emptyList()
        return masters.flatMap { master ->
            cancellationCheck()
            val overrides = RecurrenceOverrideCodec.decodeEvents(master.recurrenceOverridesJson)
            val masterMatches = master.matches(normalizedQuery, mode)
            val matchingOverrideExists = overrides.any { override ->
                !override.status.equals("CANCELLED", ignoreCase = true) &&
                    override.applyTo(master).matches(normalizedQuery, mode)
            }
            if (!masterMatches && !matchingOverrideExists) {
                emptyList()
            } else if (master.hasRecurrence()) {
                val expanded = eventExpander.expand(master, rangeStartMillis, rangeEndMillis)
                val movedIntoWindow = overrides
                    .asSequence()
                    .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
                    .map { it.applyTo(master) }
                    .filter { it.intersects(rangeStartMillis, rangeEndMillis) }
                    .toList()
                (expanded + movedIntoWindow)
                    .filter { it.intersects(rangeStartMillis, rangeEndMillis) }
                    .distinctBy { occurrence ->
                        overrides.firstOrNull { it.matchesOccurrence(occurrence) }?.recurrenceIdMillis
                            ?: occurrence.startsAtMillis
                    }
                    .filter { masterMatches || it.matches(normalizedQuery, mode) }
            } else {
                listOf(master)
            }
        }.sortedBy(EventEntity::startsAtMillis)
    }

    fun tasks(
        masters: List<TaskEntity>,
        query: String,
        mode: CalendarSearchMode,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        cancellationCheck: () -> Unit = {},
    ): List<TaskEntity> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isEmpty()) return emptyList()
        return masters.flatMap { master ->
            cancellationCheck()
            val overrides = RecurrenceOverrideCodec.decodeTasks(master.recurrenceOverridesJson)
            val masterMatches = master.matches(normalizedQuery, mode)
            val matchingOverrideExists = overrides.any { override ->
                override.applyTo(master).matches(normalizedQuery, mode)
            }
            if (!masterMatches && !matchingOverrideExists) {
                emptyList()
            } else if (master.hasRecurrence()) {
                val expanded = taskExpander.expand(master, rangeStartMillis, rangeEndMillis)
                val movedIntoWindow = overrides
                    .asSequence()
                    .map { it.applyTo(master) }
                    .filter { it.intersects(rangeStartMillis, rangeEndMillis) }
                    .toList()
                val occurrences = (expanded + movedIntoWindow)
                    .filter { it.intersects(rangeStartMillis, rangeEndMillis) }
                    .distinctBy { occurrence ->
                        overrides.firstOrNull { it.matchesOccurrence(occurrence) }?.recurrenceIdMillis
                            ?: occurrence.startAtMillis
                            ?: occurrence.dueAtMillis
                            ?: 0L
                    }
                    .filter { masterMatches || it.matches(normalizedQuery, mode) }
                if (occurrences.isEmpty() && masterMatches && master.startAtMillis == null && master.dueAtMillis == null) {
                    listOf(master)
                } else {
                    occurrences
                }
            } else {
                listOf(master)
            }
        }.sortedBy { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
    }
}

private fun EventEntity.hasRecurrence(): Boolean =
    !recurrenceRule.isNullOrBlank() || !rDatesCsv.isNullOrBlank()

private fun EventEntity.intersects(rangeStartMillis: Long, rangeEndMillis: Long): Boolean =
    startsAtMillis < rangeEndMillis && endsAtMillis.coerceAtLeast(startsAtMillis + 1L) > rangeStartMillis

private fun EventEntity.matches(query: String, mode: CalendarSearchMode): Boolean {
    val labelsMatch = categories.containsQuery(query)
    val textMatches = listOf(title, description, location, organizerJson, attendeesJson)
        .any { it.containsQuery(query) }
    return when (mode) {
        CalendarSearchMode.Text -> textMatches
        CalendarSearchMode.TextAndLabels -> textMatches || labelsMatch
        CalendarSearchMode.LabelsOnly -> labelsMatch
    }
}

private fun TaskEntity.hasRecurrence(): Boolean =
    !recurrenceRule.isNullOrBlank() || !rDatesCsv.isNullOrBlank()

private fun TaskEntity.intersects(rangeStartMillis: Long, rangeEndMillis: Long): Boolean {
    val start = startAtMillis ?: dueAtMillis ?: return false
    val end = when {
        dueAtMillis != null && dueAtMillis > start -> dueAtMillis
        else -> start + 1L
    }
    return start < rangeEndMillis && end > rangeStartMillis
}

private fun TaskEntity.matches(query: String, mode: CalendarSearchMode): Boolean {
    val labelsMatch = categories.containsQuery(query)
    val textMatches = listOf(title, notes, location, url).any { it.containsQuery(query) }
    return when (mode) {
        CalendarSearchMode.Text -> textMatches
        CalendarSearchMode.TextAndLabels -> textMatches || labelsMatch
        CalendarSearchMode.LabelsOnly -> labelsMatch
    }
}

private fun String?.containsQuery(query: String): Boolean =
    !isNullOrBlank() && lowercase(Locale.ROOT).contains(query)
