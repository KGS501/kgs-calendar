package com.kgs.calendar.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.isActive

/**
 * The scrolling page around a reorder list: its scroll state and the window bounds of its
 * viewport, so a row dragged to the page's edge can scroll the page.
 */
internal class ReorderScrollHost(val scrollState: ScrollState) {
    var viewport: Rect? = null
}

internal val LocalReorderScrollHost = staticCompositionLocalOf<ReorderScrollHost?> { null }

/**
 * While [dragging], scrolls the page of [LocalReorderScrollHost] when the dragged row, at
 * [rowBounds] in window coordinates, is held near the top or bottom edge of the page, faster the
 * deeper it is in that edge. The page moves the row with it, so [onScrolled] gets each scrolled
 * distance to add to the drag; the row then stays under the finger.
 */
@Composable
internal fun ReorderAutoScroll(
    dragging: Boolean,
    rowBounds: () -> Rect?,
    onScrolled: (Float) -> Unit,
) {
    val host = LocalReorderScrollHost.current ?: return
    val density = LocalDensity.current
    val edgePx = with(density) { ReorderAutoScrollEdge.toPx() }
    val maxSpeedPx = with(density) { ReorderAutoScrollMaxSpeedPerSecond.toPx() }
    val currentRowBounds = rememberUpdatedState(rowBounds)
    val currentOnScrolled = rememberUpdatedState(onScrolled)
    LaunchedEffect(dragging, host) {
        if (!dragging) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (isActive) {
            val frame = withFrameNanos { it }
            val seconds = (frame - lastFrame) / 1_000_000_000f
            lastFrame = frame
            val viewport = host.viewport ?: continue
            val row = currentRowBounds.value() ?: continue
            val speed = reorderAutoScrollSpeed(row.top, row.bottom, viewport.top, viewport.bottom, edgePx, maxSpeedPx)
            if (speed == 0f) continue
            val scrolled = host.scrollState.scrollBy(speed * seconds)
            if (scrolled != 0f) currentOnScrolled.value(scrolled)
        }
    }
}

/**
 * How fast (px per second, negative is up) the page scrolls for a dragged row from [rowTop] to
 * [rowBottom] in a viewport from [viewportTop] to [viewportBottom]: zero outside the [edge] zones,
 * rising to [maxSpeed] at the viewport's edge and beyond.
 */
internal fun reorderAutoScrollSpeed(
    rowTop: Float,
    rowBottom: Float,
    viewportTop: Float,
    viewportBottom: Float,
    edge: Float,
    maxSpeed: Float,
): Float {
    if (edge <= 0f || viewportBottom - viewportTop <= 2 * edge) return 0f
    val intoTop = viewportTop + edge - rowTop
    val intoBottom = rowBottom - (viewportBottom - edge)
    return when {
        intoTop > 0f && intoTop >= intoBottom -> -maxSpeed * (intoTop / edge).coerceAtMost(1f)
        intoBottom > 0f -> maxSpeed * (intoBottom / edge).coerceAtMost(1f)
        else -> 0f
    }
}

private val ReorderAutoScrollEdge = 64.dp
private val ReorderAutoScrollMaxSpeedPerSecond = 720.dp
