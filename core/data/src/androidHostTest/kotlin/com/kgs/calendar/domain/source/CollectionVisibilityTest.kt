package com.kgs.calendar.domain.source

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionVisibilityTest {
    private val events = listOf(event("a"), event("b"), event("c"))
    private val tasks = listOf(task("a"), task("b"), task("c"))

    @Test
    fun theDefaultHidesNothing() {
        val visibility = CollectionVisibility()

        assertSame(events, visibility.visibleEvents(events))
        assertSame(tasks, visibility.visibleTasks(tasks))
        assertTrue(visibility.showsEventsOf("a"))
        assertTrue(visibility.showsTasksOf("a"))
    }

    @Test
    fun aHiddenCalendarHidesBothItsEventsAndItsTasks() {
        val visibility = CollectionVisibility(hiddenCollectionHrefs = setOf("b"))

        assertEquals(listOf("a", "c"), visibility.visibleEvents(events).map { it.collectionHref })
        assertEquals(listOf("a", "c"), visibility.visibleTasks(tasks).map { it.collectionHref })
    }

    @Test
    fun hidingOnlyTheTasksOfACalendarKeepsItsEvents() {
        val visibility = CollectionVisibility(tasksHiddenIn = setOf("b"))

        assertEquals(listOf("a", "b", "c"), visibility.visibleEvents(events).map { it.collectionHref })
        assertEquals(listOf("a", "c"), visibility.visibleTasks(tasks).map { it.collectionHref })
        assertTrue(visibility.showsEventsOf("b"))
        assertFalse(visibility.showsTasksOf("b"))
    }

    @Test
    fun hidingOnlyTheEventsOfACalendarKeepsItsTasks() {
        val visibility = CollectionVisibility(eventsHiddenIn = setOf("c"))

        assertEquals(listOf("a", "b"), visibility.visibleEvents(events).map { it.collectionHref })
        assertEquals(listOf("a", "b", "c"), visibility.visibleTasks(tasks).map { it.collectionHref })
        assertEquals(setOf("c"), visibility.hrefsHidingEvents)
        assertEquals(emptySet<String>(), visibility.hrefsHidingTasks)
    }

    @Test
    fun theWholeCalendarSettingWinsOverAVisibleItemType() {
        val visibility = CollectionVisibility(hiddenCollectionHrefs = setOf("a"), eventsHiddenIn = setOf("b"))

        assertFalse(visibility.showsEventsOf("a"))
        assertFalse(visibility.showsTasksOf("a"))
        assertEquals(setOf("a", "b"), visibility.hrefsHidingEvents)
        assertEquals(setOf("a"), visibility.hrefsHidingTasks)
    }

    @Test
    fun theSignatureDistinguishesWhichTypeIsHidden() {
        val eventsHidden = CollectionVisibility(eventsHiddenIn = setOf("a"))
        val tasksHidden = CollectionVisibility(tasksHiddenIn = setOf("a"))

        assertNotEquals(eventsHidden.signature(), tasksHidden.signature())
        assertEquals(
            CollectionVisibility(hiddenCollectionHrefs = setOf("b", "a")).signature(),
            CollectionVisibility(hiddenCollectionHrefs = setOf("a", "b")).signature(),
        )
    }

    private fun event(collectionHref: String) = EventEntity(
        uid = "event-$collectionHref",
        collectionHref = collectionHref,
        resourceHref = "$collectionHref/event.ics",
        title = "Event",
        description = null,
        location = null,
        startsAtMillis = 0L,
        endsAtMillis = 1L,
        allDay = false,
        recurrenceRule = null,
        isRecurring = false,
        color = 0,
    )

    private fun task(collectionHref: String) = TaskEntity(
        uid = "task-$collectionHref",
        collectionHref = collectionHref,
        resourceHref = "$collectionHref/task.ics",
        title = "Task",
        notes = null,
        dueAtMillis = null,
        startAtMillis = null,
        completedAtMillis = null,
        isCompleted = false,
        priority = null,
        color = 0,
    )
}
