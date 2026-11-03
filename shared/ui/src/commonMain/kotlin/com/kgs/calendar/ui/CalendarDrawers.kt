@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.kgs.calendar.ui.res.pluralStringResource
import com.kgs.calendar.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.kgs.calendar.ui.res.Res
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.data.account.SourceImage
import com.kgs.calendar.domain.source.CalendarSourceGroup
import com.kgs.calendar.domain.source.ReadOnlyFeedStatus
import com.kgs.calendar.domain.source.readOnlyFeedStatus
import com.kgs.calendar.ui.drawer.SharedDrawerActionRow
import com.kgs.calendar.ui.drawer.SharedDrawerHeader
import com.kgs.calendar.ui.drawer.SharedDrawerSheet
import com.kgs.calendar.ui.drawer.SharedDrawerViewItem
import com.kgs.calendar.ui.sources.SourceAvatar
import com.kgs.calendar.ui.sources.displayName
import com.kgs.calendar.ui.sources.avatarColor
import com.kgs.calendar.ui.sources.image
import com.kgs.calendar.ui.sources.label
import com.kgs.calendar.ui.sources.subtitle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.kgs.calendar.ui.platform.onSecondaryClick
import com.kgs.calendar.ui.theme.LocalAppFontFamily
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin

@Composable
fun CalendarDrawer(
    visible: Boolean,
    state: CalendarUiState,
    onDismiss: () -> Unit,
    onViewSelected: (CalendarViewMode) -> Unit,
    onSync: () -> Unit,
    onCollectionVisibleInViews: (String, Boolean) -> Unit,
    onCollectionSettings: (CollectionEntity) -> Unit,
    onGroupSettings: (String) -> Unit,
    onAppSettings: () -> Unit,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    SharedDrawerSheet(visible = visible, onDismiss = onDismiss) {
            // Settings (and problems, when there are any) stay pinned below the scrolling list.
            Column(Modifier.fillMaxSize().padding(bottom = navBottom + 8.dp)) {
                val listScroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(listScroll)
                        .padding(top = statusTop + 22.dp, bottom = 12.dp),
                ) {
                    SharedDrawerHeader("KGS Calendar")
                    DrawerViewItem(stringResource(Res.string.agenda), Icons.Default.ViewAgenda, CalendarViewMode.Agenda, state.selectedView, onViewSelected)
                    DrawerViewItem(stringResource(Res.string.day), Icons.Default.ViewDay, CalendarViewMode.Day, state.selectedView, onViewSelected)
                    DrawerViewItem(
                        stringResource(CalendarViewMode.ThreeDay.labelRes(state.weekViewEnabled)),
                        Icons.Default.ViewWeek,
                        CalendarViewMode.ThreeDay,
                        state.selectedView,
                        onViewSelected,
                    )
                    DrawerViewItem(stringResource(Res.string.month), Icons.Default.CalendarMonth, CalendarViewMode.Month, state.selectedView, onViewSelected)
                    DrawerActionRow(stringResource(Res.string.refresh), Icons.Default.Refresh, onSync)
                    HorizontalDivider(color = WarmLine, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 6.dp))
                    CalendarSourceList(
                        state = state,
                        onCollectionVisibleInViews = onCollectionVisibleInViews,
                        onCollectionSettings = onCollectionSettings,
                        onGroupSettings = onGroupSettings,
                    )
                }
                // The line only shows while calendars scroll underneath the pinned rows.
                HorizontalDivider(color = WarmLine.copy(alpha = if (listScroll.canScrollForward) 1f else 0f))
                Spacer(Modifier.height(6.dp))
                DrawerActionRow(stringResource(Res.string.app_settings), Icons.Default.Settings, onAppSettings)
            }
    }
}

/** The calendars by source, each with its visibility switch and settings, as in the drawer. */
@Composable
internal fun CalendarSourceList(
    state: CalendarUiState,
    onCollectionVisibleInViews: (String, Boolean) -> Unit,
    onCollectionSettings: (CollectionEntity) -> Unit,
    onGroupSettings: (String) -> Unit,
) {
    val accounts = state.accounts.ifEmpty { state.account?.let(::listOf).orEmpty() }
    val visibleGroups = state.calendarGroups
        .map { group -> group to group.collections.filter { it.isEnabled } }
        .filter { (_, collections) -> collections.isNotEmpty() }
    visibleGroups.forEachIndexed { index, (group, collections) ->
        if (index > 0) {
            Spacer(Modifier.height(10.dp))
        }
        DrawerSourceSection(
            group = group,
            image = group.image(state.sourceImages),
            avatarColor = group.avatarColor(state.calendarGroupSettings),
            collections = collections,
            feedStatus = { collection ->
                readOnlyFeedStatus(accounts.firstOrNull { it.id == collection.accountId }, collection)
            },
            hiddenCollectionHrefs = state.hiddenCollectionHrefs,
            onGroupSettings = { onGroupSettings(group.key) },
            onCollectionVisibleInViews = onCollectionVisibleInViews,
            onCollectionSettings = onCollectionSettings,
        )
    }
}

