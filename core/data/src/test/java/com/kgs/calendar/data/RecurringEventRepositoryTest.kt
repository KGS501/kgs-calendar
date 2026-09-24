package com.kgs.calendar.data

import com.kgs.calendar.data.local.entity.EventEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class RecurringEventRepositoryTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val firstDay = LocalDate.of(2026, 10, 5)
    private val standupTime = LocalTime.of(10, 0)
    private lateinit var master: EventEntity

    @Before
    fun setUp() = runTest {
        repository.ensureLocalCalendar()
        repository.createEvent(
            eventPayload("Standup", firstDay, standupTime, LocalTime.of(10, 15), recurrenceRule = "FREQ=DAILY;COUNT=5"),
        )
        master = harness.eventsIn(LOCAL_COLLECTION).single()
    }

    @After
    fun tearDown() = harness.close()

    private fun at(date: LocalDate, time: LocalTime = standupTime) = harness.millis(date, time)

    private suspend fun snapshot(from: LocalDate, toExclusive: LocalDate) =
        repository.eventsSnapshot(harness.startOfDay(from), harness.startOfDay(toExclusive))

    @Test
    fun observeEventsExpandsRecurringMastersWithinRangeAndSortsWithSingleEvents() = runTest {
        repository.createEvent(eventPayload("Lunch", firstDay.plusDays(2), LocalTime.of(12, 0), LocalTime.of(13, 0)))
        val rangeStart = harness.startOfDay(firstDay.plusDays(1))
        val rangeEnd = harness.startOfDay(firstDay.plusDays(4))

        val observed = repository.observeEvents(rangeStart, rangeEnd).first()

        assertTrue(master.isRecurring)
        assertEquals("FREQ=DAILY;COUNT=5", master.recurrenceRule)
        assertEquals(
            listOf(
                "Standup" to at(firstDay.plusDays(1)),
                "Standup" to at(firstDay.plusDays(2)),
                "Lunch" to at(firstDay.plusDays(2), LocalTime.of(12, 0)),
                "Standup" to at(firstDay.plusDays(3)),
            ),
            observed.map { it.title to it.startsAtMillis },
        )
        assertTrue(observed.filter { it.title == "Standup" }.all { it.endsAtMillis - it.startsAtMillis == 15 * 60_000L })
        assertTrue(observed.filter { it.title == "Standup" }.all { it.resourceHref == master.resourceHref })
        assertEquals(observed, repository.eventsSnapshot(rangeStart, rangeEnd))
    }

    @Test
    fun countLimitsExpansionAndDisabledCalendarsAreHidden() = runTest {
        assertEquals(5, snapshot(firstDay.minusDays(3), firstDay.plusDays(30)).size)

        repository.setCollectionEnabled(LOCAL_COLLECTION, false)

        assertTrue(snapshot(firstDay.minusDays(3), firstDay.plusDays(30)).isEmpty())
        assertTrue(repository.observeEvents(harness.startOfDay(firstDay), harness.startOfDay(firstDay.plusDays(30))).first().isEmpty())
    }

    @Test
    fun deleteEventOccurrenceAddsExdateToMaster() = runTest {
        val removed = at(firstDay.plusDays(2))

        repository.deleteEventOccurrence(master.uid, removed)

        val updated = harness.event(master.uid)!!
        assertEquals(removed.toString(), updated.exDatesCsv)
        assertEquals("FREQ=DAILY;COUNT=5", updated.recurrenceRule)
        assertEquals(1, updated.sequence)
        val raw = harness.resource(master.resourceHref)!!.rawIcs.unfoldedIcs()
        assertTrue(raw.lines().any { it.startsWith("EXDATE") && it.endsWith("20261007T100000") })
        assertEquals(
            listOf(firstDay, firstDay.plusDays(1), firstDay.plusDays(3), firstDay.plusDays(4)).map { at(it) },
            snapshot(firstDay, firstDay.plusDays(10)).map { it.startsAtMillis },
        )
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun updateEventOccurrenceStoresOverrideForThatOccurrenceOnly() = runTest {
        val occurrence = at(firstDay.plusDays(2))

        repository.updateEventOccurrence(
            master.uid,
            occurrence,
            eventPayload("Moved standup", firstDay.plusDays(2), LocalTime.of(15, 0), LocalTime.of(15, 30)),
        )

        val updated = harness.event(master.uid)!!
        assertEquals("Standup", updated.title)
        assertEquals("FREQ=DAILY;COUNT=5", updated.recurrenceRule)
        assertNotNull(updated.recurrenceOverridesJson)
        assertNull(updated.exDatesCsv)
        assertEquals(1, updated.sequence)
        val raw = harness.resource(master.resourceHref)!!.rawIcs.unfoldedIcs()
        assertTrue(raw.contains("RECURRENCE-ID"))
        assertTrue(raw.contains("SUMMARY:Moved standup"))
        val expanded = snapshot(firstDay, firstDay.plusDays(10))
        assertEquals(
            listOf(
                "Standup" to at(firstDay),
                "Standup" to at(firstDay.plusDays(1)),
                "Moved standup" to at(firstDay.plusDays(2), LocalTime.of(15, 0)),
                "Standup" to at(firstDay.plusDays(3)),
                "Standup" to at(firstDay.plusDays(4)),
            ),
            expanded.map { it.title to it.startsAtMillis },
        )
    }

    @Test
    fun deleteEventFollowingCapsRuleWithUntilBeforeTheOccurrence() = runTest {
        repository.deleteEventOccurrence(master.uid, at(firstDay.plusDays(4)))
        val cut = at(firstDay.plusDays(3))

        repository.deleteEventFollowing(master.uid, cut)

        val updated = harness.event(master.uid)!!
        // 2026-10-08 10:00 Europe/Berlin is 08:00Z; UNTIL is one second earlier and COUNT is dropped.
        assertEquals("FREQ=DAILY;UNTIL=20261008T075959Z", updated.recurrenceRule)
        assertTrue(updated.isRecurring)
        assertNull(updated.exDatesCsv)
        assertEquals(2, updated.sequence)
        assertTrue(harness.resource(master.resourceHref)!!.rawIcs.unfoldedIcs().contains("RRULE:FREQ=DAILY;UNTIL=20261008T075959Z"))
        assertEquals(
            listOf(firstDay, firstDay.plusDays(1), firstDay.plusDays(2)).map { at(it) },
            snapshot(firstDay, firstDay.plusDays(10)).map { it.startsAtMillis },
        )
    }

    @Test
    fun deleteEventFollowingFromFirstOccurrenceDeletesTheSeries() = runTest {
        repository.deleteEventFollowing(master.uid, master.startsAtMillis)

        assertNull(harness.event(master.uid))
        assertNull(harness.resource(master.resourceHref))
        assertTrue(snapshot(firstDay, firstDay.plusDays(10)).isEmpty())
    }

    @Test
    fun deleteEventOccurrenceOnSingleEventDeletesIt() = runTest {
        repository.createEvent(eventPayload("Once", firstDay.plusDays(1), LocalTime.of(18, 0), LocalTime.of(19, 0)))
        val single = harness.eventsIn(LOCAL_COLLECTION).single { it.title == "Once" }

        repository.deleteEventOccurrence(single.uid, single.startsAtMillis)

        assertNull(harness.event(single.uid))
        assertFalse(snapshot(firstDay, firstDay.plusDays(10)).any { it.title == "Once" })
    }
}
