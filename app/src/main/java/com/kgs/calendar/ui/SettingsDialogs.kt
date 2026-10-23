@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import com.kgs.calendar.ui.components.GripHandleIcon
import com.kgs.calendar.ui.sources.shownName
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.key
import androidx.compose.ui.zIndex
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.settings.AppThemeMode
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun CollectionSelectionDialog(
    title: String,
    selectedHref: String?,
    collections: List<CollectionEntity>,
    hiddenCollectionHrefs: Set<String> = emptySet(),
    onSelected: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    KgsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            DialogChoiceGroup(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                DialogChoiceRow(label = appString(R.string.automatic), selected = selectedHref == null, onClick = { onSelected(null) })
                collections.forEach { collection ->
                    val hidden = collection.href in hiddenCollectionHrefs
                    DialogChoiceRow(
                        label = collection.displayName,
                        subtitle = if (hidden) appString(R.string.calendar_hidden) else collection.localizedKindLabel(),
                        subtitleColor = if (hidden) SyncPendingOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                        selected = selectedHref == collection.href,
                        leading = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(collection.color))) },
                        onClick = { onSelected(collection.href) },
                    )
                }
            }
        },
        confirmButton = { DialogDismissButton(appString(R.string.close), onClick = onDismiss) },
    )
}

internal enum class DurationUnit(val label: String, val minutes: Int) {
    Minutes("Minutes", 1),
    Hours("Hours", 60),
}

private enum class EventDurationChoice {
    ThirtyMinutes,
    OneHour,
    Custom,
}

