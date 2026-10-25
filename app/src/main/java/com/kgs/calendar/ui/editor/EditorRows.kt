@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import com.kgs.calendar.ui.components.ClearTextButton
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.CollectionEntity

@Composable
internal fun EditorTopBar(
    title: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saveEnabled: Boolean = true,
) {
    val typeSlide = LocalEditorTypeSlide.current
    val inPlace = typeSlide?.run { Modifier.staysInPlace() } ?: Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(LocalSheetHeaderDragModifier.current)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RoundEditorAction(
            icon = Icons.Default.Close,
            contentDescription = stringResource(R.string.close),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
            contentColor = WarmInk,
            onClick = onClose,
            modifier = inPlace,
        )
        Text(
            title,
            color = WarmInk,
            fontSize = 15.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .then(typeSlide?.run { Modifier.crossfades() } ?: Modifier),
            textAlign = TextAlign.Center,
        )
        RoundEditorAction(
            icon = Icons.Default.Check,
            contentDescription = stringResource(R.string.save),
            containerColor = if (saveEnabled) WarmBrown else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
            contentColor = if (saveEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
            enabled = saveEnabled,
            onClick = onSave,
            modifier = inPlace,
        )
    }
}

@Composable
internal fun RoundEditorAction(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(20.dp))
    }
}

/** True for an editor that slides in after a switch; its type switch then animates the pill over. */
internal val LocalEditorTypeSwitchAnimates = compositionLocalOf { false }

/**
 * Whether the keyboard was open when the user switched to this editor from the other type; null
 * when the editor opened by itself. A switch keeps the keyboard as it was: the "show keyboard"
 * setting only applies when the editor opens.
 */
internal val LocalEditorKeyboardAtSwitch = compositionLocalOf<Boolean?> { null }

/**
 * Switching between the event and the task editor slides only the fields below the type switch:
 * the event editor's in from the start side, the task editor's from the end side, like the halves
 * of the switch. The top bar and the switch stay in place; the editor sliding out hides its own,
 * so the ones of the editor sliding in, drawn at the same spot, look like a single sheet.
 */
internal class EditorTypeSlide(
    private val scope: AnimatedVisibilityScope,
    private val isEvent: Boolean,
) {
    private val leaving: Boolean
        get() = scope.transition.targetState == EnterExitState.PostExit

    /** The top bar's buttons and the type switch: shown only by the editor sliding in. */
    fun Modifier.staysInPlace(): Modifier = alpha(if (leaving) 0f else 1f)

    /** The title, which names the editor type: the old one fades out, then the new one in. */
    fun Modifier.crossfades(): Modifier = with(scope) {
        animateEnterExit(
            enter = fadeIn(tween(MotionShort, delayMillis = MotionShort, easing = MotionStandard)),
            exit = fadeOut(tween(MotionShort, easing = MotionStandardAccelerate)),
        )
    }

    /** The fields below the type switch. */
    fun Modifier.slides(): Modifier = with(scope) {
        val side = if (isEvent) -1 else 1
        animateEnterExit(
            enter = slideInHorizontally(tween(MotionLong, easing = MotionEmphasized)) { it * side } +
                fadeIn(tween(MotionMedium, easing = MotionStandard)),
            exit = slideOutHorizontally(tween(MotionLong, easing = MotionEmphasized)) { it * side } +
                fadeOut(tween(MotionMedium, easing = MotionStandardAccelerate)),
        )
    }
}

/** Set while an editor can switch between event and task; see [EditorTypeSlide]. */
internal val LocalEditorTypeSlide = compositionLocalOf<EditorTypeSlide?> { null }

/**
 * Event or task: a segmented switch whose selected half is a raised pill, without outlines.
 * The pill slides to the chosen half, and the labels' colors follow it.
 */
@Composable
internal fun EditorTypeSwitch(
    eventSelected: Boolean,
    onSelectEvent: () -> Unit,
    onSelectTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = if (eventSelected) 0f else 1f
    val animatesIn = LocalEditorTypeSwitchAnimates.current
    val position = remember { Animatable(if (animatesIn) 1f - target else target) }
    LaunchedEffect(target) {
        position.animateTo(target, tween(MotionLong, easing = MotionEmphasized))
    }
    val pillColor = selectedSegmentColor()
    Box(
        modifier = modifier
            .editorInset()
            .clip(RoundedCornerShape(22.dp))
            .background(editorSectionColor())
            .padding(4.dp)
            .width(IntrinsicSize.Max),
    ) {
        // Sized in the same pass as the switch, so the pill shows from the first frame.
        Box(
            Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    // The switch's intrinsic width asks with unbounded constraints; the pill adds nothing.
                    if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) return@layout layout(0, 0) {}
                    val half = constraints.maxWidth / 2
                    val pill = measurable.measure(Constraints.fixed(half, constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        pill.placeRelative((position.value * half).roundToInt(), 0)
                    }
                }
                .clip(RoundedCornerShape(18.dp))
                .background(pillColor),
        )
        Row {
            val taskShare = position.value.coerceIn(0f, 1f)
            EditorTypeSegment(stringResource(R.string.event), selectedShare = 1f - taskShare, enabled = !eventSelected, modifier = Modifier.weight(1f), onClick = onSelectEvent)
            EditorTypeSegment(stringResource(R.string.task), selectedShare = taskShare, enabled = eventSelected, modifier = Modifier.weight(1f), onClick = onSelectTask)
        }
    }
}

