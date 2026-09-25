package com.kgs.calendar.widget.model

import com.kgs.calendar.widget.WIDGET_DAY_END_HOUR
import com.kgs.calendar.widget.WIDGET_DAY_MAX_PRIORITY_MOTION_ROWS
import com.kgs.calendar.widget.WIDGET_DAY_START_HOUR
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

internal fun widgetDayGridRows(
    timeline: WidgetDayTimeline,
    settings: WidgetRenderSettings,
    nextTimeline: WidgetDayTimeline?,
    zoneId: ZoneId,
): List<WidgetDayGridRow> {
    val now = LocalTime.now(zoneId)
    val today = LocalDate.now(zoneId)
    val hourRowHeightDp = settings.dayWidgetHourRowHeightDp()
    val availableAnimatedBlocks = WIDGET_DAY_MAX_PRIORITY_MOTION_ROWS - if (
        settings.priorityAnimationsEnabled &&
        timeline.allDayItems.any { it.hasDayPriorityMotion() }
    ) {
        1
    } else {
        0
    }
    val useCurrentHourStart = settings.dayWidgetStartAtCurrentHour && timeline.day == today
    val firstVisibleHour = if (useCurrentHourStart) {
        now.hour.coerceIn(WIDGET_DAY_START_HOUR, WIDGET_DAY_END_HOUR)
    } else if (settings.dayWidgetStartAtCurrentHour) {
        WIDGET_DAY_START_HOUR
    } else {
        settings.dayWidgetStartHour.coerceIn(WIDGET_DAY_START_HOUR, WIDGET_DAY_END_HOUR)
    }
    val primaryRows = dayGridRowsForTimeline(
        timeline = timeline,
        settings = settings,
        now = now,
        today = today,
        hourRowHeightDp = hourRowHeightDp,
        firstVisibleHour = firstVisibleHour,
        lastVisibleHour = WIDGET_DAY_END_HOUR,
        availableAnimatedBlocks = availableAnimatedBlocks,
        daySwitchLabel = null,
    )
    if (!useCurrentHourStart || firstVisibleHour == WIDGET_DAY_START_HOUR || nextTimeline == null) {
        return primaryRows
    }
    val nextRows = dayGridRowsForTimeline(
        timeline = nextTimeline,
        settings = settings,
        now = now,
        today = today,
        hourRowHeightDp = hourRowHeightDp,
        firstVisibleHour = WIDGET_DAY_START_HOUR,
        lastVisibleHour = firstVisibleHour - 1,
        availableAnimatedBlocks = availableAnimatedBlocks,
        daySwitchLabel = null,
    )
    val boundaryRow = WidgetDayGridRow(
        day = nextTimeline.day,
        hour = null,
        hourRowHeightDp = hourRowHeightDp,
        daySwitchLabel = nextTimeline.day.format(DateTimeFormatter.ofPattern("EEE d.", settings.locale)),
        priorityAnimationsEnabled = false,
    )
    return primaryRows + boundaryRow + nextRows
}

