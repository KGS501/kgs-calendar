package com.kgs.calendar.widget.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.WIDGET_AGENDA_ART_BITMAP_SCALE
import com.kgs.calendar.widget.WIDGET_AGENDA_EVENT_CARD_RADIUS_DP
import com.kgs.calendar.widget.WIDGET_DAY_EVENT_CARD_RADIUS_DP
import com.kgs.calendar.widget.WIDGET_TASK_CARD_HEIGHT_DP
import com.kgs.calendar.widget.WIDGET_TASK_CARD_RENDERER
import com.kgs.calendar.widget.WIDGET_TASK_MAX_DEPTH
import com.kgs.calendar.widget.WIDGET_TASK_MIN_CARD_WIDTH_DP
import com.kgs.calendar.widget.WIDGET_TASK_PRIORITY_BITMAP_SCALE
import com.kgs.calendar.widget.WIDGET_TASK_PRIORITY_OVERDRAW_DP
import com.kgs.calendar.widget.WIDGET_TASK_STATUS_RADIUS_DP
import com.kgs.calendar.widget.WIDGET_TASK_STATUS_STROKE_DP
import com.kgs.calendar.widget.WIDGET_TASK_SUBTASK_ARROW_HALF_HEIGHT_DP
import com.kgs.calendar.widget.WIDGET_TASK_SUBTASK_ARROW_HALF_WIDTH_DP
import com.kgs.calendar.widget.WIDGET_TASK_SUBTASK_ARROW_STROKE_DP
import com.kgs.calendar.widget.WIDGET_TASK_TRANSITION_BITMAP_SCALE
import com.kgs.calendar.widget.dpToPx
import com.kgs.calendar.widget.model.WidgetListRow
import com.kgs.calendar.widget.model.lerpFloat
import com.kgs.calendar.widget.theme.WidgetPalette
import com.kgs.calendar.widget.theme.blendWith
import com.kgs.calendar.widget.theme.greyedOut
import com.kgs.calendar.widget.theme.isDarkColor
import com.kgs.calendar.widget.theme.withAlpha
import kotlin.math.roundToInt

