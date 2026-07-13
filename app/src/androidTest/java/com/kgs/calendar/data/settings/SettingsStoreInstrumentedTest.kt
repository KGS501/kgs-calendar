package com.kgs.calendar.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        try {
            store.setWeekViewEnabled(true)
            store.setFullWeekSwipeEnabled(false)
            assertTrue(store.weekViewEnabled.first())
            assertFalse(store.fullWeekSwipeEnabled.first())

            store.setWeekViewEnabled(false)
            assertFalse(store.weekViewEnabled.first())
            assertFalse(store.fullWeekSwipeEnabled.first())
        } finally {
            store.setWeekViewEnabled(originalWeekView)
            store.setFullWeekSwipeEnabled(originalFullWeekSwipe)
        }
    }
}
