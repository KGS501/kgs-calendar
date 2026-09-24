package com.kgs.calendar.widget.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.kgs.calendar.widget.WIDGET_DAY_END_HOUR
import com.kgs.calendar.widget.WIDGET_DAY_HEADER_HEIGHT_DP
import com.kgs.calendar.widget.WIDGET_DAY_ROOT_PADDING_DP
import com.kgs.calendar.widget.WIDGET_DAY_START_HOUR
import com.kgs.calendar.widget.WIDGET_DAY_TIMELINE_TOP_MARGIN_DP
import com.kgs.calendar.widget.model.WidgetDayItem
import com.kgs.calendar.widget.model.WidgetDayTimedLayout
import com.kgs.calendar.widget.model.WidgetDayTimeline
import com.kgs.calendar.widget.model.WidgetSize
import com.kgs.calendar.widget.theme.WidgetPalette
import com.kgs.calendar.widget.theme.blendWith
import com.kgs.calendar.widget.theme.isDarkColor
import com.kgs.calendar.widget.theme.withAlpha
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt

internal fun dayTimelineBitmap(
    context: Context,
    zoneId: ZoneId,
    size: WidgetSize,
    palette: WidgetPalette,
    timeline: WidgetDayTimeline,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val widthDp = size.widthDp.coerceAtLeast(120)
    val heightDp = (
        size.heightDp -
            WIDGET_DAY_ROOT_PADDING_DP * 2 -
            WIDGET_DAY_HEADER_HEIGHT_DP -
            WIDGET_DAY_TIMELINE_TOP_MARGIN_DP
        ).coerceAtLeast(96)
    val bitmap = Bitmap.createBitmap(
        (widthDp * density).roundToInt().coerceAtLeast(1),
        (heightDp * density).roundToInt().coerceAtLeast(1),
        Bitmap.Config.RGB_565,
    )
    val canvas = Canvas(bitmap)
    canvas.drawColor(palette.rootBackgroundColor)
    canvas.scale(density, density)

    val dark = palette.rootBackgroundColor.isDarkColor()
    val slotColor = if (dark) 0xFF182534.toInt() else 0xFFFAFCFF.toInt()
    val slotLine = if (dark) 0xFF35475B.toInt() else 0xFFD2E0EF.toInt()
    val timeColumnWidth = if (widthDp < 230) 28f else 30f
    val gridLeft = timeColumnWidth
    val gridWidth = (widthDp - gridLeft).coerceAtLeast(1f)
    val allDayRows = when {
        timeline.allDayItems.isEmpty() -> 0
        heightDp < 170 -> 1
        else -> 2
    }
    val allDayRowHeight = if (heightDp < 170) 17f else 20f
    val allDayHeight = if (allDayRows > 0) allDayRows * allDayRowHeight + 3f else 0f
    val timelineTop = allDayHeight + if (allDayHeight > 0f) 5f else 0f
    val timelineHeight = (heightDp - timelineTop).coerceAtLeast(60f)
    val hourCount = WIDGET_DAY_END_HOUR - WIDGET_DAY_START_HOUR + 1
    val rowHeight = timelineHeight / hourCount.toFloat()
    val slotRadius = min(10f, rowHeight / 2.7f).coerceAtLeast(3f)

    val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.muted
        textSize = when {
            rowHeight < 10f -> 5.8f
            rowHeight < 14f -> 6.6f
            else -> 7.5f
        }
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = slotColor
        style = Paint.Style.FILL
    }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = slotLine
        strokeWidth = 0.65f
    }

    drawDayAllDayItems(
        canvas = canvas,
        palette = palette,
        items = timeline.allDayItems,
        rows = allDayRows,
        rowHeight = allDayRowHeight,
        timeColumnWidth = timeColumnWidth,
        gridLeft = gridLeft,
        gridWidth = gridWidth,
    )

    val labelEvery = when {
        rowHeight < 9f -> 4
        rowHeight < 13f -> 2
        else -> 1
    }
    repeat(hourCount) { index ->
        val top = timelineTop + index * rowHeight + 1f
        val bottom = timelineTop + (index + 1) * rowHeight - 1f
        canvas.drawRoundRect(
            RectF(gridLeft, top, widthDp.toFloat(), bottom.coerceAtLeast(top + 1f)),
            slotRadius,
            slotRadius,
            slotPaint,
        )
        if (index % labelEvery == 0) {
            val hour = WIDGET_DAY_START_HOUR + index
            canvas.drawText("%02d:00".format(hour), timeColumnWidth / 2f, top + timePaint.textSize + 1f, timePaint)
        }
        if (index > 0) {
            canvas.drawLine(gridLeft, top - 1f, widthDp.toFloat(), top - 1f, linePaint)
        }
    }

    timeline.timedItems.forEach { layout ->
        drawDayTimedItem(
            canvas = canvas,
            palette = palette,
            layout = layout,
            gridLeft = gridLeft,
            gridWidth = gridWidth,
            timelineTop = timelineTop,
            rowHeight = rowHeight,
        )
    }

    if (timeline.day == LocalDate.now(zoneId)) {
        val now = LocalTime.now(zoneId)
        if (now.hour in WIDGET_DAY_START_HOUR..WIDGET_DAY_END_HOUR) {
            val minute = now.hour * 60 + now.minute - WIDGET_DAY_START_HOUR * 60
            val y = timelineTop + minute / 60f * rowHeight
            val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.accent
                strokeWidth = 1.4f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(gridLeft, y, widthDp.toFloat(), y, nowPaint)
            canvas.drawCircle(gridLeft, y, 2.4f, nowPaint)
        }
    }
    return bitmap
}

