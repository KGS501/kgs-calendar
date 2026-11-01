package com.kgs.calendar.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.kgs.calendar.ui.platform.findActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The folding features of the Activity's window, from Jetpack WindowManager, while it is at least started. */
@Composable
actual fun rememberWindowFoldFeatures(): State<List<FoldFeatureSnapshot>> {
    val context = LocalContext.current
    val features = remember(context) {
        val activity = context.findActivity()
        if (activity == null) {
            flowOf(emptyList())
        } else {
            WindowInfoTracker.getOrCreate(activity)
                .windowLayoutInfo(activity)
                .map { info -> info.displayFeatures.filterIsInstance<FoldingFeature>().map { it.toSnapshot() } }
                .distinctUntilChanged()
        }
    }
    return features.collectAsStateWithLifecycle(initialValue = emptyList())
}

private fun FoldingFeature.toSnapshot(): FoldFeatureSnapshot = FoldFeatureSnapshot(
    halfOpened = state == FoldingFeature.State.HALF_OPENED,
    horizontal = orientation == FoldingFeature.Orientation.HORIZONTAL,
    left = bounds.left,
    top = bounds.top,
    right = bounds.right,
    bottom = bounds.bottom,
)
