package com.kgs.calendar.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

@Composable
@ReadOnlyComposable
internal actual fun currentWindowMetrics(): WindowMetrics {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    val widthDp = (size.width / density).toInt()
    val heightDp = (size.height / density).toInt()
    return WindowMetrics(widthDp = widthDp, heightDp = heightDp, isLandscape = widthDp > heightDp)
}
