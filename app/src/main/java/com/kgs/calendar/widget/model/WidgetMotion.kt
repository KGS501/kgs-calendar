package com.kgs.calendar.widget.model

import kotlin.math.roundToInt

internal fun priorityMotionFrameIntervalMillis(priority: Int?, intensity: Float, frameCount: Int): Int {
    val cycleMillis = (1050 - (intensity * 420f)).roundToInt().coerceAtLeast(520)
    return if (priority == 1) {
        42
    } else {
        ((cycleMillis * 2f) / frameCount.toFloat())
            .roundToInt()
            .coerceAtLeast(48)
    }
}

internal fun motionStandardEasing(fraction: Float): Float =
    cubicBezierEasing(fraction, x1 = 0.2f, y1 = 0f, x2 = 0f, y2 = 1f)

internal fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)

private fun cubicBezierEasing(fraction: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val target = fraction.coerceIn(0f, 1f)
    var low = 0f
    var high = 1f
    repeat(14) {
        val mid = (low + high) / 2f
        if (cubicBezierCoordinate(mid, x1, x2) < target) {
            low = mid
        } else {
            high = mid
        }
    }
    return cubicBezierCoordinate((low + high) / 2f, y1, y2)
}

private fun cubicBezierCoordinate(t: Float, p1: Float, p2: Float): Float {
    val inverse = 1f - t
    return 3f * inverse * inverse * t * p1 + 3f * inverse * t * t * p2 + t * t * t
}
