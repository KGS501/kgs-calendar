package com.kgs.calendar.ui.platform

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal actual fun Modifier.mouseDragScroll(
    state: ScrollableState,
    orientation: Orientation,
    flingBehavior: FlingBehavior,
): Modifier = pointerInput(state, orientation, flingBehavior) {
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type != PointerType.Mouse) return@awaitEachGesture
            val horizontal = orientation == Orientation.Horizontal
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            // Content that handles the drag itself (e.g. moving an event) consumes it first.
            val start = if (horizontal) {
                awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            } else {
                awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            } ?: return@awaitEachGesture
            tracker.addPosition(start.uptimeMillis, start.position)
            val onDrag: (androidx.compose.ui.input.pointer.PointerInputChange) -> Unit = { change ->
                val delta = change.position - change.previousPosition
                state.dispatchRawDelta(-(if (horizontal) delta.x else delta.y))
                tracker.addPosition(change.uptimeMillis, change.position)
                change.consume()
            }
            val completed = if (horizontal) horizontalDrag(start.id, onDrag) else verticalDrag(start.id, onDrag)
            val velocity = tracker.calculateVelocity()
            val initialVelocity = if (!completed) 0f else -(if (horizontal) velocity.x else velocity.y)
            launch {
                state.scroll {
                    with(flingBehavior) { performFling(initialVelocity) }
                }
            }
        }
    }
}
