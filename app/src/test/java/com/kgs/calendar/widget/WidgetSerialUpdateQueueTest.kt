package com.kgs.calendar.widget

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetSerialUpdateQueueTest {
    @Test
    fun sameKeyRunsSeriallyAndKeepsOnlyNewestPendingRequest() = runTest {
        val firstRelease = CompletableDeferred<Unit>()
        val processed = mutableListOf<Int>()
        var active = 0
        var maxActive = 0
        val superseded = mutableListOf<Int>()
        val queue = LatestPendingSerialQueue<String, Int>(
            scope = this,
            onSuperseded = { value -> superseded.add(value) },
        ) { value ->
            active += 1
            maxActive = maxOf(maxActive, active)
            processed += value
            if (value == 1) firstRelease.await()
            active -= 1
        }

        queue.submit("month:42", 1)
        runCurrent()
        queue.submit("month:42", 2)
        queue.submit("month:42", 3)
        firstRelease.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(1, 3), processed)
        assertEquals(listOf(2), superseded)
        assertEquals(1, maxActive)
    }

    @Test
    fun differentKeysCanRunConcurrently() = runTest {
        val release = CompletableDeferred<Unit>()
        val bothStarted = CompletableDeferred<Unit>()
        var active = 0
        var maxActive = 0
        val queue = LatestPendingSerialQueue<String, Int>(this) {
            active += 1
            maxActive = maxOf(maxActive, active)
            if (active == 2) bothStarted.complete(Unit)
            release.await()
            active -= 1
        }

        queue.submit("month:1", 1)
        queue.submit("month:2", 2)
        runCurrent()

        assertTrue(bothStarted.isCompleted)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, maxActive)
    }

    @Test
    fun cancellationDropsQueuedWorkAndCancelsRunningWork() = runTest {
        val started = CompletableDeferred<Unit>()
        val neverRelease = CompletableDeferred<Unit>()
        val processed = mutableListOf<Int>()
        val queue = LatestPendingSerialQueue<String, Int>(this) { value ->
            processed += value
            started.complete(Unit)
            neverRelease.await()
        }

        queue.submit("month:42", 1)
        runCurrent()
        queue.submit("month:42", 2)
        queue.cancel("month:42")
        advanceUntilIdle()

        assertEquals(listOf(1), processed)
    }
}
