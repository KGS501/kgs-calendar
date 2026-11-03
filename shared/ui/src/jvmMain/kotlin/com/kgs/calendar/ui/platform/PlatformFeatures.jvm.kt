package com.kgs.calendar.ui.platform

/** The Windows app: no device calendars or widgets; "system" colours follow Windows' accent colour. */
actual object PlatformFeatures {
    actual val isIos: Boolean = false
    actual val deviceCalendars: Boolean = false
    actual val homeScreenWidgets: Boolean = false
    actual val systemColors: Boolean = true
}
