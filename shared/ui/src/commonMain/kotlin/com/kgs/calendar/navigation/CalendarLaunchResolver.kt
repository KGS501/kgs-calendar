package com.kgs.calendar.navigation

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarOccurrenceEnvelope
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.model.CalendarViewMode
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import com.kgs.calendar.time.ZoneId

data class CalendarLaunchResolution(
    val date: LocalDate,
    val viewMode: CalendarViewMode,
    val event: EventEntity? = null,
    val task: TaskEntity? = null,
) {
    init {
        require((event == null) != (task == null))
    }
}

class CalendarLaunchResolver(
    private val eventByResource: suspend (String) -> EventEntity?,
    private val taskByResource: suspend (String) -> TaskEntity?,
    private val expandEvents: (EventEntity, Long, Long) -> List<CalendarOccurrenceEnvelope<EventEntity>>,
    private val expandTasks: (TaskEntity, Long, Long) -> List<CalendarOccurrenceEnvelope<TaskEntity>>,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun resolve(target: CalendarLaunchTarget): CalendarLaunchResolution? {
        if (target.action != CalendarLaunchAction.OpenOccurrence) return null
        val occurrenceId = target.occurrence ?: return null
        val rangeStart = occurrenceId.recurrenceIdMillis - RANGE_PADDING_MILLIS
        val rangeEnd = occurrenceId.recurrenceIdMillis + RANGE_PADDING_MILLIS
        val viewMode = target.viewMode ?: CalendarViewMode.Day
        return when (occurrenceId) {
            is CalendarOccurrenceId.Event -> {
                val master = eventByResource(occurrenceId.resourceHref) ?: return null
                // An exception can have moved the occurrence far from its original date.
                val movedTo = RecurrenceOverrideCodec.decodeEvents(master.recurrenceOverridesJson)
                    .firstOrNull { it.recurrenceIdMillis == occurrenceId.recurrenceIdMillis }
                    ?.startsAtMillis
                    ?: occurrenceId.recurrenceIdMillis
                val found = expandEvents(master, minOf(rangeStart, movedTo - RANGE_PADDING_MILLIS), maxOf(rangeEnd, movedTo + RANGE_PADDING_MILLIS))
                val event = found.firstOrNull { it.occurrenceId == occurrenceId }?.item
                    // Widgets know an occurrence by where it is shown, which is its start.
                    ?: found.firstOrNull { it.item.startsAtMillis == occurrenceId.recurrenceIdMillis }?.item
                    ?: return null
                CalendarLaunchResolution(
                    date = event.startsAtMillis.localDate(),
                    viewMode = viewMode,
                    event = event,
                )
            }
            is CalendarOccurrenceId.Task -> {
                val master = taskByResource(occurrenceId.resourceHref) ?: return null
                val override = RecurrenceOverrideCodec.decodeTasks(master.recurrenceOverridesJson)
                    .firstOrNull { it.recurrenceIdMillis == occurrenceId.recurrenceIdMillis }
                val movedTo = override?.startAtMillis ?: override?.dueAtMillis ?: occurrenceId.recurrenceIdMillis
                val task = expandTasks(master, minOf(rangeStart, movedTo - RANGE_PADDING_MILLIS), maxOf(rangeEnd, movedTo + RANGE_PADDING_MILLIS))
                    .firstOrNull { it.occurrenceId == occurrenceId }
                    ?.item
                    ?: return null
                CalendarLaunchResolution(
                    date = (task.startAtMillis ?: task.dueAtMillis ?: occurrenceId.recurrenceIdMillis).localDate(),
                    viewMode = viewMode,
                    task = task,
                )
            }
        }
    }

    private fun Long.localDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

    private companion object {
        const val RANGE_PADDING_MILLIS = 2L * 24L * 60L * 60L * 1000L
    }
}
