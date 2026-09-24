package com.kgs.calendar.widget.model

import com.kgs.calendar.domain.task.taskPriorityIntensity
import com.kgs.calendar.widget.theme.WidgetPalette
import java.time.LocalDate
import kotlin.math.max

internal data class WidgetDayTimeline(
    val day: LocalDate,
    val allDayItems: List<WidgetDayItem>,
    val timedItems: List<WidgetDayTimedLayout>,
    val signature: String,
)

internal data class WidgetDayGridCollectionSnapshot(
    val rows: List<WidgetDayGridRow>,
    val settings: WidgetRenderSettings,
    val palette: WidgetPalette,
    val widthDp: Float,
)

internal data class WidgetDayAllDaySectionFrameData(
    val appWidgetId: Int,
    val settings: WidgetRenderSettings,
    val palette: WidgetPalette,
    val timeline: WidgetDayTimeline,
    val contentWidthDp: Float,
    val allDayExpanded: Boolean,
)

internal data class WidgetDayItem(
    val title: String,
    val meta: String,
    val color: Int,
    val completed: Boolean,
    val isTask: Boolean,
    val stableKey: String,
    val location: String? = null,
    val eventStatus: String? = null,
    val eventResourceHref: String? = null,
    val taskResourceHref: String? = null,
    val statusGlyph: String = "",
    val priority: Int? = null,
)

internal data class WidgetDayTimedItem(
    val item: WidgetDayItem,
    val startMinute: Int,
    val endMinute: Int,
)

internal data class WidgetDayTimedLayout(
    val item: WidgetDayItem,
    val startMinute: Int,
    val endMinute: Int,
    val lane: Int,
    val laneCount: Int,
)

internal data class PreparedDayWidgetRender(
    val appWidgetId: Int,
    val settings: WidgetRenderSettings,
    val palette: WidgetPalette,
    val today: LocalDate,
    val day: LocalDate,
    val size: WidgetSize,
    val timeline: WidgetDayTimeline,
    val gridRows: List<WidgetDayGridRow>,
    val allDayExpanded: Boolean,
)

internal fun WidgetDayItem.hasDayPriorityMotion(): Boolean =
    isTask && !completed && taskPriorityIntensity(priority) > 0f

internal fun widgetStableId(value: String): Long =
    value.fold(1125899906842597L) { acc, char -> acc * 31 + char.code }

internal fun widgetRequestCode(value: String): Int =
    (widgetStableId(value) and 0x3FFFFFFF).toInt()

internal fun layoutWidgetDayTimedItems(items: List<WidgetDayTimedItem>): List<WidgetDayTimedLayout> {
    val pending = items.sortedWith(compareBy<WidgetDayTimedItem> { it.startMinute }.thenBy { it.endMinute }.thenBy { it.item.title })
    val result = mutableListOf<WidgetDayTimedLayout>()
    val group = mutableListOf<WidgetDayTimedItem>()
    var groupEnd = Int.MIN_VALUE

    fun flushGroup() {
        if (group.isEmpty()) return
        val laneEnds = mutableListOf<Int>()
        val assigned = group.map { item ->
            val lane = laneEnds.indexOfFirst { it <= item.startMinute }.let { index ->
                if (index >= 0) index else laneEnds.size.also { laneEnds.add(Int.MIN_VALUE) }
            }
            laneEnds[lane] = item.endMinute
            item to lane
        }
        val laneCount = max(1, laneEnds.size)
        assigned.forEach { (item, lane) ->
            result += WidgetDayTimedLayout(
                item = item.item,
                startMinute = item.startMinute,
                endMinute = item.endMinute,
                lane = lane,
                laneCount = laneCount,
            )
        }
        group.clear()
        groupEnd = Int.MIN_VALUE
    }

    pending.forEach { item ->
        if (group.isNotEmpty() && item.startMinute >= groupEnd) {
            flushGroup()
        }
        group += item
        groupEnd = max(groupEnd, item.endMinute)
    }
    flushGroup()
    return result
}
