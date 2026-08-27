package com.kgs.calendar.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineOrientationZoomTest {
    @Test
    fun portraitAndLandscapeZoomValuesRemainIndependent() {
        val zoom = TimelineOrientationZoom(
            portraitHourHeightDp = 72f,
            landscapeHourHeightDp = 38f,
        )

        assertEquals(72f, zoom.hourHeightDp(isLandscape = false), 0.01f)
        assertEquals(38f, zoom.hourHeightDp(isLandscape = true), 0.01f)
        assertEquals(
            TimelineOrientationZoom(72f, 54f),
            zoom.withHourHeightDp(isLandscape = true, hourHeightDp = 54f),
        )
        assertEquals(72f, zoom.hourHeightDp(isLandscape = false), 0.01f)
    }

    @Test
    fun portraitAndLandscapeTopMinutesRemainIndependent() {
        val memory = TimelineOrientationViewportMemory()

        memory.updateTopMinute(isLandscape = false, topMinute = 8f * 60f)
        memory.updateTopMinute(isLandscape = true, topMinute = 14f * 60f)

        assertEquals(8f * 60f, memory.topMinute(isLandscape = false), 0.01f)
        assertEquals(14f * 60f, memory.topMinute(isLandscape = true), 0.01f)
    }

    @Test
    fun changingOrientationZoomKeepsTheTopVisibleMinuteAnchored() {
        val before = TimelineViewportState(hourHeightPx = 60f, scrollPx = 420f)

        val after = before.withHourHeightPx(
            hourHeightPx = 90f,
            viewportHeightPx = 600f,
            contentStartMinute = 0,
            contentEndMinute = 24 * 60,
        )

        assertEquals(
            before.minuteAt(viewportY = 0f, contentStartMinute = 0),
            after.minuteAt(viewportY = 0f, contentStartMinute = 0),
            0.01f,
        )
        assertEquals(630f, after.scrollPx, 0.01f)
    }

    @Test
    fun reprojectedScrollClampsToTheNewContentBounds() {
        val before = TimelineViewportState(hourHeightPx = 90f, scrollPx = 1_500f)

        val after = before.withHourHeightPx(
            hourHeightPx = 30f,
            viewportHeightPx = 600f,
            contentStartMinute = 0,
            contentEndMinute = 24 * 60,
        )

        assertEquals(120f, after.scrollPx, 0.01f)
    }
}
