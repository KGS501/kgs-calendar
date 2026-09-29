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

/** A day/row where independently continuing cards must not look like one event. */
internal data class AllDayOverflowGroup(
    val page: Int,
    val lane: Int,
    val items: List<AllDayOverlayItem>,
    val collision: Boolean = false,
)

internal data class AllDayCollapsedLayout(
    val segments: List<AllDayOverlaySegment>,
    val continuations: List<AllDayContinuationSegment>,
    val collisions: List<AllDayOverflowGroup> = emptyList(),
)

/** Geometry follows the visible portions of both independent continuations, never a clock. */
internal data class AllDayCollisionFrame(
    val leftX: Float,
    val rightX: Float,
    val intervalLeftX: Float,
    val intervalRightX: Float,
    val progress: Float,
)

internal fun allDayCollisionFrame(
    group: AllDayOverflowGroup,
    segments: List<AllDayOverlaySegment>,
    anchorPage: Int,
    anchorOffsetPx: Float,
    dayWidthPx: Float,
    dayStepPx: Float,
    viewportWidthPx: Float,
): AllDayCollisionFrame? {
    val members = group.items.mapTo(hashSetOf()) { it.id }
    val sources = segments.filter { it.lane == group.lane && it.item.id in members }
    val before = sources.filter { it.endPage < group.page }.maxByOrNull { it.endPage } ?: return null
    val after = sources.filter { it.startPage > group.page }.minByOrNull { it.startPage } ?: return null
    fun left(page: Int) = allDayPageLeftX(page, anchorPage, anchorOffsetPx, dayStepPx)
    fun visibility(segment: AllDayOverlaySegment): Float =
        ((min(left(segment.endPage) + dayWidthPx, viewportWidthPx) - max(left(segment.startPage), 0f)) /
            dayWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val beforeProgress = visibility(before)
    val afterProgress = visibility(after)
    val start = left(before.endPage + 1)
    val end = left(after.startPage - 1) + dayWidthPx
    // The hidden interval squashes toward the continuing card whose counterpart is leaving.
    // Treat consecutive collision days as one interval, then clip each abstraction to its day.
    val intervalLeft = start + (end - start) * beforeProgress * (1f - afterProgress)
    val intervalRight = start + (end - start) * beforeProgress
    val dayLeft = left(group.page)
    return AllDayCollisionFrame(
        leftX = intervalLeft.coerceIn(dayLeft, dayLeft + dayWidthPx),
        rightX = intervalRight.coerceIn(dayLeft, dayLeft + dayWidthPx),
        intervalLeftX = intervalLeft,
        intervalRightX = intervalRight,
        progress = beforeProgress * afterProgress,
    )
}

internal data class AllDayViewportWindow(
    val layoutPages: List<Int>,
    val renderPages: List<Int>,
) {
    val layoutStartPage: Int get() = layoutPages.first()
    val layoutEndPage: Int get() = layoutPages.last()
}

internal data class AllDayVisualPiece(
    val key: String,
    val item: AllDayOverlayItem,
    val collapsedSegment: AllDayOverlaySegment?,
    val primary: Boolean,
)

internal data class AllDayVisualPieceFrame(
    val leftX: Float,
    val widthPx: Float,
    val lane: Float,
    val alpha: Float,
    val leadingContinuationProgress: Float,
)

internal data class AllDaySceneMetrics(
    val expandedRowCount: Int,
    val collapsedRowCount: Int,
    val hasCollapsedOverflow: Boolean,
    val collapsedVisibleItemLimit: Int,
    val overflowLane: Int,
)

internal data class AllDayScene(
    val overlayItems: List<AllDayOverlayItem>,
    val pageItemsByPage: Map<Int, List<AllDayOverlayItem>>,
    val collapsedLayout: AllDayCollapsedLayout,
    val visualPieces: List<AllDayVisualPiece>,
    val hiddenPages: Map<Int, List<AllDayOverlayItem>>,
    val overflowGroups: List<AllDayOverflowGroup>,
    val metrics: AllDaySceneMetrics,
)

internal data class AllDayViewportCardBounds(
    val visibleLeftX: Float,
    val visibleRightX: Float,
)

internal data class AllDayTransitionTitleGeometry(
    val leftX: Float,
    val widthPx: Float,
    val lane: Float,
)

internal data class RightEdgeSquashGeometry(
    val layoutLeftX: Float,
    val layoutWidthPx: Float,
    val scaleX: Float,
)

internal fun rightEdgeSquashGeometry(
    visibleLeftX: Float,
    visibleRightX: Float,
    minimumLayoutWidthPx: Float,
    enabled: Boolean,
): RightEdgeSquashGeometry {
    val visibleWidth = (visibleRightX - visibleLeftX).coerceAtLeast(0f)
    val minimumWidth = minimumLayoutWidthPx.coerceAtLeast(1f)
    if (!enabled || visibleWidth >= minimumWidth) {
        return RightEdgeSquashGeometry(
            layoutLeftX = visibleLeftX,
            layoutWidthPx = visibleWidth,
            scaleX = 1f,
        )
    }
    return RightEdgeSquashGeometry(
        // Once the visible sliver is narrower than the card's corner diameter, keep the
        // surface at a stable size and let the viewport clip it away. Scaling this final
        // sliver also scales its corner radii and makes the leading edge visibly collapse.
        layoutLeftX = visibleLeftX,
        layoutWidthPx = minimumWidth,
        scaleX = 1f,
    )
}

internal fun shouldPreserveRightExitCardShape(
    visibleRightX: Float,
    viewportWidthPx: Float,
    trailingSurfaceOverflowPx: Float,
): Boolean = trailingSurfaceOverflowPx > 0f && visibleRightX >= viewportWidthPx - 0.5f

internal fun allDayPageIntersectsViewport(
    pageLeftX: Float,
    dayWidthPx: Float,
    viewportWidthPx: Float,
    visualOverflowPx: Float = 0f,
): Boolean = viewportWidthPx <= 0f || (
    pageLeftX < viewportWidthPx + visualOverflowPx.coerceAtLeast(0f) &&
    pageLeftX + dayWidthPx > -visualOverflowPx.coerceAtLeast(0f)
)

internal fun allDayPageLeftX(
    page: Int,
    anchorPage: Int,
    anchorOffsetPx: Float,
    dayStepPx: Float,
): Float = anchorOffsetPx + (page - anchorPage) * dayStepPx

internal fun buildAllDayViewportWindow(
    anchorPage: Int,
    anchorOffsetPx: Float,
    dayWidthPx: Float,
    dayStepPx: Float,
    viewportWidthPx: Float,
    bufferStartPage: Int,
    bufferEndPage: Int,
    renderBleedPx: Float,
    layoutViewportWidthPx: Float = viewportWidthPx,
): AllDayViewportWindow {
    fun intersectingPages(viewportEndPx: Float, overflowPx: Float): List<Int> =
        (bufferStartPage..bufferEndPage).filter { page ->
            val left = allDayPageLeftX(page, anchorPage, anchorOffsetPx, dayStepPx)
            allDayPageIntersectsViewport(
                pageLeftX = left,
                dayWidthPx = dayWidthPx,
                viewportWidthPx = viewportEndPx,
                visualOverflowPx = overflowPx,
            )
        }

    val fallbackPage = anchorPage.coerceIn(bufferStartPage, bufferEndPage)
    val layoutPages = intersectingPages(layoutViewportWidthPx, overflowPx = 0f).ifEmpty { listOf(fallbackPage) }
    val renderPages = intersectingPages(viewportWidthPx, overflowPx = renderBleedPx).ifEmpty { layoutPages }
    return AllDayViewportWindow(
        layoutPages = layoutPages,
        renderPages = renderPages,
    )
}

internal fun allDaySegmentVisualVisibility(
    segmentStartX: Float,
    segmentEndX: Float,
    viewportWidthPx: Float,
    fadeDistancePx: Float,
): Float {
    if (viewportWidthPx <= 0f) return 1f
    if (segmentEndX <= 0f || segmentStartX >= viewportWidthPx) return 0f
    val fadeDistance = fadeDistancePx.coerceAtLeast(1f)
    val leftExitProgress = (segmentEndX / fadeDistance).coerceIn(0f, 1f)
    val rightExitProgress = ((viewportWidthPx - segmentStartX) / fadeDistance).coerceIn(0f, 1f)
    return min(leftExitProgress, rightExitProgress)
}

internal fun allDayViewportCardBounds(
    segmentStartX: Float,
    segmentEndX: Float,
    continuesAfterSegment: Boolean,
    viewportWidthPx: Float,
    continuesBeforeSegment: Boolean = false,
): AllDayViewportCardBounds =
    AllDayViewportCardBounds(
        visibleLeftX = if (continuesBeforeSegment) 0f else max(segmentStartX, 0f),
        visibleRightX = if (continuesAfterSegment) viewportWidthPx else min(segmentEndX, viewportWidthPx),
    )

internal fun allDaySegmentContinuesPastViewport(
    itemStartPage: Int,
    itemEndPage: Int,
    segmentEndPage: Int,
    visibleEndPage: Int,
    segmentEndX: Float,
    viewportEndX: Float,
): Boolean =
    itemStartPage < itemEndPage && (
        (segmentEndPage == visibleEndPage && itemEndPage > visibleEndPage) ||
            segmentEndX > viewportEndX
    )

internal fun allDayLeadingContinuationProgress(
    itemStartPage: Int,
    itemEndPage: Int,
    itemStartX: Float,
    itemEndX: Float,
    dayWidthPx: Float,
    fadeExitDistancePx: Float,
    viewportStartX: Float = 0f,
): Float {
    if (itemStartPage >= itemEndPage || dayWidthPx <= 0f) return 0f
    val startExitProgress = ((viewportStartX - itemStartX) / dayWidthPx).coerceIn(0f, 1f)
    val endRemainingProgress = ((itemEndX - viewportStartX) / fadeExitDistancePx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    return min(startExitProgress, endRemainingProgress)
}

internal fun allDayContinuationFadeVisualProgress(
    continuationProgress: Float,
    transitionProgress: Float,
): Float = continuationProgress.coerceIn(0f, 1f) * transitionProgress.coerceIn(0f, 1f)

internal fun interpolateAllDayContinuationFadeProgress(
    collapsedProgress: Float,
    expandedProgress: Float,
    expansionProgress: Float,
): Float {
    val fraction = expansionProgress.coerceIn(0f, 1f)
    val start = collapsedProgress.coerceIn(0f, 1f)
    val end = expandedProgress.coerceIn(0f, 1f)
    return start + (end - start) * fraction
}

internal fun interpolateAllDayTransitionTitleGeometry(
    collapsedLeftX: Float,
    collapsedWidthPx: Float,
    collapsedLane: Float,
    expandedLeftX: Float,
    expandedWidthPx: Float,
    expandedLane: Float,
    progress: Float,
): AllDayTransitionTitleGeometry {
    val fraction = progress.coerceIn(0f, 1f)
    return AllDayTransitionTitleGeometry(
        leftX = collapsedLeftX + (expandedLeftX - collapsedLeftX) * fraction,
        widthPx = (
            collapsedWidthPx + (expandedWidthPx - collapsedWidthPx) * fraction
            ).coerceAtLeast(0f),
        lane = interpolateAllDayTransitionLane(collapsedLane, expandedLane, fraction),
    )
}

internal fun interpolateAllDayTransitionLane(
    collapsedLane: Float,
    expandedLane: Float,
    expansionProgress: Float,
): Float {
    val fraction = expansionProgress.coerceIn(0f, 1f)
    return collapsedLane + (expandedLane - collapsedLane) * fraction
}

internal fun interpolateAllDayVisualPieceFrame(
    collapsedLeftX: Float,
    collapsedWidthPx: Float,
    collapsedLane: Float,
    collapsedLeadingContinuationProgress: Float,
    expandedLeftX: Float,
    expandedWidthPx: Float,
    expandedLane: Float,
    expandedLeadingContinuationProgress: Float,
    expansionProgress: Float,
    primary: Boolean,
    visibleWhenCollapsed: Boolean,
): AllDayVisualPieceFrame {
    val progress = expansionProgress.coerceIn(0f, 1f)
    return AllDayVisualPieceFrame(
        leftX = if (primary) {
            collapsedLeftX + (expandedLeftX - collapsedLeftX) * progress
        } else {
            collapsedLeftX
        },
        widthPx = if (primary) {
            collapsedWidthPx + (expandedWidthPx - collapsedWidthPx) * progress
        } else {
            collapsedWidthPx
        }.coerceAtLeast(0f),
        lane = interpolateAllDayTransitionLane(
            collapsedLane = collapsedLane,
            expandedLane = expandedLane,
            expansionProgress = progress,
        ),
        alpha = when {
            !primary -> 1f - progress
            !visibleWhenCollapsed -> progress
            else -> 1f
        },
        leadingContinuationProgress = if (primary) {
            interpolateAllDayContinuationFadeProgress(
                collapsedProgress = collapsedLeadingContinuationProgress,
                expandedProgress = expandedLeadingContinuationProgress,
                expansionProgress = progress,
            )
        } else {
            collapsedLeadingContinuationProgress
        },
    )
}

internal fun allDayLeadingCornerRadiusFraction(continuationProgress: Float): Float =
    1f - continuationProgress.coerceIn(0f, 1f)

internal fun allDayLeadingCornerProgress(
    itemStartPage: Int,
    itemEndPage: Int,
    itemStartX: Float,
    dayWidthPx: Float,
    viewportStartX: Float = 0f,
): Float {
    if (itemStartPage >= itemEndPage || dayWidthPx <= 0f) return 0f
    return ((viewportStartX - itemStartX) / dayWidthPx).coerceIn(0f, 1f)
}

internal fun allDayTrailingCornerProgress(
    itemStartPage: Int,
    itemEndPage: Int,
    itemEndX: Float,
    dayWidthPx: Float,
    viewportEndX: Float,
): Float {
    if (itemStartPage >= itemEndPage || dayWidthPx <= 0f) return 0f
    val morphDistancePx = dayWidthPx * 0.22f
    return ((itemEndX - viewportEndX) / morphDistancePx).coerceIn(0f, 1f)
}

internal fun allDayStableSurfaceLeft(
    visibleWidthPx: Float,
    leadingRadiusPx: Float,
    trailingRadiusPx: Float,
    borderStrokePx: Float,
): Float = min(
    0f,
    visibleWidthPx - leadingRadiusPx - trailingRadiusPx - borderStrokePx.coerceAtLeast(0f),
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
        .thenByDescending { it.endPage }
        .thenBy { it.startPage }
        .thenBy { it.title }
        .thenBy { it.id }

internal fun buildCollapsedAllDayLayout(
    overlayItems: List<AllDayOverlayItem>,
    pageItemsByPage: Map<Int, List<AllDayOverlayItem>>,
    visibleStartPage: Int,
    visibleEndPage: Int,
    maxVisibleItems: Int,
    priorityStartPage: Int = visibleStartPage,
    priorityEndPage: Int = visibleEndPage,
): AllDayCollapsedLayout {
    val overflowVisibleLimit = when {
        maxVisibleItems <= 0 -> 0
        else -> (maxVisibleItems - 1).coerceAtLeast(0)
    }

    fun pageVisibleLimit(pageItems: List<AllDayOverlayItem>): Int =
        if (pageItems.size > maxVisibleItems) overflowVisibleLimit else maxVisibleItems

    val selectedIdsByPage = pageItemsByPage.mapValues { (_, items) ->
        items.take(pageVisibleLimit(items)).mapTo(hashSetOf()) { it.id }
    }
    fun itemVisibleOnPage(item: AllDayOverlayItem, page: Int): Boolean =
        item.id in selectedIdsByPage[page].orEmpty()

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

    val packedSegments = assignCollapsedAllDaySegmentLanes(
        segments = rawSegments,
        visibleStartPage = priorityStartPage,
        visibleEndPage = priorityEndPage,
        visibleLimitsByPage = pageItemsByPage.mapValues { pageVisibleLimit(it.value) },
    )
    // Only adjacent pieces with visible portions on BOTH sides of their real overlap
    // are a handoff. Off-screen events cannot introduce an abstraction into this row.
    val collisionsByCell = linkedMapOf<Pair<Int, Int>, MutableMap<String, AllDayOverlayItem>>()
    val visibleLaneByCell = packedSegments.flatMap { segment ->
        (segment.startPage..segment.endPage).map { (it to segment.item.id) to segment.lane }
    }.toMap()
    val collisionLaneByCell = mutableMapOf<Pair<Int, String>, Int>()
    packedSegments.groupBy { it.lane }.toSortedMap().forEach { (lane, pieces) ->
        pieces.sortedBy { it.startPage }.zipWithNext().forEach pair@{ (left, right) ->
            if (left.item.id == right.item.id) return@pair
            val overlapStart = max(left.item.startPage, right.item.startPage)
            val overlapEnd = min(left.item.endPage, right.item.endPage)
            if (overlapStart > overlapEnd || left.startPage >= overlapStart || right.endPage <= overlapEnd) return@pair
            (max(overlapStart, visibleStartPage)..min(overlapEnd, visibleEndPage)).forEach day@{ page ->
                val members = listOf(left.item, right.item)
                // A fragmented item may already be visible or represented on another row.
                // It must not also be counted in this row's abstraction.
                if (members.any { item ->
                    visibleLaneByCell[page to item.id]?.let { it != lane } == true ||
                        collisionLaneByCell[page to item.id]?.let { it != lane } == true
                }) return@day
                members.forEach { collisionLaneByCell[page to it.id] = lane }
                collisionsByCell.getOrPut(page to lane) { linkedMapOf() }.apply {
                    put(left.item.id, left.item)
                    put(right.item.id, right.item)
                }
            }
        }
    }
    val assignedSegments = packedSegments.flatMap { segment ->
        val result = mutableListOf<AllDayOverlaySegment>()
        var start = segment.startPage
        for (page in segment.startPage..segment.endPage) {
            if (page to segment.lane in collisionsByCell) {
                if (start < page) result += segment.copy(startPage = start, endPage = page - 1)
                start = page + 1
            }
        }
        if (start <= segment.endPage) result += segment.copy(startPage = start)
        result
    }
    val visibleIdsByPage = (visibleStartPage..visibleEndPage).associateWith { page ->
        assignedSegments.filter { page in it.startPage..it.endPage }.mapTo(hashSetOf()) { it.item.id }
    }
    fun itemHiddenOnCollapsedPage(item: AllDayOverlayItem, page: Int): Boolean =
        page in item.startPage..item.endPage && item.id !in visibleIdsByPage[page].orEmpty()
    val segmentsByItem = assignedSegments.groupBy { it.item.id }
    val continuations = overlayItems.flatMap { item ->
        val firstPage = max(item.startPage, visibleStartPage)
        val lastPage = min(item.endPage, visibleEndPage)
        if (firstPage > lastPage) return@flatMap emptyList()
        (firstPage..lastPage).mapNotNull { page ->
            if (!itemHiddenOnCollapsedPage(item, page)) return@mapNotNull null
            val previousSegment = segmentsByItem[item.id].orEmpty()
                .filter { it.endPage < page }
                .maxByOrNull { it.endPage }
            val nextSegment = segmentsByItem[item.id].orEmpty()
                .filter { it.startPage > page }
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
        collisions = collisionsByCell.map { (cell, items) ->
            AllDayOverflowGroup(cell.first, cell.second, items.values.toList(), collision = true)
        },
    )
}

private fun assignCollapsedAllDaySegmentLanes(
    segments: List<AllDayOverlaySegment>,
    visibleStartPage: Int,
    visibleEndPage: Int,
    visibleLimitsByPage: Map<Int, Int>,
): List<AllDayOverlaySegment> {
    if (segments.isEmpty()) return emptyList()
    val occupied = mutableSetOf<Pair<Int, Int>>()
    val comparator = allDayCollapsedPageItemComparator(visibleStartPage, visibleEndPage)
    fun free(page: Int, lane: Int) = lane < (visibleLimitsByPage[page] ?: 0) && page to lane !in occupied
    val result = mutableListOf<AllDayOverlaySegment>()
    fun assign(segment: AllDayOverlaySegment, start: Int, end: Int, lane: Int) {
        result += segment.copy(startPage = start, endPage = end, lane = lane)
        (start..end).forEach { occupied += it to lane }
    }
    segments.sortedWith(Comparator { a, b ->
        comparator.compare(a.item, b.item).takeIf { it != 0 } ?: a.startPage.compareTo(b.startPage)
    }).forEach { segment ->
        val pages = segment.startPage..segment.endPage
        val limit = pages.minOf { visibleLimitsByPage[it] ?: 0 }
        val continuousLane = (0 until limit).firstOrNull { lane -> pages.all { free(it, lane) } }
        if (continuousLane != null) {
            assign(segment, segment.startPage, segment.endPage, continuousLane)
        } else {
            // Fragmented intervals can defeat greedy whole-card packing even when
            // each day fits. Reuse a free row per day instead of exceeding the budget
            // or painting a card over that day's reserved overflow control.
            var start = segment.startPage
            var lane = (0 until (visibleLimitsByPage[start] ?: 0)).first { free(start, it) }
            for (page in segment.startPage + 1..segment.endPage) {
                if (!free(page, lane)) {
                    assign(segment, start, page - 1, lane)
                    start = page
                    lane = (0 until (visibleLimitsByPage[page] ?: 0)).first { free(page, it) }
                }
            }
            assign(segment, start, segment.endPage, lane)
        }
    }
    return result
}

internal fun buildAllDayScene(
    overlayItems: List<AllDayOverlayItem>,
    visibleStartPage: Int,
    visibleEndPage: Int,
    priorityStartPage: Int,
    priorityEndPage: Int,
    maxVisibleItems: Int,
): AllDayScene {
    val orderedItems = overlayItems.sortedWith(allDayCollapsedPageItemComparator(priorityStartPage, priorityEndPage))
    val pageItemsByPage = (visibleStartPage..visibleEndPage).associateWith { page ->
        orderedItems.filter { page in it.startPage..it.endPage }
    }
    val expandedRowCount = overlayItems.maxOfOrNull { it.lane + 1 } ?: 0
    val hasCollapsedOverflow = (visibleStartPage..visibleEndPage).any { page ->
        val itemCount = pageItemsByPage[page].orEmpty().size
        if (maxVisibleItems <= 0) itemCount > 0 else itemCount > maxVisibleItems
    }
    val collapsedVisibleItemLimit = when {
        maxVisibleItems <= 0 && expandedRowCount > 0 -> 0
        hasCollapsedOverflow -> (maxVisibleItems - 1).coerceAtLeast(0)
        else -> maxVisibleItems
    }
    val overflowLane = if (maxVisibleItems <= 0) 0 else collapsedVisibleItemLimit
    val collapsedLayout = buildCollapsedAllDayLayout(
        overlayItems = overlayItems,
        pageItemsByPage = pageItemsByPage,
        visibleStartPage = visibleStartPage,
        visibleEndPage = visibleEndPage,
        maxVisibleItems = maxVisibleItems,
        priorityStartPage = priorityStartPage,
        priorityEndPage = priorityEndPage,
    )
    val segmentsByItem = collapsedLayout.segments.groupBy { it.item.id }
    val visualPieces = overlayItems.flatMap { item ->
        val segments = segmentsByItem[item.id].orEmpty()
        val primarySegment = selectAllDayPrimarySegment(segments)
        if (segments.isEmpty()) {
            listOf(
                AllDayVisualPiece(
                    key = "${item.id}:primary",
                    item = item,
                    collapsedSegment = null,
                    primary = true,
                ),
            )
        } else {
            segments.map { segment ->
                val primary = segment == primarySegment
                AllDayVisualPiece(
                    key = if (primary) {
                        "${item.id}:primary"
                    } else {
                        "${item.id}:segment:${segment.startPage}:${segment.endPage}"
                    },
                    item = item,
                    collapsedSegment = segment,
                    primary = primary,
                )
            }
        }
    }
    val hiddenPages = pageItemsByPage.mapValues { (page, items) ->
        val visibleIds = collapsedLayout.segments.asSequence()
            .filter { page in it.startPage..it.endPage }.map { it.item.id }.toSet()
        items.filterNot { it.id in visibleIds }
    }.filterValues { it.isNotEmpty() }
    val overflowGroups = buildList {
        addAll(collapsedLayout.collisions)
        hiddenPages.forEach { (page, hiddenItems) ->
            val representedIds = collapsedLayout.collisions.asSequence().filter { it.page == page }
                .flatMap { it.items.asSequence() }.map { it.id }.toSet()
            val remaining = hiddenItems.filterNot { it.id in representedIds }
            if (remaining.isNotEmpty()) add(AllDayOverflowGroup(page, overflowLane, remaining))
        }
    }.groupBy { it.page to it.lane }.map { (_, groups) ->
        groups.first().copy(items = groups.flatMap { it.items }.distinctBy { it.id }, collision = groups.any { it.collision })
    }
    val visibleCollapsedRows = collapsedLayout.segments.maxOfOrNull { it.lane + 1 } ?: 0
    val collapsedRowCount = max(visibleCollapsedRows, overflowGroups.maxOfOrNull { it.lane + 1 } ?: 0)
    return AllDayScene(
        overlayItems = overlayItems,
        pageItemsByPage = pageItemsByPage,
        collapsedLayout = collapsedLayout,
        visualPieces = visualPieces.sortedBy { it.primary },
        hiddenPages = hiddenPages,
        overflowGroups = overflowGroups,
        metrics = AllDaySceneMetrics(
            expandedRowCount = expandedRowCount,
            collapsedRowCount = collapsedRowCount,
            hasCollapsedOverflow = hiddenPages.isNotEmpty(),
            collapsedVisibleItemLimit = collapsedVisibleItemLimit,
            overflowLane = overflowLane,
        ),
    )
}

internal fun selectAllDayPrimarySegment(
    segments: List<AllDayOverlaySegment>,
): AllDayOverlaySegment? = segments.minWithOrNull(
    compareBy<AllDayOverlaySegment> { it.startPage }
        .thenByDescending { it.endPage - it.startPage }
        .thenBy { it.endPage }
        .thenBy { it.item.id },
)
