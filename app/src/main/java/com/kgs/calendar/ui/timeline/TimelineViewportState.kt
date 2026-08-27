package com.kgs.calendar.ui.timeline

import kotlin.math.abs

internal data class TimelineViewportState(
    val hourHeightPx: Float,
    val scrollPx: Float,
) {
    init {
        require(hourHeightPx.isFinite() && hourHeightPx > 0f) {
            "Hour height must be finite and positive"
        }
        require(scrollPx.isFinite() && scrollPx >= 0f) {
            "Scroll must be finite and non-negative"
        }
    }

    fun minuteAt(
        viewportY: Float,
        contentStartMinute: Int,
        contentTopY: Float = 0f,
    ): Float = contentStartMinute +
        ((scrollPx + viewportY - contentTopY) / hourHeightPx) * MINUTES_PER_HOUR
}

internal data class TimelineOrientationZoom(
    val portraitHourHeightDp: Float,
    val landscapeHourHeightDp: Float,
) {
    init {
        require(portraitHourHeightDp.isFinite() && portraitHourHeightDp > 0f)
        require(landscapeHourHeightDp.isFinite() && landscapeHourHeightDp > 0f)
    }

    fun hourHeightDp(isLandscape: Boolean): Float =
        if (isLandscape) landscapeHourHeightDp else portraitHourHeightDp

    fun withHourHeightDp(isLandscape: Boolean, hourHeightDp: Float): TimelineOrientationZoom =
        if (isLandscape) {
            copy(landscapeHourHeightDp = hourHeightDp)
        } else {
            copy(portraitHourHeightDp = hourHeightDp)
        }
}

class TimelineOrientationViewportMemory(
    portraitTopMinute: Float = DEFAULT_TIMELINE_TOP_MINUTE,
    landscapeTopMinute: Float = DEFAULT_TIMELINE_TOP_MINUTE,
) {
    private var portraitTopMinute = portraitTopMinute.normalizedTimelineTopMinute()
    private var landscapeTopMinute = landscapeTopMinute.normalizedTimelineTopMinute()

    fun topMinute(isLandscape: Boolean): Float =
        if (isLandscape) landscapeTopMinute else portraitTopMinute

    fun updateTopMinute(isLandscape: Boolean, topMinute: Float) {
        if (isLandscape) {
            landscapeTopMinute = topMinute.normalizedTimelineTopMinute()
        } else {
            portraitTopMinute = topMinute.normalizedTimelineTopMinute()
        }
    }
}

internal fun TimelineViewportState.withHourHeightPx(
    hourHeightPx: Float,
    viewportHeightPx: Float,
    contentStartMinute: Int,
    contentEndMinute: Int,
): TimelineViewportState {
    require(hourHeightPx.isFinite() && hourHeightPx > 0f)
    require(viewportHeightPx.isFinite() && viewportHeightPx >= 0f)
    require(contentEndMinute > contentStartMinute)
    val topVisibleMinute = minuteAt(
        viewportY = 0f,
        contentStartMinute = contentStartMinute,
    )
    val contentHeightPx =
        ((contentEndMinute - contentStartMinute) / MINUTES_PER_HOUR) * hourHeightPx
    val maxScrollPx = (contentHeightPx - viewportHeightPx).coerceAtLeast(0f)
    val reprojectedScrollPx =
        ((topVisibleMinute - contentStartMinute) / MINUTES_PER_HOUR) * hourHeightPx
    return TimelineViewportState(
        hourHeightPx = hourHeightPx,
        scrollPx = reprojectedScrollPx.coerceIn(0f, maxScrollPx),
    )
}

