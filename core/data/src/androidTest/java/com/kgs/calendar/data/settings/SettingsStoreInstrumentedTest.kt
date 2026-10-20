package com.kgs.calendar.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun weekSettingsRoundTripWithoutChangingEachOther() = runBlocking {
        val store = SettingsStore(context)
        val originalWeekView = store.weekViewEnabled.first()
        val originalFullWeekSwipe = store.fullWeekSwipeEnabled.first()
        val originalShowCalendarWeeks = store.showCalendarWeeks.first()
        try {
            store.setWeekViewEnabled(true)
            store.setFullWeekSwipeEnabled(false)
            store.setShowCalendarWeeks(true)
            assertTrue(store.weekViewEnabled.first())
            assertFalse(store.fullWeekSwipeEnabled.first())
            assertTrue(store.showCalendarWeeks.first())

            store.setWeekViewEnabled(false)
            store.setShowCalendarWeeks(false)
            assertFalse(store.weekViewEnabled.first())
            assertFalse(store.fullWeekSwipeEnabled.first())
            assertFalse(store.showCalendarWeeks.first())
        } finally {
            store.setWeekViewEnabled(originalWeekView)
            store.setFullWeekSwipeEnabled(originalFullWeekSwipe)
            store.setShowCalendarWeeks(originalShowCalendarWeeks)
        }
    }

    @Test
    fun monthWidgetWeeksRoundTrip() = runBlocking {
        val store = SettingsStore(context)
        val original = store.monthWidgetWeeks.first()
        try {
            store.setMonthWidgetWeeks(WidgetMonthWeeks.Two)
            assertEquals(WidgetMonthWeeks.Two, store.monthWidgetWeeks.first())

            store.setMonthWidgetWeeks(WidgetMonthWeeks.FullMonth)
            assertEquals(WidgetMonthWeeks.FullMonth, store.monthWidgetWeeks.first())
        } finally {
            store.setMonthWidgetWeeks(original)
        }
    }

    @Test
    fun multiWidgetWeeksRoundTripWithoutChangingMonthWidget() = runBlocking {
        val store = SettingsStore(context)
        val originalMonth = store.monthWidgetWeeks.first()
        val originalMulti = store.multiWidgetWeeks.first()
        try {
            store.setMonthWidgetWeeks(WidgetMonthWeeks.Four)
            store.setMultiWidgetWeeks(WidgetMonthWeeks.Two)

            assertEquals(WidgetMonthWeeks.Four, store.monthWidgetWeeks.first())
            assertEquals(WidgetMonthWeeks.Two, store.multiWidgetWeeks.first())
        } finally {
            store.setMonthWidgetWeeks(originalMonth)
            store.setMultiWidgetWeeks(originalMulti)
        }
    }

    @Test
    fun portraitAndLandscapeTimelineZoomRoundTripWithoutChangingEachOther() = runBlocking {
        val store = SettingsStore(context)
        val originalPortrait = store.portraitTimelineHourHeightDp.first()
        val originalLandscape = store.landscapeTimelineHourHeightDp.first()
        try {
            store.setPortraitTimelineHourHeightDp(72f)
            store.setLandscapeTimelineHourHeightDp(34f)

            assertEquals(72f, store.portraitTimelineHourHeightDp.first(), 0.01f)
            assertEquals(34f, store.landscapeTimelineHourHeightDp.first(), 0.01f)

            store.setLandscapeTimelineHourHeightDp(51f)
            assertEquals(72f, store.portraitTimelineHourHeightDp.first(), 0.01f)
            assertEquals(51f, store.landscapeTimelineHourHeightDp.first(), 0.01f)
        } finally {
            store.setPortraitTimelineHourHeightDp(originalPortrait)
            store.setLandscapeTimelineHourHeightDp(originalLandscape)
        }
    }
}
