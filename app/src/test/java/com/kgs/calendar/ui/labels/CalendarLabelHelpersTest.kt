package com.kgs.calendar.ui.labels

import com.kgs.calendar.domain.model.REMINDER_AT_END
import com.kgs.calendar.domain.model.REMINDER_AT_START
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarLabelHelpersTest {
    @Test
    fun recurrenceLabelSummarizesFrequencyIntervalDaysCountAndUntil() {
        val label = "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;COUNT=5;UNTIL=20260731T120000Z".toRecurrenceLabel()

        assertEquals("Weekly every 2 on Mon, Wed 5 times until 2026-07-31", label)
    }

    @Test
    fun recurrenceOptionDetectsSimpleAndCustomRules() {
        assertEquals(RecurrenceOption.Once, "".toRecurrenceOption())
        assertEquals(RecurrenceOption.Weekly, "FREQ=WEEKLY".toRecurrenceOption())
        assertEquals(RecurrenceOption.Custom, "FREQ=WEEKLY;BYDAY=MO".toRecurrenceOption())
    }

    @Test
    fun reminderParsingNormalizesSupportedOffsets() {
        assertEquals(setOf(REMINDER_AT_START, 15, 60, REMINDER_AT_END), "60, 15, bad, -1, 0, 15, 999999".parseReminderMinutes())
    }

    @Test
    fun reminderSummaryUsesExistingPlainTextLabels() {
        assertEquals("At end, At start, 15 min before, 1 hr before", "0,15,60,-1".reminderSummary())
        assertNull("bad".reminderSummary())
    }
}
