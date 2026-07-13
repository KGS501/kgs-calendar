package com.kgs.calendar.ui

import com.kgs.calendar.R
import com.kgs.calendar.domain.model.CalendarViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineViewPresentationTest {
    @Test
    fun timelineDestinationLabelFollowsWeekSetting() {
        assertEquals(R.string.three_days, CalendarViewMode.ThreeDay.labelRes(false))
        assertEquals(R.string.week, CalendarViewMode.ThreeDay.labelRes(true))
        assertEquals(R.string.day, CalendarViewMode.Day.labelRes(true))
    }

    @Test
    fun weekSettingsHideMultipleDayControls() {
        val week = timelineSettingsVisibility(weekViewEnabled = true, sidebarControlsEnabled = true)
        assertTrue(week.showFullWeekSwipe)
        assertFalse(week.showMultiDayControls)
        assertFalse(week.showMultiDayCount)

        val multipleDays = timelineSettingsVisibility(weekViewEnabled = false, sidebarControlsEnabled = false)
        assertFalse(multipleDays.showFullWeekSwipe)
        assertTrue(multipleDays.showMultiDayControls)
        assertTrue(multipleDays.showMultiDayCount)

        val sidebarControls = timelineSettingsVisibility(weekViewEnabled = false, sidebarControlsEnabled = true)
        assertTrue(sidebarControls.showMultiDayControls)
        assertFalse(sidebarControls.showMultiDayCount)
    }
}
