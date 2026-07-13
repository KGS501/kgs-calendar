package com.kgs.calendar.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStoreNormalizationTest {
    @Test
    fun dayWidgetStartHourIsClampedToTheDay() {
        assertEquals(0, SettingsStore.normalizeDayWidgetStartHour(-1))
        assertEquals(7, SettingsStore.normalizeDayWidgetStartHour(7))
        assertEquals(23, SettingsStore.normalizeDayWidgetStartHour(24))
    }

    @Test
    fun dayWidgetScaleSupportsTheFullConfiguredRange() {
        assertEquals(50, SettingsStore.normalizeDayWidgetScalePercent(25))
        assertEquals(100, SettingsStore.normalizeDayWidgetScalePercent(100))
        assertEquals(200, SettingsStore.normalizeDayWidgetScalePercent(250))
    }

    @Test
    fun weekSettingsHaveCompatibilitySafeDefaults() {
        assertEquals(false, SettingsStore.DEFAULT_WEEK_VIEW_ENABLED)
        assertEquals(true, SettingsStore.DEFAULT_FULL_WEEK_SWIPE_ENABLED)
    }
}
