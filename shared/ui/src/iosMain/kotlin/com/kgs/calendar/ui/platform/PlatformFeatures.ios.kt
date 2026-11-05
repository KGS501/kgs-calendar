package com.kgs.calendar.ui.platform

import com.kgs.calendar.ui.res.Res
import com.kgs.calendar.ui.res.StringResource

/** WidgetKit widgets are not built yet; device calendars come from EventKit. */
actual object PlatformFeatures {
    actual val isIos: Boolean = true
    actual val isWindows: Boolean = false
    actual val deviceCalendars: Boolean = true
    actual val homeScreenWidgets: Boolean = false
    actual val systemColors: Boolean = false
    actual val systemColorsLabel: StringResource = Res.string.android_colors
    actual val haptics: Boolean = true
    actual val privacyDeleteBody: StringResource = Res.string.privacy_delete_body_ios
}
