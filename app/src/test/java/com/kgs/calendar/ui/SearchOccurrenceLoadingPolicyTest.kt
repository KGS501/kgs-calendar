package com.kgs.calendar.ui

import com.kgs.calendar.domain.model.CalendarRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SearchOccurrenceLoadingPolicyTest {
    private val today = LocalDate.of(2026, 8, 20)

    @Test
    fun initialWindowCoversTenYearsInBothDirections() {
        assertEquals(
            CalendarRange(today.minusYears(10), today.plusYears(10).plusDays(1)),
            SearchOccurrenceLoadingPolicy.initialRange(today),
        )
    }

    @Test
    fun extendingOneEdgeLeavesTheOtherEdgeStable() {
        val initial = SearchOccurrenceLoadingPolicy.initialRange(today)

        assertEquals(
            initial.copy(startDate = initial.startDate.minusYears(5)),
            SearchOccurrenceLoadingPolicy.extend(initial, SearchOccurrenceEdge.Earlier),
        )
        assertEquals(
            initial.copy(endExclusiveDate = initial.endExclusiveDate.plusYears(5)),
            SearchOccurrenceLoadingPolicy.extend(initial, SearchOccurrenceEdge.Later),
        )
    }

    @Test
    fun userScrollingTowardAnEdgeRequestsThatEdgeNearTheBoundary() {
        assertEquals(
            SearchOccurrenceEdge.Earlier,
            SearchOccurrenceLoadingPolicy.edgeToExtend(
                firstVisibleItemIndex = 5,
                lastVisibleItemIndex = 12,
                totalItemsCount = 200,
                scrollingTowardLater = false,
                userGestureActive = true,
                edgeAlreadyRequestedForGesture = false,
            ),
        )
        assertEquals(
            SearchOccurrenceEdge.Later,
            SearchOccurrenceLoadingPolicy.edgeToExtend(
                firstVisibleItemIndex = 188,
                lastVisibleItemIndex = 195,
                totalItemsCount = 200,
                scrollingTowardLater = true,
                userGestureActive = true,
                edgeAlreadyRequestedForGesture = false,
            ),
        )
    }

    @Test
    fun programmaticScrollWrongDirectionAndRepeatedGestureDoNotExtend() {
        assertNull(
            SearchOccurrenceLoadingPolicy.edgeToExtend(5, 12, 200, false, false, false),
        )
        assertNull(
            SearchOccurrenceLoadingPolicy.edgeToExtend(5, 12, 200, true, true, false),
        )
        assertNull(
            SearchOccurrenceLoadingPolicy.edgeToExtend(188, 195, 200, true, true, true),
        )
    }
}
