package com.kgs.calendar.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class TimelineDayHeaderMode {
    Portrait,
    Landscape,
    LandscapeCompact,
}

internal data class TimelineHeaderPresentation(
    val mode: TimelineDayHeaderMode,
    val height: Dp,
    val weekLabelHeight: Dp,
    val weekLabelTopOffset: Dp,
    val allowTopOverflow: Boolean,
    val weekLabelFontSize: Float,
    val multiDayControlsHeight: Dp,
    val multiDayControlsTopOffset: Dp,
    val showAllDaySection: Boolean,
)

internal fun timelineHeaderPresentation(
    isLandscape: Boolean,
    landscapeCompactRequested: Boolean,
    showCalendarWeeks: Boolean = false,
): TimelineHeaderPresentation = when {
    !isLandscape -> TimelineHeaderPresentation(
        mode = TimelineDayHeaderMode.Portrait,
        height = 56.dp + if (showCalendarWeeks) 28.dp else 0.dp,
        weekLabelHeight = if (showCalendarWeeks) 28.dp else 0.dp,
        weekLabelTopOffset = if (showCalendarWeeks) (-10).dp else 0.dp,
        allowTopOverflow = showCalendarWeeks,
        weekLabelFontSize = 16f,
        multiDayControlsHeight = 56.dp,
        multiDayControlsTopOffset = if (showCalendarWeeks) 28.dp else 0.dp,
        showAllDaySection = true,
    )
    landscapeCompactRequested -> TimelineHeaderPresentation(
        mode = TimelineDayHeaderMode.LandscapeCompact,
        height = 30.dp + if (showCalendarWeeks) 23.dp else 0.dp,
        weekLabelHeight = if (showCalendarWeeks) 23.dp else 0.dp,
        weekLabelTopOffset = if (showCalendarWeeks) (-10).dp else 0.dp,
        allowTopOverflow = showCalendarWeeks,
        weekLabelFontSize = 15f,
        multiDayControlsHeight = 56.dp,
        multiDayControlsTopOffset = if (showCalendarWeeks) (-6).dp else (-4).dp,
        showAllDaySection = false,
    )
    else -> TimelineHeaderPresentation(
        mode = TimelineDayHeaderMode.Landscape,
        height = 44.dp + if (showCalendarWeeks) 26.dp else 0.dp,
        weekLabelHeight = if (showCalendarWeeks) 26.dp else 0.dp,
        weekLabelTopOffset = if (showCalendarWeeks) (-10).dp else 0.dp,
        allowTopOverflow = showCalendarWeeks,
        weekLabelFontSize = 16f,
        multiDayControlsHeight = 56.dp,
        multiDayControlsTopOffset = if (showCalendarWeeks) 14.dp else (-4).dp,
        showAllDaySection = true,
    )
}
