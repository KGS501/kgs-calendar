package com.kgs.calendar.ui.platform

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.Modifier

/** Android scroll containers follow mouse drags themselves. */
internal actual fun Modifier.mouseDragScroll(
    state: ScrollableState,
    orientation: Orientation,
    flingBehavior: FlingBehavior,
): Modifier = this
