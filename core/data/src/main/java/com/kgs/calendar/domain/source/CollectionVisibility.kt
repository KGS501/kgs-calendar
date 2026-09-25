package com.kgs.calendar.domain.source

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity

/**
 * Which calendars, and which item types of a calendar, the user has hidden from the views.
 *
 * [hiddenCollectionHrefs] hides a whole calendar (the sidebar checkbox). [eventsHiddenIn] and
 * [tasksHiddenIn] hide only the events or only the tasks of a calendar that keeps its other type
 * visible. Hidden items are left out of every view, the widgets and the reminders.
 */
data class CollectionVisibility(
    val hiddenCollectionHrefs: Set<String> = emptySet(),
    val eventsHiddenIn: Set<String> = emptySet(),
    val tasksHiddenIn: Set<String> = emptySet(),
) {
    /** Calendars whose events are not shown, whether the whole calendar or only its events is hidden. */
    val hrefsHidingEvents: Set<String> = hiddenCollectionHrefs + eventsHiddenIn

    /** Calendars whose tasks are not shown, whether the whole calendar or only its tasks is hidden. */
    val hrefsHidingTasks: Set<String> = hiddenCollectionHrefs + tasksHiddenIn

    fun showsEventsOf(collectionHref: String?): Boolean = collectionHref !in hrefsHidingEvents

    fun showsTasksOf(collectionHref: String?): Boolean = collectionHref !in hrefsHidingTasks

    fun visibleEvents(events: List<EventEntity>): List<EventEntity> =
        if (hrefsHidingEvents.isEmpty()) events else events.filter { showsEventsOf(it.collectionHref) }

    fun visibleTasks(tasks: List<TaskEntity>): List<TaskEntity> =
        if (hrefsHidingTasks.isEmpty()) tasks else tasks.filter { showsTasksOf(it.collectionHref) }

    /** A stable text form for render and cache signatures. */
    fun signature(): String = buildString {
        append(hiddenCollectionHrefs.sorted().joinToString(","))
        append(';').append(eventsHiddenIn.sorted().joinToString(","))
        append(';').append(tasksHiddenIn.sorted().joinToString(","))
    }
}
