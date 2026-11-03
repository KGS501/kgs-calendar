package com.kgs.calendar.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.kgs.calendar.domain.model.CalendarWindowLayout

/** The app window in dp, from the window's container size. */
@Composable
actual fun currentCalendarWindowLayout(): CalendarWindowLayout {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    val widthDp = (size.width / density).toInt()
    val heightDp = (size.height / density).toInt()
    return CalendarWindowLayout(isLandscape = widthDp > heightDp, widthDp = widthDp, heightDp = heightDp)
}
