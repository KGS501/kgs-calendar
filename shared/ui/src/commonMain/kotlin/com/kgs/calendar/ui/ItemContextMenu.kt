package com.kgs.calendar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.trash.trashRetentionDays
import com.kgs.calendar.domain.source.isReadOnlyCollection
import com.kgs.calendar.ui.res.stringResource
import com.kgs.calendar.ui.model.occurrenceStartForEdit
import com.kgs.calendar.ui.platform.onSecondaryClick
import com.kgs.calendar.ui.shell.CalendarShellUiState
import com.kgs.calendar.ui.shell.editorSchedule
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt
import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.ui.res.Res

/*
 * The right-click menu of an event or task card, wherever the card is: what the "⋮" menu of its
 * details offers (duplicate, copy to another calendar, delete), with editing first. One menu for
 * the whole app ([ItemContextMenuHost]); cards ask for it with [itemContextMenu].
 */

/** Which item's menu is open, and where in the window it was asked for. */
internal data class ItemContextMenuRequest(val item: DetailSheet, val windowPosition: Offset)

@Stable
internal class ItemContextMenuState {
    var request by mutableStateOf<ItemContextMenuRequest?>(null)
}

internal val LocalItemContextMenu = staticCompositionLocalOf<ItemContextMenuState?> { null }

/** A right click on this card opens [event]'s menu. */
internal fun Modifier.itemContextMenu(event: EventEntity): Modifier = itemContextMenu { DetailSheet.Event(event) }

/** A right click on this card opens [task]'s menu. */
internal fun Modifier.itemContextMenu(task: TaskEntity): Modifier = itemContextMenu { DetailSheet.Task(task) }

private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

private fun Modifier.itemContextMenu(item: () -> DetailSheet): Modifier = composed {
    val menu = LocalItemContextMenu.current ?: return@composed Modifier
    val currentItem by rememberUpdatedState(item)
    val holder = remember { CoordinatesHolder() }
    Modifier
        .onGloballyPositioned { holder.coordinates = it }
        .onSecondaryClick { position ->
            val coordinates = holder.coordinates?.takeIf { it.isAttached } ?: return@onSecondaryClick
            menu.request = ItemContextMenuRequest(currentItem(), coordinates.localToWindow(position))
        }
}.then(this)

/** The open item menu, and the copy and delete dialogs it leads to. */
@Composable
internal fun ItemContextMenuHost(
    menu: ItemContextMenuState,
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    shell: CalendarShellUiState,
    today: LocalDate,
) {
    var copying by remember { mutableStateOf<DetailSheet?>(null) }
    var deleting by remember { mutableStateOf<DetailSheet?>(null) }
    val request = menu.request
    if (request != null) {
        val item = request.item
        val actions = item.menuActions(state)
        fun close() {
            menu.request = null
        }
        Popup(
            popupPositionProvider = with(LocalDensity.current) {
                val shadowRoom = ItemMenuShadowRoom.roundToPx()
                remember(request.windowPosition, shadowRoom) { AtPointPositionProvider(request.windowPosition, shadowRoom) }
            },
            onDismissRequest = ::close,
            properties = PopupProperties(focusable = true),
        ) {
            val reveal = remember(request) { Animatable(0f) }
            LaunchedEffect(request) { reveal.animateTo(1f, tween(MotionShort, easing = MotionEmphasized)) }
            Surface(
                shape = ItemMenuShape,
                color = popupSurfaceColor(),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .padding(ItemMenuShadowRoom)
                    .graphicsLayer {
                        alpha = reveal.value
                        val scale = 0.94f + 0.06f * reveal.value
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Escape) {
                            close()
                            true
                        } else {
                            false
                        }
                    },
            ) {
                Column(
                    Modifier
                        .width(IntrinsicSize.Max)
                        .widthIn(min = 210.dp, max = 300.dp)
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        item.title().ifBlank { stringResource(Res.string.no_title) },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp),
                    )
                    MorphMenuRow(
                        label = stringResource(Res.string.edit),
                        selected = false,
                        role = Role.Button,
                        onClick = {
                            close()
                            when (item) {
                                is DetailSheet.Event -> shell.editEvent(item.event, item.event.editorSchedule())
                                is DetailSheet.Task -> shell.editTask(item.task, item.task.editorSchedule(today))
                            }
                        },
                        leading = { MenuIcon(Icons.Default.Edit) },
                    )
                    MorphMenuRow(
                        label = stringResource(Res.string.duplicate),
                        selected = false,
                        role = Role.Button,
                        onClick = {
                            close()
                            when (item) {
                                is DetailSheet.Event -> shell.duplicateEvent(item.event, item.event.editorSchedule())
                                is DetailSheet.Task -> shell.duplicateTask(item.task, item.task.editorSchedule(today))
                            }
                        },
                        leading = { MenuIcon(Icons.Default.ContentCopy) },
                    )
                    MorphMenuRow(
                        label = stringResource(Res.string.copy_to),
                        selected = false,
                        enabled = actions.copyTargets.isNotEmpty(),
                        role = Role.Button,
                        onClick = {
                            close()
                            copying = item
                        },
                        leading = { MenuIcon(Icons.Default.DriveFileMove) },
                    )
                    if (actions.canDelete) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        MorphMenuRow(
                            label = stringResource(Res.string.delete),
                            selected = false,
                            labelColor = MaterialTheme.colorScheme.error,
                            role = Role.Button,
                            onClick = {
                                close()
                                deleting = item
                            },
                            leading = { MenuIcon(Icons.Default.Delete, MaterialTheme.colorScheme.error) },
                        )
                    }
                }
            }
        }
    }
    copying?.let { item ->
        val actions = item.menuActions(state)
        CopyTargetDialog(
            copyTargets = actions.copyTargets,
            hiddenCollectionHrefs = actions.hiddenCollectionHrefs,
            onDismiss = { copying = null },
            onCopyTo = { href ->
                copying = null
                when (item) {
                    is DetailSheet.Event -> viewModel.edits.copyEventTo(item.event.resourceHref, href)
                    is DetailSheet.Task -> viewModel.edits.copyTaskTo(item.task.resourceHref, href)
                }
            },
        )
    }
    deleting?.let { item ->
        val actions = item.menuActions(state)
        val event = (item as? DetailSheet.Event)?.event
        DeleteConfirmationDialog(
            itemLabel = stringResource(if (event != null) Res.string.event else Res.string.task),
            recurringScopes = event != null && (event.isRecurring || !event.recurrenceRule.isNullOrBlank()),
            trashRetentionDays = actions.trashRetentionDays,
            onDismiss = { deleting = null },
            onDeleteThis = {
                deleting = null
                event?.let { viewModel.edits.deleteEventOccurrence(it.resourceHref, it.occurrenceStartForEdit()) }
            },
            onDeleteFollowing = {
                deleting = null
                event?.let { viewModel.edits.deleteEventFollowing(it.resourceHref, it.occurrenceStartForEdit()) }
            },
            onDeleteAll = {
                deleting = null
                when (item) {
                    is DetailSheet.Event -> viewModel.edits.deleteEvent(item.event.resourceHref)
                    is DetailSheet.Task -> viewModel.edits.deleteTask(item.task.resourceHref)
                }
            },
        )
    }
}

