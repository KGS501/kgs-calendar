package com.kgs.calendar.ui.platform

import com.kgs.calendar.res.SharedR as R

internal actual object PlatformFeatures {
    actual val homeScreenWidgets: Boolean = true
    actual val haptics: Boolean = true
    actual val systemColorsLabel: Int = R.string.android_colors
}
