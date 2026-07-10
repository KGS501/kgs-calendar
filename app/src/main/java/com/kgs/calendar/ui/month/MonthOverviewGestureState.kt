package com.kgs.calendar.ui.month

import androidx.compose.ui.geometry.Offset
import java.time.YearMonth
import kotlin.math.abs

internal enum class MonthGestureAxis { Undecided, Horizontal, Vertical }

internal data class MonthOverviewGestureState(
    val axis: MonthGestureAxis = MonthGestureAxis.Undecided,
    val accumulated: Offset = Offset.Zero,
)

internal data class MonthSettleTarget(
    val displayedMonth: YearMonth,
    val targetMonth: YearMonth,
)

internal class MonthOverviewGestureReducer {
    fun update(
        delta: Offset,
        touchSlop: Float,
        state: MonthOverviewGestureState = MonthOverviewGestureState(),
    ): MonthOverviewGestureState {
        require(touchSlop >= 0f && touchSlop.isFinite()) { "Touch slop must be finite and non-negative" }
        require(delta.x.isFinite() && delta.y.isFinite()) { "Gesture delta must be finite" }
        val accumulated = state.accumulated + delta
        if (state.axis != MonthGestureAxis.Undecided) {
            return state.copy(accumulated = accumulated)
        }
        val horizontal = abs(accumulated.x)
        val vertical = abs(accumulated.y)
        val axis = when {
            maxOf(horizontal, vertical) < touchSlop -> MonthGestureAxis.Undecided
            horizontal >= vertical -> MonthGestureAxis.Horizontal
            else -> MonthGestureAxis.Vertical
        }
        return MonthOverviewGestureState(axis = axis, accumulated = accumulated)
    }

    fun interrupt(settling: MonthSettleTarget, deltaMonths: Long): MonthSettleTarget =
        settling.copy(targetMonth = settling.targetMonth.plusMonths(deltaMonths))
}