@Composable
private fun MenuIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
}

/** What the item's menu can do, as its details' "⋮" menu decides it. */
private class ItemMenuActions(
    val copyTargets: List<com.kgs.calendar.data.local.entity.CollectionEntity>,
    val hiddenCollectionHrefs: Set<String>,
    val canDelete: Boolean,
    val trashRetentionDays: Int?,
)

private fun DetailSheet.menuActions(state: CalendarUiState): ItemMenuActions {
    val href = when (this) {
        is DetailSheet.Event -> event.collectionHref
        is DetailSheet.Task -> task.collectionHref
    }
    val source = state.collections.firstOrNull { it.href == href }
    val events = this is DetailSheet.Event
    return ItemMenuActions(
        copyTargets = state.collections.filter {
            (if (events) it.supportsEvents else it.supportsTasks) && it.href != href && !it.isReadOnlyCollection()
        },
        hiddenCollectionHrefs = if (events) state.collectionVisibility.hrefsHidingEvents else state.collectionVisibility.hrefsHidingTasks,
        canDelete = source?.isReadOnlyCollection() != true,
        trashRetentionDays = trashRetentionDays(source, state.accounts.firstOrNull { it.id == source?.accountId }),
    )
}

private fun DetailSheet.title(): String = when (this) {
    is DetailSheet.Event -> event.title
    is DetailSheet.Task -> task.title
}

/** Opens at the pointer, towards the bottom right, or the other way where the window ends. */
private class AtPointPositionProvider(private val point: Offset, private val shadowRoom: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // The surface sits inside room for its shadow; its corner goes to the pointer.
        val x = point.x.roundToInt() - shadowRoom
        val y = point.y.roundToInt() - shadowRoom
        val width = popupContentSize.width
        val height = popupContentSize.height
        val left = if (x + width > windowSize.width) x - width + 2 * shadowRoom else x
        val top = if (y + height > windowSize.height) y - height + 2 * shadowRoom else y
        return IntOffset(
            left.coerceIn(0, (windowSize.width - width).coerceAtLeast(0)),
            top.coerceIn(0, (windowSize.height - height).coerceAtLeast(0)),
        )
    }
}

private val ItemMenuShape = RoundedCornerShape(22.dp)
private val ItemMenuShadowRoom = 12.dp
