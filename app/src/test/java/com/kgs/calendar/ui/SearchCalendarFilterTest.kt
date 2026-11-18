package com.kgs.calendar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchCalendarFilterTest {
    private val all = setOf("/work/", "/home/", "/club/")

    @Test
    fun fromAllCalendarsATapSearchesOnlyThatCalendar() {
        assertEquals(setOf("/work/"), toggledSearchCalendars(null, "/work/", all))
    }

    @Test
    fun severalCalendarsCanBePickedAndPickingAllAgainMeansAll() {
        val two = toggledSearchCalendars(setOf("/work/"), "/home/", all)
        assertEquals(setOf("/work/", "/home/"), two)
        assertNull(toggledSearchCalendars(two, "/club/", all))
    }

    @Test
    fun leavingOutTheLastCalendarSearchesAllAgain() {
        assertNull(toggledSearchCalendars(setOf("/work/"), "/work/", all))
    }
}