private fun drawDayAllDayItems(
    canvas: Canvas,
    palette: WidgetPalette,
    items: List<WidgetDayItem>,
    rows: Int,
    rowHeight: Float,
    timeColumnWidth: Float,
    gridLeft: Float,
    gridWidth: Float,
) {
    if (rows <= 0 || items.isEmpty()) return
    val shown = items.take(rows)
    val hidden = (items.size - shown.size).coerceAtLeast(0)
    shown.forEachIndexed { index, item ->
        val top = index * rowHeight
        val rightInset = if (hidden > 0 && index == shown.lastIndex) 31f else 0f
        drawDayChip(
            canvas = canvas,
            palette = palette,
            item = item,
            rect = RectF(gridLeft, top, gridLeft + gridWidth - rightInset, top + rowHeight - 2f),
            titleSize = 9.5f,
            showMeta = false,
        )
        if (hidden > 0 && index == shown.lastIndex) {
            val overflowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.muted
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val centerY = top + (rowHeight - 2f) / 2f - (overflowPaint.fontMetrics.ascent + overflowPaint.fontMetrics.descent) / 2f
            canvas.drawText("+$hidden", gridLeft + gridWidth - 15f, centerY, overflowPaint)
        }
    }
}

private fun drawDayTimedItem(
    canvas: Canvas,
    palette: WidgetPalette,
    layout: WidgetDayTimedLayout,
    gridLeft: Float,
    gridWidth: Float,
    timelineTop: Float,
    rowHeight: Float,
) {
    val laneGap = 2f
    val laneWidth = ((gridWidth - laneGap * (layout.laneCount - 1)) / layout.laneCount).coerceAtLeast(14f)
    val left = gridLeft + layout.lane * (laneWidth + laneGap)
    val top = timelineTop + layout.startMinute / 60f * rowHeight + 1f
    val rawHeight = (layout.endMinute - layout.startMinute).coerceAtLeast(1) / 60f * rowHeight
    val height = rawHeight.coerceAtLeast(if (rowHeight < 13f) 10f else 16f)
    val rect = RectF(left, top, (left + laneWidth).coerceAtMost(gridLeft + gridWidth), top + height - 1f)
    val titleSize = when {
        rowHeight < 10f -> 7.5f
        rowHeight < 15f -> 8.8f
        else -> 10.4f
    }
    drawDayChip(
        canvas = canvas,
        palette = palette,
        item = layout.item,
        rect = rect,
        titleSize = titleSize,
        showMeta = rect.height() >= 23f,
    )
}

private fun drawDayChip(
    canvas: Canvas,
    palette: WidgetPalette,
    item: WidgetDayItem,
    rect: RectF,
    titleSize: Float,
    showMeta: Boolean,
) {
    val color = if (item.completed) item.color.blendWith(palette.rootBackgroundColor, 0.45f) else item.color
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    val radius = min(9f, rect.height() / 2f).coerceAtLeast(4f)
    canvas.drawRoundRect(rect, radius, radius, fillPaint)
    val contentColor = if (color.isDarkColor()) 0xFFFFFFFF.toInt() else 0xFF1C1A18.toInt()
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = contentColor.withAlpha(if (item.completed) 0.68f else 1f)
        textSize = titleSize
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = contentColor.withAlpha(if (item.completed) 0.55f else 0.84f)
        textSize = (titleSize - 1.5f).coerceAtLeast(6.5f)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val statusSpace = if (item.isTask) titleSize + 8f else 0f
    if (item.isTask) {
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = titlePaint.color
            style = Paint.Style.STROKE
            strokeWidth = 1.25f
        }
        canvas.drawCircle(rect.left + 8f, rect.top + rect.height() / 2f, (titleSize * 0.42f).coerceAtLeast(3f), statusPaint)
    }
    val textStart = rect.left + 7f + statusSpace
    val textEnd = rect.right - 7f
    val textWidth = (textEnd - textStart).coerceAtLeast(0f)
    val titleBaseline = if (showMeta) {
        rect.top + 8f + titleSize
    } else {
        rect.centerY() - (titlePaint.fontMetrics.ascent + titlePaint.fontMetrics.descent) / 2f
    }
    canvas.drawText(ellipsizeForPaint(item.title, titlePaint, textWidth), textStart, titleBaseline, titlePaint)
    if (showMeta && item.meta.isNotBlank()) {
        val metaBaseline = titleBaseline + titleSize + 1f
        if (metaBaseline < rect.bottom - 3f) {
            canvas.drawText(ellipsizeForPaint(item.meta, metaPaint, textWidth), textStart, metaBaseline, metaPaint)
        }
    }
}
