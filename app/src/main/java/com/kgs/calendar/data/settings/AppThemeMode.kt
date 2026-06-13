package com.kgs.calendar.data.settings

enum class AppThemeMode(val label: String) {
    KgsBlue("KGS Blue"),
    KgsWarm("KGS Warm"),
    KgsFresh("KGS Fresh"),
    SystemDynamic("Android colors"),
}

enum class AppColorMode(val label: String) {
    Auto("Auto"),
    Light("Light"),
    Dark("Dark"),
}

enum class WidgetThemeMode(val label: String) {
    FollowApp("Follow app"),
    KgsBlue("KGS Blue"),
    KgsWarm("KGS Warm"),
    KgsFresh("KGS Fresh"),
    SystemDynamic("Android colors"),
}

enum class WidgetColorMode(val label: String) {
    FollowApp("Follow app"),
    FollowOs("Follow OS"),
    Light("Light"),
    Dark("Dark"),
}

enum class WidgetTaskDisplayMode(val label: String) {
    Planned("Planned tasks"),
    Unplanned("Unplanned tasks"),
    Today("Tasks for today"),
}

enum class WidgetTaskSortMode(val label: String) {
    Date("Date"),
    Priority("Priority"),
    Status("Status"),
}

enum class WidgetTaskCreateMode(val label: String) {
    Today("Task for today"),
    Unplanned("Unplanned task"),
}

enum class AppLanguageMode(val localeTag: String?) {
    System(null),
    English("en"),
    German("de"),
}

enum class TaskColorMode(val label: String) {
    Collection("Calendar color"),
    Priority("Priority"),
}
