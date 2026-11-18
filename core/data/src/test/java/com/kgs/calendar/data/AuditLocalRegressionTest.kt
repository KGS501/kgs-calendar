package com.kgs.calendar.data

import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.source.CollectionVisibility
import com.kgs.calendar.domain.trash.TrashOrigin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.kgs.calendar.time.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@RunWith(RobolectricTestRunner::class)
class AuditLocalRegressionTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val day = LocalDate.of(2026, 11, 2)

    @After
    fun tearDown() = harness.close()

    private suspend fun syncedEvent(ics: String, name: String = "series.ics"): String {
        val href = harness.server.putRemote(harness.server.eventsHref, name, ics)
        harness.addSyncedCalDavAccount()
        return href
    }

    // --- #72 --------------------------------------------------------------------------------------

    private val rDateOnly = SampleIcs.calendar(
        "BEGIN:VEVENT",
        "UID:rdate-only",
        "DTSTAMP:20260901T000000Z",
        "DTSTART:20261102T090000Z",
        "DTEND:20261102T100000Z",
        "RDATE:20261109T090000Z",
        "SUMMARY:Extra date",
        "END:VEVENT",
    )

    @Test
    fun anRdateOnlySeriesShowsOnItsExtraDate() = runTest {
        syncedEvent(rDateOnly)
        val start = harness.startOfDay(LocalDate.of(2026, 11, 9))

        val events = harness.components.queries.eventsSnapshot(start, start + 86_400_000L)

        assertEquals(listOf("Extra date"), events.map { it.title })
    }

    @Test
    fun deletingOneRdateOccurrenceKeepsTheOthers() = runTest {
        syncedEvent(rDateOnly)
        val extra = kotlin.time.Instant.parse("2026-11-09T09:00:00Z").toEpochMilliseconds()

        repository.deleteEventOccurrence("rdate-only", extra)

        assertTrue(harness.pendingMutations().single().payloadIcs!!.contains("EXDATE"))
        val all = harness.components.queries.eventsSnapshot(extra - 30L * 86_400_000L, extra + 30L * 86_400_000L)
        assertEquals(listOf(extra - 7L * 86_400_000L), all.map { it.startsAtMillis })
    }

    // --- #69 / #67 ----------------------------------------------------------------------------------

    @Test
    fun aDeletedOrFinishedItemIsNoLongerDeliverable() = runTest {
        repository.ensureLocalCalendar()
        repository.createTask(taskPayload("Pay invoice", dueDate = day))
        val task = harness.tasksIn(LOCAL_COLLECTION).single()
        val occurrence = CalendarOccurrenceId.Task(task.resourceHref, task.dueAtMillis!!)
        val visibility = CollectionVisibility()
        assertTrue(harness.components.queries.isReminderDeliverable(occurrence, visibility))

        repository.setTaskCompleted(task.resourceHref, true)
        assertFalse(harness.components.queries.isReminderDeliverable(occurrence, visibility))

        repository.deleteTask(task.uid)
        assertFalse(harness.components.queries.isReminderDeliverable(occurrence, visibility))
    }

    @Test
    fun aCancelledOccurrenceIsNotDeliverableButItsNeighbourIs() = runTest {
        val ics = SampleIcs.calendar(
            "BEGIN:VEVENT",
            "UID:weekly",
            "DTSTAMP:20260901T000000Z",
            "DTSTART:20261102T090000Z",
            "DTEND:20261102T100000Z",
            "RRULE:FREQ=DAILY;COUNT=3",
            "SUMMARY:Daily",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "UID:weekly",
            "DTSTAMP:20260901T000000Z",
            "RECURRENCE-ID:20261103T090000Z",
            "DTSTART:20261103T090000Z",
            "DTEND:20261103T100000Z",
            "STATUS:CANCELLED",
            "SUMMARY:Daily",
            "END:VEVENT",
        )
        val href = syncedEvent(ics)
        val first = kotlin.time.Instant.parse("2026-11-02T09:00:00Z").toEpochMilliseconds()

        assertTrue(harness.components.queries.isReminderDeliverable(CalendarOccurrenceId.Event(href, first), CollectionVisibility()))
        assertFalse(harness.components.queries.isReminderDeliverable(CalendarOccurrenceId.Event(href, first + 86_400_000L), CollectionVisibility()))
    }

    // --- #94 ------------------------------------------------------------------------------------

    @Test
    fun removingASourceOnlyPurgesItsSnapshotsWhenAsked() = runTest {
        val href = syncedEvent(SampleIcs.event("a", "Sensitive"))
        repository.deleteEvent("a")
        repository.ensureLocalCalendar()
        repository.createEvent(eventPayload("Other source", day, collectionHref = LOCAL_COLLECTION))
        repository.deleteEvent(harness.eventsIn(LOCAL_COLLECTION).single().uid)
        val accountId = harness.collection(harness.server.eventsHref)!!.accountId

        repository.deleteAccount(accountId, purgeRecentlyDeleted = true)

        assertNull(harness.resource(href))
        val left = repository.observeTrashedItems().first()
        assertEquals(listOf("Other source"), left.map { it.title })
        assertTrue(left.all { it.origin == TrashOrigin.LocalSnapshot })
        assertTrue(harness.server.requests("DELETE").isEmpty())
    }
}
