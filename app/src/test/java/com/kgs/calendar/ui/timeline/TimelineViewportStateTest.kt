package com.kgs.calendar.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineViewportStateTest {
    private fun snapshot(
        upperY: Float = 100f,
        lowerY: Float = 200f,
        hourHeightPx: Float = 60f,
        scrollPx: Float = 300f,
    ): PinchSnapshot = PinchSnapshot.begin(
        upperY = upperY,
        lowerY = lowerY,
        viewport = TimelineViewportState(hourHeightPx = hourHeightPx, scrollPx = scrollPx),
        contentStartMinute = 0,
        contentEndMinute = 24 * 60,
        viewportHeightPx = 600f,
        minHourHeightPx = 30f,
        maxHourHeightPx = 180f,
    )

    @Test
    fun stationaryUpperFingerKeepsItsContentMinuteAnchored() {
        val start = snapshot()

        val result = updateVerticalPinch(start, upperY = 100f, lowerY = 300f)

        assertEquals(start.anchorMinute, result.minuteAtCentroid, 0.01f)
        assertEquals(120f, result.viewport.hourHeightPx, 0.01f)
    }

    @Test
    fun stationaryLowerFingerKeepsItsContentMinuteAnchored() {
        val start = snapshot()

        val result = updateVerticalPinch(start, upperY = 50f, lowerY = 200f)

        assertEquals(start.anchorMinute, result.minuteAtCentroid, 0.01f)
        assertEquals(90f, result.viewport.hourHeightPx, 0.01f)
    }

    @Test
    fun updatesAreCumulativeFromGestureStartInsteadOfPreviousFrame() {
        val start = snapshot()

        updateVerticalPinch(start, upperY = 90f, lowerY = 220f)
        val final = updateVerticalPinch(start, upperY = 50f, lowerY = 250f)

        assertEquals(120f, final.viewport.hourHeightPx, 0.01f)
        assertEquals(start.anchorMinute, final.minuteAtCentroid, 0.01f)
    }

    @Test
    fun hourHeightClampsToConfiguredLimits() {
        val start = snapshot()

        val minimum = updateVerticalPinch(start, upperY = 145f, lowerY = 155f)
        val maximum = updateVerticalPinch(start, upperY = -100f, lowerY = 400f)

        assertEquals(30f, minimum.viewport.hourHeightPx, 0.01f)
        assertEquals(180f, maximum.viewport.hourHeightPx, 0.01f)
        assertEquals(start.anchorMinute, minimum.minuteAtCentroid, 0.01f)
        assertEquals(start.anchorMinute, maximum.minuteAtCentroid, 0.01f)
    }

    @Test
    fun scrollClampsAtContentEdges() {
        val start = snapshot(hourHeightPx = 60f, scrollPx = 0f)

        val result = updateVerticalPinch(start, upperY = 500f, lowerY = 700f)

        assertEquals(0f, result.viewport.scrollPx, 0.01f)
        assertEquals(120f, result.viewport.hourHeightPx, 0.01f)
    }
}