@Composable
private fun DrawerSourceSection(
    group: CalendarSourceGroup,
    image: SourceImage?,
    avatarColor: Int,
    collections: List<CollectionEntity>,
    feedStatus: (CollectionEntity) -> ReadOnlyFeedStatus?,
    hiddenCollectionHrefs: Set<String>,
    onGroupSettings: () -> Unit,
    onCollectionVisibleInViews: (String, Boolean) -> Unit,
    onCollectionSettings: (CollectionEntity) -> Unit,
) {
    val groupName = group.displayName()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(Res.string.calendar_group_edit), onClick = onGroupSettings)
            .onSecondaryClick { onGroupSettings() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SourceAvatar(groupName, image, 30.dp, avatarColor)
        Column(Modifier.weight(1f)) {
            Text(groupName, color = WarmInk, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = group.subtitle()
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 13.sp,
            )
        }
    }
    val sortedCollections = collections.sortedWith(
        compareBy<CollectionEntity> {
            when {
                it.supportsEvents && it.supportsTasks -> 0
                it.supportsEvents -> 1
                else -> 2
            }
        }.thenBy { it.displayName.lowercase() },
    )
    sortedCollections.forEach { collection ->
        DrawerCollectionRow(
            collection = collection,
            feedStatus = feedStatus(collection),
            visibleInViews = collection.href !in hiddenCollectionHrefs,
            onVisibleInViewsChanged = { onCollectionVisibleInViews(collection.href, it) },
            onSettings = { onCollectionSettings(collection) },
        )
    }
}

@Composable
private fun DrawerViewItem(
    label: String,
    icon: ImageVector,
    viewMode: CalendarViewMode,
    selectedView: CalendarViewMode,
    onViewSelected: (CalendarViewMode) -> Unit,
) = SharedDrawerViewItem(label, icon, selected = viewMode == selectedView, onClick = { onViewSelected(viewMode) })

@Composable
private fun DrawerActionRow(label: String, icon: ImageVector, onClick: () -> Unit, count: Int? = null) =
    SharedDrawerActionRow(label, icon, onClick, count)

