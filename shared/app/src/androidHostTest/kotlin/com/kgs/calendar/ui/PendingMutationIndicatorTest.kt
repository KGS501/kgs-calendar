package com.kgs.calendar.ui

import com.kgs.calendar.data.RepositoryHarness
import com.kgs.calendar.data.SampleIcs
import com.kgs.calendar.data.eventPayload
import com.kgs.calendar.data.taskPayload
import com.kgs.calendar.domain.model.MutationAction
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * The unsynced-change dot and delete fade are drawn per displayed occurrence (#29). These run the
 * real expansion of a synced series and ask the indicator predicate about each occurrence.
 */
@RunWith(RobolectricTestRunner::class)
class PendingMutationIndicatorTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val server = harness.server
    private lateinit var seriesHref: String
    private val rangeStart = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()
    private val rangeEnd = Instant.parse("2026-11-15T00:00:00Z").toEpochMilli()
    private val second = Instant.parse("2026-10-12T08:00:00Z").toEpochMilli()

    @Before
    fun setUp() = runTest {
        seriesHref = server.putRemote(
            server.eventsHref,
            "series.ics",
            SampleIcs.event("remote-series", "Weekly", rrule = "FREQ=WEEKLY;COUNT=4"),
        )
        harness.addSyncedCalDavAccount()
    }

    @After
    fun tearDown() = harness.close()

    private suspend fun pendingOccurrenceTitles(): List<Pair<String, Boolean>> {
        val pending = harness.pendingMutations()
        return repository.eventsSnapshot(rangeStart, rangeEnd)
            .filter { it.resourceHref == seriesHref }
            .map { it.title to (pending.pendingMutationForEvent(it) != null) }
    }

    @Test
    fun movedOccurrenceIsTheOnlyOnePending() = runTest {
        repository.moveTimedEvent("remote-series", second, LocalDate.of(2026, 10, 13), LocalTime.of(14, 0), LocalTime.of(15, 0))
        repository.updateEventOccurrence(
            "remote-series",
            Instant.parse("2026-10-19T08:00:00Z").toEpochMilli(),
            eventPayload("Renamed once", LocalDate.of(2026, 10, 19), LocalTime.of(10, 0), LocalTime.of(11, 0), collectionHref = server.eventsHref),
        )

        val occurrences = repository.eventsSnapshot(rangeStart, rangeEnd).filter { it.resourceHref == seriesHref }
        val pending = harness.pendingMutations()
        assertEquals(
            listOf(false, true, true, false),
            occurrences.sortedBy { it.startsAtMillis }.map { pending.pendingMutationForEvent(it) != null },
        )
    }

    @Test
    fun seriesEditMarksEveryOccurrence() = runTest {
        repository.updateEvent(
            "remote-series",
            eventPayload("Renamed", LocalDate.of(2026, 10, 5), collectionHref = server.eventsHref, recurrenceRule = "FREQ=WEEKLY;COUNT=4"),
        )

        assertEquals(List(4) { "Renamed" to true }, pendingOccurrenceTitles())
    }

    @Test
    fun deletingTheSeriesFadesEveryOccurrence() = runTest {
        repository.deleteEvent("remote-series")

        val pending = harness.pendingMutations()
        val actions = repository.eventsSnapshot(rangeStart, rangeEnd)
            .filter { it.resourceHref == seriesHref }
            .map { pending.pendingMutationForEvent(it)?.action }
        assertEquals(List(4) { MutationAction.Delete }, actions)
    }

    @Test
    fun completedTaskOccurrenceIsTheOnlyOnePending() = runTest {
        repository.createTask(
            taskPayload("Water plants", collectionHref = server.tasksHref, dueDate = LocalDate.of(2026, 10, 5), dueTime = LocalTime.of(18, 0))
                .copy(recurrenceRule = "FREQ=DAILY;COUNT=3"),
        )
        repository.pushPendingChangesCreatedSince(0)
        val secondDue = harness.millis(LocalDate.of(2026, 10, 6), LocalTime.of(18, 0))
        val href = harness.tasksIn(server.tasksHref).single { it.title == "Water plants" }.resourceHref

        repository.setTaskOccurrenceStatus(href, secondDue, "COMPLETED")

        val pending = harness.pendingMutations()
        val occurrences = repository.datedTasksSnapshot(rangeStart, rangeEnd)
            .filter { it.resourceHref == href }
            .sortedBy { it.dueAtMillis }
        assertEquals(3, occurrences.size)
        assertNull(pending.pendingMutationForTask(occurrences[0]))
        assertNotNull(pending.pendingMutationForTask(occurrences[1]))
        assertNull(pending.pendingMutationForTask(occurrences[2]))
    }
}
