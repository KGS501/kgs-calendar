package com.kgs.calendar.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSheetScaffoldTest {
    @Test
    fun sharedPopoverViewportRespectsTheKeyboardInset() {
        assertTrue(sheetInsetPolicy.respectsImeBottomInset)
    }
}
