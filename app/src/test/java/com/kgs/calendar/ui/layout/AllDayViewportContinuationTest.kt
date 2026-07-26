package com.kgs.calendar.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class AllDayViewportContinuationTest {
    @Test
    fun leadingFadeTracksHorizontalDisplacementContinuously() {
        assertEquals(
            0.4f,
            allDayLeadingContinuationProgress(
                itemStartPage = 100,
                itemEndPage = 102,
                itemStartX = -80f,
                itemEndX = 520f,
                dayWidthPx = 200f,
                fadeExitDistancePx = 20f,
            ),
            0.0001f,
        )
    }

    @Test
    fun singleDayItemNeverReceivesContinuationFade() {
        assertEquals(
            0f,
            allDayLeadingContinuationProgress(
                itemStartPage = 100,
                itemEndPage = 100,
                itemStartX = -80f,
                itemEndX = 120f,
                dayWidthPx = 200f,
                fadeExitDistancePx = 20f,
            ),
            0.0001f,
        )
    }

    @Test
    fun leadingCornerRadiusTracksCornerProgressDirectly() {
        assertEquals(1f, allDayLeadingCornerRadiusFraction(0f), 0.0001f)
        assertEquals(0.6f, allDayLeadingCornerRadiusFraction(0.4f), 0.0001f)
        assertEquals(0f, allDayLeadingCornerRadiusFraction(1f), 0.0001f)
    }

    @Test
    fun leadingCornerStaysFlatWhileFinalEdgeLeaves() {
        assertEquals(
            1f,
            allDayLeadingCornerProgress(
                itemStartPage = 100,
                itemEndPage = 102,
                itemStartX = -600f,
                dayWidthPx = 200f,
            ),
            0.0001f,
        )
    }

    @Test
    fun trailingCornerIsSquareBeyondRightViewportAndRoundsAfterEntering() {
        assertEquals(
            1f,
            allDayTrailingCornerProgress(
                itemStartPage = 100,
                itemEndPage = 102,
                itemEndX = 920f,
                dayWidthPx = 200f,
                viewportEndX = 720f,
            ),
            0.0001f,
        )
        assertEquals(
            0.5f,
            allDayTrailingCornerProgress(
                itemStartPage = 100,
                itemEndPage = 102,
                itemEndX = 742f,
                dayWidthPx = 200f,
                viewportEndX = 720f,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            allDayTrailingCornerProgress(
                itemStartPage = 100,
                itemEndPage = 102,
                itemEndX = 712f,
                dayWidthPx = 200f,
                viewportEndX = 720f,
            ),
            0.0001f,
        )
    }

    @Test
    fun pageSpacingDoesNotParticipateInViewportLayout() {
        assertEquals(false, allDayPageIntersectsViewport(pageLeftX = 720f, dayWidthPx = 200f, viewportWidthPx = 720f))
        assertEquals(false, allDayPageIntersectsViewport(pageLeftX = -200f, dayWidthPx = 200f, viewportWidthPx = 720f))
        assertEquals(true, allDayPageIntersectsViewport(pageLeftX = 719f, dayWidthPx = 200f, viewportWidthPx = 720f))
        assertEquals(true, allDayPageIntersectsViewport(pageLeftX = -199f, dayWidthPx = 200f, viewportWidthPx = 720f))
    }

    @Test
    fun connectorVisualOverflowKeepsPageRenderedUntilItsFadeLeaves() {
        assertEquals(
            true,
            allDayPageIntersectsViewport(
                pageLeftX = 730f,
                dayWidthPx = 200f,
                viewportWidthPx = 720f,
                visualOverflowPx = 20f,
            ),
        )
        assertEquals(
            true,
            allDayPageIntersectsViewport(
                pageLeftX = -215f,
                dayWidthPx = 200f,
                viewportWidthPx = 720f,
                visualOverflowPx = 20f,
            ),
        )
        assertEquals(
            false,
            allDayPageIntersectsViewport(
                pageLeftX = 740f,
                dayWidthPx = 200f,
                viewportWidthPx = 720f,
                visualOverflowPx = 20f,
            ),
        )
        assertEquals(
            false,
            allDayPageIntersectsViewport(
                pageLeftX = -220f,
                dayWidthPx = 200f,
                viewportWidthPx = 720f,
                visualOverflowPx = 20f,
            ),
        )
    }

    @Test
    fun connectorVisibilityFadesOverTheFinalSourceSegmentPixels() {
        assertEquals(
            1f,
            allDaySegmentVisualVisibility(
                segmentStartX = -180f,
                segmentEndX = 40f,
                viewportWidthPx = 720f,
                fadeDistancePx = 20f,
            ),
            0.0001f,
        )
        assertEquals(
            0.5f,
            allDaySegmentVisualVisibility(
                segmentStartX = -210f,
                segmentEndX = 10f,
                viewportWidthPx = 720f,
                fadeDistancePx = 20f,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            allDaySegmentVisualVisibility(
                segmentStartX = -220f,
                segmentEndX = 0f,
                viewportWidthPx = 720f,
                fadeDistancePx = 20f,
            ),
            0.0001f,
        )
        assertEquals(
            0.5f,
            allDaySegmentVisualVisibility(
                segmentStartX = 710f,
                segmentEndX = 930f,
                viewportWidthPx = 720f,
                fadeDistancePx = 20f,
            ),
            0.0001f,
        )
    }

    @Test
    fun trailingCornerStaysRoundedDuringFinalLeftExit() {
        assertEquals(
            0f,
            allDayTrailingCornerProgress(
                itemStartPage = 100,
                itemEndPage = 102,
                itemEndX = 100f,
                dayWidthPx = 200f,
                viewportEndX = 720f,
            ),
            0.0001f,
        )
    }

    @Test
    fun trailingSurfaceExtendsOffscreenInsteadOfShrinkingFinalRadius() {
        assertEquals(
            -5f,
            allDayStableSurfaceLeft(
                visibleWidthPx = 4f,
                leadingRadiusPx = 0f,
                trailingRadiusPx = 8f,
                borderStrokePx = 1f,
            ),
            0.0001f,
        )
    }

    @Test
    fun leadingSurfaceRadiusStaysStableWhileCardExitsRight() {
        assertEquals(
            0f,
            allDayStableSurfaceLeft(
                visibleWidthPx = 16f,
                leadingRadiusPx = 8f,
                trailingRadiusPx = 8f,
                borderStrokePx = 0f,
            ),
            0.0001f,
        )
        val geometry = rightEdgeSquashGeometry(
            visibleLeftX = 716f,
            visibleRightX = 720f,
            minimumLayoutWidthPx = 16f,
            enabled = true,
        )
        assertEquals(716f, geometry.layoutLeftX, 0.0001f)
        assertEquals(16f, geometry.layoutWidthPx, 0.0001f)
        assertEquals(1f, geometry.scaleX, 0.0001f)
    }

    @Test
    fun hiddenOverflowContinuationDoesNotCompressTheFinalTrailingRadius() {
        assertEquals(
            -4f,
            allDayStableSurfaceLeft(
                visibleWidthPx = 4f,
                leadingRadiusPx = 0f,
                trailingRadiusPx = 8f,
                borderStrokePx = 0f,
            ),
            0.0001f,
        )
    }

    @Test
    fun collapsedSegmentCompactionPreservesPriorityLaneOrder() {
        val priorityItem = AllDayOverlayItem(
            id = "priority",
            title = "Long priority event",
            color = 0,
            startPage = 25,
            endPage = 26,
            lane = 0,
        )
        val secondItem = AllDayOverlayItem(
            id = "second",
            title = "Second event",
            color = 0,
            startPage = 25,
            endPage = 26,
            lane = 1,
        )
        val overflowItem = AllDayOverlayItem(
            id = "overflow",
            title = "Overflow",
            color = 0,
            startPage = 25,
            endPage = 25,
            lane = 2,
        )
        val layout = buildCollapsedAllDayLayout(
            overlayItems = listOf(priorityItem, secondItem, overflowItem),
            pageItemsByPage = mapOf(
                25 to listOf(secondItem, overflowItem, priorityItem),
                26 to listOf(priorityItem, secondItem),
            ),
            visibleStartPage = 25,
            visibleEndPage = 26,
            maxVisibleItems = 2,
            collapsedVisibleItemLimit = 1,
        )

        val prioritySegment = layout.segments.single { it.item.id == priorityItem.id }
        val secondSegment = layout.segments.single { it.item.id == secondItem.id }
        assertEquals(0, prioritySegment.lane)
        assertEquals(1, secondSegment.lane)
    }

    @Test
    fun futureContinuationDoesNotPinFinalSliverDuringLeftExit() {
        val preserveShape = shouldPreserveRightExitCardShape(
            visibleRightX = 4f,
            viewportWidthPx = 720f,
            trailingSurfaceOverflowPx = 420f,
        )
        val geometry = rightEdgeSquashGeometry(
            visibleLeftX = 0f,
            visibleRightX = 4f,
            minimumLayoutWidthPx = 16f,
            enabled = preserveShape,
        )

        assertEquals(false, preserveShape)
        assertEquals(0f, geometry.layoutLeftX, 0.0001f)
        assertEquals(4f, geometry.layoutWidthPx, 0.0001f)
    }

    @Test
    fun rightExitStillPreservesItsFinalCornerShape() {
        assertEquals(
            true,
            shouldPreserveRightExitCardShape(
                visibleRightX = 720f,
                viewportWidthPx = 720f,
                trailingSurfaceOverflowPx = 420f,
            ),
        )
    }

    @Test
    fun leadingFadeWithdrawsAsRealEndLeavesViewport() {
        assertEquals(
            1f,
            allDayLeadingContinuationProgress(
                itemStartPage = 100,
                itemEndPage = 102,
                itemStartX = -500f,
                itemEndX = 100f,
                dayWidthPx = 200f,
                fadeExitDistancePx = 20f,
            ),
            0.0001f,
        )
        assertEquals(
            0.5f,
            allDayLeadingContinuationProgress(
                itemStartPage = 100,
                itemEndPage = 102,
                itemStartX = -590f,
                itemEndX = 10f,
                dayWidthPx = 200f,
                fadeExitDistancePx = 20f,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            allDayLeadingContinuationProgress(
                itemStartPage = 100,
                itemEndPage = 102,
                itemStartX = -600f,
                itemEndX = 0f,
                dayWidthPx = 200f,
                fadeExitDistancePx = 20f,
            ),
            0.0001f,
        )
    }

    @Test
    fun transitionTitleGeometryMovesAsOneElementBetweenLayouts() {
        val collapsed = interpolateAllDayTransitionTitleGeometry(
            collapsedLeftX = 240f,
            collapsedWidthPx = 220f,
            collapsedLane = 1f,
            expandedLeftX = 0f,
            expandedWidthPx = 680f,
            expandedLane = 3f,
            progress = 0f,
        )
        val halfway = interpolateAllDayTransitionTitleGeometry(
            collapsedLeftX = 240f,
            collapsedWidthPx = 220f,
            collapsedLane = 1f,
            expandedLeftX = 0f,
            expandedWidthPx = 680f,
            expandedLane = 3f,
            progress = 0.5f,
        )
        val expanded = interpolateAllDayTransitionTitleGeometry(
            collapsedLeftX = 240f,
            collapsedWidthPx = 220f,
            collapsedLane = 1f,
            expandedLeftX = 0f,
            expandedWidthPx = 680f,
            expandedLane = 3f,
            progress = 1f,
        )

        assertEquals(240f, collapsed.leftX, 0.0001f)
        assertEquals(220f, collapsed.widthPx, 0.0001f)
        assertEquals(1f, collapsed.lane, 0.0001f)
        assertEquals(120f, halfway.leftX, 0.0001f)
        assertEquals(450f, halfway.widthPx, 0.0001f)
        assertEquals(2f, halfway.lane, 0.0001f)
        assertEquals(0f, expanded.leftX, 0.0001f)
        assertEquals(680f, expanded.widthPx, 0.0001f)
        assertEquals(3f, expanded.lane, 0.0001f)
    }

    @Test
    fun connectedTransitionElementsShareTheSameLaneInterpolation() {
        assertEquals(
            1.5f,
            interpolateAllDayTransitionLane(
                collapsedLane = 1f,
                expandedLane = 3f,
                expansionProgress = 0.25f,
            ),
            0.0001f,
        )
    }

    @Test
    fun continuationFadeGrowsAndRetractsWithTheAbstractionTransition() {
        assertEquals(
            0f,
            allDayContinuationFadeVisualProgress(
                continuationProgress = 1f,
                transitionProgress = 0f,
            ),
            0.0001f,
        )
        assertEquals(
            0.4f,
            allDayContinuationFadeVisualProgress(
                continuationProgress = 0.8f,
                transitionProgress = 0.5f,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            allDayContinuationFadeVisualProgress(
                continuationProgress = 1f,
                transitionProgress = 1f,
            ),
            0.0001f,
        )
    }

    @Test
    fun transitionOwnedFadeStaysVisibleWhenBothLayoutsShareTheLeftEdge() {
        assertEquals(
            1f,
            interpolateAllDayContinuationFadeProgress(
                collapsedProgress = 1f,
                expandedProgress = 1f,
                expansionProgress = 0.5f,
            ),
            0.0001f,
        )
    }

    @Test
    fun transitionOwnedFadeAppearsContinuouslyWhenExpansionRevealsTheLeftEdge() {
        assertEquals(
            0.4f,
            interpolateAllDayContinuationFadeProgress(
                collapsedProgress = 0f,
                expandedProgress = 1f,
                expansionProgress = 0.4f,
            ),
            0.0001f,
        )
    }

    @Test
    fun cardSquashesAgainstLeftViewportEdgeWhileLeaving() {
        val bounds = allDayViewportCardBounds(
            segmentStartX = -80f,
            segmentEndX = 520f,
            continuesAfterSegment = false,
            viewportWidthPx = 720f,
        )

        assertEquals(0f, bounds.visibleLeftX, 0.0001f)
        assertEquals(520f, bounds.visibleRightX, 0.0001f)
    }

    @Test
    fun cardSquashesAgainstRightViewportEdgeWhileLeaving() {
        val bounds = allDayViewportCardBounds(
            segmentStartX = 610f,
            segmentEndX = 910f,
            continuesAfterSegment = false,
            viewportWidthPx = 720f,
        )

        assertEquals(610f, bounds.visibleLeftX, 0.0001f)
        assertEquals(720f, bounds.visibleRightX, 0.0001f)
    }

    @Test
    fun collapsedContinuationFillsRemainingViewportWidth() {
        val bounds = allDayViewportCardBounds(
            segmentStartX = 10f,
            segmentEndX = 690f,
            continuesAfterSegment = true,
            viewportWidthPx = 720f,
        )

        assertEquals(10f, bounds.visibleLeftX, 0.0001f)
        assertEquals(720f, bounds.visibleRightX, 0.0001f)
    }

    @Test
    fun collapsedContinuationKeepsItsPagerPositionAcrossSpacingHandoff() {
        val bounds = allDayViewportCardBounds(
            segmentStartX = 3f,
            segmentEndX = 690f,
            continuesAfterSegment = true,
            viewportWidthPx = 720f,
        )

        assertEquals(3f, bounds.visibleLeftX, 0.0001f)
        assertEquals(720f, bounds.visibleRightX, 0.0001f)
    }

    @Test
    fun pastContinuationStaysPinnedAcrossPagerSpacingHandoff() {
        val bounds = allDayViewportCardBounds(
            segmentStartX = 8f,
            segmentEndX = 642f,
            continuesAfterSegment = true,
            viewportWidthPx = 641f,
            continuesBeforeSegment = true,
        )

        assertEquals(0f, bounds.visibleLeftX, 0.0001f)
        assertEquals(641f, bounds.visibleRightX, 0.0001f)
    }
}
