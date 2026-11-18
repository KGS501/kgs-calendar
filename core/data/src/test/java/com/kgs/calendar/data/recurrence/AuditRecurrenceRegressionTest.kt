package com.kgs.calendar.data.recurrence

import com.kgs.calendar.data.ical.EventRecurrenceOverride
import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.ical.TaskRecurrenceOverride
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditRecurrenceRegressionTest {
    private val utc = ZoneId.of("UTC")
    private val expander = RecurrenceExpander(utc)

    private fun millis(value: String) = kotlin.time.Instant.parse(value).toEpochMilliseconds()

    private fun event(start: String, rule: String?, overrides: String? = null, rDates: String? = null, exDates: String? = null) = EventEntity(
        uid = "u",
        collectionHref = "/c/",
        resourceHref = "/c/u.ics",
        title = "Series",
        description = null,
        location = null,
        startsAtMillis = millis(start),
        endsAtMillis = millis(start) + 3_600_000L,
        allDay = false,
        recurrenceRule = rule,
        isRecurring = true,
        recurrenceOverridesJson = overrides,
        rDatesCsv = rDates,
        exDatesCsv = exDates,
        timezoneId = "UTC",
        color = 0,
    )

    private fun moved(recurrenceId: String, to: String, status: String? = null) = RecurrenceOverrideCodec.encodeEvents(
        listOf(EventRecurrenceOverride.fromEvent(millis(recurrenceId), event(to, null).copy(status = status))),
    )

    private fun starts(event: EventEntity, from: String, to: String) =
        expander.expand(event, millis(from), millis(to)).map { it.startsAtMillis }

    // --- #73 ------------------------------------------------------------------------------------

    @Test
    fun aMovedOccurrenceShowsWhereItEndedUp() {
        val series = event("2026-11-02T09:00:00Z", "FREQ=DAILY;COUNT=2", moved("2026-11-03T09:00:00Z", "2026-11-20T09:00:00Z"))

        assertEquals(listOf(millis("2026-11-20T09:00:00Z")), starts(series, "2026-11-20T00:00:00Z", "2026-11-21T00:00:00Z"))
        assertTrue(starts(series, "2026-11-03T00:00:00Z", "2026-11-04T00:00:00Z").isEmpty())
    }

    @Test
    fun anOccurrenceCanMoveBeforeTheSeriesStart() {
        val series = event("2026-11-02T09:00:00Z", "FREQ=DAILY;COUNT=2", moved("2026-11-03T09:00:00Z", "2026-10-20T09:00:00Z"))

        assertEquals(listOf(millis("2026-10-20T09:00:00Z")), starts(series, "2026-10-20T00:00:00Z", "2026-10-21T00:00:00Z"))
    }

    @Test
    fun aMovedOccurrenceKeepsItsIdentity() {
        val series = event("2026-11-02T09:00:00Z", "FREQ=DAILY;COUNT=2", moved("2026-11-03T09:00:00Z", "2026-11-20T09:00:00Z"))

        val found = expander.expandWithIdentity(series, millis("2026-11-20T00:00:00Z"), millis("2026-11-21T00:00:00Z")).single()

        assertEquals(millis("2026-11-03T09:00:00Z"), found.occurrenceId.recurrenceIdMillis)
    }

    @Test
    fun aCancelledOccurrenceStaysAway() {
        val series = event("2026-11-02T09:00:00Z", "FREQ=DAILY;COUNT=2", moved("2026-11-03T09:00:00Z", "2026-11-20T09:00:00Z", "CANCELLED"))

        assertTrue(starts(series, "2026-11-20T00:00:00Z", "2026-11-21T00:00:00Z").isEmpty())
        assertEquals(listOf(millis("2026-11-02T09:00:00Z")), starts(series, "2026-11-01T00:00:00Z", "2026-11-30T00:00:00Z"))
    }

    @Test
    fun aMovedTaskOccurrenceShowsWhereItEndedUp() {
        val master = TaskEntity(
            uid = "t",
            collectionHref = "/c/",
            resourceHref = "/c/t.ics",
            title = "Task",
            notes = null,
            startAtMillis = null,
            completedAtMillis = null,
            isCompleted = false,
            priority = null,
            dueAtMillis = millis("2026-11-02T09:00:00Z"),
            dueHasTime = true,
            recurrenceRule = "FREQ=DAILY;COUNT=2",
            timezoneId = "UTC",
            color = 0,
        )
        val moved = master.copy(dueAtMillis = millis("2026-11-20T09:00:00Z"))
        val series = master.copy(
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeTasks(listOf(TaskRecurrenceOverride.fromTask(millis("2026-11-03T09:00:00Z"), moved))),
        )

        val found = TaskRecurrenceExpander(expander).expandWithIdentity(series, millis("2026-11-20T00:00:00Z"), millis("2026-11-21T00:00:00Z")).single()

        assertEquals(millis("2026-11-20T09:00:00Z"), found.item.dueAtMillis)
        assertEquals(millis("2026-11-03T09:00:00Z"), found.occurrenceId.recurrenceIdMillis)
    }

    // --- #72 ------------------------------------------------------------------------------------

    @Test
    fun exdateAppliesToRdateOnlySeries() {
        val extra = millis("2026-11-09T09:00:00Z")
        val series = event("2026-11-02T09:00:00Z", null, rDates = "$extra", exDates = "$extra")

        assertEquals(listOf(millis("2026-11-02T09:00:00Z")), starts(series, "2026-11-01T00:00:00Z", "2026-11-30T00:00:00Z"))
    }

    // --- #74 ------------------------------------------------------------------------------------

    @Test
    fun monthlyByMonthDayAndByDayIntersect() {
        val series = event("2026-06-01T09:00:00Z", "FREQ=MONTHLY;BYMONTHDAY=1;BYDAY=MO;COUNT=2")

        assertEquals(
            listOf("2026-06-01T09:00:00Z", "2027-02-01T09:00:00Z").map(::millis),
            starts(series, "2026-06-01T00:00:00Z", "2027-12-31T00:00:00Z"),
        )
    }

    @Test
    fun monthsWithoutTheDayAreSkippedAndNotCounted() {
        val series = event("2026-01-31T09:00:00Z", "FREQ=MONTHLY;COUNT=3")

        assertEquals(
            listOf("2026-01-31T09:00:00Z", "2026-03-31T09:00:00Z", "2026-05-31T09:00:00Z").map(::millis),
            starts(series, "2026-01-01T00:00:00Z", "2026-12-31T00:00:00Z"),
        )
    }

    @Test
    fun aLeapDaySeriesOnlyOccursInLeapYears() {
        val series = event("2028-02-29T09:00:00Z", "FREQ=YEARLY;COUNT=2")

        assertEquals(
            listOf("2028-02-29T09:00:00Z", "2032-02-29T09:00:00Z").map(::millis),
            starts(series, "2028-01-01T00:00:00Z", "2033-01-01T00:00:00Z"),
        )
    }

    @Test(timeout = 20_000)
    fun aMonthlyRuleThatNeverMatchesEnds() {
        val series = event("2026-01-31T09:00:00Z", "FREQ=MONTHLY;BYMONTHDAY=32")

        assertTrue(starts(series, "2026-01-01T00:00:00Z", "2026-12-31T00:00:00Z").isEmpty())
    }

    // --- #100 -------------------------------------------------------------------------------------

    @Test(timeout = 30_000)
    fun expensiveRulesStayWithinTheirBudget() {
        val window = "2026-11-02T00:00:00Z" to "2026-12-14T00:00:00Z"
        listOf(
            "1970-01-01T00:00:00Z" to "FREQ=SECONDLY",
            "2026-01-01T00:00:00Z" to "FREQ=SECONDLY",
            "2020-01-01T00:00:00Z" to "FREQ=MINUTELY;COUNT=2000000000",
            "2026-11-01T00:00:00Z" to "FREQ=MINUTELY",
            "2000-01-01T00:00:00Z" to "FREQ=YEARLY;BYHOUR=0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23;" +
                "BYMINUTE=" + (0..59).joinToString(",") + ";BYSECOND=" + (0..59).joinToString(","),
        ).forEach { (start, rule) ->
            // A series past the budget keeps only its first occurrence rather than a misleading part.
            expander.expand(event(start, rule), millis(window.first), millis(window.second))
        }
    }

    @Test
    fun aHighFrequencyRuleIsFastForwardedWithoutCount() {
        val series = event("1970-01-01T00:00:00Z", "FREQ=HOURLY;INTERVAL=6")

        val found = starts(series, "2026-11-02T00:00:00Z", "2026-11-03T00:00:00Z")

        assertEquals(listOf("00", "06", "12", "18"), found.map { kotlin.time.Instant.fromEpochMilliseconds(it).toString().substring(11, 13) })
    }

    @Test
    fun aRuleWithBySetPosIsStillExact() {
        val series = event("2026-01-01T09:00:00Z", "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1")

        assertEquals(listOf(millis("2026-11-30T09:00:00Z")), starts(series, "2026-11-01T00:00:00Z", "2026-12-01T00:00:00Z"))
    }
}

class RecurrenceBudgetTest {
    private val utc = ZoneId.of("UTC")
    private val start = kotlin.time.Instant.parse("2026-11-02T09:00:00Z").toEpochMilliseconds()

    private fun exceeded(rule: String?) = RecurrenceBudget.isExceeded(rule, false, start, utc, start)

    @Test
    fun onlyRulesTooFrequentToExpandAreFlagged() {
        assertTrue(exceeded("FREQ=MINUTELY"))
        assertTrue(exceeded("FREQ=SECONDLY"))
        listOf("FREQ=DAILY", "FREQ=WEEKLY;BYDAY=MO,WE", "FREQ=MONTHLY;BYMONTHDAY=31", "FREQ=HOURLY", null, "").forEach {
            assertEquals("Rule $it", false, exceeded(it))
        }
    }

    @Test
    fun anUnreadableRuleIsNotCalledTooFrequent() {
        assertEquals(false, exceeded("FREQ=SOMETIMES"))
    }
}