private fun dayGridRowsForTimeline(
    timeline: WidgetDayTimeline,
    settings: WidgetRenderSettings,
    now: LocalTime,
    today: LocalDate,
    hourRowHeightDp: Float,
    firstVisibleHour: Int,
    lastVisibleHour: Int,
    availableAnimatedBlocks: Int,
    daySwitchLabel: String?,
): List<WidgetDayGridRow> {
    if (firstVisibleHour > lastVisibleHour) return emptyList()
    val allTimedItems = timeline.timedItems
    val protectedRanges = if (settings.priorityAnimationsEnabled && availableAnimatedBlocks > 0) {
        allTimedItems.asSequence()
            .filter { it.item.hasDayPriorityMotion() }
            .distinctBy { it.item.stableKey }
            .sortedWith(
                compareBy<WidgetDayTimedLayout> { it.item.priority ?: 9 }
                    .thenBy { it.startMinute }
                    .thenBy { it.endMinute }
                    .thenBy { it.item.title.lowercase(settings.locale) },
            )
            .take(availableAnimatedBlocks)
            .map { layout ->
                val firstHour = (layout.startMinute / 60 - 1).coerceIn(0, 23)
                val occupiedLastHour = ((layout.endMinute - 1).coerceAtLeast(layout.startMinute) / 60).coerceIn(0, 23)
                firstHour..(occupiedLastHour + 1).coerceAtMost(23)
            }
            .toList()
            .sortedBy { it.first }
            .fold(mutableListOf<IntRange>()) { merged, range ->
                val previous = merged.lastOrNull()
                if (previous != null && range.first <= previous.last + 1) {
                    merged[merged.lastIndex] = previous.first..max(previous.last, range.last)
                } else {
                    merged += range
                }
                merged
            }
    } else {
        emptyList()
    }
    val spanningRanges = allTimedItems.asSequence()
        .mapNotNull { layout ->
            val firstHour = (layout.startMinute / 60).coerceIn(WIDGET_DAY_START_HOUR, WIDGET_DAY_END_HOUR)
            val occupiedLastHour = ((layout.endMinute - 1).coerceAtLeast(layout.startMinute) / 60)
                .coerceIn(WIDGET_DAY_START_HOUR, WIDGET_DAY_END_HOUR)
            val clippedFirst = max(firstHour, firstVisibleHour)
            val clippedLast = min(occupiedLastHour, lastVisibleHour)
            if (clippedLast > clippedFirst) clippedFirst..clippedLast else null
        }
        .toList()
    val groupedRanges = (protectedRanges + spanningRanges)
        .sortedBy { it.first }
        .fold(mutableListOf<IntRange>()) { merged, range ->
            val clipped = max(range.first, firstVisibleHour)..min(range.last, lastVisibleHour)
            if (clipped.first > clipped.last) return@fold merged
            val previous = merged.lastOrNull()
            if (previous != null && clipped.first <= previous.last + 1) {
                merged[merged.lastIndex] = previous.first..max(previous.last, clipped.last)
            } else {
                merged += clipped
            }
            merged
        }

    fun row(
        startHour: Int,
        hourCount: Int,
        animatePriority: Boolean,
    ): WidgetDayGridRow {
        val startMinute = startHour * 60
        val endMinute = (startHour + hourCount) * 60
        return WidgetDayGridRow(
            day = timeline.day,
            hour = startHour,
            hourCount = hourCount,
            hourRowHeightDp = hourRowHeightDp,
            timedItems = allTimedItems.filter { it.startMinute < endMinute && it.endMinute > startMinute },
            nowMinute = if (
                timeline.day == today &&
                now.hour in startHour until (startHour + hourCount)
            ) {
                now.hour * 60 + now.minute
            } else {
                null
            },
            daySwitchLabel = daySwitchLabel.takeIf { startHour == firstVisibleHour },
            priorityAnimationsEnabled = animatePriority,
        )
    }

    return buildList {
        var hour = firstVisibleHour
        var groupedIndex = 0
        while (hour <= lastVisibleHour) {
            val grouped = groupedRanges.getOrNull(groupedIndex)
            if (grouped != null && hour in grouped) {
                val groupedStart = max(hour, grouped.first)
                val groupedLast = min(grouped.last, lastVisibleHour)
                val animatePriority = protectedRanges.any { range ->
                    range.first <= groupedLast && range.last >= groupedStart
                }
                add(
                    row(
                        startHour = groupedStart,
                        hourCount = groupedLast - groupedStart + 1,
                        animatePriority = animatePriority,
                    ),
                )
                repeat(groupedLast - groupedStart) { offset ->
                    add(
                        WidgetDayGridRow(
                            day = timeline.day,
                            hour = groupedStart + offset + 1,
                            hourCount = 0,
                            hourRowHeightDp = hourRowHeightDp,
                            priorityAnimationsEnabled = false,
                        ),
                    )
                }
                hour = groupedLast + 1
                groupedIndex++
            } else {
                add(row(startHour = hour, hourCount = 1, animatePriority = false))
                hour++
            }
        }
    }
}
