package com.kgs.calendar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kgs.calendar.R
import com.kgs.calendar.domain.source.isReadOnlyCollection
import com.kgs.calendar.ui.shell.CalendarShellUiState
import java.time.LocalDate

/** The event and task editor sheets: the low timeline draft and the full editors. */
@Composable
internal fun CreationSheetHost(
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    shell: CalendarShellUiState,
    today: LocalDate,
    showHiddenSaveNotice: (collectionHref: String?, kind: HiddenSaveKind) -> Unit,
) {
    val creationSheet = shell.creationSheet
    if (creationSheet == CreationSheet.EventLow || creationSheet == CreationSheet.TaskLow) {
        KgsModalBottomSheet(
            onDismissRequest = shell::closeCreationSheet,
            containerColor = MaterialTheme.colorScheme.surface,
            initialSnap = SheetSnap.EditorTiny,
            dimBackground = false,
            dismissOnOutsideTap = false,
            collapseRequest = shell.creationCollapseRequest,
            expandRequest = shell.creationExpandRequest,
            collapseSnap = SheetSnap.EditorTiny,
            anchorMode = SheetAnchorMode.Editor,
            onSnapChanged = shell::onEditorSnapChanged,
            separationShadow = true,
        ) {
            when (shell.creationSheet) {
                CreationSheet.EventLow -> EventEditorSheet(
                    state = state,
                    schedule = shell.editorSchedule,
                    onScheduleChange = { shell.editorSchedule = it },
                    expanded = false,
                    initialEvent = null,
                    transferDraft = shell.editorTransferDraft,
                    onDraftCollectionColorChanged = { shell.draftWireframeColor = it },
                    requestTitleFocus = state.focusTitleOnCreate,
                    onSave = { payload ->
                        viewModel.edits.createEvent(payload)
                        showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Event)
                        shell.finishCreation()
                    },
                    onSwitchToTask = { transfer ->
                        shell.switchEditor(
                            transfer = transfer.withDestinationReminderDefaults(state.defaultTaskReminderMinutes),
                            target = CreationSheet.TaskLow,
                            conversion = null,
                            today = today,
                        )
                    },
                    onOpenCalendarSources = shell::openAddCalendarSources,
                    onClose = shell::closeCreationSheet,
                )
                CreationSheet.TaskLow -> TaskEditorSheet(
                    state = state,
                    schedule = shell.editorSchedule,
                    onScheduleChange = { shell.editorSchedule = it },
                    requestTitleFocus = state.focusTitleOnCreate,
                    initialTask = null,
                    transferDraft = shell.editorTransferDraft,
                    onDraftCollectionColorChanged = { shell.draftWireframeColor = it },
                    onSave = { payload ->
                        viewModel.edits.createTask(payload)
                        showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Task)
                        shell.finishCreation()
                    },
                    onSwitchToEvent = { transfer ->
                        shell.switchEditor(
                            transfer = transfer.withDestinationReminderDefaults(state.defaultEventReminderMinutes),
                            target = CreationSheet.EventLow,
                            conversion = null,
                            today = today,
                        )
                    },
                    onOpenCalendarSources = shell::openAddCalendarSources,
                    onClose = shell::closeCreationSheet,
                )
                else -> Unit
            }
        }
    } else creationSheet?.let { sheet ->
        KgsModalBottomSheet(
            onDismissRequest = shell::closeCreationSheet,
            containerColor = MaterialTheme.colorScheme.surface,
            dimBackground = false,
            dismissOnOutsideTap = false,
            collapseRequest = 0,
            anchorMode = SheetAnchorMode.Editor,
            separationShadow = sheet == CreationSheet.EventFull || sheet == CreationSheet.Task,
            initialSnap = when (sheet) {
                CreationSheet.EventLow,
                CreationSheet.TaskLow,
                -> SheetSnap.Half
                CreationSheet.EventFull,
                is CreationSheet.EditEvent,
                is CreationSheet.DuplicateEvent,
                CreationSheet.Task,
                is CreationSheet.TaskForParent,
                is CreationSheet.EditTask,
                is CreationSheet.DuplicateTask,
                -> SheetSnap.Expanded
            },
        ) {
            when (sheet) {
                CreationSheet.EventLow,
                CreationSheet.TaskLow,
                -> Unit
                CreationSheet.EventFull,
                -> EventEditorSheet(
                    state = state,
                    schedule = shell.editorSchedule,
                    onScheduleChange = { shell.editorSchedule = it },
                    expanded = sheet == CreationSheet.EventFull,
                    initialEvent = null,
                    transferDraft = shell.editorTransferDraft,
                    onDraftCollectionColorChanged = { shell.draftWireframeColor = it },
                    requestTitleFocus = state.focusTitleOnCreate,
                    onSave = { payload ->
                        when (val source = shell.conversionSource) {
                            is ConversionSource.Task -> viewModel.edits.convertTaskToEvent(source.task.resourceHref, payload)
                            else -> viewModel.edits.createEvent(payload)
                        }
                        showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Event)
                        shell.finishCreation()
                    },
                    onSwitchToTask = { transfer ->
                        shell.switchEditor(
                            transfer = transfer.withDestinationReminderDefaults(state.defaultTaskReminderMinutes),
                            target = CreationSheet.Task,
                            conversion = null,
                            today = today,
                        )
                    },
                    onOpenCalendarSources = shell::openAddCalendarSources,
                    onClose = shell::closeCreationSheet,
                )
                is CreationSheet.EditEvent -> EventEditorSheet(
                    state = state,
                    schedule = shell.editorSchedule,
                    onScheduleChange = { shell.editorSchedule = it },
                    expanded = true,
                    initialEvent = sheet.event,
                    readOnlyRemote = state.collections.firstOrNull { it.href == sheet.event.collectionHref }?.isReadOnlyCollection() == true,
                    transferDraft = null,
                    requestTitleFocus = false,
                    onSave = { payload ->
                        if (state.collections.firstOrNull { it.href == sheet.event.collectionHref }?.isReadOnlyCollection() == true) {
                            viewModel.edits.updateEventManualColor(sheet.event.resourceHref, payload.manualColor)
                            showHiddenSaveNotice(sheet.event.collectionHref, HiddenSaveKind.Event)
                            shell.closeCreationSheet()
                        } else if (!sheet.event.recurrenceRule.isNullOrBlank() || sheet.event.isRecurring) {
                            shell.requestRecurringSave(RecurringSaveRequest.Event(sheet.event, payload))
                        } else {
                            viewModel.edits.updateEvent(sheet.event.resourceHref, payload)
                            showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Event)
                            shell.closeCreationSheet()
                        }
                    },
                    onSwitchToTask = { transfer ->
                        shell.switchEditor(
                            transfer = transfer.withDestinationReminderDefaults(state.defaultTaskReminderMinutes),
                            target = CreationSheet.Task,
                            conversion = ConversionSource.Event(sheet.event),
                            today = today,
                        )
                    },
                    onOpenCalendarSources = shell::openAddCalendarSources,
                    onClose = shell::closeCreationSheet,
                )
                is CreationSheet.DuplicateEvent -> EventEditorSheet(
                    state = state,
                    schedule = shell.editorSchedule,
                    onScheduleChange = { shell.editorSchedule = it },
                    expanded = true,
                    initialEvent = sheet.event,
                    transferDraft = null,
                    requestTitleFocus = state.focusTitleOnCreate,
                    headerTitle = stringResource(R.string.duplicate_event),
                    onSave = { payload ->
                        viewModel.edits.createEvent(payload)
                        showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Event)
                        shell.finishCreation()
                    },
                    onSwitchToTask = { transfer ->
                        shell.switchEditor(
                            transfer = transfer.withDestinationReminderDefaults(state.defaultTaskReminderMinutes),
                            target = CreationSheet.Task,
                            conversion = null,
                            today = today,
                        )
                    },
                    onOpenCalendarSources = shell::openAddCalendarSources,
                    onClose = shell::closeCreationSheet,
                )
                CreationSheet.Task -> TaskEditorSheet(
                    state = state,
                    schedule = shell.editorSchedule,
                    onScheduleChange = { shell.editorSchedule = it },
                    requestTitleFocus = state.focusTitleOnCreate,
                    initialTask = null,
                    transferDraft = shell.editorTransferDraft,
                    onDraftCollectionColorChanged = { shell.draftWireframeColor = it },
                    onSave = { payload ->
                        when (val source = shell.conversionSource) {
                            is ConversionSource.Event -> viewModel.edits.convertEventToTask(source.event.resourceHref, payload)
                            else -> viewModel.edits.createTask(payload)
                        }
                        showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Task)
                        shell.finishCreation()
                    },
                    onSwitchToEvent = { transfer ->
                        shell.switchEditor(
                            transfer = transfer.withDestinationReminderDefaults(state.defaultEventReminderMinutes),
                            target = CreationSheet.EventFull,
                            conversion = null,
                            today = today,
                        )
                    },
                    onOpenCalendarSources = shell::openAddCalendarSources,
                    onClose = shell::closeCreationSheet,
                )
                is CreationSheet.TaskForParent -> TaskEditorSheet(
                    state = state,
                    schedule = shell.editorSchedule,
                    onScheduleChange = { shell.editorSchedule = it },
                    requestTitleFocus = state.focusTitleOnCreate,
                    initialTask = null,
                    forcedParentTask = sheet.parent,
                    headerTitle = stringResource(R.string.add_subtask),
                    onSave = { payload ->
                        viewModel.edits.createTask(payload)
                        showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Task)
                        shell.closeCreationSheet()
                    },
                    onSwitchToEvent = {},
                    onOpenCalendarSources = shell::openAddCalendarSources,
                    onClose = shell::closeCreationSheet,
                )
                is CreationSheet.EditTask -> TaskEditorSheet(
                    state = state,
                    schedule = shell.editorSchedule,
                    onScheduleChange = { shell.editorSchedule = it },
                    requestTitleFocus = false,
                    initialTask = sheet.task,
                    readOnlyRemote = state.collections.firstOrNull { it.href == sheet.task.collectionHref }?.isReadOnlyCollection() == true,
                    transferDraft = null,
                    onSave = { payload ->
                        if (state.collections.firstOrNull { it.href == sheet.task.collectionHref }?.isReadOnlyCollection() == true) {
                            viewModel.edits.updateTaskManualColor(sheet.task.resourceHref, payload.manualColor)
                            showHiddenSaveNotice(sheet.task.collectionHref, HiddenSaveKind.Task)
                            shell.closeCreationSheet()
                        } else if (!sheet.task.recurrenceRule.isNullOrBlank()) {
                            shell.requestRecurringSave(RecurringSaveRequest.Task(sheet.task, payload))
                        } else {
                            viewModel.edits.updateTask(sheet.task.resourceHref, payload)
                            showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Task)
                            shell.closeCreationSheet()
                        }
                    },
                    onSwitchToEvent = { transfer ->
                        shell.switchEditor(
                            transfer = transfer.withDestinationReminderDefaults(state.defaultEventReminderMinutes),
                            target = CreationSheet.EventFull,
                            conversion = ConversionSource.Task(sheet.task),
                            today = today,
                        )
                    },
                    onOpenCalendarSources = shell::openAddCalendarSources,
                    onClose = shell::closeCreationSheet,
                )
                is CreationSheet.DuplicateTask -> TaskEditorSheet(
                    state = state,
                    schedule = shell.editorSchedule,
                    onScheduleChange = { shell.editorSchedule = it },
                    requestTitleFocus = state.focusTitleOnCreate,
                    initialTask = sheet.task,
                    transferDraft = null,
                    headerTitle = stringResource(R.string.duplicate_task),
                    onSave = { payload ->
                        viewModel.edits.createTask(payload)
                        showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Task)
                        shell.finishCreation()
                    },
                    onSwitchToEvent = { transfer ->
                        shell.switchEditor(
                            transfer = transfer.withDestinationReminderDefaults(state.defaultEventReminderMinutes),
                            target = CreationSheet.EventFull,
                            conversion = null,
                            today = today,
                        )
                    },
                    onOpenCalendarSources = shell::openAddCalendarSources,
                    onClose = shell::closeCreationSheet,
                )
            }
        }
    }
}
