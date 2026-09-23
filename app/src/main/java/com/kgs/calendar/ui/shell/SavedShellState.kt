package com.kgs.calendar.ui.shell

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.ConversionSource
import com.kgs.calendar.ui.CreationSheet
import com.kgs.calendar.ui.DetailSheet
import com.kgs.calendar.ui.EditorTransferDraft
import com.kgs.calendar.ui.HiddenSaveKind
import com.kgs.calendar.ui.HiddenSaveNotice
import com.kgs.calendar.ui.SettingsDestination
import com.kgs.calendar.ui.editor.EditorSchedulePreview
import com.kgs.calendar.ui.editor.EditorScheduleState
import com.kgs.calendar.ui.model.occurrenceStartForEdit
import java.time.LocalDate
import java.time.LocalTime

/**
 * An item a sheet refers to, saved by identity instead of as a whole Room entity and looked up
 * again in the loaded calendar data on restore.
 */
internal sealed interface SavedItemRef {
    val resourceHref: String

    data class Event(override val resourceHref: String, val occurrenceStart: Long) : SavedItemRef

    /** [occurrenceStart] is only kept for recurring tasks; an undated task has no stable start. */
    data class Task(override val resourceHref: String, val occurrenceStart: Long?) : SavedItemRef
}

internal fun EventEntity.savedRef(): SavedItemRef.Event =
    SavedItemRef.Event(resourceHref, occurrenceStartForEdit())

internal fun TaskEntity.savedRef(): SavedItemRef.Task =
    SavedItemRef.Task(resourceHref, occurrenceStartForEdit().takeUnless { recurrenceRule.isNullOrBlank() })

internal fun CalendarUiState.findEvent(ref: SavedItemRef.Event): EventEntity? =
    (events.asSequence() + searchResults.asSequence() + problemEvents.asSequence())
        .firstOrNull { it.resourceHref == ref.resourceHref && it.occurrenceStartForEdit() == ref.occurrenceStart }

internal fun CalendarUiState.findTask(ref: SavedItemRef.Task): TaskEntity? =
    (datedTasks.asSequence() + allTasks.asSequence()).firstOrNull {
        it.resourceHref == ref.resourceHref &&
            (ref.occurrenceStart == null || it.occurrenceStartForEdit() == ref.occurrenceStart)
    }

internal fun CalendarUiState.canFind(ref: SavedItemRef): Boolean = when (ref) {
    is SavedItemRef.Event -> findEvent(ref) != null
    is SavedItemRef.Task -> findTask(ref) != null
}

internal data class SavedCreationSheet(
    val kind: Kind,
    val item: SavedItemRef? = null,
) {
    enum class Kind {
        EventLow,
        EventFull,
        TaskLow,
        Task,
        TaskForParent,
        EditEvent,
        EditTask,
        DuplicateEvent,
        DuplicateTask,
    }
}

internal fun CreationSheet.toSaved(): SavedCreationSheet = when (this) {
    CreationSheet.EventLow -> SavedCreationSheet(SavedCreationSheet.Kind.EventLow)
    CreationSheet.EventFull -> SavedCreationSheet(SavedCreationSheet.Kind.EventFull)
    CreationSheet.TaskLow -> SavedCreationSheet(SavedCreationSheet.Kind.TaskLow)
    CreationSheet.Task -> SavedCreationSheet(SavedCreationSheet.Kind.Task)
    is CreationSheet.TaskForParent -> SavedCreationSheet(SavedCreationSheet.Kind.TaskForParent, parent.savedRef())
    is CreationSheet.EditEvent -> SavedCreationSheet(SavedCreationSheet.Kind.EditEvent, event.savedRef())
    is CreationSheet.EditTask -> SavedCreationSheet(SavedCreationSheet.Kind.EditTask, task.savedRef())
    is CreationSheet.DuplicateEvent -> SavedCreationSheet(SavedCreationSheet.Kind.DuplicateEvent, event.savedRef())
    is CreationSheet.DuplicateTask -> SavedCreationSheet(SavedCreationSheet.Kind.DuplicateTask, task.savedRef())
}