@Composable
private fun DrawerCollectionGroupHeader(label: String) {
    Text(
        label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 30.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun DrawerCollectionRow(
    collection: CollectionEntity,
    feedStatus: ReadOnlyFeedStatus?,
    visibleInViews: Boolean,
    onVisibleInViewsChanged: (Boolean) -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSettings)
            .onSecondaryClick { onSettings() }
            // Indented under the group's picture, so each group's calendars read as one set.
            .padding(start = 42.dp, end = 24.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CollectionToggle(color = Color(collection.color), checked = visibleInViews) {
            onVisibleInViewsChanged(!visibleInViews)
        }
        // A hidden calendar fades back, with its labels.
        Column(Modifier.weight(1f).alpha(if (visibleInViews) 1f else 0.55f)) {
            Text(
                collection.displayName,
                color = WarmInk,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (collection.supportsEvents) {
                    CalendarCapabilityChip(stringResource(Res.string.events), Color(0xFF2F5AEA))
                }
                if (collection.supportsTasks) {
                    CalendarCapabilityChip(stringResource(Res.string.tasks), Color(0xFF00A86B))
                }
                // A subscription without items says why, instead of looking like an empty calendar.
                if (feedStatus != null) {
                    Text(
                        feedStatus.label(),
                        color = if (feedStatus == ReadOnlyFeedStatus.Empty) MaterialTheme.colorScheme.onSurfaceVariant else SyncPendingOrange,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun HiddenCalendarCreationDialog(
    collection: CollectionEntity,
    itemLabel: String,
    onDismiss: () -> Unit,
    onUnhide: () -> Unit,
) {
    KgsAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.hidden_calendar_creation_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(Res.string.hidden_calendar_creation_body, itemLabel, collection.displayName),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                val hiddenCalendarShape = RoundedCornerShape(18.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dashedBorder(SyncPendingOrange, 18.dp)
                        .clickable(onClick = onUnhide),
                    shape = hiddenCalendarShape,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CollectionToggle(color = Color(collection.color), checked = false, onClick = onUnhide)
                        Column(Modifier.weight(1f)) {
                            Text(
                                collection.displayName,
                                color = WarmInk,
                                fontSize = 14.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (collection.supportsEvents) {
                                    CalendarCapabilityChip(stringResource(Res.string.events), Color(0xFF2F5AEA))
                                }
                                if (collection.supportsTasks) {
                                    CalendarCapabilityChip(stringResource(Res.string.tasks), Color(0xFF00A86B))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUnhide) {
                Text(stringResource(Res.string.unhide_calendar))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.close))
            }
        },
    )
}

/** A small, borderless label for what a calendar holds (events, tasks). */
@Composable
fun CalendarCapabilityChip(label: String, color: Color) {
    val dark = MaterialTheme.colorScheme.background.isDark()
    Text(
        label,
        // Saturated blues and greens are hard to read on dark surfaces, so they are lightened there.
        color = if (dark) color.blendWith(Color.White, 0.45f) else color,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = if (dark) 0.24f else 0.10f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * Whether a calendar shows in the views: a rounded square outline in the calendar's color with a
 * filled rounded square inside. The inner corners follow the outer ones (outer radius minus the
 * inset), so both read as one shape. Pressing presses the box in; checking grows the inner square
 * with a slight overshoot, unchecking shrinks it away.
 */
@Composable
fun CollectionToggle(color: Color, checked: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val boxScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f),
        label = "collectionTogglePress",
    )
    val innerScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (checked) spring(dampingRatio = 0.55f, stiffness = 500f) else tween(MotionShort, easing = MotionStandardAccelerate),
        label = "collectionToggleInner",
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(ToggleOuterSize)
                .scale(boxScale)
                .border(ToggleStroke, color, RoundedCornerShape(ToggleOuterRadius)),
            contentAlignment = Alignment.Center,
        ) {
            val innerSize = ToggleOuterSize - (ToggleStroke + ToggleGap) * 2
            Box(
                Modifier
                    .size(innerSize)
                    .graphicsLayer {
                        scaleX = innerScale
                        scaleY = innerScale
                        alpha = innerScale.coerceIn(0f, 1f)
                    }
                    // A scaled copy of the outer shape: the same radius in proportion to its size.
                    .clip(RoundedCornerShape(ToggleOuterRadius * (innerSize / ToggleOuterSize)))
                    .background(color),
            )
        }
    }
}

private val ToggleOuterSize = 20.dp
private val ToggleOuterRadius = 6.5.dp
private val ToggleStroke = 1.8.dp
private val ToggleGap = 2.dp

@Composable
private fun DrawerGlyph(selected: Boolean, modifier: Modifier = Modifier) {
    val color = if (selected) WarmInk else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .width(24.dp)
            .height(22.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(3) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = if (selected) 1f else 0.72f)),
            )
        }
    }
}

@Composable
fun TaskDrawer(
    visible: Boolean,
    state: CalendarUiState,
    onDismiss: () -> Unit,
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onShowCompleted: () -> Unit,
    onCreateTask: () -> Unit,
    /** Pins the sidebar as a permanent pane (the desktop); null where it can't be pinned. */
    onPin: (() -> Unit)? = null,
    completedInline: Boolean = false,
) {
    val drawerProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 145 else 115, easing = if (visible) MotionStandard else MotionStandardAccelerate),
        label = "taskDrawerProgress",
    )
    val quietInteraction = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val drawerWidth = 336.dp
    val drawerWidthPx = with(density) { drawerWidth.toPx() }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val effectiveProgress = (drawerProgress - dragOffsetPx / drawerWidthPx).coerceIn(0f, 1f)
    LaunchedEffect(visible) {
        if (visible) {
            dragOffsetPx = 0f
        } else {
            delay(150)
            dragOffsetPx = 0f
        }
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        if (effectiveProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(effectiveProgress)
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(
                        interactionSource = quietInteraction,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(drawerWidth)
                .align(Alignment.CenterEnd)
                .offset {
                    IntOffset((drawerWidthPx * (1f - effectiveProgress)).roundToInt(), 0)
                }
                .pointerInput(visible, drawerWidthPx) {
                    detectHorizontalDragGestures(
                        onDragCancel = { dragOffsetPx = 0f },
                        onDragEnd = {
                            if (dragOffsetPx >= drawerWidthPx * 0.28f) {
                                onDismiss()
                            } else {
                                dragOffsetPx = 0f
                            }
                        },
                    ) { change, dragAmount ->
                        if (dragAmount > 0f || dragOffsetPx > 0f) {
                            change.consume()
                            dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(0f, drawerWidthPx)
                        }
                    }
                },
            shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 0.dp,
        ) {
            TaskSidebarContent(
                state = state,
                onTaskStatusChanged = onTaskStatusChanged,
                onTaskClick = onTaskClick,
                onShowCompleted = onShowCompleted,
                onCreateTask = onCreateTask,
                pinned = false,
                onPinnedChange = onPin?.let { pin -> { pinned: Boolean -> if (pinned) pin() } },
                completedInline = completedInline,
            )
        }
    }
}

