@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.kgs.calendar.ui.components.ClearTextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.key.key
import androidx.compose.ui.zIndex
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.data.search.CalendarSearchMode
import com.kgs.calendar.domain.event.isTentative
import com.kgs.calendar.domain.task.isInactive
import com.kgs.calendar.domain.time.toDate
import com.kgs.calendar.ui.layout.ListContentMaxWidth
import com.kgs.calendar.ui.layout.centeredContentPadding
import com.kgs.calendar.ui.layout.centeredMaxWidth
import com.kgs.calendar.ui.layout.currentCalendarWindowLayout
import com.kgs.calendar.ui.layout.largeScreenMaxWidth
import com.kgs.calendar.ui.agenda.AgendaTimelineEntry
import com.kgs.calendar.ui.agenda.AgendaTimelinePlanner
import com.kgs.calendar.ui.agenda.AgendaTimelineRow
import com.kgs.calendar.ui.model.agendaSortMillis
import com.kgs.calendar.ui.model.agendaEventDateSpans
import com.kgs.calendar.ui.model.taskDate
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.kgs.calendar.ui.theme.calendarTextScale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kgs.calendar.data.local.entity.CollectionEntity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Sell
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.semantics.selected

@Composable
internal fun CalendarSearchOverlay(
    visible: Boolean,
    query: String,
    searchMode: CalendarSearchMode,
    results: List<EventEntity>,
    taskResults: List<TaskEntity>,
    allTasksForHierarchy: List<TaskEntity>,
    taskColorMode: TaskColorMode,
    subtasksExpandedByDefault: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchModeChange: (CalendarSearchMode) -> Unit,
    calendars: List<CollectionEntity>,
    selectedCalendars: Set<String>?,
    onSelectedCalendarsChange: (Set<String>?) -> Unit,
    onLoadEarlierOccurrences: () -> Unit,
    onLoadLaterOccurrences: () -> Unit,
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onClose: () -> Unit,
    showCalendarWeeks: Boolean = false,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
) {
    // The page whites out the app with a fade, while the header slides down from the top like a
    // sheet; results scroll on underneath the header.
    AnimatedVisibility(
        visible = visible,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
    ) {
        val density = LocalDensity.current
        var headerHeightPx by remember { mutableIntStateOf(0) }
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .animateEnterExit(
                        enter = fadeIn(tween(MotionMedium, easing = MotionStandard)),
                        exit = fadeOut(tween(MotionMedium, easing = MotionStandardAccelerate)),
                    )
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                SearchResultsList(
                    query = query,
                    eventResults = results,
                    taskResults = taskResults,
                    allTasksForHierarchy = allTasksForHierarchy,
                    taskColorMode = taskColorMode,
                    subtasksExpandedByDefault = subtasksExpandedByDefault,
                    onEventClick = onEventClick,
                    onTaskClick = onTaskClick,
                    onTaskStatusChanged = onTaskStatusChanged,
                    onLoadEarlierOccurrences = onLoadEarlierOccurrences,
                    onLoadLaterOccurrences = onLoadLaterOccurrences,
                    agendaDateHierarchy = true,
                    showCalendarWeeks = showCalendarWeeks,
                    firstDayOfWeek = firstDayOfWeek,
                    agendaHeaderTopSpacing = 6.dp,
                    topInset = with(density) { headerHeightPx.toDp() },
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateEnterExit(
                        enter = slideInVertically(tween(MotionLong, easing = MotionEmphasized)) { -it } +
                            fadeIn(tween(MotionShort, easing = MotionStandard)),
                        exit = slideOutVertically(tween(MotionMedium, easing = MotionStandardAccelerate)) { -it } +
                            fadeOut(tween(MotionMedium, easing = MotionStandardAccelerate)),
                    )
                    .onSizeChanged { headerHeightPx = it.height }
                    .shadow(10.dp, SearchHeaderShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
                    .background(MaterialTheme.colorScheme.background, SearchHeaderShape)
                    .testTag("search-header"),
            ) {
                SearchTopBar(query = query, onQueryChange = onQueryChange, onClose = onClose)
                SearchOptionsBar(
                    searchMode = searchMode,
                    onSearchModeChange = onSearchModeChange,
                    calendars = calendars,
                    selectedCalendars = selectedCalendars,
                    onSelectedCalendarsChange = onSelectedCalendarsChange,
                )
            }
        }
    }
}

private val SearchHeaderShape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)

/** The order the search modes are offered in: text and labels first, as the most used. */
private val SearchModeOrder = listOf(CalendarSearchMode.TextAndLabels, CalendarSearchMode.Text, CalendarSearchMode.LabelsOnly)

private fun CalendarSearchMode.icon(): ImageVector = when (this) {
    CalendarSearchMode.TextAndLabels -> Icons.AutoMirrored.Filled.ManageSearch
    CalendarSearchMode.Text -> Icons.Default.TextFields
    CalendarSearchMode.LabelsOnly -> Icons.Default.Sell
}

/**
 * Two chips under the search field: what to search in (text, labels or both) and which calendars,
 * each opening a menu. Calendars can be picked several at a time.
 */
