package com.kgs.calendar.ui

import androidx.annotation.StringRes
import com.kgs.calendar.R
import com.kgs.calendar.domain.model.CalendarViewMode

@StringRes
internal fun CalendarViewMode.labelRes(weekViewEnabled: Boolean): Int = when (this) {
    CalendarViewMode.ThreeDay -> if (weekViewEnabled) R.string.week else R.string.three_days
    CalendarViewMode.Day -> R.string.day
    CalendarViewMode.Month -> R.string.month
    CalendarViewMode.Agenda -> R.string.agenda
    CalendarViewMode.Tasks -> R.string.tasks
}

internal data class TimelineSettingsVisibility(
    val showFullWeekSwipe: Boolean,
    val showMultiDayControls: Boolean,
    val showMultiDayCount: Boolean,
)

internal fun timelineSettingsVisibility(
    weekViewEnabled: Boolean,
    sidebarControlsEnabled: Boolean,
): TimelineSettingsVisibility = TimelineSettingsVisibility(
    showFullWeekSwipe = weekViewEnabled,
    showMultiDayControls = !weekViewEnabled,
    showMultiDayCount = !weekViewEnabled && !sidebarControlsEnabled,
)
