@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.kgs.calendar.ui.components.ClearableOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import java.time.format.DateTimeFormatter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kgs.calendar.R
import com.kgs.calendar.ui.editor.EditorScheduleState
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import java.time.Instant
import java.time.LocalDate
import android.text.format.DateFormat as AndroidDateFormat
import java.util.Locale
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.kgs.calendar.ui.theme.MaxAppFontScale

@Composable
internal fun ReadOnlyEditorNotice(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title.ifBlank { stringResource(R.string.no_title) },
            color = WarmInk,
            fontSize = 22.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.read_only_editor_notice),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
internal fun NoWritableSourceNotice(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .editorInset()
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            color = WarmInk,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
        TextButton(
            onClick = onAction,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp),
        ) {
            Text(
                action,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

@Composable
internal fun InvalidTimeRangeDialog(
    onDismiss: () -> Unit,
    @StringRes message: Int = R.string.invalid_time_range_message,
) {
    KgsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appString(R.string.invalid_time_range_title)) },
        text = { Text(appString(message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(appString(R.string.ok))
            }
        },
        containerColor = popupSurfaceColor(),
    )
}

@Composable
internal fun EventScheduleEditor(
    schedule: EditorScheduleState,
    onScheduleChange: (EditorScheduleState) -> Unit,
    endIsError: Boolean = false,
) {
    Column(
        modifier = Modifier.animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EditorScheduleLine(label = stringResource(R.string.start)) {
            DatePickerField(
                value = schedule.startDateText,
                onValueChange = { onScheduleChange(schedule.copy(startDateText = it).recalculatePreview()) },
                label = stringResource(R.string.start_date),
                modifier = Modifier.weight(1f).testTag("editor-start-date"),
            )
            AnimatedVisibility(
                visible = !schedule.allDay,
                enter = fadeIn(animationSpec = tween(MotionMedium, easing = MotionStandard)) +
                    expandHorizontally(animationSpec = tween(MotionMedium, easing = MotionStandard), expandFrom = Alignment.Start),
                exit = fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) +
                    shrinkHorizontally(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate), shrinkTowards = Alignment.Start),
            ) {
                TimePickerField(
                    value = schedule.startTimeText,
                    onValueChange = { onScheduleChange(schedule.copy(startTimeText = it).recalculatePreview()) },
                    label = stringResource(R.string.start),
                    modifier = Modifier.width(editorTimeChipWidth()).testTag("editor-start-time"),
                )
            }
        }
        EditorScheduleLine(label = stringResource(R.string.end)) {
            DatePickerField(
                value = schedule.endDateText,
                onValueChange = { onScheduleChange(schedule.copy(endDateText = it).recalculatePreview()) },
                label = stringResource(R.string.end_date),
                isError = endIsError,
                modifier = Modifier.weight(1f).testTag("editor-end-date"),
            )
            AnimatedVisibility(
                visible = !schedule.allDay,
                enter = fadeIn(animationSpec = tween(MotionMedium, easing = MotionStandard)) +
                    expandHorizontally(animationSpec = tween(MotionMedium, easing = MotionStandard), expandFrom = Alignment.Start),
                exit = fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) +
                    shrinkHorizontally(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate), shrinkTowards = Alignment.Start),
            ) {
                TimePickerField(
                    value = schedule.endTimeText,
                    onValueChange = { onScheduleChange(schedule.copy(endTimeText = it).recalculatePreview()) },
                    label = stringResource(R.string.end),
                    isError = endIsError,
                    modifier = Modifier.width(editorTimeChipWidth()).testTag("editor-end-time"),
                )
            }
        }
    }
}

private val EditorTimeChipWidth = 76.dp

/** The width of a time chip, which grows with the font so the time is never cut off. */
@Composable
internal fun editorTimeChipWidth(): Dp =
    EditorTimeChipWidth * LocalDensity.current.fontScale.coerceIn(1f, MaxAppFontScale)

/**
 * One line of a schedule: a short label such as "Start", then its date and time chips. With a
 * large font the label sits above the chips, so the date keeps room for its text.
 */
@Composable
internal fun EditorScheduleLine(
    label: String,
    content: @Composable RowScope.() -> Unit,
) {
    val labelAbove = LocalDensity.current.fontScale > EditorScheduleLabelAboveFontScale
    val labelText: @Composable (Modifier) -> Unit = { modifier ->
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            softWrap = false,
            modifier = modifier,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (labelAbove) labelText(Modifier.padding(start = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!labelAbove) labelText(Modifier.width(48.dp))
            content()
        }
    }
}

private const val EditorScheduleLabelAboveFontScale = 1.15f

@Composable
internal fun editorDateLabel(isoDate: String): String {
    val locale = LocalAppLocale.current
    val today = LocalCalendarTimeSnapshot.current.today
    return remember(isoDate, locale, today) { formatEditorDate(isoDate, locale, today) }
}

/**
 * The weekday with a short date in the locale's own order, e.g. "Thu, Oct 22" or "Do., 22. Okt.";
 * the year is added when it is not the current one.
 */
