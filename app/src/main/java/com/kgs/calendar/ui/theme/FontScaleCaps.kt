package com.kgs.calendar.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/*
 * The app follows the system font size up to a limit. Above it, single words no longer fit narrow
 * labels and break mid-word. The calendar surfaces (timeline, month grid, their headers and the
 * toolbar) pack seven days into one row, so they stop growing earlier and scale their cells with
 * the text instead.
 */

/** The largest system font scale the app follows. */
internal const val MaxAppFontScale = 1.5f

/** The largest font scale of the calendar surfaces. */
internal const val MaxCalendarFontScale = 1.3f

/** This context with its font scale limited to [MaxAppFontScale], for an activity's base context. */
fun Context.withAppFontScaleCap(): Context {
    val configuration = resources.configuration
    if (configuration.fontScale <= MaxAppFontScale) return this
    return createConfigurationContext(Configuration(configuration).apply { fontScale = MaxAppFontScale })
}

/** Lays out [content] with the font scale limited to [maxFontScale]. */
@Composable
internal fun CappedFontScale(maxFontScale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= maxFontScale) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density, maxFontScale), content = content)
    }
}

/**
 * How much fixed-size calendar cells grow with the text: 1 at the default font size, up to
 * [MaxCalendarFontScale].
 */
@Composable
internal fun calendarTextScale(): Float = LocalDensity.current.fontScale.coerceIn(1f, MaxCalendarFontScale)
