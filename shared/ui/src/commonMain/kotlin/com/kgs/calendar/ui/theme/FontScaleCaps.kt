package com.kgs.calendar.ui.theme

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
const val MaxAppFontScale = 1.5f

/** The largest font scale of the calendar surfaces. */
const val MaxCalendarFontScale = 1.3f

/** Lays out [content] with the font scale limited to [maxFontScale]. */
@Composable
fun CappedFontScale(maxFontScale: Float, content: @Composable () -> Unit) {
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
fun calendarTextScale(): Float = LocalDensity.current.fontScale.coerceIn(1f, MaxCalendarFontScale)
