@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/*
 * Drop-down menus that grow out of their button: the button's surface stretches into the menu's
 * background and back, and the button's own icons, dots and label travel to the same items in
 * the menu (see [morphElement]). The open menu draws in a popup over the whole window, so it
 * works in pages, sheets and popups alike; while it is shown, the popup draws the button too.
 */

private class MorphMenuScopes(val shared: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

private val LocalMorphMenuScopes = compositionLocalOf<MorphMenuScopes?> { null }

/**
 * An element of a drop-down's button that is also in its menu under the same [key], such as the
 * chosen option's icon and label: while the menu opens or closes, it moves from one place to the
 * other. It does nothing outside an opening or closing menu.
 */
@Composable
internal fun Modifier.morphElement(key: Any): Modifier {
    val scopes = LocalMorphMenuScopes.current ?: return this
    return with(scopes.shared) {
        this@morphElement
            .sharedElement(rememberSharedContentState(key), scopes.visibility, boundsTransform = MorphMenuBoundsTransform)
            .skipToLookaheadSize()
    }
}

/**
 * A button that opens a menu growing out of it. The button is [button] on [buttonColor] in
 * [buttonShape]; a tap calls [onClick] (null when a larger row around it handles the tap). The
 * menu ([menu], usually [MorphMenuRow]s) starts at the button's top corner, on the button's
 * start side or, with [alignEnd], its end side, and opens upwards when there is no room below.
 */
@Composable
internal fun MorphingDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    buttonShape: Shape,
    buttonColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    role: Role = Role.DropdownList,
    alignEnd: Boolean = false,
    menuMinWidth: Dp = 200.dp,
    menuMaxHeight: Dp = 460.dp,
    menuColor: Color = popupSurfaceColor(),
    button: @Composable () -> Unit,
    menu: @Composable ColumnScope.() -> Unit,
) {
    var buttonBounds by remember { mutableStateOf<Rect?>(null) }
    var popupShown by remember { mutableStateOf(false) }
    // The popup draws the button while it is shown; this one comes back once the menu has closed.
    var popupDrawsButton by remember { mutableStateOf(false) }
    val transitionState = remember { MutableTransitionState(false) }
    val currentExpanded by rememberUpdatedState(expanded)
    LaunchedEffect(expanded) {
        when {
            !expanded -> transitionState.targetState = false
            // Opened again while closing: the popup is still there.
            popupDrawsButton -> transitionState.targetState = true
            else -> popupShown = true
        }
    }
    if (popupShown && !expanded && transitionState.isIdle && !transitionState.currentState) {
        LaunchedEffect(Unit) {
            popupShown = false
            popupDrawsButton = false
        }
    }
    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val topLeft = coordinates.positionOnScreen()
                buttonBounds = Rect(topLeft, Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
            }
            .graphicsLayer { alpha = if (popupDrawsButton) 0f else 1f }
            .clip(buttonShape)
            .background(buttonColor)
            .then(
                if (onClick != null) Modifier.clickable(role = role, onClickLabel = onClickLabel, onClick = onClick) else Modifier,
            ),
    ) {
        button()
    }
    val bounds = buttonBounds
    if (popupShown && bounds != null) {
        Popup(
            popupPositionProvider = WindowOriginPositionProvider,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true),
        ) {
            MorphMenuPopupContent(
                transitionState = transitionState,
                buttonBounds = bounds,
                buttonShape = buttonShape,
                buttonColor = buttonColor,
                alignEnd = alignEnd,
                menuMinWidth = menuMinWidth,
                menuMaxHeight = menuMaxHeight,
                menuColor = menuColor,
                onReady = {
                    popupDrawsButton = true
                    transitionState.targetState = currentExpanded
                },
                onDismissRequest = onDismissRequest,
                button = button,
                menu = menu,
            )
        }
    }
}

