package com.kgs.calendar.ui.month

import androidx.compose.ui.geometry.Offset
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthOverviewGestureStateTest {
    private val reducer = MonthOverviewGestureReducer()

    @Test
    fun horizontalIntentWinsDespiteSmallVerticalDrift() {
        val state = reducer.update(Offset(-40f, -8f), touchSlop = 12f)

        assertEquals(MonthGestureAxis.Horizontal, state.axis)
    }

    @Test
    fun verticalIntentLocksAfterTouchSlop() {
        val state = reducer.update(Offset(5f, -32f), touchSlop = 12f)

        assertEquals(MonthGestureAxis.Vertical, state.axis)
    }

    @Test
    fun motionBelowTouchSlopRemainsUndecided() {
        val state = reducer.update(Offset(7f, -6f), touchSlop = 12f)

        assertEquals(MonthGestureAxis.Undecided, state.axis)
    }

    @Test
    fun lockedAxisDoesNotFlipWhenLaterMotionCrossesTheOtherAxis() {
        val horizontal = reducer.update(Offset(-30f, -4f), touchSlop = 12f)
        val retained = reducer.update(
            state = horizontal,
            delta = Offset(0f, -80f),
            touchSlop = 12f,
        )

        assertEquals(MonthGestureAxis.Horizontal, retained.axis)
    }

    @Test
    fun newSwipeReplacesSettlingTarget() {
        val settlingAugust = MonthSettleTarget(
            displayedMonth = YearMonth.of(2026, 7),
            targetMonth = YearMonth.of(2026, 8),
        )

        assertEquals(
            YearMonth.of(2026, 9),
            reducer.interrupt(settlingAugust, deltaMonths = 1).targetMonth,
        )
    }

    @Test
    fun oppositeSwipeAlsoStartsFromCurrentSettleTarget() {
        val settlingAugust = MonthSettleTarget(
            displayedMonth = YearMonth.of(2026, 7),
            targetMonth = YearMonth.of(2026, 8),
        )

        assertEquals(
            YearMonth.of(2026, 7),
            reducer.interrupt(settlingAugust, deltaMonths = -1).targetMonth,
        )
    }
}
