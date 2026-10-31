package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.R
import com.kgs.calendar.data.settings.ReminderPresets
import com.kgs.calendar.domain.model.MAX_REMINDER_MINUTES
import com.kgs.calendar.domain.model.REMINDER_AT_END
import com.kgs.calendar.domain.model.REMINDER_AT_START
import com.kgs.calendar.ui.haptics.LocalKgsHaptics
import com.kgs.calendar.ui.labels.ReminderUnit

/** The reminders the reminder popup opens with; the app provides the user's choice. */
internal val LocalReminderPresets = compositionLocalOf { ReminderPresets() }

/** Long presses step minutes by this much: to the next multiple of five. */
private const val MinuteJump = 5

/** The time between the steps of a held minus or plus button: slow enough to let go at the right one. */
private const val HeldStepMillis = 420L

/** The units of the reminder popup's cards, in the order they are shown. */
private val ReminderCardUnits = listOf(ReminderUnit.Minutes, ReminderUnit.Hours, ReminderUnit.Days, ReminderUnit.Weeks)

internal fun ReminderUnit.amountRange(): IntRange = when (this) {
    ReminderUnit.Minutes -> ReminderPresets.MINUTES_RANGE
    ReminderUnit.Hours -> ReminderPresets.HOURS_RANGE
    ReminderUnit.Days -> ReminderPresets.DAYS_RANGE
    ReminderUnit.Weeks -> ReminderPresets.WEEKS_RANGE
}

internal fun ReminderPresets.amountOf(unit: ReminderUnit): Int = when (unit) {
    ReminderUnit.Minutes -> minutes
    ReminderUnit.Hours -> hours
    ReminderUnit.Days -> days
    ReminderUnit.Weeks -> weeks
}

internal fun ReminderPresets.withAmount(unit: ReminderUnit, amount: Int): ReminderPresets = when (unit) {
    ReminderUnit.Minutes -> copy(minutes = amount)
    ReminderUnit.Hours -> copy(hours = amount)
    ReminderUnit.Days -> copy(days = amount)
    ReminderUnit.Weeks -> copy(weeks = amount)
}.coerced()

/**
 * The amount after one step of [unit]'s minus ([up] false) or plus button. A tap steps by one; a
 * held minutes button jumps to the previous or next multiple of five. Amounts stay in range.
 */
internal fun steppedReminderAmount(unit: ReminderUnit, amount: Int, up: Boolean, held: Boolean): Int {
    val next = when {
        held && unit == ReminderUnit.Minutes ->
            if (up) (amount / MinuteJump + 1) * MinuteJump else ((amount - 1) / MinuteJump) * MinuteJump
        up -> amount + 1
        else -> amount - 1
    }
    return next.coerceIn(unit.amountRange())
}

/** What the reminder popup has picked: a moment of the item, or an amount of a unit before it. */
internal sealed interface ReminderPick {
    data class Moment(val minutes: Int) : ReminderPick
    data class Before(val unit: ReminderUnit) : ReminderPick
}

/**
 * Where the popup starts for a reminder [initialMinutes] minutes before (null for a new one): the
 * pick and the cards' amounts. An existing reminder shows on the card of its largest whole unit,
 * the other cards keep the presets. A new reminder starts on the minutes card.
 */
internal fun initialReminderState(initialMinutes: Int?, presets: ReminderPresets): Pair<ReminderPick, ReminderPresets> {
    if (initialMinutes == REMINDER_AT_START || initialMinutes == REMINDER_AT_END) {
        return ReminderPick.Moment(initialMinutes) to presets
    }
    if (initialMinutes == null || initialMinutes < 0) return ReminderPick.Before(ReminderUnit.Minutes) to presets
    // The largest unit that holds the reminder as a whole amount within its card's range.
    val unit = ReminderCardUnits.reversed().firstOrNull { candidate ->
        initialMinutes % candidate.minutes == 0 && initialMinutes / candidate.minutes in candidate.amountRange()
    }
    // An amount no card can hold (such as 3 h 20 min) starts on the minutes card; saving it
    // unchanged keeps its exact minutes.
        ?: return ReminderPick.Before(ReminderUnit.Minutes) to presets
    return ReminderPick.Before(unit) to presets.withAmount(unit, initialMinutes / unit.minutes)
}

