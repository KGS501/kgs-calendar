package com.kgs.calendar.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.ConversionSource
import com.kgs.calendar.ui.CreationSheet
import com.kgs.calendar.ui.DetailSheet
import com.kgs.calendar.ui.DraftEventSelection
import com.kgs.calendar.ui.EditorTransferDraft
import com.kgs.calendar.ui.HiddenSaveKind
import com.kgs.calendar.ui.HiddenSaveNotice
import com.kgs.calendar.ui.RecurringSaveRequest
import com.kgs.calendar.ui.SettingsDestination
import com.kgs.calendar.ui.SheetSnap
import com.kgs.calendar.ui.editor.EditorDraftStore
import com.kgs.calendar.ui.editor.EditorSchedulePreview
import com.kgs.calendar.ui.editor.EditorScheduleState
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Navigation state of the app shell: which sheet, drawer, overlay or dialog is open, the task
 * detail back stack, the view history for back navigation and the draft behind the editor sheets.
 */
@Stable
internal class CalendarShellUiState(
    initialEditorSchedule: EditorScheduleState,
    initialWireframeColor: Int,
    private val editorDrafts: EditorDraftStore = EditorDraftStore(),
) {
    var createMenuOpen by mutableStateOf(false)
        private set
    var overdueTasksExpanded by mutableStateOf(false)
    private var creationSheetState by mutableStateOf<CreationSheet?>(null)

    /** Every editor that opens starts a new draft in [editorDrafts]; closing one ends its draft. */
    var creationSheet: CreationSheet?
        get() = creationSheetState
        private set(value) {
            if (value == creationSheetState) return
            editorDraftId?.let(editorDrafts::discard)
            editorDraftId = value?.let { editorDrafts.newDraft() }
            creationSheetState = value
        }

    /** Identifies the open editor's draft; only this ID is kept in the saved-state Bundle. */
    var editorDraftId by mutableStateOf<String?>(null)
        private set
    var detailSheet by mutableStateOf<DetailSheet?>(null)
        private set
    private val detailTaskStack = mutableStateListOf<TaskEntity>()
    val detailTaskBackStack: List<TaskEntity> get() = detailTaskStack
    var detailTaskMorphGeneration by mutableStateOf(0)
        private set
    var detailTaskMorphSourceHref by mutableStateOf<String?>(null)
        private set
    var searchOpen by mutableStateOf(false)
        private set
    var drawerOpen by mutableStateOf(false)
        private set
    var taskDrawerOpen by mutableStateOf(false)
        private set
    var completedTasksOpen by mutableStateOf(false)
        private set
    var settingsOpen by mutableStateOf(false)
        private set
    var settingsStartDestination by mutableStateOf(SettingsDestination.Main)
        private set
    var problemsOpen by mutableStateOf(false)
        private set
    var editingCollection by mutableStateOf<CollectionEntity?>(null)
        private set
    var editorSchedule by mutableStateOf(initialEditorSchedule)
    var draftWireframeColor by mutableStateOf(initialWireframeColor)
    var editorWireframeMode by mutableStateOf(false)
        private set
    private var transferDraftState by mutableStateOf<EditorTransferDraft?>(null)

    /** The draft carried over from the other editor type, stored with the open editor's draft. */
    var editorTransferDraft: EditorTransferDraft?
        get() = transferDraftState
        private set(value) {
            transferDraftState = value
            editorDraftId?.let { editorDrafts.put(it, TransferDraftKey, value?.toSaveable()) }
        }
    var creationCollapseRequest by mutableStateOf(0)
        private set
    var creationExpandRequest by mutableStateOf(0)
        private set
    var conversionSource by mutableStateOf<ConversionSource?>(null)
        private set
    var recurringSaveRequest by mutableStateOf<RecurringSaveRequest?>(null)
        private set
    var hiddenSaveNotice by mutableStateOf<HiddenSaveNotice?>(null)
        private set
    private val viewHistory = mutableStateListOf<CalendarViewMode>()

    /** Restored state that waits until the calendar data its sheets refer to has loaded. */
    private var pendingRestore by mutableStateOf<SavedShellState?>(null)

    val hasPendingRestore: Boolean get() = pendingRestore != null

    /** The editor drafts this state refers to, including one of a restore that still waits. */
    val referencedDraftIds: Set<String> get() = setOfNotNull(pendingRestore?.editorDraftId, editorDraftId)

    val anyOverlayOpen: Boolean
        get() = createMenuOpen || searchOpen || drawerOpen || taskDrawerOpen ||
            completedTasksOpen || settingsOpen || problemsOpen || editingCollection != null ||
            detailSheet != null || creationSheet != null

    val canNavigateBack: Boolean
        get() = anyOverlayOpen || viewHistory.isNotEmpty()

    /** Remembers [current] so that back returns to it after a view switch. */
    fun recordViewChange(current: CalendarViewMode) {
        if (viewHistory.lastOrNull() != current) {
            viewHistory.add(current)
        }
    }

    /**
     * Closes the topmost open overlay, otherwise steps back through the view history (e.g. Day ->
     * Month -> Multiple days) by passing the previous view to [selectView].
     */
    fun navigateBack(closeSearch: () -> Unit, selectView: (CalendarViewMode) -> Unit) {
        when {
            createMenuOpen -> createMenuOpen = false
            detailSheet is DetailSheet.Task && detailTaskStack.isNotEmpty() -> popDetailTask()
            detailSheet != null -> closeDetail()
            creationSheet != null -> creationSheet = null
            editingCollection != null -> editingCollection = null
            searchOpen -> closeSearch()
            taskDrawerOpen -> taskDrawerOpen = false
            drawerOpen -> drawerOpen = false
            completedTasksOpen -> completedTasksOpen = false
            problemsOpen -> problemsOpen = false
            settingsOpen -> settingsOpen = false
            viewHistory.isNotEmpty() -> selectView(viewHistory.removeAt(viewHistory.lastIndex))
        }
    }

    fun setCreateMenuExpanded(expanded: Boolean) {
        if (overdueTasksExpanded) {
            overdueTasksExpanded = false
            createMenuOpen = false
        } else {
            createMenuOpen = expanded
        }
    }

    fun openMenuDrawer() {
        createMenuOpen = false
        searchOpen = false
        creationSheet = null
        detailSheet = null
        editingCollection = null
        completedTasksOpen = false
        recurringSaveRequest = null
        editorWireframeMode = false
        drawerOpen = true
    }

    fun closeDrawer() {
        drawerOpen = false
    }

    fun openSearch() {
        drawerOpen = false
        taskDrawerOpen = false
        createMenuOpen = false
        searchOpen = true
    }

    /** Only hides the overlay; clearing the query is left to the caller. */
    fun closeSearch() {
        searchOpen = false
    }

    fun openTaskDrawer() {
        drawerOpen = false
        searchOpen = false
        createMenuOpen = false
        taskDrawerOpen = true
    }

    fun closeTaskDrawer() {
        taskDrawerOpen = false
    }

    fun openCompletedTasks() {
        completedTasksOpen = true
    }

    fun closeCompletedTasks() {
        completedTasksOpen = false
    }

    fun openProblems() {
        drawerOpen = false
        problemsOpen = true
    }

    fun closeProblems() {
        problemsOpen = false
    }

    fun openSettings(destination: SettingsDestination) {
        drawerOpen = false
        settingsStartDestination = destination
        settingsOpen = true
    }

    fun closeSettings() {
        settingsOpen = false
    }

    fun openAddCalendarSources() {
        creationSheet = null
        detailSheet = null
        detailTaskStack.clear()
        editingCollection = null
        taskDrawerOpen = false
        searchOpen = false
        openSettings(SettingsDestination.AddSource)
    }

    fun editCollection(collection: CollectionEntity) {
        editingCollection = collection
    }

    fun setEditingCollectionEnabled(enabled: Boolean) {
        editingCollection = editingCollection?.copy(isEnabled = enabled)
    }

    fun closeCollectionEditor() {
        editingCollection = null
    }

    fun openEventCreation(schedule: EditorScheduleState, wireframeColor: Int) {
        openCreation(CreationSheet.EventFull, schedule, wireframeColor)
    }

    fun openTaskCreation(schedule: EditorScheduleState, wireframeColor: Int) {
        openCreation(CreationSheet.Task, schedule, wireframeColor)
    }

    private fun openCreation(sheet: CreationSheet, schedule: EditorScheduleState, wireframeColor: Int) {
        editorSchedule = schedule
        draftWireframeColor = wireframeColor
        editorTransferDraft = null
        conversionSource = null
        createMenuOpen = false
        searchOpen = false
        drawerOpen = false
        taskDrawerOpen = false
        settingsOpen = false
        problemsOpen = false
        editingCollection = null
        detailSheet = null
        detailTaskStack.clear()
        creationSheet = sheet
    }

    /** A tap on an empty timeline or all-day slot: start (or move) the low event draft there. */
    fun selectDraftSlot(preview: EditorSchedulePreview, wireframeColor: Int) {
        editorWireframeMode = true
        if (creationSheet != null) creationCollapseRequest++
        editorSchedule = editorSchedule.applyTimelineChange(preview)
        draftWireframeColor = wireframeColor
        editorTransferDraft = null
        creationSheet = CreationSheet.EventLow
    }

    fun moveDraft(preview: EditorSchedulePreview) {
        editorSchedule = editorSchedule.applyTimelineChange(preview)
    }

    fun onDraftInteraction() {
        editorWireframeMode = true
        creationCollapseRequest++
    }

    fun requestCreationExpand() {
        creationExpandRequest++
    }

    fun onEditorSnapChanged(snap: SheetSnap) {
        editorWireframeMode = snap == SheetSnap.EditorTiny
    }

    /** The timeline wireframe of the open editor, or null when no editor draft is shown. */
    fun draftEventSelection(): DraftEventSelection? = editorSchedule.lastValidPreview?.let { preview ->
        when (creationSheet) {
            CreationSheet.EventLow,
            CreationSheet.EventFull,
            CreationSheet.TaskLow,
            CreationSheet.Task,
            -> DraftEventSelection(
                preview.date,
                preview.start,
                preview.end,
                draftWireframeColor,
                preview.allDay,
            )
            else -> null
        }
    }

    /** Switches between the event and task editor, carrying the typed draft over. */
    fun switchEditor(
        transfer: EditorTransferDraft,
        target: CreationSheet,
        conversion: ConversionSource?,
        today: LocalDate,
    ) {
        creationSheet = target
        editorTransferDraft = transfer
        editorSchedule = transfer.transferredSchedule(editorSchedule, today)
        conversionSource = conversion
    }

    fun closeCreationSheet() {
        creationSheet = null
    }

    /** Closes the editor after a save and forgets the item it converted. */
    fun finishCreation() {
        conversionSource = null
        creationSheet = null
    }

    /** Called once the creation sheet has gone: the wireframe and conversion end with it. */
    fun onCreationSheetClosed() {
        if (creationSheet == null) {
            editorWireframeMode = false
            conversionSource = null
        }
    }

    fun requestRecurringSave(request: RecurringSaveRequest) {
        recurringSaveRequest = request
    }

    fun dismissRecurringSave() {
        recurringSaveRequest = null
    }

    fun finishRecurringSave() {
        recurringSaveRequest = null
        creationSheet = null
    }

    /** Tells the user when a saved item landed in a calendar hidden from the views. */
    fun showHiddenSaveNotice(collectionHref: String?, kind: HiddenSaveKind, state: CalendarUiState) {
        val resolvedHref = collectionHref ?: when (kind) {
            HiddenSaveKind.Event -> state.defaultEventCollectionHref
            HiddenSaveKind.Task -> state.defaultTaskCollectionHref
        }
        if (resolvedHref != null && resolvedHref in state.hiddenCollectionHrefs) {
            hiddenSaveNotice = HiddenSaveNotice(resolvedHref, kind)
        }
    }

    fun dismissHiddenSaveNotice() {
        hiddenSaveNotice = null
    }

    fun openDetail(detail: DetailSheet) {
        detailSheet = detail
    }

    /** Opens a task detail without a subtask morph. */
    fun openTaskDetail(task: TaskEntity) {
        detailTaskMorphGeneration = 0
        detailTaskMorphSourceHref = null
        detailSheet = DetailSheet.Task(task)
    }

    fun openProblemEventDetail(event: EventEntity) {
        problemsOpen = false
        detailTaskStack.clear()
        detailSheet = DetailSheet.Event(event)
    }

    fun openProblemTaskDetail(task: TaskEntity) {
        problemsOpen = false
        detailTaskStack.clear()
        openTaskDetail(task)
    }

    /** A widget, notification or external launch opens a detail on top of a clean shell. */
    fun openLaunchedDetail(detail: DetailSheet) {
        createMenuOpen = false
        searchOpen = false
        drawerOpen = false
        taskDrawerOpen = false
        settingsOpen = false
        problemsOpen = false
        editingCollection = null
        creationSheet = null
        detailTaskStack.clear()
        detailTaskMorphGeneration = 0
        detailTaskMorphSourceHref = null
        detailSheet = detail
    }

    fun closeDetail() {
        detailSheet = null
        detailTaskStack.clear()
    }

    /** Back inside the detail sheet: return to the parent task, otherwise close. */
    fun navigateDetailBack() {
        if (detailSheet is DetailSheet.Task && detailTaskStack.isNotEmpty()) {
            popDetailTask()
        } else {
            closeDetail()
        }
    }

    private fun popDetailTask() {
        detailTaskMorphSourceHref = (detailSheet as? DetailSheet.Task)?.task?.resourceHref
        detailTaskMorphGeneration++
        detailSheet = DetailSheet.Task(detailTaskStack.removeAt(detailTaskStack.lastIndex))
    }

    fun openSubtask(parent: TaskEntity, child: TaskEntity) {
        detailTaskStack.add(parent)
        detailTaskMorphSourceHref = parent.resourceHref
        detailTaskMorphGeneration++
        detailSheet = DetailSheet.Task(child)
    }

    fun openParentTask(parent: TaskEntity) {
        val stackIndex = detailTaskStack.indexOfLast { it.resourceHref == parent.resourceHref }
        if (stackIndex >= 0) {
            while (detailTaskStack.size > stackIndex) {
                detailTaskStack.removeAt(detailTaskStack.lastIndex)
            }
        } else {
            detailTaskStack.clear()
        }
        detailTaskMorphSourceHref = (detailSheet as? DetailSheet.Task)?.task?.resourceHref
        detailTaskMorphGeneration++
        detailSheet = DetailSheet.Task(parent)
    }

    /** Called once the detail sheet has gone so the next one starts without a morph. */
    fun onDetailSheetClosed() {
        if (detailSheet == null) detailTaskMorphGeneration = 0
    }

    fun editEvent(event: EventEntity, schedule: EditorScheduleState) {
        editorSchedule = schedule
        creationSheet = CreationSheet.EditEvent(event)
        detailSheet = null
    }

    fun duplicateEvent(event: EventEntity, schedule: EditorScheduleState) {
        editorSchedule = schedule
        creationSheet = CreationSheet.DuplicateEvent(event)
        detailSheet = null
    }

    fun editTask(task: TaskEntity, schedule: EditorScheduleState) {
        editorSchedule = schedule
        creationSheet = CreationSheet.EditTask(task)
        closeDetail()
    }

    fun duplicateTask(task: TaskEntity, schedule: EditorScheduleState) {
        editorSchedule = schedule
        creationSheet = CreationSheet.DuplicateTask(task)
        closeDetail()
    }

    fun addSubtask(parent: TaskEntity, schedule: EditorScheduleState) {
        editorSchedule = schedule
        closeDetail()
        creationSheet = CreationSheet.TaskForParent(parent)
    }

    fun snapshot(): SavedShellState = pendingRestore ?: SavedShellState(
        createMenuOpen = createMenuOpen,
        overdueTasksExpanded = overdueTasksExpanded,
        searchOpen = searchOpen,
        drawerOpen = drawerOpen,
        taskDrawerOpen = taskDrawerOpen,
        completedTasksOpen = completedTasksOpen,
        settingsOpen = settingsOpen,
        settingsStartDestination = settingsStartDestination,
        problemsOpen = problemsOpen,
        editingCollectionHref = editingCollection?.href,
        creationSheet = creationSheet?.toSaved(),
        detailSheet = detailSheet?.savedRef(),
        detailTaskBackStack = detailTaskStack.map { it.savedRef() },
        editorSchedule = editorSchedule,
        draftWireframeColor = draftWireframeColor,
        editorWireframeMode = editorWireframeMode,
        editorDraftId = editorDraftId,
        conversionSource = conversionSource?.savedRef(),
        hiddenSaveNotice = hiddenSaveNotice,
        viewHistory = viewHistory.toList(),
    )

    /**
     * Reopens what [saved] had open, looking its items up in [state]. Before the first data load
     * this waits in [hasPendingRestore] for [applyPendingRestore]. A sheet whose item is gone stays
     * closed; an editor that converted an item that is gone stays closed as well.
     */
    fun restore(saved: SavedShellState, state: CalendarUiState) {
        if (!state.initialDataLoaded) {
            pendingRestore = saved
            return
        }
        pendingRestore = null
        createMenuOpen = saved.createMenuOpen
        overdueTasksExpanded = saved.overdueTasksExpanded
        searchOpen = saved.searchOpen
        drawerOpen = saved.drawerOpen
        taskDrawerOpen = saved.taskDrawerOpen
        completedTasksOpen = saved.completedTasksOpen
        settingsOpen = saved.settingsOpen
        settingsStartDestination = saved.settingsStartDestination
        problemsOpen = saved.problemsOpen
        editingCollection = saved.editingCollectionHref?.let { href ->
            state.collections.firstOrNull { it.href == href }
        }
        val conversion = saved.conversionSource?.resolveConversion(state)
        val restoredSheet = saved.creationSheet?.resolve(state)
            ?.takeIf { saved.conversionSource == null || conversion != null }
        // The editor continues its saved draft; a draft whose editor cannot come back is dropped.
        val previousDraftId = editorDraftId
        creationSheetState = restoredSheet
        editorDraftId = if (restoredSheet != null) {
            saved.editorDraftId ?: editorDrafts.newDraft()
        } else {
            saved.editorDraftId?.let(editorDrafts::discard)
            null
        }
        if (previousDraftId != null && previousDraftId != editorDraftId) editorDrafts.discard(previousDraftId)
        conversionSource = conversion
        editorSchedule = saved.editorSchedule
        draftWireframeColor = saved.draftWireframeColor
        editorWireframeMode = saved.editorWireframeMode
        transferDraftState = editorDraftId
            ?.let { editorDrafts.values(it)[TransferDraftKey] }
            ?.let(::transferDraftFromSaveable)
        detailSheet = saved.detailSheet?.resolveDetail(state)
        detailTaskStack.clear()
        if (detailSheet != null) {
            detailTaskStack.addAll(saved.detailTaskBackStack.mapNotNull(state::findTask))
        }
        hiddenSaveNotice = saved.hiddenSaveNotice
        viewHistory.clear()
        viewHistory.addAll(saved.viewHistory)
    }

    /** Whether a pending restore would find every item it refers to in [state]. */
    fun canResolvePendingRestore(state: CalendarUiState): Boolean =
        state.initialDataLoaded && pendingRestore?.canResolveAll(state) != false

    fun applyPendingRestore(state: CalendarUiState) {
        pendingRestore?.let { restore(it, state) }
    }

    companion object {
        /** Saves by value; on restore, [currentState] resolves the items the sheets refer to. */
        fun saver(
            today: LocalDate,
            defaultWireframeColor: Int,
            editorDrafts: EditorDraftStore,
            currentState: () -> CalendarUiState,
        ): Saver<CalendarShellUiState, Any> = Saver(
            save = { it.snapshot().toSaveable() },
            restore = { value ->
                CalendarShellUiState(initialEditorSchedule(today), defaultWireframeColor, editorDrafts).apply {
                    SavedShellState.fromSaveable(value)?.let { restore(it, currentState()) }
                }
            },
        )
    }
}

