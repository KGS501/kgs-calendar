package com.kgs.calendar.reminder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WindowsToastTagTest {
    @Test fun theSameKeyAlwaysGivesTheSameShortTag() {
        val key = "reminder:task:calendars/personal/9f3c6a1e-2b7d-4c8e-a5f0-1d2e3f4a5b6c.ics"
        val tag = windowsToastTag(key)
        assertEquals(tag, windowsToastTag(key))
        assertEquals(16, tag.length)
        assertTrue(tag.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test fun differentItemsGetDifferentTags() {
        assertNotEquals(windowsToastTag("reminder:task:a.ics"), windowsToastTag("reminder:task:b.ics"))
    }

    /** Pinned, so a notification shown by one version is still replaced after an update. */
    @Test fun theHashIsStandardFnv1a() {
        assertEquals("cbf29ce484222325", windowsToastTag(""))
        assertEquals("af63dc4c8601ec8c", windowsToastTag("a"))
    }
}
