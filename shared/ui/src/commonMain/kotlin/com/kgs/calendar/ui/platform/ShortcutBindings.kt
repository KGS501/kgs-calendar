package com.kgs.calendar.ui.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key

/** The calendar's actions that a key can be set for, in the order the settings list them. */
enum class ShortcutAction {
    NewEvent,
    NewTask,
    Search,
    Today,
    Previous,
    Next,
    DayView,
    MultiDayView,
    MonthView,
    YearView,
    AgendaView,
    MoreDays,
    FewerDays,
    Tasks,
    Refresh,
    Settings,
}

/** A key with the modifiers held with it, e.g. Ctrl+N. [keyCode] is a Compose key code. */
data class KeyBinding(
    val keyCode: Long,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
) {
    companion object {
        fun of(event: KeyEvent): KeyBinding =
            KeyBinding(event.key.keyCode, ctrl = event.isCtrlPressed, shift = event.isShiftPressed, alt = event.isAltPressed)
    }
}

/**
 * The keys set for the shortcuts: the platform's defaults, changed in the settings. A key belongs
 * to one action at a time, so setting it for one takes it from another.
 */
class ShortcutBindings(
    val defaults: Map<ShortcutAction, KeyBinding>,
    initial: Map<ShortcutAction, KeyBinding?> = emptyMap(),
    /** How a key reads on this platform, e.g. "Ctrl+Shift+N". */
    val label: (KeyBinding) -> String,
    /** Whether [KeyBinding] is only a modifier (Ctrl, Shift, …), which can't be a shortcut alone. */
    val isModifierOnly: (KeyBinding) -> Boolean,
    private val onChanged: (Map<ShortcutAction, KeyBinding?>) -> Unit = {},
) {
    var bindings: Map<ShortcutAction, KeyBinding?> by mutableStateOf(ShortcutAction.entries.associateWith { action ->
        if (action in initial) initial[action] else defaults[action]
    })
        private set

    fun actionFor(binding: KeyBinding): ShortcutAction? = bindings.entries.firstOrNull { it.value == binding }?.key

    fun assign(action: ShortcutAction, binding: KeyBinding) {
        bindings = bindings.mapValues { (other, current) -> if (other != action && current == binding) null else current } + (action to binding)
        onChanged(bindings)
    }

    fun clear(action: ShortcutAction) {
        bindings = bindings + (action to null)
        onChanged(bindings)
    }

    fun resetToDefaults() {
        bindings = ShortcutAction.entries.associateWith { defaults[it] }
        onChanged(bindings)
    }

    val isDefault: Boolean get() = ShortcutAction.entries.all { bindings[it] == defaults[it] }
}

/** The settable shortcuts where the platform has a keyboard to use them (the desktop); null elsewhere. */
val LocalShortcutBindings = staticCompositionLocalOf<ShortcutBindings?> { null }