internal fun WidgetListRow.agendaEventCardBitmap(
    context: Context,
    palette: WidgetPalette,
    cardWidthDp: Float,
    rowHeightDp: Int,
    dayCard: Boolean,
    muted: Boolean,
): Bitmap {
    val widthDp = cardWidthDp.coerceAtLeast(WIDGET_TASK_MIN_CARD_WIDTH_DP)
    val bitmapScale = context.resources.displayMetrics.density * WIDGET_AGENDA_ART_BITMAP_SCALE
    val bitmap = Bitmap.createBitmap(
        (widthDp * bitmapScale).roundToInt().coerceAtLeast(1),
        (rowHeightDp * bitmapScale).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    canvas.drawColor(palette.rootBackgroundColor)
    canvas.scale(bitmapScale, bitmapScale)

    val baseColor = color
    val tentative = eventStatus.equals("TENTATIVE", ignoreCase = true)
    val backgroundColor = when {
        tentative -> palette.rootBackgroundColor
        muted -> baseColor.greyedOut(0.62f).blendWith(palette.rootBackgroundColor, 0.28f)
        else -> baseColor
    }
    val contentBase = when {
        tentative -> palette.text
        baseColor.isDarkColor() && !muted -> 0xFFFFFFFF.toInt()
        else -> 0xFF1C1A18.toInt()
    }
    val contentColor = contentBase.withAlpha(if (muted) 0.64f else 1f)
    val secondaryColor = contentBase.withAlpha(if (muted) 0.72f else 0.92f)
    val tertiaryColor = contentBase.withAlpha(if (muted) 0.66f else 0.86f)
    val radius = if (dayCard) WIDGET_DAY_EVENT_CARD_RADIUS_DP else WIDGET_AGENDA_EVENT_CARD_RADIUS_DP
    val cardRect = RectF(0.5f, 0.5f, widthDp - 0.5f, rowHeightDp - 0.5f)
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = backgroundColor
    }
    canvas.drawRoundRect(cardRect, radius, radius, backgroundPaint)
    if (tentative || muted) {
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = if (tentative) baseColor.withAlpha(0.95f) else baseColor.greyedOut(0.7f).withAlpha(0.9f)
            if (tentative) {
                pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
            }
        }
        canvas.drawRoundRect(cardRect, radius, radius, borderPaint)
    }

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = contentColor
        textSize = if (dayCard) 12f else 13f
        typeface = Typeface.create(Typeface.SANS_SERIF, if (dayCard) Typeface.BOLD else Typeface.BOLD)
        isStrikeThruText = eventStatus.equals("CANCELLED", ignoreCase = true)
    }
    val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryColor
        textSize = if (dayCard) 10.5f else 10.8f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        isStrikeThruText = titlePaint.isStrikeThruText
    }
    val locationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tertiaryColor
        textSize = 10f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        isStrikeThruText = titlePaint.isStrikeThruText
    }
    val textStart = 12f
    val textWidth = (widthDp - 24f).coerceAtLeast(0f)
    val locationText = location?.takeIf { it.isNotBlank() }
    if (dayCard) {
        val titleBaseline = if (locationText == null) rowHeightDp / 2f + 4f else 20f
        canvas.drawText(ellipsizeForPaint(title, titlePaint, textWidth), textStart, titleBaseline, titlePaint)
        locationText?.let {
            canvas.drawText(ellipsizeForPaint(it, locationPaint, textWidth), textStart, 34.5f, locationPaint)
        }
    } else {
        canvas.drawText(ellipsizeForPaint(title, titlePaint, textWidth), textStart, 19f, titlePaint)
        canvas.drawText(ellipsizeForPaint(meta, timePaint, textWidth), textStart, 34.5f, timePaint)
        locationText?.let {
            canvas.drawText(ellipsizeForPaint(it, locationPaint, textWidth), textStart, 48.5f, locationPaint)
        }
    }
    return bitmap
}

internal fun WidgetListRow.taskRowBackgroundBitmap(
    context: Context,
    palette: WidgetPalette,
    taskArtWidthDp: Float,
    cardColor: Int,
    contentColor: Int,
    secondaryColor: Int,
    subtaskExpansionProgress: Float,
    lightweight: Boolean,
    baseSpec: TaskCardBaseSpec,
    cardMeta: String,
): Bitmap {
    val bitmapScale = context.resources.displayMetrics.density *
        if (lightweight) WIDGET_TASK_TRANSITION_BITMAP_SCALE else WIDGET_TASK_PRIORITY_BITMAP_SCALE
    val bitmap = Bitmap.createBitmap(
        (taskArtWidthDp * bitmapScale).roundToInt().coerceAtLeast(1),
        (baseSpec.rowHeightDp * bitmapScale).roundToInt().coerceAtLeast(1),
        Bitmap.Config.RGB_565,
    )
    val canvas = Canvas(bitmap)
    canvas.drawColor(palette.rootBackgroundColor)
    canvas.scale(bitmapScale, bitmapScale)
    drawTaskHierarchy(canvas, palette.hierarchyLine, subtaskExpansionProgress, baseSpec)
    drawTaskCard(canvas, baseSpec, cardColor, TaskPriorityEffect.None)
    drawTaskContent(
        context = context,
        canvas = canvas,
        baseSpec = baseSpec,
        contentColor = contentColor,
        secondaryColor = secondaryColor,
        effect = TaskPriorityEffect.None,
        cardMeta = cardMeta,
        subtaskExpansionProgress = subtaskExpansionProgress,
    )
    return bitmap
}

