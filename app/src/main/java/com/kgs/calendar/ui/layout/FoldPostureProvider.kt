package com.kgs.calendar.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.kgs.calendar.ui.findActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The current fold posture; [FoldPosture.Normal] outside [ProvideFoldPosture] and on every non-foldable. */
internal val LocalFoldPosture = compositionLocalOf<FoldPosture> { FoldPosture.Normal }

/** The folding features of the Activity's window, from Jetpack WindowManager, while it is at least started. */
@Composable
internal fun rememberWindowFoldFeatures(): State<List<FoldFeatureSnapshot>> {
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

/**
 * Provides [LocalFoldPosture] for [content], with the hinge converted into this root's coordinates.
 *
 * The root box passes its exact size on to its children like the Compose root does, so the layout
 * inside is unchanged; it only measures where the root sits in the window.
 */
@Composable
internal fun ProvideFoldPosture(
    features: List<FoldFeatureSnapshot>,
    content: @Composable () -> Unit,
) {
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val posture = remember(features, rootOffset, rootSize) {
        foldPostureOf(
            features = features,
            rootLeftInWindowPx = rootOffset.x,
            rootTopInWindowPx = rootOffset.y,
            rootWidthPx = rootSize.width,
            rootHeightPx = rootSize.height,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                rootOffset = coordinates.positionInWindow()
                rootSize = coordinates.size
            },
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalFoldPosture provides posture, content = content)
    }
}

private fun FoldingFeature.toSnapshot(): FoldFeatureSnapshot = FoldFeatureSnapshot(
    halfOpened = state == FoldingFeature.State.HALF_OPENED,
    horizontal = orientation == FoldingFeature.Orientation.HORIZONTAL,
    left = bounds.left,
    top = bounds.top,
    right = bounds.right,
    bottom = bounds.bottom,
)
