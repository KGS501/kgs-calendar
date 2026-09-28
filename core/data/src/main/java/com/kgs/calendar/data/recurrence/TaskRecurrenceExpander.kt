package com.kgs.calendar.data.recurrence

import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.model.CalendarOccurrenceEnvelope
import com.kgs.calendar.domain.task.isOpen
import com.kgs.calendar.domain.task.isRecurring

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

    /**
     * Materializes terminal per-occurrence overrides without expanding the whole series.
     * These exceptions are persisted inside their recurring master, so the task sidebar
     * cannot discover them through the normal completed-task database query.
     */
    fun inactiveOverrides(master: TaskEntity): List<TaskEntity> =
        RecurrenceOverrideCodec.decodeTasks(master.recurrenceOverridesJson)
            .filter { override ->
                override.isCompleted ||
                    override.status.equals("COMPLETED", ignoreCase = true) ||
                    override.status.equals("CANCELLED", ignoreCase = true)
            }
            .sortedBy { it.recurrenceIdMillis }
            .map { it.applyTo(master) }

    /**
     * The occurrence that stands for an open recurring series in task lists: the earliest open
     * occurrence that is due today or later, so completing it moves the list on to the next one,
     * the same occurrence the calendar shows on that day. A series without such an occurrence (it
     * has ended) falls back to its earliest open past occurrence. Null when no occurrence is open.
     */
    fun currentOpenOccurrence(master: TaskEntity, todayStartMillis: Long): TaskEntity? {
        CURRENT_OCCURRENCE_WINDOWS_MILLIS.forEach { window ->
            expand(master, todayStartMillis, todayStartMillis + window)
                .filter { it.isOpen() && (it.dueAtMillis ?: it.startAtMillis ?: Long.MIN_VALUE) >= todayStartMillis }
                .minByOrNull { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
                ?.let { return it }
        }
        val seriesStart = master.seriesStartMillis() ?: return null
        return expand(master, seriesStart, todayStartMillis)
            .filter { it.isOpen() }
            .minByOrNull { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
    }

    /**
     * Replaces every open recurring series in [tasks] by its [currentOpenOccurrence] and drops
     * series that have no open occurrence left. Single tasks, closed series and recurring tasks
     * without a date stay as they are.
     */
    fun withCurrentOccurrences(tasks: List<TaskEntity>, todayStartMillis: Long): List<TaskEntity> =
        tasks.mapNotNull { task ->
            if (!task.isRecurring || !task.isOpen() || (task.startAtMillis == null && task.dueAtMillis == null)) {
                task
            } else {
                currentOpenOccurrence(task, todayStartMillis)
            }
        }

    /**
     * The missed occurrences of the recurring [master]: open occurrences due (DUE, or DTSTART
     * without one) before [todayStartMillis]. The overdue list shows each of them until it is
     * completed or cancelled, next to the series' [currentOpenOccurrence] in the task list. Only
     * the most recent [limit] are returned, oldest first, so a series missed for years stays
     * bounded. Empty for a single task, a closed series and a recurring task without a date.
     */
    fun missedOpenOccurrences(
        master: TaskEntity,
        todayStartMillis: Long,
        limit: Int = MAX_MISSED_OCCURRENCES_PER_SERIES,
    ): List<TaskEntity> {
        if (!master.isRecurring || !master.isOpen() || limit <= 0) return emptyList()
        val seriesStart = master.seriesStartMillis()?.takeIf { it < todayStartMillis } ?: return emptyList()
        // Look back in growing windows: a series missed daily fills the limit from the first one,
        // and only a series with few missed occurrences is expanded from its start.
        for (window in MISSED_OCCURRENCE_WINDOWS_MILLIS) {
            val from = maxOf(seriesStart, todayStartMillis - window)
            val missed = missedOpenOccurrencesBetween(master, from, todayStartMillis)
            if (missed.size >= limit || from == seriesStart) return missed.takeLast(limit)
        }
        return missedOpenOccurrencesBetween(master, seriesStart, todayStartMillis).takeLast(limit)
    }

    /** The [missedOpenOccurrences] of every open recurring series in [tasks]. */
    fun missedOccurrences(tasks: List<TaskEntity>, todayStartMillis: Long): List<TaskEntity> =
        tasks.flatMap { missedOpenOccurrences(it, todayStartMillis) }

    private fun missedOpenOccurrencesBetween(master: TaskEntity, fromMillis: Long, todayStartMillis: Long): List<TaskEntity> =
        expand(master, fromMillis, todayStartMillis)
            .filter { it.isOpen() && (it.dueAtMillis ?: it.startAtMillis ?: Long.MAX_VALUE) < todayStartMillis }
            .sortedBy { it.dueAtMillis ?: it.startAtMillis }

    private fun TaskEntity.seriesStartMillis(): Long? =
        listOfNotNull(startAtMillis, dueAtMillis)
            .plus(rDatesCsv?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty())
            .minOrNull()

    companion object {
        /** How many missed occurrences of one series the overdue list shows at most: the most recent ones. */
        const val MAX_MISSED_OCCURRENCES_PER_SERIES = 30

        private const val DEFAULT_TASK_DURATION_MILLIS = 30L * 60L * 1000L
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

        /** Growing look-ahead windows, so daily and weekly series stay cheap and yearly ones are found. */
        private val CURRENT_OCCURRENCE_WINDOWS_MILLIS = longArrayOf(62L * DAY_MILLIS, 800L * DAY_MILLIS, 7_400L * DAY_MILLIS)

        /** Growing look-back windows for [missedOpenOccurrences]; the last resort is the series start. */
        private val MISSED_OCCURRENCE_WINDOWS_MILLIS = CURRENT_OCCURRENCE_WINDOWS_MILLIS
    }
}

/**
 * The occurrence of this recurring [TaskEntity] master whose RECURRENCE-ID is [recurrenceIdMillis]:
 * the generated occurrence with its stored override, if any, applied. Masters without a date are
 * returned unchanged.
 */
fun TaskEntity.occurrenceAt(recurrenceIdMillis: Long): TaskEntity {
    val anchor = startAtMillis ?: dueAtMillis ?: return this
    val shift = recurrenceIdMillis - anchor
    val generated = copy(
        startAtMillis = startAtMillis?.plus(shift),
        dueAtMillis = dueAtMillis?.plus(shift),
    )
    return RecurrenceOverrideCodec.decodeTasks(recurrenceOverridesJson)
        .firstOrNull { it.recurrenceIdMillis == recurrenceIdMillis }
        ?.applyTo(generated)
        ?: generated
}
