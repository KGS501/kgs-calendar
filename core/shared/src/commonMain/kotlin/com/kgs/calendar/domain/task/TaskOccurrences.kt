package com.kgs.calendar.domain.task

import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarOccurrenceId

/**
 * RECURRENCE-ID of this occurrence of a recurring task: the id of the stored override it was
 * built from, otherwise its own start (or due) time, which is how generated occurrences are keyed.
 * Null for a task without a date.
 */
fun TaskEntity.occurrenceRecurrenceIdMillis(): Long? =
    RecurrenceOverrideCodec.decodeTasks(recurrenceOverridesJson)
        .firstOrNull { it.matchesOccurrence(this) }
        ?.recurrenceIdMillis
        ?: startAtMillis
        ?: dueAtMillis

/**
 * The occurrence a status change on this row refers to: null for a single task, which changes as a
 * whole, and for a recurring task without a date, which has no occurrences.
 */
fun TaskEntity.occurrenceIdOrNull(): CalendarOccurrenceId.Task? =
    if (isRecurring) {
        occurrenceRecurrenceIdMillis()?.let { CalendarOccurrenceId.Task(resourceHref, it) }
    } else {
        null
    }
