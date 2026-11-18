package com.kgs.calendar.ui.layout

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import kotlin.math.max
import kotlin.math.min

data class AllDayOverlayItem(
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

data class AllDayOverlaySegment(
    val item: AllDayOverlayItem,
    val startPage: Int,
    val endPage: Int,
    val lane: Int,
    /** The item comes out of the day before's overflow card: the card fades in at its start. */
    val fromOverflow: Boolean = false,
    /** The item goes on into the next day's overflow card: the card fades out at its end. */
    val intoOverflow: Boolean = false,
)

/** The hidden items of an overloaded day, shown as one card in the bottom row. */
data class AllDayOverflowGroup(
    val page: Int,
    val lane: Int,
    val items: List<AllDayOverlayItem>,
)

data class AllDayCollapsedLayout(
    val segments: List<AllDayOverlaySegment>,
)

data class AllDayViewportWindow(
    val layoutPages: List<Int>,
    val renderPages: List<Int>,
) {
    val layoutStartPage: Int get() = layoutPages.first()
    val layoutEndPage: Int get() = layoutPages.last()
}

data class AllDayVisualPiece(
    val key: String,
    val item: AllDayOverlayItem,
    val collapsedSegment: AllDayOverlaySegment?,
    val primary: Boolean,
)

data class AllDayVisualPieceFrame(
    val leftX: Float,
    val widthPx: Float,
    val lane: Float,
    val alpha: Float,
    val leadingContinuationProgress: Float,
)

data class AllDaySceneMetrics(
    val expandedRowCount: Int,
    val collapsedRowCount: Int,
    val hasCollapsedOverflow: Boolean,
    val collapsedVisibleItemLimit: Int,
    val overflowLane: Int,
)

data class AllDayScene(
    val overlayItems: List<AllDayOverlayItem>,
    val pageItemsByPage: Map<Int, List<AllDayOverlayItem>>,
    val collapsedLayout: AllDayCollapsedLayout,
    val visualPieces: List<AllDayVisualPiece>,
    val hiddenPages: Map<Int, List<AllDayOverlayItem>>,
    val overflowGroups: List<AllDayOverflowGroup>,
    val metrics: AllDaySceneMetrics,
)

data class AllDayViewportCardBounds(
    val visibleLeftX: Float,
    val visibleRightX: Float,
)

data class AllDayTransitionTitleGeometry(
    val leftX: Float,
    val widthPx: Float,
    val lane: Float,
)

data class RightEdgeSquashGeometry(
    val layoutLeftX: Float,
    val layoutWidthPx: Float,
    val scaleX: Float,
)

fun rightEdgeSquashGeometry(
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

fun shouldPreserveRightExitCardShape(
    visibleRightX: Float,
    viewportWidthPx: Float,
    trailingSurfaceOverflowPx: Float,
): Boolean = trailingSurfaceOverflowPx > 0f && visibleRightX >= viewportWidthPx - 0.5f

fun allDayPageIntersectsViewport(
    pageLeftX: Float,
    dayWidthPx: Float,
    viewportWidthPx: Float,
    visualOverflowPx: Float = 0f,
): Boolean = viewportWidthPx <= 0f || (
    pageLeftX < viewportWidthPx + visualOverflowPx.coerceAtLeast(0f) &&
    pageLeftX + dayWidthPx > -visualOverflowPx.coerceAtLeast(0f)
)

fun allDayPageLeftX(
    page: Int,
    anchorPage: Int,
    anchorOffsetPx: Float,
    dayStepPx: Float,
): Float = anchorOffsetPx + (page - anchorPage) * dayStepPx

fun buildAllDayViewportWindow(
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

fun allDaySegmentVisualVisibility(
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

fun allDayViewportCardBounds(
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

fun allDaySegmentContinuesPastViewport(
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

fun allDayLeadingContinuationProgress(
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

fun allDayContinuationFadeVisualProgress(
    continuationProgress: Float,
    transitionProgress: Float,
): Float = continuationProgress.coerceIn(0f, 1f) * transitionProgress.coerceIn(0f, 1f)

fun interpolateAllDayContinuationFadeProgress(
    collapsedProgress: Float,
    expandedProgress: Float,
    expansionProgress: Float,
): Float {
    val fraction = expansionProgress.coerceIn(0f, 1f)
    val start = collapsedProgress.coerceIn(0f, 1f)
    val end = expandedProgress.coerceIn(0f, 1f)
    return start + (end - start) * fraction
}

fun interpolateAllDayTransitionTitleGeometry(
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

fun interpolateAllDayTransitionLane(
    collapsedLane: Float,
    expandedLane: Float,
    expansionProgress: Float,
): Float {
    val fraction = expansionProgress.coerceIn(0f, 1f)
    return collapsedLane + (expandedLane - collapsedLane) * fraction
}

fun interpolateAllDayVisualPieceFrame(
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

fun allDayLeadingCornerRadiusFraction(continuationProgress: Float): Float =
    1f - continuationProgress.coerceIn(0f, 1f)

fun allDayLeadingCornerProgress(
    itemStartPage: Int,
    itemEndPage: Int,
    itemStartX: Float,
    dayWidthPx: Float,
    viewportStartX: Float = 0f,
): Float {
    if (itemStartPage >= itemEndPage || dayWidthPx <= 0f) return 0f
    return ((viewportStartX - itemStartX) / dayWidthPx).coerceIn(0f, 1f)
}

fun allDayTrailingCornerProgress(
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

fun allDayStableSurfaceLeft(
    visibleWidthPx: Float,
    leadingRadiusPx: Float,
    trailingRadiusPx: Float,
    borderStrokePx: Float,
): Float = min(
    0f,
    visibleWidthPx - leadingRadiusPx - trailingRadiusPx - borderStrokePx.coerceAtLeast(0f),
)

/**
 * How many days before and after the visible ones take part in lane assignment. A row depends on
 * the items that started earlier and on how far an item runs, so this context keeps every visible
 * item in its row while the view scrolls: rows close up when an item ends, but never trade places.
 */
const val AllDayLaneLookbackPages = 62
const val AllDayLaneLookaheadPages = 31

/**
 * The order in which all-day items claim rows and the limited space of a day: earlier starts
 * first, so an item keeps its row and stays visible for as long as it runs and incoming items
 * never push it aside; on the same start day the longer item first. It does not depend on the
 * visible window, so scrolling cannot reorder items.
 */
val AllDayItemOrder: Comparator<AllDayOverlayItem> =
    compareBy<AllDayOverlayItem> { it.startPage }
        .thenByDescending { it.endPage }
        .thenBy { it.title }
        .thenBy { it.id }

/** Renumbers [lanes] in order, closing the gaps of lanes that have nothing in the visible window. */
fun compactLanes(lanes: Collection<Int>): Map<Int, Int> =
    lanes.distinct().sorted().withIndex().associate { (index, lane) -> lane to index }

fun buildCollapsedAllDayLayout(
    overlayItems: List<AllDayOverlayItem>,
    pageItemsByPage: Map<Int, List<AllDayOverlayItem>>,
    visibleStartPage: Int,
    visibleEndPage: Int,
    maxVisibleItems: Int,
    /** Items of the look-back range as well, so rows don't depend on where the window starts. */
    layoutItems: List<AllDayOverlayItem> = overlayItems,
): AllDayCollapsedLayout {
    val overflowVisibleLimit = when {
        maxVisibleItems <= 0 -> 0
        else -> (maxVisibleItems - 1).coerceAtLeast(0)
    }

    fun pageVisibleLimit(pageItems: List<AllDayOverlayItem>): Int =
        if (pageItems.size > maxVisibleItems) overflowVisibleLimit else maxVisibleItems

    val visibleItemsById = overlayItems.associateBy { it.id }
    val orderedLayoutItems = layoutItems.sortedWith(AllDayItemOrder)
    val laneStartPage = min(
        visibleStartPage,
        max(visibleStartPage - AllDayLaneLookbackPages, orderedLayoutItems.minOfOrNull { it.startPage } ?: visibleStartPage),
    )
    val laneEndPage = max(
        visibleEndPage,
        min(visibleEndPage + AllDayLaneLookaheadPages, orderedLayoutItems.maxOfOrNull { it.endPage } ?: visibleEndPage),
    )
    val layoutPageItems = (laneStartPage..laneEndPage).associateWith { page ->
        pageItemsByPage[page] ?: orderedLayoutItems.filter { page in it.startPage..it.endPage }
    }
    val selectedIdsByPage = layoutPageItems.mapValues { (_, items) ->
        items.take(pageVisibleLimit(items)).mapTo(hashSetOf()) { it.id }
    }
    fun itemVisibleOnPage(item: AllDayOverlayItem, page: Int): Boolean =
        item.id in selectedIdsByPage[page].orEmpty()

    val rawSegments = orderedLayoutItems.flatMap { item ->
        val visiblePages = (max(item.startPage, laneStartPage)..min(item.endPage, laneEndPage))
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

    val laidOutSegments = assignCollapsedAllDaySegmentLanes(
        segments = rawSegments,
        visibleLimitsByPage = layoutPageItems.mapValues { pageVisibleLimit(it.value) },
        overflowLaneByPage = layoutPageItems
            .filterValues { maxVisibleItems > 0 && it.size > maxVisibleItems }
            .mapValues { overflowVisibleLimit },
    ).mapNotNull { segment ->
        val item = visibleItemsById[segment.item.id] ?: return@mapNotNull null
        if (segment.endPage < visibleStartPage || segment.startPage > visibleEndPage) return@mapNotNull null
        segment.copy(item = item, startPage = max(segment.startPage, visibleStartPage), endPage = min(segment.endPage, visibleEndPage))
    }
    val collapsedLanes = compactLanes(laidOutSegments.map { it.lane })
    val packedSegments = laidOutSegments.map { it.copy(lane = collapsedLanes.getValue(it.lane)) }
    val visibleIdsByPage = (visibleStartPage..visibleEndPage).associateWith { page ->
        packedSegments.filter { page in it.startPage..it.endPage }.mapTo(hashSetOf()) { it.item.id }
    }
    fun hiddenOn(item: AllDayOverlayItem, page: Int): Boolean =
        page in visibleStartPage..visibleEndPage && page in item.startPage..item.endPage &&
            item.id !in visibleIdsByPage[page].orEmpty()
    return AllDayCollapsedLayout(
        segments = packedSegments.map { segment ->
            segment.copy(
                fromOverflow = hiddenOn(segment.item, segment.startPage - 1),
                intoOverflow = hiddenOn(segment.item, segment.endPage + 1),
            )
        },
    )
}

private fun assignCollapsedAllDaySegmentLanes(
    segments: List<AllDayOverlaySegment>,
    visibleLimitsByPage: Map<Int, Int>,
    /** The row of each overloaded day's overflow control. */
    overflowLaneByPage: Map<Int, Int> = emptyMap(),
): List<AllDayOverlaySegment> {
    if (segments.isEmpty()) return emptyList()
    val occupants = mutableMapOf<Pair<Int, Int>, String>()
    fun free(page: Int, lane: Int) = lane < (visibleLimitsByPage[page] ?: 0) && page to lane !in occupants
    val result = mutableListOf<AllDayOverlaySegment>()
    fun assign(segment: AllDayOverlaySegment, start: Int, end: Int, lane: Int) {
        result += segment.copy(startPage = start, endPage = end, lane = lane)
        (start..end).forEach { occupants[it to lane] = segment.item.id }
    }
    segments.sortedWith(Comparator { a, b ->
        AllDayItemOrder.compare(a.item, b.item).takeIf { it != 0 } ?: a.startPage.compareTo(b.startPage)
    }).forEach { segment ->
        val pages = segment.startPage..segment.endPage
        val limit = pages.minOf { visibleLimitsByPage[it] ?: 0 }
        val continuous = (0 until limit).filter { lane -> pages.all { free(it, lane) } }
        // A card that returns after a day it spent in the overflow continues in that day's
        // overflow row, so it visibly comes out of the abstraction, and never right after another
        // card of that day, which would read as one event changing into another.
        val previousPage = segment.startPage - 1
        val returning = segment.item.startPage <= previousPage
        val continuousLane = if (returning) {
            val overflowLane = overflowLaneByPage[previousPage]
            continuous.firstOrNull { it == overflowLane }
                ?: continuous.firstOrNull { lane -> occupants[previousPage to lane] == null }
                ?: continuous.firstOrNull()
        } else {
            continuous.firstOrNull()
        }
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

/**
 * The all-day section of [visibleStartPage]..[visibleEndPage]. [overlayItems] may include items of
 * the look-back range before it (see [buildAllDayOverlayItems]); they place the visible items in
 * stable rows but are not shown. Rows without a visible item close up.
 */
fun buildAllDayScene(
    overlayItems: List<AllDayOverlayItem>,
    visibleStartPage: Int,
    visibleEndPage: Int,
    maxVisibleItems: Int,
): AllDayScene {
    val visibleInWindow = overlayItems.filter { it.endPage >= visibleStartPage && it.startPage <= visibleEndPage }
    val expandedLanes = compactLanes(visibleInWindow.map { it.lane })
    val windowItems = visibleInWindow.map { it.copy(lane = expandedLanes.getValue(it.lane)) }
    val orderedItems = windowItems.sortedWith(AllDayItemOrder)
    val pageItemsByPage = (visibleStartPage..visibleEndPage).associateWith { page ->
        orderedItems.filter { page in it.startPage..it.endPage }
    }
    val expandedRowCount = windowItems.maxOfOrNull { it.lane + 1 } ?: 0
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
        overlayItems = windowItems,
        pageItemsByPage = pageItemsByPage,
        visibleStartPage = visibleStartPage,
        visibleEndPage = visibleEndPage,
        maxVisibleItems = maxVisibleItems,
        layoutItems = overlayItems,
    )
    val segmentsByItem = collapsedLayout.segments.groupBy { it.item.id }
    val visualPieces = windowItems.flatMap { item ->
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
    // The overflow card always takes the bottom row; the cards of the day fill the rows above it.
    val overflowGroups = hiddenPages.map { (page, hiddenItems) -> AllDayOverflowGroup(page, overflowLane, hiddenItems) }
    val visibleCollapsedRows = collapsedLayout.segments.maxOfOrNull { it.lane + 1 } ?: 0
    val collapsedRowCount = max(visibleCollapsedRows, overflowGroups.maxOfOrNull { it.lane + 1 } ?: 0)
    return AllDayScene(
        overlayItems = windowItems,
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

fun selectAllDayPrimarySegment(
    segments: List<AllDayOverlaySegment>,
): AllDayOverlaySegment? = segments.minWithOrNull(
    compareBy<AllDayOverlaySegment> { it.startPage }
        .thenByDescending { it.endPage - it.startPage }
        .thenBy { it.endPage }
        .thenBy { it.item.id },
)
