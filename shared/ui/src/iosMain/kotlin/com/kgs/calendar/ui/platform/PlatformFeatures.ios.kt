package com.kgs.calendar.ui.platform

/** WidgetKit widgets are not built yet; device calendars come from EventKit. */
actual object PlatformFeatures {
    actual val isIos: Boolean = true
    actual val deviceCalendars: Boolean = true
    actual val homeScreenWidgets: Boolean = false
    actual val systemColors: Boolean = false
}