internal fun WidgetListRow.taskPriorityMotionBitmap(
    context: Context,
    palette: WidgetPalette,
    taskArtWidthDp: Float,
    cardColor: Int,
    contentColor: Int,
    secondaryColor: Int,
    priority: Int?,
    frame: Int,
    frameCount: Int,
    subtaskExpansionProgress: Float,
    baseSpec: TaskCardBaseSpec,
    cardMeta: String,
): Bitmap {
    val overdrawDp = if (launchKind == KgsWidgetKind.Agenda || launchKind == KgsWidgetKind.Day) WIDGET_TASK_PRIORITY_OVERDRAW_DP else 0f
    val bitmapScale = context.resources.displayMetrics.density * WIDGET_TASK_PRIORITY_BITMAP_SCALE
    val bitmap = Bitmap.createBitmap(
        ((taskArtWidthDp + overdrawDp * 2f) * bitmapScale).roundToInt().coerceAtLeast(1),
        ((baseSpec.rowHeightDp + overdrawDp * 2f) * bitmapScale).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    canvas.drawColor(palette.rootBackgroundColor)
    canvas.scale(bitmapScale, bitmapScale)
    canvas.translate(overdrawDp, overdrawDp)
    val effect = WIDGET_TASK_CARD_RENDERER.effect(priority, frame, frameCount, bitmapScale)
    val glowOutset = effect.glowSpread / 2f
    drawTaskHierarchy(canvas, palette.hierarchyLine, subtaskExpansionProgress, baseSpec)
    drawTaskCard(
        canvas = canvas,
        baseSpec = baseSpec,
        color = cardColor,
        effect = effect.copy(scale = 1f),
        horizontalSpread = glowOutset,
        verticalSpread = glowOutset,
        alpha = effect.glowAlpha,
        radius = baseSpec.cornerRadiusDp + effect.glowSpread / 3f,
    )
    drawTaskCard(
        canvas = canvas,
        baseSpec = baseSpec,
        color = cardColor,
        effect = effect,
    )
    drawTaskContent(
        context = context,
        canvas = canvas,
        baseSpec = baseSpec,
        contentColor = contentColor.withAlpha(if (completed) 0.62f else 1f),
        secondaryColor = secondaryColor,
        effect = effect,
        cardMeta = cardMeta,
        subtaskExpansionProgress = subtaskExpansionProgress,
    )
    return bitmap
}

private fun WidgetListRow.drawTaskHierarchy(
    canvas: Canvas,
    lineColor: Int,
    subtaskExpansionProgress: Float,
    baseSpec: TaskCardBaseSpec,
) {
    val boundedDepth = baseSpec.hierarchyDepth
    if (boundedDepth <= 0 && childCount <= 0 && continuationLevels.isEmpty()) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        strokeWidth = baseSpec.hierarchyLineStrokeDp
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    val centerY = baseSpec.rowHeightDp / 2f
    val height = baseSpec.rowHeightDp
    continuationLevels.forEach { level ->
        if (level in 0 until WIDGET_TASK_MAX_DEPTH) {
            val x = baseSpec.hierarchySideInsetDp + level * baseSpec.hierarchyIndentDp + baseSpec.hierarchyStemDp
            canvas.drawLine(x, -baseSpec.hierarchyOverlapDp, x, height + baseSpec.hierarchyOverlapDp, paint)
        }
    }
    if (boundedDepth > 0) {
        val branchLevel = boundedDepth - 1
        val branchX = baseSpec.hierarchySideInsetDp + branchLevel * baseSpec.hierarchyIndentDp + baseSpec.hierarchyStemDp
        val branchBottom = if (lastSibling) centerY else height + baseSpec.hierarchyOverlapDp
        canvas.drawLine(branchX, -baseSpec.hierarchyOverlapDp, branchX, branchBottom, paint)
        val branchEndX = baseSpec.hierarchySideInsetDp + boundedDepth * baseSpec.hierarchyIndentDp + baseSpec.hierarchyOverlapDp + 2f
        canvas.drawLine(branchX, centerY, branchEndX, centerY, paint)
    }
    if (childCount > 0 && subtaskExpansionProgress > 0.01f) {
        val x = baseSpec.hierarchySideInsetDp + boundedDepth * baseSpec.hierarchyIndentDp + baseSpec.hierarchyStemDp
        val cardBottom = (baseSpec.rowHeightDp + baseSpec.cardHeightDp) / 2f
        val tailEnd = lerpFloat(cardBottom - 1f, height + baseSpec.hierarchyOverlapDp, subtaskExpansionProgress.coerceIn(0f, 1f))
        canvas.drawLine(x, cardBottom - 1f, x, tailEnd, paint)
    }
}

private fun WidgetListRow.drawTaskCard(
    canvas: Canvas,
    baseSpec: TaskCardBaseSpec,
    color: Int,
    effect: TaskPriorityEffect,
    horizontalSpread: Float = 0f,
    verticalSpread: Float = horizontalSpread,
    alpha: Float = 1f,
    radius: Float = baseSpec.cornerRadiusDp + horizontalSpread * 0.45f,
) {
    val cardTop = (baseSpec.rowHeightDp - baseSpec.cardHeightDp) / 2f
    val cardBottom = cardTop + baseSpec.cardHeightDp
    val centerX = (baseSpec.cardLeftDp + baseSpec.cardRightDp) / 2f + effect.translationX
    val centerY = (cardTop + cardBottom) / 2f + effect.translationY
    val halfWidth = ((baseSpec.cardRightDp - baseSpec.cardLeftDp) * effect.scale) / 2f
    val halfHeight = (baseSpec.cardHeightDp * effect.scale) / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = if (alpha >= 0.999f) color else color.withAlpha(alpha)
    }
    val rect = RectF(
        centerX - halfWidth - horizontalSpread,
        centerY - halfHeight - verticalSpread,
        centerX + halfWidth + horizontalSpread,
        centerY + halfHeight + verticalSpread,
    )
    canvas.drawRoundRect(rect, radius, radius, paint)
}

