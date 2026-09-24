package com.kgs.calendar.widget.model

import android.graphics.RectF
import com.kgs.calendar.widget.WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP
import com.kgs.calendar.widget.WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP
import com.kgs.calendar.widget.WIDGET_DAY_ALL_DAY_TOP_PADDING_DP
import com.kgs.calendar.widget.WIDGET_DAY_BOUNDARY_ROW_HEIGHT_DP
import com.kgs.calendar.widget.WIDGET_DAY_CARD_MIN_HEIGHT_DP
import com.kgs.calendar.widget.WIDGET_DAY_CARD_MIN_TOUCH_HEIGHT_DP
import com.kgs.calendar.widget.WIDGET_DAY_GRID_GAP_DP
import com.kgs.calendar.widget.WIDGET_DAY_HOUR_ROW_HEIGHT_DP
import com.kgs.calendar.widget.WIDGET_DAY_PRIORITY_BITMAP_SCALE
import com.kgs.calendar.widget.WIDGET_DAY_RENDER_SIGNATURE_VERSION
import com.kgs.calendar.widget.WIDGET_DAY_ROW_SIDE_INSET_DP
import com.kgs.calendar.widget.WIDGET_DAY_TIME_COLUMN_WIDTH_DP
import com.kgs.calendar.widget.WIDGET_TASK_MIN_CARD_WIDTH_DP
import com.kgs.calendar.widget.theme.WidgetPalette
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

private fun allDayRowsHeight(rows: Int): Float {
    val safeRows = rows.coerceAtLeast(1)
    return safeRows * WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP.toFloat() +
        (safeRows - 1).coerceAtLeast(0) * 5f +
        10f
}

