package com.kgs.calendar.ui.agenda

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.domain.model.CalendarRange
import com.kgs.calendar.domain.model.contains
import com.kgs.calendar.ui.AgendaBoundaryMarker
import com.kgs.calendar.ui.AgendaDynamicTopHeaderState
import com.kgs.calendar.ui.AgendaStickyHeaderViewport
import com.kgs.calendar.ui.AgendaVisibleHeaderItem
import com.kgs.calendar.ui.CalendarSearchResult
import com.kgs.calendar.ui.CalendarSearchResultRow
import com.kgs.calendar.ui.DetailSheet
import com.kgs.calendar.ui.SearchPastFutureDivider
import com.kgs.calendar.ui.agendaChronologyPresentation
import com.kgs.calendar.ui.agendaStickyHeaderViewport
import com.kgs.calendar.ui.buildAgendaEventResults
import com.kgs.calendar.ui.rememberTaskHierarchyPresentation
import com.kgs.calendar.ui.resolvedAgendaDateStackHeight
import com.kgs.calendar.ui.resolvedAgendaDatePushDistance
import com.kgs.calendar.ui.stableKey
import com.kgs.calendar.ui.model.agendaSortMillis
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

internal data class AgendaTimelineSnapshot(
    val navigation: AgendaNavigationSnapshot<CalendarSearchResult>,
)

