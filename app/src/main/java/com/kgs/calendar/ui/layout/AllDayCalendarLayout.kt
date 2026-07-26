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
        .thenByDescending { it.endPage - it.startPage }
        .thenBy { it.startPage }
        .thenBy { it.title }
        .thenBy { it.id }

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

    val assignedSegments = assignCollapsedAllDaySegmentLanes(
        segments = rawSegments,
        visibleStartPage = visibleStartPage,
        visibleEndPage = visibleEndPage,
    )
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

private fun assignCollapsedAllDaySegmentLanes(
    segments: List<AllDayOverlaySegment>,
    visibleStartPage: Int,
    visibleEndPage: Int,
): List<AllDayOverlaySegment> {
    if (segments.isEmpty()) return emptyList()
    val laneSegments = mutableListOf<MutableList<AllDayOverlaySegment>>()
    return segments
        .sortedWith(
            compareBy<AllDayOverlaySegment> {
                allDayViewportPriorityTier(
                    startPage = it.item.startPage,
                    endPage = it.item.endPage,
                    visibleStartPage = visibleStartPage,
                    visibleEndPage = visibleEndPage,
                )
            }
                .thenByDescending { it.item.endPage - it.item.startPage }
                .thenBy { it.item.startPage }
                .thenBy { it.item.title }
                .thenBy { it.item.id }
                .thenBy { it.startPage },
        )
        .map { segment ->
            val lane = laneSegments.indexOfFirst { assigned ->
                assigned.none { existing ->
                    segment.startPage <= existing.endPage && existing.startPage <= segment.endPage
                }
            }.let { index ->
                if (index >= 0) index else laneSegments.size.also { laneSegments.add(mutableListOf()) }
            }
            laneSegments[lane] += segment
            segment.copy(lane = lane)
        }
}

internal fun buildAllDayScene(
    overlayItems: List<AllDayOverlayItem>,
    visibleStartPage: Int,
    visibleEndPage: Int,
    priorityStartPage: Int,
    priorityEndPage: Int,
    maxVisibleItems: Int,
): AllDayScene {
    val pageItemsByPage = (visibleStartPage..visibleEndPage).associateWith { page ->
        overlayItems
            .filter { page in it.startPage..it.endPage }
            .sortedWith(allDayCollapsedPageItemComparator(priorityStartPage, priorityEndPage))
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
        collapsedVisibleItemLimit = collapsedVisibleItemLimit,
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
    val hiddenPages = if (!hasCollapsedOverflow) {
        emptyMap()
    } else {
        (visibleStartPage..visibleEndPage).mapNotNull { page ->
            val pageItems = pageItemsByPage[page].orEmpty()
            if (pageItems.size <= maxVisibleItems) return@mapNotNull null
            val hiddenItems = pageItems.drop(collapsedVisibleItemLimit)
            if (hiddenItems.isEmpty()) null else page to hiddenItems
        }.toMap()
    }
    val visibleCollapsedRows = collapsedLayout.segments.maxOfOrNull { it.lane + 1 } ?: 0
    val collapsedRowCount = max(
        visibleCollapsedRows,
        if (hasCollapsedOverflow) overflowLane + 1 else 0,
    )
    return AllDayScene(
        overlayItems = overlayItems,
        pageItemsByPage = pageItemsByPage,
        collapsedLayout = collapsedLayout,
        visualPieces = visualPieces,
        hiddenPages = hiddenPages,
        metrics = AllDaySceneMetrics(
            expandedRowCount = expandedRowCount,
            collapsedRowCount = collapsedRowCount,
            hasCollapsedOverflow = hasCollapsedOverflow,
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
