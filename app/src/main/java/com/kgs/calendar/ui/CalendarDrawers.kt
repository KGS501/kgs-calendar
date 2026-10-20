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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.data.account.SourceImage
import com.kgs.calendar.domain.source.CalendarSourceGroup
import com.kgs.calendar.domain.source.ReadOnlyFeedStatus
import com.kgs.calendar.domain.source.readOnlyFeedStatus
import com.kgs.calendar.ui.sources.SourceAvatar
import com.kgs.calendar.ui.sources.displayName
import com.kgs.calendar.ui.sources.image
import com.kgs.calendar.ui.sources.label
import com.kgs.calendar.ui.sources.subtitle
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun CalendarDrawer(
    visible: Boolean,
    state: CalendarUiState,
    onDismiss: () -> Unit,
    onViewSelected: (CalendarViewMode) -> Unit,
    onSync: () -> Unit,
    onCollectionVisibleInViews: (String, Boolean) -> Unit,
    onCollectionSettings: (CollectionEntity) -> Unit,
    onGroupSettings: (String) -> Unit,
    onAppSettings: () -> Unit,
    problems: List<ProblemItem>,
    onProblems: () -> Unit,
) {
    val drawerProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 135 else 115, easing = if (visible) MotionStandard else MotionStandardAccelerate),
        label = "drawerProgress",
    )
    val quietInteraction = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val drawerWidth = 292.dp
    val drawerWidthPx = with(density) { drawerWidth.toPx() }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val effectiveProgress = (drawerProgress + dragOffsetPx / drawerWidthPx).coerceIn(0f, 1f)
    LaunchedEffect(visible) {
        if (visible) {
            dragOffsetPx = 0f
        } else {
            delay(150)
            dragOffsetPx = 0f
        }
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        if (effectiveProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(effectiveProgress)
                    .background(Color.Black.copy(alpha = 0.58f))
                    .clickable(
                        interactionSource = quietInteraction,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        Surface(
            modifier = Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .offset {
                    IntOffset(((-drawerWidthPx) * (1f - effectiveProgress)).roundToInt(), 0)
                }
                .pointerInput(visible, drawerWidthPx) {
                    detectHorizontalDragGestures(
                        onDragCancel = { dragOffsetPx = 0f },
                        onDragEnd = {
                            if (dragOffsetPx <= -drawerWidthPx * 0.28f) {
                                onDismiss()
                            } else {
                                dragOffsetPx = 0f
                            }
                        },
                    ) { change, dragAmount ->
                        if (dragAmount < 0f || dragOffsetPx < 0f) {
                            change.consume()
                            dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(-drawerWidthPx, 0f)
                        }
                    }
                },
            shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 0.dp,
        ) {
            // Settings (and problems, when there are any) stay pinned below the scrolling list.
            Column(Modifier.fillMaxSize().padding(bottom = navBottom + 8.dp)) {
                val listScroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(listScroll)
                        .padding(top = statusTop + 22.dp, bottom = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        KgsLogoBurstButton()
                        Text(
                            "KGS Calendar",
                            fontSize = 22.sp,
                            lineHeight = 25.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif,
                            color = WarmInk,
                        )
                    }
                    DrawerViewItem(stringResource(R.string.agenda), Icons.Default.ViewAgenda, CalendarViewMode.Agenda, state.selectedView, onViewSelected)
                    DrawerViewItem(stringResource(R.string.day), Icons.Default.ViewDay, CalendarViewMode.Day, state.selectedView, onViewSelected)
                    DrawerViewItem(
                        stringResource(CalendarViewMode.ThreeDay.labelRes(state.weekViewEnabled)),
                        Icons.Default.ViewWeek,
                        CalendarViewMode.ThreeDay,
                        state.selectedView,
                        onViewSelected,
                    )
                    DrawerViewItem(stringResource(R.string.month), Icons.Default.CalendarMonth, CalendarViewMode.Month, state.selectedView, onViewSelected)
                    DrawerActionRow(stringResource(R.string.refresh), Icons.Default.Refresh, onSync)
                    HorizontalDivider(color = WarmLine, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 6.dp))
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
                // The line only shows while calendars scroll underneath the pinned rows.
                HorizontalDivider(color = WarmLine.copy(alpha = if (listScroll.canScrollForward) 1f else 0f))
                Spacer(Modifier.height(6.dp))
                if (problems.isNotEmpty()) {
                    DrawerProblemRow(
                        count = problems.size,
                        onClick = onProblems,
                    )
                }
                DrawerActionRow(stringResource(R.string.app_settings), Icons.Default.Settings, onAppSettings)
            }
        }
    }
}

