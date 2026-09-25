package com.kgs.calendar.widget.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import com.kgs.calendar.widget.WIDGET_MONTH_DOT_SIZE_DP
import com.kgs.calendar.widget.dpToPx
import kotlin.math.roundToInt

internal fun monthDotBitmap(context: Context, color: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (WIDGET_MONTH_DOT_SIZE_DP * density).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = sizePx / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    canvas.drawCircle(radius, radius, radius, paint)
    return bitmap
}

internal fun widgetSortIconBitmap(context: Context, tint: Int): Bitmap {
    val size = context.dpToPx(17)
    val density = context.resources.displayMetrics.density
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        strokeWidth = 1.85f * density
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    val start = 3f * density
    canvas.drawLine(start, 5f * density, 14f * density, 5f * density, paint)
    canvas.drawLine(start, 8.5f * density, 11f * density, 8.5f * density, paint)
    canvas.drawLine(start, 12f * density, 8f * density, 12f * density, paint)
    return bitmap
}

internal fun widgetTodayDateIconBitmap(context: Context, tint: Int, day: Int): Bitmap {
    val size = context.dpToPx(20)
    val density = context.resources.displayMetrics.density
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = 1.55f * density
    val iconRect = RectF(
        stroke / 2f,
        stroke / 2f,
        size - stroke / 2f,
        size - stroke / 2f,
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawRoundRect(iconRect, 3.8f * density, 3.8f * density, paint)
    canvas.drawLine(
        stroke * 2.2f,
        size * 0.28f,
        size - stroke * 2.2f,
        size * 0.28f,
        paint,
    )
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9.2f, context.resources.displayMetrics)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val metrics = textPaint.fontMetrics
    val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f + 1.6f * density
    canvas.drawText(day.coerceIn(1, 31).toString(), size / 2f, baseline, textPaint)
    return bitmap
}

internal fun ellipsizeForPaint(text: String, paint: Paint, maxWidth: Float): String {
    if (maxWidth <= 0f || text.isEmpty()) return ""
    if (paint.measureText(text) <= maxWidth) return text
    val ellipsis = "\u2026"
    val ellipsisWidth = paint.measureText(ellipsis)
    if (ellipsisWidth >= maxWidth) return ""
    var low = 0
    var high = text.length
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (paint.measureText(text, 0, mid) + ellipsisWidth <= maxWidth) {
            low = mid
        } else {
            high = mid - 1
        }
    }
    return text.take(low).trimEnd() + ellipsis
}
