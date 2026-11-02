package com.kgs.calendar.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.kgs.calendar.domain.model.CalendarViewMode

/**
 * The window's keyboard shortcuts. The window passes key presses that no focused field used;
 * they act only while the calendar itself is shown.
 *
 * - Ctrl+N new event, Ctrl+Shift+N new task, Ctrl+F search, Ctrl+, settings, F5 refresh
 * - T today, Page Up / Page Down previous / next range
 * - ← / → a day in the multi-day view (a page elsewhere), ↑ / ↓ a row in the month and year views
 * - D day, W multi-day, M month, Y (or J) year, A agenda; + / − one day more or fewer
 * - Ctrl+Shift+T tasks, Ctrl+M menu
 */
class DesktopShortcuts {
    internal var shortcuts: CalendarShortcuts? = null
    internal var enabled: Boolean = false

    fun handle(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val actions = shortcuts?.takeIf { enabled } ?: return false
        val ctrl = event.isCtrlPressed
        val shift = event.isShiftPressed
        if (event.isAltPressed) return false
        val action: (() -> Unit)? = when {
            ctrl && shift && event.key == Key.N -> actions.newTask
            ctrl && shift && event.key == Key.T -> actions.tasks
            ctrl && event.key == Key.N -> actions.newEvent
            ctrl && event.key == Key.F -> actions.search
            ctrl && event.key == Key.Comma -> actions.settings
            ctrl && event.key == Key.M -> actions.menu
            ctrl -> null
            event.key == Key.F5 -> actions.refresh
            event.key == Key.T -> actions.today
            event.key == Key.PageUp -> { { actions.step(-1) } }
            event.key == Key.PageDown -> { { actions.step(1) } }
            event.key == Key.DirectionLeft -> { { actions.nudge(-1) } }
            event.key == Key.DirectionRight -> { { actions.nudge(1) } }
            event.key == Key.DirectionUp -> { { actions.nudgeVertical(-1) } }
            event.key == Key.DirectionDown -> { { actions.nudgeVertical(1) } }
            event.key == Key.D -> { { actions.selectView(CalendarViewMode.Day) } }
            event.key == Key.W -> { { actions.selectView(CalendarViewMode.ThreeDay) } }
            event.key == Key.M -> { { actions.selectView(CalendarViewMode.Month) } }
            event.key == Key.A -> { { actions.selectView(CalendarViewMode.Agenda) } }
            event.key == Key.Y || event.key == Key.J -> actions.yearView
            event.key == Key.Plus || event.key == Key.NumPadAdd || (shift && event.key == Key.Equals) -> { { actions.changeDayCount(1) } }
            event.key == Key.Minus || event.key == Key.NumPadSubtract -> { { actions.changeDayCount(-1) } }
            else -> null
        }
        action?.invoke() ?: return false
        return true
    }
}

val LocalDesktopShortcuts = staticCompositionLocalOf<DesktopShortcuts?> { null }

@Composable
internal actual fun ProvideCalendarShortcuts(shortcuts: CalendarShortcuts, enabled: Boolean) {
    val holder = LocalDesktopShortcuts.current ?: return
    SideEffect {
        holder.shortcuts = shortcuts
        holder.enabled = enabled
    }
    DisposableEffect(holder) {
        onDispose { holder.shortcuts = null }
    }
}
