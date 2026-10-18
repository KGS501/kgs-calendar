package com.kgs.calendar.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReminderNotificationTextTest {
    @Test
    fun plainNotesKeepPlusAndPercentCharacters() {
        assertEquals("C++ review, 50% done", "C++ review, 50% done".reminderNotificationText())
        assertEquals("a+b=c", "  a+b=c \n".reminderNotificationText())
    }

    @Test
    fun htmlDataUrlIsDecodedAndFlattened() {
        assertEquals("Hello world", "data:text/html,%3Cb%3EHello%3C%2Fb%3E%20world".reminderNotificationText())
    }

    @Test
    fun blankNotesHaveNoText() {
        assertNull(" \n ".reminderNotificationText())
        assertNull(null.reminderNotificationText())
    }
}
