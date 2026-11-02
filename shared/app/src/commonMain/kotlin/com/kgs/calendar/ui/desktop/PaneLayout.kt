package com.kgs.calendar.ui.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kgs.calendar.ui.MotionShort
import com.kgs.calendar.ui.WarmBrown
import com.kgs.calendar.ui.WarmLine
import com.kgs.calendar.ui.platform.ResizeHorizontalPointerIcon

/**
 * The sizes of the side-by-side panes the user can drag: the desktop's sidebar and pinned task
 * pane, and the settings' list column. The desktop keeps them between launches.
 */
class PaneLayoutPreferences(
    sidebarWidthDp: Float = DefaultSidebarWidthDp,
    tasksPaneWidthDp: Float = DefaultTasksPaneWidthDp,
    tasksPanePinned: Boolean = false,
    settingsListFraction: Float = DefaultSettingsListFraction,
    private val onChanged: (PaneLayoutPreferences) -> Unit = {},
) {
    var sidebarWidthDp by mutableFloatStateOf(sidebarWidthDp.coerceIn(SidebarWidthRangeDp))
    var tasksPaneWidthDp by mutableFloatStateOf(tasksPaneWidthDp.coerceIn(TasksPaneWidthRangeDp))
    var tasksPanePinned by mutableStateOf(tasksPanePinned)
        private set
    var settingsListFraction by mutableFloatStateOf(settingsListFraction.coerceIn(SettingsListFractionRange))

    fun dragSidebar(deltaDp: Float) {
        sidebarWidthDp = (sidebarWidthDp + deltaDp).coerceIn(SidebarWidthRangeDp)
    }

    /** The pane is right of its seam, so dragging the seam left makes it wider. */
    fun dragTasksPane(deltaDp: Float) {
        tasksPaneWidthDp = (tasksPaneWidthDp - deltaDp).coerceIn(TasksPaneWidthRangeDp)
    }

    fun dragSettingsList(deltaDp: Float, totalWidthDp: Float) {
        if (totalWidthDp <= 0f) return
        settingsListFraction = (settingsListFraction + deltaDp / totalWidthDp).coerceIn(SettingsListFractionRange)
    }

    fun pinTasksPane(pinned: Boolean) {
        tasksPanePinned = pinned
        commit()
    }

    /** Stores the current sizes, e.g. when a drag ends. */
    fun commit() = onChanged(this)

    companion object {
        const val DefaultSidebarWidthDp = 296f
        const val DefaultTasksPaneWidthDp = 336f
        const val DefaultSettingsListFraction = 0.4f
        val SidebarWidthRangeDp = 248f..420f
        val TasksPaneWidthRangeDp = 280f..520f
        val SettingsListFractionRange = 0.28f..0.6f
    }
}

val LocalPaneLayout = staticCompositionLocalOf { PaneLayoutPreferences() }

/**
 * The seam between two panes: a hairline that a pointer can grab to move it. It highlights while
 * hovered or dragged; [onDrag] gets the movement in dp, [onDragEnd] follows the release.
 */
@Composable
internal fun PaneSeam(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val emphasis by animateFloatAsState(
        targetValue = when {
            dragged -> 1f
            hovered -> 0.6f
            else -> 0f
        },
        animationSpec = tween(MotionShort),
        label = "paneSeamEmphasis",
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(PaneSeamWidth)
            .pointerHoverIcon(ResizeHorizontalPointerIcon)
            .hoverable(interaction)
            .draggable(
                state = rememberDraggableState { deltaPx -> currentOnDrag(deltaPx / density.density) },
                orientation = Orientation.Horizontal,
                interactionSource = interaction,
                onDragStopped = { currentOnDragEnd() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(1.dp + 2.dp * emphasis)
                .background(lerp(WarmLine.copy(alpha = 0.6f), WarmBrown.copy(alpha = 0.8f), emphasis)),
        )
    }
}

internal val PaneSeamWidth = 9.dp
