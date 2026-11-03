package com.kgs.calendar.ui

import com.kgs.calendar.ui.res.StringResource
import com.kgs.calendar.ui.res.Res
import com.kgs.calendar.domain.model.CalendarViewMode

fun CalendarViewMode.labelRes(weekViewEnabled: Boolean): com.kgs.calendar.ui.res.StringResource = when (this) {
    CalendarViewMode.ThreeDay -> if (weekViewEnabled) Res.string.week else Res.string.three_days
    CalendarViewMode.Day -> Res.string.day
    CalendarViewMode.Month -> Res.string.month
    CalendarViewMode.Agenda -> Res.string.agenda
    CalendarViewMode.Tasks -> Res.string.tasks
}

data class TimelineSettingsVisibility(
    val showFullWeekSwipe: Boolean,
    val showMultiDayControls: Boolean,
    val showMultiDayCount: Boolean,
)

fun timelineSettingsVisibility(
    weekViewEnabled: Boolean,
    sidebarControlsEnabled: Boolean,
): TimelineSettingsVisibility = TimelineSettingsVisibility(
    showFullWeekSwipe = weekViewEnabled,
    showMultiDayControls = !weekViewEnabled,
    showMultiDayCount = !weekViewEnabled && !sidebarControlsEnabled,
)
