package com.kgs.calendar.lifecycle

class ForegroundRecenterPolicy(
    private val thresholdMillis: Long = DEFAULT_THRESHOLD_MILLIS,
) {
    fun shouldRecenter(
        backgroundedAt: Long?,
        foregroundedAt: Long,
        explicitLaunchPending: Boolean,
    ): Boolean {
        if (explicitLaunchPending || backgroundedAt == null) return false
        val elapsed = foregroundedAt - backgroundedAt
        return elapsed >= thresholdMillis
    }

    companion object {
        const val DEFAULT_THRESHOLD_MILLIS = 120_000L
    }
}