@Composable
private fun MorphMenuPopupContent(
    transitionState: MutableTransitionState<Boolean>,
    buttonBounds: Rect,
    buttonShape: Shape,
    buttonColor: Color,
    alignEnd: Boolean,
    menuMinWidth: Dp,
    menuMaxHeight: Dp,
    menuColor: Color,
    onReady: () -> Unit,
    onDismissRequest: () -> Unit,
    button: @Composable () -> Unit,
    menu: @Composable ColumnScope.() -> Unit,
) {
    var origin by remember { mutableStateOf<Offset?>(null) }
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionOnScreen() }
            .pointerInput(Unit) { detectTapGestures { currentOnDismiss() } },
    ) {
        val popupOrigin = origin ?: return@Box
        LaunchedEffect(Unit) { onReady() }
        // The button in this popup's coordinates.
        val local = buttonBounds.translate(-popupOrigin)
        val marginPx = with(density) { MorphMenuMargin.roundToPx() }
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            rememberTransition(transitionState).AnimatedContent(
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                modifier = Modifier.fillMaxSize(),
            ) { open ->
                CompositionLocalProvider(LocalMorphMenuScopes provides MorphMenuScopes(this@SharedTransitionLayout, this)) {
                    if (!open) {
                        // A full-size box loosens the popup's constraints, so the copy keeps the button's size.
                        Box(Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(local.left.roundToInt(), local.top.roundToInt()) }
                                    .sharedBounds(
                                        rememberSharedContentState(MorphMenuContainerKey),
                                        this@AnimatedContent,
                                        enter = fadeIn(tween(MotionShort, easing = MotionStandard)),
                                        exit = fadeOut(tween(MotionShort, easing = MotionStandard)),
                                        boundsTransform = MorphMenuBoundsTransform,
                                        resizeMode = MorphMenuResize,
                                    )
                                    .size(with(density) { local.width.toDp() }, with(density) { local.height.toDp() })
                                    .clip(buttonShape)
                                    .background(buttonColor),
                            ) {
                                Box(Modifier.skipToLookaheadSize()) { button() }
                            }
                        }
                    } else {
                        val minWidth = maxOf(menuMinWidth, with(density) { local.width.toDp() })
                        Layout(
                            content = {
                                Column(
                                    // The size limits come after the shared bounds, so the menu's
                                    // surface can be as small as the button while it grows.
                                    modifier = Modifier
                                        .sharedBounds(
                                            rememberSharedContentState(MorphMenuContainerKey),
                                            this@AnimatedContent,
                                            enter = fadeIn(tween(MotionShort, easing = MotionStandard)),
                                            exit = fadeOut(tween(MotionShort, delayMillis = MotionShort, easing = MotionStandard)),
                                            boundsTransform = MorphMenuBoundsTransform,
                                            resizeMode = MorphMenuResize,
                                        )
                                        .widthIn(min = minWidth)
                                        .heightIn(max = menuMaxHeight)
                                        // Taps between the rows stay in the menu.
                                        .pointerInput(Unit) { detectTapGestures { } }
                                        .shadow(MorphMenuElevation, MorphMenuShape)
                                        .clip(MorphMenuShape)
                                        .background(menuColor)
                                        .skipToLookaheadSize()
                                        // As wide as the longest row, not as the window.
                                        .width(IntrinsicSize.Max)
                                        .verticalScroll(rememberScrollState())
                                        .padding(vertical = 8.dp),
                                    content = menu,
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) { measurables, constraints ->
                            val maxWidth = (constraints.maxWidth - 2 * marginPx).coerceAtLeast(0)
                            val maxHeight = (constraints.maxHeight - 2 * marginPx).coerceAtLeast(0)
                            val placeable = measurables.single().measure(
                                constraints.copy(minWidth = 0, minHeight = 0, maxWidth = maxWidth, maxHeight = maxHeight),
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                val start = if (alignEnd) local.right.roundToInt() - placeable.width else local.left.roundToInt()
                                val x = start.coerceIn(marginPx, (constraints.maxWidth - marginPx - placeable.width).coerceAtLeast(marginPx))
                                val fitsBelow = local.top + placeable.height <= constraints.maxHeight - marginPx
                                val y = if (fitsBelow) {
                                    local.top.roundToInt()
                                } else {
                                    (local.bottom.roundToInt() - placeable.height).coerceAtLeast(marginPx)
                                }
                                placeable.place(x, y)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A row of a drop-down menu. The chosen rows sit on a soft accent square and their icon takes the
 * accent, instead of a check mark. With a [morphKey], its icon and label are the ones the button
 * shows for it (see [morphLeadingKey] and [morphLabelKey]).
 */
@Composable
internal fun MorphMenuRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    morphKey: Any? = null,
    labelColor: Color = WarmInk,
    enabled: Boolean = true,
    role: Role = Role.RadioButton,
    leading: (@Composable (selected: Boolean) -> Unit)? = null,
) {
    val container by animateColorAsState(
        targetValue = if (selected) WarmBrown.copy(alpha = 0.13f) else Color.Transparent,
        animationSpec = tween(MotionShort, easing = MotionStandard),
        label = "morphMenuRow",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(MorphMenuRowShape)
            .background(container)
            .clickable(enabled = enabled, role = role, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (leading != null) {
            Box(if (morphKey != null) Modifier.morphElement(morphLeadingKey(morphKey)) else Modifier) {
                leading(selected)
            }
        }
        Text(
            label,
            color = labelColor,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (morphKey != null) Modifier.morphElement(morphLabelKey(morphKey)) else Modifier,
        )
    }
}

/** The key under which a button's icon travels to (or from) its menu row with [morphKey]. */
internal fun morphLeadingKey(morphKey: Any): Any = MorphElementKey("leading", morphKey)

/** The key under which a button's label travels to (or from) its menu row with [morphKey]. */
internal fun morphLabelKey(morphKey: Any): Any = MorphElementKey("label", morphKey)

private data class MorphElementKey(val part: String, val key: Any)

private object MorphMenuContainerKey

/** The open menu fills the window; its content places itself at the button. */
private object WindowOriginPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

// The surface takes each in-between size; what is on it keeps its own size (see skipToLookaheadSize).
private val MorphMenuResize = SharedTransitionScope.ResizeMode.RemeasureToBounds
private val MorphMenuBoundsTransform = BoundsTransform { _, _ -> tween(MotionMedium, easing = MotionEmphasized) }
private val MorphMenuShape = RoundedCornerShape(22.dp)
private val MorphMenuRowShape = RoundedCornerShape(14.dp)
private val MorphMenuElevation = 10.dp
private val MorphMenuMargin = 16.dp