/** The sheet again, or null when the item it edits is no longer part of the calendar data. */
internal fun SavedCreationSheet.resolve(state: CalendarUiState): CreationSheet? {
    val event = (item as? SavedItemRef.Event)?.let(state::findEvent)
    val task = (item as? SavedItemRef.Task)?.let(state::findTask)
    return when (kind) {
        SavedCreationSheet.Kind.EventLow -> CreationSheet.EventLow
        SavedCreationSheet.Kind.EventFull -> CreationSheet.EventFull
        SavedCreationSheet.Kind.TaskLow -> CreationSheet.TaskLow
        SavedCreationSheet.Kind.Task -> CreationSheet.Task
        SavedCreationSheet.Kind.TaskForParent -> task?.let(CreationSheet::TaskForParent)
        SavedCreationSheet.Kind.EditEvent -> event?.let(CreationSheet::EditEvent)
        SavedCreationSheet.Kind.EditTask -> task?.let(CreationSheet::EditTask)
        SavedCreationSheet.Kind.DuplicateEvent -> event?.let(CreationSheet::DuplicateEvent)
        SavedCreationSheet.Kind.DuplicateTask -> task?.let(CreationSheet::DuplicateTask)
    }
}

internal fun DetailSheet.savedRef(): SavedItemRef = when (this) {
    is DetailSheet.Event -> event.savedRef()
    is DetailSheet.Task -> task.savedRef()
}

internal fun SavedItemRef.resolveDetail(state: CalendarUiState): DetailSheet? = when (this) {
    is SavedItemRef.Event -> state.findEvent(this)?.let(DetailSheet::Event)
    is SavedItemRef.Task -> state.findTask(this)?.let(DetailSheet::Task)
}

internal fun ConversionSource.savedRef(): SavedItemRef = when (this) {
    is ConversionSource.Event -> event.savedRef()
    is ConversionSource.Task -> task.savedRef()
}

internal fun SavedItemRef.resolveConversion(state: CalendarUiState): ConversionSource? = when (this) {
    is SavedItemRef.Event -> state.findEvent(this)?.let(ConversionSource::Event)
    is SavedItemRef.Task -> state.findTask(this)?.let(ConversionSource::Task)
}

/**
 * Everything of [CalendarShellUiState] that survives activity recreation. Animation requests and
 * the recurring-save scope dialog are left out on purpose: the sheets reopen at their initial snap
 * and a pending recurring save is simply asked for again.
 */
