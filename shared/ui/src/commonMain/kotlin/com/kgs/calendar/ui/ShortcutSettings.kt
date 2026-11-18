package com.kgs.calendar.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.ui.res.stringResource
import com.kgs.calendar.ui.platform.KeyBinding
import com.kgs.calendar.ui.platform.ShortcutAction
import com.kgs.calendar.ui.platform.ShortcutBindings
import com.kgs.calendar.ui.res.Res

/**
 * The keyboard shortcuts page (the desktop): each action with its keys. A click on an action
 * waits for the new keys; Backspace removes the shortcut and Escape keeps the old one.
 */
@Composable
internal fun KeyboardShortcutsSettings(bindings: ShortcutBindings) {
    var capturing by remember { mutableStateOf<ShortcutAction?>(null) }
    SettingsGroup(footer = stringResource(Res.string.shortcuts_help)) {
        ShortcutAction.entries.forEach { action ->
            SettingsRow(
                title = action.label(),
                role = Role.Button,
                onClick = { capturing = if (capturing == action) null else action },
                trailing = {
                    ShortcutKeys(
                        binding = bindings.bindings[action],
                        label = bindings.label,
                        capturing = capturing == action,
                        onCaptured = { binding ->
                            capturing = null
                            when {
                                binding == null -> bindings.clear(action)
                                else -> bindings.assign(action, binding)
                            }
                        },
                        onCancel = { if (capturing == action) capturing = null },
                        isModifierOnly = bindings.isModifierOnly,
                    )
                },
            )
        }
    }
    SettingsGroup {
        SettingsRow(
            title = stringResource(Res.string.shortcuts_reset),
            icon = Icons.Default.RestartAlt,
            enabled = !bindings.isDefault,
            onClick = {
                capturing = null
                bindings.resetToDefaults()
            },
        )
    }
}

/** An action's keys as a key cap, or the prompt for new keys while they are being set. */
@Composable
private fun ShortcutKeys(
    binding: KeyBinding?,
    label: (KeyBinding) -> String,
    capturing: Boolean,
    onCaptured: (KeyBinding?) -> Unit,
    onCancel: () -> Unit,
    isModifierOnly: (KeyBinding) -> Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(capturing) {
        if (capturing) focusRequester.requestFocus()
    }
    val border by animateColorAsState(
        targetValue = if (capturing) WarmBrown else WarmLine.copy(alpha = 0.9f),
        animationSpec = tween(MotionShort),
        label = "shortcutKeyBorder",
    )
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                // Clicking elsewhere while waiting for keys keeps the old ones.
                if (focused && !state.isFocused) onCancel()
                focused = state.isFocused
            }
            .focusable(enabled = capturing)
            .onPreviewKeyEvent { event ->
                if (!capturing) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                val plain = !event.isCtrlPressed && !event.isShiftPressed && !event.isAltPressed
                when {
                    plain && event.key == Key.Escape -> onCancel()
                    plain && (event.key == Key.Backspace || event.key == Key.Delete) -> onCaptured(null)
                    else -> {
                        val pressed = KeyBinding.of(event)
                        // Ctrl, Shift and Alt wait for the key they go with.
                        if (!isModifierOnly(pressed)) onCaptured(pressed)
                    }
                }
                true
            }
            .clip(shape)
            .background(if (capturing) WarmBrown.copy(alpha = 0.12f) else MaterialTheme.colorScheme.background)
            .border(1.dp, border, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            when {
                capturing -> stringResource(Res.string.shortcut_press_keys)
                binding == null -> stringResource(Res.string.shortcut_none)
                else -> label(binding)
            },
            color = when {
                capturing -> WarmBrown
                binding == null -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> WarmInk
            },
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShortcutAction.label(): String = stringResource(
    when (this) {
        ShortcutAction.NewEvent -> Res.string.shortcut_new_event
        ShortcutAction.NewTask -> Res.string.shortcut_new_task
        ShortcutAction.Search -> Res.string.shortcut_search
        ShortcutAction.Today -> Res.string.shortcut_today
        ShortcutAction.Previous -> Res.string.shortcut_previous
        ShortcutAction.Next -> Res.string.shortcut_next
        ShortcutAction.DayView -> Res.string.shortcut_day_view
        ShortcutAction.MultiDayView -> Res.string.shortcut_multi_day_view
        ShortcutAction.MonthView -> Res.string.shortcut_month_view
        ShortcutAction.YearView -> Res.string.shortcut_year_view
        ShortcutAction.AgendaView -> Res.string.shortcut_agenda_view
        ShortcutAction.MoreDays -> Res.string.shortcut_more_days
        ShortcutAction.FewerDays -> Res.string.shortcut_fewer_days
        ShortcutAction.Tasks -> Res.string.shortcut_tasks
        ShortcutAction.Refresh -> Res.string.shortcut_refresh
        ShortcutAction.Settings -> Res.string.shortcut_settings
    },
)