/**
 * The shell state, kept across activity recreation. After a rotation the ViewModel still holds
 * the calendar data, so the open sheets come back in the first frame; after process death they
 * come back once the data they refer to has loaded (or [PendingRestoreTimeoutMillis] later).
 */
@Composable
internal fun rememberCalendarShellUiState(
    state: CalendarUiState,
    today: LocalDate,
    defaultWireframeColor: Int,
    editorDrafts: EditorDraftStore,
): CalendarShellUiState {
    val currentState by rememberUpdatedState(state)
    val shell = rememberSaveable(
        saver = CalendarShellUiState.saver(today, defaultWireframeColor, editorDrafts) { currentState },
    ) {
        CalendarShellUiState(initialEditorSchedule(today), defaultWireframeColor, editorDrafts)
    }
    LaunchedEffect(shell) {
        editorDrafts.onShellRestored(shell.referencedDraftIds)
    }
    if (shell.hasPendingRestore) {
        LaunchedEffect(shell) {
            withTimeoutOrNull(PendingRestoreTimeoutMillis) {
                snapshotFlow { currentState }.first(shell::canResolvePendingRestore)
            }
            snapshotFlow { currentState }.first { it.initialDataLoaded }
            shell.applyPendingRestore(currentState)
        }
    }
    return shell
}

private const val PendingRestoreTimeoutMillis = 5_000L

private const val TransferDraftKey = "transferDraft"
