package com.kgs.calendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import com.kgs.calendar.R

enum class KgsWidgetKind {
    Agenda,
    Month,
    Tasks,
    Multi,
    Day;

    val providerClass: Class<out AppWidgetProvider>
        get() = when (this) {
            Agenda -> KgsAgendaWidgetProvider::class.java
            Month -> KgsMonthWidgetProvider::class.java
            Tasks -> KgsTasksWidgetProvider::class.java
            Multi -> KgsMultiWidgetProvider::class.java
            Day -> KgsDayWidgetProvider::class.java
        }

    fun title(context: Context): String = when (this) {
        Agenda -> context.getString(R.string.agenda)
        Month -> context.getString(R.string.month)
        Tasks -> context.getString(R.string.tasks)
        Multi -> context.getString(R.string.widget_multi_title)
        Day -> context.getString(R.string.day)
    }
}

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
