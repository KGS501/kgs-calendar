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
