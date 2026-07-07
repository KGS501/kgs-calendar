package com.kgs.calendar.ui.layout

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.ui.calendar.DayEndHour
import com.kgs.calendar.ui.calendar.DayStartHour
import com.kgs.calendar.ui.calendar.DefaultTaskDurationMillis
import com.kgs.calendar.ui.model.isTimedMultiDayMiddleOn
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class TimedPlacement(
    val topDp: Float,
    val heightDp: Float,
    val startMinute: Int,
    val endMinute: Int,
)

internal data class TimedEventLayout(
    val event: EventEntity,
    val placement: TimedPlacement,
    val lane: Int,
    val laneCount: Int,
)

internal sealed interface TimedCalendarItem {
    val placement: TimedPlacement

    data class EventItem(val event: EventEntity, override val placement: TimedPlacement) : TimedCalendarItem
    data class TaskItem(val task: TaskEntity, override val placement: TimedPlacement) : TimedCalendarItem
}

internal data class TimedCalendarLayout(
    val item: TimedCalendarItem,
    val placement: TimedPlacement,
    val lane: Int,
    val laneCount: Int,
)

private data class PendingTimedEvent(
    val event: EventEntity,
    val placement: TimedPlacement,
)

internal fun layoutTimedItemsForDay(
    day: LocalDate,
    hourHeightDp: Float,
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
): List<TimedCalendarLayout> {
    val pending = buildList {
        events.forEach { event ->
            event.timedPlacementOn(day, hourHeightDp)?.let { add(TimedCalendarItem.EventItem(event, it)) }
        }
        tasks.forEach { task ->
            task.timedPlacementOn(day, hourHeightDp)?.let { add(TimedCalendarItem.TaskItem(task, it)) }
        }
    }.sortedWith(compareBy<TimedCalendarItem> { it.placement.startMinute }.thenBy { it.placement.endMinute })

    val result = mutableListOf<TimedCalendarLayout>()
    val group = mutableListOf<TimedCalendarItem>()
    var groupEnd = Int.MIN_VALUE

    fun flushGroup() {
        if (group.isEmpty()) return
        val laneEnds = mutableListOf<Int>()
        val assigned = group.map { item ->
            val lane = laneEnds.indexOfFirst { it <= item.placement.startMinute }.let { index ->
                if (index >= 0) index else laneEnds.size.also { laneEnds.add(Int.MIN_VALUE) }
            }
            laneEnds[lane] = item.placement.endMinute
            item to lane
        }
        val laneCount = max(1, laneEnds.size)
        assigned.forEach { (item, lane) ->
            result += TimedCalendarLayout(item, item.placement, lane, laneCount)
        }
        group.clear()
        groupEnd = Int.MIN_VALUE
    }

    pending.forEach { item ->
        if (group.isNotEmpty() && item.placement.startMinute >= groupEnd) {
            flushGroup()
        }
        group += item
        groupEnd = max(groupEnd, item.placement.endMinute)
    }
    flushGroup()

    return result
}

internal fun List<EventEntity>.layoutTimedEventsForDay(day: LocalDate, hourHeightDp: Float): List<TimedEventLayout> {
    val pending = mapNotNull { event ->
        event.timedPlacementOn(day, hourHeightDp)?.let { PendingTimedEvent(event, it) }
    }.sortedWith(compareBy<PendingTimedEvent> { it.placement.startMinute }.thenBy { it.placement.endMinute })

    val result = mutableListOf<TimedEventLayout>()
    val group = mutableListOf<PendingTimedEvent>()
    var groupEnd = Int.MIN_VALUE

    fun flushGroup() {
        if (group.isEmpty()) return
        val laneEnds = mutableListOf<Int>()
        val assigned = group.map { item ->
            val lane = laneEnds.indexOfFirst { it <= item.placement.startMinute }.let { index ->
                if (index >= 0) index else laneEnds.size.also { laneEnds.add(Int.MIN_VALUE) }
            }
            laneEnds[lane] = item.placement.endMinute
            item to lane
        }
        val laneCount = max(1, laneEnds.size)
        assigned.forEach { (item, lane) ->
            result += TimedEventLayout(item.event, item.placement, lane, laneCount)
        }
        group.clear()
        groupEnd = Int.MIN_VALUE
    }

    pending.forEach { item ->
        if (group.isNotEmpty() && item.placement.startMinute >= groupEnd) {
            flushGroup()
        }
        group += item
        groupEnd = max(groupEnd, item.placement.endMinute)
    }
    flushGroup()

    return result
}

internal fun EventEntity.timedPlacementOn(day: LocalDate, hourHeightDp: Float): TimedPlacement? {
    if (allDay || isTimedMultiDayMiddleOn(day)) return null
    val visibleStart = day.atTime(DayStartHour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val visibleEnd = day.atTime(DayEndHour, 0).plusHours(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val overlapStart = max(startsAtMillis, visibleStart)
    val overlapEnd = min(endsAtMillis, visibleEnd)
    if (overlapEnd <= overlapStart) return null

    val topMinutes = ((overlapStart - visibleStart) / 60_000.0).roundToInt()
    val durationMinutes = max(1, ((overlapEnd - overlapStart) / 60_000.0).roundToInt())
    return TimedPlacement(
        topDp = topMinutes / 60f * hourHeightDp,
        heightDp = max(4f, durationMinutes / 60f * hourHeightDp),
        startMinute = topMinutes,
        endMinute = topMinutes + durationMinutes,
    )
}

internal fun TaskEntity.timedPlacementOn(day: LocalDate, hourHeightDp: Float): TimedPlacement? {
    val startTimed = startAtMillis?.takeIf { startHasTime }
    val dueTimed = dueAtMillis?.takeIf { dueHasTime }
    if (startTimed == null && dueTimed == null) return null

    val start = startTimed ?: (dueTimed!! - DefaultTaskDurationMillis)
    val end = when {
        startTimed != null && dueTimed != null && dueTimed > startTimed -> dueTimed
        startTimed != null -> startTimed + DefaultTaskDurationMillis
        else -> dueTimed!!
    }
    val visibleStart = day.atTime(DayStartHour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val visibleEnd = day.atTime(DayEndHour, 0).plusHours(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val overlapStart = max(start, visibleStart)
    val overlapEnd = min(max(end, start + DefaultTaskDurationMillis), visibleEnd)
    if (overlapEnd <= overlapStart) return null

    val topMinutes = ((overlapStart - visibleStart) / 60_000.0).roundToInt()
    val durationMinutes = max(1, ((overlapEnd - overlapStart) / 60_000.0).roundToInt())
    return TimedPlacement(
        topDp = topMinutes / 60f * hourHeightDp,
        heightDp = max(4f, durationMinutes / 60f * hourHeightDp),
        startMinute = topMinutes,
        endMinute = topMinutes + durationMinutes,
    )
}
