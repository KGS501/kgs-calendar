package com.kgs.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadOnlyCalendarUrlTest {
    @Test
    fun webcalUrlsAreFetchedOverHttps() {
        assertEquals(
            "https://example.com/calendar.ics?token=abc",
            normalizeReadOnlyCalendarUrl("  WEBCAL://example.com/calendar.ics?token=abc  "),
        )
    }

    @Test
    fun httpAndHttpsUrlsRemainSupported() {
        assertEquals("https://example.com/feed.ics", normalizeReadOnlyCalendarUrl("https://example.com/feed.ics"))
        assertEquals("http://example.com/feed.ics", normalizeReadOnlyCalendarUrl("http://example.com/feed.ics"))
    }

    @Test
    fun unsupportedOrIncompleteUrlsAreRejected() {
        assertFalse(isSupportedReadOnlyCalendarUrl("example.com/feed.ics"))
        assertFalse(isSupportedReadOnlyCalendarUrl("webcal://"))
        assertTrue(isSupportedReadOnlyCalendarUrl("webcal://example.com/feed.ics"))
    }
}
