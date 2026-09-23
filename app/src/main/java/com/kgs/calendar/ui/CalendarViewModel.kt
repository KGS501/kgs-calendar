package com.kgs.calendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kgs.calendar.AppGraph
import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.domain.model.CalendarRange
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.AgendaWindowPolicy
import com.kgs.calendar.domain.model.calendarViewModeForOrientation
import com.kgs.calendar.domain.model.DEFAULT_MULTI_DAY_COUNT
import com.kgs.calendar.domain.model.coerceMultiDayCount
import com.kgs.calendar.domain.model.multiDayCountForOrientation
import com.kgs.calendar.domain.model.startOfWeek
import com.kgs.calendar.domain.model.timelineDayCount
import com.kgs.calendar.domain.model.timelineEntryDate
import com.kgs.calendar.domain.model.timelineRestoreDate
import com.kgs.calendar.domain.model.timelineVisibleAnchor
import com.kgs.calendar.domain.model.visibleRangeFor
import com.kgs.calendar.lifecycle.ForegroundRecenterPolicy
import com.kgs.calendar.ui.timeline.TimelineOrientationViewportMemory
import com.kgs.calendar.reminder.TaskMutationCoordinator
import com.kgs.calendar.navigation.CalendarLaunchResolver
import com.kgs.calendar.navigation.CalendarLaunchTarget
import com.kgs.calendar.sync.SourceCalendarMutationCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

data class CalendarWidgetLaunchTarget(
    val date: LocalDate,
    val viewMode: CalendarViewMode,
    val createEvent: Boolean = false,
    val createTaskScheduled: Boolean? = null,
    val openEventUid: String? = null,
    val openTaskUid: String? = null,
)

private data class TimelinePolicySettings(
    val multiDayCount: Int,
    val weekViewEnabled: Boolean,
    val fullWeekSwipeEnabled: Boolean,
    val firstDayOfWeek: DayOfWeek,
)

private data class LoadedCalendarItems<T>(
    val range: CalendarRange,
    val items: List<T>,
)

