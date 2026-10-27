package com.kgs.calendar.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPresetsTest {
    @Test
    fun theDefaultsAreFifteenMinutesTwoHoursOneDayAndOneWeek() {
        assertEquals(ReminderPresets(15, 2, 1, 1), ReminderPresets.decode(null))
    }

    @Test
    fun presetsSurviveEncodingAndUnreadablePartsFallBackOneByOne() {
        val presets = ReminderPresets(minutes = 30, hours = 6, days = 3, weeks = 2)
        assertEquals(presets, ReminderPresets.decode(presets.encode()))
        assertEquals(ReminderPresets(minutes = 5, hours = 2, days = 1, weeks = 1), ReminderPresets.decode("5,x"))
        // Out-of-range amounts are kept in range.
        assertEquals(ReminderPresets(minutes = 120, hours = 1, days = 30, weeks = 4), ReminderPresets.decode("500,0,99,9"))
    }
}
