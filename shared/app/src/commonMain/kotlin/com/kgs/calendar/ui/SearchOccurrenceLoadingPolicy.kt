package com.kgs.calendar.ui

import com.kgs.calendar.domain.model.CalendarRange
import java.time.LocalDate

internal enum class SearchOccurrenceEdge {
    Earlier,
    Later,
}

/** Keeps recurring-search loading broad by default and incremental at user-reached edges. */
internal object SearchOccurrenceLoadingPolicy {
    private const val INITIAL_YEARS_EACH_DIRECTION = 10L
    private const val EXTENSION_YEARS = 5L
    private const val PREFETCH_ITEM_DISTANCE = 8

    fun initialRange(today: LocalDate): CalendarRange = CalendarRange(
        startDate = today.minusYears(INITIAL_YEARS_EACH_DIRECTION),
        endExclusiveDate = today.plusYears(INITIAL_YEARS_EACH_DIRECTION).plusDays(1),
    )

    fun extend(range: CalendarRange, edge: SearchOccurrenceEdge): CalendarRange = when (edge) {
        SearchOccurrenceEdge.Earlier -> range.copy(startDate = range.startDate.minusYears(EXTENSION_YEARS))
        SearchOccurrenceEdge.Later -> range.copy(endExclusiveDate = range.endExclusiveDate.plusYears(EXTENSION_YEARS))
    }

    fun edgeToExtend(
        firstVisibleItemIndex: Int,
        lastVisibleItemIndex: Int,
        totalItemsCount: Int,
        scrollingTowardLater: Boolean,
        userGestureActive: Boolean,
        edgeAlreadyRequestedForGesture: Boolean,
    ): SearchOccurrenceEdge? {
        if (!userGestureActive || edgeAlreadyRequestedForGesture || totalItemsCount <= 0) return null
        return when {
            !scrollingTowardLater && firstVisibleItemIndex <= PREFETCH_ITEM_DISTANCE -> SearchOccurrenceEdge.Earlier
            scrollingTowardLater && lastVisibleItemIndex >= totalItemsCount - 1 - PREFETCH_ITEM_DISTANCE -> SearchOccurrenceEdge.Later
            else -> null
        }
    }
}