@Composable
private fun SearchOptionsBar(
    searchMode: CalendarSearchMode,
    onSearchModeChange: (CalendarSearchMode) -> Unit,
    calendars: List<CollectionEntity>,
    selectedCalendars: Set<String>?,
    onSelectedCalendarsChange: (Set<String>?) -> Unit,
) {
    val labels = mapOf(
        CalendarSearchMode.Text to stringResource(R.string.search_scope_text),
        CalendarSearchMode.TextAndLabels to stringResource(R.string.search_scope_text_and_labels),
        CalendarSearchMode.LabelsOnly to stringResource(R.string.search_scope_labels_only),
    )
    // A filter keeps only calendars that still exist; none left, or all of them, means all.
    val calendarHrefs = calendars.mapTo(LinkedHashSet()) { it.href }
    val activeCalendars = selectedCalendars?.filterTo(LinkedHashSet()) { it in calendarHrefs }?.takeIf { it.isNotEmpty() && it.size < calendarHrefs.size }
    val pickedCalendars = calendars.filter { activeCalendars != null && it.href in activeCalendars }
    var modeMenuOpen by remember { mutableStateOf(false) }
    var calendarMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .testTag("search-options-bar")
            .fillMaxWidth()
            .centeredMaxWidth(largeScreenMaxWidth(ListContentMaxWidth))
            .horizontalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            SearchFilterChip(
                label = labels.getValue(searchMode),
                contentDescription = stringResource(R.string.search_in),
                highlighted = searchMode != CalendarSearchMode.TextAndLabels,
                open = modeMenuOpen,
                onClick = { modeMenuOpen = true },
                leading = { tint -> Icon(searchMode.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("search-mode-button"),
            )
            SearchFilterMenu(expanded = modeMenuOpen, onDismiss = { modeMenuOpen = false }) {
                SearchModeOrder.forEach { mode ->
                    SearchMenuRow(
                        label = labels.getValue(mode),
                        selected = mode == searchMode,
                        onClick = {
                            onSearchModeChange(mode)
                            modeMenuOpen = false
                        },
                        leading = { selected ->
                            Icon(
                                mode.icon(),
                                contentDescription = null,
                                tint = if (selected) WarmBrown else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        modifier = Modifier.testTag("searchMode-${mode.name}"),
                    )
                }
            }
        }
        if (calendars.size > 1) {
            Box {
                SearchFilterChip(
                    label = when {
                        activeCalendars == null -> stringResource(R.string.search_all_calendars)
                        pickedCalendars.size == 1 -> pickedCalendars.single().displayName
                        else -> pluralStringResource(R.plurals.settings_calendar_count, pickedCalendars.size, pickedCalendars.size)
                    },
                    contentDescription = stringResource(R.string.search_calendars),
                    highlighted = activeCalendars != null,
                    open = calendarMenuOpen,
                    onClick = { calendarMenuOpen = true },
                    leading = { tint ->
                        if (pickedCalendars.isEmpty()) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                        } else {
                            StackedCalendarDots(pickedCalendars.map { Color(it.color) })
                        }
                    },
                    modifier = Modifier.testTag("search-calendars-button"),
                )
                SearchFilterMenu(expanded = calendarMenuOpen, onDismiss = { calendarMenuOpen = false }) {
                    SearchMenuRow(
                        label = stringResource(R.string.search_all_calendars),
                        selected = activeCalendars == null,
                        onClick = { onSelectedCalendarsChange(null) },
                        leading = { selected ->
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = if (selected) WarmBrown else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        modifier = Modifier.testTag("search-calendar-all"),
                    )
                    HorizontalDivider(color = WarmLine.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
                    calendars.forEach { calendar ->
                        SearchMenuRow(
                            label = calendar.displayName,
                            selected = activeCalendars != null && calendar.href in activeCalendars,
                            onClick = {
                                onSelectedCalendarsChange(toggledSearchCalendars(activeCalendars, calendar.href, calendarHrefs))
                            },
                            leading = { selected -> CalendarColorDot(Color(calendar.color), filled = selected || activeCalendars == null) },
                            modifier = Modifier.testTag("search-calendar-${calendar.href}"),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The calendars searched after tapping [href] in the calendar menu; null means all. From all
 * calendars, a tap searches only that one; further taps add or leave out calendars. Leaving out
 * the last one, or picking every calendar, means all again.
 */
internal fun toggledSearchCalendars(selected: Set<String>?, href: String, all: Set<String>): Set<String>? {
    if (selected == null) return setOf(href).takeIf { all.size > 1 }
    val next = if (href in selected) selected - href else selected + href
    return next.takeIf { it.isNotEmpty() && !it.containsAll(all) }
}

/** A chip of the search header: an icon or calendar colors, a label and a turning chevron. */
@Composable
private fun SearchFilterChip(
    label: String,
    contentDescription: String,
    highlighted: Boolean,
    open: Boolean,
    onClick: () -> Unit,
    leading: @Composable (tint: Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    // White chips sit lightly on the tinted header; a chip that narrows the search takes the accent.
    val container by animateColorAsState(
        targetValue = if (highlighted) WarmBrown.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "searchChipContainer",
    )
    val iconTint by animateColorAsState(
        targetValue = if (highlighted) WarmBrown else WarmInk,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "searchChipIcon",
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        label = "searchChipChevron",
    )
    Row(
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(container)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = "$contentDescription: $label" }
            .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp)
            .animateContentSize(tween(MotionMedium, easing = MotionStandard)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        leading(iconTint)
        Text(label, color = WarmInk, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = chevronRotation },
        )
    }
}

/** Up to four calendar colors, overlapping like a little stack; more show as "+n". */
@Composable
private fun StackedCalendarDots(colors: List<Color>) {
    val ring = MaterialTheme.colorScheme.surface
    val shown = colors.take(4)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            shown.forEachIndexed { index, color ->
                Box(
                    Modifier
                        .offset(x = (index * 9).dp)
                        .size(16.dp)
                        .border(1.5.dp, ring, CircleShape)
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
            // Reserves the width of the stack.
            Spacer(Modifier.width((16 + (shown.size - 1) * 9).dp).height(16.dp))
        }
        if (colors.size > shown.size) {
            Text("+${colors.size - shown.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 14.sp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** A calendar's color as a circle: filled when the calendar is searched, a ring when it is not. */
@Composable
private fun CalendarColorDot(color: Color, filled: Boolean) {
    val fill by animateColorAsState(
        targetValue = if (filled) color else Color.Transparent,
        animationSpec = tween(MotionShort, easing = MotionStandard),
        label = "calendarDotFill",
    )
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .border(2.dp, color, CircleShape)
            .background(fill),
    )
}

/**
 * The menu of a search chip. It is as wide as its longest row, up to the screen's width, so
 * long calendar names fit.
 */
@Composable
private fun SearchFilterMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val maxWidth = currentCalendarWindowLayout().widthDp.dp - 32.dp
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = popupSurfaceColor(),
        shadowElevation = 10.dp,
        offset = DpOffset(0.dp, 6.dp),
        modifier = Modifier
            .widthIn(min = 200.dp, max = maxWidth)
            .heightIn(max = 460.dp),
        content = content,
    )
}

/**
 * A row of a search chip's menu. The picked rows sit on a soft accent square and their icon takes
 * the accent, instead of a check mark.
 */
@Composable
private fun SearchMenuRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable (selected: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = if (selected) WarmBrown.copy(alpha = 0.13f) else Color.Transparent,
        animationSpec = tween(MotionShort, easing = MotionStandard),
        label = "searchMenuRow",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading(selected)
        Text(label, color = WarmInk, fontSize = 15.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var editorValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = query,
                selection = TextRange(query.length),
            ),
        )
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    LaunchedEffect(query, focused) {
        if (!focused && query.isEmpty() && editorValue.text.isNotEmpty()) {
            editorValue = TextFieldValue("")
        }
    }
    LaunchedEffect(editorValue.text) {
        delay(searchTypingDelayMillis(editorValue.text))
        if (editorValue.text != query) {
            onQueryChange(editorValue.text)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusTop + 62.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(top = statusTop)
            .centeredMaxWidth(largeScreenMaxWidth(ListContentMaxWidth))
            .padding(start = 16.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = WarmInk, modifier = Modifier.size(25.dp))
        }
        BasicTextField(
            value = editorValue,
            onValueChange = { changed ->
                editorValue = changed
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                color = WarmInk,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Normal,
            ),
            modifier = Modifier
                .testTag("search-query-field")
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
            cursorBrush = SolidColor(WarmBrown),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (editorValue.text.isBlank() && !focused) {
                        Text(stringResource(R.string.search), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp, lineHeight = 26.sp)
                    }
                    innerTextField()
                }
            },
        )
        if (editorValue.text.isNotEmpty()) {
            ClearTextButton(
                modifier = Modifier.size(38.dp),
                onClick = {
                    editorValue = TextFieldValue("")
                    onQueryChange("")
                    focusManager.clearFocus(force = true)
                    keyboard?.hide()
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SearchResultsList(
    query: String,
    eventResults: List<EventEntity>,
    taskResults: List<TaskEntity>,
    allTasksForHierarchy: List<TaskEntity> = taskResults,
    taskColorMode: TaskColorMode,
    subtasksExpandedByDefault: Boolean,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    showSearchIntro: Boolean = true,
    autoScrollToNow: Boolean = query.isNotBlank() || !showSearchIntro,
    emptyMessage: String? = null,
    scrollRequestKey: Int = 0,
    scrollTargetDate: LocalDate? = null,
    stickyHeaderBackground: Color = MaterialTheme.colorScheme.surface,
    showTaskChains: Boolean = true,
    expandMultiDayEventSpans: Boolean = false,
    onLoadEarlierOccurrences: (() -> Unit)? = null,
    onLoadLaterOccurrences: (() -> Unit)? = null,
    agendaDateHierarchy: Boolean = false,
    showCalendarWeeks: Boolean = false,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    compositionObserver: (() -> Unit)? = null,
    agendaHeaderTopSpacing: Dp = 0.dp,
    // Room for a header floating above the list; the results scroll on underneath it.
    topInset: Dp = 0.dp,
) {
    SideEffect { compositionObserver?.invoke() }
    val resolvedEmptyMessage = emptyMessage ?: stringResource(R.string.no_results)
    val noDateLabel = stringResource(R.string.no_date)
    val recurringTaskResults = remember(taskResults, showTaskChains) {
        if (!showTaskChains) {
            emptyList()
        } else {
            taskResults.filter { !it.recurrenceRule.isNullOrBlank() || !it.rDatesCsv.isNullOrBlank() }
        }
    }
    val hierarchyTaskResults = remember(taskResults, recurringTaskResults, showTaskChains) {
        if (!showTaskChains) {
            emptyList()
        } else {
            val recurringTasks = recurringTaskResults.toHashSet()
            taskResults.filterNot(recurringTasks::contains)
        }
    }
    val hierarchySubset = remember(query, showSearchIntro, showTaskChains, hierarchyTaskResults, allTasksForHierarchy) {
        if (!showTaskChains) {
            TaskHierarchySubset(emptyList())
        } else {
            when {
                query.isNotBlank() -> allTasksForHierarchy.searchHierarchySubsetForMatches(hierarchyTaskResults)
                !showSearchIntro -> TaskHierarchySubset(
                    tasks = allTasksForHierarchy.chainSubsetForTargets(
                        targets = hierarchyTaskResults,
                        includeTargetDescendants = true,
                    ),
                )
                else -> TaskHierarchySubset(tasks = hierarchyTaskResults)
            }
        }
    }
    val hierarchyExpandedByDefault = when {
        query.isNotBlank() -> false
        !showSearchIntro -> true
        else -> subtasksExpandedByDefault
    }
    val taskHierarchy = rememberTaskHierarchyPresentation(
        tasks = hierarchySubset.tasks,
        expandedByDefault = hierarchyExpandedByDefault,
        defaultExpandedResourceHrefs = hierarchySubset.defaultExpandedResourceHrefs,
    )
    val taskGroups = remember(taskHierarchy.entries) {
        buildList {
            var current = mutableListOf<TaskHierarchyEntry>()
            taskHierarchy.entries.forEach { entry ->
                if (entry.depth == 0 && current.isNotEmpty()) {
                    add(CalendarSearchResult.TaskGroup(current.toList()))
                    current = mutableListOf()
                }
                current += entry
            }
            if (current.isNotEmpty()) add(CalendarSearchResult.TaskGroup(current.toList()))
        }
    }
    val flatTaskItems = remember(showTaskChains, taskResults, recurringTaskResults) {
        val flatTasks = if (showTaskChains) recurringTaskResults else taskResults
        flatTasks
            .filter { it.agendaSortMillis() != null }
            .map { CalendarSearchResult.TaskItem(it) }
    }
    val eventItems = remember(eventResults, taskResults, expandMultiDayEventSpans) {
        if (expandMultiDayEventSpans) {
            buildAgendaEventResults(eventResults, taskResults)
        } else {
            eventResults.map { CalendarSearchResult.Event(it) }
        }
    }
    val results = remember(eventItems, taskGroups, flatTaskItems) {
        (eventItems + taskGroups + flatTaskItems)
            .sortedWith(
                compareBy<CalendarSearchResult> { it.sortMillis == null }
                    .thenBy { it.sortMillis ?: Long.MAX_VALUE },
            )
    }
    val groupedResults = remember(results, agendaDateHierarchy) {
        results.groupBy { item ->
            if (agendaDateHierarchy) {
                item.date?.let(::agendaDayGroupKey) ?: noDateLabel
            } else {
                item.date?.year?.toString() ?: noDateLabel
            }
        }
    }
    val today = LocalCalendarTimeSnapshot.current.today
    val agendaPlan = remember(
        results,
        agendaDateHierarchy,
        showCalendarWeeks,
        firstDayOfWeek,
        today,
        scrollTargetDate,
    ) {
        if (agendaDateHierarchy) {
            AgendaTimelinePlanner.plan(
                entries = results.map { result ->
                    AgendaTimelineEntry(
                        key = result.stableKey(),
                        date = result.date,
                        sortMillis = result.sortMillis,
                        value = result,
                    )
                },
                today = today,
                firstDayOfWeek = firstDayOfWeek,
                showCalendarWeeks = showCalendarWeeks,
                requiredAnchorDate = scrollTargetDate,
            )
        } else {
            null
        }
    }
    val agendaRows = agendaPlan?.rows.orEmpty()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val firstFutureKey = remember(results, today) {
        results.firstOrNull { it.isFutureSearchSection(today) }?.stableKey()
    }
    var initialAutoScrollHandled by remember { mutableStateOf(false) }
    var lastHandledScrollRequestKey by remember { mutableStateOf(scrollRequestKey) }
    val firstFutureLazyIndex = remember(
        agendaRows,
        groupedResults,
        firstFutureKey,
        today,
        agendaDateHierarchy,
        showCalendarWeeks,
    ) {
        if (firstFutureKey == null) {
            null
        } else if (agendaDateHierarchy) {
            agendaRows.indexOfFirst { row ->
                row is AgendaTimelineRow.Entry && row.entry.key == firstFutureKey
            }.takeIf { it >= 0 }
        } else {
            var lazyIndex = 0
            var found: Int? = null
            var dividerInserted = false
            for (groupItems in groupedResults.values) {
                if (found != null) break
                lazyIndex += 1 // sticky year header
                for (item in groupItems) {
                    if (!dividerInserted && item.isFutureSearchSection(today)) {
                        lazyIndex++ // past/future divider
                        dividerInserted = true
                    }
                    if (item.stableKey() == firstFutureKey) {
                        found = lazyIndex
                        break
                    }
                    lazyIndex++
                }
            }
            found
        }
    }
    val requestedDateLazyIndex = remember(
        agendaRows,
        groupedResults,
        scrollTargetDate,
        showSearchIntro,
        query,
        today,
        agendaDateHierarchy,
        showCalendarWeeks,
    ) {
        val targetDate = scrollTargetDate ?: return@remember null
        if (agendaDateHierarchy) {
            return@remember agendaPlan?.anchorsByDate?.get(targetDate)?.rowIndex
        }
        var lazyIndex = 0
        var found: Int? = null
        var dividerInserted = false
        for (groupItems in groupedResults.values) {
            if (found != null) break
            lazyIndex += 1 // sticky year header
            for (item in groupItems) {
                if (!dividerInserted && item.isFutureSearchSection(today)) {
                    lazyIndex++ // past/future divider
                    dividerInserted = true
                }
                val itemDate = item.date
                if (itemDate != null && !itemDate.isBefore(targetDate)) {
                    found = lazyIndex
                    break
                }
                lazyIndex++
            }
        }
        found
    }
    LaunchedEffect(query, firstFutureLazyIndex, requestedDateLazyIndex, scrollRequestKey) {
        if (autoScrollToNow) {
            val explicitAgendaRequest = !showSearchIntro && scrollRequestKey != lastHandledScrollRequestKey
            val requestedIndex = if (explicitAgendaRequest && requestedDateLazyIndex != null) requestedDateLazyIndex else null
            val baseTarget = requestedIndex ?: firstFutureLazyIndex
            if (baseTarget == null) return@LaunchedEffect
            val targetIndex = if (agendaDateHierarchy) {
                baseTarget
            } else {
                (baseTarget - 2).coerceAtLeast(0)
            }
            val targetOffset = if (agendaDateHierarchy || showSearchIntro) 0 else -with(density) { 38.dp.roundToPx() }
            val jumpBehavior = if (showSearchIntro) {
                AgendaJumpBehavior.Direct
            } else {
                agendaJumpBehavior(initialAutoScrollHandled, explicitAgendaRequest)
            }
            when (jumpBehavior) {
                AgendaJumpBehavior.Direct -> listState.scrollToItem(targetIndex, targetOffset)
                AgendaJumpBehavior.Animated -> listState.animateScrollToItem(targetIndex, targetOffset)
                AgendaJumpBehavior.None -> return@LaunchedEffect
            }
            initialAutoScrollHandled = true
            lastHandledScrollRequestKey = scrollRequestKey
        }
    }
    var searchGestureActive by remember(listState) { mutableStateOf(false) }
    var searchGestureSerial by remember(listState) { mutableStateOf(0) }
    var earlierRequestedGesture by remember(listState) { mutableStateOf(-1) }
    var laterRequestedGesture by remember(listState) { mutableStateOf(-1) }
    val latestGestureActive = rememberUpdatedState(searchGestureActive)
    val latestGestureSerial = rememberUpdatedState(searchGestureSerial)
    val latestEarlierRequest = rememberUpdatedState(onLoadEarlierOccurrences)
    val latestLaterRequest = rememberUpdatedState(onLoadLaterOccurrences)
    LaunchedEffect(listState, onLoadEarlierOccurrences, onLoadLaterOccurrences) {
        if (onLoadEarlierOccurrences == null && onLoadLaterOccurrences == null) return@LaunchedEffect
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    searchGestureSerial += 1
                    searchGestureActive = true
                }
                is DragInteraction.Cancel,
                is DragInteraction.Stop -> Unit // Keep the gesture armed through any fling; scroll settlement disarms it.
            }
        }
    }
    LaunchedEffect(listState, onLoadEarlierOccurrences, onLoadLaterOccurrences) {
        if (onLoadEarlierOccurrences == null && onLoadLaterOccurrences == null) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) searchGestureActive = false
        }
    }
    LaunchedEffect(listState, onLoadEarlierOccurrences, onLoadLaterOccurrences) {
        if (onLoadEarlierOccurrences == null && onLoadLaterOccurrences == null) return@LaunchedEffect
        var previousFirstIndex: Int? = null
        var previousFirstOffset = 0
        snapshotFlow {
            val layout = listState.layoutInfo
            SearchOccurrenceScrollSnapshot(
                firstVisibleItemIndex = layout.visibleItemsInfo.firstOrNull()?.index ?: 0,
                firstVisibleItemOffset = layout.visibleItemsInfo.firstOrNull()?.offset ?: 0,
                lastVisibleItemIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: 0,
                totalItemsCount = layout.totalItemsCount,
            )
        }.collect { snapshot ->
            val priorIndex = previousFirstIndex
            val movingTowardLater = priorIndex != null && (
                snapshot.firstVisibleItemIndex > priorIndex ||
                    (snapshot.firstVisibleItemIndex == priorIndex && snapshot.firstVisibleItemOffset < previousFirstOffset)
                )
            val movingTowardEarlier = priorIndex != null && (
                snapshot.firstVisibleItemIndex < priorIndex ||
                    (snapshot.firstVisibleItemIndex == priorIndex && snapshot.firstVisibleItemOffset > previousFirstOffset)
                )
            previousFirstIndex = snapshot.firstVisibleItemIndex
            previousFirstOffset = snapshot.firstVisibleItemOffset
            if (!movingTowardLater && !movingTowardEarlier) return@collect
            val gesture = latestGestureSerial.value
            val edge = SearchOccurrenceLoadingPolicy.edgeToExtend(
                firstVisibleItemIndex = snapshot.firstVisibleItemIndex,
                lastVisibleItemIndex = snapshot.lastVisibleItemIndex,
                totalItemsCount = snapshot.totalItemsCount,
                scrollingTowardLater = movingTowardLater,
                userGestureActive = latestGestureActive.value,
                edgeAlreadyRequestedForGesture = if (movingTowardLater) {
                    laterRequestedGesture == gesture
                } else {
                    earlierRequestedGesture == gesture
                },
            )
            when (edge) {
                SearchOccurrenceEdge.Earlier -> {
                    earlierRequestedGesture = gesture
                    latestEarlierRequest.value?.invoke()
                }
                SearchOccurrenceEdge.Later -> {
                    laterRequestedGesture = gesture
                    latestLaterRequest.value?.invoke()
                }
                null -> Unit
            }
        }
    }
    val agendaHeaderSignals = remember(
        agendaPlan,
        agendaDateHierarchy,
    ) {
        if (!agendaDateHierarchy) {
            AgendaHeaderSignals(emptyList(), emptyList(), emptyList(), emptyList())
        } else {
            agendaPlan?.headerSignals ?: AgendaHeaderSignals(emptyList(), emptyList(), emptyList(), emptyList())
        }
    }
    val firstAgendaDate = results.firstNotNullOfOrNull(CalendarSearchResult::date)
    val agendaDateStackHeight = resolvedAgendaDateStackHeight(density.fontScale)
    val agendaDayElementHeightPx = with(density) {
        resolvedAgendaDatePushDistance(density.fontScale).roundToPx()
    }
    val agendaInformationElementHeightPx = with(density) { 22.dp.roundToPx() }
    val agendaInformationContentTopInsetPx = with(density) { 5.dp.roundToPx() }
    val topInsetPx = with(density) { topInset.roundToPx() }
    val displayedAgendaHeaderViewport = remember(
        listState,
        agendaHeaderSignals,
        firstAgendaDate,
        agendaDayElementHeightPx,
        agendaInformationElementHeightPx,
        agendaInformationContentTopInsetPx,
        topInsetPx,
    ) {
        derivedStateOf {
            if (firstAgendaDate == null) return@derivedStateOf null
            val layout = listState.layoutInfo
            agendaStickyHeaderViewport(
                signals = agendaHeaderSignals,
                visibleItems = layout.visibleItemsInfo.map { itemInfo ->
                    AgendaVisibleHeaderItem(
                        key = itemInfo.key,
                        offset = itemInfo.offset,
                        lazyIndex = itemInfo.index,
                    )
                },
                firstVisibleItemIndex = layout.visibleItemsInfo.firstOrNull()?.index ?: 0,
                // Positions count from below a floating header, where the date overlay starts.
                viewportStartOffset = layout.viewportStartOffset + topInsetPx,
                informationContentTopInset = agendaInformationContentTopInsetPx,
                dayElementHeight = agendaDayElementHeightPx,
                informationElementHeight = agendaInformationElementHeightPx,
            )
        }
    }

    // The list spans the window so it scrolls from anywhere; its content stays centred on wide screens.
    val listSidePadding = centeredContentPadding(
        availableWidth = currentCalendarWindowLayout().widthDp.dp,
        maxWidth = largeScreenMaxWidth(ListContentMaxWidth),
        basePadding = 18.dp,
    )
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .testTag("search-results-list")
            .fillMaxSize(),
        contentPadding = PaddingValues(
            start = listSidePadding,
            top = topInset + if (agendaDateHierarchy) {
                agendaDateStackHeight + 16.dp + agendaHeaderTopSpacing
            } else {
                20.dp
            },
            end = listSidePadding,
            bottom = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (query.isBlank() && showSearchIntro) {
            item {
                Text(stringResource(R.string.search_events_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 62.dp, top = 18.dp))
            }
        } else {
            if (results.isEmpty()) {
                item {
                    Text(resolvedEmptyMessage, color = WarmInk, fontSize = 16.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 62.dp, top = 18.dp))
                }
            } else if (agendaDateHierarchy) {
                items(
                    items = agendaRows,
                    key = { row -> row.stableKey },
                    contentType = { row -> row::class },
                ) { row ->
                    when (row) {
                        is AgendaTimelineRow.Boundary -> AgendaBoundaryMarker(
                            date = row.date,
                            changes = row.changes,
                            showCalendarWeeks = showCalendarWeeks,
                            firstDayOfWeek = firstDayOfWeek,
                        )
                        is AgendaTimelineRow.DateAnchor -> Spacer(Modifier.fillMaxWidth().height(1.dp))
                        AgendaTimelineRow.PastFutureDivider -> SearchPastFutureDivider()
                        is AgendaTimelineRow.Entry -> CalendarSearchResultRow(
                            item = row.entry.value,
                            showDate = row.showDate,
                            taskColorMode = taskColorMode,
                            onTaskStatusChanged = onTaskStatusChanged,
                            agendaDateHierarchy = true,
                            showCalendarWeeks = showCalendarWeeks,
                            firstDayOfWeek = firstDayOfWeek,
                            taskHierarchy = taskHierarchy,
                            onEventClick = onEventClick,
                            onTaskClick = onTaskClick,
                        )
                        AgendaTimelineRow.Footer -> Spacer(Modifier.height(80.dp))
                    }
                }
            } else {
                var futureDividerInserted = false
                groupedResults.forEach { (group, groupItems) ->
                    stickyHeader(key = "search-year-$group") {
                        SearchGroupHeader(group, stickyHeaderBackground)
                    }
                    var lastDateKey: String? = null
                    groupItems.forEach { item ->
                        val dateKey = item.date?.toString() ?: "__no_date__"
                        val showDate = dateKey != lastDateKey
                        if (!futureDividerInserted && item.isFutureSearchSection(today)) {
                            item(key = "search-past-future-divider") {
                                SearchPastFutureDivider()
                            }
                            futureDividerInserted = true
                        }
                        item(key = item.stableKey()) {
                            CalendarSearchResultRow(
                                item = item,
                                showDate = showDate,
                                taskColorMode = taskColorMode,
                                onTaskStatusChanged = onTaskStatusChanged,
                                agendaDateHierarchy = false,
                                showCalendarWeeks = showCalendarWeeks,
                                firstDayOfWeek = firstDayOfWeek,
                                taskHierarchy = taskHierarchy,
                                onEventClick = onEventClick,
                                onTaskClick = onTaskClick,
                            )
                        }
                        lastDateKey = dateKey
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
    if (agendaDateHierarchy) {
        AgendaDynamicTopHeaderState(
            viewportState = displayedAgendaHeaderViewport,
            today = today,
            showCalendarWeeks = showCalendarWeeks,
            firstDayOfWeek = firstDayOfWeek,
            background = stickyHeaderBackground,
            topSpacing = agendaHeaderTopSpacing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topInset)
                .centeredMaxWidth(largeScreenMaxWidth(ListContentMaxWidth)),
        )
    }
    }
}

@Composable
internal fun AgendaDynamicTopHeaderState(
    viewportState: State<AgendaStickyHeaderViewport?>,
    today: LocalDate,
    showCalendarWeeks: Boolean,
    firstDayOfWeek: DayOfWeek,
    background: Color,
    topSpacing: Dp,
    modifier: Modifier = Modifier,
) {
    val viewport = viewportState.value ?: return
    AgendaDynamicTopHeader(
        viewport = viewport,
        today = today,
        showCalendarWeeks = showCalendarWeeks,
        firstDayOfWeek = firstDayOfWeek,
        background = background,
        topSpacing = topSpacing,
        modifier = modifier,
    )
}

private data class SearchOccurrenceScrollSnapshot(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemOffset: Int,
    val lastVisibleItemIndex: Int,
    val totalItemsCount: Int,
)

internal enum class AgendaJumpBehavior {
    Direct,
    Animated,
    None,
}

internal fun agendaJumpBehavior(
    initialAutoScrollHandled: Boolean,
    explicitAgendaRequest: Boolean,
): AgendaJumpBehavior = when {
    explicitAgendaRequest -> AgendaJumpBehavior.Direct
    !initialAutoScrollHandled -> AgendaJumpBehavior.Direct
    else -> AgendaJumpBehavior.None
}

internal sealed interface CalendarSearchResult {
    val sortMillis: Long?
    val date: LocalDate?

    data class Event(
        val event: EventEntity,
        override val date: LocalDate = event.startsAtMillis.toDate(),
        val spanEndDate: LocalDate = date,
    ) : CalendarSearchResult {
        override val sortMillis: Long = if (date == event.startsAtMillis.toDate()) {
            event.startsAtMillis
        } else {
            date.startOfDayMillis()
        }
    }

    data class TaskGroup(val entries: List<TaskHierarchyEntry>) : CalendarSearchResult {
        private val root: TaskEntity = entries.first().task
        override val sortMillis: Long? = entries
            .mapNotNull { it.task.startAtMillis ?: it.task.dueAtMillis }
            .minOrNull()
            ?: root.startAtMillis
            ?: root.dueAtMillis
        override val date: LocalDate? = sortMillis?.toDate()
    }

    data class TaskItem(val task: TaskEntity) : CalendarSearchResult {
        override val sortMillis: Long? = task.agendaSortMillis()
        override val date: LocalDate? = sortMillis?.toDate()
    }
}

internal fun CalendarSearchResult.stableKey(): String = when (this) {
    is CalendarSearchResult.Event -> "event-${event.resourceHref}-${event.startsAtMillis}-$date-$spanEndDate"
    is CalendarSearchResult.TaskGroup -> "task-group-${entries.first().task.resourceHref}"
    is CalendarSearchResult.TaskItem -> "task-${task.resourceHref}-${task.agendaSortMillis() ?: 0L}"
}

internal fun CalendarSearchResult.isFutureSearchSection(today: LocalDate): Boolean =
    date?.isBefore(today) != true

@Composable
internal fun CalendarSearchResultRow(
    item: CalendarSearchResult,
    showDate: Boolean,
    taskColorMode: TaskColorMode,
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    agendaDateHierarchy: Boolean,
    showCalendarWeeks: Boolean,
    firstDayOfWeek: DayOfWeek,
    taskHierarchy: TaskHierarchyPresentation,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    taskStatusToggleEnabled: Boolean = true,
    mutePastEvents: Boolean = true,
) {
    when (item) {
        is CalendarSearchResult.Event -> SearchResultCard(
            event = item.event,
            displayDate = item.date,
            spanEndDate = item.spanEndDate,
            showDate = showDate,
            agendaDateHierarchy = agendaDateHierarchy,
            showCalendarWeeks = showCalendarWeeks,
            firstDayOfWeek = firstDayOfWeek,
            mutePast = mutePastEvents,
            onClick = { onEventClick(item.event) },
        )
        is CalendarSearchResult.TaskItem -> SearchTaskResultCard(
            task = item.task,
            showDate = showDate,
            taskColorMode = taskColorMode,
            onTaskStatusChanged = onTaskStatusChanged,
            hierarchyDepth = 0,
            hierarchyContinuationLevels = emptySet(),
            hierarchyLastSibling = true,
            agendaDateHierarchy = agendaDateHierarchy,
            showCalendarWeeks = showCalendarWeeks,
            firstDayOfWeek = firstDayOfWeek,
            hasSubtasks = false,
            subtasksExpanded = false,
            onToggleSubtasks = {},
            statusToggleEnabled = taskStatusToggleEnabled,
            onClick = { onTaskClick(item.task) },
        )
        is CalendarSearchResult.TaskGroup -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { clip = false },
        ) {
            item.entries.forEach { entry ->
                AnimatedTaskHierarchyEntry(entry) {
                    SearchTaskResultCard(
                        task = entry.task,
                        showDate = showDate,
                        taskColorMode = taskColorMode,
                        onTaskStatusChanged = onTaskStatusChanged,
                        hierarchyDepth = entry.depth,
                        hierarchyContinuationLevels = entry.continuationLevels,
                        hierarchyLastSibling = entry.lastSibling,
                        agendaDateHierarchy = agendaDateHierarchy,
                        showCalendarWeeks = showCalendarWeeks,
                        firstDayOfWeek = firstDayOfWeek,
                        hasSubtasks = entry.hasChildren,
                        subtasksExpanded = entry.expanded,
                        onToggleSubtasks = { taskHierarchy.toggle(entry.task) },
                        statusToggleEnabled = taskStatusToggleEnabled,
                        onClick = { onTaskClick(entry.task) },
                    )
                }
            }
        }
    }
}

internal fun buildAgendaEventResults(
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
): List<CalendarSearchResult.Event> =
    agendaEventDateSpans(events, tasks).map { span ->
        CalendarSearchResult.Event(span.event, span.startDate, span.endDate)
    }

@Composable
private fun AgendaDynamicTopHeader(
    viewport: AgendaStickyHeaderViewport,
    today: LocalDate,
    showCalendarWeeks: Boolean,
    firstDayOfWeek: DayOfWeek,
    background: Color,
    topSpacing: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val informationFontSize = agendaChronologyPresentation.informationFontSizeSp
    val neutralColor = MaterialTheme.colorScheme.onSurface
    val informationStart = 18.dp + agendaChronologyPresentation.eventGutterWidth + 12.dp
    val monthLaneWidth = agendaChronologyPresentation.monthLaneWidth
    val yearLaneWidth = agendaChronologyPresentation.yearLaneWidth
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .zIndex(1_000f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(agendaChronologyPresentation.topHeaderHeight + topSpacing)
                .background(
                    background.copy(alpha = calendarInformationChrome.agendaStickyHeaderAlpha),
                ),
        )
        Box(
            modifier = Modifier
                .width(informationStart)
                .fillMaxHeight()
                .background(
                    background.copy(alpha = calendarInformationChrome.agendaStickyHeaderAlpha),
                ),
        )
        AgendaStickyValueLane(
            viewport = viewport.day,
            modifier = Modifier
                .testTag("agenda-header-day")
                .offset(x = 18.dp, y = topSpacing)
                .width(agendaChronologyPresentation.eventGutterWidth)
                .fillMaxHeight(),
        ) { date ->
            AgendaDateStack(
                date = date,
                color = neutralColor,
                highlighted = date == today,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 18.dp + agendaChronologyPresentation.eventGutterWidth + 12.dp,
                    top = topSpacing,
                    end = 18.dp,
                ),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                AgendaStickyValueLane(
                    viewport = viewport.month,
                    modifier = Modifier
                        .testTag("agenda-header-month")
                        .width(monthLaneWidth)
                        .fillMaxHeight(),
                ) { monthDate ->
                    Text(
                        text = YearMonth.from(monthDate).atDay(1).format(
                            DateTimeFormatter.ofPattern("MMMM", LocalAppLocale.current),
                        ),
                        color = neutralColor,
                        fontSize = informationFontSize.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AgendaStickyValueLane(
                    viewport = viewport.year,
                    modifier = Modifier
                        .testTag("agenda-header-year")
                        .width(yearLaneWidth)
                        .fillMaxHeight(),
                ) { yearDate ->
                    Text(
                        text = yearDate.year.toString(),
                        color = neutralColor,
                        fontSize = informationFontSize.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            if (showCalendarWeeks) {
                AgendaStickyValueLane(
                    viewport = viewport.week,
                    modifier = Modifier
                        .testTag("agenda-header-week")
                        .fillMaxHeight(),
                ) { weekDate ->
                    Text(
                        text = stringResource(
                            R.string.calendar_week_label,
                            weekDate.calendarWeekNumber(firstDayOfWeek),
                        ),
                        color = neutralColor,
                        fontSize = agendaChronologyPresentation.weekFontSizeSp.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgendaStickyValueLane(
    viewport: AgendaStickyFieldViewport,
    modifier: Modifier = Modifier,
    content: @Composable (LocalDate) -> Unit,
) {
    Box(
        modifier = modifier.clipToBounds(),
    ) {
        viewport.elements.forEach { element ->
            key(element.key) {
                Box(
                    modifier = Modifier.offset {
                        IntOffset(x = 0, y = element.offset)
                    },
                ) {
                    content(element.date)
                }
            }
        }
    }
}

@Composable
private fun AgendaDateStack(
    date: LocalDate,
    color: Color,
    highlighted: Boolean = false,
) {
    val informationFontSize = agendaChronologyPresentation.informationFontSizeSp
    val highlightColor = WarmBrown
    val highlightedTextColor = Color.White
    Column(
        modifier = Modifier
            .width(agendaChronologyPresentation.eventGutterWidth)
            .then(if (highlighted) Modifier.testTag("agenda-current-day") else Modifier)
            .then(
                if (highlighted) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(highlightColor)
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = agendaChronologyPresentation.dateHorizontalPadding,
                vertical = agendaChronologyPresentation.dateVerticalPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = if (highlighted) highlightedTextColor else color,
            fontSize = informationFontSize.sp,
            lineHeight = agendaChronologyPresentation.dateLineHeightSp.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        // Never wrapped: a wide weekday such as "Wed" would otherwise lose its last letter.
        Text(
            text = date.format(DateTimeFormatter.ofPattern("EEE", LocalAppLocale.current)).replace(".", ""),
            color = if (highlighted) highlightedTextColor else color,
            fontSize = informationFontSize.sp,
            lineHeight = agendaChronologyPresentation.dateLineHeightSp.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            // Centered, it may use the column's side padding.
            modifier = Modifier.wrapContentWidth(unbounded = true),
        )
    }
}

@Composable
internal fun AgendaBoundaryMarker(
    date: LocalDate,
    changes: AgendaBoundaryChanges,
    showCalendarWeeks: Boolean,
    firstDayOfWeek: DayOfWeek,
) {
    if (!agendaChronologyRenderingPolicy.renderValuesInsideListRows) {
        Spacer(Modifier.fillMaxWidth().height(28.dp))
        return
    }
    val informationFontSize = agendaChronologyPresentation.informationFontSizeSp
    val neutralColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp, bottom = 1.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.width(agendaChronologyPresentation.eventGutterWidth))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (changes.monthChanged) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("MMMM", LocalAppLocale.current)),
                    color = neutralColor,
                    fontSize = informationFontSize.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            if (changes.yearChanged) {
                Text(
                    text = date.year.toString(),
                    color = neutralColor,
                    fontSize = informationFontSize.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            if (showCalendarWeeks && changes.weekChanged) {
                Text(
                    text = stringResource(
                        R.string.calendar_week_label,
                        date.calendarWeekNumber(firstDayOfWeek),
                    ),
                    color = neutralColor,
                    fontSize = agendaChronologyPresentation.weekFontSizeSp.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The sticky header of a group of search results, e.g. a year; also used by "Recently deleted". */
@Composable
internal fun SearchGroupHeader(text: String, background: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1_000f)
            .background(background.copy(alpha = 0.97f))
            .padding(start = 62.dp, top = 7.dp, bottom = 7.dp),
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun SearchPastFutureDivider() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalBleed(18.dp)
            .padding(top = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            stringResource(R.string.past),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider(color = WarmLine)
        Text(
            stringResource(R.string.future),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SearchResultCard(
    event: EventEntity,
    showDate: Boolean,
    onClick: () -> Unit,
    displayDate: LocalDate = event.startsAtMillis.toDate(),
    spanEndDate: LocalDate = displayDate,
    agendaDateHierarchy: Boolean = false,
    showCalendarWeeks: Boolean = false,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    mutePast: Boolean = true,
) {
    val isPast = mutePast && event.endsAtMillis < System.currentTimeMillis()
    val isSpan = spanEndDate.isAfter(displayDate)
    val attendees = remember(event.attendeesJson) { event.attendeesJson.toCalendarParticipants() }
    // The card keeps room for its three lines when the text is larger.
    val rowHeight = when {
        isSpan -> AgendaMultiDaySpanCardHeight
        attendees.isNotEmpty() -> 76.dp
        else -> 64.dp
    } * calendarTextScale()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val visuals = event.cardVisuals(muted = isPast, darkPalette = CurrentDarkPalette)
        SearchDateColumn(
            date = displayDate,
            muted = isPast,
            visible = showDate,
            endDate = spanEndDate.takeIf { isSpan },
            height = if (isSpan) rowHeight else null,
            agendaDateHierarchy = agendaDateHierarchy,
            showCalendarWeeks = showCalendarWeeks,
            firstDayOfWeek = firstDayOfWeek,
        )
        val eventTextStyle = tentativeReadableTextStyle(event.isTentative() && !isPast)
        val shape = RoundedCornerShape(13.dp)
        val pendingAlpha = pendingDeleteAlpha(event)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(rowHeight)
                .alpha(pendingAlpha)
                .clip(shape)
                .background(visuals.background)
                .then(
                    when {
                        visuals.dashedBorder && visuals.borderColor != null -> Modifier.dashedBorder(visuals.borderColor, 13.dp)
                        visuals.borderColor != null -> Modifier.border(1.dp, visuals.borderColor, shape)
                        else -> Modifier
                    },
                )
                .clickable(onClick = onClick)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .bottomEdgeFadeMask(10.dp)
                    .padding(start = 12.dp, top = 7.dp, end = 12.dp, bottom = 0.dp)
                    .padding(end = if (attendees.isNotEmpty()) 54.dp else 0.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                FadingTimedText(event.title, color = visuals.contentColor, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textDecoration = visuals.textDecoration, shadow = eventTextStyle.shadow)
                FadingTimedText(
                    if (isSpan) event.localizedAgendaSpanLabel(displayDate, spanEndDate) else event.localizedTimeLabel(),
                    color = visuals.contentColor.copy(alpha = if (isPast) 0.72f else 0.92f),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    textDecoration = visuals.textDecoration,
                    shadow = eventTextStyle.shadow,
                )
                event.location?.takeIf { it.isNotBlank() }?.let {
                    FadingTimedText(it.cardLocationText(event.locationMapVerified), color = visuals.contentColor.copy(alpha = if (isPast) 0.66f else 0.86f), fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, textDecoration = visuals.textDecoration, shadow = eventTextStyle.shadow)
                }
            }
            EventParticipantStack(
                attendees = attendees,
                eventColor = visuals.baseColor,
                contentColor = visuals.contentColor,
                modifier = Modifier.align(Alignment.BottomEnd),
                circleSize = 24.dp,
                maxVisible = 5,
                muted = isPast,
            )
            PendingMutationBadge(
                event = event,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp),
            )
        }
    }
}

@Composable
private fun SearchTaskResultCard(
    task: TaskEntity,
    showDate: Boolean,
    taskColorMode: TaskColorMode,
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    hierarchyDepth: Int = 0,
    hierarchyContinuationLevels: Set<Int> = emptySet(),
    hierarchyLastSibling: Boolean = true,
    agendaDateHierarchy: Boolean = false,
    showCalendarWeeks: Boolean = false,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    hasSubtasks: Boolean = false,
    subtasksExpanded: Boolean = true,
    onToggleSubtasks: (() -> Unit)? = null,
    statusToggleEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val taskDate = (task.startAtMillis ?: task.dueAtMillis)?.toDate()
    val isMuted = task.isInactive()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(taskHierarchyLayerZ(hierarchyDepth) + taskPriorityLayerZ(task, PriorityAnimationsEnabled))
            .graphicsLayer { clip = false },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (hierarchyDepth == 0) {
            SearchDateColumn(
                date = taskDate,
                muted = isMuted,
                visible = showDate,
                agendaDateHierarchy = agendaDateHierarchy,
                showCalendarWeeks = showCalendarWeeks,
                firstDayOfWeek = firstDayOfWeek,
            )
        } else {
            Spacer(Modifier.width(searchDateColumnWidth(agendaDateHierarchy)))
        }
        TaskRow(
            task = task,
            taskColorMode = taskColorMode,
            onTaskStatusChanged = onTaskStatusChanged,
            prominent = true,
            hierarchyDepth = hierarchyDepth,
            hierarchyContinuationLevels = hierarchyContinuationLevels,
            hierarchyLastSibling = hierarchyLastSibling,
            hasSubtasks = hasSubtasks,
            subtasksExpanded = subtasksExpanded,
            onToggleSubtasks = onToggleSubtasks,
            modifier = Modifier.weight(1f),
            outerHorizontalPadding = 0.dp,
            outerVerticalPadding = 3.dp,
            connectorStemInset = TaskHierarchyStemInset,
            priorityMotionEnabled = !isMuted,
            statusToggleEnabled = statusToggleEnabled,
            onClick = onClick,
        )
    }
}

private val AgendaMultiDaySpanCardHeight = 96.dp

@Composable
private fun SearchDateColumn(
    date: LocalDate?,
    muted: Boolean,
    visible: Boolean = true,
    endDate: LocalDate? = null,
    height: Dp? = null,
    agendaDateHierarchy: Boolean = false,
    showCalendarWeeks: Boolean = false,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
) {
    if (agendaDateHierarchy) {
        AgendaEventDateColumn(
            date = date,
            muted = muted,
            visible = visible,
            height = height,
        )
        return
    }
    Column(
        modifier = Modifier
            .width(50.dp)
            .then(if (height != null) Modifier.height(height) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // An undated (inbox) task leaves the column empty, like the agenda-style date column.
        if (visible && date != null) {
            Text(
                date.format(DateTimeFormatter.ofPattern("MMM", LocalAppLocale.current)).replace(".", ""),
                color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                date.dayOfMonth.toString(),
                color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                fontSize = 20.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (endDate != null) {
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(WarmInk.copy(alpha = if (muted) 0.22f else 0.42f)),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    endDate.format(DateTimeFormatter.ofPattern("MMM", LocalAppLocale.current)).replace(".", ""),
                    color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    endDate.dayOfMonth.toString(),
                    color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                    fontSize = 17.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun searchDateColumnWidth(
    agendaDateHierarchy: Boolean,
): Dp = when {
    !agendaDateHierarchy -> 50.dp
    else -> agendaChronologyPresentation.eventGutterWidth
}

@Composable
private fun AgendaEventDateColumn(
    date: LocalDate?,
    muted: Boolean,
    visible: Boolean,
    height: Dp?,
) {
    Column(
        modifier = Modifier
            .width(agendaChronologyPresentation.eventGutterWidth)
            .then(if (height != null) Modifier.height(height) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (
            agendaChronologyRenderingPolicy.renderValuesInsideListRows &&
            visible &&
            date != null
        ) {
            AgendaDateStack(
                date = date,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (muted) 0.58f else 1f,
                ),
            )
        }
    }
}

/** The calendars the search can be limited to: the enabled ones shown in the calendar, in their order. */
internal fun CalendarUiState.searchableCalendars(): List<CollectionEntity> =
    collections.filter { it.isEnabled && it.href !in hiddenCollectionHrefs && (it.supportsEvents || it.supportsTasks) }
