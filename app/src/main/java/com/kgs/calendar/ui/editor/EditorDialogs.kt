@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.kgs.calendar.ui.components.ClearableOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Dialog
import com.kgs.calendar.R
import com.kgs.calendar.domain.model.MAX_REMINDER_MINUTES
import com.kgs.calendar.domain.model.REMINDER_AT_END
import com.kgs.calendar.domain.model.REMINDER_AT_START
import com.kgs.calendar.domain.model.normalizedReminderOffsets
import com.kgs.calendar.ui.labels.RecurrenceOption
import com.kgs.calendar.ui.labels.ReminderChoice
import com.kgs.calendar.ui.labels.ReminderUnit
import com.kgs.calendar.ui.labels.recurrenceFrequency
import com.kgs.calendar.ui.labels.recurrencePart
import com.kgs.calendar.ui.labels.toIsoUntilDate
import com.kgs.calendar.ui.labels.toRecurrenceUntilValue
import com.kgs.calendar.ui.labels.toReminderAmountUnit
import kotlinx.coroutines.flow.map
import java.util.Locale

private val ReminderPresets = listOf(0, 5, 10, 15, 30, 60, 120, 1440)

internal interface EventIcalOption {
    val value: String
    val label: String
}

internal enum class EventStatusOption(override val value: String, override val label: String) : EventIcalOption {
    Confirmed("CONFIRMED", "Confirmed"),
    Tentative("TENTATIVE", "Tentative"),
    Cancelled("CANCELLED", "Cancelled"),
}

internal enum class EventClassOption(override val value: String, override val label: String) : EventIcalOption {
    Public("PUBLIC", "Full details"),
    Confidential("CONFIDENTIAL", "Busy only"),
    Private("PRIVATE", "Hidden"),
}

internal enum class EventTransparencyOption(override val value: String, override val label: String) : EventIcalOption {
    Busy("OPAQUE", "Busy"),
    Free("TRANSPARENT", "Free"),
}

@Composable
internal fun EventIcalOption.localizedLabel(): String = when (value.uppercase(Locale.US)) {
    "CONFIRMED" -> appString(R.string.confirmed)
    "TENTATIVE" -> appString(R.string.tentative)
    "CANCELLED" -> appString(R.string.cancelled)
    "PUBLIC" -> appString(R.string.full_details)
    "CONFIDENTIAL" -> appString(R.string.busy_only)
    "PRIVATE" -> appString(R.string.hidden)
    "OPAQUE" -> appString(R.string.busy)
    "TRANSPARENT" -> appString(R.string.free)
    else -> label
}

@Composable
internal fun SharedEventEditor(
    organizerJson: String?,
    attendeesJson: String?,
    onAttendeesJsonChange: (String?) -> Unit,
) {
    val organizer = remember(organizerJson) { organizerJson.toCalendarParticipant() }
    val attendees = remember(attendeesJson) { attendeesJson.toCalendarParticipants() }
    var newEmail by remember { mutableStateOf("") }
    fun addParticipant() {
        val email = newEmail.trim()
        if (!email.isLikelyEmailAddress()) return
        if (attendees.none { it.email.equals(email, ignoreCase = true) }) {
            onAttendeesJsonChange(
                (attendees + CalendarParticipant(name = email, email = email, rsvp = true)).toAttendeesJson(),
            )
        }
        newEmail = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorLine(Icons.Default.PersonAdd) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.participants), fontSize = 15.sp, lineHeight = 18.sp, color = WarmInk, fontWeight = FontWeight.SemiBold)
                Text(
                    if (attendees.isEmpty()) stringResource(R.string.no_participants) else stringResource(R.string.participant_count, attendees.size),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        organizer?.let {
            Text(stringResource(R.string.organized_by, it.displayName), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 16.sp)
        }
        attendees.forEachIndexed { index, attendee ->
            ParticipantEditorRow(
                attendee = attendee,
                onChange = { changed ->
                    onAttendeesJsonChange(attendees.toMutableList().also { it[index] = changed }.toAttendeesJson())
                },
                onRemove = {
                    onAttendeesJsonChange(attendees.toMutableList().also { it.removeAt(index) }.toAttendeesJson())
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newEmail,
                onValueChange = { newEmail = it },
                placeholder = { Text(stringResource(R.string.email_address)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addParticipant() }),
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            addParticipant()
                            true
                        } else {
                            false
                        }
                    },
            )
            IconButton(
                enabled = newEmail.trim().isLikelyEmailAddress(),
                onClick = { addParticipant() },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_participant), tint = WarmBrown)
            }
        }
    }
}

