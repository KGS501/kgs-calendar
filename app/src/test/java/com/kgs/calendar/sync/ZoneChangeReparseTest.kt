package com.kgs.calendar.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneChangeReparseTest {
    private val calls = mutableListOf<String>()
    private var recorded: String? = null

    private suspend fun run(stored: String?, current: String, failure: Throwable? = null) = reparseForZoneChangeIfNeeded(
        storedZoneId = stored,
        currentZoneId = current,
        reparseAllResources = {
            calls += "reparse"
            failure?.let { throw it }
        },
        reloadDeviceCalendars = { calls += "device" },
        recordZone = { recorded = it },
    )

    @Test
    fun theFirstCheckOnlyRecordsTheZone() = runTest {
        assertFalse(run(stored = null, current = "Europe/Berlin"))
        assertEquals(emptyList<String>(), calls)
        assertEquals("Europe/Berlin", recorded)
    }

    @Test
    fun anUnchangedZoneDoesNothing() = runTest {
        assertFalse(run(stored = "Europe/Berlin", current = "Europe/Berlin"))
        assertEquals(emptyList<String>(), calls)
        assertNull(recorded)
    }

    @Test
    fun aNewZoneRereadsTheDataAndRecordsIt() = runTest {
        assertTrue(run(stored = "Europe/Berlin", current = "America/New_York"))
        assertEquals(listOf("reparse", "device"), calls)
        assertEquals("America/New_York", recorded)
    }

    @Test
    fun aFailedPassIsNotRecordedSoItRunsAgain() = runTest {
        assertFalse(run(stored = "Europe/Berlin", current = "America/New_York", failure = IllegalStateException("disk")))
        assertNull(recorded)
    }
}
