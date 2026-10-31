package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.R
import com.kgs.calendar.ui.labels.RecurrenceOption

private val RecurrenceUnits = listOf(RecurrenceOption.Daily, RecurrenceOption.Weekly, RecurrenceOption.Monthly, RecurrenceOption.Yearly)

/** The default number of times for a repeat that ends after a count. */
private const val DefaultRepeatCount = 10

/**
 * The repeat a popup for an item without one starts with: every week, never ending. Saving it
 * sets the repeat; Cancel keeps the item from repeating.
 */
internal fun initialRecurrenceDraft(recurrenceRule: String): RecurrenceDraft =
    RecurrenceDraft.fromRule(recurrenceRule).takeIf { it.option != RecurrenceOption.Once }
        ?: RecurrenceDraft(option = RecurrenceOption.Weekly)

/**
 * The repeat popup: how often (a number and a unit) and when the repeat ends. Numbers change with
 * minus and plus, or by tapping them to type one on a number keyboard. An existing repeat can be
 * removed with the red button, apart from Cancel and Save.
 */
@Composable
internal fun RecurrenceDialog(
    recurrenceRule: String,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var draft by remember(recurrenceRule) { mutableStateOf(initialRecurrenceDraft(recurrenceRule)) }
    ModalEditorDialog(title = appString(R.string.recurrence), onDismiss = onDismiss) {
        Column(
            modifier = Modifier.animateContentSize(tween(MotionMedium, easing = MotionStandard)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecurrenceSectionLabel(stringResource(R.string.every))
            RecurrenceNumberRow(
                value = draft.interval,
                unitLabel = draft.option.intervalUnitLabel(draft.interval),
                range = 1..99,
                onValueChange = { draft = draft.copy(interval = it) },
                tag = "interval",
            )
            DialogSegmentedControl(
                options = RecurrenceUnits,
                selected = draft.option,
                label = { it.intervalUnitLabel(draft.interval) },
                onSelected = { draft = draft.withUnit(it) },
            )
            // A little more room before the second question.
            Spacer(Modifier.height(2.dp))
            RecurrenceSectionLabel(stringResource(R.string.ends))
            DialogSegmentedControl(
                options = RecurrenceEndMode.entries,
                selected = draft.endMode,
                label = { mode ->
                    when (mode) {
                        RecurrenceEndMode.Never -> stringResource(R.string.never)
                        RecurrenceEndMode.Count -> stringResource(R.string.repeat_ends_after)
                        RecurrenceEndMode.Until -> stringResource(R.string.repeat_ends_on)
                    }
                },
                onSelected = { mode ->
                    draft = draft.copy(
                        endMode = mode,
                        count = if (mode == RecurrenceEndMode.Count) draft.count ?: DefaultRepeatCount else draft.count,
                    )
                },
            )
            // "Never" needs no input; the other ends unfold theirs below.
            AnimatedContent(
                targetState = draft.endMode,
                transitionSpec = {
                    (fadeIn(tween(MotionMedium, delayMillis = MotionShort / 2, easing = MotionStandard)) + expandVertically(tween(MotionMedium, easing = MotionEmphasized)))
                        .togetherWith(fadeOut(tween(MotionShort, easing = MotionStandardAccelerate)) + shrinkVertically(tween(MotionMedium, easing = MotionStandardAccelerate)))
                        .using(SizeTransform(clip = true) { _, _ -> tween(MotionMedium, easing = MotionEmphasized) })
                },
                label = "repeatEnd",
            ) { endMode ->
                when (endMode) {
                    RecurrenceEndMode.Never -> Spacer(Modifier.fillMaxWidth())
                    RecurrenceEndMode.Count -> {
                        val count = draft.count ?: DefaultRepeatCount
                        RecurrenceNumberRow(
                            value = count,
                            unitLabel = pluralStringResource(R.plurals.repeat_times, count),
                            range = 1..999,
                            onValueChange = { draft = draft.copy(count = it) },
                            tag = "count",
                        )
                    }
                    RecurrenceEndMode.Until -> DatePickerField(
                        value = draft.untilDate.orEmpty(),
                        onValueChange = { value -> draft = draft.copy(untilDate = value.take(10)) },
                        label = stringResource(R.string.end_date),
                        showLabel = true,
                        containerColor = editorSectionColor(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Removing is a different kind of action than setting the repeat, so it stands apart on the left.
            if (recurrenceRule.isNotBlank()) {
                PopupRemoveButton(
                    label = stringResource(R.string.remove_repeat),
                    icon = Icons.Default.EventBusy,
                    onClick = { onSave(null) },
                    modifier = Modifier.testTag("repeat-remove"),
                )
            }
            Spacer(Modifier.weight(1f))
            DialogDismissButton(appString(R.string.cancel), onClick = onDismiss)
            DialogConfirmButton(
                appString(R.string.save),
                onClick = { onSave(draft.toRule()) },
                // An end date has to be picked before a repeat can end on it.
                enabled = draft.endMode != RecurrenceEndMode.Until || !draft.untilDate.isNullOrBlank(),
            )
        }
    }
}

@Composable
private fun RecurrenceSectionLabel(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
}

/** A number with its unit and, on the right, minus and plus. Tapping the number types one. */
@Composable
private fun RecurrenceNumberRow(value: Int, unitLabel: String, range: IntRange, onValueChange: (Int) -> Unit, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(editorSectionColor())
            .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TypeableAmount(value = value, range = range, onValueChange = onValueChange, tag = tag)
        Text(unitLabel, color = WarmInk, fontSize = 16.sp, lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        ValueStepper(value = value, range = range, onValueChange = onValueChange, tagPrefix = "repeat", tagSuffix = tag)
    }
}

/**
 * A number that rolls when minus and plus change it, and turns into a number field when tapped.
 * Typed digits apply at once when they are in [range]; leaving the field restores a valid number.
 */
@Composable
internal fun TypeableAmount(value: Int, range: IntRange, onValueChange: (Int) -> Unit, tag: String, fontSizeSp: Int = 24) {
    var editing by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (editing) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 6.dp)
            .testTag("repeat-amount-$tag"),
        contentAlignment = Alignment.Center,
    ) {
        if (editing) {
            val focusRequester = remember { FocusRequester() }
            var field by remember { mutableStateOf(TextFieldValue(value.toString(), TextRange(0, value.toString().length))) }
            var focused by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            val digits = range.last.toString().length
            BasicTextField(
                value = field,
                onValueChange = { changed ->
                    val text = changed.text.filter(Char::isDigit).take(digits)
                    field = changed.copy(text = text, selection = TextRange(changed.selection.end.coerceAtMost(text.length)))
                    text.toIntOrNull()?.takeIf { it in range }?.let(onValueChange)
                },
                singleLine = true,
                textStyle = TextStyle(color = WarmBrown, fontSize = fontSizeSp.sp, lineHeight = (fontSizeSp + 4).sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(WarmBrown),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { editing = false }),
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .widthIn(min = 18.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (focused && !state.isFocused) editing = false
                        focused = state.isFocused
                    },
            )
        } else {
            Box(
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.type_number),
                    onClick = { editing = true },
                ),
            ) {
                ReminderAmountText(amount = value, color = WarmBrown, fontSizeSp = fontSizeSp)
            }
        }
    }
}

@Composable
private fun RecurrenceOption.intervalUnitLabel(count: Int): String = when (this) {
    RecurrenceOption.Daily -> pluralStringResource(R.plurals.repeat_unit_days, count)
    RecurrenceOption.Weekly -> pluralStringResource(R.plurals.repeat_unit_weeks, count)
    RecurrenceOption.Monthly -> pluralStringResource(R.plurals.repeat_unit_months, count)
    RecurrenceOption.Yearly -> pluralStringResource(R.plurals.repeat_unit_years, count)
    else -> localizedIntervalUnitLabel()
}
