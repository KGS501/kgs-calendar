package com.kgs.calendar.ui.layout

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.event.isTimedMultiDayMiddleOn
import com.kgs.calendar.domain.task.isTimedMultiDayMiddleOn
import com.kgs.calendar.domain.time.clockMinutesOn
import com.kgs.calendar.ui.calendar.DayEndHour
import com.kgs.calendar.ui.calendar.DayStartHour
import com.kgs.calendar.ui.calendar.DefaultTaskDurationMillis
import kotlinx.datetime.LocalDate
import com.kgs.calendar.time.ZoneId
import kotlin.math.max
import kotlin.math.min

data class TimedPlacement(
    val topDp: Float,
    val heightDp: Float,
    val startMinute: Int,
    val endMinute: Int,
)

data class TimedEventLayout(
    val event: EventEntity,
    val placement: TimedPlacement,
    val lane: Int,
    val laneCount: Int,
)

sealed interface TimedCalendarItem {
    val placement: TimedPlacement

    data class EventItem(val event: EventEntity, override val placement: TimedPlacement) : TimedCalendarItem
    data class TaskItem(val task: TaskEntity, override val placement: TimedPlacement) : TimedCalendarItem
}

data class TimedCalendarLayout(
    val item: TimedCalendarItem,
    val placement: TimedPlacement,
    val lane: Int,
    val laneCount: Int,
)

private data class PendingTimedEvent(
    val event: EventEntity,
    val placement: TimedPlacement,
)

fun layoutTimedItemsForDay(
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

fun List<EventEntity>.layoutTimedEventsForDay(day: LocalDate, hourHeightDp: Float): List<TimedEventLayout> {
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

/**
 * The day's timeline from [DayStartHour] to the end of [DayEndHour], in epoch millis. Runs for every item
 * on every visible day, also on each frame of a pinch zoom, so it converts each end only once.
 */
private fun LocalDate.visibleTimelineMillis(): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val start = LocalDateTime(this, LocalTime(DayStartHour, 0)).toEpochMillis(zone)
    val endLocal = if (DayEndHour == 23) LocalDateTime(plusDays(1), LocalTime(0, 0)) else LocalDateTime(this, LocalTime(DayEndHour + 1, 0))
    return start to endLocal.toEpochMillis(zone)
}

fun EventEntity.timedPlacementOn(day: LocalDate, hourHeightDp: Float): TimedPlacement? {
    if (allDay || isTimedMultiDayMiddleOn(day)) return null
    val (visibleStart, visibleEnd) = day.visibleTimelineMillis()
    val overlapStart = max(startsAtMillis, visibleStart)
    val effectiveEnd = if (endsAtMillis - startsAtMillis <= NearZeroTimedEventThresholdMillis) {
        startsAtMillis + MinimumTimedEventVisualDurationMillis
    } else {
        endsAtMillis
    }
    val overlapEnd = min(effectiveEnd, visibleEnd)
    if (overlapEnd <= overlapStart) return null

    return clockPlacementOn(day, overlapStart, overlapEnd, hourHeightDp)
}

/** Clock time, so that items line up with the hour labels on the days the clocks change. */
private fun clockPlacementOn(day: LocalDate, overlapStart: Long, overlapEnd: Long, hourHeightDp: Float): TimedPlacement {
    val (startMinute, endMinute) = clockMinutesOn(day, overlapStart, overlapEnd, ZoneId.systemDefault())
    val topMinutes = startMinute - DayStartHour * 60
    val durationMinutes = max(1, endMinute - startMinute)
    return TimedPlacement(
        topDp = topMinutes / 60f * hourHeightDp,
        heightDp = max(4f, durationMinutes / 60f * hourHeightDp),
        startMinute = topMinutes,
        endMinute = topMinutes + durationMinutes,
    )
}

private const val NearZeroTimedEventThresholdMillis = 1_000L
private const val MinimumTimedEventVisualDurationMillis = 15L * 60L * 1_000L

fun TaskEntity.timedPlacementOn(day: LocalDate, hourHeightDp: Float): TimedPlacement? {
    if (isTimedMultiDayMiddleOn(day)) return null
    val startTimed = startAtMillis?.takeIf { startHasTime }
    val dueTimed = dueAtMillis?.takeIf { dueHasTime }
    if (startTimed == null && dueTimed == null) return null

    val start = startTimed ?: (dueTimed!! - DefaultTaskDurationMillis)
    val end = when {
        startTimed != null && dueTimed != null && dueTimed > startTimed -> dueTimed
        startTimed != null -> startTimed + DefaultTaskDurationMillis
        else -> dueTimed!!
    }
    val (visibleStart, visibleEnd) = day.visibleTimelineMillis()
    val overlapStart = max(start, visibleStart)
    val overlapEnd = min(max(end, start + DefaultTaskDurationMillis), visibleEnd)
    if (overlapEnd <= overlapStart) return null

    return clockPlacementOn(day, overlapStart, overlapEnd, hourHeightDp)
}
