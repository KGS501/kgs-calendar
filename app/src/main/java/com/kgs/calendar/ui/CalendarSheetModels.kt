package com.kgs.calendar.ui

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.EventEditPayload
import com.kgs.calendar.domain.model.TaskEditPayload

internal enum class SettingsDestination(val title: String) {
    Main("Settings"),
    Accounts("Calendar"),
    AddSource("Add calendar"),
    AddAccount("Add CalDAV"),
    AddReadOnly("Read-only calendar"),
    AccountDetail("Edit source"),
    Behavior("Behavior"),
    Design("Design"),
    Widgets("Widgets"),
    WidgetAgenda("Agenda widget"),
    WidgetMonth("Month widget"),
    WidgetTasks("Tasks widget"),
    WidgetMulti("Multi widget"),
    WidgetDay("Day widget"),
    Privacy("Privacy"),
    EventFieldOrder("Event fields"),
    TaskFieldOrder("Task fields"),
    Sources("Calendars & sources"),
    Reorder("Order"),
}

internal sealed interface CreationSheet {
    data object EventLow : CreationSheet
    data object EventFull : CreationSheet
    data object TaskLow : CreationSheet
    data object Task : CreationSheet
    data class TaskForParent(val parent: TaskEntity) : CreationSheet
    data class EditEvent(val event: EventEntity) : CreationSheet
    data class EditTask(val task: TaskEntity) : CreationSheet
    data class DuplicateEvent(val event: EventEntity) : CreationSheet
    data class DuplicateTask(val task: TaskEntity) : CreationSheet
}

internal data class HiddenSaveNotice(
    val collectionHref: String,
    val kind: HiddenSaveKind,
)

internal enum class HiddenSaveKind {
    Event,
    Task,
}

internal sealed interface ConversionSource {
    data class Event(val event: EventEntity) : ConversionSource
    data class Task(val task: TaskEntity) : ConversionSource
}

internal sealed interface RecurringSaveRequest {
    data class Event(val event: EventEntity, val payload: EventEditPayload) : RecurringSaveRequest
    data class Task(val task: TaskEntity, val payload: TaskEditPayload) : RecurringSaveRequest
}

internal sealed interface DetailSheet {
    data class Event(val event: EventEntity) : DetailSheet
    data class Task(val task: TaskEntity) : DetailSheet
}
