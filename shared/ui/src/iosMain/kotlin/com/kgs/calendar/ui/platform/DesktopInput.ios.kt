package com.kgs.calendar.ui.platform

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon

/** Touch screens scroll their containers themselves; mouse dragging is a desktop addition. */
internal actual fun Modifier.mouseDragScroll(
    state: ScrollableState,
    orientation: Orientation,
    flingBehavior: FlingBehavior,
): Modifier = this

internal actual val ResizeHorizontalPointerIcon: PointerIcon = PointerIcon.Default

/** Keyboard shortcuts are the desktop's. */
@Composable
internal actual fun ProvideCalendarShortcuts(shortcuts: CalendarShortcuts, enabled: Boolean) = Unit
