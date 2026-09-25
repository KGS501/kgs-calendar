package com.kgs.calendar.widget.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.kgs.calendar.domain.task.taskPriorityIntensity
import com.kgs.calendar.widget.WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP
import com.kgs.calendar.widget.WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP
import com.kgs.calendar.widget.WIDGET_DAY_ALL_DAY_TOP_PADDING_DP
import com.kgs.calendar.widget.WIDGET_DAY_CARD_MIN_HEIGHT_DP
import com.kgs.calendar.widget.WIDGET_DAY_CARD_RADIUS_DP
import com.kgs.calendar.widget.WIDGET_DAY_GRID_GAP_DP
import com.kgs.calendar.widget.WIDGET_DAY_PRIORITY_BITMAP_SCALE
import com.kgs.calendar.widget.WIDGET_DAY_PRIORITY_OVERDRAW_DP
import com.kgs.calendar.widget.WIDGET_DAY_ROW_SIDE_INSET_DP
import com.kgs.calendar.widget.WIDGET_DAY_TIME_COLUMN_WIDTH_DP
import com.kgs.calendar.widget.WIDGET_DAY_TITLE_FADE_WIDTH_DP
import com.kgs.calendar.widget.model.WidgetDayGridRow
import com.kgs.calendar.widget.model.WidgetDayItem
import com.kgs.calendar.widget.model.hasDayPriorityMotion
import com.kgs.calendar.widget.model.motionStandardEasing
import com.kgs.calendar.widget.theme.WidgetPalette
import com.kgs.calendar.widget.theme.blendWith
import com.kgs.calendar.widget.theme.isDarkColor
import com.kgs.calendar.widget.theme.withAlpha
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun WidgetDayGridRow.dayGridRowBitmap(
    context: Context,
    palette: WidgetPalette,
    widthDp: Float,
    heightDp: Float,
    omitPriorityCards: Boolean,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val bitmap = Bitmap.createBitmap(
        (widthDp * density).roundToInt().coerceAtLeast(1),
        (heightDp * density).roundToInt().coerceAtLeast(1),
        if (hour == null) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565,
    )
    val canvas = Canvas(bitmap)
    canvas.drawColor(palette.rootBackgroundColor)
    canvas.scale(density, density)
    if (hour == null && daySwitchLabel != null) {
        drawDayBoundaryRow(
            canvas = canvas,
            palette = palette,
            widthDp = widthDp,
            heightDp = heightDp,
            label = daySwitchLabel,
        )
    } else if (hour == null) {
        drawAllDayRow(
            canvas = canvas,
            palette = palette,
            widthDp = widthDp,
            omitPriorityCards = omitPriorityCards,
        )
    } else {
        drawHourRow(
            canvas = canvas,
            palette = palette,
            widthDp = widthDp,
            heightDp = heightDp,
            hour = hour,
            omitPriorityCards = omitPriorityCards,
        )
    }
    return bitmap
}

