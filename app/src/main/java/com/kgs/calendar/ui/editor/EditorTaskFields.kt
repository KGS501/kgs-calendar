@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kgs.calendar.ui.components.ClearableOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.domain.task.TaskParentLookup
import com.kgs.calendar.domain.task.isInactive
import com.kgs.calendar.ui.editor.EditorScheduleState
import com.kgs.calendar.ui.model.taskDate
import java.util.Locale
import kotlin.math.max

@Composable
internal fun TaskScheduleEditor(
    schedule: EditorScheduleState,
    onScheduleChange: (EditorScheduleState) -> Unit,
    endIsError: Boolean = false,
) {
    val scheduled = schedule.hasStartDate || schedule.hasEndDate
    Column(
        modifier = Modifier.animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (scheduled) {
            EditorLine(Icons.Default.AccessTime) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.all_day), fontSize = 17.sp, lineHeight = 20.sp)
                    Switch(
                        checked = schedule.allDay,
                        onCheckedChange = { allDay ->
                            onScheduleChange(
                                schedule.copy(
                                    allDay = allDay,
                                    hasStartTime = if (allDay) false else schedule.hasStartDate,
                                    hasEndTime = if (allDay) false else schedule.hasEndDate,
                                ).recalculatePreview(),
                            )
                        },
                    )
                }
            }
        } else {
            Text(stringResource(R.string.task_no_date), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 17.sp)
        }
        TaskScheduleRow(
            label = stringResource(R.string.start_date),
            addLabel = stringResource(R.string.add_field, stringResource(R.string.start_date)),
            removeLabel = stringResource(R.string.remove_field, stringResource(R.string.start_date)),
            timeLabel = stringResource(R.string.start),
            hasDate = schedule.hasStartDate,
            onHasDateChange = { hasDate ->
                val allDay = if (hasDate && !schedule.hasStartDate && !schedule.hasEndDate) true else schedule.allDay
                onScheduleChange(
                    schedule.copy(
                        hasStartDate = hasDate,
                        hasStartTime = hasDate && !allDay,
                        allDay = allDay,
                    ).recalculatePreview(),
                )
            },
            dateText = schedule.startDateText,
            onDateTextChange = { onScheduleChange(schedule.copy(startDateText = it).recalculatePreview()) },
            hasTime = schedule.hasStartTime,
            timeText = schedule.startTimeText,
            onTimeTextChange = { onScheduleChange(schedule.copy(startTimeText = it).recalculatePreview()) },
            allDay = schedule.allDay,
        )
        TaskScheduleRow(
            label = stringResource(R.string.end_date),
            addLabel = stringResource(R.string.add_field, stringResource(R.string.end_date)),
            removeLabel = stringResource(R.string.remove_field, stringResource(R.string.end_date)),
            timeLabel = stringResource(R.string.end),
            hasDate = schedule.hasEndDate,
            onHasDateChange = { hasDate ->
                val allDay = if (hasDate && !schedule.hasStartDate && !schedule.hasEndDate) true else schedule.allDay
                onScheduleChange(
                    schedule.copy(
                        hasEndDate = hasDate,
                        hasEndTime = hasDate && !allDay,
                        allDay = allDay,
                    ).recalculatePreview(),
                )
            },
            dateText = schedule.endDateText,
            onDateTextChange = { onScheduleChange(schedule.copy(endDateText = it).recalculatePreview()) },
            hasTime = schedule.hasEndTime,
            timeText = schedule.endTimeText,
            onTimeTextChange = { onScheduleChange(schedule.copy(endTimeText = it).recalculatePreview()) },
            allDay = schedule.allDay,
            isError = endIsError,
        )
    }
}