@Composable
internal fun ParticipantEditorRow(
    attendee: CalendarParticipant,
    onChange: (CalendarParticipant) -> Unit,
    onRemove: () -> Unit,
) {
    var roleMenuOpen by remember(attendee.email) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(attendee.displayName, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(attendee.email, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_participant), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(19.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = attendee.rsvp,
                    onCheckedChange = { onChange(attendee.copy(rsvp = it)) },
                    modifier = Modifier.size(34.dp),
                )
                Text(stringResource(R.string.request_response), color = WarmInk, fontSize = 13.sp, lineHeight = 16.sp)
            }
            Box {
                FilledTonalButton(
                    onClick = { roleMenuOpen = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(attendee.role.localizedParticipantRoleLabel(), fontSize = 13.sp, lineHeight = 16.sp)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(17.dp))
                }
                DropdownMenu(
                    expanded = roleMenuOpen,
                    onDismissRequest = { roleMenuOpen = false },
                    shape = RoundedCornerShape(14.dp),
                ) {
                    ParticipantRoleOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.localizedLabel()) },
                            onClick = {
                                roleMenuOpen = false
                                onChange(attendee.copy(role = option.value))
                            },
                        )
                    }
                }
            }
            attendee.localizedDeliveryStatusLabel()?.let {
                Text(it, color = attendee.deliveryStatusColor(), fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun reminderMinuteLabel(minutes: Int): String = when {
    minutes == REMINDER_AT_END -> appString(R.string.at_end)
    minutes == REMINDER_AT_START -> appString(R.string.at_start)
    minutes % 1440 == 0 -> appString(R.string.days_before, minutes / 1440)
    minutes % 60 == 0 -> appString(R.string.hours_before, minutes / 60)
    else -> appString(R.string.minutes_before, minutes)
}

@Composable
internal fun ReminderUnit.localizedLabel(): String = when (this) {
    ReminderUnit.Minutes -> appString(R.string.minutes)
    ReminderUnit.Hours -> appString(R.string.hours)
    ReminderUnit.Days -> appString(R.string.days)
    ReminderUnit.Weeks -> appString(R.string.weeks)
}

@Composable
internal fun ReminderChoice.localizedLabel(): String = when (this) {
    ReminderChoice.None -> appString(R.string.no_reminder)
    ReminderChoice.AtStart -> appString(R.string.at_start)
    ReminderChoice.AtEnd -> appString(R.string.at_end)
    ReminderChoice.FifteenMinutes -> appString(R.string.fifteen_min_before)
    ReminderChoice.TwoHours -> appString(R.string.two_hours_before)
    ReminderChoice.OneDay -> appString(R.string.one_day_before)
    ReminderChoice.OneWeek -> appString(R.string.one_week_before_full)
    ReminderChoice.Custom -> appString(R.string.custom_reminder)
}

@Composable
internal fun ModalEditorDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = DialogShape,
            color = popupSurfaceColor(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, color = WarmInk, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
                content()
            }
        }
    }
}

@Composable
internal fun DialogActions(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DialogDismissButton(appString(R.string.cancel), onClick = onDismiss)
        DialogConfirmButton(appString(R.string.save), onClick = onSave, enabled = saveEnabled)
    }
}

@Composable
internal fun CompactEditorHeaderLine(
    icon: ImageVector,
    title: String,
    value: String,
    actionIcon: ImageVector,
    actionDescription: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = WarmInk, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            if (value.isNotEmpty()) {
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onAction, modifier = Modifier.size(36.dp)) {
            Icon(actionIcon, contentDescription = actionDescription, tint = WarmBrown, modifier = Modifier.size(20.dp))
        }
    }
}

