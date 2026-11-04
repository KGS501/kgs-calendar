package com.kgs.calendar.ui.platform

import com.kgs.calendar.ui.res.Res
import com.kgs.calendar.ui.res.StringResource

/** The Windows app: no device calendars or widgets; "system" colours follow Windows' accent colour. */
actual object PlatformFeatures {
    actual val isIos: Boolean = false
    actual val isWindows: Boolean = true
    actual val deviceCalendars: Boolean = false
    actual val homeScreenWidgets: Boolean = false
    actual val systemColors: Boolean = true
    actual val systemColorsLabel: StringResource = Res.string.windows_colors
    actual val haptics: Boolean = false
}