/**
 * The reminder popup: four cards with an amount of minutes, hours, days and weeks before, each with
 * minus and plus buttons so no keyboard is needed, two moments (at start, at end), and, apart from
 * them, removing the reminder. The cards open with the user's presets.
 */
@Composable
internal fun ReminderDialog(initialMinutes: Int?, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    val presets = LocalReminderPresets.current
    val initial = remember(initialMinutes, presets) { initialReminderState(initialMinutes, presets) }
    var pick by remember(initialMinutes) { mutableStateOf(initial.first) }
    var minutesAmount by rememberSaveable(initialMinutes) { mutableIntStateOf(initial.second.minutes) }
    var hoursAmount by rememberSaveable(initialMinutes) { mutableIntStateOf(initial.second.hours) }
    var daysAmount by rememberSaveable(initialMinutes) { mutableIntStateOf(initial.second.days) }
    var weeksAmount by rememberSaveable(initialMinutes) { mutableIntStateOf(initial.second.weeks) }
    // An existing reminder no card can show exactly is kept as it is unless the user changes the pick.
    var keepExact by remember(initialMinutes) {
        mutableStateOf(initialMinutes?.takeIf { it > 0 && initial.first == ReminderPick.Before(ReminderUnit.Minutes) && it != initial.second.minutes })
    }
    fun amountOf(unit: ReminderUnit): Int = when (unit) {
        ReminderUnit.Minutes -> minutesAmount
        ReminderUnit.Hours -> hoursAmount
        ReminderUnit.Days -> daysAmount
        ReminderUnit.Weeks -> weeksAmount
    }
    fun setAmount(unit: ReminderUnit, amount: Int) {
        keepExact = null
        when (unit) {
            ReminderUnit.Minutes -> minutesAmount = amount
            ReminderUnit.Hours -> hoursAmount = amount
            ReminderUnit.Days -> daysAmount = amount
            ReminderUnit.Weeks -> weeksAmount = amount
        }
    }
    ModalEditorDialog(
        title = appString(if (initialMinutes == null) R.string.add_reminder else R.string.edit_reminder),
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ReminderCardUnits.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { unit ->
                        ReminderAmountCard(
                            unit = unit,
                            amount = amountOf(unit),
                            selected = pick == ReminderPick.Before(unit),
                            onSelect = {
                                if (pick != ReminderPick.Before(unit)) keepExact = null
                                pick = ReminderPick.Before(unit)
                            },
                            onAmountChange = { amount ->
                                pick = ReminderPick.Before(unit)
                                setAmount(unit, amount)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(REMINDER_AT_START to R.string.at_start, REMINDER_AT_END to R.string.at_end).forEach { (minutes, label) ->
                    ReminderMomentPill(
                        label = stringResource(label),
                        selected = pick == ReminderPick.Moment(minutes),
                        onClick = {
                            keepExact = null
                            pick = ReminderPick.Moment(minutes)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reminder-moment-$minutes"),
                    )
                }
            }
        }
        HorizontalDivider(color = WarmLine.copy(alpha = 0.7f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Removing is a different kind of action than picking a time, so it stands apart on the left.
            if (initialMinutes != null) {
                PopupRemoveButton(
                    label = stringResource(R.string.remove_reminder),
                    icon = Icons.Default.NotificationsOff,
                    onClick = { onSave(null) },
                    modifier = Modifier.testTag("reminder-remove"),
                )
            }
            Spacer(Modifier.weight(1f))
            DialogDismissButton(appString(R.string.cancel), onClick = onDismiss)
            DialogConfirmButton(
                appString(R.string.save),
                onClick = {
                    val chosen = when (val current = pick) {
                        is ReminderPick.Moment -> current.minutes
                        is ReminderPick.Before -> keepExact ?: (amountOf(current.unit) * current.unit.minutes)
                    }
                    onSave(if (chosen > 0) chosen.coerceAtMost(MAX_REMINDER_MINUTES) else chosen)
                },
            )
        }
    }
}

@Composable
private fun ReminderAmountCard(
    unit: ReminderUnit,
    amount: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    onAmountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The picked card is filled with the accent; its text and buttons turn light to stay readable.
    val container by animateColorAsState(
        targetValue = if (selected) WarmBrown else editorSectionColor(),
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "reminderCardContainer",
    )
    val onAccent = accentContainerContentColor()
    val amountColor by animateColorAsState(
        targetValue = if (selected) onAccent else WarmInk,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "reminderCardAmount",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) onAccent.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "reminderCardLabel",
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .testTag("reminder-card-${unit.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ReminderAmountText(amount = amount, color = amountColor)
        Text(
            "${reminderUnitLabel(unit, amount)} ${stringResource(R.string.reminder_before)}",
            color = labelColor,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(6.dp))
        ReminderStepper(unit = unit, amount = amount, onAmountChange = onAmountChange, onAccent = selected)
    }
}

/** The amount, rolling up or down as it grows or shrinks. */
@Composable
internal fun ReminderAmountText(amount: Int, color: Color, fontSizeSp: Int = 28) {
    AnimatedContent(
        targetState = amount,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(tween(MotionShort, easing = MotionStandard)) { if (up) it / 2 else -it / 2 } + fadeIn(tween(MotionShort))) togetherWith
                (slideOutVertically(tween(MotionShort, easing = MotionStandardAccelerate)) { if (up) -it / 2 else it / 2 } + fadeOut(tween(MotionShort)))
        },
        label = "reminderAmount",
    ) { value ->
        Text(
            value.toString(),
            color = color,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp + 4).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Minus and plus for [unit]'s amount. A tap steps by one; holding a button keeps stepping, by five
 * minutes on the minutes card, with a short pause between steps so the user can let go in time.
 */
@Composable
internal fun ReminderStepper(
    unit: ReminderUnit,
    amount: Int,
    onAmountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onAccent: Boolean = false,
    buttonColor: Color? = null,
) {
    ValueStepper(
        onAccent = onAccent,
        buttonColor = buttonColor,
        value = amount,
        range = unit.amountRange(),
        onValueChange = onAmountChange,
        step = { value, up, held -> steppedReminderAmount(unit, value, up, held) },
        tagPrefix = "reminder",
        tagSuffix = unit.name,
        modifier = modifier,
    )
}

/**
 * A round minus and plus button for a number in [range], so that no keyboard is needed. A tap
 * steps once, holding a button keeps stepping; [step] decides how far.
 */
@Composable
internal fun ValueStepper(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: (value: Int, up: Boolean, held: Boolean) -> Int = { current, up, _ -> (if (up) current + 1 else current - 1).coerceIn(range) },
    tagPrefix: String = "value",
    tagSuffix: String = "",
    onAccent: Boolean = false,
    // The buttons' fill on a plain background; null uses the surface, which suits tinted cards.
    buttonColor: Color? = null,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(false, true).forEach { up ->
            ReminderStepButton(
                up = up,
                onAccent = onAccent,
                buttonColor = buttonColor,
                enabled = if (up) value < range.last else value > range.first,
                onStep = { held -> onValueChange(step(value, up, held)) },
                modifier = Modifier.testTag("$tagPrefix-${if (up) "plus" else "minus"}-$tagSuffix"),
            )
        }
    }
}

@Composable
private fun ReminderStepButton(up: Boolean, enabled: Boolean, onStep: (held: Boolean) -> Unit, modifier: Modifier = Modifier, onAccent: Boolean = false, buttonColor: Color? = null) {
    val haptics = LocalKgsHaptics.current
    val currentOnStep by rememberUpdatedState(onStep)
    val currentEnabled by rememberUpdatedState(enabled)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, tween(MotionShort, easing = MotionStandard), label = "reminderStepScale")
    val description = stringResource(if (up) R.string.reminder_more else R.string.reminder_less)
    // A plain round button on the card's own surface, with an ink sign: on the accent-filled card
    // it turns into a soft light circle with a light sign.
    val onAccentColor = accentContainerContentColor()
    val container by animateColorAsState(
        targetValue = when {
            onAccent -> onAccentColor.copy(alpha = if (enabled) 0.2f else 0.08f)
            else -> (buttonColor ?: MaterialTheme.colorScheme.surface).let { it.copy(alpha = it.alpha * if (enabled) 1f else 0.5f) }
        },
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "stepButtonContainer",
    )
    val tint by animateColorAsState(
        targetValue = when {
            onAccent -> onAccentColor.copy(alpha = if (enabled) 1f else 0.4f)
            else -> if (enabled) WarmInk else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        },
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "stepButtonTint",
    )
    fun step(held: Boolean) {
        if (!currentEnabled) return
        haptics.sliderStep()
        currentOnStep(held)
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(container)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { step(held = false); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    pressed = true
                    try {
                        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            ReleaseResult(waitForUpOrCancellation())
                        }
                        if (released != null) {
                            // Let go before a long press: one step, unless the press was cancelled.
                            if (released.up != null) step(held = false)
                            return@awaitEachGesture
                        }
                        while (true) {
                            step(held = true)
                            val ended = withTimeoutOrNull(HeldStepMillis) { ReleaseResult(waitForUpOrCancellation()) }
                            if (ended != null) break
                        }
                    } finally {
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (up) Icons.Default.Add else Icons.Default.Remove,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

private class ReleaseResult(val up: androidx.compose.ui.input.pointer.PointerInputChange?)

@Composable
private fun ReminderMomentPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val container by animateColorAsState(
        targetValue = if (selected) WarmBrown else editorSectionColor(),
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "reminderMomentContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) accentContainerContentColor() else WarmInk,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "reminderMomentContent",
    )
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(container)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = content, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The red "remove" action of the reminder and repeat popups, apart from Cancel and Save. */
@Composable
internal fun PopupRemoveButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val error = MaterialTheme.colorScheme.error
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(error.copy(alpha = 0.1f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = error, modifier = Modifier.size(18.dp))
        Text(label, color = error, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
internal fun reminderUnitLabel(unit: ReminderUnit, amount: Int): String = pluralStringResource(
    when (unit) {
        ReminderUnit.Minutes -> R.plurals.reminder_unit_minutes
        ReminderUnit.Hours -> R.plurals.reminder_unit_hours
        ReminderUnit.Days -> R.plurals.reminder_unit_days
        ReminderUnit.Weeks -> R.plurals.reminder_unit_weeks
    },
    amount,
)

/**
 * The settings row for the reminder presets: one amount per unit, changed with minus and plus.
 * Sits inside a settings group, which gives it its card.
 */
@Composable
internal fun ReminderPresetsEditor(presets: ReminderPresets, onPresetsChange: (ReminderPresets) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(settingsControlColor())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.reminder_presets), color = WarmInk, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
        if (!LocalProMode.current) Text(stringResource(R.string.reminder_presets_help), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 17.sp)
        ReminderCardUnits.forEach { unit ->
            val amount = presets.amountOf(unit)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reminder-preset-${unit.name}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderAmountText(amount = amount, color = WarmInk, fontSizeSp = 20)
                    Text(
                        "${reminderUnitLabel(unit, amount)} ${stringResource(R.string.reminder_before)}",
                        color = WarmInk,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ReminderStepper(
                    unit = unit,
                    amount = amount,
                    onAmountChange = { onPresetsChange(presets.withAmount(unit, it)) },
                    buttonColor = editorSectionColor(),
                )
            }
        }
    }
}