@Composable
internal fun TaskParentSelector(
    selectedParentUid: String?,
    candidates: List<TaskEntity>,
    taskColorMode: TaskColorMode,
    onSelected: (String?) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selected = candidates.firstOrNull { it.uid == selectedParentUid }
    LaunchedEffect(pickerOpen) {
        if (pickerOpen) query = ""
    }
    Column(
        modifier = Modifier.editorInset(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.parent_task),
            color = WarmInk,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { pickerOpen = true },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                border = BorderStroke(1.dp, WarmLine),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.TaskAlt, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(20.dp))
                    Text(
                        selected?.title ?: stringResource(R.string.no_parent_task),
                        color = WarmInk,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = WarmInk, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    if (pickerOpen) {
        TaskParentPickerDialog(
            selectedParentUid = selectedParentUid,
            candidates = candidates,
            query = query,
            onQueryChange = { query = it },
            taskColorMode = taskColorMode,
            onDismiss = { pickerOpen = false },
            onSelected = {
                onSelected(it)
                pickerOpen = false
            },
        )
    }
}

@Composable
internal fun TaskParentPickerDialog(
    selectedParentUid: String?,
    candidates: List<TaskEntity>,
    query: String,
    onQueryChange: (String) -> Unit,
    taskColorMode: TaskColorMode,
    onDismiss: () -> Unit,
    onSelected: (String?) -> Unit,
) {
    val filteredCandidates = remember(candidates, query) { candidates.filterForParentPicker(query) }
    val hierarchy = rememberTaskHierarchyPresentation(filteredCandidates, expandedByDefault = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.parent_task),
                        color = WarmInk,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = WarmInk)
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.search_ellipsis)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelected(null) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selectedParentUid == null) WarmPeach.copy(alpha = 0.62f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                    border = BorderStroke(1.dp, if (selectedParentUid == null) WarmBrown.copy(alpha = 0.5f) else WarmLine),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.TaskAlt, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.no_parent_task),
                            color = WarmInk,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                            fontWeight = if (selectedParentUid == null) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (selectedParentUid == null) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (filteredCandidates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(116.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (candidates.isEmpty()) stringResource(R.string.no_parent_task) else stringResource(R.string.no_matches),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 430.dp)
                            .verticalScroll(rememberScrollState())
                            .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard))
                            .graphicsLayer { clip = false },
                    ) {
                        hierarchy.entries.forEach { entry ->
                            AnimatedTaskHierarchyEntry(entry) {
                                TaskRow(
                                    task = entry.task,
                                    taskColorMode = taskColorMode,
                                    onTaskStatusChanged = { _, _ -> onSelected(entry.task.uid) },
                                    prominent = true,
                                    hierarchyDepth = entry.depth,
                                    hierarchyContinuationLevels = entry.continuationLevels,
                                    hierarchyLastSibling = entry.lastSibling,
                                    hasSubtasks = entry.hasChildren,
                                    subtasksExpanded = entry.expanded,
                                    onToggleSubtasks = { hierarchy.toggle(entry.task) },
                                    outerHorizontalPadding = 0.dp,
                                    outerVerticalPadding = 3.dp,
                                    connectorStemInset = TaskHierarchyStemInset,
                                    onClick = { onSelected(entry.task.uid) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun List<TaskEntity>.filterForParentPicker(query: String): List<TaskEntity> {
    val normalized = query.trim().lowercase(Locale.ROOT)
    if (normalized.isBlank()) return this
    val parents = TaskParentLookup(this)
    val included = mutableSetOf<String>()

    fun includeWithAncestors(task: TaskEntity) {
        parents.selfAndAncestors(task).forEach { included += it.resourceHref }
    }

    fun TaskEntity.matchesSearch(): Boolean {
        return listOf(title, notes.orEmpty(), categories.orEmpty(), location.orEmpty())
            .any { it.lowercase(Locale.ROOT).contains(normalized) }
    }

    filter { it.matchesSearch() }.forEach(::includeWithAncestors)
    return filter { it.resourceHref in included }
}

internal data class TaskHierarchySubset(
    val tasks: List<TaskEntity>,
    val defaultExpandedResourceHrefs: Set<String> = emptySet(),
)

internal fun List<TaskEntity>.searchHierarchySubsetForMatches(matches: List<TaskEntity>): TaskHierarchySubset {
    if (matches.isEmpty()) return TaskHierarchySubset(emptyList())
    val base = (this + matches).distinctBy { it.resourceHref }
    val byResource = base.associateBy { it.resourceHref }
    val parents = TaskParentLookup(base)
    val childrenByParent = base
        .filter { !it.parentUid.isNullOrBlank() }
        .groupBy { it.collectionHref to it.parentUid.orEmpty() }
    val included = linkedSetOf<String>()
    val expanded = linkedSetOf<String>()

    fun includeDescendants(task: TaskEntity) {
        val queue = ArrayDeque<TaskEntity>()
        val seen = mutableSetOf<String>()
        queue.add(task)
        while (queue.isNotEmpty()) {
            val cursor = queue.removeFirst()
            if (!seen.add(cursor.resourceHref)) continue
            included += cursor.resourceHref
            childrenByParent[cursor.collectionHref to cursor.uid].orEmpty().forEach(queue::add)
        }
    }

    matches.forEach { rawMatch ->
        val match = byResource[rawMatch.resourceHref] ?: rawMatch
        includeDescendants(parents.rootOf(match))
        parents.selfAndAncestors(match).drop(1).forEach { expanded += it.resourceHref }
    }
    return TaskHierarchySubset(
        tasks = base.filter { it.resourceHref in included },
        defaultExpandedResourceHrefs = expanded,
    )
}

internal fun List<TaskEntity>.chainSubsetForTargets(
    targets: List<TaskEntity>,
    includeTargetDescendants: Boolean,
): List<TaskEntity> {
    if (targets.isEmpty()) return emptyList()
    val base = (this + targets).distinctBy { it.resourceHref }
    val byResource = base.associateBy { it.resourceHref }
    val parents = TaskParentLookup(base)
    val childrenByParent = base
        .filter { !it.parentUid.isNullOrBlank() }
        .groupBy { it.collectionHref to it.parentUid.orEmpty() }
    val included = linkedSetOf<String>()

    fun includeAncestors(task: TaskEntity) {
        parents.selfAndAncestors(task).forEach { included += it.resourceHref }
    }

    fun includeDescendants(task: TaskEntity) {
        val queue = ArrayDeque<TaskEntity>()
        queue.add(task)
        val seen = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            val cursor = queue.removeFirst()
            if (!seen.add(cursor.resourceHref)) continue
            included += cursor.resourceHref
            childrenByParent[cursor.collectionHref to cursor.uid].orEmpty().forEach(queue::add)
        }
    }

    targets.forEach { rawTarget ->
        val target = byResource[rawTarget.resourceHref] ?: rawTarget
        includeAncestors(target)
        if (includeTargetDescendants) includeDescendants(target)
    }
    return base.filter { it.resourceHref in included }
}

internal fun List<TaskEntity>.descendantUids(task: TaskEntity): Set<String> {
    val childrenByParent = filter { it.collectionHref == task.collectionHref && !it.parentUid.isNullOrBlank() }
        .groupBy { it.parentUid }
    val result = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    queue.add(task.uid)
    while (queue.isNotEmpty()) {
        val parent = queue.removeFirst()
        childrenByParent[parent].orEmpty().forEach { child ->
            if (result.add(child.uid)) queue.add(child.uid)
        }
    }
    return result
}

internal fun List<TaskEntity>.descendantsOf(task: TaskEntity): List<TaskEntity> {
    val childrenByParent = filter { it.collectionHref == task.collectionHref && !it.parentUid.isNullOrBlank() }
        .groupBy { it.parentUid }
    val result = mutableListOf<TaskEntity>()
    val seen = mutableSetOf(task.uid)

    fun append(parentUid: String) {
        childrenByParent[parentUid].orEmpty().forEach { child ->
            if (seen.add(child.uid)) {
                result += child
                append(child.uid)
            }
        }
    }

    append(task.uid)
    return result
}

internal data class TaskRootActivityPartition(
    val activeRootTasks: List<TaskEntity>,
    val inactiveRootTasks: List<TaskEntity>,
)

internal fun List<TaskEntity>.partitionByRootActivity(): TaskRootActivityPartition {
    val distinctTasks = distinctBy { it.resourceHref }
    val parents = TaskParentLookup(distinctTasks)
    val tasksByRoot = distinctTasks.groupBy { parents.rootOf(it).resourceHref }
    val fullyInactiveRoots = tasksByRoot
        .filterValues { rootTasks -> rootTasks.isNotEmpty() && rootTasks.all { it.isInactive() } }
        .keys
    val active = tasksByRoot
        .filterKeys { it !in fullyInactiveRoots }
        .values
        .flatten()
    val inactive = distinctTasks.filter { it.isInactive() }
    return TaskRootActivityPartition(
        activeRootTasks = active,
        inactiveRootTasks = inactive,
    )
}

internal fun List<TaskEntity>.partitionByRootSchedule(): Pair<List<TaskEntity>, List<TaskEntity>> {
    val byCollectionUid = associateBy { it.collectionHref to it.uid }

    fun rootOf(task: TaskEntity): TaskEntity {
        var current = task
        val seen = mutableSetOf(task.resourceHref)
        while (!current.parentUid.isNullOrBlank()) {
            val parent = byCollectionUid[current.collectionHref to current.parentUid] ?: break
            if (!seen.add(parent.resourceHref)) break
            current = parent
        }
        return current
    }

    return partition { rootOf(it).taskDate() == null }
}

@Composable
internal fun TaskScheduleRow(
    label: String,
    addLabel: String,
    removeLabel: String,
    timeLabel: String,
    hasDate: Boolean,
    onHasDateChange: (Boolean) -> Unit,
    dateText: String,
    onDateTextChange: (String) -> Unit,
    hasTime: Boolean,
    timeText: String,
    onTimeTextChange: (String) -> Unit,
    allDay: Boolean,
    isError: Boolean = false,
) {
    AnimatedContent(
        targetState = hasDate,
        transitionSpec = {
            (fadeIn(tween(MotionMedium, easing = MotionStandard)) + expandVertically(tween(MotionMedium, easing = MotionStandard))) togetherWith
                (fadeOut(tween(MotionShort, easing = MotionStandardAccelerate)) + shrinkVertically(tween(MotionShort, easing = MotionStandardAccelerate)))
        },
        label = "taskScheduleRow-$label",
    ) { dateVisible ->
        if (!dateVisible) {
            AddEditorValueRow(addLabel) { onHasDateChange(true) }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DatePickerField(
                    value = dateText,
                    onValueChange = onDateTextChange,
                    label = { Text(label) },
                    isError = isError,
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(
                    visible = !allDay && hasTime,
                    enter = fadeIn(tween(MotionMedium, easing = MotionStandard)) +
                        expandHorizontally(tween(MotionMedium, easing = MotionStandard), expandFrom = Alignment.Start),
                    exit = fadeOut(tween(MotionShort, easing = MotionStandardAccelerate)) +
                        shrinkHorizontally(tween(MotionShort, easing = MotionStandardAccelerate), shrinkTowards = Alignment.Start),
                ) {
                    TimePickerField(
                        value = timeText,
                        onValueChange = onTimeTextChange,
                        label = { Text(timeLabel) },
                        isError = isError,
                        modifier = Modifier.width(104.dp),
                    )
                }
                IconButton(
                    onClick = { onHasDateChange(false) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = removeLabel, tint = WarmInk, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
internal fun AddEditorValueRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(UniversalControlHeight)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, WarmLine, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.Add, contentDescription = label, tint = WarmBrown, modifier = Modifier.size(22.dp))
        Text(label, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp)
    }
}

@Composable
internal fun TagEditor(
    label: String,
    tags: List<String>,
    suggestions: List<String>,
    onTagsChanged: (List<String>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val availableSuggestions = remember(tags, suggestions, normalizedQuery) {
        suggestions
            .filterNot { existing -> tags.any { it.equals(existing, ignoreCase = true) } }
            .filter { normalizedQuery.isBlank() || it.contains(normalizedQuery, ignoreCase = true) }
            .take(6)
    }
    fun addTag(value: String) {
        val cleaned = value.trim().trim(',')
        if (cleaned.isBlank() || tags.any { it.equals(cleaned, ignoreCase = true) }) return
        onTagsChanged(tags + cleaned)
        query = ""
    }
    EditorSection {
        Text(label, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
        if (tags.isNotEmpty()) {
            FadedHorizontalScrollRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                tags.forEach { tag ->
                    TagPill(
                        tag = tag,
                        onRemove = { onTagsChanged(tags.filterNot { it == tag }) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.add_tag)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { addTag(query) },
                enabled = normalizedQuery.isNotBlank(),
                modifier = Modifier.size(UniversalControlHeight),
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_tag), tint = WarmBrown)
            }
        }
        if (availableSuggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                availableSuggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { addTag(suggestion) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(18.dp))
                        Text(suggestion, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TagPill(tag: String, onRemove: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = WarmPeach.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, WarmLine.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 11.dp, end = if (onRemove == null) 11.dp else 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(tag, color = WarmInk, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1)
            onRemove?.let {
                IconButton(onClick = it, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_tag, tag), tint = WarmInk, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
internal fun CategoryDetailLine(label: String, categories: String) {
    val tags = remember(categories) { categories.toCategoryTags() }
    if (tags.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
            FadedHorizontalScrollRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                tags.forEach { TagPill(it) }
            }
        }
    }
}
