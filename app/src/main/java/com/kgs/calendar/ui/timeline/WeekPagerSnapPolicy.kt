package com.kgs.calendar.ui.timeline

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.PagerState
import com.kgs.calendar.domain.model.WEEK_DAY_COUNT
import com.kgs.calendar.domain.model.coerceMultiDayCount
import com.kgs.calendar.ui.calendar.DayPagerBaseDate
import java.time.DayOfWeek
import kotlin.math.abs
import kotlin.math.sign

private const val GesturePositionEpsilon = 0.001f
private const val LowVelocityThreshold = 1f

internal fun weekStartPageOffset(firstDayOfWeek: DayOfWeek): Int =
    Math.floorMod(firstDayOfWeek.value - DayPagerBaseDate.dayOfWeek.value, WEEK_DAY_COUNT)

internal fun fullWeekTargetPage(
    stableAnchorPage: Int,
    gestureDirection: Int,
    pageCount: Int,
    weekStartPageOffset: Int,
): Int {
    val anchor = nearestAlignedPage(stableAnchorPage, pageCount, weekStartPageOffset)
    val minimumAlignedPage = minimumAlignedPage(pageCount, weekStartPageOffset)
    val maximumAlignedPage = maximumAlignedPage(pageCount, weekStartPageOffset)
    return (anchor + gestureDirection.sign * WEEK_DAY_COUNT)
        .coerceIn(minimumAlignedPage, maximumAlignedPage)
}

internal fun fallbackPagerSnapPageLimit(
    isDayMode: Boolean,
    weekViewEnabled: Boolean,
    configuredMultiDayCount: Int,
): Int = if (!isDayMode && weekViewEnabled) {
    WEEK_DAY_COUNT
} else {
    configuredMultiDayCount.coerceMultiDayCount().coerceAtLeast(3)
}

internal class FullWeekPagerGestureState(
    initialAnchorPage: Int,
    private val pageCount: Int,
    private val weekStartPageOffset: Int,
) {
    private var stableAnchorPage = nearestAlignedPage(
        initialAnchorPage,
        pageCount,
        weekStartPageOffset,
    )
    private var pendingTargetPage: Int? = null
    private var gestureAnchorPage: Int? = null
    private var gestureStartPosition = 0f
    private var previousGesturePosition = 0f
    private var currentGesturePosition = 0f
    private var gestureActive = false
    private var gestureCancelled = false

    fun beginGesture(pagePosition: Float, settledPage: Int) {
        if (pendingTargetPage == null) {
            stableAnchorPage = nearestAlignedPage(settledPage, pageCount, weekStartPageOffset)
        }
        gestureAnchorPage = pendingTargetPage ?: stableAnchorPage
        gestureStartPosition = pagePosition
        previousGesturePosition = pagePosition
        currentGesturePosition = pagePosition
        gestureActive = true
        gestureCancelled = false
    }

    fun recordGesturePosition(pagePosition: Float) {
        if (!gestureActive || gestureCancelled) return
        recordPosition(pagePosition)
    }

    fun cancelGesture() {
        if (gestureAnchorPage != null) gestureCancelled = true
        gestureActive = false
    }

    fun targetPage(
        currentPagePosition: Float,
        settledPage: Int,
        velocity: Float,
    ): Int {
        if (gestureAnchorPage == null) {
            beginGesture(currentPagePosition, settledPage)
        }
        if (!gestureCancelled) {
            recordPosition(currentPagePosition)
        }

        val direction = when {
            gestureCancelled -> 0
            abs(velocity) > LowVelocityThreshold -> velocity.sign.toInt()
            abs(currentGesturePosition - previousGesturePosition) > GesturePositionEpsilon ->
                (currentGesturePosition - previousGesturePosition).sign.toInt()
            abs(currentGesturePosition - gestureStartPosition) > GesturePositionEpsilon ->
                (currentGesturePosition - gestureStartPosition).sign.toInt()
            else -> 0
        }
        val target = fullWeekTargetPage(
            stableAnchorPage = checkNotNull(gestureAnchorPage),
            gestureDirection = direction,
            pageCount = pageCount,
            weekStartPageOffset = weekStartPageOffset,
        )
        pendingTargetPage = target
        clearGesture()
        return target
    }

    fun onSettled(page: Int) {
        if (!isAlignedPage(page, weekStartPageOffset)) return
        if (pendingTargetPage == null || pendingTargetPage == page) {
            stableAnchorPage = page
            pendingTargetPage = null
        }
    }

    fun resetAnchor(page: Int) {
        stableAnchorPage = nearestAlignedPage(page, pageCount, weekStartPageOffset)
        pendingTargetPage = null
        clearGesture()
    }

    private fun recordPosition(pagePosition: Float) {
        if (abs(pagePosition - currentGesturePosition) <= GesturePositionEpsilon) return
        previousGesturePosition = currentGesturePosition
        currentGesturePosition = pagePosition
    }

    private fun clearGesture() {
        gestureAnchorPage = null
        gestureActive = false
        gestureCancelled = false
    }
}