internal fun WidgetDayGridRow.dayPriorityMotionBitmap(
    context: Context,
    palette: WidgetPalette,
    widthDp: Float,
    heightDp: Float,
    frame: Int,
    frameCount: Int,
    frameIntervalMillis: Int,
    motionBounds: RectF,
): Bitmap {
    val bitmapScale = context.resources.displayMetrics.density * WIDGET_DAY_PRIORITY_BITMAP_SCALE
    val overdraw = WIDGET_DAY_PRIORITY_OVERDRAW_DP
    val bitmap = Bitmap.createBitmap(
        ((motionBounds.width() + overdraw * 2f) * bitmapScale).roundToInt().coerceAtLeast(1),
        ((motionBounds.height() + overdraw * 2f) * bitmapScale).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    canvas.scale(bitmapScale, bitmapScale)
    canvas.translate(overdraw - motionBounds.left, overdraw - motionBounds.top)
    if (hour == null) {
        drawAllDayRow(
            canvas = canvas,
            palette = palette,
            widthDp = widthDp,
            priorityOnly = true,
            frame = frame,
            frameCount = frameCount,
            frameIntervalMillis = frameIntervalMillis,
            priorityBitmapScale = bitmapScale,
        )
    } else {
        drawHourRow(
            canvas = canvas,
            palette = palette,
            widthDp = widthDp,
            heightDp = heightDp,
            hour = hour,
            priorityOnly = true,
            frame = frame,
            frameCount = frameCount,
            frameIntervalMillis = frameIntervalMillis,
            priorityBitmapScale = bitmapScale,
        )
    }
    return bitmap
}

private fun WidgetDayGridRow.drawAllDayRow(
    canvas: Canvas,
    palette: WidgetPalette,
    widthDp: Float,
    omitPriorityCards: Boolean = false,
    priorityOnly: Boolean = false,
    frame: Int = 0,
    frameCount: Int = 1,
    frameIntervalMillis: Int = 0,
    priorityBitmapScale: Float = 1f,
) {
    val gridLeft = WIDGET_DAY_TIME_COLUMN_WIDTH_DP + WIDGET_DAY_GRID_GAP_DP
    if (!priorityOnly) {
        drawAllDayExpansionArrow(canvas, palette)
    }
    renderedAllDayItemsWithLanes().forEach { renderItem ->
        val item = renderItem.item
        val animated = item.hasDayPriorityMotion()
        if ((omitPriorityCards && animated) || (priorityOnly && !animated)) return@forEach
        val top = WIDGET_DAY_ALL_DAY_TOP_PADDING_DP + renderItem.lane * WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP
        val rect = RectF(
            gridLeft,
            top,
            widthDp - 2f,
            top + WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP,
        )
        drawDayGridCard(
            canvas = canvas,
            palette = palette,
            item = item,
            rect = rect,
            heightDp = WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP.toFloat(),
            showMeta = false,
            priorityMotion = if (priorityOnly) {
                item.dayPriorityMotion(frame, frameCount, frameIntervalMillis, priorityBitmapScale)
            } else {
                null
            },
            alpha = allDayItemAlpha(renderItem.sourceIndex),
        )
    }
    if (!priorityOnly && allDayHasOverflow && !renderExpandedAllDayItems) {
        drawAllDayOverflowChip(
            canvas = canvas,
            palette = palette,
            rect = RectF(
                gridLeft,
                WIDGET_DAY_ALL_DAY_TOP_PADDING_DP + allDayOverflowLane * WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP,
                widthDp - 2f,
                WIDGET_DAY_ALL_DAY_TOP_PADDING_DP + allDayOverflowLane * WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP + WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP,
            ),
        )
    }
}

private fun WidgetDayGridRow.drawAllDayExpansionArrow(canvas: Canvas, palette: WidgetPalette) {
    if (!allDayHasOverflow && !allDayExpanded) return
    val centerX = WIDGET_DAY_TIME_COLUMN_WIDTH_DP / 2f
    val centerY = (rowHeightDp - 16f).coerceAtLeast(13f)
    val restore = canvas.save()
    canvas.translate(centerX, centerY)
    canvas.rotate(180f * allDayExpansionProgress.coerceIn(0f, 1f))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.text.withAlpha(0.82f)
        style = Paint.Style.STROKE
        strokeWidth = 1.75f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val path = Path().apply {
        moveTo(-4.8f, -1.6f)
        lineTo(0f, 2.15f)
        lineTo(4.8f, -1.6f)
    }
    canvas.drawPath(path, paint)
    canvas.restoreToCount(restore)
}

private fun WidgetDayGridRow.drawAllDayOverflowChip(
    canvas: Canvas,
    palette: WidgetPalette,
    rect: RectF,
) {
    if (rect.width() <= 1f || rect.height() <= 1f) return
    val dark = palette.rootBackgroundColor.isDarkColor()
    val frontColor = if (dark) 0xFF7E8A96.toInt() else 0xFFA4AFBA.toInt()
    val rearColor = if (dark) 0xFF687683.toInt() else 0xFFB4BEC8.toInt()
    val frontHeight = min(20f, rect.height())
    val radius = 8f
    val rearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = rearColor
        style = Paint.Style.FILL
    }
    val rearTop = rect.top + 3f
    canvas.drawRoundRect(
        RectF(rect.left + 4f, rearTop, rect.right - 4f, min(rect.bottom, rearTop + 18f)),
        radius,
        radius,
        rearPaint,
    )
    rearPaint.color = rearColor.withAlpha(0.92f)
    val secondRearTop = rect.top + 5f
    canvas.drawRoundRect(
        RectF(rect.left + 7f, secondRearTop, rect.right - 7f, min(rect.bottom, secondRearTop + 18f)),
        radius,
        radius,
        rearPaint,
    )
    val frontRect = RectF(rect.left, rect.top, rect.right, rect.top + frontHeight)
    val frontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = frontColor
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(frontRect, radius, radius, frontPaint)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (frontColor.isDarkColor()) 0xFFFFFFFF.toInt() else 0xFF1C1A18.toInt()
        textSize = 13f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val centerY = frontRect.centerY() - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
    canvas.drawText("\u2022\u2022\u2022", frontRect.centerX(), centerY, textPaint)
}

