package com.kgs.calendar.ui.timeline

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekPagerSnapPolicyTest {
    private val mondayOffset = weekStartPageOffset(DayOfWeek.MONDAY)

    @Test
    fun fullWeekTargetMovesExactlySevenPagesInGestureDirection() {
        assertEquals(107, fullWeekTargetPage(100, 101, 1_000, mondayOffset))
        assertEquals(93, fullWeekTargetPage(100, 99, 1_000, mondayOffset))
    }

    @Test
    fun fullWeekTargetStaysWhenPagerSuggestsNoMovement() {
        assertEquals(100, fullWeekTargetPage(100, 100, 1_000, mondayOffset))
    }

    @Test
    fun interruptedAnimationStillTargetsAnAlignedWeek() {
        assertEquals(107, fullWeekTargetPage(103, 104, 1_000, mondayOffset))
        assertEquals(93, fullWeekTargetPage(103, 102, 1_000, mondayOffset))
    }

    @Test
    fun fullWeekTargetClampsToAlignedPagerBounds() {
        assertEquals(mondayOffset, fullWeekTargetPage(mondayOffset, mondayOffset - 1, 1_000, mondayOffset))
        assertEquals(996, fullWeekTargetPage(996, 997, 1_000, mondayOffset))
    }
}