private fun WidgetListRow.drawTaskContent(
    context: Context,
    canvas: Canvas,
    baseSpec: TaskCardBaseSpec,
    contentColor: Int,
    secondaryColor: Int,
    effect: TaskPriorityEffect,
    cardMeta: String,
    subtaskExpansionProgress: Float,
) {
    val cardCenterX = (baseSpec.cardLeftDp + baseSpec.cardRightDp) / 2f
    fun transformedX(value: Float): Float =
        cardCenterX + (value - cardCenterX) * effect.scale + effect.translationX

    val centerY = baseSpec.rowHeightDp / 2f + effect.translationY
    drawTaskStatusGlyph(
        canvas = canvas,
        statusGlyph = statusGlyph,
        tint = contentColor,
        centerX = transformedX(baseSpec.statusCenterXDp),
        centerY = centerY,
        radius = baseSpec.statusRadiusDp * effect.scale,
        strokeWidth = baseSpec.statusStrokeDp * effect.scale,
    )

    val hasMeta = cardMeta.isNotBlank()
    val textStart = transformedX(baseSpec.textStartDp)
    val textEnd = transformedX(baseSpec.textEndDp)
    val fontScale = context.resources.displayMetrics.scaledDensity / context.resources.displayMetrics.density
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = contentColor
        textSize = baseSpec.titleTextSizeSp * fontScale * effect.scale
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryColor
        textSize = baseSpec.metaTextSizeSp * fontScale * effect.scale
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val maxTextWidth = (textEnd - textStart).coerceAtLeast(0f)
    canvas.drawText(
        ellipsizeForPaint(title, titlePaint, maxTextWidth),
        textStart,
        centerY + baseSpec.titleBaselineOffsetDp * effect.scale,
        titlePaint,
    )
    if (hasMeta) {
        canvas.drawText(
            ellipsizeForPaint(cardMeta, metaPaint, maxTextWidth),
            textStart,
            centerY + baseSpec.metaBaselineOffsetDp * effect.scale,
            metaPaint,
        )
    }

    if (childCount > 0) {
        drawTaskSubtasksChevron(
            canvas = canvas,
            tint = contentColor,
            centerX = transformedX(baseSpec.chevronCenterXDp),
            centerY = centerY,
            expandedProgress = subtaskExpansionProgress,
            scale = effect.scale,
        )
    }
}

