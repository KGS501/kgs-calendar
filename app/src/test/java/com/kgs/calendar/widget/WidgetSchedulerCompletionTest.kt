package com.kgs.calendar.widget

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetSchedulerCompletionTest {
    @Test
    fun awaitScheduledCompletionsWaitsForEveryCallback() = runTest {
        val callbacks = mutableListOf<() -> Unit>()
        var completed = false
        backgroundScope.launch {
            awaitScheduledCompletions(2) { callback -> callbacks += callback }
            completed = true
        }
        runCurrent()

        assertFalse(completed)
        callbacks[0]()
        runCurrent()
        assertFalse(completed)

        callbacks[1]()
        runCurrent()
        assertTrue(completed)
    }

    @Test
    fun awaitScheduledCompletionsReturnsImmediatelyForNoWork() = runTest {
        var enqueued = false

        awaitScheduledCompletions(0) { enqueued = true }

        assertFalse(enqueued)
    }
}
