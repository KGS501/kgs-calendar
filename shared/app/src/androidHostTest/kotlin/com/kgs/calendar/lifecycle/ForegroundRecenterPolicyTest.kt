package com.kgs.calendar.lifecycle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRecenterPolicyTest {
    private val policy = ForegroundRecenterPolicy()

    @Test
    fun recenterStartsAtExactlyTwoMinutes() {
        assertFalse(policy.shouldRecenter(backgroundedAt = 1_000, foregroundedAt = 120_999, explicitLaunchPending = false))
        assertTrue(policy.shouldRecenter(backgroundedAt = 1_000, foregroundedAt = 121_000, explicitLaunchPending = false))
    }

    @Test
    fun explicitLaunchWinsOverAutomaticRecenter() {
        assertFalse(policy.shouldRecenter(1_000, 500_000, explicitLaunchPending = true))
    }

    @Test
    fun missingOrFutureBackgroundTimestampDoesNotRecenter() {
        assertFalse(policy.shouldRecenter(null, 500_000, explicitLaunchPending = false))
        assertFalse(policy.shouldRecenter(600_000, 500_000, explicitLaunchPending = false))
    }
}
