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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.kgs.calendar.ui.components.ClearableOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.domain.model.EventEditPayload
import com.kgs.calendar.domain.model.Organizer
import com.kgs.calendar.domain.model.ParticipantJson
import com.kgs.calendar.domain.model.normalizedReminderOffsets
import com.kgs.calendar.domain.source.isReadOnlyCollection
import com.kgs.calendar.ui.editor.EditorScheduleState
import com.kgs.calendar.ui.labels.parseReminderMinutes
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

@Composable
internal fun EventEditorSheet(
    state: CalendarUiState,
    schedule: EditorScheduleState,
    onScheduleChange: (EditorScheduleState) -> Unit,
    expanded: Boolean,
    initialEvent: EventEntity?,
    draftStore: EditorDraftStore,
    draftId: String?,
    transferDraft: EditorTransferDraft? = null,
    headerTitle: String? = null,
    requestTitleFocus: Boolean = false,
    readOnlyRemote: Boolean = false,
    onDraftCollectionColorChanged: (Int) -> Unit = {},
    onSave: (EventEditPayload) -> Unit,
    onSwitchToTask: (EditorTransferDraft) -> Unit,
    onOpenCalendarSources: () -> Unit = {},
    onClose: () -> Unit,
) {
    val eventCollections = remember(state.collections, state.defaultEventCollectionHref, initialEvent?.collectionHref, readOnlyRemote) {
        if (readOnlyRemote && initialEvent != null) {
            state.collections.filter { it.href == initialEvent.collectionHref }
        } else {
            state.collections
                .filter { it.supportsEvents && it.isEnabled && !it.isReadOnlyCollection() }
                .sortedWithDefaultFirst(state.defaultEventCollectionHref)
        }
    }
    val draft = rememberEditorDraftFields(draftStore, draftId)
    var title by draft.field("title", DraftCodec.Text, initialEvent?.uid, transferDraft) { initialEvent?.title ?: transferDraft?.title.orEmpty() }
    var chosenCollectionHref by draft.field("collectionHref", DraftCodec.OptionalText, initialEvent?.uid, eventCollections, state.defaultEventCollectionHref) {
        val preferred = state.defaultEventCollectionHref
            ?.takeIf { href -> eventCollections.any { it.href == href } }
        initialEvent?.collectionHref
            ?: preferred
            ?: eventCollections.firstOrNull()?.href
    }
    val selectedCollectionHref = remember(eventCollections, chosenCollectionHref, initialEvent?.collectionHref, state.defaultEventCollectionHref) {
        eventCollections.editorCollectionHref(chosenCollectionHref, initialEvent?.collectionHref, state.defaultEventCollectionHref)
    }
    val selectedCollectionIndex = remember(eventCollections, selectedCollectionHref) {
        eventCollections.indexOfFirst { it.href == selectedCollectionHref }
    }
    val dateText = schedule.startDateText
    val endDateText = schedule.endDateText
    val startText = schedule.startTimeText
    val endText = schedule.endTimeText
    val allDay = schedule.allDay
    var location by draft.field("location", DraftCodec.Text, initialEvent?.uid, transferDraft) { initialEvent?.location ?: transferDraft?.location.orEmpty() }
    var locationMapVerified by draft.field("locationMapVerified", DraftCodec.OptionalFlag, initialEvent?.uid, transferDraft) { initialEvent?.locationMapVerified ?: transferDraft?.locationMapVerified }
    var manualColor by draft.field("manualColor", DraftCodec.OptionalNumber, initialEvent?.uid, transferDraft) { initialEvent?.manualColor ?: transferDraft?.manualColor }
    var description by draft.field("description", DraftCodec.Text, initialEvent?.uid, transferDraft) { initialEvent?.description ?: transferDraft?.notes.orEmpty() }
    var recurrenceRule by draft.field("recurrenceRule", DraftCodec.Text, initialEvent?.uid, transferDraft) { initialEvent?.recurrenceRule ?: transferDraft?.recurrenceRule.orEmpty() }
    var eventStatus by draft.field("status", DraftCodec.Text, initialEvent?.uid) { initialEvent?.status ?: EventStatusOption.Confirmed.value }
    var eventClassification by draft.field("classification", DraftCodec.Text, initialEvent?.uid) { initialEvent?.classification ?: EventClassOption.Public.value }
    var eventTransparency by draft.field("transparency", DraftCodec.Text, initialEvent?.uid) { initialEvent?.transparency ?: EventTransparencyOption.Busy.value }
    var categories by draft.field("categories", DraftCodec.Text, initialEvent?.uid, transferDraft) { initialEvent?.categories ?: transferDraft?.categories.orEmpty() }
    val knownCategories = remember(state.events, state.datedTasks, state.inboxTasks, state.completedTasks, state.collections, state.categorySync) {
        state.allKnownCategoryTags()
    }
    val keyboardAtSwitch = LocalEditorKeyboardAtSwitch.current
    var reminderMinutes by draft.field("reminderMinutes", DraftCodec.MinuteSet, initialEvent?.uid, transferDraft, state.defaultEventReminderMinutes) {
        when {
            initialEvent != null -> initialEvent.remindersCsv.parseReminderMinutes()
            transferDraft != null -> transferDraft.reminderMinutes
            else -> state.defaultEventReminderMinutes
        }
    }
    val organizerJson = initialEvent?.organizerJson ?: remember(selectedCollectionHref, state.accounts, eventCollections) {
        val accountId = eventCollections.firstOrNull { it.href == selectedCollectionHref }?.accountId
        val account = state.accounts.firstOrNull { it.id == accountId }
        val email = account?.username?.trim()?.takeIf { it.isLikelyEmailAddress() }
        email?.let {
            ParticipantJson.encodeOrganizer(
                Organizer(
                    email = email,
                    name = account.displayName?.takeIf { name -> name.isNotBlank() } ?: email,
                ),
            )
        }
    }
    var attendeesJson by draft.field("attendeesJson", DraftCodec.OptionalText, initialEvent?.uid) { initialEvent?.attendeesJson }
    var locationPickerOpen by remember(initialEvent?.uid) { mutableStateOf(false) }
    var invalidRangeDialogOpen by remember(initialEvent?.uid) { mutableStateOf(false) }
    val invalidTimeRange = eventDateTimeRangeInvalid(
        startDateText = dateText,
        endDateText = endDateText,
        startTimeText = startText,
        endTimeText = endText,
        allDay = allDay,
    )

    fun currentTransferDraft(): EditorTransferDraft =
        EditorTransferDraft(
            title = title,
            notes = description,
            location = location,
            locationMapVerified = locationMapVerified,
            manualColor = manualColor,
            categories = categories,
            recurrenceRule = recurrenceRule,
            reminderMinutes = reminderMinutes,
            sourceDefaultReminderMinutes = transferDraft?.sourceDefaultReminderMinutes
                ?: if (initialEvent == null) state.defaultEventReminderMinutes else emptySet(),
            date = runCatching { LocalDate.parse(dateText) }.getOrNull(),
            endDate = runCatching { LocalDate.parse(endDateText) }.getOrNull(),
            startTime = if (allDay) null else runCatching { LocalTime.parse(startText) }.getOrNull(),
            endTime = if (allDay) null else runCatching { LocalTime.parse(endText) }.getOrNull(),
            allDay = allDay,
            schedule = schedule,
        )

    LaunchedEffect(initialEvent?.uid, selectedCollectionHref, eventCollections) {
        if (initialEvent == null) {
            eventCollections.firstOrNull { it.href == selectedCollectionHref }
                ?.let { onDraftCollectionColorChanged(it.color) }
        }
    }

    AnimatedContent(
        targetState = locationPickerOpen,
        transitionSpec = {
            (slideInHorizontally(animationSpec = tween(MotionMedium, easing = MotionStandard)) { width -> width / 3 } + fadeIn(tween(MotionShort))) togetherWith
                (slideOutHorizontally(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) { width -> -width / 3 } + fadeOut(tween(MotionShort)))
        },
        label = "eventLocationPicker",
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
            if (!readOnlyRemote && eventCollections.isEmpty()) {
                EditorContainer(
                    title = headerTitle ?: stringResource(if (initialEvent == null) R.string.new_event else R.string.edit_event),
                    action = stringResource(R.string.save),
                    onAction = {},
                    onClose = onClose,
                    actionEnabled = false,
                    typeSwitch = {
                        EditorTypeSwitch(
                            eventSelected = true,
                            onSelectEvent = {},
                            onSelectTask = { onSwitchToTask(currentTransferDraft()) },
                        )
                    },
                ) {
                    NoWritableSourceNotice(
                        title = stringResource(R.string.no_writable_event_calendar_title),
                        body = stringResource(R.string.no_writable_event_calendar_body),
                        action = stringResource(R.string.add_calendar_source),
                        onAction = onOpenCalendarSources,
                    )
                }
            } else {
                EditorContainer(
                title = headerTitle ?: stringResource(if (initialEvent == null) R.string.new_event else R.string.edit_event),
                action = stringResource(R.string.save),
                onAction = {
                    // Never save an event without a calendar that can hold it.
                    if (selectedCollectionHref == null) return@EditorContainer
                    if (invalidTimeRange) {
                        invalidRangeDialogOpen = true
                        return@EditorContainer
                    }
                    onSave(
                        EventEditPayload(
                            title = title,
                            collectionHref = selectedCollectionHref,
                            date = LocalDate.parse(dateText),
                            endDate = LocalDate.parse(endDateText),
                            startTime = if (allDay) null else LocalTime.parse(startText),
                            endTime = if (allDay) null else LocalTime.parse(endText),
                            allDay = allDay,
                            description = description,
                            location = location,
                            locationMapVerified = locationMapVerified,
                            manualColor = manualColor,
                            recurrenceRule = recurrenceRule.ifBlank { null },
                            reminderMinutes = reminderMinutes.normalizedReminderOffsets(),
                            status = eventStatus,
                            classification = eventClassification,
                            transparency = eventTransparency,
                            categories = categories.ifBlank { null },
                            organizerJson = organizerJson,
                            attendeesJson = attendeesJson,
                        ),
                    )
                },
                onClose = onClose,
                typeSwitch = if (readOnlyRemote) null else {
                    {
                        EditorTypeSwitch(
                            eventSelected = true,
                            onSelectEvent = {},
                            onSelectTask = { onSwitchToTask(currentTransferDraft()) },
                        )
                    }
                },
            ) {
                if (readOnlyRemote) {
                    EditorSection {
                        ReadOnlyEditorNotice(title = title)
                    }
                } else {
                    EventTitleField(
                        value = title,
                        onValueChange = { title = it },
                        requestFocus = (keyboardAtSwitch ?: requestTitleFocus) && initialEvent == null,
                        focusAtOnce = keyboardAtSwitch != null,
                        modifier = Modifier.editorInset(),
                    )
                }
                if (!readOnlyRemote) {
                    EditorLabel(stringResource(R.string.calendar))
                    FadedHorizontalScrollRow(
                        contentPadding = PaddingValues(horizontal = EditorHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        initialScrollIndex = selectedCollectionIndex.takeIf { initialEvent != null && it >= 0 },
                        initialScrollKey = initialEvent?.resourceHref,
                    ) {
                        eventCollections.forEach { collection ->
                            CalendarSelectorChip(
                                collection = collection,
                                selected = collection.href == selectedCollectionHref,
                                onClick = { chosenCollectionHref = collection.href },
                                hidden = collection.href in state.collectionVisibility.hrefsHidingEvents,
                            )
                        }
                    }
                    EditorSection {
                        EditorLine(Icons.Default.AccessTime) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.all_day), color = WarmInk, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                                Switch(
                                    checked = allDay,
                                    onCheckedChange = {
                                        onScheduleChange(
                                            schedule.copy(
                                                allDay = it,
                                                hasStartTime = !it,
                                                hasEndTime = !it,
                                            ).recalculatePreview(),
                                        )
                                    },
                                )
                            }
                        }
                        EventScheduleEditor(schedule = schedule, onScheduleChange = onScheduleChange, endIsError = invalidTimeRange)
                    }
                }
                val selectedCollection = eventCollections.firstOrNull { it.href == selectedCollectionHref }
                val eventCapabilities = selectedCollection?.eventEditorCapabilities() ?: EventEditorCapabilities.Full
                val editableFields = if (readOnlyRemote) {
                    listOf("color")
                } else {
                    state.eventFieldOrder.filterNot { it == "time" }.filter { eventCapabilities.allows(it) }
                }
                // All further fields form one card, in the order chosen in the settings.
                if (editableFields.isNotEmpty()) EditorGroup {
                editableFields.forEach { field ->
                    when (field) {
                        "recurrence" -> CompactRecurrenceEditor(recurrenceRule = recurrenceRule, onRecurrenceRuleChange = { recurrenceRule = it.orEmpty() })
                        "reminders" -> CompactReminderEditor(selected = reminderMinutes, onSelectedChange = { reminderMinutes = it })
                        "location" -> LocationSelectorField(location = location, onClick = { locationPickerOpen = true })
                        "notes" -> EditorSection {
                            EditorLine(Icons.AutoMirrored.Filled.Notes, verticalAlignment = Alignment.Top) {
                                EditorPlainTextField(
                                    value = description,
                                    onValueChange = { description = it },
                                    placeholder = stringResource(R.string.notes),
                                    minLines = 2,
                                )
                            }
                        }
                        "status" -> EditorSection {
                            MetadataDropdownLine(stringResource(R.string.event_status), EventStatusOption.entries, eventStatus) { eventStatus = it }
                            MetadataDropdownLine(stringResource(R.string.sharing_visibility), EventClassOption.entries, eventClassification) { eventClassification = it }
                            MetadataDropdownLine(stringResource(R.string.availability), EventTransparencyOption.entries, eventTransparency) { eventTransparency = it }
                        }
                        "categories" -> TagEditor(stringResource(R.string.categories), categories.toCategoryTags(), knownCategories) { categories = it.toCategoriesCsv() }
                        "color" -> ColorOverrideEditor(
                            selectedColor = manualColor,
                            automaticColor = eventCollections.firstOrNull { it.href == selectedCollectionHref }?.color ?: WarmBrown.toArgb(),
                            onColorSelected = { manualColor = it },
                        )
                        "participants" -> EditorSection {
                            SharedEventEditor(organizerJson, attendeesJson) { attendeesJson = it }
                        }
                    }
                }
                }
            }
            }
        }
    }
    if (invalidRangeDialogOpen) {
        InvalidTimeRangeDialog(onDismiss = { invalidRangeDialogOpen = false }, message = R.string.invalid_event_time_range_message)
    }
}

@Composable
private fun EventTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
    focusAtOnce: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            // After a switch the title takes over the focus at once, so the keyboard stays up.
            if (!focusAtOnce) delay(220)
            focusRequester.requestFocus()
        }
    }
    EditorTitleField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.focusRequester(focusRequester),
    )
}