internal data class SavedShellState(
    val createMenuOpen: Boolean = false,
    val overdueTasksExpanded: Boolean = false,
    val searchOpen: Boolean = false,
    val drawerOpen: Boolean = false,
    val taskDrawerOpen: Boolean = false,
    val completedTasksOpen: Boolean = false,
    val settingsOpen: Boolean = false,
    val settingsStartDestination: SettingsDestination = SettingsDestination.Main,
    val problemsOpen: Boolean = false,
    val editingCollectionHref: String? = null,
    val creationSheet: SavedCreationSheet? = null,
    val detailSheet: SavedItemRef? = null,
    val detailTaskBackStack: List<SavedItemRef.Task> = emptyList(),
    val editorSchedule: EditorScheduleState,
    val draftWireframeColor: Int,
    val editorWireframeMode: Boolean = false,
    val editorTransferDraft: EditorTransferDraft? = null,
    val conversionSource: SavedItemRef? = null,
    val hiddenSaveNotice: HiddenSaveNotice? = null,
    val viewHistory: List<CalendarViewMode> = emptyList(),
) {
    /** Whether every item the saved sheets refer to is part of [state]. */
    fun canResolveAll(state: CalendarUiState): Boolean =
        listOfNotNull(creationSheet?.item, detailSheet, conversionSource)
            .plus(detailTaskBackStack)
            .all(state::canFind)

    /** Plain lists, maps, strings and numbers only, so that it fits into a saved-state Bundle. */
    fun toSaveable(): Map<String, Any?> = hashMapOf(
        "createMenuOpen" to createMenuOpen,
        "overdueTasksExpanded" to overdueTasksExpanded,
        "searchOpen" to searchOpen,
        "drawerOpen" to drawerOpen,
        "taskDrawerOpen" to taskDrawerOpen,
        "completedTasksOpen" to completedTasksOpen,
        "settingsOpen" to settingsOpen,
        "settingsStartDestination" to settingsStartDestination.name,
        "problemsOpen" to problemsOpen,
        "editingCollectionHref" to editingCollectionHref,
        "creationSheet" to creationSheet?.let { arrayListOf(it.kind.name, it.item?.toSaveable()) },
        "detailSheet" to detailSheet?.toSaveable(),
        "detailTaskBackStack" to ArrayList(detailTaskBackStack.map { it.toSaveable() }),
        "editorSchedule" to editorSchedule.toSaveable(),
        "draftWireframeColor" to draftWireframeColor,
        "editorWireframeMode" to editorWireframeMode,
        "editorTransferDraft" to editorTransferDraft?.toSaveable(),
        "conversionSource" to conversionSource?.toSaveable(),
        "hiddenSaveNotice" to hiddenSaveNotice?.let { arrayListOf(it.collectionHref, it.kind.name) },
        "viewHistory" to ArrayList(viewHistory.map { it.name }),
    )

    companion object {
        fun fromSaveable(value: Any?): SavedShellState? {
            val map = value as? Map<*, *> ?: return null
            val editorSchedule = map["editorSchedule"]?.let(::editorScheduleFromSaveable) ?: return null
            val draftWireframeColor = (map["draftWireframeColor"] as? Number)?.toInt() ?: return null
            return SavedShellState(
                createMenuOpen = map["createMenuOpen"] == true,
                overdueTasksExpanded = map["overdueTasksExpanded"] == true,
                searchOpen = map["searchOpen"] == true,
                drawerOpen = map["drawerOpen"] == true,
                taskDrawerOpen = map["taskDrawerOpen"] == true,
                completedTasksOpen = map["completedTasksOpen"] == true,
                settingsOpen = map["settingsOpen"] == true,
                settingsStartDestination = enumValueOrNull<SettingsDestination>(map["settingsStartDestination"])
                    ?: SettingsDestination.Main,
                problemsOpen = map["problemsOpen"] == true,
                editingCollectionHref = map["editingCollectionHref"] as? String,
                creationSheet = (map["creationSheet"] as? List<*>)?.let { saved ->
                    enumValueOrNull<SavedCreationSheet.Kind>(saved.getOrNull(0))?.let { kind ->
                        SavedCreationSheet(kind, itemRefFromSaveable(saved.getOrNull(1)))
                    }
                },
                detailSheet = itemRefFromSaveable(map["detailSheet"]),
                detailTaskBackStack = (map["detailTaskBackStack"] as? List<*>).orEmpty()
                    .mapNotNull { itemRefFromSaveable(it) as? SavedItemRef.Task },
                editorSchedule = editorSchedule,
                draftWireframeColor = draftWireframeColor,
                editorWireframeMode = map["editorWireframeMode"] == true,
                editorTransferDraft = map["editorTransferDraft"]?.let(::transferDraftFromSaveable),
                conversionSource = itemRefFromSaveable(map["conversionSource"]),
                hiddenSaveNotice = (map["hiddenSaveNotice"] as? List<*>)?.let { saved ->
                    val href = saved.getOrNull(0) as? String
                    val kind = enumValueOrNull<HiddenSaveKind>(saved.getOrNull(1))
                    if (href != null && kind != null) HiddenSaveNotice(href, kind) else null
                },
                viewHistory = (map["viewHistory"] as? List<*>).orEmpty()
                    .mapNotNull { enumValueOrNull<CalendarViewMode>(it) },
            )
        }
    }
}

private fun SavedItemRef.toSaveable(): ArrayList<Any?> = when (this) {
    is SavedItemRef.Event -> arrayListOf("event", resourceHref, occurrenceStart)
    is SavedItemRef.Task -> arrayListOf("task", resourceHref, occurrenceStart)
}

private fun itemRefFromSaveable(value: Any?): SavedItemRef? {
    val saved = value as? List<*> ?: return null
    val href = saved.getOrNull(1) as? String ?: return null
    val occurrenceStart = (saved.getOrNull(2) as? Number)?.toLong()
    return when (saved.getOrNull(0)) {
        "event" -> occurrenceStart?.let { SavedItemRef.Event(href, it) }
        "task" -> SavedItemRef.Task(href, occurrenceStart)
        else -> null
    }
}