private fun WidgetDayGridRow.drawDayBoundaryRow(
    canvas: Canvas,
    palette: WidgetPalette,
    widthDp: Float,
    heightDp: Float,
    label: String,
) {
    val dark = palette.rootBackgroundColor.isDarkColor()
    val centerY = heightDp / 2f
    val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.accent.withAlpha(if (dark) 0.16f else 0.1f)
        style = Paint.Style.FILL
    }
    canvas.drawRect(0f, centerY - 2f, widthDp, centerY + 2f, bandPaint)
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.accent.withAlpha(if (dark) 0.82f else 0.6f)
        strokeWidth = 1.2f
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(4.5f, 3.5f), 0f)
    }
    canvas.drawLine(0f, centerY, widthDp, centerY, linePaint)
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.accent.withAlpha(if (dark) 0.92f else 0.78f)
        textSize = 8f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    canvas.drawText(
        label.shortDayGutterLabel(),
        widthDp / 2f,
        (centerY - 3.2f).coerceAtLeast(6f),
        labelPaint,
    )
}

private fun WidgetDayGridRow.drawHourRow(
    canvas: Canvas,
    palette: WidgetPalette,
    widthDp: Float,
    heightDp: Float,
    hour: Int,
    omitPriorityCards: Boolean = false,
    priorityOnly: Boolean = false,
    frame: Int = 0,
    frameCount: Int = 1,
    frameIntervalMillis: Int = 0,
    priorityBitmapScale: Float = 1f,
) {
    val contentLeft = WIDGET_DAY_ROW_SIDE_INSET_DP
    val gridLeft = contentLeft + WIDGET_DAY_TIME_COLUMN_WIDTH_DP + WIDGET_DAY_GRID_GAP_DP
    val gridRight = widthDp - WIDGET_DAY_ROW_SIDE_INSET_DP - 2f
    if (!priorityOnly) {
        val dark = palette.rootBackgroundColor.isDarkColor()
        val slotColor = if (dark) 0xFF182534.toInt() else 0xFFFAFCFF.toInt()
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted
            textSize = 8.2f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slotColor
            style = Paint.Style.FILL
        }
        repeat(hourCount.coerceAtLeast(1)) { index ->
            val slotTop = index * hourRowHeightDp
            canvas.drawText(
                "%02d:00".format(hour + index),
                contentLeft + WIDGET_DAY_TIME_COLUMN_WIDTH_DP / 2f,
                slotTop + 11f,
                timePaint,
            )
            canvas.drawRoundRect(
                RectF(
                    gridLeft,
                    slotTop + 2f,
                    gridRight,
                    slotTop + hourRowHeightDp - 2f,
                ),
                10f,
                10f,
                slotPaint,
            )
        }
    }

    val hourStart = hour * 60
    val hourEnd = (hour + hourCount.coerceAtLeast(1)) * 60
    val gridWidth = (gridRight - gridLeft).coerceAtLeast(1f)
    timedItems.forEach { layout ->
        val animated = layout.item.hasDayPriorityMotion()
        if ((omitPriorityCards && animated) || (priorityOnly && !animated)) return@forEach
        val topMinute = max(layout.startMinute, hourStart)
        val bottomMinute = min(layout.endMinute, hourEnd)
        if (bottomMinute <= topMinute) return@forEach
        val laneWidth = gridWidth / layout.laneCount.coerceAtLeast(1)
        val left = gridLeft + laneWidth * layout.lane + 1f
        val top = (topMinute - hourStart) / 60f * hourRowHeightDp + 1f
        val bottom = (bottomMinute - hourStart) / 60f * hourRowHeightDp - 1f
        val rect = RectF(
            left,
            top.coerceIn(0f, heightDp),
            left + laneWidth - 3f,
            bottom.coerceIn(0f, heightDp).coerceAtLeast(top + WIDGET_DAY_CARD_MIN_HEIGHT_DP),
        )
        drawDayGridCard(
            canvas = canvas,
            palette = palette,
            item = layout.item,
            rect = rect,
            heightDp = rect.height(),
            showMeta = rect.height() >= 40f,
            priorityMotion = if (priorityOnly) {
                layout.item.dayPriorityMotion(frame, frameCount, frameIntervalMillis, priorityBitmapScale)
            } else {
                null
            },
        )
    }

}

