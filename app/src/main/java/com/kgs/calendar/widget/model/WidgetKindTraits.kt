package com.kgs.calendar.widget.model

import android.content.Context
import android.os.Build
import com.kgs.calendar.R
import com.kgs.calendar.data.settings.WidgetTaskDisplayMode
import com.kgs.calendar.data.settings.WidgetTaskSortMode
import com.kgs.calendar.data.settings.WidgetTaskSubtaskDefaultMode
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.WIDGET_AGENDA_PRIORITY_FRAME_COUNT
import com.kgs.calendar.widget.WIDGET_AGENDA_PRIORITY_MOTION_FRAME_IDS
import com.kgs.calendar.widget.WIDGET_TASKS_PRIORITY_FRAME_COUNT
import com.kgs.calendar.widget.WIDGET_TASK_PRIORITY_MOTION_FRAME_IDS

internal val KgsWidgetKind.usesCollectionList: Boolean
    get() = this != KgsWidgetKind.Month && this != KgsWidgetKind.Day

internal fun KgsWidgetKind.emptyText(context: Context): String = when (this) {
    KgsWidgetKind.Tasks -> context.getString(R.string.no_scheduled_open_tasks)
    else -> context.getString(R.string.no_events_or_tasks)
}

internal fun WidgetTaskDisplayMode.widgetLabel(context: Context): String = when (this) {
    WidgetTaskDisplayMode.Planned -> context.getString(R.string.planned_tasks)
    WidgetTaskDisplayMode.Unplanned -> context.getString(R.string.unplanned_tasks)
    WidgetTaskDisplayMode.Today -> context.getString(R.string.tasks_for_today)
}

internal fun WidgetTaskSortMode.widgetLabel(context: Context): String = when (this) {
    WidgetTaskSortMode.Date -> context.getString(R.string.date)
    WidgetTaskSortMode.Priority -> context.getString(R.string.priority)
    WidgetTaskSortMode.Status -> context.getString(R.string.status)
}

internal fun WidgetTaskSortMode.next(): WidgetTaskSortMode =
    WidgetTaskSortMode.entries[(ordinal + 1) % WidgetTaskSortMode.entries.size]

internal fun WidgetTaskSubtaskDefaultMode.resolveSubtasksExpandedByDefault(appDefault: Boolean): Boolean = when (this) {
    WidgetTaskSubtaskDefaultMode.FollowApp -> appDefault
    WidgetTaskSubtaskDefaultMode.Open -> true
    WidgetTaskSubtaskDefaultMode.Closed -> false
}

// Agenda rows render card bitmaps; keeping them service-backed avoids attaching the full
// bitmap set to the top-level RemoteViews transaction. Multi includes those rows as well.
internal fun KgsWidgetKind.usesDirectCollectionItems(): Boolean =
    this == KgsWidgetKind.Tasks && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

internal fun KgsWidgetKind.usesDirectCollectionItemsAtSdk(sdkInt: Int): Boolean =
    this == KgsWidgetKind.Tasks && sdkInt >= Build.VERSION_CODES.S

internal fun usesDirectDayGridItems(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

internal fun KgsWidgetKind.priorityMotionFrameIds(): IntArray =
    if (usesAgendaCollectionStyle()) WIDGET_AGENDA_PRIORITY_MOTION_FRAME_IDS else WIDGET_TASK_PRIORITY_MOTION_FRAME_IDS

internal fun KgsWidgetKind.priorityMotionFrameCount(): Int =
    if (usesAgendaCollectionStyle()) WIDGET_AGENDA_PRIORITY_FRAME_COUNT else WIDGET_TASKS_PRIORITY_FRAME_COUNT

internal fun KgsWidgetKind.usesAgendaCollectionStyle(): Boolean =
    this == KgsWidgetKind.Agenda || this == KgsWidgetKind.Day || this == KgsWidgetKind.Multi
