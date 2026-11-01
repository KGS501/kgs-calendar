@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.kgs.calendar.ui.haptics.DragSnapHaptics
import com.kgs.calendar.ui.calendar.DayEndHour
import com.kgs.calendar.ui.calendar.DayStartHour
import com.kgs.calendar.ui.editor.EditorSchedulePreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.abs


@Composable
internal fun DraftEventWireframe(
    draft: EditorSchedulePreview,
    color: Int,
    hourHeightDp: Float,
    dayWidthPx: Float,
    onDraftChanged: (EditorSchedulePreview) -> Unit,
    onInteraction: () -> Unit,
    onTap: () -> Unit,
    timeScroll: androidx.compose.foundation.ScrollState,
    gridViewportHeightPx: Int,
) {
    val density = LocalDensity.current
    val currentDraft by rememberUpdatedState(draft)
    val currentOnDraftChanged by rememberUpdatedState(onDraftChanged)
    val visibleStartMinute = DayStartHour * 60
    val visibleEndMinute = (DayEndHour + 1) * 60 - 1
    val startMinute = draft.start.minuteOfDay().coerceIn(visibleStartMinute, visibleEndMinute - DraftMinDurationMinutes)
    val endMinute = draft.end.minuteOfDay().coerceIn(startMinute + DraftMinDurationMinutes, visibleEndMinute)
    val targetTopDp = (startMinute - visibleStartMinute) / 60f * hourHeightDp
    val targetVisualHeightDp = ((endMinute - startMinute) / 60f * hourHeightDp).coerceAtLeast(1f)
    val topDp by animateFloatAsState(
        targetValue = targetTopDp,
        animationSpec = tween(80, easing = MotionStandard),
        label = "draftWireframeTop",
    )
    val visualHeightDp by animateFloatAsState(
        targetValue = targetVisualHeightDp,
        animationSpec = tween(80, easing = MotionStandard),
        label = "draftWireframeHeight",
    )
    val touchHeightDp = max(44f, visualHeightDp)
    val visualTopInsetDp = (touchHeightDp - visualHeightDp) / 2f
    val shape = RoundedCornerShape(8.dp)
    val wireframeColor = Color(color)
    var dragX by remember { mutableFloatStateOf(0f) }
    var draggingBody by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val dayStepPx = dayWidthPx + with(density) { DayColumnSpacing.toPx() }
    val snappedDayDelta = if (draggingBody && dayStepPx > 0f) (dragX / dayStepPx).roundToInt() else 0
    // One light tick each time a moved or resized draft snaps to another time slot or day.
    if (dragging) DragSnapHaptics(slot = Triple(startMinute, endMinute, snappedDayDelta))
    val animatedDragX by animateFloatAsState(
        targetValue = if (draggingBody) snappedDayDelta * dayStepPx else 0f,
        animationSpec = tween(110, easing = MotionStandard),
        label = "draftWireframeDragX",
    )

    fun Modifier.draftDrag(mode: DraftDragMode): Modifier = pointerInput(hourHeightDp, dayWidthPx, mode, gridViewportHeightPx) {
        coroutineScope {
        val hourHeightPx = with(density) { hourHeightDp.dp.toPx() }
        val edgeThresholdPx = with(density) { 34.dp.toPx() }
        val dayStep = dayWidthPx + with(density) { DayColumnSpacing.toPx() }
        var dragStartMinute = 0
        var dragEndMinute = 0
        var dragStartDate = currentDraft.date
        var totalDragPx = 0f
        var lastPublishedDraft: EditorSchedulePreview? = null
        var autoScrollDirection = 0f
        var autoScrollJob: Job? = null

        fun publishDraft(commitDate: Boolean = false) {
            val deltaMinutes = ((totalDragPx / hourHeightPx) * 60f).roundToInt().snapDraftMinute()
            val dayDelta = if (mode == DraftDragMode.Move && dayStep > 0f) {
                (dragX / dayStep).roundToInt()
            } else {
                0
            }
            val nextDraft = currentDraft.withDraggedMinutes(
                    mode = mode,
                    originalStartMinute = dragStartMinute,
                    originalEndMinute = dragEndMinute,
                    deltaMinutes = deltaMinutes,
                )
                .copy(date = if (commitDate) dragStartDate.plusDays(dayDelta.toLong()) else dragStartDate)
            if (nextDraft != lastPublishedDraft) {
                lastPublishedDraft = nextDraft
                currentOnDraftChanged(nextDraft)
            }
        }

        fun updateAutoScroll(direction: Float) {
            autoScrollDirection = direction
            if (direction == 0f) {
                autoScrollJob?.cancel()
                autoScrollJob = null
                return
            }
            if (autoScrollJob?.isActive == true) return
            autoScrollJob = launch {
                while (true) {
                    val consumed = timeScroll.dispatchRawDelta(autoScrollDirection * 9f)
                    if (abs(consumed) < 0.5f) {
                        delay(28)
                    } else {
                        totalDragPx += consumed
                        publishDraft()
                        delay(16)
                    }
                }
            }
        }

        detectDragGestures(
            onDragStart = {
                onInteraction()
                val snapshot = currentDraft
                dragStartDate = snapshot.date
                dragStartMinute = snapshot.start.minuteOfDay()
                dragEndMinute = snapshot.end.minuteOfDay()
                totalDragPx = 0f
                lastPublishedDraft = snapshot
                dragX = 0f
                draggingBody = mode == DraftDragMode.Move
                dragging = true
            },
            onDrag = { change, dragAmount ->
                change.consume()
                totalDragPx += dragAmount.y
                if (mode == DraftDragMode.Move) dragX += dragAmount.x
                publishDraft()
                val originalTopPx = ((dragStartMinute - visibleStartMinute) / 60f) * hourHeightPx
                val visiblePointerY = originalTopPx + change.position.y + totalDragPx - timeScroll.value
                updateAutoScroll(
                    when {
                        visiblePointerY < edgeThresholdPx -> -1f
                        gridViewportHeightPx > 0 && visiblePointerY > gridViewportHeightPx - edgeThresholdPx -> 1f
                        else -> 0f
                    },
                )
            },
            onDragEnd = {
                updateAutoScroll(0f)
                publishDraft(commitDate = true)
                draggingBody = false
                dragging = false
                dragX = 0f
            },
            onDragCancel = {
                updateAutoScroll(0f)
                draggingBody = false
                dragging = false
                dragX = 0f
            },
        )
        }
    }

    Box(
        modifier = Modifier
            .zIndex(80f)
            .offset {
                IntOffset(
                    animatedDragX.roundToInt(),
                    with(density) { (topDp - visualTopInsetDp).dp.roundToPx() },
                )
            }
            .fillMaxWidth()
            .height(touchHeightDp.dp)
            .padding(horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTap,
                )
                .testTag("editor-wireframe-body")
                .draftDrag(DraftDragMode.Move),
        )
        Box(
            modifier = Modifier
                .offset(y = visualTopInsetDp.dp)
                .fillMaxWidth()
                .height(visualHeightDp.dp)
                .clip(shape)
                .background(wireframeColor.copy(alpha = 0.14f))
                .border(1.5.dp, wireframeColor, shape)
        )
        // Start handle on the LEFT edge (top), end handle on the RIGHT edge (bottom) — diagonally
        // opposite so they never collide even in a narrow 3-day column.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-6).dp, y = (visualTopInsetDp - 20f).dp)
                .size(40.dp)
                .testTag("editor-wireframe-start-handle")
                .draftDrag(DraftDragMode.Start),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(wireframeColor)
                    .border(2.dp, Color.White, CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (visualTopInsetDp + visualHeightDp - 20f).dp)
                .size(40.dp)
                .testTag("editor-wireframe-end-handle")
                .draftDrag(DraftDragMode.End),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(wireframeColor)
                    .border(2.dp, Color.White, CircleShape),
            )
        }
    }
}