/** A value such as a reminder as a borderless pill: tap to edit, cross to remove. */
@Composable
internal fun EditorValuePill(value: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, color = WarmInk, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
internal fun MetadataDropdownLine(
    label: String,
    options: List<out EventIcalOption>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value.equals(selected, ignoreCase = true) } ?: options.first()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = WarmInk, fontSize = 15.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
        Box {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .clickable { expanded = true },
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(current.localizedLabel(), color = WarmInk, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = WarmInk, modifier = Modifier.size(17.dp))
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.localizedLabel()) },
                        onClick = {
                            onSelected(option.value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** A preset in a popup list; callers place these rows in a [DialogChoiceGroup]. */
@Composable
internal fun PresetListRow(label: String, selected: Boolean, onClick: () -> Unit) {
    DialogChoiceRow(label = label, selected = selected, onClick = onClick)
}

@Composable
internal fun NumberUnitRow(
    amount: String,
    onAmountChange: (String) -> Unit,
    unitLabel: String,
    onUnitSelected: (String) -> Unit,
    unitOptions: List<String>,
    label: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DialogTextField(
            value = amount,
            onValueChange = { onAmountChange(it.filter(Char::isDigit).take(4)) },
            label = label,
            numeric = true,
            modifier = Modifier.weight(1f),
        )
        DropdownPill(
            label = unitLabel,
            options = unitOptions,
            onSelected = onUnitSelected,
            modifier = Modifier.width(122.dp),
        )
    }
}

@Composable
internal fun DropdownPill(
    label: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = editorSectionColor(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(label, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun CompactReminderEditor(selected: Set<Int>, onSelectedChange: (Set<Int>) -> Unit) {
    var editingReminder by remember { mutableStateOf<Int?>(null) }
    var addingReminder by remember { mutableStateOf(false) }
    EditorSection(
        onClick = {
            editingReminder = null
            addingReminder = true
        },
    ) {
        CompactEditorHeaderLine(
            icon = Icons.Default.Notifications,
            title = appString(R.string.reminders),
            // The reminders show as pills below, so the summary only says when there are none.
            value = if (selected.isEmpty()) appString(R.string.none) else "",
            actionIcon = Icons.Default.Add,
            actionDescription = stringResource(R.string.add_reminder),
            onAction = {
                editingReminder = null
                addingReminder = true
            },
        )
        AnimatedVisibility(visible = selected.isNotEmpty()) {
            // Each reminder is a pill: a tap edits it, its cross removes it.
            FlowRow(
                modifier = Modifier.padding(start = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selected.normalizedReminderOffsets().forEach { minutes ->
                    EditorValuePill(
                        value = reminderMinuteLabel(minutes),
                        onClick = {
                            editingReminder = minutes
                            addingReminder = true
                        },
                        onRemove = { onSelectedChange(selected - minutes) },
                    )
                }
            }
        }
    }
    if (addingReminder) {
        ReminderDialog(
            initialMinutes = editingReminder,
            onDismiss = { addingReminder = false },
            onSave = { minutes ->
                onSelectedChange(
                    if (minutes == null) {
                        selected - listOfNotNull(editingReminder).toSet()
                    } else {
                        (selected - listOfNotNull(editingReminder).toSet() + minutes).normalizedReminderOffsets().toSet()
                    },
                )
                addingReminder = false
            },
        )
    }
}

@Composable
internal fun SettingsReminderEditor(
    title: String,
    selected: Set<Int>,
    onSelectedChange: (Set<Int>) -> Unit,
) {
    var editingReminder by remember { mutableStateOf<Int?>(null) }
    var addingReminder by remember { mutableStateOf(false) }
    // A row inside a settings group, which gives it its rounded card. Each reminder is a pill:
    // tapping it edits the reminder, its cross removes it.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(settingsControlColor())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = WarmInk, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            selected.normalizedReminderOffsets().forEach { minutes ->
                Row(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(WarmBrown.copy(alpha = 0.12f))
                        .clickable {
                            editingReminder = minutes
                            addingReminder = true
                        }
                        .padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(reminderMinuteLabel(minutes), color = WarmInk, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                    IconButton(
                        onClick = { onSelectedChange((selected - minutes).normalizedReminderOffsets().toSet()) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .clickable {
                        editingReminder = null
                        addingReminder = true
                    }
                    .padding(start = 10.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.add_reminder), color = WarmBrown, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (addingReminder) {
        ReminderDialog(
            initialMinutes = editingReminder,
            onDismiss = { addingReminder = false },
            onSave = { minutes ->
                onSelectedChange(
                    if (minutes == null) {
                        (selected - listOfNotNull(editingReminder).toSet()).normalizedReminderOffsets().toSet()
                    } else {
                        (selected - listOfNotNull(editingReminder).toSet() + minutes).normalizedReminderOffsets().toSet()
                    },
                )
                addingReminder = false
            },
        )
    }
}

@Composable
internal fun ReminderDialog(initialMinutes: Int?, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    val initialChoice = remember(initialMinutes) {
        ReminderChoice.entries.firstOrNull { it.minutes == initialMinutes } ?: if (initialMinutes == null) ReminderChoice.None else ReminderChoice.Custom
    }
    val initial = remember(initialMinutes) { (initialMinutes ?: 15).toReminderAmountUnit() }
    var selectedChoice by rememberSaveable(initialMinutes) { mutableStateOf(initialChoice) }
    var amountText by rememberSaveable(initialMinutes) { mutableStateOf(initial.first.toString()) }
    var unit by rememberSaveable(initialMinutes) { mutableStateOf(initial.second) }
    val minuteUnitLabel = ReminderUnit.Minutes.localizedLabel()
    val hourUnitLabel = ReminderUnit.Hours.localizedLabel()
    val dayUnitLabel = ReminderUnit.Days.localizedLabel()
    val weekUnitLabel = ReminderUnit.Weeks.localizedLabel()
    val unitLabels = listOf(
        ReminderUnit.Minutes to minuteUnitLabel,
        ReminderUnit.Hours to hourUnitLabel,
        ReminderUnit.Days to dayUnitLabel,
        ReminderUnit.Weeks to weekUnitLabel,
    )
    ModalEditorDialog(title = appString(if (initialMinutes == null) R.string.add_reminder else R.string.edit_reminder), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DialogChoiceGroup {
                ReminderChoice.entries.forEach { choice ->
                    PresetListRow(
                        label = choice.localizedLabel(),
                        selected = selectedChoice == choice,
                        onClick = {
                            selectedChoice = choice
                            choice.minutes?.takeIf { it > 0 }?.let { minutes ->
                                val (amount, presetUnit) = minutes.toReminderAmountUnit()
                                amountText = amount.toString()
                                unit = presetUnit
                            }
                        },
                    )
                }
            }
            AnimatedVisibility(visible = selectedChoice == ReminderChoice.Custom) {
                NumberUnitRow(
                    amount = amountText,
                    onAmountChange = {
                        amountText = it
                        selectedChoice = ReminderChoice.Custom
                    },
                    unitLabel = unitLabels.firstOrNull { it.first == unit }?.second ?: minuteUnitLabel,
                    onUnitSelected = { label ->
                        unitLabels.firstOrNull { it.second == label }?.let { unit = it.first }
                        selectedChoice = ReminderChoice.Custom
                    },
                    unitOptions = unitLabels.map { it.second },
                    label = stringResource(R.string.custom_reminder),
                )
            }
            DialogActions(
                onDismiss = onDismiss,
                onSave = {
                    when (selectedChoice) {
                        ReminderChoice.None -> onSave(null)
                        ReminderChoice.Custom -> amountText.toIntOrNull()?.let { onSave((it * unit.minutes).coerceIn(0, MAX_REMINDER_MINUTES)) }
                        else -> onSave(selectedChoice.minutes)
                    }
                },
                saveEnabled = selectedChoice != ReminderChoice.Custom || amountText.toIntOrNull() != null,
            )
        }
    }
}

@Composable
internal fun CompactRecurrenceEditor(recurrenceRule: String, onRecurrenceRuleChange: (String?) -> Unit) {
    var dialogOpen by remember { mutableStateOf(false) }
    EditorSection(onClick = { dialogOpen = true }) {
        CompactEditorHeaderLine(
            icon = Icons.Default.Repeat,
            title = appString(R.string.recurrence),
            value = recurrenceRule.takeIf { it.isNotBlank() }?.toLocalizedRecurrenceLabel() ?: appString(R.string.none),
            actionIcon = if (recurrenceRule.isBlank()) Icons.Default.Add else Icons.Default.Edit,
            actionDescription = if (recurrenceRule.isBlank()) stringResource(R.string.add_recurrence) else stringResource(R.string.edit_recurrence),
            onAction = { dialogOpen = true },
        )
    }
    if (dialogOpen) {
        RecurrenceDialog(
            recurrenceRule = recurrenceRule,
            onDismiss = { dialogOpen = false },
            onSave = {
                onRecurrenceRuleChange(it)
                dialogOpen = false
            },
        )
    }
}

@Composable
internal fun RecurrenceDialog(
    recurrenceRule: String,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var draft by remember(recurrenceRule) { mutableStateOf(RecurrenceDraft.fromRule(recurrenceRule)) }
    fun update(next: RecurrenceDraft) {
        draft = next
    }
    val dailyUnitLabel = RecurrenceOption.Daily.localizedIntervalUnitLabel()
    val weeklyUnitLabel = RecurrenceOption.Weekly.localizedIntervalUnitLabel()
    val monthlyUnitLabel = RecurrenceOption.Monthly.localizedIntervalUnitLabel()
    val yearlyUnitLabel = RecurrenceOption.Yearly.localizedIntervalUnitLabel()
    val recurrenceUnitLabels = listOf(
        RecurrenceOption.Daily to dailyUnitLabel,
        RecurrenceOption.Weekly to weeklyUnitLabel,
        RecurrenceOption.Monthly to monthlyUnitLabel,
        RecurrenceOption.Yearly to yearlyUnitLabel,
    )
    ModalEditorDialog(title = appString(R.string.recurrence), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RepeatModeSegmented(
                repeated = draft.option != RecurrenceOption.Once,
                onRepeatedChange = { repeated ->
                    update(if (repeated) draft.copy(option = RecurrenceOption.Weekly, interval = draft.interval.coerceAtLeast(1)) else RecurrenceDraft())
                },
            )
            AnimatedVisibility(visible = draft.option != RecurrenceOption.Once) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberUnitRow(
                        amount = draft.interval.toString(),
                        onAmountChange = { value ->
                            update(draft.copy(interval = value.toIntOrNull()?.coerceIn(1, 999) ?: 1))
                        },
                        unitLabel = draft.option.localizedIntervalUnitLabel(),
                        onUnitSelected = { label ->
                            val option = recurrenceUnitLabels.firstOrNull { it.second == label }?.first ?: draft.option
                            update(draft.copy(option = option))
                        },
                        unitOptions = recurrenceUnitLabels.map { it.second },
                        label = stringResource(R.string.every),
                    )
                    Text(stringResource(R.string.ends), color = WarmInk, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                    RecurrenceEndSegmented(
                        selected = draft.endMode,
                        onSelected = { update(draft.copy(endMode = it)) },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        AnimatedContent(
                            targetState = draft.endMode,
                            transitionSpec = {
                                (fadeIn(tween(MotionShort, easing = MotionStandard)) + scaleIn(tween(MotionShort, easing = MotionStandard), initialScale = 0.98f)) togetherWith
                                    (fadeOut(tween(MotionShort, easing = MotionStandardAccelerate)) + scaleOut(tween(MotionShort, easing = MotionStandardAccelerate), targetScale = 0.98f))
                            },
                            label = "recurrenceEndInput",
                        ) { endMode ->
                            when (endMode) {
                                RecurrenceEndMode.Never -> Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = editorSectionColor(),
                                ) {
                                    Text(
                                        stringResource(R.string.repeat_never_ends),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        lineHeight = 17.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                    )
                                }
                                RecurrenceEndMode.Count -> DialogTextField(
                                    value = draft.count?.toString().orEmpty(),
                                    onValueChange = { value ->
                                        update(draft.copy(count = value.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, 999)))
                                    },
                                    label = stringResource(R.string.repeat_after_count),
                                    numeric = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                RecurrenceEndMode.Until -> DatePickerField(
                                    value = draft.untilDate.orEmpty(),
                                    onValueChange = { value -> update(draft.copy(untilDate = value.take(10))) },
                                    label = stringResource(R.string.end_date),
                                    showLabel = true,
                                    containerColor = editorSectionColor(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(visible = draft.option == RecurrenceOption.Once) {
                Text(
                    stringResource(R.string.does_not_repeat),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                )
            }
            DialogActions(
                onDismiss = onDismiss,
                onSave = { onSave(draft.toRule()) },
                saveEnabled = true,
            )
        }
    }
}

@Composable
internal fun RepeatModeSegmented(
    repeated: Boolean,
    onRepeatedChange: (Boolean) -> Unit,
) {
    val onceLabel = stringResource(R.string.once)
    val repeatedLabel = stringResource(R.string.multiple_times)
    DialogSegmentedControl(
        options = listOf(false, true),
        selected = repeated,
        label = { if (it) repeatedLabel else onceLabel },
        onSelected = onRepeatedChange,
    )
}

@Composable
internal fun RecurrenceEndSegmented(
    selected: RecurrenceEndMode,
    onSelected: (RecurrenceEndMode) -> Unit,
) {
    DialogSegmentedControl(
        options = RecurrenceEndMode.entries,
        selected = selected,
        label = { mode ->
            when (mode) {
                RecurrenceEndMode.Never -> stringResource(R.string.never)
                RecurrenceEndMode.Count -> stringResource(R.string.count)
                RecurrenceEndMode.Until -> stringResource(R.string.date)
            }
        },
        onSelected = onSelected,
    )
}

internal enum class RecurrenceEndMode(val label: String) {
    Never("Never"),
    Count("After count"),
    Until("Until date"),
}

@Composable
internal fun RecurrenceEndMode.localizedLabel(): String = when (this) {
    RecurrenceEndMode.Never -> appString(R.string.never)
    RecurrenceEndMode.Count -> appString(R.string.after_count)
    RecurrenceEndMode.Until -> appString(R.string.until_date)
}

internal data class RecurrenceDraft(
    val option: RecurrenceOption = RecurrenceOption.Once,
    val interval: Int = 1,
    val endMode: RecurrenceEndMode = RecurrenceEndMode.Never,
    val count: Int? = null,
    val untilDate: String? = null,
) {
    fun toRule(): String? {
        if (option == RecurrenceOption.Once) return null
        val freq = option.rule?.substringAfter("FREQ=")?.substringBefore(';') ?: return null
        val parts = mutableListOf("FREQ=$freq")
        if (interval > 1) parts += "INTERVAL=${interval.coerceAtLeast(1)}"
        when (endMode) {
            RecurrenceEndMode.Never -> Unit
            RecurrenceEndMode.Count -> count?.coerceAtLeast(1)?.let { parts += "COUNT=$it" }
            RecurrenceEndMode.Until -> untilDate?.toRecurrenceUntilValue()?.let { parts += "UNTIL=$it" }
        }
        return parts.joinToString(";")
    }

    companion object {
        fun fromRule(rule: String): RecurrenceDraft {
            val freq = rule.recurrenceFrequency()
            val option = when (freq) {
                "DAILY" -> RecurrenceOption.Daily
                "WEEKLY" -> RecurrenceOption.Weekly
                "MONTHLY" -> RecurrenceOption.Monthly
                "YEARLY" -> RecurrenceOption.Yearly
                else -> RecurrenceOption.Once
            }
            val interval = rule.recurrencePart("INTERVAL")?.toIntOrNull()?.coerceIn(1, 999) ?: 1
            val count = rule.recurrencePart("COUNT")?.toIntOrNull()?.coerceIn(1, 999)
            val until = rule.recurrencePart("UNTIL")?.toIsoUntilDate()
            val endMode = when {
                count != null -> RecurrenceEndMode.Count
                until != null -> RecurrenceEndMode.Until
                else -> RecurrenceEndMode.Never
            }
            return RecurrenceDraft(option = option, interval = interval, endMode = endMode, count = count, untilDate = until)
        }
    }
}
