package com.kgs.calendar.widget.model

import com.kgs.calendar.widget.KgsWidgetKind
import java.time.LocalDate

internal data class WidgetListRow(
    val type: WidgetListRowType,
    val title: String,
    val meta: String,
    val color: Int,
    val sortMillis: Long,
    val date: LocalDate,
    val completed: Boolean,
    val allDaySort: Int,
    val launchKind: KgsWidgetKind,
    val stableId: Long,
    val location: String?,
    val eventStatus: String?,
    val endMillis: Long,
    val spanEndDate: LocalDate?,
    val showAgendaDate: Boolean,
    val eventResourceHref: String?,
    val taskResourceHref: String?,
    val statusGlyph: String,
    val depth: Int,
    val childCount: Int,
    val continuationLevels: Set<Int>,
    val lastSibling: Boolean,
    val subtasksExpanded: Boolean,
    val priority: Int?,
    val priorityMotionEnabled: Boolean,
) {
    fun appendSignatureTo(builder: StringBuilder) {
        builder
            .append(type.name)
            .append('|').append(stableId)
            .append('|').append(title)
            .append('|').append(meta)
            .append('|').append(color)
            .append('|').append(sortMillis)
            .append('|').append(date.toEpochDay())
            .append('|').append(completed)
            .append('|').append(allDaySort)
            .append('|').append(launchKind.name)
            .append('|').append(location.orEmpty())
            .append('|').append(eventStatus.orEmpty())
            .append('|').append(endMillis)
            .append('|').append(spanEndDate?.toEpochDay() ?: Long.MIN_VALUE)
            .append('|').append(showAgendaDate)
            .append('|').append(eventResourceHref.orEmpty())
            .append('|').append(taskResourceHref.orEmpty())
            .append('|').append(statusGlyph)
            .append('|').append(depth)
            .append('|').append(childCount)
            .append('|').append(continuationLevels.sorted().joinToString(","))
            .append('|').append(lastSibling)
            .append('|').append(subtasksExpanded)
            .append('|').append(priority ?: 0)
            .append('|').append(priorityMotionEnabled)
    }

    companion object {
        fun empty(title: String): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Empty,
            title = title,
            meta = "",
            color = 0,
            sortMillis = Long.MIN_VALUE,
            date = LocalDate.now(),
            completed = false,
            allDaySort = 0,
            launchKind = KgsWidgetKind.Tasks,
            stableId = stableId("tasks-empty:$title"),
            location = null,
            eventStatus = null,
            endMillis = Long.MIN_VALUE,
            spanEndDate = null,
            showAgendaDate = true,
            eventResourceHref = null,
            taskResourceHref = null,
            statusGlyph = "",
            depth = 0,
            childCount = 0,
            continuationLevels = emptySet(),
            lastSibling = true,
            subtasksExpanded = true,
            priority = null,
            priorityMotionEnabled = false,
        )

        fun section(title: String, sortValue: Long = Long.MIN_VALUE): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Section,
            title = title,
            meta = "",
            color = 0,
            sortMillis = sortValue,
            date = LocalDate.now(),
            completed = false,
            allDaySort = 0,
            launchKind = KgsWidgetKind.Agenda,
            stableId = stableId("section:$title"),
            location = null,
            eventStatus = null,
            endMillis = Long.MIN_VALUE,
            spanEndDate = null,
            showAgendaDate = true,
            eventResourceHref = null,
            taskResourceHref = null,
            statusGlyph = "",
            depth = 0,
            childCount = 0,
            continuationLevels = emptySet(),
            lastSibling = true,
            subtasksExpanded = true,
            priority = null,
            priorityMotionEnabled = false,
        )

        fun now(date: LocalDate, timeLabel: String, sortValue: Long): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Now,
            title = timeLabel,
            meta = "",
            color = 0,
            sortMillis = sortValue,
            date = date,
            completed = false,
            allDaySort = 0,
            launchKind = KgsWidgetKind.Day,
            stableId = stableId("day-now:${date.toEpochDay()}:$sortValue"),
            location = null,
            eventStatus = null,
            endMillis = Long.MIN_VALUE,
            spanEndDate = null,
            showAgendaDate = true,
            eventResourceHref = null,
            taskResourceHref = null,
            statusGlyph = "",
            depth = 0,
            childCount = 0,
            continuationLevels = emptySet(),
            lastSibling = true,
            subtasksExpanded = true,
            priority = null,
            priorityMotionEnabled = false,
        )

        fun item(
            title: String,
            meta: String,
            color: Int,
            sortMillis: Long,
            date: LocalDate,
            completed: Boolean,
            allDaySort: Int,
            launchKind: KgsWidgetKind,
            stableKey: String,
            eventResourceHref: String? = null,
            taskResourceHref: String? = null,
            location: String? = null,
            eventStatus: String? = null,
            endMillis: Long = sortMillis,
            spanEndDate: LocalDate? = null,
        ): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Item,
            title = title,
            meta = meta,
            color = color,
            sortMillis = sortMillis,
            date = date,
            completed = completed,
            allDaySort = allDaySort,
            launchKind = launchKind,
            stableId = stableId(stableKey),
            location = location,
            eventStatus = eventStatus,
            endMillis = endMillis,
            spanEndDate = spanEndDate,
            showAgendaDate = true,
            eventResourceHref = eventResourceHref,
            taskResourceHref = taskResourceHref,
            statusGlyph = "",
            depth = 0,
            childCount = 0,
            continuationLevels = emptySet(),
            lastSibling = true,
            subtasksExpanded = true,
            priority = null,
            priorityMotionEnabled = false,
        )

        fun task(
            title: String,
            meta: String,
            color: Int,
            sortMillis: Long,
            date: LocalDate,
            completed: Boolean,
            taskResourceHref: String,
            statusGlyph: String,
            location: String? = null,
            depth: Int,
            childCount: Int,
            continuationLevels: Set<Int>,
            lastSibling: Boolean,
            subtasksExpanded: Boolean,
            priority: Int?,
            priorityMotionEnabled: Boolean,
            launchKind: KgsWidgetKind = KgsWidgetKind.Tasks,
        ): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Task,
            title = title,
            meta = meta,
            color = color,
            sortMillis = sortMillis,
            date = date,
            completed = completed,
            allDaySort = 1,
            launchKind = launchKind,
            stableId = stableId("task-row:$taskResourceHref"),
            location = location,
            eventStatus = null,
            endMillis = sortMillis,
            spanEndDate = null,
            showAgendaDate = true,
            eventResourceHref = null,
            taskResourceHref = taskResourceHref,
            statusGlyph = statusGlyph,
            depth = depth,
            childCount = childCount,
            continuationLevels = continuationLevels,
            lastSibling = lastSibling,
            subtasksExpanded = subtasksExpanded,
            priority = priority,
            priorityMotionEnabled = priorityMotionEnabled,
        )

        private fun stableId(value: String): Long =
            value.fold(1125899906842597L) { acc, char -> acc * 31 + char.code }
    }
}

internal enum class WidgetListRowType {
    Empty,
    Section,
    Now,
    Item,
    Task,
}
