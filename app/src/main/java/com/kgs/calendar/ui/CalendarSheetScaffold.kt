@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.composed
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import com.kgs.calendar.ui.layout.BookPane
import com.kgs.calendar.ui.layout.FoldPanelSlot
import com.kgs.calendar.ui.layout.FoldPosture
import com.kgs.calendar.ui.layout.LocalFoldPosture
import com.kgs.calendar.ui.layout.bookPaneHideDeltaPx
import com.kgs.calendar.ui.layout.bookPaneSlideOffsetPx
import com.kgs.calendar.ui.layout.foldPanelSlot
import com.kgs.calendar.ui.layout.paneBounds
import com.kgs.calendar.ui.layout.SheetMaxWidth
import com.kgs.calendar.ui.layout.tabletopPanelImeOverlapPx
import com.kgs.calendar.ui.layout.largeScreenMaxWidth
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.abs

internal data class SheetInsetPolicy(
    val respectsImeBottomInset: Boolean,
)

internal val sheetInsetPolicy = SheetInsetPolicy(
    respectsImeBottomInset = true,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KgsModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    initialSnap: SheetSnap = SheetSnap.Half,
    initialContentHeight: Dp? = null,
    dimBackground: Boolean = true,
    dismissOnOutsideTap: Boolean = true,
    collapseRequest: Int = 0,
    expandRequest: Int = 0,
    collapseSnap: SheetSnap = SheetSnap.Quarter,
    anchorMode: SheetAnchorMode = SheetAnchorMode.ContentFit,
    onSnapChanged: (SheetSnap) -> Unit = {},
    onBackRequest: (() -> Unit)? = null,
    separationShadow: Boolean = false,
    followFoldPosture: Boolean = false,
    bookPane: BookPane = BookPane.Right,
    content: @Composable ColumnScope.() -> Unit,
) {
    val foldPosture = if (followFoldPosture) LocalFoldPosture.current else FoldPosture.Normal
    // In the tabletop posture the detail and editor sheets become a panel covering the top half, above
    // the hinge, so the calendar below stays visible and usable and the keyboard opens below the panel.
    val tabletopPanelBottomPx = (foldPosture as? FoldPosture.Tabletop)?.hingeTopPx
    // In the book posture they fill [bookPane] from top to bottom, beside the hinge, while the other pane
    // stays visible and usable. The keyboard spans both panes, so the pane pads for it like a bottom sheet.
    val bookPosture = foldPosture as? FoldPosture.Book
    val panelSlot = foldPanelSlot(foldPosture, bookPane)
    val scope = rememberCoroutineScope()
    val quietInteraction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val navigationBarColor = Color.Transparent.toArgb()

    DisposableEffect(view, navigationBarColor) {
        val window = view.context.findActivity()?.window
        val previousColor = window?.navigationBarColor
        window?.navigationBarColor = navigationBarColor
        onDispose {
            if (previousColor != null) {
                window.navigationBarColor = previousColor
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
    ) {
            val screenHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val density = LocalDensity.current
            val sheetBottomPx = screenHeightPx
            val expandedAnchor = screenHeightPx * 0.065f
            val halfAnchor = screenHeightPx * 0.50f
            val hiddenAnchor = sheetBottomPx
            val navigationBarInsetPx = with(density) {
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx()
            }
            // While the keyboard is open the stops count from its top edge, so what a stop shows stays
            // above the keyboard instead of disappearing under it.
            val imeBottomPx = if (sheetInsetPolicy.respectsImeBottomInset) WindowInsets.ime.getBottom(density).toFloat() else 0f
            val sheetVisualBottomPx = (hiddenAnchor - max(navigationBarInsetPx, imeBottomPx)).coerceAtLeast(expandedAnchor + 1f)
            val quarterAnchor = (sheetVisualBottomPx - screenHeightPx * 0.18f).coerceIn(expandedAnchor, hiddenAnchor)
            val editorSmallAnchor = (sheetVisualBottomPx - with(density) { EditorSmallVisibleHeight.toPx() }).coerceAtLeast(expandedAnchor)
            val editorTinyAnchor = (sheetVisualBottomPx - with(density) { EditorTinyVisibleHeight.toPx() }).coerceAtLeast(expandedAnchor)
            // Heights of the content parts that measure themselves (see [sheetContentPart]).
            val contentParts = remember { mutableStateMapOf<String, Int>() }
            val sheetHandlePx = with(density) { SheetHandleHeight.toPx() }
            /** Where the sheet shows all of its content, or as much as fits. */
            fun currentContentAnchor(): Float? {
                if (contentParts.isNotEmpty()) {
                    val visible = (contentParts.values.sum() + sheetHandlePx).coerceAtMost(sheetVisualBottomPx - expandedAnchor)
                    return (sheetVisualBottomPx - visible).coerceAtLeast(expandedAnchor)
                }
                return initialContentHeight?.let { requestedHeight ->
                    val minVisibleHeight = with(density) { 148.dp.toPx() }
                    val sheetChromeAndBreathingRoom = with(density) { 104.dp.toPx() }
                    val requestedHeightPx = (with(density) { requestedHeight.toPx() } + sheetChromeAndBreathingRoom)
                        .coerceIn(minVisibleHeight, sheetVisualBottomPx - expandedAnchor)
                    (sheetVisualBottomPx - requestedHeightPx).coerceIn(expandedAnchor, sheetVisualBottomPx - minVisibleHeight)
                }
            }
            val contentAnchor = currentContentAnchor()
            var sheetOffsetPx by remember { mutableFloatStateOf(hiddenAnchor) }
            var edgeBouncePx by remember { mutableFloatStateOf(0f) }
            var edgeBounceJob by remember { mutableStateOf<Job?>(null) }
            var shown by remember { mutableStateOf(false) }
            var lastCollapseRequest by remember { mutableStateOf(collapseRequest) }
            var lastExpandRequest by remember { mutableStateOf(expandRequest) }
            // The stop the sheet rests at; it follows that stop when the keyboard moves it. Null while it moves.
            var restingAt by remember { mutableStateOf<SheetRest?>(null) }
            val maxEdgeBouncePx = min(with(density) { 24.dp.toPx() }, expandedAnchor * 0.45f)
            val inTopPanel = tabletopPanelBottomPx != null
            val topPanelHeightPx = (tabletopPanelBottomPx ?: 0f).coerceIn(1f, screenHeightPx)
            val bookPaneBounds = bookPosture?.paneBounds(bookPane, constraints.maxWidth.toFloat())
            val inBookPane = bookPaneBounds != null
            // The tabletop top panel and the book panes are fold panels: always fully open, sliding in from
            // their outer screen edge (the top, or the pane's side) over the length of [panelExtentPx].
            val inPanel = inTopPanel || inBookPane
            val panelExtentPx = if (bookPaneBounds != null) bookPaneBounds.widthPx.coerceAtLeast(1f) else topPanelHeightPx
            val foldPanels = LocalFoldPanels.current
            // A panel that replaces another one in the same place (detail -> editor) stays in place and only
            // fades its content in.
            val enterPanelInPlace = remember(panelSlot) { panelSlot != null && foldPanels.isOpen(panelSlot) }
            // How far the panel is pushed out towards its outer edge: -panelExtentPx is hidden, 0 is open.
            var panelOffsetPx by remember { mutableFloatStateOf(-panelExtentPx) }
            var panelShownIn by remember { mutableStateOf<FoldPanelSlot?>(null) }
            var panelClosing by remember { mutableStateOf(false) }
            val panelContentAlpha = remember { Animatable(1f) }
            DisposableEffect(panelSlot) {
                panelSlot?.let(foldPanels::opened)
                onDispose { panelSlot?.let(foldPanels::closed) }
            }

            suspend fun animatePanelTo(target: Float, easing: androidx.compose.animation.core.Easing = MotionEmphasized) {
                animate(
                    initialValue = panelOffsetPx,
                    targetValue = target,
                    animationSpec = tween(MotionMedium, easing = easing),
                ) { value, _ ->
                    panelOffsetPx = value
                }
            }

            fun closePanel(afterClose: () -> Unit = onDismissRequest) {
                if (panelClosing) return
                panelClosing = true
                scope.launch {
                    animatePanelTo(-panelExtentPx, MotionStandardAccelerate)
                    afterClose()
                }
            }

            /** [delta] towards open is positive: down for the top panel, towards the hinge for a pane. */
            fun dragPanelBy(delta: Float) {
                if (panelClosing) return
                panelOffsetPx = (panelOffsetPx + delta).coerceIn(-panelExtentPx, 0f)
            }

            fun settlePanel(velocity: Float) {
                if (panelClosing) return
                if (velocity < -850f || panelOffsetPx < -panelExtentPx * 0.3f) {
                    closePanel()
                } else {
                    scope.launch { animatePanelTo(0f) }
                }
            }

            fun clampedOffset(value: Float): Float = value.coerceIn(expandedAnchor, hiddenAnchor)

            fun dragSheetBy(deltaY: Float): Float {
                val previous = sheetOffsetPx
                restingAt = null
                edgeBouncePx = 0f
                edgeBounceJob?.cancel()
                sheetOffsetPx = clampedOffset(previous + deltaY)
                return sheetOffsetPx - previous
            }

            fun settleEdgeBounce(delayMillis: Long = 0L) {
                edgeBounceJob?.cancel()
                edgeBounceJob = scope.launch {
                    if (delayMillis > 0L) delay(delayMillis)
                    animate(
                        initialValue = edgeBouncePx,
                        targetValue = 0f,
                        animationSpec = tween(MotionShort, easing = MotionEmphasized),
                    ) { value, _ ->
                        edgeBouncePx = value
                    }
                }
            }

            fun bounceAtExpandedEdge(deltaY: Float): Float {
                if (sheetOffsetPx > expandedAnchor + 0.5f || deltaY >= 0f || maxEdgeBouncePx <= 0f) return 0f
                edgeBounceJob?.cancel()
                edgeBouncePx = (edgeBouncePx + deltaY * 0.22f).coerceIn(-maxEdgeBouncePx, 0f)
                settleEdgeBounce(delayMillis = 80L)
                return deltaY
            }

            fun anchorFor(snap: SheetSnap): Float = when (snap) {
                SheetSnap.Expanded -> expandedAnchor
                SheetSnap.Half -> halfAnchor
                SheetSnap.Quarter -> quarterAnchor
                SheetSnap.EditorSmall -> editorSmallAnchor
                SheetSnap.EditorTiny -> editorTinyAnchor
            }

            fun anchorOf(rest: SheetRest): Float = when (rest) {
                SheetRest.Expanded -> expandedAnchor
                SheetRest.Half -> halfAnchor
                SheetRest.Quarter -> quarterAnchor
                SheetRest.EditorSmall -> editorSmallAnchor
                SheetRest.EditorTiny -> editorTinyAnchor
                SheetRest.Content -> currentContentAnchor() ?: expandedAnchor
            }

            fun restFor(anchor: Float): SheetRest? = when {
                abs(anchor - expandedAnchor) < 2f -> SheetRest.Expanded
                anchorMode == SheetAnchorMode.ContentFit && contentAnchor != null && abs(anchor - contentAnchor) < 2f -> SheetRest.Content
                anchorMode == SheetAnchorMode.Editor && abs(anchor - editorSmallAnchor) < 2f -> SheetRest.EditorSmall
                anchorMode == SheetAnchorMode.Editor && abs(anchor - editorTinyAnchor) < 2f -> SheetRest.EditorTiny
                abs(anchor - halfAnchor) < 2f -> SheetRest.Half
                abs(anchor - quarterAnchor) < 2f -> SheetRest.Quarter
                else -> null
            }

            // The stops as they are now, for animations that outlast the composition that started them.
            val liveAnchorOf = rememberUpdatedState<(SheetRest) -> Float>(::anchorOf)

            fun sheetAnchors(): List<Float> =
                when (anchorMode) {
                    SheetAnchorMode.ContentFit -> listOfNotNull(expandedAnchor, contentAnchor, hiddenAnchor)
                    SheetAnchorMode.Editor -> listOf(expandedAnchor, editorSmallAnchor, editorTinyAnchor, hiddenAnchor)
                }
                    .distinctBy { (it / 4f).roundToInt() }
                    .sorted()

            fun snapForAnchor(anchor: Float): SheetSnap = when {
                abs(anchor - expandedAnchor) < 2f -> SheetSnap.Expanded
                abs(anchor - editorSmallAnchor) < 2f -> SheetSnap.EditorSmall
                abs(anchor - editorTinyAnchor) < 2f -> SheetSnap.EditorTiny
                abs(anchor - halfAnchor) < 2f -> SheetSnap.Half
                else -> SheetSnap.Quarter
            }

            fun targetAnchor(velocityY: Float = 0f): Float {
                val anchors = sheetAnchors()
                if (velocityY < -850f) {
                    return anchors.lastOrNull { it < sheetOffsetPx - 8f } ?: expandedAnchor
                }
                if (velocityY > 850f) {
                    return anchors.firstOrNull { it > sheetOffsetPx + 8f } ?: hiddenAnchor
                }
                return anchors.minBy { abs(it - sheetOffsetPx) }
            }

            suspend fun animateSheetTo(target: Float, dismissAfter: Boolean = false, toRest: SheetRest? = null) {
                val clampedTarget = clampedOffset(target)
                // Moving to a stop follows that stop, which the keyboard may move while the sheet moves.
                val rest = if (dismissAfter || clampedTarget == hiddenAnchor) null else toRest ?: restFor(clampedTarget)
                val start = sheetOffsetPx
                restingAt = null
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = tween(MotionMedium, easing = MotionEmphasized),
                ) { progress, _ ->
                    val end = rest?.let { liveAnchorOf.value(it) } ?: clampedTarget
                    sheetOffsetPx = clampedOffset(start + (end - start) * progress)
                }
                if (dismissAfter || clampedTarget == hiddenAnchor) {
                    onDismissRequest()
                } else {
                    restingAt = rest
                    onSnapChanged(snapForAnchor(rest?.let { liveAnchorOf.value(it) } ?: clampedTarget))
                }
            }

            fun settleSheet(velocityY: Float = 0f) {
                scope.launch { animateSheetTo(targetAnchor(velocityY)) }
            }

            fun closeSheet() {
                if (inPanel) {
                    closePanel()
                } else {
                    scope.launch { animateSheetTo(hiddenAnchor, dismissAfter = true) }
                }
            }

            BackHandler(enabled = true) {
                onBackRequest?.invoke() ?: closeSheet()
            }

            LaunchedEffect(screenHeightPx, initialSnap, initialContentHeight, panelSlot, panelExtentPx) {
                if (panelSlot != null) {
                    // Leaving the posture later brings the bottom sheet up again instead of showing it at once.
                    shown = false
                    if (panelShownIn != panelSlot) {
                        panelShownIn = panelSlot
                        if (enterPanelInPlace) {
                            panelOffsetPx = 0f
                            panelContentAlpha.snapTo(0f)
                            panelContentAlpha.animateTo(1f, tween(MotionMedium, easing = MotionStandard))
                        } else {
                            panelContentAlpha.snapTo(1f)
                            panelOffsetPx = -panelExtentPx
                            animatePanelTo(0f)
                        }
                    } else {
                        panelOffsetPx = panelOffsetPx.coerceIn(-panelExtentPx, 0f)
                    }
                    return@LaunchedEffect
                }
                panelShownIn = null
                if (!shown) {
                    sheetOffsetPx = hiddenAnchor
                    shown = true
                    // Content that measures itself has done so after its first frame.
                    withFrameNanos { }
                    animateSheetTo(currentContentAnchor() ?: anchorFor(initialSnap))
                } else {
                    sheetOffsetPx = clampedOffset(sheetOffsetPx)
                }
            }

            LaunchedEffect(collapseRequest) {
                val shouldCollapse = collapseRequest != lastCollapseRequest
                lastCollapseRequest = collapseRequest
                if (shown && shouldCollapse && !inPanel) {
                    animateSheetTo(anchorFor(collapseSnap))
                }
            }

            LaunchedEffect(expandRequest) {
                val shouldExpand = expandRequest != lastExpandRequest
                lastExpandRequest = expandRequest
                if (shown && shouldExpand && !inPanel) {
                    animateSheetTo(expandedAnchor)
                }
            }

            // The lowest editor stop shows only the top bar; typing needs the title in view, so the
            // keyboard opening there moves the sheet up one stop, where it stays.
            val keyboardOpen = imeBottomPx > navigationBarInsetPx + 1f
            val restingLowest = restingAt == SheetRest.EditorTiny
            LaunchedEffect(keyboardOpen, restingLowest) {
                if (keyboardOpen && restingLowest) {
                    animateSheetTo(liveAnchorOf.value(SheetRest.EditorSmall), toRest = SheetRest.EditorSmall)
                }
            }

            // A resting sheet follows its stop when the keyboard (or measured content) moves it.
            val restingTarget = restingAt?.let(::anchorOf)
            LaunchedEffect(restingTarget) {
                if (restingTarget != null && !inPanel && abs(restingTarget - sheetOffsetPx) > 0.5f) {
                    sheetOffsetPx = clampedOffset(restingTarget)
                }
            }

            // Only a finger moves the sheet. Scrolls without one, such as a focused field scrolled into
            // view when the keyboard opens, move the content and leave the sheet at its stop.
            val fingerOnSheet = remember { FingerTracker() }
            val sheetNestedScrollConnection = remember(screenHeightPx) {
                object : NestedScrollConnection {
                    // Swiping up first opens the sheet fully; only then does its content scroll.
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        // A focused field brought into view (no finger involved) raises a sheet that is not fully
                        // open by one stop instead of scrolling the content, so the sheet always rests at a stop.
                        if (!fingerOnSheet.down && available.y < 0f && sheetOffsetPx > expandedAnchor + 0.5f) {
                            val current = restingAt
                            if (current != null) {
                                val next = if (current == SheetRest.EditorTiny) SheetRest.EditorSmall else SheetRest.Expanded
                                scope.launch { animateSheetTo(liveAnchorOf.value(next), toRest = next) }
                            }
                            return available
                        }
                        if (
                            source == NestedScrollSource.UserInput &&
                            fingerOnSheet.down &&
                            available.y < 0f &&
                            sheetOffsetPx > expandedAnchor + 0.5f
                        ) {
                            return Offset(x = 0f, y = dragSheetBy(available.y))
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (source != NestedScrollSource.UserInput || !fingerOnSheet.down) return Offset.Zero
                        val deltaY = available.y
                        return when {
                            deltaY > 0f && sheetOffsetPx < hiddenAnchor -> Offset(x = 0f, y = dragSheetBy(deltaY))
                            deltaY < 0f && sheetOffsetPx > expandedAnchor -> Offset(x = 0f, y = dragSheetBy(deltaY))
                            deltaY < 0f && sheetOffsetPx <= expandedAnchor + 0.5f -> Offset(x = 0f, y = bounceAtExpandedEdge(deltaY))
                            else -> Offset.Zero
                        }
                    }

                    // A fling up on a sheet that is not fully open opens it instead of flinging the content.
                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (available.y < 0f && sheetOffsetPx > expandedAnchor + 0.5f) {
                            animateSheetTo(targetAnchor(available.y))
                            return available
                        }
                        return Velocity.Zero
                    }

                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        if (abs(consumed.y) > 50f) {
                            return Velocity.Zero
                        }
                        if (available.y < 0f && sheetOffsetPx <= expandedAnchor + 0.5f && maxEdgeBouncePx > 0f) {
                            edgeBounceJob?.cancel()
                            edgeBouncePx = (-min(maxEdgeBouncePx, max(8f, abs(available.y) * 0.012f))).coerceIn(-maxEdgeBouncePx, 0f)
                            settleEdgeBounce()
                            return available
                        }
                        return if (available.y != 0f || sheetAnchors().none { abs(it - sheetOffsetPx) < 1f }) {
                            val target = if (sheetOffsetPx <= expandedAnchor + 0.5f && available.y < 0f) {
                                expandedAnchor
                            } else {
                                targetAnchor(available.y)
                            }
                            animateSheetTo(target)
                            available
                        } else {
                            Velocity.Zero
                        }
                    }
                }
            }

            val openProgress = ((hiddenAnchor - sheetOffsetPx) / (hiddenAnchor - quarterAnchor)).coerceIn(0f, 1f)
            // A resting sheet takes its stop's position in the same frame, so it moves exactly with the
            // keyboard; a frame late, a focused field would count as covered and scroll the sheet away.
            val shownOffsetPx = restingTarget?.takeIf { !inPanel }?.let(::clampedOffset) ?: sheetOffsetPx
            val effectiveSheetOffsetPx = (shownOffsetPx + edgeBouncePx).coerceIn(0f, hiddenAnchor)
            val sheetHeight = with(density) { (sheetBottomPx - effectiveSheetOffsetPx).coerceAtLeast(1f).toDp() }
            val cornerRadius = 28.dp

            val panelProgress = (1f + panelOffsetPx / panelExtentPx).coerceIn(0f, 1f)
            val topPanelHeight = with(density) { topPanelHeightPx.toDp() }
            val bookPaneLeftPx = bookPaneBounds?.leftPx ?: 0f
            val bookPaneWidth = with(density) { (bookPaneBounds?.widthPx ?: 0f).toDp() }
            if (dimBackground || dismissOnOutsideTap) {
                Box(
                    modifier = Modifier
                        // A fold panel dims only its own half; the other half of the screen stays usable.
                        .then(
                            when {
                                inTopPanel -> Modifier.fillMaxWidth().height(topPanelHeight)
                                inBookPane -> Modifier
                                    .offset { IntOffset(x = bookPaneLeftPx.roundToInt(), y = 0) }
                                    .width(bookPaneWidth)
                                    .fillMaxHeight()
                                else -> Modifier.fillMaxSize()
                            },
                        )
                        .alpha(if (dimBackground) (if (inPanel) panelProgress else openProgress) else 1f)
                        .then(if (dimBackground) Modifier.background(Color.Black.copy(alpha = 0.38f)) else Modifier)
                        .then(
                            if (dismissOnOutsideTap) {
                                Modifier.clickable(
                                    interactionSource = quietInteraction,
                                    indication = null,
                                    onClick = ::closeSheet,
                                )
                            } else {
                                Modifier
                            },
                        ),
                )
            }

            val sheetDragModifier = Modifier.draggable(
                state = rememberDraggableState { delta -> dragSheetBy(delta) },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> settleSheet(velocity) },
            )
            val sheetHeaderModifier = Modifier
                .pointerInput(screenHeightPx) {
                    detectTapGestures(
                        onTap = {
                            scope.launch { animateSheetTo(expandedAnchor) }
                        },
                    )
                }
                .then(sheetDragModifier)
            // The top panel is always fully open; dragging its header or handle up puts it away.
            val topPanelDragModifier = Modifier.draggable(
                state = rememberDraggableState { delta -> dragPanelBy(delta) },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> settlePanel(velocity) },
            )
            // A book pane puts its header away sideways, towards the screen edge it came from, like a drawer.
            val bookPaneDragModifier = Modifier.draggable(
                state = rememberDraggableState { delta -> dragPanelBy(-bookPaneHideDeltaPx(bookPane, delta)) },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity -> settlePanel(-bookPaneHideDeltaPx(bookPane, velocity)) },
            )
            val closeAnimator: (() -> Unit) -> Unit = { action ->
                if (inPanel) closePanel(afterClose = action) else action()
            }
            val expandSheet: () -> Unit = {
                if (!inPanel && restingAt != SheetRest.Expanded) {
                    scope.launch { animateSheetTo(liveAnchorOf.value(SheetRest.Expanded), toRest = SheetRest.Expanded) }
                }
            }

            // On tablets and unfolded foldables the sheet stays at most SheetMaxWidth wide, centred.
            // Phones, rotated ones included, keep the full window width.
            val sheetMaxWidth = largeScreenMaxWidth(SheetMaxWidth)
            val separationShadowDark = MaterialTheme.colorScheme.background.isDark()
            if (separationShadow && !inPanel) {
                val shadowHeight = if (separationShadowDark) 42.dp else 34.dp
                val shadowHeightPx = with(density) { shadowHeight.toPx() }
                val cornerRadiusPx = with(density) { cornerRadius.toPx() }
                val topAlpha = if (separationShadowDark) 0.2f else 0.075f
                val bottomAlpha = if (separationShadowDark) 0.36f else 0.15f
                Canvas(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = sheetMaxWidth)
                        .fillMaxWidth()
                        .height(shadowHeight + cornerRadius)
                        .offset {
                            IntOffset(
                                x = 0,
                                y = (effectiveSheetOffsetPx - shadowHeightPx).roundToInt(),
                            )
                        },
                ) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.42f to Color.Black.copy(alpha = topAlpha),
                            1f to Color.Black.copy(alpha = bottomAlpha),
                        ),
                        topLeft = Offset.Zero,
                        size = Size(width = size.width, height = size.height + cornerRadiusPx),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    )
                }
            }

            // Fold panels round only the corners next to the hinge.
            val panelShape = when {
                inTopPanel -> RoundedCornerShape(bottomStart = cornerRadius, bottomEnd = cornerRadius)
                bookPane == BookPane.Left -> RoundedCornerShape(topEnd = cornerRadius, bottomEnd = cornerRadius)
                else -> RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius)
            }
            Surface(
                modifier = Modifier
                    .then(
                        if (inBookPane) {
                            Modifier
                                .align(Alignment.TopStart)
                                .offset {
                                    IntOffset(
                                        x = (bookPaneLeftPx + bookPaneSlideOffsetPx(bookPane, -panelOffsetPx)).roundToInt(),
                                        y = 0,
                                    )
                                }
                                .width(bookPaneWidth)
                        } else {
                            Modifier
                                .align(Alignment.TopCenter)
                                .widthIn(max = sheetMaxWidth)
                                .fillMaxWidth()
                        },
                    )
                    .testTag(KgsModalBottomSheetSurfaceTag)
                    .then(
                        if (inPanel) {
                            Modifier
                                .then(
                                    if (inTopPanel) {
                                        Modifier
                                            .height(topPanelHeight)
                                            .offset { IntOffset(x = 0, y = panelOffsetPx.roundToInt()) }
                                    } else {
                                        Modifier.fillMaxHeight()
                                    },
                                )
                                .shadow(
                                    elevation = if (separationShadowDark) 30.dp else 18.dp,
                                    shape = panelShape,
                                    clip = false,
                                    ambientColor = Color.Black.copy(alpha = if (separationShadowDark) 0.24f else 0.13f),
                                    spotColor = Color.Black.copy(alpha = if (separationShadowDark) 0.28f else 0.17f),
                                )
                        } else {
                            Modifier
                                .height(sheetHeight)
                                .offset { IntOffset(x = 0, y = effectiveSheetOffsetPx.roundToInt()) }
                        },
                    )
                    .then(
                        if (separationShadow && !inPanel) {
                            Modifier.shadow(
                                elevation = if (separationShadowDark) 30.dp else 18.dp,
                                shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = if (separationShadowDark) 0.24f else 0.13f),
                                spotColor = Color.Black.copy(alpha = if (separationShadowDark) 0.28f else 0.17f),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (inPanel) {
                            Modifier
                        } else {
                            Modifier
                                .pointerInput(fingerOnSheet) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            fingerOnSheet.down = event.changes.any { it.pressed }
                                        }
                                    }
                                }
                                .nestedScroll(sheetNestedScrollConnection)
                        },
                    ),
                shape = if (inPanel) {
                    panelShape
                } else {
                    RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                },
                color = containerColor,
                shadowElevation = 0.dp,
            ) {
                CompositionLocalProvider(LocalOverscrollFactory provides null) {
                    CompositionLocalProvider(
                        LocalSheetContentHeights provides contentParts,
                        LocalSheetHeaderDragModifier provides when {
                            inTopPanel -> topPanelDragModifier
                            inBookPane -> bookPaneDragModifier
                            else -> sheetHeaderModifier
                        },
                        LocalSheetCloseAnimator provides closeAnimator,
                        LocalSheetExpander provides expandSheet,
                    ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (inTopPanel) {
                                    Modifier
                                        .windowInsetsPadding(WindowInsets.statusBars)
                                        .tabletopPanelImePadding(panelBottomPx = topPanelHeightPx, windowHeightPx = screenHeightPx)
                                } else if (inBookPane) {
                                    // The keyboard spans both panes, so a pane pads for it exactly like a bottom sheet.
                                    Modifier
                                        .windowInsetsPadding(WindowInsets.statusBars)
                                        .imePadding()
                                } else if (sheetInsetPolicy.respectsImeBottomInset) {
                                    Modifier.imePadding()
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        if (!inPanel) {
                            KgsSheetHandle(
                                modifier = sheetHeaderModifier,
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .then(
                                    if (inPanel) {
                                        Modifier.graphicsLayer { alpha = panelContentAlpha.value }
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            content()
                        }
                        if (inTopPanel) {
                            // The handle sits at the panel's lower edge, next to the hinge.
                            KgsSheetHandle(
                                modifier = topPanelDragModifier.testTag(TabletopTopPanelHandleTag),
                            )
                        }
                    }
                    }
                }
                if (inPanel && panelClosing) {
                    // The content stays visible while the panel slides away; it must not take a second save.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(interactionSource = quietInteraction, indication = null, onClick = {}),
                    )
                }
            }
        }
}

