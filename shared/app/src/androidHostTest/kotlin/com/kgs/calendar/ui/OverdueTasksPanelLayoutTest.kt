package com.kgs.calendar.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class OverdueTasksPanelLayoutTest {
    @Test
    fun expandedHeightWrapsOneTaskAndFooter() {
        assertEquals(58.dp, overdueTasksPanelContentHeight(taskCount = 1))
    }

    @Test
    fun expandedHeightAddsOnlyOneRowAndGapPerAdditionalTask() {
        assertEquals(87.dp, overdueTasksPanelContentHeight(taskCount = 2))
        assertEquals(116.dp, overdueTasksPanelContentHeight(taskCount = 3))
    }

    @Test
    fun narrowMultiDayColumnExpandsToTwoFifthsOfViewport() {
        val geometry = expandedOverduePanelGeometry(
            fullLeftPx = 270f,
            fullWidthPx = 90f,
            viewportWidthPx = 720f,
        )

        assertEquals(171f, geometry.leftPx, 0.001f)
        assertEquals(288f, geometry.widthPx, 0.001f)
    }

    @Test
    fun expandedPanelGrowsInwardAtViewportEdges() {
        val leftGeometry = expandedOverduePanelGeometry(
            fullLeftPx = 0f,
            fullWidthPx = 90f,
            viewportWidthPx = 720f,
        )
        val rightGeometry = expandedOverduePanelGeometry(
            fullLeftPx = 630f,
            fullWidthPx = 90f,
            viewportWidthPx = 720f,
        )

        assertEquals(0f, leftGeometry.leftPx, 0.001f)
        assertEquals(432f, rightGeometry.leftPx, 0.001f)
        assertEquals(288f, leftGeometry.widthPx, 0.001f)
        assertEquals(288f, rightGeometry.widthPx, 0.001f)
    }

    @Test
    fun oneDayPanelDoesNotShrinkWhenAlreadyWiderThanTargetFraction() {
        val geometry = expandedOverduePanelGeometry(
            fullLeftPx = 0f,
            fullWidthPx = 640f,
            viewportWidthPx = 640f,
        )

        assertEquals(0f, geometry.leftPx, 0.001f)
        assertEquals(640f, geometry.widthPx, 0.001f)
    }
}