/** One half of [EditorTypeSwitch]; [selectedShare] is how much of the pill lies under it. */
@Composable
private fun EditorTypeSegment(text: String, selectedShare: Float, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val selected = selectedShare >= 0.5f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            softWrap = false,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = lerp(MaterialTheme.colorScheme.onSurfaceVariant, WarmInk, selectedShare),
        )
    }
}

@Composable
internal fun FadedHorizontalScrollRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    viewportBleed: Dp = 0.dp,
    initialScrollIndex: Int? = null,
    initialScrollKey: Any? = initialScrollIndex,
    content: @Composable RowScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(initialScrollKey, initialScrollIndex) {
        val index = initialScrollIndex?.takeIf { it > 0 } ?: return@LaunchedEffect
        repeat(4) { withFrameNanos { } }
        val target = with(density) { (132 * index).dp.roundToPx() }
            .coerceIn(0, scrollState.maxValue)
        scrollState.scrollTo(target)
    }
    val showStartFade = scrollState.value > 0
    val showEndFade = scrollState.maxValue > scrollState.value
    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalBleed(viewportBleed)
            .horizontalEdgeFade(edgeWidth = 22.dp, fadeStart = showStartFade, fadeEnd = showEndFade),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(contentPadding),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** True inside an [EditorGroup], whose card already gives its rows their inset and rounding. */
internal val LocalEditorGrouped = compositionLocalOf { false }

/** The fill of editor cards: the page tint, so cards stand out from the sheet without outlines. */
@Composable
internal fun editorSectionColor(): Color =
    // In dark mode, raised cards are a touch lighter than the sheet, as elevation reads there.
    if (MaterialTheme.colorScheme.background.isDark()) {
        MaterialTheme.colorScheme.surface.blendWith(Color.White, 0.07f)
    } else {
        MaterialTheme.colorScheme.background
    }

private val EditorCardShape = RoundedCornerShape(24.dp)

/**
 * A filled card of editor content. Inside an [EditorGroup] it is one row of the group's card;
 * on its own it is a rounded card of its own. [onClick] makes the whole card tappable.
 */
@Composable
internal fun EditorSection(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val grouped = LocalEditorGrouped.current
    Column(
        modifier = modifier
            .then(if (grouped) Modifier else Modifier.editorInset().clip(EditorCardShape))
            .fillMaxWidth()
            .background(editorSectionColor())
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = EditorSectionHorizontalPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Several [EditorSection]s as one card, separated by thin gaps instead of lines. */
@Composable
internal fun EditorGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CardGroup(modifier.editorInset(), content)
}

/** Like [EditorGroup], for places that already have their own side padding. */
@Composable
internal fun CardGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(EditorCardShape),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CompositionLocalProvider(LocalEditorGrouped provides true) {
            content()
        }
    }
}

/** The heading of a card row: its icon in the icon column, then the title. */
@Composable
internal fun EditorRowTitle(icon: ImageVector, title: String, trailing: (@Composable () -> Unit)? = null) {
    EditorLine(icon) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = WarmInk, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}

/** A small accent heading above a part of the editor. */
@Composable
internal fun EditorLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = WarmBrown,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.editorInset().padding(top = 6.dp),
    )
}

/** The title as large, borderless text with a placeholder. */
@Composable
internal fun EditorTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    EditorPlainTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = stringResource(R.string.add_title),
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(
            color = WarmInk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
        ),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.padding(vertical = 4.dp),
    )
}

/**
 * A borderless text field with a placeholder instead of a label, for the title, notes and links.
 * Like the app's other fields it offers a clear button while it holds text.
 */
@Composable
internal fun EditorPlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(color = WarmInk, fontSize = 16.sp, lineHeight = 22.sp),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        singleLine = singleLine,
        minLines = minLines,
        cursorBrush = SolidColor(WarmBrown),
        keyboardOptions = keyboardOptions,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top) {
                Box(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    ClearTextButton(
                        onClick = {
                            onValueChange("")
                            focusManager.clearFocus(force = true)
                            keyboard?.hide()
                        },
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        },
    )
}

@Composable
internal fun CalendarSelectorChip(
    collection: CollectionEntity,
    selected: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
) {
    // Selected: the calendar's color as a tint behind a check. A hidden calendar keeps the
    // dashed outline it has everywhere else in the app.
    val shape = RoundedCornerShape(19.dp)
    val color = Color(collection.color)
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(shape)
            .background(if (selected) WarmPeach else editorSectionColor())
            .then(if (hidden) Modifier.dashedBorder(SyncPendingOrange, 19.dp) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = if (color.luminance() > 0.62f) Color.Black else Color.White, modifier = Modifier.size(13.dp))
            }
        }
        Text(
            collection.displayName,
            maxLines = 1,
            softWrap = false,
            color = WarmInk,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
internal fun EditorLine(
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    iconDescription: String? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
            icon?.let { Icon(it, contentDescription = iconDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(23.dp)) }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}
