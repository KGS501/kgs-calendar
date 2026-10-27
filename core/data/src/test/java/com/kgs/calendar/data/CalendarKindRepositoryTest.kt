package com.kgs.calendar.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** Events and tasks always land in a calendar that can hold their kind, so they can sync. */
@RunWith(RobolectricTestRunner::class)
class CalendarKindRepositoryTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val server = harness.server
    private val day = LocalDate.of(2026, 10, 26)

    @Before
    fun setUp() = runTest {
        harness.addSyncedCalDavAccount()
    }

    @After
    fun tearDown() = harness.close()

    @Test
    fun aNewEventAskedForATaskListGoesToAnEventCalendar() = runTest {
        assertTrue(!harness.collection(server.tasksHref)!!.supportsEvents)

        repository.createEvent(eventPayload("Dentist", day, collectionHref = server.tasksHref))

        assertTrue(harness.eventsIn(server.tasksHref).isEmpty())
        assertEquals(listOf("Dentist"), harness.eventsIn(server.eventsHref).map { it.title })
        assertEquals(server.eventsHref, harness.pendingMutations().single().collectionHref)
    }

    @Test
    fun aNewTaskAskedForAnEventCalendarGoesToATaskList() = runTest {
        assertTrue(!harness.collection(server.eventsHref)!!.supportsTasks)

        repository.createTask(taskPayload("Buy milk", collectionHref = server.eventsHref))

        assertTrue(harness.tasksIn(server.eventsHref).isEmpty())
        assertEquals(listOf("Buy milk"), harness.tasksIn(server.tasksHref).map { it.title })
    }

    @Test
    fun anEventCannotBeMovedIntoATaskList() = runTest {
        repository.createEvent(eventPayload("Dentist", day, collectionHref = server.eventsHref))
        val event = harness.eventsIn(server.eventsHref).single()

        val error = runCatching {
            repository.updateEvent(event.uid, eventPayload("Dentist", day, collectionHref = server.tasksHref))
        }.exceptionOrNull()

        assertEquals("This calendar cannot hold events.", error?.message)
        assertEquals(server.eventsHref, harness.event(event.uid)!!.collectionHref)
    }

    @Test
    fun aTaskCannotBeMovedIntoAnEventCalendar() = runTest {
        repository.createTask(taskPayload("Buy milk", collectionHref = server.tasksHref))
        val task = harness.tasksIn(server.tasksHref).single()

        val error = runCatching {
            repository.updateTask(task.uid, taskPayload("Buy milk", collectionHref = server.eventsHref))
        }.exceptionOrNull()

        assertEquals("This calendar cannot hold tasks.", error?.message)
        assertEquals(server.tasksHref, harness.task(task.uid)!!.collectionHref)
    }
}