internal fun EditorScheduleState.toSaveable(): Map<String, Any?> = hashMapOf(
    "startDateText" to startDateText,
    "endDateText" to endDateText,
    "startTimeText" to startTimeText,
    "endTimeText" to endTimeText,
    "hasStartDate" to hasStartDate,
    "hasEndDate" to hasEndDate,
    "hasStartTime" to hasStartTime,
    "hasEndTime" to hasEndTime,
    "allDay" to allDay,
    "lastValidPreview" to lastValidPreview?.let {
        arrayListOf(it.date.toString(), it.start.toString(), it.end.toString(), it.allDay)
    },
)

internal fun editorScheduleFromSaveable(value: Any?): EditorScheduleState? {
    val map = value as? Map<*, *> ?: return null
    return EditorScheduleState(
        startDateText = map["startDateText"] as? String ?: return null,
        endDateText = map["endDateText"] as? String ?: return null,
        startTimeText = map["startTimeText"] as? String ?: return null,
        endTimeText = map["endTimeText"] as? String ?: return null,
        hasStartDate = map["hasStartDate"] == true,
        hasEndDate = map["hasEndDate"] == true,
        hasStartTime = map["hasStartTime"] == true,
        hasEndTime = map["hasEndTime"] == true,
        allDay = map["allDay"] == true,
        lastValidPreview = (map["lastValidPreview"] as? List<*>)?.let { saved ->
            val date = saved.getOrNull(0).parseOrNull(LocalDate::parse)
            val start = saved.getOrNull(1).parseOrNull(LocalTime::parse)
            val end = saved.getOrNull(2).parseOrNull(LocalTime::parse)
            if (date != null && start != null && end != null) {
                EditorSchedulePreview(date, start, end, saved.getOrNull(3) == true)
            } else {
                null
            }
        },
    )
}

internal fun EditorTransferDraft.toSaveable(): Map<String, Any?> = hashMapOf(
    "title" to title,
    "notes" to notes,
    "location" to location,
    "locationMapVerified" to locationMapVerified,
    "manualColor" to manualColor,
    "categories" to categories,
    "recurrenceRule" to recurrenceRule,
    "reminderMinutes" to ArrayList(reminderMinutes),
    "sourceDefaultReminderMinutes" to ArrayList(sourceDefaultReminderMinutes),
    "date" to date?.toString(),
    "endDate" to endDate?.toString(),
    "startTime" to startTime?.toString(),
    "endTime" to endTime?.toString(),
    "allDay" to allDay,
    "schedule" to schedule?.toSaveable(),
)

internal fun transferDraftFromSaveable(value: Any?): EditorTransferDraft? {
    val map = value as? Map<*, *> ?: return null
    return EditorTransferDraft(
        title = map["title"] as? String ?: "",
        notes = map["notes"] as? String ?: "",
        location = map["location"] as? String ?: "",
        locationMapVerified = map["locationMapVerified"] as? Boolean,
        manualColor = (map["manualColor"] as? Number)?.toInt(),
        categories = map["categories"] as? String ?: "",
        recurrenceRule = map["recurrenceRule"] as? String ?: "",
        reminderMinutes = map["reminderMinutes"].toIntSet(),
        sourceDefaultReminderMinutes = map["sourceDefaultReminderMinutes"].toIntSet(),
        date = map["date"].parseOrNull(LocalDate::parse),
        endDate = map["endDate"].parseOrNull(LocalDate::parse),
        startTime = map["startTime"].parseOrNull(LocalTime::parse),
        endTime = map["endTime"].parseOrNull(LocalTime::parse),
        allDay = map["allDay"] as? Boolean,
        schedule = map["schedule"]?.let(::editorScheduleFromSaveable),
    )
}

private fun Any?.toIntSet(): Set<Int> =
    (this as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() }.toSet()

private fun <T> Any?.parseOrNull(parse: (String) -> T): T? =
    (this as? String)?.let { runCatching { parse(it) }.getOrNull() }

private inline fun <reified E : Enum<E>> enumValueOrNull(value: Any?): E? =
    enumValues<E>().firstOrNull { it.name == value }
