package com.kgs.calendar.ui.platform

import com.kgs.calendar.res.SharedR as R

internal actual object PlatformFeatures {
    actual val homeScreenWidgets: Boolean = false
    actual val haptics: Boolean = false
    actual val systemColorsLabel: Int = R.string.windows_colors
}