@Composable
private fun DrawerProblemRow(count: Int, onClick: () -> Unit) {
    val color = SyncPendingOrange
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(color.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.34f else 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(23.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(stringResource(R.string.problems), color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.problem_count, count), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun DrawerSourceSection(
    group: CalendarSourceGroup,
    image: SourceImage?,
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
            .clickable(onClickLabel = stringResource(R.string.calendar_group_edit), onClick = onGroupSettings)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SourceAvatar(groupName, image, 30.dp)
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
        }.thenBy { it.displayName.lowercase(Locale.ROOT) },
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
) {
    val selected = viewMode == selectedView
    val background by animateColorAsState(
        targetValue = if (selected) WarmPeach else Color.Transparent,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "drawerSelection",
    )
    val glyphScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "drawerGlyphScale",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(background)
            .clickable { onViewSelected(viewMode) }
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) WarmInk else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(23.dp).scale(glyphScale),
        )
        Text(label, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun DrawerActionRow(label: String, icon: ImageVector, onClick: () -> Unit, count: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(23.dp))
        Text(label, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        if (count != null) {
            Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 17.sp)
        }
    }
}

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
            .padding(start = 18.dp, end = 24.dp, top = 3.dp, bottom = 3.dp),
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
                    CalendarCapabilityChip(stringResource(R.string.events), Color(0xFF2F5AEA))
                }
                if (collection.supportsTasks) {
                    CalendarCapabilityChip(stringResource(R.string.tasks), Color(0xFF00A86B))
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
internal fun HiddenCalendarCreationDialog(
    collection: CollectionEntity,
    itemLabel: String,
    onDismiss: () -> Unit,
    onUnhide: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.hidden_calendar_creation_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(R.string.hidden_calendar_creation_body, itemLabel, collection.displayName),
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
                                    CalendarCapabilityChip(stringResource(R.string.events), Color(0xFF2F5AEA))
                                }
                                if (collection.supportsTasks) {
                                    CalendarCapabilityChip(stringResource(R.string.tasks), Color(0xFF00A86B))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUnhide) {
                Text(stringResource(R.string.unhide_calendar))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

/** A small, borderless label for what a calendar holds (events, tasks). */
@Composable
internal fun CalendarCapabilityChip(label: String, color: Color) {
    Text(
        label,
        color = color,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.24f else 0.10f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * Whether a calendar shows in the views: a dot in the calendar's color, filled with a check
 * when shown and a faint tint when hidden. The touch target is larger than the dot.
 */
@Composable
private fun CollectionToggle(color: Color, checked: Boolean, onClick: () -> Unit) {
    val fillColor by animateColorAsState(
        targetValue = if (checked) color else color.copy(alpha = 0.18f),
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "collectionToggleFill",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(MotionShort, easing = MotionStandard),
        label = "collectionToggleCheck",
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() }),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(fillColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.62f) Color.Black.copy(alpha = 0.72f) else Color.White,
                modifier = Modifier
                    .size(15.dp)
                    .scale(checkScale),
            )
        }
    }
}

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
internal fun TaskDrawer(
    visible: Boolean,
    state: CalendarUiState,
    onDismiss: () -> Unit,
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onShowCompleted: () -> Unit,
    onCreateTask: () -> Unit,
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
            )
        }
    }
}

/**
 * The task sidebar in the book posture: the task drawer's content as a permanent pane right of the hinge.
 * A hairline on its hinge side separates it from the calendar like the drawers' dividers.
 */
@Composable
internal fun TaskPane(
    state: CalendarUiState,
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onShowCompleted: () -> Unit,
    onCreateTask: () -> Unit,
    modifier: Modifier = Modifier,
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
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(WarmLine),
            )
        }
    }
}

internal const val TaskPaneTag = "task-pane"

/** The task sidebar's header with its "new task" button, above the task inbox. */
@Composable
private fun TaskSidebarContent(
    state: CalendarUiState,
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onShowCompleted: () -> Unit,
    onCreateTask: () -> Unit,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
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
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WarmInk, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tasks), color = WarmInk, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
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
                    contentDescription = stringResource(R.string.new_task),
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
            onShowCompleted = onShowCompleted,
        )
    }
}
