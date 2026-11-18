package com.kgs.calendar.ui.platform

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.Modifier

/**
 * Lets a mouse drag scroll [state] like a finger does, ending in [flingBehavior] so pagers snap
 * as after a swipe. Touch already scrolls on every platform; desktop scroll containers otherwise
 * only follow the wheel. The drag starts past the touch slop, so clicks still reach the content.
 */
internal expect fun Modifier.mouseDragScroll(
    state: ScrollableState,
    orientation: Orientation,
    flingBehavior: FlingBehavior,
): Modifier
