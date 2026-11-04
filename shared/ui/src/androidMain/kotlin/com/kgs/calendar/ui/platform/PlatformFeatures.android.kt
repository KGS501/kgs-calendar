package com.kgs.calendar.ui.platform

import com.kgs.calendar.ui.res.Res
import com.kgs.calendar.ui.res.StringResource

actual object PlatformFeatures {
    actual val isIos: Boolean = false
    actual val deviceCalendars: Boolean = true
    actual val homeScreenWidgets: Boolean = true
    actual val systemColors: Boolean = true
    actual val systemColorsLabel: StringResource = Res.string.android_colors
    actual val haptics: Boolean = true
}
