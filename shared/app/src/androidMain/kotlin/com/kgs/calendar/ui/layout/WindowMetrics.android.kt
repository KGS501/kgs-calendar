package com.kgs.calendar.ui.layout

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
@ReadOnlyComposable
internal actual fun currentWindowMetrics(): WindowMetrics {
    val configuration = LocalConfiguration.current
    return WindowMetrics(
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
        isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
    )
}
