package com.kgs.calendar.data.recurrence

import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.model.CalendarOccurrenceEnvelope

/**
 * Reuses the RFC 5545 recurrence-set implementation for VTODO. DTSTART is the
 * recurrence anchor when present; otherwise DUE is used, matching common CalDAV
 * task-server behavior.
 */
class TaskRecurrenceExpander(
    private val eventExpander: RecurrenceExpander,
) {
    fun expand(master: TaskEntity, rangeStartMillis: Long, rangeEndMillis: Long): List<TaskEntity> {
        val anchor = master.startAtMillis ?: master.dueAtMillis ?: return emptyList()
        val end = when {
            master.startAtMillis != null && master.dueAtMillis != null && master.dueAtMillis > master.startAtMillis ->
                master.dueAtMillis
            else -> anchor + DEFAULT_TASK_DURATION_MILLIS
        }
        val recurrenceEvent = EventEntity(
            uid = master.uid,
            collectionHref = master.collectionHref,
            resourceHref = master.resourceHref,
            title = master.title,
            description = master.notes,
            location = master.location,
            startsAtMillis = anchor,
            endsAtMillis = end,
            allDay = !(master.startHasTime || master.dueHasTime),
            recurrenceRule = master.recurrenceRule,
            isRecurring = !master.recurrenceRule.isNullOrBlank() || !master.rDatesCsv.isNullOrBlank(),
            exDatesCsv = master.exDatesCsv,
            rDatesCsv = master.rDatesCsv,
            timezoneId = master.timezoneId,
            color = master.color,
        )
        val overrides = RecurrenceOverrideCodec.decodeTasks(master.recurrenceOverridesJson)
            .associateBy { it.recurrenceIdMillis }
        return eventExpander.expand(recurrenceEvent, rangeStartMillis, rangeEndMillis).mapNotNull { occurrence ->
            val recurrenceId = occurrence.startsAtMillis
            val shift = recurrenceId - anchor
            val expanded = master.copy(
                startAtMillis = master.startAtMillis?.plus(shift),
                dueAtMillis = master.dueAtMillis?.plus(shift),
            )
            val override = overrides[recurrenceId]
            when {
                override?.status.equals("CANCELLED", ignoreCase = true) -> null
                override != null -> override.applyTo(expanded)
                else -> expanded
            }
        }
    }

    fun expandWithIdentity(
        master: TaskEntity,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<CalendarOccurrenceEnvelope<TaskEntity>> {
        val overrides = RecurrenceOverrideCodec.decodeTasks(master.recurrenceOverridesJson)
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
        return expand(master, rangeStartMillis, rangeEndMillis).map { occurrence ->
            val recurrenceIdMillis = overrides.firstOrNull { override ->
                override.startAtMillis == occurrence.startAtMillis &&
                    override.dueAtMillis == occurrence.dueAtMillis &&
                    override.title == occurrence.title
            }?.recurrenceIdMillis ?: occurrence.startAtMillis ?: occurrence.dueAtMillis ?: 0L
            CalendarOccurrenceEnvelope(
                occurrenceId = CalendarOccurrenceId.Task(master.resourceHref, recurrenceIdMillis),
                item = occurrence,
            )
        }
    }

    companion object {
        private const val DEFAULT_TASK_DURATION_MILLIS = 30L * 60L * 1000L
    }
}
