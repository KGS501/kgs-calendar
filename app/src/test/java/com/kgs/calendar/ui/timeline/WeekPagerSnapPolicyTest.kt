package com.kgs.calendar.ui.timeline

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekPagerSnapPolicyTest {
    private val mondayOffset = weekStartPageOffset(DayOfWeek.MONDAY)

    @Test
    fun postDragApproachPageDoesNotReplaceStableBackwardAnchor() {
        val state = gestureState(initialAnchorPage = 100)

        state.beginGesture(pagePosition = 100f, settledPage = 100)
        state.recordGesturePosition(99f)

        assertEquals(
            93,
            state.targetPage(currentPagePosition = 99f, settledPage = 100, velocity = 0f),
        )
    }

    @Test
    fun dragStartsOnEitherSideOfBoundaryRemainAlignedToStableWeek() {
        val backward = gestureState(initialAnchorPage = 100)
        backward.beginGesture(pagePosition = 99.4f, settledPage = 100)
        backward.recordGesturePosition(98.9f)

        val forward = gestureState(initialAnchorPage = 100)
        forward.beginGesture(pagePosition = 100.6f, settledPage = 100)
        forward.recordGesturePosition(101.1f)

        assertEquals(93, backward.targetPage(98.9f, 100, 0f))
        assertEquals(107, forward.targetPage(101.1f, 100, 0f))
    }

    @Test
    fun lowAndZeroVelocityUseObservedDragDirection() {
        val zeroVelocity = gestureState(initialAnchorPage = 100)
        zeroVelocity.beginGesture(pagePosition = 100f, settledPage = 100)
        zeroVelocity.recordGesturePosition(100.05f)

        val lowVelocity = gestureState(initialAnchorPage = 100)
        lowVelocity.beginGesture(pagePosition = 100f, settledPage = 100)
        lowVelocity.recordGesturePosition(99.95f)

        assertEquals(107, zeroVelocity.targetPage(100.05f, 100, 0f))
        assertEquals(93, lowVelocity.targetPage(99.95f, 100, -0.5f))
    }

    @Test
    fun reversalUsesTheFinalGestureDirection() {
        val state = gestureState(initialAnchorPage = 100)

        state.beginGesture(pagePosition = 100f, settledPage = 100)
        state.recordGesturePosition(100.8f)
        state.recordGesturePosition(100.4f)

        assertEquals(93, state.targetPage(100.4f, 100, 0f))
    }

    @Test
    fun rapidRepeatedGesturesAdvanceFromPreviousTargetWithoutWaiting() {
        val state = gestureState(initialAnchorPage = 100)

        state.beginGesture(pagePosition = 100f, settledPage = 100)
        state.recordGesturePosition(100.5f)
        assertEquals(107, state.targetPage(100.5f, 100, 0f))

        state.beginGesture(pagePosition = 103f, settledPage = 100)
        state.recordGesturePosition(103.5f)
        assertEquals(114, state.targetPage(103.5f, 100, 0f))
    }

    @Test
    fun rapidReverseGestureMovesBackFromPreviousTargetWithoutWaiting() {
        val state = gestureState(initialAnchorPage = 100)

        state.beginGesture(pagePosition = 100f, settledPage = 100)
        state.recordGesturePosition(100.5f)
        assertEquals(107, state.targetPage(100.5f, 100, 0f))

        state.beginGesture(pagePosition = 103f, settledPage = 100)
        state.recordGesturePosition(102.5f)
        assertEquals(100, state.targetPage(102.5f, 100, 0f))
    }

    @Test
    fun noMovementReturnsToStableAlignedAnchor() {
        val state = gestureState(initialAnchorPage = 100)

        state.beginGesture(pagePosition = 99.7f, settledPage = 100)

        assertEquals(100, state.targetPage(99.7f, 100, 0f))
    }

    @Test
    fun targetsClampToAlignedPagerBounds() {
        val minimum = gestureState(initialAnchorPage = mondayOffset)
        minimum.beginGesture(pagePosition = mondayOffset.toFloat(), settledPage = mondayOffset)
        minimum.recordGesturePosition(mondayOffset - 0.5f)

        val maximum = gestureState(initialAnchorPage = 996)
        maximum.beginGesture(pagePosition = 996f, settledPage = 996)
        maximum.recordGesturePosition(996.5f)

        assertEquals(mondayOffset, minimum.targetPage(mondayOffset - 0.5f, mondayOffset, 0f))
        assertEquals(996, maximum.targetPage(996.5f, 996, 0f))
    }

    @Test
    fun dayModeKeepsConfiguredLegacySnapLimitWhenWeekPreferenceIsEnabled() {
        assertEquals(
            4,
            fallbackPagerSnapPageLimit(
                isDayMode = true,
                weekViewEnabled = true,
                configuredMultiDayCount = 4,
            ),
        )
        assertEquals(
            4,
            fallbackPagerSnapPageLimit(
                isDayMode = false,
                weekViewEnabled = false,
                configuredMultiDayCount = 4,
            ),
        )
        assertEquals(
            7,
            fallbackPagerSnapPageLimit(
                isDayMode = false,
                weekViewEnabled = true,
                configuredMultiDayCount = 4,
            ),
        )
    }

    private fun gestureState(initialAnchorPage: Int) = FullWeekPagerGestureState(
        initialAnchorPage = initialAnchorPage,
        pageCount = 1_000,
        weekStartPageOffset = mondayOffset,
    )
}
