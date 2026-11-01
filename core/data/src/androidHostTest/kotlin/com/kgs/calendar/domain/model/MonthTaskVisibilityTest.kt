package com.kgs.calendar.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthTaskVisibilityTest {
    @Test fun activeTaskIsVisible() {
        assertTrue(isMonthSurfaceTaskVisible(isCompleted = false, status = "NEEDS-ACTION"))
    }

    @Test fun completedBooleanAndTerminalStatusesAreHidden() {
        assertFalse(isMonthSurfaceTaskVisible(isCompleted = true, status = "NEEDS-ACTION"))
        assertFalse(isMonthSurfaceTaskVisible(isCompleted = false, status = "completed"))
        assertFalse(isMonthSurfaceTaskVisible(isCompleted = false, status = "CANCELLED"))
    }
}