/**
 * The task sidebar in the book posture: the task drawer's content as a permanent pane right of the hinge.
 * A hairline on its hinge side separates it from the calendar like the drawers' dividers.
 */
@Composable
fun TaskPane(
    state: CalendarUiState,
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onShowCompleted: () -> Unit,
    onCreateTask: () -> Unit,
    modifier: Modifier = Modifier,
    /** The hairline on the pane's calendar side; the desktop draws its own seam there. */
    showDivider: Boolean = true,
    pinned: Boolean = false,
    onPinnedChange: ((Boolean) -> Unit)? = null,
    completedInline: Boolean = false,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .testTag(TaskPaneTag),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            TaskSidebarContent(
                state = state,
                onTaskStatusChanged = onTaskStatusChanged,
                onTaskClick = onTaskClick,
                onShowCompleted = onShowCompleted,
                onCreateTask = onCreateTask,
                pinned = pinned,
                onPinnedChange = onPinnedChange,
                completedInline = completedInline,
            )
            if (showDivider) Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(WarmLine),
            )
        }
    }
}

const val TaskPaneTag = "task-pane"

/** The task sidebar's header with its "new task" button, above the task inbox. */
@Composable
private fun TaskSidebarContent(
    state: CalendarUiState,
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onShowCompleted: () -> Unit,
    onCreateTask: () -> Unit,
    pinned: Boolean = false,
    onPinnedChange: ((Boolean) -> Unit)? = null,
    /** The completed tasks open inside the sidebar (the desktop) instead of as their own page. */
    completedInline: Boolean = false,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var showingCompleted by remember { mutableStateOf(false) }
    AnimatedContent(
        targetState = completedInline && showingCompleted,
        transitionSpec = {
            val forward = targetState
            (slideInHorizontally(tween(MotionMedium, easing = MotionEmphasized)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(MotionMedium))) togetherWith
                (slideOutHorizontally(tween(MotionShort, easing = MotionStandardAccelerate)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(MotionShort)))
        },
        label = "taskSidebarCompleted",
    ) { completed ->
    if (completed) {
        CompletedTasksView(
            tasks = remember(state.taskHierarchyTasks) {
                state.taskHierarchyTasks.partitionByRootActivity().inactiveRootTasks
            },
            taskColorMode = state.taskColorMode,
            subtasksExpandedByDefault = state.subtasksExpandedByDefault,
            onTaskStatusChanged = onTaskStatusChanged,
            onTaskClick = onTaskClick,
            onClose = { showingCompleted = false },
            embedded = true,
        )
        return@AnimatedContent
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = statusTop + 18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // One word that shrinks to fit a narrow sidebar rather than breaking.
            BasicText(
                stringResource(Res.string.tasks),
                style = MaterialTheme.typography.titleLarge.copy(
                    color = WarmInk,
                    fontSize = 21.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 21.sp, stepSize = 0.5.sp),
                modifier = Modifier.weight(1f),
            )
            if (onPinnedChange != null) {
                // Pinned, the sidebar stays beside the calendar like the left one.
                // Active, only the pin itself takes the accent color.
                val pinTint by animateColorAsState(
                    targetValue = if (pinned) WarmBrown else WarmInk,
                    animationSpec = tween(MotionMedium, easing = MotionStandard),
                    label = "taskPinTint",
                )
                IconButton(
                    onClick = { onPinnedChange(!pinned) },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(if (pinned) Res.string.desktop_unpin_tasks else Res.string.desktop_pin_tasks),
                        tint = pinTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(
                onClick = onCreateTask,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accentContainerColor()),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(Res.string.new_task),
                    tint = accentContainerContentColor(),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        TaskInbox(
            state = state,
            onTaskStatusChanged = onTaskStatusChanged,
            onDetail = { detail ->
                if (detail is DetailSheet.Task) onTaskClick(detail.task)
            },
            onShowCompleted = if (completedInline) {
                { showingCompleted = true }
            } else {
                onShowCompleted
            },
        )
    }
    }
}