internal const val KgsModalBottomSheetSurfaceTag = "kgs-modal-bottom-sheet"

private val SheetHandleHeight = 18.dp

/** Whether a finger touches the sheet; read by its scroll handling, so it is not state. */
private class FingerTracker {
    var down: Boolean = false
}

/** The stops a sheet can rest at; see [KgsModalBottomSheet]. */
private enum class SheetRest { Expanded, Half, Quarter, EditorSmall, EditorTiny, Content }

/** Where parts of a sheet's content report their natural heights, by part. */
private val LocalSheetContentHeights = staticCompositionLocalOf<MutableMap<String, Int>?> { null }

/**
 * Reports this part's natural height to its sheet, which then opens just far enough to show all
 * parts, or fully when they do not fit. Use it on fixed parts such as a top bar, and on the inner
 * content of a scrolling part, where the height is not limited.
 */
internal fun Modifier.sheetContentPart(part: String): Modifier = composed {
    val heights = LocalSheetContentHeights.current
    if (heights == null) {
        this
    } else {
        DisposableEffect(heights, part) { onDispose { heights.remove(part) } }
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            if (heights[part] != placeable.height) heights[part] = placeable.height
            layout(placeable.width, placeable.height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
                placeable.place(0, 0)
            }
        }
    }
}
internal const val TabletopTopPanelHandleTag = "tabletop-top-panel-handle"

