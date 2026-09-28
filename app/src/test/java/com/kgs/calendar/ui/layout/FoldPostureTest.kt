package com.kgs.calendar.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class FoldPostureTest {
    @Test
    fun noFoldIsNormal() {
        assertEquals(FoldPosture.Normal, posture(emptyList()))
    }

    @Test
    fun flatFoldKeepsTheRegularLayout() {
        val flat = horizontalFold(top = 884, bottom = 884).copy(halfOpened = false)

        assertEquals(FoldPosture.Normal, posture(listOf(flat)))
    }

    @Test
    fun halfOpenedHorizontalFoldIsTabletop() {
        assertEquals(
            FoldPosture.Tabletop(hingeTopPx = 884f, hingeBottomPx = 884f),
            posture(listOf(horizontalFold(top = 884, bottom = 884)), width = 2208, height = 1768),
        )
    }

    @Test
    fun halfOpenedVerticalFoldIsBook() {
        assertEquals(
            FoldPosture.Book(hingeLeftPx = 884f, hingeRightPx = 884f),
            posture(listOf(verticalFold(left = 884, right = 884)), width = 1768, height = 2208),
        )
    }

    @Test
    fun occludingHingeKeepsItsWholeBand() {
        // A physical hinge 40 px tall: content stays above its top and below its bottom edge.
        assertEquals(
            FoldPosture.Tabletop(hingeTopPx = 864f, hingeBottomPx = 904f),
            posture(listOf(horizontalFold(top = 864, bottom = 904)), width = 2208, height = 1768),
        )
    }

    @Test
    fun hingeIsConvertedIntoRootCoordinates() {
        // The Compose root starts 100 px below and 30 px right of the window's origin.
        assertEquals(
            FoldPosture.Tabletop(hingeTopPx = 784f, hingeBottomPx = 784f),
            posture(listOf(horizontalFold(top = 884, bottom = 884)), rootLeft = 30f, rootTop = 100f),
        )
        assertEquals(
            FoldPosture.Book(hingeLeftPx = 854f, hingeRightPx = 854f),
            posture(listOf(verticalFold(left = 884, right = 884)), rootLeft = 30f, rootTop = 100f),
        )
    }

    @Test
    fun foldNearTheWindowEdgeKeepsTheRegularLayout() {
        // A window that only just reaches past the hinge (e.g. a small split-screen window).
        assertEquals(
            FoldPosture.Normal,
            posture(listOf(horizontalFold(top = 100, bottom = 100)), width = 2208, height = 1768),
        )
        assertEquals(
            FoldPosture.Normal,
            posture(listOf(verticalFold(left = 1700, right = 1700)), width = 1768, height = 2208),
        )
    }

    @Test
    fun foldOutsideTheRootKeepsTheRegularLayout() {
        assertEquals(
            FoldPosture.Normal,
            posture(listOf(horizontalFold(top = 2400, bottom = 2400)), width = 2208, height = 1768),
        )
    }

    @Test
    fun unmeasuredRootIsNormal() {
        assertEquals(
            FoldPosture.Normal,
            posture(listOf(horizontalFold(top = 884, bottom = 884)), width = 0, height = 0),
        )
    }

    @Test
    fun theHalfOpenedFeatureWinsOverAFlatOne() {
        assertEquals(
            FoldPosture.Tabletop(hingeTopPx = 884f, hingeBottomPx = 884f),
            posture(
                listOf(
                    verticalFold(left = 1104, right = 1104).copy(halfOpened = false),
                    horizontalFold(top = 884, bottom = 884),
                ),
                width = 2208,
                height = 1768,
            ),
        )
    }

    private fun posture(
        features: List<FoldFeatureSnapshot>,
        width: Int = 2208,
        height: Int = 1768,
        rootLeft: Float = 0f,
        rootTop: Float = 0f,
    ): FoldPosture = foldPostureOf(
        features = features,
        rootLeftInWindowPx = rootLeft,
        rootTopInWindowPx = rootTop,
        rootWidthPx = width,
        rootHeightPx = height,
    )

    private fun horizontalFold(top: Int, bottom: Int) = FoldFeatureSnapshot(
        halfOpened = true,
        horizontal = true,
        left = 0,
        top = top,
        right = 2208,
        bottom = bottom,
    )

    private fun verticalFold(left: Int, right: Int) = FoldFeatureSnapshot(
        halfOpened = true,
        horizontal = false,
        left = left,
        top = 0,
        right = right,
        bottom = 2208,
    )
}
