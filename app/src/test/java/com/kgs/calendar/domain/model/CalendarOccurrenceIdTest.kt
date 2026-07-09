package com.kgs.calendar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CalendarOccurrenceIdTest {
    @Test
    fun differentRecurringOccurrencesHaveDifferentStableKeys() {
        val first = CalendarOccurrenceId.Task("tasks/42.ics", 1_700_000_000_000)
        val second = CalendarOccurrenceId.Task("tasks/42.ics", 1_700_086_400_000)

        assertNotEquals(first.stableKey, second.stableKey)
    }

    @Test
    fun stableKeyKeepsResourceBoundariesUnambiguous() {
        val first = CalendarOccurrenceId.Event("a:12:b", 34)
        val second = CalendarOccurrenceId.Event("a", 12_000_000_000_034)

        assertNotEquals(first.stableKey, second.stableKey)
        assertEquals("event:6:a:12:b:34", first.stableKey)
    }
}