/**
 * Runs a close action from inside a sheet (the editor's close button, a save, a delete). A fold panel (the
 * tabletop top panel or a book pane) first slides away and then runs it; a regular bottom sheet runs it at
 * once, exactly as before.
 */
internal val LocalSheetCloseAnimator = compositionLocalOf<(() -> Unit) -> Unit> { { action -> action() } }

/** Opens the sheet fully, for content that needs the whole height (a page with a long list). */
internal val LocalSheetExpander = compositionLocalOf<() -> Unit> { {} }

/** Counts the open fold panels per place, so a panel that replaces another one does not slide in again. */
internal class FoldPanels {
    private val openCounts = mutableMapOf<FoldPanelSlot, Int>()

    fun isOpen(slot: FoldPanelSlot): Boolean = (openCounts[slot] ?: 0) > 0

    fun opened(slot: FoldPanelSlot) {
        openCounts[slot] = (openCounts[slot] ?: 0) + 1
    }

    fun closed(slot: FoldPanelSlot) {
        openCounts[slot] = ((openCounts[slot] ?: 0) - 1).coerceAtLeast(0)
    }
}

internal val LocalFoldPanels = staticCompositionLocalOf { FoldPanels() }

/**
 * The keyboard opens in the bottom half, below the hinge, so the top panel ignores it; only the part of a
 * keyboard taller than the bottom half pads the panel's content.
 */
private fun Modifier.tabletopPanelImePadding(panelBottomPx: Float, windowHeightPx: Float): Modifier = composed {
    val density = LocalDensity.current
    val imeHeightPx = WindowInsets.ime.getBottom(density).toFloat()
    val overlapPx = tabletopPanelImeOverlapPx(
        imeHeightPx = imeHeightPx,
        windowHeightPx = windowHeightPx,
        panelBottomPx = panelBottomPx,
    )
    padding(bottom = with(density) { overlapPx.toDp() })
}

@Composable
internal fun KgsSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SheetHandleHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(WarmLine),
        )
    }
}
