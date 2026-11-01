package com.kgs.calendar.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.TypedValue
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun systemDynamicColorScheme(darkTheme: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return (if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
        .withSystemAccent(context.systemAccentColor(darkTheme), darkTheme)
}

private fun Context.systemAccentColor(darkTheme: Boolean): Color? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val preferred = if (darkTheme) "system_accent1_200" else "system_accent1_600"
        val fallback = if (darkTheme) "system_accent1_300" else "system_accent1_500"
        listOf(preferred, fallback).forEach { name ->
            val id = resources.getIdentifier(name, "color", "android")
            if (id != 0) {
                val systemColor = runCatching { Color(getColor(id)) }.getOrNull()
                if (systemColor != null) return systemColor
            }
        }
    }
    val typedValue = TypedValue()
    return if (theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)) {
        Color(typedValue.data)
    } else {
        null
    }
}

/** This context with its font scale limited to [MaxAppFontScale], for an activity's base context. */
fun Context.withAppFontScaleCap(): Context {
    val configuration = resources.configuration
    if (configuration.fontScale <= MaxAppFontScale) return this
    return createConfigurationContext(Configuration(configuration).apply { fontScale = MaxAppFontScale })
}
