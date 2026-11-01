package com.kgs.calendar.data

import com.kgs.calendar.domain.model.MutationAction
import com.kgs.calendar.domain.sync.PendingOccurrenceScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Which occurrences a queued change marks as waiting for sync (#29): edits to one occurrence mark
 * only that one, and the scope of coalesced uploads merges.
 */
@RunWith(RobolectricTestRunner::class)
class PendingOccurrenceScopeRepositoryTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val server = harness.server
    private lateinit var seriesHref: String
    private val first = Instant.parse("2026-10-05T08:00:00Z").toEpochMilli()
    private val second = Instant.parse("2026-10-12T08:00:00Z").toEpochMilli()
    private val third = Instant.parse("2026-10-19T08:00:00Z").toEpochMilli()

    @Before
    fun setUp() = runTest {
        seriesHref = server.putRemote(
            server.eventsHref,
            "series.ics",
            SampleIcs.event("remote-series", "Weekly", rrule = "FREQ=WEEKLY;COUNT=4"),
        )
        harness.addSyncedCalDavAccount()
        server.clearRequests()
    }

    @After
    fun tearDown() = harness.close()

    private suspend fun queuedScope(href: String = seriesHref): String? =
        harness.pendingMutations().single { it.resourceHref == href }.occurrenceScope

    @Test
    fun editingOneOccurrenceMarksOnlyThatOccurrence() = runTest {
        repository.updateEventOccurrence(
            "remote-series",
            second,
            eventPayload("Moved weekly", LocalDate.of(2026, 10, 13), collectionHref = server.eventsHref),
        )

        assertEquals(PendingOccurrenceScope.single(second), queuedScope())
    }

    @Test
    fun draggingOneOccurrenceMarksOnlyThatOccurrence() = runTest {
        repository.moveTimedEvent("remote-series", third, LocalDate.of(2026, 10, 20), LocalTime.of(14, 0), LocalTime.of(15, 0))

        assertEquals(PendingOccurrenceScope.single(third), queuedScope())
    }

    @Test
    fun deletingOneOccurrenceMarksOnlyThatOccurrence() = runTest {
        repository.deleteEventOccurrence("remote-series", second)

        assertEquals(PendingOccurrenceScope.single(second), queuedScope())
    }

    @Test
    fun deletingFollowingOccurrencesMarksThatOccurrenceAndLaterOnes() = runTest {
        repository.deleteEventFollowing("remote-series", third)

        assertEquals(PendingOccurrenceScope.following(third), queuedScope())
    }

    @Test
    fun coalescedOccurrenceEditsMarkTheUnionAndASeriesEditMarksEverything() = runTest {
        repository.deleteEventOccurrence("remote-series", second)
        repository.moveTimedEvent("remote-series", third, LocalDate.of(2026, 10, 20), LocalTime.of(14, 0), LocalTime.of(15, 0))

        val queued = harness.pendingMutations().single()
        assertEquals(MutationAction.Put, queued.action)
        assertEquals("$second,$third", queued.occurrenceScope)

        repository.updateEvent("remote-series", eventPayload("Renamed", LocalDate.of(2026, 10, 5), collectionHref = server.eventsHref, recurrenceRule = "FREQ=WEEKLY;COUNT=4"))

        assertNull(queuedScope())

        // A later single-occurrence edit keeps the whole series marked: its payload still carries the series edit.
        repository.deleteEventOccurrence("remote-series", first + 21 * DAY_MILLIS)
        assertNull(queuedScope())
    }

    @Test
    fun scopedChangeUploadsLikeAnyOtherAndLeavesTheQueue() = runTest {
        repository.deleteEventOccurrence("remote-series", second)

        repository.syncNow()

        assertEquals(seriesHref, server.requests("PUT").single().path)
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun completingOneTaskOccurrenceMarksOnlyThatOccurrence() = runTest {
        repository.createTask(
            taskPayload("Water plants", collectionHref = server.tasksHref, dueDate = LocalDate.of(2026, 10, 5), dueTime = LocalTime.of(18, 0))
                .copy(recurrenceRule = "FREQ=DAILY;COUNT=3"),
        )
        val task = harness.tasksIn(server.tasksHref).single { it.title == "Water plants" }
        assertNull(queuedScope(task.resourceHref))
        repository.pushPendingChangesCreatedSince(0)
        assertTrue(harness.pendingMutations().isEmpty())
        val secondDue = harness.millis(LocalDate.of(2026, 10, 6), LocalTime.of(18, 0))

        repository.setTaskOccurrenceStatus(task.resourceHref, secondDue, "COMPLETED")

        assertEquals(PendingOccurrenceScope.single(secondDue), queuedScope(task.resourceHref))
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