internal fun WidgetDayGridRow.dayNowLineOverlayBitmap(
    context: Context,
    palette: WidgetPalette,
    widthDp: Float,
    heightDp: Float,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val bitmap = Bitmap.createBitmap(
        (widthDp * density).roundToInt().coerceAtLeast(1),
        (heightDp * density).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    canvas.scale(density, density)
    val currentHour = hour ?: return bitmap
    val currentMinute = nowMinute ?: return bitmap
    val lineY = (currentMinute - currentHour * 60) / 60f * hourRowHeightDp
    if (lineY in 0f..heightDp) {
        val contentLeft = WIDGET_DAY_ROW_SIDE_INSET_DP
        val contentRight = widthDp - WIDGET_DAY_ROW_SIDE_INSET_DP - 2f
        drawDayNowLine(canvas, palette, contentRight, contentLeft, lineY, heightDp)
    }
    return bitmap
}

private fun WidgetDayGridRow.drawDayNowLine(
    canvas: Canvas,
    palette: WidgetPalette,
    contentRight: Float,
    contentLeft: Float,
    lineY: Float,
    contentHeight: Float,
) {
    val dark = palette.rootBackgroundColor.isDarkColor()
    val indicatorColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF1D1511.toInt()
    val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = indicatorColor.withAlpha(if (dark) 0.86f else 0.72f)
        style = Paint.Style.FILL
    }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = indicatorColor.withAlpha(if (dark) 0.62f else 0.52f)
        strokeWidth = 1.2f
        strokeCap = Paint.Cap.ROUND
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (dark) 0xFF151515.toInt() else 0xFFFFFFFF.toInt()
        textSize = 9.3f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val pillCenterY = lineY.coerceIn(9f, contentHeight - 9f)
    val pill = RectF(
        contentLeft + 2f,
        pillCenterY - 9f,
        contentLeft + WIDGET_DAY_TIME_COLUMN_WIDTH_DP - 2f,
        pillCenterY + 9f,
    )
    canvas.drawRoundRect(pill, 9f, 9f, pillPaint)
    val currentMinute = nowMinute ?: 0
    canvas.drawText(
        LocalTime.of((currentMinute / 60).coerceIn(0, 23), currentMinute.rem(60)).format(DateTimeFormatter.ofPattern("HH:mm")),
        pill.centerX(),
        pillCenterY + 3.8f,
        textPaint,
    )
    canvas.drawLine(pill.right, pillCenterY, contentRight, pillCenterY, linePaint)
}

