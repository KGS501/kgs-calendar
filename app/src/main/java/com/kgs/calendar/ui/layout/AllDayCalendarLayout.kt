package com.kgs.calendar.ui.layout

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import kotlin.math.max
import kotlin.math.min

internal data class AllDayOverlayItem(
    val id: String,
    val title: String,
    val color: Int,
    val startPage: Int,
    val endPage: Int,
    val lane: Int,
    val event: EventEntity? = null,
    val task: TaskEntity? = null,
    val completed: Boolean = false,
)

internal data class AllDayOverlaySegment(
    val item: AllDayOverlayItem,
    val startPage: Int,
    val endPage: Int,
    val lane: Int,
)

internal data class AllDayContinuationSegment(
    val item: AllDayOverlayItem,
    val page: Int,
    val lane: Int,
    val fromPrevious: Boolean,
    val toNext: Boolean,
)

internal data class AllDayCollapsedLayout(
    val segments: List<AllDayOverlaySegment>,
    val continuations: List<AllDayContinuationSegment>,
)

internal fun allDayViewportPriorityTier(
    startPage: Int,
    endPage: Int,
    visibleStartPage: Int,
    visibleEndPage: Int,
): Int {
    val fillsVisibleWindow = startPage <= visibleStartPage && endPage >= visibleEndPage
    return if (startPage < visibleStartPage || fillsVisibleWindow) 0 else 1
}

internal fun allDayCollapsedPageItemComparator(
    visibleStartPage: Int,
    visibleEndPage: Int,
): Comparator<AllDayOverlayItem> =
    compareBy<AllDayOverlayItem> { allDayViewportPriorityTier(it.startPage, it.endPage, visibleStartPage, visibleEndPage) }
        .thenByDescending { it.endPage - it.startPage }
        .thenBy { it.startPage }
        .thenBy { it.lane }
        .thenBy { it.title }

internal fun buildCollapsedAllDayLayout(
    overlayItems: List<AllDayOverlayItem>,
    pageItemsByPage: Map<Int, List<AllDayOverlayItem>>,
    visibleStartPage: Int,
    visibleEndPage: Int,
    maxVisibleItems: Int,
    collapsedVisibleItemLimit: Int,
): AllDayCollapsedLayout {
    val overflowVisibleLimit = when {
        maxVisibleItems <= 0 -> 0
        else -> (maxVisibleItems - 1).coerceAtLeast(0)
    }

    fun pageVisibleLimit(pageItems: List<AllDayOverlayItem>): Int =
        if (pageItems.size > maxVisibleItems) overflowVisibleLimit else maxVisibleItems

    fun itemVisibleOnPage(item: AllDayOverlayItem, page: Int): Boolean {
        val pageItems = pageItemsByPage[page].orEmpty()
        if (pageItems.isEmpty()) return false
        val limit = pageVisibleLimit(pageItems)
        if (limit <= 0) return false
        return pageItems.take(limit).any { it.id == item.id }
    }

    fun itemHiddenOnCollapsedPage(item: AllDayOverlayItem, page: Int): Boolean {
        val pageItems = pageItemsByPage[page].orEmpty()
        if (pageItems.size <= maxVisibleItems) return false
        return pageItems.any { it.id == item.id } && !itemVisibleOnPage(item, page)
    }

    val rawSegments = overlayItems.flatMap { item ->
        val visiblePages = (max(item.startPage, visibleStartPage)..min(item.endPage, visibleEndPage))
            .filter { page -> itemVisibleOnPage(item, page) }
        if (visiblePages.isEmpty()) return@flatMap emptyList()
        val segments = mutableListOf<AllDayOverlaySegment>()
        var start = visiblePages.first()
        var previous = start
        visiblePages.drop(1).forEach { page ->
            if (page == previous + 1) {
                previous = page
            } else {
                segments += AllDayOverlaySegment(item, start, previous, lane = item.lane)
                start = page
                previous = page
            }
        }
        segments += AllDayOverlaySegment(item, start, previous, lane = item.lane)
        segments
    }

    val assignedSegments = assignCollapsedAllDaySegmentLanes(rawSegments)
    val continuations = overlayItems.flatMap { item ->
        val firstPage = max(item.startPage, visibleStartPage)
        val lastPage = min(item.endPage, visibleEndPage)
        if (firstPage > lastPage) return@flatMap emptyList()
        (firstPage..lastPage).mapNotNull { page ->
            if (!itemHiddenOnCollapsedPage(item, page)) return@mapNotNull null
            val previousSegment = assignedSegments
                .filter { it.item.id == item.id && it.endPage < page }
                .maxByOrNull { it.endPage }
            val nextSegment = assignedSegments
                .filter { it.item.id == item.id && it.startPage > page }
                .minByOrNull { it.startPage }
            val previousPageHidden = page > visibleStartPage && itemHiddenOnCollapsedPage(item, page - 1)
            val nextPageHidden = page < visibleEndPage && itemHiddenOnCollapsedPage(item, page + 1)
            val fromPrevious = (previousSegment != null || item.startPage < page) && !previousPageHidden
            val toNext = (nextSegment != null || item.endPage > page) && !nextPageHidden
            val lane = when {
                previousSegment != null -> previousSegment.lane
                nextSegment != null -> nextSegment.lane
                else -> overflowVisibleLimit
            }.coerceIn(0, overflowVisibleLimit)
            if (!fromPrevious && !toNext) {
                null
            } else {
                AllDayContinuationSegment(
                    item = item,
                    page = page,
                    lane = lane,
                    fromPrevious = fromPrevious,
                    toNext = toNext,
                )
            }
        }
    }
    return AllDayCollapsedLayout(
        segments = assignedSegments,
        continuations = continuations,
    )
}

private fun assignCollapsedAllDaySegmentLanes(segments: List<AllDayOverlaySegment>): List<AllDayOverlaySegment> {
    if (segments.isEmpty()) return emptyList()
    data class AssignedSegment(
        val segment: AllDayOverlaySegment,
        var lane: Int,
    )

    val laneEnds = mutableListOf<Int>()
    val assigned = segments
        .sortedWith(
            compareBy<AllDayOverlaySegment> { it.startPage }
                .thenBy { it.item.lane }
                .thenBy { it.item.title },
        )
        .map { segment ->
            val lane = laneEnds.indexOfFirst { it < segment.startPage }.let { index ->
                if (index >= 0) index else laneEnds.size.also { laneEnds.add(Int.MIN_VALUE) }
            }
            laneEnds[lane] = segment.endPage
            AssignedSegment(segment, lane)
        }
        .toMutableList()

    return assigned.map { it.segment.copy(lane = it.lane) }
}
