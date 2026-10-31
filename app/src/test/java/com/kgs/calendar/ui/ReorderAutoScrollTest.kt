package com.kgs.calendar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A row dragged to the page's edge scrolls the page, faster the deeper it is in the edge. */
class ReorderAutoScrollTest {
    private val viewportTop = 100f
    private val viewportBottom = 1100f
    private val edge = 100f
    private val maxSpeed = 1000f

    private fun speed(rowTop: Float, rowHeight: Float = 60f) =
        reorderAutoScrollSpeed(rowTop, rowTop + rowHeight, viewportTop, viewportBottom, edge, maxSpeed)

    @Test
    fun aRowAwayFromTheEdgesDoesNotScroll() {
        assertEquals(0f, speed(rowTop = 500f), 0f)
        assertEquals(0f, speed(rowTop = viewportTop + edge), 0f)
        assertEquals(0f, speed(rowTop = viewportBottom - edge - 60f), 0f)
    }

    @Test
    fun aRowInTheTopEdgeScrollsUpFasterTheDeeperItIs() {
        val shallow = speed(rowTop = viewportTop + 75f)
        val deep = speed(rowTop = viewportTop + 25f)
        assertTrue(shallow < 0f)
        assertTrue(deep < shallow)
        assertEquals(-maxSpeed, speed(rowTop = viewportTop), 0f)
        // Dragged past the edge, the speed stays at its maximum.
        assertEquals(-maxSpeed, speed(rowTop = 0f), 0f)
    }

    @Test
    fun aRowInTheBottomEdgeScrollsDown() {
        assertEquals(maxSpeed / 2, speed(rowTop = viewportBottom - 50f - 60f), 0.01f)
        assertEquals(maxSpeed, speed(rowTop = viewportBottom), 0f)
    }

    @Test
    fun aViewportWithNoRoomBetweenItsEdgesNeverScrolls() {
        assertEquals(0f, reorderAutoScrollSpeed(100f, 160f, 100f, 280f, 100f, maxSpeed), 0f)
    }
}