/**
 * Calendar navigation (date, view, visible/data ranges), the combined [uiState] and launch
 * handling. Settings, search, item edits and source management live in [settings], [search],
 * [edits] and [sources].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: CalendarRepository,
    private val settingsStore: SettingsStore,
    sourceCalendarMutationCoordinator: SourceCalendarMutationCoordinator,
    taskMutationCoordinator: TaskMutationCoordinator,
    private val calendarLaunchResolver: CalendarLaunchResolver,
    internal val timelineViewportMemory: TimelineOrientationViewportMemory,
    widgetRefresher: WidgetRefresher,
    reminderRescheduler: ReminderRescheduler,
    appLifecycleSignals: AppLifecycleSignals,
    uiStrings: UiStrings,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    initialWidgetLaunchTarget: CalendarWidgetLaunchTarget? = null,
    initialCalendarLaunchTarget: CalendarLaunchTarget? = null,
    // False when the activity is restored from saved state: the launch was already handled then,
    // so only its date/view selection is reapplied.
    deliverInitialLaunchEvents: Boolean = true,
) : ViewModel() {
    private val initialSelectedDate = initialCalendarLaunchTarget?.date ?: initialWidgetLaunchTarget?.date ?: LocalDate.now()
    private val initialSelectedView = initialCalendarLaunchTarget?.viewMode ?: initialWidgetLaunchTarget?.viewMode ?: CalendarViewMode.ThreeDay
    private val initialDateNavigationSerial = if (initialWidgetLaunchTarget != null || initialCalendarLaunchTarget != null) 1 else 0
    private val transient = CalendarTransientState()
    private val message = transient.message
    private val uiEventChannel = Channel<CalendarUiEvent>(Channel.BUFFERED)

    /** One-off launch commands; collect exactly once (see [CalendarUiEvent]). */
    val uiEvents: Flow<CalendarUiEvent> = uiEventChannel.receiveAsFlow()
    private var pendingOpenEventJob: Job? = null
    private var pendingOpenTaskJob: Job? = null
    private val isLandscape = MutableStateFlow(false)
    private val useLandscapeEntryView = MutableStateFlow(false)
    private val selectedViewOverride = MutableStateFlow<CalendarViewMode?>(
        initialCalendarLaunchTarget?.viewMode ?: initialWidgetLaunchTarget?.viewMode,
    )
    private val selectedDateOverride = MutableStateFlow<LocalDate?>(
        initialCalendarLaunchTarget?.date ?: initialWidgetLaunchTarget?.date,
    )

    /**
     * Stays a counter in [CalendarUiState]: the timeline pager treats any serial above the one it
     * last handled as "jump without animation", including when it is freshly composed.
     */
    private val dateNavigationSerial = MutableStateFlow(initialDateNavigationSerial)
    private val initialDataReady = MutableStateFlow(false)
    private var selectedDatePersistJob: Job? = null
    private val foregroundRecenterPolicy = ForegroundRecenterPolicy()
    private var explicitLaunchSuppressionUntilMillis = if (
        initialWidgetLaunchTarget != null || initialCalendarLaunchTarget != null
    ) {
        System.currentTimeMillis() + EXPLICIT_LAUNCH_SUPPRESSION_MILLIS
    } else {
        Long.MIN_VALUE
    }

    val sources = CalendarSourceActions(
        scope = viewModelScope,
        repository = repository,
        settingsStore = settingsStore,
        sourceCalendarMutationCoordinator = sourceCalendarMutationCoordinator,
        reminderRescheduler = reminderRescheduler,
        appLifecycleSignals = appLifecycleSignals,
        strings = uiStrings,
        transient = transient,
    )

    val edits = CalendarEditActions(
        scope = viewModelScope,
        repository = repository,
        taskMutationCoordinator = taskMutationCoordinator,
        widgetRefresher = widgetRefresher,
        reminderRescheduler = reminderRescheduler,
        message = message,
        currentState = { uiState.value },
    )

    init {
        viewModelScope.launch {
            runCatching { repository.ensureLocalCalendar() }
                .onSuccess { message.value = null }
                .onFailure { message.value = it.message ?: "Could not prepare local calendar." }
        }
        initialCalendarLaunchTarget?.let { target ->
            launchFromCalendarTarget(target, openDetail = deliverInitialLaunchEvents)
        }
        viewModelScope.launch {
            val blockInitialUi = runCatching {
                repository.shouldBlockInitialAndroidProviderRefresh()
            }.getOrDefault(false)
            if (!blockInitialUi) {
                initialDataReady.value = true
            }
            try {
                runCatching {
                    repository.refreshAndroidCalendarsIfEnabled(
                        includeDisabledProviderCalendars = sources.includeDisabledAndroidProviderCalendars(),
                    )
                    sources.refreshAndroidProviderDiagnosticsInternal()
                }
                    .onFailure { /* Missing permission should not show a startup error. */ }
            } finally {
                if (blockInitialUi) {
                    initialDataReady.value = true
                }
            }
        }
        if (initialWidgetLaunchTarget != null) {
            persistWidgetSelection(initialWidgetLaunchTarget.date, initialWidgetLaunchTarget.viewMode)
        }
        viewModelScope.launch {
            appLifecycleSignals.processForegroundedAt.collect { foregroundedAt ->
                val backgroundedAt = settingsStore.lastBackgroundedAtMillis.first()
                settingsStore.setLastBackgroundedAtMillis(null)
                if (
                    foregroundRecenterPolicy.shouldRecenter(
                        backgroundedAt = backgroundedAt,
                        foregroundedAt = foregroundedAt,
                        explicitLaunchPending = foregroundedAt <= explicitLaunchSuppressionUntilMillis,
                    )
                ) {
                    recenterToToday(LocalDate.now(zoneId))
                }
            }
        }
    }

    private val requestedSelectedView = combine(settingsStore.selectedView, selectedViewOverride) { stored, override -> override ?: stored }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSelectedView)
    private val selectedView = combine(
        requestedSelectedView,
        isLandscape,
        useLandscapeEntryView,
    ) { requestedView, landscape, applyLandscapeEntry ->
        if (applyLandscapeEntry) {
            calendarViewModeForOrientation(requestedView, landscape)
        } else {
            requestedView
        }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSelectedView)
    private val restoredSelectedDate = restorePersistedSelectedDate(
        storedDate = settingsStore.selectedDate,
        storedView = settingsStore.selectedView,
        weekViewEnabled = settingsStore.weekViewEnabled,
        firstDayOfWeek = settingsStore.firstDayOfWeek,
    )
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSelectedDate)
    private val selectedDate = combine(restoredSelectedDate, selectedDateOverride) { restored, override ->
        override ?: restored
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSelectedDate)
    private val agendaDataRange = MutableStateFlow(AgendaWindowPolicy.around(initialSelectedDate))
    private val hiddenCollectionHrefs = settingsStore.hiddenCollectionHrefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val portraitMultiDayCount = settingsStore.portraitMultiDayCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_MULTI_DAY_COUNT)
    private val landscapeMultiDayCount = settingsStore.landscapeMultiDayCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_MULTI_DAY_COUNT)
    private val multiDayCount = combine(
        portraitMultiDayCount,
        landscapeMultiDayCount,
        isLandscape,
    ) { portrait, landscape, landscapeOrientation ->
        multiDayCountForOrientation(landscapeOrientation, portrait, landscape)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_MULTI_DAY_COUNT)
    private val firstDayOfWeek = settingsStore.firstDayOfWeek
        .stateIn(viewModelScope, SharingStarted.Eagerly, DayOfWeek.MONDAY)
    private val weekViewEnabled = settingsStore.weekViewEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsStore.DEFAULT_WEEK_VIEW_ENABLED)
    private val fullWeekSwipeEnabled = settingsStore.fullWeekSwipeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsStore.DEFAULT_FULL_WEEK_SWIPE_ENABLED)
    private val timelinePolicySettings = combine(
        multiDayCount,
        weekViewEnabled,
        fullWeekSwipeEnabled,
        firstDayOfWeek,
    ) { dayCount, weekEnabled, fullWeekSwipe, weekStart ->
        TimelinePolicySettings(dayCount, weekEnabled, fullWeekSwipe, weekStart)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        TimelinePolicySettings(
            DEFAULT_MULTI_DAY_COUNT,
            SettingsStore.DEFAULT_WEEK_VIEW_ENABLED,
            SettingsStore.DEFAULT_FULL_WEEK_SWIPE_ENABLED,
            DayOfWeek.MONDAY,
        ),
    )
    private val visibleRange = combine(
        selectedDate,
        selectedView,
        timelinePolicySettings,
        agendaDataRange,
    ) { date, view, policy, agendaRange ->
        if (view == CalendarViewMode.Agenda) {
            agendaRange
        } else {
            val anchor = timelineVisibleAnchor(
                date,
                view,
                policy.weekViewEnabled,
                policy.fullWeekSwipeEnabled,
                policy.firstDayOfWeek,
            )
            visibleRangeFor(anchor, view, policy.multiDayCount, policy.weekViewEnabled)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, visibleRangeFor(initialSelectedDate, initialSelectedView))
    private val dataRange = combine(
        selectedDate,
        selectedView,
        timelinePolicySettings,
        agendaDataRange,
    ) { date, view, policy, agendaRange ->
        when (view) {
            CalendarViewMode.Agenda -> agendaRange
            CalendarViewMode.ThreeDay -> timelineVisibleAnchor(
                date,
                view,
                policy.weekViewEnabled,
                policy.fullWeekSwipeEnabled,
                policy.firstDayOfWeek,
            ).multiDayDataRange(
                timelineDayCount(view, policy.weekViewEnabled, policy.multiDayCount),
            )
            else -> visibleRangeFor(date, view)
        }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, visibleRangeFor(initialSelectedDate, initialSelectedView))
    private val loadedEvents = dataRange.flatMapLatest { range ->
        combine(
            repository.observeEvents(range.startMillis(zoneId), range.endMillis(zoneId)),
            hiddenCollectionHrefs,
        ) { events, hidden ->
            LoadedCalendarItems(
                range = range,
                items = events.filterNot { it.collectionHref in hidden },
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        LoadedCalendarItems(dataRange.value, emptyList()),
    )
    private val loadedDatedTasks = dataRange.flatMapLatest { range ->
        combine(
            repository.observeDatedTasks(range.startMillis(zoneId), range.endMillis(zoneId)),
            hiddenCollectionHrefs,
        ) { tasks, hidden ->
            LoadedCalendarItems(
                range = range,
                items = tasks.filterNot { it.collectionHref in hidden },
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        LoadedCalendarItems(dataRange.value, emptyList()),
    )
    private val events = loadedEvents.map { it.items }
    private val datedTasks = loadedDatedTasks.map { it.items }
    private val loadedDataRange = combine(loadedEvents, loadedDatedTasks) { eventWindow, taskWindow ->
        eventWindow.range.takeIf { it == taskWindow.range }
    }
    private val inboxTasks = combine(repository.observeInboxTasks(), hiddenCollectionHrefs) { tasks, hidden ->
        tasks.filterNot { it.collectionHref in hidden }
    }
    private val scheduledOpenTasks = combine(repository.observeScheduledOpenTasks(), hiddenCollectionHrefs) { tasks, hidden ->
        tasks.filterNot { it.collectionHref in hidden }
    }
    private val completedTasks = combine(repository.observeCompletedTasks(), hiddenCollectionHrefs) { tasks, hidden ->
        tasks.filterNot { it.collectionHref in hidden }
    }

    val search = SearchStateHolder(repository, hiddenCollectionHrefs, zoneId)

    val settings = SettingsActions(
        scope = viewModelScope,
        settingsStore = settingsStore,
        widgetRefresher = widgetRefresher,
        reminderRescheduler = reminderRescheduler,
        message = message,
        currentState = { uiState.value },
        isLandscape = { isLandscape.value },
        publishedFirstDayOfWeek = firstDayOfWeek,
        selectDate = ::selectDate,
    )

    private val dataState = calendarDataState(
        repository = repository,
        items = combine(events, datedTasks, inboxTasks, scheduledOpenTasks, completedTasks, ::CalendarItemData),
        loadedDataRange = loadedDataRange,
        requestedDataRange = dataRange,
    )
    private val navigationState = combine(
        selectedDate,
        selectedView,
        visibleRange,
        dateNavigationSerial,
        multiDayCount,
        ::NavigationUiState,
    )
    private val settingsState = combine(
        generalSettings(
            settingsStore = settingsStore,
            hiddenCollectionHrefs = hiddenCollectionHrefs,
            firstDayOfWeek = firstDayOfWeek,
            weekViewEnabled = weekViewEnabled,
            fullWeekSwipeEnabled = fullWeekSwipeEnabled,
            portraitMultiDayCount = portraitMultiDayCount,
            landscapeMultiDayCount = landscapeMultiDayCount,
        ),
        settingsStore.editorDefaults(),
        settingsStore.widgetSettings(),
        ::CalendarSettingsState,
    )

    private val initialUiState = CalendarUiState(
        selectedDate = initialSelectedDate,
        dateNavigationSerial = initialDateNavigationSerial,
        selectedView = initialSelectedView,
        visibleRange = visibleRangeFor(initialSelectedDate, initialSelectedView),
        loadedDataRange = dataRange.value,
        requestedDataRange = dataRange.value,
        searchOccurrenceRange = search.occurrenceRange,
    )

    val uiState: StateFlow<CalendarUiState> = combine(
        dataState,
        navigationState,
        search.state,
        settingsState,
        transient.state,
    ) { data, navigation, searchUi, settingsUi, transientUi ->
        calendarUiState(
            initialDataLoaded = true,
            data = data,
            navigation = navigation,
            search = searchUi,
            settings = settingsUi,
            transient = transientUi,
        )
    }
        .combine(initialDataReady) { uiState, ready ->
            uiState.copy(initialDataLoaded = ready)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialUiState)

    init {
        if (initialWidgetLaunchTarget != null && deliverInitialLaunchEvents) {
            sendWidgetLaunchEvents(
                date = initialSelectedDate,
                createEvent = initialWidgetLaunchTarget.createEvent,
                createTaskScheduled = initialWidgetLaunchTarget.createTaskScheduled,
                openEventUid = initialWidgetLaunchTarget.openEventUid,
                openTaskUid = initialWidgetLaunchTarget.openTaskUid,
            )
        }
    }

    fun selectView(viewMode: CalendarViewMode) {
        // Once the user deliberately chooses a view in landscape, respect that choice. The
        // automatic Day -> multi-day handoff only applies when entering landscape.
        useLandscapeEntryView.value = false
        val state = uiState.value
        val entryDate = timelineEntryDate(
            date = state.selectedDate,
            viewMode = viewMode,
            weekViewEnabled = state.weekViewEnabled,
            firstDayOfWeek = state.firstDayOfWeek,
        )
        if (entryDate != state.selectedDate) {
            selectDate(entryDate)
        }
        if (viewMode == CalendarViewMode.Agenda) {
            agendaDataRange.update { AgendaWindowPolicy.recenterIfNeeded(it, entryDate) }
        }
        selectedViewOverride.value = viewMode
        viewModelScope.launch { settingsStore.setSelectedView(viewMode) }
    }

    fun openFromWidget(
        date: LocalDate,
        viewMode: CalendarViewMode,
        createEvent: Boolean = false,
        createTaskScheduled: Boolean? = null,
        openEventUid: String? = null,
        openTaskUid: String? = null,
    ) {
        suppressAutomaticRecenterForExplicitLaunch()
        selectedViewOverride.value = viewMode
        if (viewMode == CalendarViewMode.Agenda) {
            agendaDataRange.update { AgendaWindowPolicy.recenterIfNeeded(it, date) }
        }
        selectedDateOverride.value = date
        dateNavigationSerial.update { it + 1 }
        sendWidgetLaunchEvents(date, createEvent, createTaskScheduled, openEventUid, openTaskUid)
        persistWidgetSelection(date, viewMode)
    }

    fun openFromCalendarLaunch(target: CalendarLaunchTarget) {
        launchFromCalendarTarget(target, openDetail = true)
    }

    private fun launchFromCalendarTarget(target: CalendarLaunchTarget, openDetail: Boolean) {
        suppressAutomaticRecenterForExplicitLaunch()
        viewModelScope.launch {
            val resolution = calendarLaunchResolver.resolve(target) ?: return@launch
            selectedViewOverride.value = resolution.viewMode
            if (resolution.viewMode == CalendarViewMode.Agenda) {
                agendaDataRange.update { AgendaWindowPolicy.recenterIfNeeded(it, resolution.date) }
            }
            selectedDateOverride.value = resolution.date
            dateNavigationSerial.update { it + 1 }
            val openEvent = resolution.event?.let(CalendarUiEvent::OpenEvent)
                ?: resolution.task?.let(CalendarUiEvent::OpenTask)
            if (openDetail) openEvent?.let { uiEventChannel.send(it) }
            persistWidgetSelection(resolution.date, resolution.viewMode)
        }
    }

    /**
     * Create commands are delivered right away. Open commands wait until the item is part of the
     * loaded state; a newer open request of the same kind replaces a still pending one.
     */
    private fun sendWidgetLaunchEvents(
        date: LocalDate,
        createEvent: Boolean,
        createTaskScheduled: Boolean?,
        openEventUid: String?,
        openTaskUid: String?,
    ) {
        if (createEvent) {
            uiEventChannel.trySend(CalendarUiEvent.CreateEvent(date))
        }
        if (createTaskScheduled != null) {
            uiEventChannel.trySend(CalendarUiEvent.CreateTask(date, createTaskScheduled))
        }
        if (!openEventUid.isNullOrBlank()) {
            pendingOpenEventJob?.cancel()
            pendingOpenEventJob = viewModelScope.launch {
                val event = uiState
                    .mapNotNull { state -> state.events.firstOrNull { it.resourceHref == openEventUid || it.uid == openEventUid } }
                    .first()
                uiEventChannel.send(CalendarUiEvent.OpenEvent(event))
            }
        }
        if (!openTaskUid.isNullOrBlank()) {
            pendingOpenTaskJob?.cancel()
            pendingOpenTaskJob = viewModelScope.launch {
                val task = uiState
                    .mapNotNull { state -> state.allTasks.firstOrNull { it.resourceHref == openTaskUid || it.uid == openTaskUid } }
                    .first()
                uiEventChannel.send(CalendarUiEvent.OpenTask(task))
            }
        }
    }

    private fun persistWidgetSelection(date: LocalDate, viewMode: CalendarViewMode) {
        selectedDatePersistJob?.cancel()
        selectedDatePersistJob = viewModelScope.launch {
            settingsStore.setSelectedView(viewMode)
            settingsStore.setSelectedDate(date)
        }
    }

    fun selectDate(date: LocalDate) {
        if (selectedView.value == CalendarViewMode.Agenda) {
            agendaDataRange.update { AgendaWindowPolicy.recenterIfNeeded(it, date) }
        }
        if (selectedDate.value == date) return
        selectedDateOverride.value = date
        selectedDatePersistJob?.cancel()
        selectedDatePersistJob = viewModelScope.launch {
            delay(700)
            settingsStore.setSelectedDate(date)
        }
    }

    fun recenterToToday(today: LocalDate = LocalDate.now(zoneId)) {
        if (selectedView.value == CalendarViewMode.Agenda) {
            agendaDataRange.update { AgendaWindowPolicy.recenterIfNeeded(it, today) }
        }
        selectedDateOverride.value = today
        dateNavigationSerial.update { it + 1 }
        uiEventChannel.trySend(CalendarUiEvent.ForegroundRecentered)
        selectedDatePersistJob?.cancel()
        selectedDatePersistJob = viewModelScope.launch {
            settingsStore.setSelectedDate(today)
        }
    }

    fun loadEarlierAgenda() {
        agendaDataRange.update(AgendaWindowPolicy::extendEarlier)
    }

    fun loadLaterAgenda() {
        agendaDataRange.update(AgendaWindowPolicy::extendLater)
    }

    private fun suppressAutomaticRecenterForExplicitLaunch() {
        explicitLaunchSuppressionUntilMillis = System.currentTimeMillis() + EXPLICIT_LAUNCH_SUPPRESSION_MILLIS
    }

    fun setDeviceOrientation(landscape: Boolean) {
        isLandscape.value = landscape
        useLandscapeEntryView.value = landscape
    }

    fun today() {
        selectDate(LocalDate.now())
    }

    fun messageShown() {
        message.update { null }
    }

    private companion object {
        const val EXPLICIT_LAUNCH_SUPPRESSION_MILLIS = 15_000L
    }

}

private fun LocalDate.multiDayDataRange(dayCount: Int): CalendarRange {
    val monthStart = withDayOfMonth(1)
    val forwardMonths = if (dayCount.coerceMultiDayCount() > DEFAULT_MULTI_DAY_COUNT) 4L else 3L
    return CalendarRange(
        startDate = monthStart.minusMonths(1),
        endExclusiveDate = monthStart.plusMonths(forwardMonths),
    )
}

internal suspend fun applyFirstDayOfWeekChange(
    dayOfWeek: DayOfWeek,
    activeWeekDate: () -> LocalDate?,
    persistFirstDayOfWeek: suspend (DayOfWeek) -> Unit,
    publishedFirstDayOfWeek: Flow<DayOfWeek>,
    selectDate: (LocalDate) -> Unit,
) {
    persistFirstDayOfWeek(dayOfWeek)
    publishedFirstDayOfWeek.first { it == dayOfWeek }
    activeWeekDate()?.let { selectDate(it.startOfWeek(dayOfWeek)) }
}

internal fun restorePersistedSelectedDate(
    storedDate: Flow<LocalDate>,
    storedView: Flow<CalendarViewMode>,
    weekViewEnabled: Flow<Boolean>,
    firstDayOfWeek: Flow<DayOfWeek>,
): Flow<LocalDate> = combine(
    storedDate,
    storedView,
    weekViewEnabled,
    firstDayOfWeek,
) { date, view, weekEnabled, weekStart ->
    timelineRestoreDate(date, view, weekEnabled, weekStart)
}.take(1)

@Suppress("UNCHECKED_CAST")
class CalendarViewModelFactory(
    private val graph: AppGraph,
    private val initialWidgetLaunchTarget: CalendarWidgetLaunchTarget? = null,
    private val initialCalendarLaunchTarget: CalendarLaunchTarget? = null,
    private val deliverInitialLaunchEvents: Boolean = true,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CalendarViewModel(
            repository = graph.repository,
            settingsStore = graph.settingsStore,
            sourceCalendarMutationCoordinator = graph.sourceCalendarMutationCoordinator,
            taskMutationCoordinator = graph.taskMutationCoordinator,
            calendarLaunchResolver = graph.calendarLaunchResolver,
            timelineViewportMemory = graph.timelineViewportMemory,
            widgetRefresher = graph.widgetRefresher,
            reminderRescheduler = graph.reminderRescheduler,
            appLifecycleSignals = graph.appLifecycleSignals,
            uiStrings = graph.uiStrings,
            initialWidgetLaunchTarget = initialWidgetLaunchTarget,
            initialCalendarLaunchTarget = initialCalendarLaunchTarget,
            deliverInitialLaunchEvents = deliverInitialLaunchEvents,
        ) as T
    }
}