internal fun formatEditorDate(isoDate: String, locale: Locale, today: LocalDate = LocalDate.now()): String =
    runCatching {
        val date = LocalDate.parse(isoDate)
        val skeleton = if (date.year == today.year) "EEEdMMM" else "EEEdMMMy"
        date.format(DateTimeFormatter.ofPattern(AndroidDateFormat.getBestDateTimePattern(locale, skeleton), locale))
    }.getOrDefault(isoDate)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    showLabel: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    PickerOutlinedField(
        value = editorDateLabel(value),
        label = label,
        onClick = { dialogOpen = true },
        isError = isError,
        supportingText = supportingText,
        showLabel = showLabel,
        containerColor = containerColor,
        modifier = modifier,
    )
    if (dialogOpen) {
        val initialDate = runCatching { LocalDate.parse(value) }.getOrDefault(LocalCalendarTimeSnapshot.current.today)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        PickerPopupTheme {
            DatePickerDialog(
                onDismissRequest = { dialogOpen = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickerState.selectedDateMillis?.let { millis ->
                                onValueChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                            }
                            dialogOpen = false
                        },
                    ) {
                        Text(stringResource(R.string.apply))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogOpen = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            ) {
                DatePicker(state = pickerState)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    PickerOutlinedField(
        value = value,
        label = label,
        onClick = { dialogOpen = true },
        isError = isError,
        supportingText = supportingText,
        modifier = modifier,
    )
    if (dialogOpen) {
        val initialTime = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.now())
        val pickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = true,
        )
        Dialog(onDismissRequest = { dialogOpen = false }) {
            PickerPopupTheme {
                Surface(
                    modifier = Modifier.widthIn(min = 328.dp, max = 360.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = popupSurfaceColor(),
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.choose_time),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val accent = WarmBrown
                        val accentContainer = accentContainerColor()
                        val onAccent = if (accent.isDark()) Color.White else Color(0xFF1C1A18)
                        val onAccentContainer = if (accentContainer.isDark()) Color.White else Color(0xFF1C1A18)
                        TimePicker(
                            state = pickerState,
                            colors = TimePickerDefaults.colors(
                                clockDialColor = popupControlSurfaceColor(),
                                selectorColor = accent,
                                containerColor = popupSurfaceColor(),
                                periodSelectorBorderColor = MaterialTheme.colorScheme.outline,
                                clockDialSelectedContentColor = onAccent,
                                clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                                periodSelectorSelectedContainerColor = accentContainer,
                                periodSelectorUnselectedContainerColor = popupControlSurfaceColor(),
                                periodSelectorSelectedContentColor = onAccentContainer,
                                periodSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                                timeSelectorSelectedContainerColor = accentContainer,
                                timeSelectorUnselectedContainerColor = popupControlSurfaceColor(),
                                timeSelectorSelectedContentColor = onAccentContainer,
                                timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { dialogOpen = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                            TextButton(
                                onClick = {
                                    onValueChange(LocalTime.of(pickerState.hour, pickerState.minute).toString())
                                    dialogOpen = false
                                },
                            ) {
                                Text(stringResource(R.string.apply))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PickerPopupTheme(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val popupSurface = popupSurfaceColor()
    val controlSurface = popupControlSurfaceColor()
    val accentContainer = accentContainerColor()
    val onAccent = if (WarmBrown.isDark()) Color.White else Color(0xFF1C1A18)
    val onAccentContainer = if (accentContainer.isDark()) Color.White else Color(0xFF1C1A18)
    MaterialTheme(
        colorScheme = colors.copy(
            primary = WarmBrown,
            onPrimary = onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = onAccentContainer,
            secondary = WarmBrown,
            onSecondary = onAccent,
            secondaryContainer = accentContainer,
            onSecondaryContainer = onAccentContainer,
            tertiary = WarmBrown,
            onTertiary = onAccent,
            tertiaryContainer = accentContainer,
            onTertiaryContainer = onAccentContainer,
            surface = popupSurface,
            surfaceVariant = controlSurface,
            surfaceContainerLowest = popupSurface,
            surfaceContainerLow = popupSurface,
            surfaceContainer = popupSurface,
            surfaceContainerHigh = popupSurface,
            surfaceContainerHighest = controlSurface,
        ),
        typography = MaterialTheme.typography,
        content = content,
    )
}

@Composable
internal fun popupSurfaceColor(): Color =
    if (MaterialTheme.colorScheme.background.isDark()) Color(0xFF202124) else Color.White

@Composable
internal fun popupControlSurfaceColor(): Color =
    if (MaterialTheme.colorScheme.background.isDark()) Color(0xFF303134) else Color(0xFFF1F3F4)

/** A date or time as a tappable chip on the editor card; [label] names it for screen readers. */
@Composable
internal fun PickerOutlinedField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    showLabel: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val shape = RoundedCornerShape(14.dp)
    val error = MaterialTheme.colorScheme.error
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showLabel) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 16.sp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(shape)
                .background(if (isError) error.copy(alpha = 0.12f) else containerColor)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "$label: $value" }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                value,
                color = if (isError) error else WarmInk,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (supportingText != null) {
            Text(supportingText, color = if (isError) error else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp)
        }
    }
}