internal fun WidgetListRow.taskStatusIconBitmap(context: Context, statusGlyph: String, tint: Int): Bitmap {
    val width = context.dpToPx(30)
    val height = context.dpToPx(WIDGET_TASK_CARD_HEIGHT_DP)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centerX = width / 2f
    val centerY = height / 2f
    drawTaskStatusGlyph(
        canvas = canvas,
        statusGlyph = statusGlyph,
        tint = tint,
        centerX = centerX,
        centerY = centerY,
        radius = context.dpToPx(WIDGET_TASK_STATUS_RADIUS_DP),
        strokeWidth = context.dpToPx(WIDGET_TASK_STATUS_STROKE_DP),
    )
    return bitmap
}

private fun WidgetListRow.drawTaskStatusGlyph(
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
    }
    when (statusGlyph) {
        "\u2713" -> {
            paint.style = Paint.Style.STROKE
            canvas.drawCircle(centerX, centerY, radius, paint)
            val path = Path().apply {
                moveTo(centerX - radius * 0.48f, centerY + radius * 0.01f)
                lineTo(centerX - radius * 0.11f, centerY + radius * 0.38f)
                lineTo(centerX + radius * 0.56f, centerY - radius * 0.48f)
            }
            canvas.drawPath(path, paint)
        }
        "\u25D0" -> {
            paint.style = Paint.Style.STROKE
            canvas.drawCircle(centerX, centerY, radius, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(centerX, centerY, radius * 0.34f, paint)
        }
        "\u00D7" -> {
            paint.style = Paint.Style.STROKE
            canvas.drawCircle(centerX, centerY, radius, paint)
            canvas.drawLine(
                centerX - radius * 0.44f,
                centerY - radius * 0.44f,
                centerX + radius * 0.44f,
                centerY + radius * 0.44f,
                paint,
            )
            canvas.drawLine(
                centerX + radius * 0.44f,
                centerY - radius * 0.44f,
                centerX - radius * 0.44f,
                centerY + radius * 0.44f,
                paint,
            )
        }
        else -> {
            paint.style = Paint.Style.STROKE
            canvas.drawCircle(centerX, centerY, radius, paint)
        }
    }
}

internal fun WidgetListRow.taskSubtasksArrowBitmap(context: Context, tint: Int, expandedProgress: Float): Bitmap {
    val size = context.dpToPx(30)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawTaskSubtasksChevron(
        canvas = canvas,
        tint = tint,
        centerX = size / 2f,
        centerY = size / 2f,
        expandedProgress = expandedProgress,
        scale = context.resources.displayMetrics.density,
    )
    return bitmap
}

private fun WidgetListRow.drawTaskSubtasksChevron(
    canvas: Canvas,
    tint: Int,
    centerX: Float,
    centerY: Float,
    expandedProgress: Float,
    scale: Float,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        strokeWidth = WIDGET_TASK_SUBTASK_ARROW_STROKE_DP * scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }
    val progress = expandedProgress.coerceIn(0f, 1f)
    canvas.save()
    canvas.rotate(-90f * (1f - progress), centerX, centerY)
    val path = Path().apply {
        moveTo(
            centerX - WIDGET_TASK_SUBTASK_ARROW_HALF_WIDTH_DP * scale,
            centerY - WIDGET_TASK_SUBTASK_ARROW_HALF_HEIGHT_DP * scale,
        )
        lineTo(centerX, centerY + WIDGET_TASK_SUBTASK_ARROW_HALF_HEIGHT_DP * scale)
        lineTo(
            centerX + WIDGET_TASK_SUBTASK_ARROW_HALF_WIDTH_DP * scale,
            centerY - WIDGET_TASK_SUBTASK_ARROW_HALF_HEIGHT_DP * scale,
        )
    }
    canvas.drawPath(path, paint)
    canvas.restore()
}
