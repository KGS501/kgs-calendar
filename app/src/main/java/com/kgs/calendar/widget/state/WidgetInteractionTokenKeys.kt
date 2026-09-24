package com.kgs.calendar.widget.state

internal fun tasksListTokenKey(appWidgetId: Int): String =
    "tasks-list:$appWidgetId"

internal fun tasksSortButtonTokenKey(appWidgetId: Int): String =
    "tasks-sort-button:$appWidgetId"

internal fun dayAllDayTokenKey(appWidgetId: Int): String =
    "day-all-day:$appWidgetId"
