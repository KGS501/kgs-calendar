package com.kgs.calendar.data

import android.graphics.Color
import com.kgs.calendar.domain.model.ComponentType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class LocalCalendarRepositoryTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val day = LocalDate.of(2026, 10, 5)

    @After
    fun tearDown() = harness.close()

    @Test
    fun ensureLocalCalendarCreatesWritableLocalAccountAndCollection() = runTest {
        repository.ensureLocalCalendar()

        val account = harness.account("local")!!
        assertEquals(SourceType.Local, account.sourceType)
        assertEquals("local://device", account.serverUrl)
        assertEquals("Local calendar", account.displayName)
        val collection = harness.collection(LOCAL_COLLECTION)!!
        assertEquals("local", collection.accountId)
        assertEquals("Lokal", collection.displayName)
        assertEquals(SourceType.Local, collection.sourceType)
        assertTrue(collection.supportsEvents)
        assertTrue(collection.supportsTasks)
        assertFalse(collection.readOnly)
        assertTrue(collection.isEnabled)
        assertEquals(-10_000, collection.sortOrder)
        assertEquals(Color.rgb(23, 107, 93), collection.color)
    }

    @Test
    fun ensureLocalCalendarKeepsUserAppearanceAndEnabledState() = runTest {
        repository.ensureLocalCalendar()
        repository.updateCollectionAppearance(LOCAL_COLLECTION, "Private", 0xFF112233.toInt())
        repository.setCollectionEnabled(LOCAL_COLLECTION, false)

        repository.ensureLocalCalendar()

        val collection = harness.collection(LOCAL_COLLECTION)!!
        assertEquals("Private", collection.displayName)
        // NOTE: current behaviour - the rebuilt row keeps displayName but drops customDisplayName.
        assertNull(collection.customDisplayName)
        assertEquals(0xFF112233.toInt(), collection.color)
        assertEquals(0xFF112233.toInt(), collection.customColor)
        assertEquals(Color.rgb(23, 107, 93), collection.automaticColor)
        assertFalse(collection.isEnabled)
    }

    @Test
    fun createEventWithoutWritableCalendarFails() = runTest {
        val error = expectFailure<IllegalStateException> { repository.createEvent(eventPayload("Nowhere", day)) }

        assertEquals("No writable event calendar has been synced yet.", error.message)
    }

    @Test
    fun createLocalEventWritesRowAndRawIcsWithoutPendingMutation() = runTest {
        repository.ensureLocalCalendar()

        repository.createEvent(eventPayload("Dentist", day, LocalTime.of(10, 0), LocalTime.of(11, 30), description = "Bring card"))

        val event = harness.eventsIn(LOCAL_COLLECTION).single()
        assertTrue(event.uid.endsWith("@kgs-calendar"))
        assertEquals("$LOCAL_COLLECTION/${event.uid.replace("@", "%40")}.ics", event.resourceHref)
        assertEquals("Dentist", event.title)
        assertEquals("Bring card", event.description)
        assertEquals(harness.millis(day, LocalTime.of(10, 0)), event.startsAtMillis)
        assertEquals(harness.millis(day, LocalTime.of(11, 30)), event.endsAtMillis)
        assertFalse(event.allDay)
        assertEquals(TEST_ZONE.id, event.timezoneId)
        assertEquals(0, event.sequence)
        val resource = harness.resource(event.resourceHref)!!
        assertEquals(ComponentType.Event, resource.componentType)
        assertEquals(event.uid, resource.uid)
        assertNull(resource.etag)
        val raw = resource.rawIcs.unfoldedIcs()
        assertTrue(raw.contains("BEGIN:VEVENT"))
        assertTrue(raw.contains("UID:${event.uid}"))
        assertTrue(raw.contains("SUMMARY:Dentist"))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun createAllDayEventEndsAtNextMidnight() = runTest {
        repository.ensureLocalCalendar()

        repository.createEvent(eventPayload("Holiday", day, start = null, end = null, allDay = true))

        val event = harness.eventsIn(LOCAL_COLLECTION).single()
        assertTrue(event.allDay)
        assertNull(event.timezoneId)
        assertEquals(harness.startOfDay(day), event.startsAtMillis)
        assertEquals(harness.startOfDay(day.plusDays(1)), event.endsAtMillis)
    }

    @Test
    fun updateLocalEventRewritesRowAndRawIcsWithoutPendingMutation() = runTest {
        repository.ensureLocalCalendar()
        repository.createEvent(eventPayload("Draft", day))
        val created = harness.eventsIn(LOCAL_COLLECTION).single()

        repository.updateEvent(created.uid, eventPayload("Final", day.plusDays(1), LocalTime.of(14, 0), LocalTime.of(15, 0)))

        val updated = harness.event(created.uid)!!
        assertEquals(created.resourceHref, updated.resourceHref)
        assertEquals("Final", updated.title)
        assertEquals(harness.millis(day.plusDays(1), LocalTime.of(14, 0)), updated.startsAtMillis)
        assertEquals(1, updated.sequence)
        val raw = harness.resource(created.resourceHref)!!.rawIcs.unfoldedIcs()
        assertTrue(raw.contains("SUMMARY:Final"))
        assertFalse(raw.contains("SUMMARY:Draft"))
        assertTrue(raw.contains("SEQUENCE:1"))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun deleteLocalEventRemovesRowAndResource() = runTest {
        repository.ensureLocalCalendar()
        repository.createEvent(eventPayload("Gone soon", day))
        val created = harness.eventsIn(LOCAL_COLLECTION).single()

        repository.deleteEvent(created.uid)

        assertNull(harness.event(created.uid))
        assertNull(harness.resource(created.resourceHref))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun createLocalTaskWritesRowAndRawIcsWithoutPendingMutation() = runTest {
        repository.ensureLocalCalendar()

        repository.createTask(taskPayload("Buy milk", dueDate = day, dueTime = LocalTime.of(18, 0)))

        val task = harness.tasksIn(LOCAL_COLLECTION).single()
        assertEquals("Buy milk", task.title)
        assertEquals(harness.millis(day, LocalTime.of(18, 0)), task.dueAtMillis)
        assertTrue(task.dueHasTime)
        assertNull(task.startAtMillis)
        assertFalse(task.isCompleted)
        assertEquals("NEEDS-ACTION", task.status)
        val resource = harness.resource(task.resourceHref)!!
        assertEquals(ComponentType.Task, resource.componentType)
        assertNull(resource.etag)
        val raw = resource.rawIcs.unfoldedIcs()
        assertTrue(raw.contains("BEGIN:VTODO"))
        assertTrue(raw.contains("SUMMARY:Buy milk"))
        assertTrue(raw.contains("STATUS:NEEDS-ACTION"))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun setTaskStatusUpdatesCompletionFieldsAndRawIcs() = runTest {
        repository.ensureLocalCalendar()
        repository.createTask(taskPayload("Report", dueDate = day))
        val task = harness.tasksIn(LOCAL_COLLECTION).single()

        repository.setTaskStatus(task.resourceHref, "COMPLETED")

        val completed = harness.task(task.resourceHref)!!
        assertTrue(completed.isCompleted)
        assertNotNull(completed.completedAtMillis)
        assertEquals("COMPLETED", completed.status)
        assertEquals(1, completed.sequence)
        assertTrue(harness.resource(task.resourceHref)!!.rawIcs.unfoldedIcs().contains("STATUS:COMPLETED"))

        repository.setTaskStatus(task.resourceHref, "in-process")

        val reopened = harness.task(task.resourceHref)!!
        assertFalse(reopened.isCompleted)
        assertNull(reopened.completedAtMillis)
        assertEquals("IN-PROCESS", reopened.status)
        assertEquals(2, reopened.sequence)
        assertTrue(harness.resource(task.resourceHref)!!.rawIcs.unfoldedIcs().contains("STATUS:IN-PROCESS"))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun updateLocalTaskRewritesFieldsAndDerivesCompletedStatus() = runTest {
        repository.ensureLocalCalendar()
        repository.createTask(taskPayload("Draft", dueDate = day))
        val created = harness.tasksIn(LOCAL_COLLECTION).single()
        assertFalse(created.dueHasTime)
        assertEquals(harness.startOfDay(day), created.dueAtMillis)

        repository.updateTask(created.uid, taskPayload("Final", dueDate = day.plusDays(2), dueTime = LocalTime.of(8, 30), isCompleted = true))

        val updated = harness.task(created.resourceHref)!!
        assertEquals(created.resourceHref, updated.resourceHref)
        assertEquals("Final", updated.title)
        assertEquals(harness.millis(day.plusDays(2), LocalTime.of(8, 30)), updated.dueAtMillis)
        assertTrue(updated.dueHasTime)
        assertEquals(TEST_ZONE.id, updated.timezoneId)
        assertTrue(updated.isCompleted)
        assertNotNull(updated.completedAtMillis)
        assertEquals("COMPLETED", updated.status)
        assertEquals(1, updated.sequence)
        val raw = harness.resource(created.resourceHref)!!.rawIcs.unfoldedIcs()
        assertTrue(raw.contains("SUMMARY:Final"))
        assertTrue(raw.contains("STATUS:COMPLETED"))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun createTaskRejectsParentOutsideTheTaskList() = runTest {
        repository.ensureLocalCalendar()

        val error = expectFailure<IllegalArgumentException> {
            repository.createTask(taskPayload("Orphan", parentUid = "missing-parent"))
        }

        assertEquals("The selected parent task is not in this task list.", error.message)
        assertTrue(harness.tasksIn(LOCAL_COLLECTION).isEmpty())
    }

    @Test
    fun deletingParentTaskReparentsChildrenToGrandparent() = runTest {
        repository.ensureLocalCalendar()
        repository.createTask(taskPayload("Project"))
        val root = harness.tasksIn(LOCAL_COLLECTION).single()
        repository.createTask(taskPayload("Phase", parentUid = root.uid))
        val middle = harness.tasksIn(LOCAL_COLLECTION).single { it.title == "Phase" }
        repository.createTask(taskPayload("Step", parentUid = middle.uid))
        val leaf = harness.tasksIn(LOCAL_COLLECTION).single { it.title == "Step" }
        assertEquals(middle.uid, leaf.parentUid)

        repository.deleteTask(middle.uid)

        assertNull(harness.task(middle.resourceHref))
        assertNull(harness.resource(middle.resourceHref))
        val reparented = harness.task(leaf.resourceHref)!!
        assertEquals(root.uid, reparented.parentUid)
        assertEquals(1, reparented.sequence)
        assertTrue(
            harness.resource(leaf.resourceHref)!!.rawIcs.unfoldedIcs()
                .contains("RELATED-TO;RELTYPE=PARENT:${root.uid}"),
        )
        assertEquals(root, harness.task(root.resourceHref))
        assertTrue(harness.pendingMutations().isEmpty())
    }
}
