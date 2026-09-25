package com.kgs.calendar.widget.render

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.kgs.calendar.R
import com.kgs.calendar.data.settings.WidgetTaskCreateMode
import com.kgs.calendar.data.settings.WidgetTaskSortMode
import com.kgs.calendar.widget.bitmap.widgetSortIconBitmap
import com.kgs.calendar.widget.model.widgetLabel
import java.time.LocalDate

internal fun RemoteViews.bindTasksSortButtonState(
    context: Context,
    appWidgetId: Int,
    mode: WidgetTaskSortMode,
    widthDp: Float,
    createMode: WidgetTaskCreateMode? = null,
    updateDisplayedChild: Boolean = true,
) {
    setViewVisibility(R.id.widget_sort, View.VISIBLE)
    setImageViewBitmap(R.id.widget_sort_icon, widgetSortIconBitmap(context, 0xFFFFFFFF.toInt()))
    setTextViewText(R.id.widget_sort_label_date, WidgetTaskSortMode.Date.widgetLabel(context))
    setTextViewText(R.id.widget_sort_label_priority, WidgetTaskSortMode.Priority.widgetLabel(context))
    setTextViewText(R.id.widget_sort_label_status, WidgetTaskSortMode.Status.widgetLabel(context))
    setTextColor(R.id.widget_sort_label_date, 0xFFFFFFFF.toInt())
    setTextColor(R.id.widget_sort_label_priority, 0xFFFFFFFF.toInt())
    setTextColor(R.id.widget_sort_label_status, 0xFFFFFFFF.toInt())
    if (updateDisplayedChild) {
        setDisplayedChild(R.id.widget_sort_label_flipper, mode.ordinal)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setViewLayoutWidth(R.id.widget_sort, widthDp, TypedValue.COMPLEX_UNIT_DIP)
    }
    setTextViewText(R.id.widget_badge, "+")
    setTextViewTextSize(R.id.widget_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
    setTextColor(R.id.widget_badge, 0xFFFFFFFF.toInt())
    createMode?.let {
        setOnClickPendingIntent(
            R.id.widget_badge,
            WidgetPendingIntents(context).createTaskPendingIntent(appWidgetId, LocalDate.now(), it),
        )
    }
    val sortPendingIntent = WidgetPendingIntents(context).tasksSortPendingIntent(appWidgetId)
    setOnClickPendingIntent(R.id.widget_sort, sortPendingIntent)
    setOnClickPendingIntent(R.id.widget_sort_icon, sortPendingIntent)
    setOnClickPendingIntent(R.id.widget_sort_label_flipper, sortPendingIntent)
    setOnClickPendingIntent(R.id.widget_sort_label_date, sortPendingIntent)
    setOnClickPendingIntent(R.id.widget_sort_label_priority, sortPendingIntent)
    setOnClickPendingIntent(R.id.widget_sort_label_status, sortPendingIntent)
}

internal fun RemoteViews.bindTasksSortButtonWidth(widthDp: Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setViewLayoutWidth(R.id.widget_sort, widthDp, TypedValue.COMPLEX_UNIT_DIP)
    }
}

internal fun WidgetTaskSortMode.widgetButtonWidthDp(context: Context): Float {
    val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
    val textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, context.resources.displayMetrics)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSizePx
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val labelWidthDp = paint.measureText(widgetLabel(context)) / density
    return (13f + 17f + 5f + labelWidthDp + 13f + 8f).coerceAtLeast(70f)
}