internal class PinchSnapshot private constructor(
    val initialUpperY: Float,
    val initialLowerY: Float,
    val initialViewport: TimelineViewportState,
    val contentStartMinute: Int,
    val contentEndMinute: Int,
    val viewportHeightPx: Float,
    val contentTopY: Float,
    val minHourHeightPx: Float,
    val maxHourHeightPx: Float,
    val anchorMinute: Float,
) {
    val initialSpanPx: Float = abs(initialLowerY - initialUpperY)

    companion object {
        fun begin(
            upperY: Float,
            lowerY: Float,
            viewport: TimelineViewportState,
            contentStartMinute: Int,
            contentEndMinute: Int,
            viewportHeightPx: Float,
            minHourHeightPx: Float,
            maxHourHeightPx: Float,
            contentTopY: Float = 0f,
        ): PinchSnapshot {
            requireFinite(upperY, lowerY, viewportHeightPx, minHourHeightPx, maxHourHeightPx, contentTopY)
            require(abs(lowerY - upperY) > 0f) { "Initial pinch span must be positive" }
            require(contentEndMinute > contentStartMinute) { "Timeline content range must be positive" }
            require(viewportHeightPx > 0f) { "Viewport height must be positive" }
            require(minHourHeightPx > 0f && maxHourHeightPx >= minHourHeightPx) {
                "Hour-height bounds are invalid"
            }
            val centroidY = (upperY + lowerY) / 2f
            return PinchSnapshot(
                initialUpperY = upperY,
                initialLowerY = lowerY,
                initialViewport = viewport,
                contentStartMinute = contentStartMinute,
                contentEndMinute = contentEndMinute,
                viewportHeightPx = viewportHeightPx,
                contentTopY = contentTopY,
                minHourHeightPx = minHourHeightPx,
                maxHourHeightPx = maxHourHeightPx,
                anchorMinute = viewport.minuteAt(centroidY, contentStartMinute, contentTopY),
            )
        }
    }
}

internal data class VerticalPinchUpdate(
    val viewport: TimelineViewportState,
    val centroidY: Float,
    val minuteAtCentroid: Float,
)

internal fun updateVerticalPinch(
    start: PinchSnapshot,
    upperY: Float,
    lowerY: Float,
): VerticalPinchUpdate {
    requireFinite(upperY, lowerY)
    val currentSpanPx = abs(lowerY - upperY)
    val scale = currentSpanPx / start.initialSpanPx
    val hourHeightPx = (start.initialViewport.hourHeightPx * scale)
        .coerceIn(start.minHourHeightPx, start.maxHourHeightPx)
    val centroidY = (upperY + lowerY) / 2f
    val desiredScrollPx =
        ((start.anchorMinute - start.contentStartMinute) / MINUTES_PER_HOUR) * hourHeightPx -
            (centroidY - start.contentTopY)
    val contentHeightPx =
        ((start.contentEndMinute - start.contentStartMinute) / MINUTES_PER_HOUR) * hourHeightPx
    val maxScrollPx = (contentHeightPx - start.viewportHeightPx).coerceAtLeast(0f)
    val viewport = TimelineViewportState(
        hourHeightPx = hourHeightPx,
        scrollPx = desiredScrollPx.coerceIn(0f, maxScrollPx),
    )
    return VerticalPinchUpdate(
        viewport = viewport,
        centroidY = centroidY,
        minuteAtCentroid = viewport.minuteAt(
            viewportY = centroidY,
            contentStartMinute = start.contentStartMinute,
            contentTopY = start.contentTopY,
        ),
    )
}

private fun requireFinite(vararg values: Float) {
    require(values.all(Float::isFinite)) { "Pinch values must be finite" }
}

private const val MINUTES_PER_HOUR = 60f
private const val DEFAULT_TIMELINE_TOP_MINUTE = 9f * MINUTES_PER_HOUR
private const val LAST_TIMELINE_MINUTE = 24f * MINUTES_PER_HOUR

private fun Float.normalizedTimelineTopMinute(): Float =
    if (isFinite()) coerceIn(0f, LAST_TIMELINE_MINUTE) else DEFAULT_TIMELINE_TOP_MINUTE
