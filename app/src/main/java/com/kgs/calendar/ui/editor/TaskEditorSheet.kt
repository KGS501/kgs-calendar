@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.MaterialTheme
import com.kgs.calendar.ui.components.ClearableOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.R
import com.kgs.calendar.ui.editor.DraftCodec
import com.kgs.calendar.ui.editor.EditorDraftStore
import com.kgs.calendar.ui.editor.rememberEditorDraftFields
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.TaskEditPayload
import com.kgs.calendar.domain.model.normalizedReminderOffsets
import com.kgs.calendar.domain.source.isReadOnlyCollection
import com.kgs.calendar.ui.editor.EditorScheduleState
import com.kgs.calendar.ui.labels.parseReminderMinutes
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@Composable
internal fun TaskEditorSheet(
    state: CalendarUiState,
    schedule: EditorScheduleState,
    onScheduleChange: (EditorScheduleState) -> Unit,
    requestTitleFocus: Boolean = false,
    initialTask: TaskEntity?,
    draftStore: EditorDraftStore,
    draftId: String?,
    forcedParentTask: TaskEntity? = null,
    transferDraft: EditorTransferDraft? = null,
    headerTitle: String? = null,
    readOnlyRemote: Boolean = false,
    onDraftCollectionColorChanged: (Int) -> Unit = {},
    onSave: (TaskEditPayload) -> Unit,
    onSwitchToEvent: (EditorTransferDraft) -> Unit,
    onOpenCalendarSources: () -> Unit = {},
    onClose: () -> Unit,
) {
    val relationLocksCollection = initialTask?.let { task ->
        task.parentUid != null || state.allTasks.any {
            it.collectionHref == task.collectionHref && it.parentUid == task.uid
        }
    } == true
    val lockedCollectionHref = forcedParentTask?.collectionHref
        ?: initialTask?.collectionHref?.takeIf { relationLocksCollection }
    val taskCollections = remember(state.collections, state.defaultTaskCollectionHref, initialTask?.collectionHref, readOnlyRemote, lockedCollectionHref) {
        if (readOnlyRemote && initialTask != null) {
            state.collections.filter { it.href == initialTask.collectionHref }
        } else {
            state.collections
                .filter { it.supportsTasks && it.isEnabled && !it.isReadOnlyCollection() }
                .filter { lockedCollectionHref == null || it.href == lockedCollectionHref }
                .sortedWithDefaultFirst(state.defaultTaskCollectionHref)
        }
    }
    val draft = rememberEditorDraftFields(draftStore, draftId)
    var title by draft.field("title", DraftCodec.Text, initialTask?.uid, transferDraft) { initialTask?.title ?: transferDraft?.title.orEmpty() }
    var notes by draft.field("notes", DraftCodec.Text, initialTask?.uid, transferDraft) { initialTask?.notes ?: transferDraft?.notes.orEmpty() }
    var location by draft.field("location", DraftCodec.Text, initialTask?.uid, transferDraft) { initialTask?.location ?: transferDraft?.location.orEmpty() }
    var locationMapVerified by draft.field("locationMapVerified", DraftCodec.OptionalFlag, initialTask?.uid, transferDraft) { initialTask?.locationMapVerified ?: transferDraft?.locationMapVerified }
    var manualColor by draft.field("manualColor", DraftCodec.OptionalNumber, initialTask?.uid, transferDraft) { initialTask?.manualColor ?: transferDraft?.manualColor }
    var url by draft.field("url", DraftCodec.Text, initialTask?.uid) { initialTask?.url.orEmpty() }
    var categories by draft.field("categories", DraftCodec.Text, initialTask?.uid, transferDraft) { initialTask?.categories ?: transferDraft?.categories.orEmpty() }
    val knownCategories = remember(state.events, state.datedTasks, state.inboxTasks, state.completedTasks) {
        state.allKnownCategoryTags()
    }
    var percentComplete by draft.field("percentComplete", DraftCodec.Number, initialTask?.uid) { initialTask?.percentComplete ?: 0 }
    var parentUid by draft.field("parentUid", DraftCodec.OptionalText, initialTask?.resourceHref, forcedParentTask?.resourceHref) {
        forcedParentTask?.uid ?: initialTask?.parentUid
    }
    var selectedCollectionHref by draft.field("collectionHref", DraftCodec.OptionalText, initialTask?.uid, taskCollections, state.defaultTaskCollectionHref) {
        val preferred = state.defaultTaskCollectionHref
            ?.takeIf { href -> taskCollections.any { it.href == href } }
        initialTask?.collectionHref
            ?: preferred
            ?: taskCollections.firstOrNull()?.href
    }
    val selectedCollectionIndex = remember(taskCollections, selectedCollectionHref) {
        taskCollections.indexOfFirst { it.href == selectedCollectionHref }
    }
    val parentCandidates = remember(state.allTasks, selectedCollectionHref, initialTask?.uid) {
        val excludedUids = initialTask?.let { state.allTasks.descendantUids(it) }.orEmpty() + listOfNotNull(initialTask?.uid)
        state.allTasks
            .filter { it.collectionHref == selectedCollectionHref }
            .filterNot { it.uid in excludedUids }
            .distinctBy { it.uid }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
    }
    var isCompleted by draft.field("isCompleted", DraftCodec.Flag, initialTask?.uid) { initialTask?.isCompleted ?: false }
    var statusValue by draft.field("status", DraftCodec.Text, initialTask?.uid) {
        initialTask?.status?.uppercase()
            ?: if (initialTask?.isCompleted == true) "COMPLETED" else "NEEDS-ACTION"
    }
    val hasStartDate = schedule.hasStartDate
    val hasEndDate = schedule.hasEndDate
    val startDateText = schedule.startDateText
    val endDateText = schedule.endDateText
    val hasStartTime = schedule.hasStartTime
    val startTimeText = schedule.startTimeText
    val hasEndTime = schedule.hasEndTime
    val dueTimeText = schedule.endTimeText
    val allDay = schedule.allDay
    var priority by draft.field("priority", DraftCodec.Number, initialTask?.uid) { initialTask?.priority ?: 9 }
    var recurrenceRule by draft.field("recurrenceRule", DraftCodec.Text, initialTask?.uid, transferDraft) { initialTask?.recurrenceRule ?: transferDraft?.recurrenceRule.orEmpty() }
    var reminderMinutes by draft.field("reminderMinutes", DraftCodec.MinuteSet, initialTask?.uid, transferDraft, state.defaultTaskReminderMinutes) {
        when {
            initialTask != null -> initialTask.remindersCsv.parseReminderMinutes()
            transferDraft != null -> transferDraft.reminderMinutes
            else -> state.defaultTaskReminderMinutes
        }
    }
    var locationPickerOpen by remember(initialTask?.uid) { mutableStateOf(false) }
    var invalidRangeDialogOpen by remember(initialTask?.uid) { mutableStateOf(false) }
    val invalidTimeRange = taskDateTimeRangeInvalid(
        hasStartDate = hasStartDate,
        startDateText = startDateText,
        hasStartTime = hasStartTime,
        startTimeText = startTimeText,
        hasEndDate = hasEndDate,
        endDateText = endDateText,
        hasEndTime = hasEndTime,
        endTimeText = dueTimeText,
        allDay = allDay,
    )
    val titleFocusRequester = remember { FocusRequester() }

    LaunchedEffect(initialTask?.uid, selectedCollectionHref, taskCollections, parentCandidates) {
        if (parentUid != null && parentCandidates.none { it.uid == parentUid }) {
            parentUid = null
        }
        if (initialTask == null) {
            taskCollections.firstOrNull { it.href == selectedCollectionHref }
                ?.let { onDraftCollectionColorChanged(it.color) }
        }
    }

    LaunchedEffect(requestTitleFocus, initialTask?.uid) {
        if (requestTitleFocus && initialTask == null) {
            delay(220)
            titleFocusRequester.requestFocus()
        }
    }

    fun currentTransferDraft(): EditorTransferDraft {
        val parsedStartDate = if (hasStartDate) runCatching { LocalDate.parse(startDateText) }.getOrNull() else null
        val parsedEndDate = if (hasEndDate) runCatching { LocalDate.parse(endDateText) }.getOrNull() else null
        val parsedEndTime = if (hasEndTime && !allDay) runCatching { LocalTime.parse(dueTimeText) }.getOrNull() else null
        val parsedStartTime = when {
            allDay -> null
            hasStartTime -> runCatching { LocalTime.parse(startTimeText) }.getOrNull()
            parsedStartDate == null && parsedEndTime != null -> parsedEndTime.minusMinutes(30)
            else -> null
        }
        return EditorTransferDraft(
            title = title,
            notes = notes,
            location = location,
            locationMapVerified = locationMapVerified,
            manualColor = manualColor,
            categories = categories,
            recurrenceRule = recurrenceRule,
            reminderMinutes = reminderMinutes,
            sourceDefaultReminderMinutes = transferDraft?.sourceDefaultReminderMinutes
                ?: if (initialTask == null) state.defaultTaskReminderMinutes else emptySet(),
            date = parsedStartDate ?: parsedEndDate,
            endDate = parsedEndDate ?: parsedStartDate,
            startTime = parsedStartTime,
            endTime = parsedEndTime,
            allDay = allDay,
            schedule = schedule,
        )
    }

    AnimatedContent(
        targetState = locationPickerOpen,
        transitionSpec = {
            (slideInHorizontally(animationSpec = tween(MotionMedium, easing = MotionStandard)) { width -> width / 3 } + fadeIn(tween(MotionShort))) togetherWith
                (slideOutHorizontally(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) { width -> -width / 3 } + fadeOut(tween(MotionShort)))
        },
        label = "taskLocationPicker",
    ) { pickingLocation ->
        if (pickingLocation) {
            LocationPickerPage(
                initialQuery = location,
                onSelected = { selection ->
                    location = selection.value
                    locationMapVerified = selection.mapVerified
                    locationPickerOpen = false
                },
                onClose = { locationPickerOpen = false },
            )
        } else {
            if (!readOnlyRemote && taskCollections.isEmpty()) {
                EditorContainer(
                    title = headerTitle ?: stringResource(if (initialTask == null) R.string.new_task else R.string.edit_task),
                    action = stringResource(R.string.save),
                    onAction = {},
                    onClose = onClose,
                    actionEnabled = false,
                ) {
                    FadedHorizontalScrollRow(
                        contentPadding = PaddingValues(horizontal = EditorHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TypeChip(
                            stringResource(R.string.event),
                            selected = false,
                            onClick = {
                                onSwitchToEvent(currentTransferDraft())
                            },
                        )
                        TypeChip(stringResource(R.string.task), selected = true)
                    }
                    NoWritableSourceNotice(
                        title = stringResource(R.string.no_writable_task_list_title),
                        body = stringResource(R.string.no_writable_task_list_body),
                        action = stringResource(R.string.add_calendar_source),
                        onAction = onOpenCalendarSources,
                    )
                }
            } else {
                EditorContainer(title = headerTitle ?: stringResource(if (initialTask == null) R.string.new_task else R.string.edit_task), action = stringResource(R.string.save), onAction = {
                if (invalidTimeRange) {
                    invalidRangeDialogOpen = true
                    return@EditorContainer
                }
                val payload = runCatching {
                    TaskEditPayload(
                        title = title,
                        collectionHref = selectedCollectionHref,
                        notes = notes,
                        location = location,
                        locationMapVerified = locationMapVerified,
                        manualColor = manualColor,
                        url = url,
                        categories = categories,
                        startDate = if (hasStartDate) LocalDate.parse(startDateText) else null,
                        startTime = if (hasStartDate && hasStartTime && !allDay) LocalTime.parse(startTimeText) else null,
                        startHasTime = hasStartDate && hasStartTime && !allDay,
                        dueDate = if (hasEndDate) LocalDate.parse(endDateText) else null,
                        dueTime = if (hasEndDate && hasEndTime && !allDay) LocalTime.parse(dueTimeText) else null,
                        dueHasTime = hasEndDate && hasEndTime && !allDay,
                        priority = priority,
                        percentComplete = percentComplete.coerceIn(0, 100),
                        isCompleted = isCompleted,
                        recurrenceRule = recurrenceRule.ifBlank { null },
                        parentUid = parentUid,
                        status = statusValue,
                        reminderMinutes = reminderMinutes.normalizedReminderOffsets(),
                    )
                }.getOrNull() ?: return@EditorContainer
                onSave(payload)
            }, onClose = onClose) {
                if (!readOnlyRemote && forcedParentTask == null) {
                    FadedHorizontalScrollRow(
                        contentPadding = PaddingValues(horizontal = EditorHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TypeChip(
                            stringResource(R.string.event),
                            selected = false,
                            onClick = {
                                onSwitchToEvent(currentTransferDraft())
                            },
                        )
                        TypeChip(stringResource(R.string.task), selected = true)
                    }
                }
                EditorSection {
                    if (readOnlyRemote) {
                        ReadOnlyEditorNotice(title = title)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // The editor opens the full status picker on tap.
                            TaskStatusCheckbox(
                                status = statusValue,
                                tint = if (statusValue == "COMPLETED") WarmBrown else WarmInk,
                                onStatusChange = {
                                    statusValue = it
                                    isCompleted = it == "COMPLETED"
                                },
                                tapOpensPicker = true,
                                boxSize = 40.dp,
                                iconSize = 26.dp,
                            )
                            OutlinedTextField(
                                title,
                                { title = it },
                                placeholder = { Text(stringResource(R.string.add_title)) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleLarge.copy(
                                    color = WarmInk,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 22.sp,
                                    lineHeight = 25.sp,
                                ),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .focusRequester(titleFocusRequester),
                            )
                        }
                        Text(
                            stringResource(R.string.long_press_status_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
                if (!readOnlyRemote && taskCollections.isNotEmpty()) {
                    Text(
                        stringResource(R.string.task_list),
                        color = WarmInk,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.editorInset(),
                    )
                    FadedHorizontalScrollRow(
                        contentPadding = PaddingValues(horizontal = EditorHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        initialScrollIndex = selectedCollectionIndex.takeIf { initialTask != null && it >= 0 },
                        initialScrollKey = initialTask?.resourceHref,
                    ) {
                        taskCollections.forEach { collection ->
                            CalendarSelectorChip(
                                collection = collection,
                                selected = collection.href == selectedCollectionHref,
                                onClick = { selectedCollectionHref = collection.href },
                                hidden = collection.href in state.collectionVisibility.hrefsHidingTasks,
                            )
                        }
                    }
                }
                if (!readOnlyRemote && selectedCollectionHref != null) {
                    TaskParentSelector(
                        selectedParentUid = parentUid,
                        candidates = parentCandidates,
                        taskColorMode = state.taskColorMode,
                        onSelected = { parentUid = it },
                    )
                }
                if (!readOnlyRemote) {
                    EditorSection {
                        TaskScheduleEditor(
                            schedule = schedule,
                            onScheduleChange = onScheduleChange,
                            endIsError = invalidTimeRange,
                        )
                    }
                }
                val editableFields = if (readOnlyRemote) listOf("color") else state.taskFieldOrder.filterNot { it == "time" || it == "status" }
                editableFields.forEach { field ->
                    when (field) {
                        "recurrence" -> CompactRecurrenceEditor(recurrenceRule = recurrenceRule, onRecurrenceRuleChange = { recurrenceRule = it.orEmpty() })
                        "reminders" -> CompactReminderEditor(selected = reminderMinutes, onSelectedChange = { reminderMinutes = it })
                        "location" -> Box(Modifier.editorInset()) { LocationSelectorField(location = location, onClick = { locationPickerOpen = true }) }
                        "notes" -> OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.note)) }, minLines = 3, shape = RoundedCornerShape(16.dp), modifier = Modifier.editorInset().fillMaxWidth())
                        "url" -> OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.url)) }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.editorInset().fillMaxWidth())
                        "priority" -> EditorSection { PrioritySlider(priority = priority, onPriorityChange = { priority = it }) }
                        "progress" -> EditorSection { ProgressSlider(progress = percentComplete, onProgressChange = { percentComplete = it }) }
                        "color" -> ColorOverrideEditor(
                            selectedColor = manualColor,
                            automaticColor = taskCollections.firstOrNull { it.href == selectedCollectionHref }?.color ?: WarmBrown.toArgb(),
                            onColorSelected = { manualColor = it },
                        )
                        "tags" -> TagEditor(stringResource(R.string.categories), categories.toCategoryTags(), knownCategories) { categories = it.toCategoriesCsv() }
                    }
                }
            }
            }
        }
    }
    if (invalidRangeDialogOpen) {
        InvalidTimeRangeDialog(onDismiss = { invalidRangeDialogOpen = false })
    }
}