internal data class WidgetDayGridRow(
    val day: LocalDate,
    val hour: Int?,
    val hourCount: Int = 1,
    val hourRowHeightDp: Float = WIDGET_DAY_HOUR_ROW_HEIGHT_DP.toFloat(),
    val allDayItems: List<WidgetDayItem> = emptyList(),
    val timedItems: List<WidgetDayTimedLayout> = emptyList(),
    val nowMinute: Int? = null,
    val daySwitchLabel: String? = null,
    val priorityAnimationsEnabled: Boolean = true,
    val maxVisibleAllDayItems: Int = 3,
    val allDayExpanded: Boolean = false,
    val allDayExpansionProgress: Float = if (allDayExpanded) 1f else 0f,
) {
    val stableId: Long = when (hour) {
        null -> if (daySwitchLabel != null) {
            widgetStableId("day-grid-boundary:${day.toEpochDay()}:$daySwitchLabel")
        } else {
            widgetStableId("day-grid-all-day:${day.toEpochDay()}")
        }
        else -> widgetStableId("day-grid-hour:${day.toEpochDay()}:$hour")
    }

    internal val normalizedMaxVisibleAllDayItems: Int =
        maxVisibleAllDayItems.coerceIn(0, 10)

    internal val allDayHasOverflow: Boolean =
        allDayItems.isNotEmpty() && allDayItems.size > normalizedMaxVisibleAllDayItems

    internal val collapsedVisibleAllDayLimit: Int = when {
        !allDayHasOverflow -> normalizedMaxVisibleAllDayItems
        normalizedMaxVisibleAllDayItems <= 0 -> 0
        else -> (normalizedMaxVisibleAllDayItems - 1).coerceAtLeast(0)
    }

    internal val collapsedAllDayRows: Int = when {
        allDayItems.isEmpty() -> 0
        normalizedMaxVisibleAllDayItems <= 0 -> 1
        allDayHasOverflow -> normalizedMaxVisibleAllDayItems
        else -> allDayItems.size.coerceAtMost(normalizedMaxVisibleAllDayItems)
    }

    internal val expandedAllDayRows: Int =
        allDayItems.size

    internal val allDayOverflowLane: Int =
        if (normalizedMaxVisibleAllDayItems <= 0) 0 else collapsedVisibleAllDayLimit

    internal val renderExpandedAllDayItems: Boolean =
        allDayExpanded || allDayExpansionProgress > 0.01f

    internal val rowHeightDp: Float = when {
        hour == null && daySwitchLabel != null -> WIDGET_DAY_BOUNDARY_ROW_HEIGHT_DP
        hour == null -> {
            val collapsedHeight = allDayRowsHeight(collapsedAllDayRows.coerceAtLeast(1))
            val expandedHeight = allDayRowsHeight(expandedAllDayRows.coerceAtLeast(1))
            lerpFloat(
                collapsedHeight,
                expandedHeight,
                allDayExpansionProgress.coerceIn(0f, 1f),
            )
        }
        hourCount <= 0 -> 0f
        else -> hourRowHeightDp * hourCount.toFloat()
    }

    internal val hasPriorityMotion: Boolean =
        priorityAnimationsEnabled &&
            (allDayItems.asSequence() + timedItems.asSequence().map { it.item })
                .any { it.hasDayPriorityMotion() }

    internal fun imageCacheKey(
        palette: WidgetPalette,
        widthDp: Float,
        suffix: String,
    ): String =
        "$this|$palette|$widthDp|$rowHeightDp|dayRender=$WIDGET_DAY_RENDER_SIGNATURE_VERSION|timeWidth=$WIDGET_DAY_TIME_COLUMN_WIDTH_DP|rowInset=$WIDGET_DAY_ROW_SIDE_INSET_DP|dayScale=$WIDGET_DAY_PRIORITY_BITMAP_SCALE|$suffix"

    internal fun renderedAllDayItemsWithLanes(): List<WidgetDayAllDayRenderItem> {
        if (allDayItems.isEmpty()) return emptyList()
        return if (renderExpandedAllDayItems) {
            val progress = allDayExpansionProgress.coerceIn(0f, 1f)
            allDayItems.mapIndexed { index, item ->
                val lane = if (allDayHasOverflow && index >= collapsedVisibleAllDayLimit) {
                    allDayOverflowLane + (index - allDayOverflowLane) * progress
                } else {
                    index.toFloat()
                }
                WidgetDayAllDayRenderItem(item, lane, index)
            }
        } else {
            allDayItems
                .take(collapsedVisibleAllDayLimit.coerceAtLeast(0))
                .mapIndexed { index, item -> WidgetDayAllDayRenderItem(item, index.toFloat(), index) }
        }
    }

    internal fun allDayItemAlpha(index: Int): Float =
        if (allDayHasOverflow && renderExpandedAllDayItems && index >= collapsedVisibleAllDayLimit) {
            allDayExpansionProgress.coerceIn(0f, 1f)
        } else {
            1f
        }

    internal fun priorityMotionBounds(widthDp: Float): RectF? {
        val rects = if (hour == null) {
            renderedAllDayItemsWithLanes().mapNotNull { renderItem ->
                val item = renderItem.item
                if (!item.hasDayPriorityMotion()) return@mapNotNull null
                val top = WIDGET_DAY_ALL_DAY_TOP_PADDING_DP + renderItem.lane * WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP
                RectF(
                    WIDGET_DAY_TIME_COLUMN_WIDTH_DP + WIDGET_DAY_GRID_GAP_DP,
                    top,
                    widthDp - 2f,
                    top + WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP,
                )
            }
        } else {
            val gridLeft = WIDGET_DAY_ROW_SIDE_INSET_DP + WIDGET_DAY_TIME_COLUMN_WIDTH_DP + WIDGET_DAY_GRID_GAP_DP
            val gridRight = widthDp - WIDGET_DAY_ROW_SIDE_INSET_DP - 2f
            val gridWidth = (gridRight - gridLeft).coerceAtLeast(1f)
            val hourStart = hour * 60
            val hourEnd = (hour + hourCount.coerceAtLeast(1)) * 60
            timedItems.mapNotNull { layout ->
                if (!layout.item.hasDayPriorityMotion()) return@mapNotNull null
                val topMinute = max(layout.startMinute, hourStart)
                val bottomMinute = min(layout.endMinute, hourEnd)
                if (bottomMinute <= topMinute) return@mapNotNull null
                val laneWidth = gridWidth / layout.laneCount.coerceAtLeast(1)
                val left = gridLeft + laneWidth * layout.lane + 1f
                val top = (topMinute - hourStart) / 60f * hourRowHeightDp + 1f
                val bottom = (bottomMinute - hourStart) / 60f * hourRowHeightDp - 1f
                RectF(
                    left,
                    top.coerceIn(0f, rowHeightDp),
                    left + laneWidth - 3f,
                    bottom.coerceIn(0f, rowHeightDp).coerceAtLeast(top + WIDGET_DAY_CARD_MIN_HEIGHT_DP),
                )
            }
        }
        val first = rects.firstOrNull() ?: return null
        return RectF(first).also { result ->
            rects.drop(1).forEach(result::union)
        }
    }

    internal fun allDayTouchTargets(widthDp: Float): List<WidgetDayTouchTarget> {
        val gridLeft = WIDGET_DAY_TIME_COLUMN_WIDTH_DP + WIDGET_DAY_GRID_GAP_DP
        val gridWidth = (widthDp - gridLeft - 2f).coerceAtLeast(WIDGET_TASK_MIN_CARD_WIDTH_DP)
        return renderedAllDayItemsWithLanes().map { renderItem ->
            WidgetDayTouchTarget(
                item = renderItem.item,
                leftDp = gridLeft,
                topDp = WIDGET_DAY_ALL_DAY_TOP_PADDING_DP + renderItem.lane * WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP,
                widthDp = gridWidth,
                heightDp = WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP.toFloat(),
            )
        }
    }

    internal fun timedTouchTargets(widthDp: Float, hour: Int): List<WidgetDayTouchTarget> {
        val gridLeft = WIDGET_DAY_ROW_SIDE_INSET_DP + WIDGET_DAY_TIME_COLUMN_WIDTH_DP + WIDGET_DAY_GRID_GAP_DP
        val gridRight = widthDp - WIDGET_DAY_ROW_SIDE_INSET_DP - 2f
        val gridWidth = (gridRight - gridLeft).coerceAtLeast(WIDGET_TASK_MIN_CARD_WIDTH_DP)
        val hourStart = hour * 60
        val hourEnd = (hour + hourCount.coerceAtLeast(1)) * 60
        return timedItems.mapNotNull { layout ->
            val topMinute = max(layout.startMinute, hourStart)
            val bottomMinute = min(layout.endMinute, hourEnd)
            if (bottomMinute <= topMinute) return@mapNotNull null
            val laneWidth = gridWidth / layout.laneCount.coerceAtLeast(1)
            val left = gridLeft + laneWidth * layout.lane + 1f
            val top = (topMinute - hourStart) / 60f * hourRowHeightDp + 1f
            val bottom = (bottomMinute - hourStart) / 60f * hourRowHeightDp - 1f
            WidgetDayTouchTarget(
                item = layout.item,
                leftDp = left,
                topDp = top.coerceIn(0f, rowHeightDp),
                widthDp = (laneWidth - 3f).coerceAtLeast(WIDGET_TASK_MIN_CARD_WIDTH_DP),
                heightDp = (bottom - top).coerceAtLeast(WIDGET_DAY_CARD_MIN_TOUCH_HEIGHT_DP),
            )
        }
    }

    fun containsHour(targetHour: Int): Boolean =
        hour != null && targetHour in hour until (hour + hourCount.coerceAtLeast(1))
}

internal data class WidgetDayAllDayRenderItem(
    val item: WidgetDayItem,
    val lane: Float,
    val sourceIndex: Int,
)

internal data class WidgetDayTouchTarget(
    val item: WidgetDayItem,
    val leftDp: Float,
    val topDp: Float,
    val widthDp: Float,
    val heightDp: Float,
)