@Composable
internal fun AgendaTimeline(
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    loadedRange: CalendarRange?,
    navigationRequest: AgendaNavigationRequest,
    today: LocalDate,
    taskColorMode: TaskColorMode,
    showCalendarWeeks: Boolean,
    firstDayOfWeek: DayOfWeek,
    stickyHeaderBackground: Color,
    emptyMessage: String,
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    onDetail: (DetailSheet) -> Unit,
    onLoadEarlier: () -> Unit,
    onLoadLater: () -> Unit,
    modifier: Modifier = Modifier,
    headerTopSpacing: Dp = 0.dp,
) {
    val snapshot by produceState<AgendaTimelineSnapshot?>(
        initialValue = null,
        events,
        tasks,
        loadedRange,
        navigationRequest.date,
        today,
        firstDayOfWeek,
        showCalendarWeeks,
    ) {
        val range = loadedRange ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val results = buildList<CalendarSearchResult> {
                addAll(buildAgendaEventResults(events, tasks))
                tasks.asSequence()
                    .filter { it.agendaSortMillis() != null }
                    .map { CalendarSearchResult.TaskItem(it) }
                    .forEach(::add)
            }
            val entries = results.map { result ->
                AgendaTimelineEntry(
                    key = result.stableKey(),
                    date = result.date,
                    sortMillis = result.sortMillis,
                    value = result,
                )
            }
            val requiredAnchor = navigationRequest.date.takeIf(range::contains)
            AgendaTimelineSnapshot(
                navigation = AgendaNavigationSnapshot(
                    loadedRange = range,
                    requiredAnchorDate = requiredAnchor,
                    plan = AgendaTimelinePlanner.plan(
                        entries = entries,
                        today = today,
                        firstDayOfWeek = firstDayOfWeek,
                        showCalendarWeeks = showCalendarWeeks,
                        requiredAnchorDate = requiredAnchor,
                    ),
                ),
            )
        }
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val agendaDateStackHeight = resolvedAgendaDateStackHeight(density.fontScale)
    var lastHandledRequestId by remember { mutableLongStateOf(Long.MIN_VALUE) }
    var currentVisibleDate by remember { mutableStateOf<LocalDate?>(null) }
    val currentSnapshot = snapshot
    val plan = currentSnapshot?.navigation?.plan
    androidx.compose.runtime.LaunchedEffect(navigationRequest, currentSnapshot) {
        val readySnapshot = currentSnapshot ?: return@LaunchedEffect
        val placement = resolveAgendaNavigation(
            request = navigationRequest,
            lastHandledRequestId = lastHandledRequestId,
            snapshot = readySnapshot.navigation,
        ) ?: return@LaunchedEffect
        val motion = agendaNavigationMotion(
            lastHandledRequestId = lastHandledRequestId,
            currentVisibleDate = currentVisibleDate,
            targetDate = placement.date,
        )
        when (motion) {
            AgendaNavigationMotion.Instant -> listState.scrollToItem(placement.rowIndex)
            AgendaNavigationMotion.AnimateForward,
            AgendaNavigationMotion.AnimateBackward,
            -> {
                val direction = if (motion == AgendaNavigationMotion.AnimateForward) 1 else -1
                val travel = agendaNavigationTravel(
                    viewportHeightPx = listState.layoutInfo.viewportSize.height,
                    minimumDistancePx = with(density) {
                        AgendaNavigationMinimumTravelDistance.roundToPx()
                    },
                )
                listState.animateScrollBy(
                    value = direction * travel.distancePx.toFloat(),
                    animationSpec = tween(
                        durationMillis = travel.departureDurationMillis,
                        easing = AgendaNavigationDepartureEasing,
                    ),
                )
                listState.scrollToItem(
                    index = placement.rowIndex,
                    scrollOffset = -direction * travel.distancePx,
                )
                listState.animateScrollBy(
                    value = direction * travel.distancePx.toFloat(),
                    animationSpec = tween(
                        durationMillis = travel.arrivalDurationMillis,
                        easing = AgendaNavigationArrivalEasing,
                    ),
                )
            }
        }
        lastHandledRequestId = placement.requestId
        currentVisibleDate = placement.date
    }

    androidx.compose.runtime.LaunchedEffect(
        listState,
        plan,
        navigationRequest.id,
        lastHandledRequestId,
    ) {
        val signals = plan?.headerSignals?.days.orEmpty()
        if (signals.isEmpty() || navigationRequest.id > lastHandledRequestId) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisibleIndex ->
                currentVisibleDate = signals.lastOrNull { it.lazyIndex <= firstVisibleIndex }?.date
                    ?: signals.first().date
            }
    }

    val latestEarlier = rememberUpdatedState(onLoadEarlier)
    val latestLater = rememberUpdatedState(onLoadLater)
    androidx.compose.runtime.LaunchedEffect(listState, currentSnapshot?.navigation?.loadedRange) {
        currentSnapshot?.navigation?.loadedRange ?: return@LaunchedEffect
        var earlierRequested = false
        var laterRequested = false
        snapshotFlow {
            val layout = listState.layoutInfo
            AgendaEdgeSnapshot(
                scrolling = listState.isScrollInProgress,
                firstVisibleIndex = layout.visibleItemsInfo.firstOrNull()?.index ?: 0,
                lastVisibleIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: 0,
                totalItems = layout.totalItemsCount,
            )
        }
            .distinctUntilChanged()
            .collect { edge ->
                if (!edge.scrolling || edge.totalItems == 0) return@collect
                if (!earlierRequested && edge.firstVisibleIndex <= 6) {
                    earlierRequested = true
                    latestEarlier.value()
                }
                if (!laterRequested && edge.lastVisibleIndex >= edge.totalItems - 7) {
                    laterRequested = true
                    latestLater.value()
                }
            }
    }

    val headerViewport: State<AgendaStickyHeaderViewport?> = remember(
        listState,
        plan?.headerSignals,
        density,
    ) {
        derivedStateOf {
            val signals = plan?.headerSignals ?: return@derivedStateOf null
            if (signals.days.isEmpty()) return@derivedStateOf null
            val layout = listState.layoutInfo
            agendaStickyHeaderViewport(
                signals = signals,
                visibleItems = layout.visibleItemsInfo.map { item ->
                    AgendaVisibleHeaderItem(item.key, item.offset, item.index)
                },
                firstVisibleItemIndex = layout.visibleItemsInfo.firstOrNull()?.index ?: 0,
                viewportStartOffset = layout.viewportStartOffset,
                informationContentTopInset = with(density) { 5.dp.roundToPx() },
                dayElementHeight = with(density) {
                    resolvedAgendaDatePushDistance(density.fontScale).roundToPx()
                },
                informationElementHeight = with(density) { 22.dp.roundToPx() },
            )
        }
    }
    val taskHierarchy = rememberTaskHierarchyPresentation(emptyList(), expandedByDefault = false)

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("agenda-timeline-list"),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = agendaDateStackHeight + 16.dp + headerTopSpacing,
                end = 18.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            val rows = plan?.rows.orEmpty()
            items(
                items = rows,
                key = { row -> row.stableKey },
                contentType = { row -> row::class },
            ) { row ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agenda-row-${row.stableKey}"),
                ) {
                    when (row) {
                        is AgendaTimelineRow.Boundary -> AgendaBoundaryMarker(
                            date = row.date,
                            changes = row.changes,
                            showCalendarWeeks = showCalendarWeeks,
                            firstDayOfWeek = firstDayOfWeek,
                        )
                        is AgendaTimelineRow.DateAnchor -> {
                            BasicText(
                                text = emptyMessage,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 62.dp, top = 18.dp),
                                style = androidx.compose.ui.text.TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    lineHeight = 20.sp,
                                ),
                            )
                        }
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
                            onEventClick = { onDetail(DetailSheet.Event(it)) },
                            onTaskClick = { onDetail(DetailSheet.Task(it)) },
                        )
                        AgendaTimelineRow.Footer -> Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
        AgendaDynamicTopHeaderState(
            viewportState = headerViewport,
            today = today,
            showCalendarWeeks = showCalendarWeeks,
            firstDayOfWeek = firstDayOfWeek,
            background = stickyHeaderBackground,
            topSpacing = headerTopSpacing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

private data class AgendaEdgeSnapshot(
    val scrolling: Boolean,
    val firstVisibleIndex: Int,
    val lastVisibleIndex: Int,
    val totalItems: Int,
)

private val AgendaNavigationMinimumTravelDistance = 240.dp
private val AgendaNavigationDepartureEasing = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private val AgendaNavigationArrivalEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
