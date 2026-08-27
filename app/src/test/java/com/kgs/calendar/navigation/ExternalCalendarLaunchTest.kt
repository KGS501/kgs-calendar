package com.kgs.calendar.navigation

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalCalendarLaunchTest {
    private val zoneId = ZoneId.of("Europe/Berlin")
    private val launchMillis = LocalDate.of(2026, 8, 21).atTime(14, 30).atZone(zoneId).toInstant().toEpochMilli()

    @Test
    fun calendarTimeViewIntentOpensItsDate() {
        assertEquals(
            LocalDate.of(2026, 8, 21),
            externalCalendarLaunchDate(
                action = "android.intent.action.VIEW",
                uriValue = "content://com.android.calendar/time/$launchMillis",
                beginTimeMillis = null,
                zoneId = zoneId,
            ),
        )
    }

    @Test
    fun eventBeginExtraProvidesFallbackDate() {
        assertEquals(
            LocalDate.of(2026, 8, 21),
            externalCalendarLaunchDate(
                action = "android.intent.action.VIEW",
                uriValue = "content://com.android.calendar/events/42",
                beginTimeMillis = launchMillis,
                zoneId = zoneId,
            ),
        )
    }

    @Test
    fun unrelatedIntentsAreIgnored() {
        assertNull(externalCalendarLaunchDate("android.intent.action.MAIN", null, launchMillis, zoneId))
        assertNull(externalCalendarLaunchDate("android.intent.action.VIEW", "https://example.com", null, zoneId))
    }
}
