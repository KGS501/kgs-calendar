package com.kgs.calendar.widget.model

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.WIDGET_AGENDA_COLUMN_GAP_DP
import com.kgs.calendar.widget.WIDGET_AGENDA_DATE_COLUMN_WIDTH_DP
import com.kgs.calendar.widget.WIDGET_TASK_CARD_SIDE_INSET_DP
import com.kgs.calendar.widget.WIDGET_TASK_MIN_CARD_WIDTH_DP

internal data class WidgetSize(
    val widthDp: Int,
    val heightDp: Int,
) {
    val compact: Boolean = widthDp < 280 || heightDp < 300

    companion object {
        fun from(context: Context, options: Bundle, kind: KgsWidgetKind): WidgetSize {
            val fallback = when (kind) {
                KgsWidgetKind.Month -> 320 to 320
                KgsWidgetKind.Multi -> 320 to 460
                else -> 320 to 180
            }
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val currentWidth = if (portrait) {
                minWidth.takeIf { it > 0 } ?: maxWidth
            } else {
                maxWidth.takeIf { it > 0 } ?: minWidth
            }
            val currentHeight = if (portrait) {
                maxHeight.takeIf { it > 0 } ?: minHeight
            } else {
                minHeight.takeIf { it > 0 } ?: maxHeight
            }
            return WidgetSize(
                widthDp = currentWidth.takeIf { it > 0 } ?: fallback.first,
                heightDp = currentHeight.takeIf { it > 0 } ?: fallback.second,
            )
        }
    }
}

internal fun WidgetSize.collectionArtWidthDp(kind: KgsWidgetKind): Float =
    when (kind) {
        KgsWidgetKind.Agenda,
        KgsWidgetKind.Multi,
        KgsWidgetKind.Day -> (
            widthDp -
                WIDGET_TASK_CARD_SIDE_INSET_DP * 2 -
                WIDGET_AGENDA_DATE_COLUMN_WIDTH_DP -
                WIDGET_AGENDA_COLUMN_GAP_DP
            ).toFloat().coerceAtLeast(WIDGET_TASK_MIN_CARD_WIDTH_DP)
        else -> widthDp.toFloat().coerceAtLeast(1f)
    }

internal fun WidgetSize.dayGridContentWidthDp(): Float =
    widthDp.toFloat().coerceAtLeast(120f)

internal fun shouldHideWidgetTitle(
    widthDp: Number,
    title: String,
    reservedDp: Float,
    textSp: Float,
): Boolean {
    val available = (widthDp.toFloat() - reservedDp).coerceAtLeast(0f)
    if (available < 28f) return true
    val estimatedTitleWidth = title.length * textSp * 0.54f
    if (estimatedTitleWidth <= 0f) return false
    return available / estimatedTitleWidth < 0.38f
}