/** The app themes as tiles with their colors; the selected one is filled with the accent. */
@Composable
internal fun ThemeSwatchPicker(
    selected: AppThemeMode,
    onSelected: (AppThemeMode) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(settingsControlColor())
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AppThemeMode.entries.forEach { mode ->
                ThemeSwatch(
                    label = mode.localizedLabel(),
                    colors = mode.themePreviewColors(scheme),
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ThemeSwatch(
    label: String,
    colors: List<Color>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) WarmPeach else Color.Transparent
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.getOrElse(1) { Color.LightGray }),
            contentAlignment = Alignment.Center,
        ) {
            // The main color as a large dot, the accent as a small one beside it.
            Box(Modifier.size(30.dp).clip(CircleShape).background(colors.first()))
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 6.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(colors.getOrElse(3) { colors.first() }),
            )
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            label,
            color = WarmInk,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun AppThemeMode.themePreviewColors(currentScheme: androidx.compose.material3.ColorScheme): List<Color> = when (this) {
    AppThemeMode.KgsBlue -> listOf(Color(0xFF2563A8), Color(0xFFDCEBFF), Color.White, Color(0xFF4FA7BD))
    AppThemeMode.KgsWarm -> listOf(Color(0xFF9E572B), Color(0xFFFBE9E2), Color.White, Color(0xFF56B0A2))
    AppThemeMode.KgsFresh -> listOf(Color(0xFF0E7C66), Color(0xFFDDF2EC), Color.White, Color(0xFFE29D3E))
    AppThemeMode.SystemDynamic -> listOf(currentScheme.primary, currentScheme.background, currentScheme.surface, currentScheme.tertiary)
}

@Composable
internal fun EventDurationDialog(
    currentMinutes: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialChoice = remember(currentMinutes) {
        when (currentMinutes) {
            30 -> EventDurationChoice.ThirtyMinutes
            60 -> EventDurationChoice.OneHour
            else -> EventDurationChoice.Custom
        }
    }
    var choice by remember(currentMinutes) { mutableStateOf(initialChoice) }
    val initialUnit = remember(currentMinutes) {
        if (currentMinutes >= 60 && currentMinutes % 60 == 0) DurationUnit.Hours else DurationUnit.Minutes
    }
    var unit by remember(currentMinutes) { mutableStateOf(initialUnit) }
    var amountText by remember(currentMinutes) {
        mutableStateOf((if (initialUnit == DurationUnit.Hours) currentMinutes / 60 else currentMinutes).coerceAtLeast(1).toString())
    }
    val minuteDurationLabel = DurationUnit.Minutes.localizedLabel()
    val hourDurationLabel = DurationUnit.Hours.localizedLabel()
    val durationUnitLabels = listOf(
        DurationUnit.Minutes to minuteDurationLabel,
        DurationUnit.Hours to hourDurationLabel,
    )
    ModalEditorDialog(title = appString(R.string.default_event_duration), onDismiss = onDismiss) {
        DialogChoiceGroup {
        PresetListRow(
            label = appString(R.string.thirty_min),
            selected = choice == EventDurationChoice.ThirtyMinutes,
            onClick = { choice = EventDurationChoice.ThirtyMinutes },
        )
        PresetListRow(
            label = appString(R.string.one_hour),
            selected = choice == EventDurationChoice.OneHour,
            onClick = { choice = EventDurationChoice.OneHour },
        )
        PresetListRow(
            label = appString(R.string.custom_duration),
            selected = choice == EventDurationChoice.Custom,
            onClick = { choice = EventDurationChoice.Custom },
        )
        }
        AnimatedVisibility(visible = choice == EventDurationChoice.Custom) {
            NumberUnitRow(
                amount = amountText,
                onAmountChange = {
                    amountText = it
                    choice = EventDurationChoice.Custom
                },
                unitLabel = durationUnitLabels.firstOrNull { it.first == unit }?.second ?: minuteDurationLabel,
                onUnitSelected = { label ->
                    durationUnitLabels.firstOrNull { it.second == label }?.let { selectedUnit ->
                        unit = selectedUnit.first
                        choice = EventDurationChoice.Custom
                    }
                },
                unitOptions = durationUnitLabels.map { it.second },
                label = appString(R.string.duration),
            )
        }
        DialogActions(
            onDismiss = onDismiss,
            onSave = {
                val minutes = when (choice) {
                    EventDurationChoice.ThirtyMinutes -> 30
                    EventDurationChoice.OneHour -> 60
                    EventDurationChoice.Custom -> (amountText.toIntOrNull() ?: 1).coerceAtLeast(1) * unit.minutes
                }
                onSelected(minutes.coerceIn(5, 24 * 60))
            },
            saveEnabled = choice != EventDurationChoice.Custom || amountText.toIntOrNull() != null,
        )
    }
}

@Composable
internal fun FieldOrderList(
    fields: List<String>,
    onChanged: (List<String>) -> Unit,
) {
    var ordered by remember { mutableStateOf(fields) }
    LaunchedEffect(fields) {
        if (fields != ordered) ordered = fields
    }
    val density = LocalDensity.current
    val rowHeightDp = ReorderRowHeight
    val spacingDp = ReorderRowGap
    val stepPx = with(density) { (rowHeightDp + spacingDp).toPx() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val targetIndex = draggingIndex?.let { start ->
        (start + (dragOffsetPx / stepPx).roundToInt()).coerceIn(0, ordered.lastIndex)
    }

    // Nothing here may clip: a lifted row's shadow reaches past the list's edges.
    Column(
        verticalArrangement = Arrangement.spacedBy(spacingDp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ordered.forEachIndexed { index, field ->
            key(field) {
                val isDragging = index == draggingIndex
                val displacement = when {
                    isDragging || draggingIndex == null || targetIndex == null -> 0f
                    draggingIndex!! < targetIndex!! && index > draggingIndex!! && index <= targetIndex!! -> -stepPx
                    draggingIndex!! > targetIndex!! && index < draggingIndex!! && index >= targetIndex!! -> stepPx
                    else -> 0f
                }
                val animatedDisplacement by animateFloatAsState(
                    targetValue = displacement,
                    animationSpec = if (draggingIndex == null) snap() else tween(MotionMedium, easing = MotionStandard),
                    label = "fieldOrderShift",
                )
                Box(
                    modifier = Modifier
                        .reorderLift(isDragging) { if (isDragging) dragOffsetPx else animatedDisplacement }
                        .fillMaxWidth()
                        .height(rowHeightDp)
                        .background(settingsControlColor()),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIconBadge(field.settingsFieldIcon())
                        Spacer(Modifier.width(16.dp))
                        Text(
                            field.localizedSettingsFieldLabel(),
                            color = WarmInk,
                            fontSize = 16.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(56.dp)
                                .pointerInput(field, index) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingIndex = index
                                            dragOffsetPx = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetPx += dragAmount.y
                                        },
                                        onDragEnd = {
                                            val start = draggingIndex
                                            if (start != null) {
                                                val end = (start + (dragOffsetPx / stepPx).roundToInt()).coerceIn(0, ordered.lastIndex)
                                                if (start != end) {
                                                    val mutable = ordered.toMutableList()
                                                    val moved = mutable.removeAt(start)
                                                    mutable.add(end, moved)
                                                    ordered = mutable
                                                    onChanged(mutable)
                                                }
                                            }
                                            draggingIndex = null
                                            dragOffsetPx = 0f
                                        },
                                        onDragCancel = {
                                            draggingIndex = null
                                            dragOffsetPx = 0f
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(GripHandleIcon, contentDescription = stringResource(R.string.move), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun String.localizedSettingsFieldLabel(): String = when (this) {
    "time" -> stringResource(R.string.field_time)
    "status" -> stringResource(R.string.field_status)
    "location" -> stringResource(R.string.field_location)
    "reminders" -> stringResource(R.string.field_reminders)
    "notes" -> stringResource(R.string.field_notes)
    "categories" -> stringResource(R.string.field_categories)
    "tags" -> stringResource(R.string.field_categories)
    "color" -> stringResource(R.string.field_color)
    "participants" -> stringResource(R.string.field_participants)
    "recurrence" -> stringResource(R.string.field_recurrence)
    "url" -> stringResource(R.string.field_url)
    "priority" -> stringResource(R.string.field_priority)
    "progress" -> stringResource(R.string.field_progress)
    else -> replaceFirstChar { it.titlecase(LocalAppLocale.current) }
}

private fun String.settingsFieldIcon(): ImageVector = when (this) {
    "time" -> Icons.Default.AccessTime
    "status" -> Icons.Default.CheckCircle
    "location" -> Icons.Default.LocationOn
    "reminders" -> Icons.Default.Notifications
    "notes" -> Icons.Default.Notes
    "categories" -> Icons.Default.Label
    "tags" -> Icons.Default.Label
    "color" -> Icons.Default.Palette
    "participants" -> Icons.Default.PersonAdd
    "recurrence" -> Icons.Default.Repeat
    "url" -> Icons.Default.Link
    "priority" -> Icons.Default.Flag
    "progress" -> Icons.Default.Percent
    else -> Icons.Default.Edit
}

@Composable
internal fun CollectionReorderList(
    collections: List<CollectionEntity>,
    accounts: List<AccountEntity>,
    hiddenCollectionHrefs: Set<String> = emptySet(),
    onReordered: (List<String>) -> Unit,
    onCollectionClick: (CollectionEntity) -> Unit = {},
) {
    // Local working copy. The list order itself stays STABLE during a drag — we never
    // mutate it mid-gesture (that's what made the old version "snap" and drop the
    // gesture). Instead the dragged row floats by the raw finger delta, and the other
    // rows animate into the gap. We only commit the new order on release.
    var ordered by remember { mutableStateOf(collections) }
    val density = LocalDensity.current
    val rowHeightDp = ReorderRowHeight
    val spacingDp = ReorderRowGap
    val stepPx = with(density) { (rowHeightDp + spacingDp).toPx() }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var pendingOrderHrefs by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(collections, draggingIndex) {
        val incomingHrefs = collections.map { it.href }
        val pending = pendingOrderHrefs
        when {
            pending != null && incomingHrefs == pending -> {
                pendingOrderHrefs = null
                ordered = collections
            }
            pending == null && draggingIndex == null -> {
                ordered = collections
            }
        }
    }

    val reorderable = remember(ordered) { ordered.filter { it.isVisibleInSettingsCalendarList() && it.isEnabled } }
    val inactiveCollections = remember(ordered) {
        ordered.filter { it.isVisibleInSettingsCalendarList() && !it.isEnabled }
    }

    // Live target slot derived from how far the finger has travelled in whole visible rows.
    val targetIndex = draggingIndex?.let { start ->
        (start + (dragOffsetPx / stepPx).roundToInt()).coerceIn(0, reorderable.lastIndex)
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacingDp), modifier = Modifier.fillMaxWidth()) {
        val accountNames = accounts.associate { it.id to it.shownName() }
        ordered.forEach { collection ->
          key(collection.href) {
            val visibleIndex = reorderable.indexOfFirst { it.href == collection.href }
            val visible = visibleIndex >= 0
            val isDragging = visible && visibleIndex == draggingIndex
            // Non-dragged rows shift by one step to open a gap at the target slot.
            val displacement: Float = when {
                !visible || isDragging || draggingIndex == null || targetIndex == null -> 0f
                draggingIndex!! < targetIndex!! && visibleIndex > draggingIndex!! && visibleIndex <= targetIndex!! -> -stepPx
                draggingIndex!! > targetIndex!! && visibleIndex < draggingIndex!! && visibleIndex >= targetIndex!! -> stepPx
                else -> 0f
            }
            // While dragging, neighbouring rows glide to open the gap. The instant the
            // drag ends (draggingIndex == null) the underlying list has already been
            // reordered, so displacement must snap to 0 — otherwise rows animate from
            // their old gap offset and you get the "flick back then shuffle" glitch.
            val animatedDisplacement by animateFloatAsState(
                targetValue = displacement,
                animationSpec = if (draggingIndex == null) snap() else tween(MotionMedium, easing = MotionStandard),
                label = "reorderRowShift",
            )
            AnimatedVisibility(
                visible = visible,
                // Lifted on the row itself, the list's direct child, so it draws above the others.
                modifier = Modifier.reorderLift(isDragging) { if (isDragging) dragOffsetPx else animatedDisplacement },
                enter = expandVertically(animationSpec = tween(MotionMedium, easing = MotionStandard)) + fadeIn(animationSpec = tween(MotionShort, easing = MotionStandard)),
                exit = shrinkVertically(animationSpec = tween(MotionMedium, easing = MotionStandardAccelerate)) + fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)),
            ) {
                val hidden = collection.href in hiddenCollectionHrefs
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeightDp)
                        .background(settingsControlColor())
                        .clickable(onClick = { onCollectionClick(collection) }),
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(collection.color).copy(alpha = if (hidden) 0.35f else 1f)),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(end = 4.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            collection.displayName,
                            color = WarmInk.copy(alpha = if (hidden) 0.6f else 1f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (hidden) {
                                Text(stringResource(R.string.calendar_hidden), color = SyncPendingOrange, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 16.sp)
                            }
                            Text(
                                accountNames[collection.accountId] ?: stringResource(R.string.source),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                    // Generous handle hit area: full row height, ~56dp wide. Keying the
                    // pointerInput on `index` ensures the gesture closure always sees this
                    // row's CURRENT index (after a previous reorder it would otherwise be
                    // stale and grab the wrong calendar).
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(56.dp)
                            .pointerInput(collection.href, visibleIndex) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingIndex = visibleIndex.takeIf { it >= 0 }
                                        dragOffsetPx = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetPx += dragAmount.y
                                    },
                                    onDragEnd = {
                                        val start = draggingIndex
                                        if (start != null) {
                                            val shift = (dragOffsetPx / stepPx).roundToInt()
                                            val end = (start + shift).coerceIn(0, reorderable.lastIndex)
                                            if (start != end) {
                                                val visibleMutable = reorderable.toMutableList()
                                                val moved = visibleMutable.removeAt(start)
                                                visibleMutable.add(end, moved)
                                                val nextVisible = visibleMutable.toMutableList()
                                                val reorderedAll = ordered.map { item ->
                                                    if (item.isVisibleInSettingsCalendarList() && item.isEnabled) nextVisible.removeAt(0) else item
                                                }
                                                val nextOrder = reorderedAll.map { it.href }
                                                ordered = reorderedAll
                                                pendingOrderHrefs = nextOrder
                                                onReordered(nextOrder)
                                            }
                                        }
                                        draggingIndex = null
                                        dragOffsetPx = 0f
                                    },
                                    onDragCancel = {
                                        draggingIndex = null
                                        dragOffsetPx = 0f
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = GripHandleIcon,
                            contentDescription = stringResource(R.string.move),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                }
            }
          }
        }
        if (inactiveCollections.isNotEmpty()) {
            Text(
                stringResource(R.string.inactive_calendars),
                color = WarmBrown,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 18.dp, start = 16.dp, bottom = 6.dp),
            )
            inactiveCollections.forEachIndexed { index, collection ->
                key("inactive-${collection.href}") {
                    InactiveCollectionOrderRow(
                        collection = collection,
                        shape = ReorderCardShape,
                        accountName = accountNames[collection.accountId] ?: stringResource(R.string.source),
                        hidden = collection.href in hiddenCollectionHrefs,
                        onClick = { onCollectionClick(collection) },
                    )
                }
            }
        }
        if (ordered.none { it.isVisibleInSettingsCalendarList() }) {
            Text(
                stringResource(R.string.no_calendars_synced),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun InactiveCollectionOrderRow(
    collection: CollectionEntity,
    shape: Shape,
    accountName: String,
    hidden: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ReorderRowHeight)
            .clip(shape)
            .background(settingsControlColor().copy(alpha = 0.72f))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(collection.color).copy(alpha = 0.45f)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    collection.displayName,
                    color = WarmInk.copy(alpha = 0.72f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (hidden) {
                        "$accountName · ${stringResource(R.string.calendar_hidden)}"
                    } else {
                        accountName
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val ReorderRowHeight = 64.dp
private val ReorderRowGap = 8.dp
private val ReorderCardShape = RoundedCornerShape(20.dp)

/**
 * Each row of a reorderable list is its own rounded card. While dragged it follows the finger
 * in front of the others, grows slightly and casts a shadow that fades in and out.
 */
@Composable
private fun Modifier.reorderLift(isDragging: Boolean, translation: () -> Float): Modifier {
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 0.dp,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "reorderLiftElevation",
    )
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "reorderLiftScale",
    )
    return this
        // Stays in front until the shadow has faded after the drop.
        .zIndex(if (isDragging || elevation > 0.dp) 10f else 0f)
        .graphicsLayer {
            translationY = translation()
            scaleX = scale
            scaleY = scale
            shadowElevation = elevation.toPx()
            shape = ReorderCardShape
            clip = true
        }
}