private fun WidgetDayGridRow.drawDayGridCard(
    canvas: Canvas,
    palette: WidgetPalette,
    item: WidgetDayItem,
    rect: RectF,
    heightDp: Float,
    showMeta: Boolean,
    priorityMotion: WidgetDayPriorityMotion? = null,
    alpha: Float = 1f,
) {
    if (rect.width() <= 1f || rect.height() <= 1f) return
    val alphaRestore = if (alpha < 0.999f) {
        canvas.saveLayer(
            rect.left - WIDGET_DAY_PRIORITY_OVERDRAW_DP,
            rect.top - WIDGET_DAY_PRIORITY_OVERDRAW_DP,
            rect.right + WIDGET_DAY_PRIORITY_OVERDRAW_DP,
            rect.bottom + WIDGET_DAY_PRIORITY_OVERDRAW_DP,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt() },
        )
    } else {
        -1
    }
    val cardColor = if (item.completed) item.color.blendWith(palette.rootBackgroundColor, 0.48f) else item.color
    val contentColor = if (cardColor.isDarkColor()) 0xFFFFFFFF.toInt() else 0xFF1C1A18.toInt()
    priorityMotion?.let { motion ->
        val scaledWidth = rect.width() * motion.scale
        val scaledHeight = rect.height() * motion.scale
        val glowRect = RectF(
            rect.centerX() - scaledWidth / 2f + motion.translationX - motion.glowSpread / 2f,
            rect.centerY() - scaledHeight / 2f + motion.translationY - motion.glowSpread / 2f,
            rect.centerX() + scaledWidth / 2f + motion.translationX + motion.glowSpread / 2f,
            rect.centerY() + scaledHeight / 2f + motion.translationY + motion.glowSpread / 2f,
        )
        canvas.drawRoundRect(
            glowRect,
            WIDGET_DAY_CARD_RADIUS_DP + motion.glowSpread / 3f,
            WIDGET_DAY_CARD_RADIUS_DP + motion.glowSpread / 3f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = cardColor.withAlpha(motion.glowAlpha)
                style = Paint.Style.FILL
            },
        )
    }
    val restore = canvas.save()
    priorityMotion?.let { motion ->
        canvas.translate(motion.translationX, motion.translationY)
        canvas.scale(motion.scale, motion.scale, rect.centerX(), rect.centerY())
    }
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cardColor
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(rect, WIDGET_DAY_CARD_RADIUS_DP, WIDGET_DAY_CARD_RADIUS_DP, backgroundPaint)
    val titleScale = if (item.isTask) {
        ((heightDp - 18f) / 18f).coerceIn(0f, 1f)
    } else {
        ((heightDp - 13f) / 20f).coerceIn(0f, 1f)
    }
    val compactVerticalPadding = 2.6f + titleScale * 1.4f
    val contentTop = rect.top + compactVerticalPadding
    val textStart = rect.left + if (item.isTask) 23.5f else 7f
    if (item.isTask) {
        val checkboxRadius = 5.1f
        val checkboxStrokeWidth = 1.25f
        val topAlignedCenterY = contentTop + 7.9f
        val visualRadius = checkboxRadius + checkboxStrokeWidth / 2f
        if (rect.height() >= visualRadius * 2f) {
            val topSpace = topAlignedCenterY - visualRadius - rect.top
            val bottomSpace = rect.bottom - topAlignedCenterY - visualRadius
            val checkboxCenterY = if (bottomSpace < topSpace) rect.centerY() else topAlignedCenterY
            drawDayStatusGlyph(
                canvas = canvas,
                statusGlyph = item.statusGlyph,
                tint = contentColor,
                centerX = rect.left + 13.4f,
                centerY = checkboxCenterY,
                radius = checkboxRadius,
                strokeWidth = checkboxStrokeWidth,
            )
        }
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = contentColor.withAlpha(if (item.completed) 0.62f else 1f)
        textSize = 12f * (0.9f + titleScale * 0.1f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val titleMetrics = titlePaint.fontMetrics
    val titleHeight = titleMetrics.descent - titleMetrics.ascent
    if (heightDp >= titleHeight) {
        val topAlignedBaseline = contentTop - titleMetrics.ascent
        val topSpace = contentTop - rect.top
        val bottomSpace = rect.bottom - (topAlignedBaseline + titleMetrics.descent)
        val titleBaseline = if (bottomSpace < topSpace) {
            rect.centerY() - (titleMetrics.ascent + titleMetrics.descent) / 2f
        } else {
            topAlignedBaseline
        }
        drawDayFadingText(
            canvas = canvas,
            text = item.title,
            paint = titlePaint,
            startX = textStart,
            baseline = titleBaseline,
            rightEdge = rect.right,
        )
        if (showMeta && !item.location.isNullOrBlank()) {
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = contentColor.withAlpha(0.82f)
                textSize = 10.6f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val metaBaseline = titleBaseline + 12f
            if (metaBaseline + metaPaint.fontMetrics.descent <= rect.bottom - 2f) {
                drawDayFadingText(
                    canvas = canvas,
                    text = item.location,
                    paint = metaPaint,
                    startX = textStart,
                    baseline = metaBaseline,
                    rightEdge = rect.right,
                )
            }
        }
    }
    canvas.restoreToCount(restore)
    if (alphaRestore >= 0) {
        canvas.restoreToCount(alphaRestore)
    }
}

private fun WidgetDayGridRow.drawDayFadingText(
    canvas: Canvas,
    text: String,
    paint: Paint,
    startX: Float,
    baseline: Float,
    rightEdge: Float,
) {
    val availableWidth = (rightEdge - startX).coerceAtLeast(0f)
    if (availableWidth <= 0f || text.isEmpty()) return
    val metrics = paint.fontMetrics
    val top = baseline + metrics.ascent - 1f
    val bottom = baseline + metrics.descent + 1f
    val restore = canvas.saveLayer(startX, top, rightEdge, bottom, null)
    canvas.clipRect(startX, top, rightEdge, bottom)
    canvas.drawText(text, startX, baseline, paint)
    val fadeWidth = min(WIDGET_DAY_TITLE_FADE_WIDTH_DP, availableWidth * 0.36f)
    if (fadeWidth > 0f) {
        canvas.drawRect(
            rightEdge - fadeWidth,
            top,
            rightEdge,
            bottom,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    rightEdge - fadeWidth,
                    0f,
                    rightEdge,
                    0f,
                    0xFFFFFFFF.toInt(),
                    0x00FFFFFF,
                    Shader.TileMode.CLAMP,
                )
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            },
        )
    }
    canvas.restoreToCount(restore)
}

