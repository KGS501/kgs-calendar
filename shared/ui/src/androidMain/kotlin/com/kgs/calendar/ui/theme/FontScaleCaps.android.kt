package com.kgs.calendar.ui.theme

import android.content.Context
import android.content.res.Configuration

/** This context with its font scale limited to [MaxAppFontScale], for an activity's base context. */
fun Context.withAppFontScaleCap(): Context {
    val configuration = resources.configuration
    if (configuration.fontScale <= MaxAppFontScale) return this
    return createConfigurationContext(Configuration(configuration).apply { fontScale = MaxAppFontScale })
}
