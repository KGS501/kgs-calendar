package com.kgs.calendar.ui.platform

import androidx.compose.runtime.Composable
import com.kgs.calendar.domain.model.CalendarViewMode

/** What the calendar's keyboard shortcuts do. */
internal class CalendarShortcuts(
    val newEvent: () -> Unit,
    val newTask: () -> Unit,
    val search: () -> Unit,
    val tasks: () -> Unit,
    val menu: () -> Unit,
    val settings: () -> Unit,
    val today: () -> Unit,
    /** Moves the shown range back (-1) or forward (+1) by one page of the current view. */
    val step: (Int) -> Unit,
    val selectView: (CalendarViewMode) -> Unit,
    /** The desktop's year view. */
    val yearView: () -> Unit,
    /** Shows one day more (+1) or fewer (-1) in the multi-day view. */
    val changeDayCount: (Int) -> Unit,
    val refresh: () -> Unit,
)

/**
 * Makes [shortcuts] available while [enabled], i.e. while no sheet, drawer or page covers the
 * calendar. Desktop keyboards use them; on Android this does nothing.
 */
@Composable
internal expect fun ProvideCalendarShortcuts(shortcuts: CalendarShortcuts, enabled: Boolean)
