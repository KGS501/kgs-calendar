package com.kgs.calendar.widget

import android.appwidget.AppWidgetProvider
import android.content.Context
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