private fun WidgetDayGridRow.drawDayStatusGlyph(
    canvas: Canvas,
    statusGlyph: String,
    tint: Int,
    centerX: Float,
    centerY: Float,
    radius: Float,
    strokeWidth: Float,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        this.strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }
    canvas.drawCircle(centerX, centerY, radius, paint)
    when (statusGlyph) {
        "\u2713" -> {
            val path = Path().apply {
                moveTo(centerX - radius * 0.48f, centerY)
                lineTo(centerX - radius * 0.1f, centerY + radius * 0.38f)
                lineTo(centerX + radius * 0.56f, centerY - radius * 0.48f)
            }
            canvas.drawPath(path, paint)
        }
        "\u25D0" -> {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(centerX, centerY, radius * 0.34f, paint)
        }
        "\u00D7" -> {
            canvas.drawLine(centerX - radius * 0.44f, centerY - radius * 0.44f, centerX + radius * 0.44f, centerY + radius * 0.44f, paint)
            canvas.drawLine(centerX + radius * 0.44f, centerY - radius * 0.44f, centerX - radius * 0.44f, centerY + radius * 0.44f, paint)
        }
    }
}

private fun String.shortDayGutterLabel(): String =
    if (length > 7) "${take(5)}." else this

private data class WidgetDayPriorityMotion(
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val glowSpread: Float,
    val glowAlpha: Float,
)

private fun WidgetDayItem.dayPriorityMotion(
    frame: Int,
    frameCount: Int,
    frameIntervalMillis: Int,
    bitmapScale: Float,
): WidgetDayPriorityMotion {
    val intensity = taskPriorityIntensity(priority)
    val elapsedMillis = (frame.coerceIn(0, frameCount.coerceAtLeast(1) - 1) + 0.5f) *
        frameIntervalMillis.coerceAtLeast(1)
    val pulseDuration = (1050 - intensity * 420f).roundToInt().coerceAtLeast(520)
    val fullPulseDuration = pulseDuration * 2f
    val cycleFraction = (elapsedMillis % fullPulseDuration) / fullPulseDuration
    val halfFraction = if (cycleFraction < 0.5f) cycleFraction * 2f else (cycleFraction - 0.5f) * 2f
    val eased = motionStandardEasing(halfFraction)
    val pulse = if (cycleFraction < 0.5f) eased else 1f - eased
    val shakePhase = (((frame + 0.5f) * 42f) % 210f) / 210f
    val shake = if (priority == 1) {
        cos(shakePhase.toDouble() * PI * 6.0).toFloat() * 1.05f / bitmapScale.coerceAtLeast(0.01f)
    } else {
        0f
    }
    return WidgetDayPriorityMotion(
        translationX = shake,
        translationY = (pulse - 0.5f) * -2f * intensity,
        scale = 1f + intensity * 0.018f * pulse,
        glowSpread = 8f * intensity * (0.45f + pulse),
        glowAlpha = 0.18f * intensity * (0.45f + 0.55f * pulse),
    )
}