internal class FullWeekPagerFlingBehavior(
    private val pagerState: PagerState,
    private val gestureState: FullWeekPagerGestureState,
    private val animationSpec: AnimationSpec<Float>,
) : TargetedFlingBehavior {
    override suspend fun ScrollScope.performFling(
        initialVelocity: Float,
        onRemainingDistanceUpdated: (Float) -> Unit,
    ): Float {
        val targetPage = gestureState.targetPage(
            currentPagePosition = pagerState.pagePosition(),
            settledPage = pagerState.settledPage,
            velocity = initialVelocity,
        )
        val initialDistance = pagerState.distanceToPage(targetPage)
        if (initialDistance == 0f) {
            onRemainingDistanceUpdated(0f)
            return 0f
        }

        var consumedDistance = 0f
        var velocityLeft = initialVelocity
        onRemainingDistanceUpdated(initialDistance)
        animate(
            initialValue = 0f,
            targetValue = initialDistance,
            initialVelocity = initialVelocity,
            animationSpec = animationSpec,
        ) { value, velocity ->
            val consumed = scrollBy(value - consumedDistance)
            consumedDistance += consumed
            velocityLeft = velocity
            onRemainingDistanceUpdated(pagerState.distanceToPage(targetPage))
        }

        val correction = pagerState.distanceToPage(targetPage)
        val remaining = correction - scrollBy(correction)
        onRemainingDistanceUpdated(remaining)
        return if (abs(remaining) <= GesturePositionEpsilon) 0f else velocityLeft
    }
}

internal fun PagerState.pagePosition(): Float = currentPage + currentPageOffsetFraction

private fun PagerState.distanceToPage(targetPage: Int): Float {
    val pageSizeWithSpacing = (layoutInfo.pageSize + layoutInfo.pageSpacing).toFloat()
    if (pageSizeWithSpacing == 0f) return 0f
    return (targetPage - currentPage) * pageSizeWithSpacing -
        currentPageOffsetFraction * pageSizeWithSpacing
}

private fun nearestAlignedPage(
    page: Int,
    pageCount: Int,
    weekStartPageOffset: Int,
): Int {
    require(pageCount > WEEK_DAY_COUNT)
    val minimum = minimumAlignedPage(pageCount, weekStartPageOffset)
    val maximum = maximumAlignedPage(pageCount, weekStartPageOffset)
    val boundedPage = page.coerceIn(0, pageCount - 1)
    val lower = (boundedPage - Math.floorMod(boundedPage - weekStartPageOffset, WEEK_DAY_COUNT))
        .coerceIn(minimum, maximum)
    val upper = (lower + WEEK_DAY_COUNT).coerceAtMost(maximum)
    return if (boundedPage - lower <= upper - boundedPage) lower else upper
}

private fun minimumAlignedPage(pageCount: Int, weekStartPageOffset: Int): Int =
    weekStartPageOffset.coerceIn(0, pageCount - 1)

private fun maximumAlignedPage(pageCount: Int, weekStartPageOffset: Int): Int {
    val lastPage = pageCount - 1
    return lastPage - Math.floorMod(lastPage - weekStartPageOffset, WEEK_DAY_COUNT)
}

private fun isAlignedPage(page: Int, weekStartPageOffset: Int): Boolean =
    Math.floorMod(page - weekStartPageOffset, WEEK_DAY_COUNT) == 0
