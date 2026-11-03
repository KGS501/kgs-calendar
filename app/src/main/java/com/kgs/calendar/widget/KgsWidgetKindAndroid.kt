package com.kgs.calendar.widget

import android.appwidget.AppWidgetProvider
import android.content.Context
import com.kgs.calendar.R

val KgsWidgetKind.providerClass: Class<out AppWidgetProvider>
    get() = when (this) {
        KgsWidgetKind.Agenda -> KgsAgendaWidgetProvider::class.java
        KgsWidgetKind.Month -> KgsMonthWidgetProvider::class.java
        KgsWidgetKind.Tasks -> KgsTasksWidgetProvider::class.java
        KgsWidgetKind.Multi -> KgsMultiWidgetProvider::class.java
        KgsWidgetKind.Day -> KgsDayWidgetProvider::class.java
    }

fun KgsWidgetKind.title(context: Context): String = when (this) {
    KgsWidgetKind.Agenda -> context.getString(R.string.agenda)
    KgsWidgetKind.Month -> context.getString(R.string.month)
    KgsWidgetKind.Tasks -> context.getString(R.string.tasks)
    KgsWidgetKind.Multi -> context.getString(R.string.widget_multi_title)
    KgsWidgetKind.Day -> context.getString(R.string.day)
}
