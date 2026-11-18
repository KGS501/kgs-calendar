package com.kgs.calendar.ui.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.ui.res.stringResource
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.CalendarViewModel
import com.kgs.calendar.ui.DetailSheet
import com.kgs.calendar.ui.MotionEmphasized
import com.kgs.calendar.ui.MotionLong
import com.kgs.calendar.ui.MotionMedium
import com.kgs.calendar.ui.MotionStandardAccelerate
import com.kgs.calendar.ui.SearchOptionsBar
import com.kgs.calendar.ui.SearchQueryField
import com.kgs.calendar.ui.SearchResultsList
import com.kgs.calendar.ui.WarmInk
import com.kgs.calendar.ui.layout.ListContentMaxWidth
import com.kgs.calendar.ui.searchableCalendars
import com.kgs.calendar.ui.settingsControlColor
import com.kgs.calendar.ui.shell.CalendarShellUiState
import kotlin.math.roundToInt
import com.kgs.calendar.ui.res.Res

/**
 * The desktop's search, over the calendar's column only: the sidebars stay. The toolbar's search
 * field ([anchor], in root coordinates) grows into the search's own field at the top, the
 * options and results fade in below it, and closing reverses it into the toolbar.
 */
@Composable
internal fun DesktopSearchPanel(
    visible: Boolean,
    anchor: Rect?,
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    shell: CalendarShellUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    var shown by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            shown = true
            progress.animateTo(1f, tween(MotionLong, easing = MotionEmphasized))
        } else {
            progress.animateTo(0f, tween(MotionMedium, easing = MotionStandardAccelerate))
            shown = false
        }
    }
    if (!shown) return
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var optionsHeightPx by remember { mutableIntStateOf(0) }
    val quiet = remember { MutableInteractionSource() }
    val background = MaterialTheme.colorScheme.background
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        val p = progress.value
        // Content (results, options) follows the field once it is mostly in place.
        val contentAlpha = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = p }
                .background(background)
                .clickable(interactionSource = quiet, indication = null, onClick = {}),
        )
        val headerHeight = with(density) { DesktopToolbarHeight + optionsHeightPx.toDp() }
        // The field and its options line up with the results below, right of the back button.
        val listPadding = 18.dp + ((maxWidth - ListContentMaxWidth) / 2).coerceAtLeast(0.dp)
        val fieldStart = maxOf(listPadding, 18.dp + 40.dp + 14.dp)
        val fieldWidth = (maxWidth - listPadding - fieldStart).coerceAtLeast(120.dp)
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = headerHeight)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = (1f - contentAlpha) * 24.dp.toPx()
                },
        ) {
            SearchResultsList(
                query = state.searchQuery,
                eventResults = state.searchResults,
                taskResults = state.searchTaskResults,
                allTasksForHierarchy = state.allTasks,
                taskColorMode = state.taskColorMode,
                subtasksExpandedByDefault = state.subtasksExpandedByDefault,
                onEventClick = { shell.openDetail(DetailSheet.Event(it)) },
                onTaskClick = { shell.openTaskDetail(it) },
                onTaskStatusChanged = viewModel.edits::setTaskStatus,
                onLoadEarlierOccurrences = viewModel.search::loadEarlierSearchOccurrences,
                onLoadLaterOccurrences = viewModel.search::loadLaterSearchOccurrences,
                stickyHeaderBackground = background,
                agendaDateHierarchy = true,
                showCalendarWeeks = state.showCalendarWeeks,
                firstDayOfWeek = state.firstDayOfWeek,
                agendaHeaderTopSpacing = 6.dp,
            )
        }
        SearchOptionsBar(
            searchMode = state.searchMode,
            onSearchModeChange = viewModel.search::setSearchMode,
            calendars = state.searchableCalendars(),
            selectedCalendars = state.searchCalendars,
            onSelectedCalendarsChange = viewModel.search::setSearchCalendars,
            modifier = Modifier
                .offset(x = fieldStart - 18.dp, y = DesktopToolbarHeight)
                .width(fieldWidth + 36.dp)
                .onSizeChanged { optionsHeightPx = it.height }
                .graphicsLayer { alpha = contentAlpha },
        )
        // Back to the calendar, where the toolbar's arrows were.
        Box(
            Modifier
                .padding(start = 18.dp)
                .size(40.dp, DesktopToolbarHeight)
                .graphicsLayer { alpha = contentAlpha },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(settingsControlColor())
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(Res.string.back), tint = WarmInk, modifier = Modifier.size(22.dp))
            }
        }
        // The field, from the toolbar's search field to its place at the top of the search.
        val target = with(density) {
            Rect(
                offset = Offset(fieldStart.toPx(), ((DesktopToolbarHeight - SearchFieldHeight) / 2).toPx()),
                size = androidx.compose.ui.geometry.Size(fieldWidth.toPx(), SearchFieldHeight.toPx()),
            )
        }
        val start = anchor?.translate(-origin.x, -origin.y) ?: target.deflate(with(density) { 24.dp.toPx() })
        val frame = androidx.compose.ui.geometry.lerp(start, target, p)
        val radius = with(density) { (frame.height / 2f).toDp() }
        Row(
            modifier = Modifier
                .offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
                .size(with(density) { frame.width.toDp() }, with(density) { frame.height.toDp() })
                .clip(RoundedCornerShape(radius))
                .background(lerp(settingsControlColor(), MaterialTheme.colorScheme.surface, p))
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp + 3.dp * p).padding(end = 0.dp),
            )
            SearchQueryField(
                query = state.searchQuery,
                onQueryChange = viewModel.search::setSearchQuery,
                fontSize = (14f + 3f * p).sp,
                clearButtonSize = 34.dp,
                placeholderWhileFocused = true,
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
            )
        }
    }
}

private val SearchFieldHeight = 48.dp
