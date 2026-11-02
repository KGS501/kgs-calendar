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
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import com.kgs.calendar.domain.model.CalendarViewMode

/** The shortcuts' keys before any are changed in the settings. */
val DefaultDesktopShortcuts: Map<ShortcutAction, KeyBinding> = mapOf(
    ShortcutAction.NewEvent to KeyBinding(Key.N.keyCode, ctrl = true),
    ShortcutAction.NewTask to KeyBinding(Key.N.keyCode, ctrl = true, shift = true),
    ShortcutAction.Search to KeyBinding(Key.F.keyCode, ctrl = true),
    ShortcutAction.Today to KeyBinding(Key.T.keyCode),
    ShortcutAction.Previous to KeyBinding(Key.PageUp.keyCode),
    ShortcutAction.Next to KeyBinding(Key.PageDown.keyCode),
    ShortcutAction.DayView to KeyBinding(Key.D.keyCode),
    ShortcutAction.MultiDayView to KeyBinding(Key.W.keyCode),
    ShortcutAction.MonthView to KeyBinding(Key.M.keyCode),
    ShortcutAction.YearView to KeyBinding(Key.Y.keyCode),
    ShortcutAction.AgendaView to KeyBinding(Key.A.keyCode),
    ShortcutAction.MoreDays to KeyBinding(Key.Plus.keyCode),
    ShortcutAction.FewerDays to KeyBinding(Key.Minus.keyCode),
    ShortcutAction.Tasks to KeyBinding(Key.T.keyCode, ctrl = true, shift = true),
    ShortcutAction.Refresh to KeyBinding(Key.F5.keyCode),
    ShortcutAction.Settings to KeyBinding(Key.Comma.keyCode, ctrl = true),
)

private val ModifierKeys = setOf(
    Key.CtrlLeft, Key.CtrlRight, Key.ShiftLeft, Key.ShiftRight, Key.AltLeft, Key.AltRight,
    Key.MetaLeft, Key.MetaRight, Key.CapsLock,
)

/** A key as Windows names it, e.g. "Ctrl+Shift+N" or "Bild ab". */
fun desktopShortcutLabel(binding: KeyBinding): String = buildList {
    if (binding.ctrl) add(java.awt.event.KeyEvent.getKeyText(java.awt.event.KeyEvent.VK_CONTROL))
    if (binding.alt) add(java.awt.event.KeyEvent.getKeyText(java.awt.event.KeyEvent.VK_ALT))
    if (binding.shift) add(java.awt.event.KeyEvent.getKeyText(java.awt.event.KeyEvent.VK_SHIFT))
    val key = Key(binding.keyCode)
    add(
        when (key) {
            Key.Plus -> "+"
            Key.Minus -> "−"
            Key.Comma -> ","
            else -> java.awt.event.KeyEvent.getKeyText(key.nativeKeyCode)
        },
    )
}.joinToString("+")

fun isDesktopModifierKey(binding: KeyBinding): Boolean = Key(binding.keyCode) in ModifierKeys

/**
 * The window's keyboard shortcuts. The window passes key presses that no focused field used;
 * they act only while the calendar itself is shown. The keys of the main actions come from
 * [bindings] and can be changed in the settings; the arrows are fixed:
 * ← / → a day in the multi-day view (a range elsewhere), ↑ / ↓ a row in the month and year views.
 */
class DesktopShortcuts(val bindings: ShortcutBindings) {
    internal var shortcuts: CalendarShortcuts? = null
    internal var enabled: Boolean = false

    fun handle(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val actions = shortcuts?.takeIf { enabled } ?: return false
        val plain = !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed
        val fixed: (() -> Unit)? = when {
            plain && event.key == Key.DirectionLeft -> { { actions.nudge(-1) } }
            plain && event.key == Key.DirectionRight -> { { actions.nudge(1) } }
            plain && event.key == Key.DirectionUp -> { { actions.nudgeVertical(-1) } }
            plain && event.key == Key.DirectionDown -> { { actions.nudgeVertical(1) } }
            else -> null
        }
        val action = fixed ?: bindings.actionFor(KeyBinding.of(event))?.let { bound -> actionOf(bound, actions) }
            // The number pad's plus and minus (and Shift+= for plus) as well, while they are the defaults.
            ?: when {
                event.key == Key.NumPadAdd || (event.isShiftPressed && event.key == Key.Equals) ->
                    bindings.bindings[ShortcutAction.MoreDays]?.takeIf { it == DefaultDesktopShortcuts[ShortcutAction.MoreDays] }
                        ?.let { { actions.changeDayCount(1) } }
                event.key == Key.NumPadSubtract ->
                    bindings.bindings[ShortcutAction.FewerDays]?.takeIf { it == DefaultDesktopShortcuts[ShortcutAction.FewerDays] }
                        ?.let { { actions.changeDayCount(-1) } }
                else -> null
            }
            ?: return false
        action()
        return true
    }

    private fun actionOf(action: ShortcutAction, actions: CalendarShortcuts): () -> Unit = when (action) {
        ShortcutAction.NewEvent -> actions.newEvent
        ShortcutAction.NewTask -> actions.newTask
        ShortcutAction.Search -> actions.search
        ShortcutAction.Today -> actions.today
        ShortcutAction.Previous -> { { actions.step(-1) } }
        ShortcutAction.Next -> { { actions.step(1) } }
        ShortcutAction.DayView -> { { actions.selectView(CalendarViewMode.Day) } }
        ShortcutAction.MultiDayView -> { { actions.selectView(CalendarViewMode.ThreeDay) } }
        ShortcutAction.MonthView -> { { actions.selectView(CalendarViewMode.Month) } }
        ShortcutAction.YearView -> actions.yearView
        ShortcutAction.AgendaView -> { { actions.selectView(CalendarViewMode.Agenda) } }
        ShortcutAction.MoreDays -> { { actions.changeDayCount(1) } }
        ShortcutAction.FewerDays -> { { actions.changeDayCount(-1) } }
        ShortcutAction.Tasks -> actions.tasks
        ShortcutAction.Refresh -> actions.refresh
        ShortcutAction.Settings -> actions.settings
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
