package com.kgs.calendar.widget

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPreparedValueTest {
    @Test
    fun preparedValueAvoidsFallbackLoad() = runTest {
        var loads = 0

        val value = preparedWidgetValue("prepared") {
            loads += 1
            "loaded"
        }

        assertEquals("prepared", value)
        assertEquals(0, loads)
    }

    @Test
    fun absentPreparedValueLoadsExactlyOnce() = runTest {
        var loads = 0

        val value = preparedWidgetValue<String>(null) {
            loads += 1
            "loaded"
        }

        assertEquals("loaded", value)
        assertEquals(1, loads)
    }

    @Test
    fun subtaskTransitionUsesCachedBeforeAndLoadsOnlyTarget() = runTest {
        var loads = 0

        val snapshots = loadSubtaskTransitionSnapshots("before") {
            loads += 1
            "target"
        }

        assertEquals("before", snapshots.before)
        assertEquals("target", snapshots.target)
        assertEquals(1, loads)
    }
}
