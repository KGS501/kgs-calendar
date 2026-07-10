package com.kgs.calendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.kgs.calendar.AppGraph
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.R
import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppLanguageMode
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.data.settings.WidgetColorMode
import com.kgs.calendar.data.settings.WidgetTaskCreateMode
import com.kgs.calendar.data.settings.WidgetTaskDisplayMode
import com.kgs.calendar.data.settings.WidgetTaskSortMode
import com.kgs.calendar.data.settings.WidgetTaskSubtaskDefaultMode
import com.kgs.calendar.data.settings.WidgetThemeMode
import com.kgs.calendar.domain.model.CalendarRange
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.DEFAULT_MULTI_DAY_COUNT
import com.kgs.calendar.domain.model.EventEditPayload
import com.kgs.calendar.domain.model.TaskEditPayload
import com.kgs.calendar.domain.model.coerceMultiDayCount
import com.kgs.calendar.domain.model.visibleRangeFor
import com.kgs.calendar.lifecycle.ForegroundRecenterPolicy
import com.kgs.calendar.reminder.ReminderScheduler
import com.kgs.calendar.reminder.TaskMutationCoordinator
import com.kgs.calendar.navigation.CalendarLaunchResolution
import com.kgs.calendar.navigation.CalendarLaunchResolver
import com.kgs.calendar.navigation.CalendarLaunchTarget
import com.kgs.calendar.sync.CalendarStructuralMutation
import com.kgs.calendar.sync.PostMutationStage
import com.kgs.calendar.sync.SourceCalendarMutationCoordinator
import com.kgs.calendar.sync.StructuralMutationResult
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.KgsWidgetUpdateScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class CalendarWidgetLaunchTarget(
    val date: LocalDate,
    val viewMode: CalendarViewMode,
    val createEvent: Boolean = false,
    val createTaskScheduled: Boolean? = null,
    val openEventUid: String? = null,
    val openTaskUid: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: CalendarRepository,
    private val settingsStore: SettingsStore,
    private val sourceCalendarMutationCoordinator: SourceCalendarMutationCoordinator,
    private val taskMutationCoordinator: TaskMutationCoordinator,
    private val calendarLaunchResolver: CalendarLaunchResolver,
    private val appContext: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    initialWidgetLaunchTarget: CalendarWidgetLaunchTarget? = null,
    initialCalendarLaunchTarget: CalendarLaunchTarget? = null,
) : ViewModel() {
    private val initialSelectedDate = initialCalendarLaunchTarget?.date ?: initialWidgetLaunchTarget?.date ?: LocalDate.now()
    private val initialSelectedView = initialCalendarLaunchTarget?.viewMode ?: initialWidgetLaunchTarget?.viewMode ?: CalendarViewMode.ThreeDay
    private val initialWidgetCreatesEvent = initialWidgetLaunchTarget?.createEvent == true
    private val initialWidgetCreatesTask = initialWidgetLaunchTarget?.createTaskScheduled != null
    private val initialWidgetOpenEventUid = initialWidgetLaunchTarget?.openEventUid?.takeIf { it.isNotBlank() }
    private val initialWidgetOpenTaskUid = initialWidgetLaunchTarget?.openTaskUid?.takeIf { it.isNotBlank() }
    private val initialWidgetSerial = if (initialWidgetLaunchTarget != null || initialCalendarLaunchTarget != null) 1 else 0
    private val busy = MutableStateFlow(false)
    private val manualSyncing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val externalLoginUrl = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val hiddenAndroidProviderCalendarNames = MutableStateFlow<List<String>>(emptyList())
    private val selectedViewOverride = MutableStateFlow<CalendarViewMode?>(
        initialCalendarLaunchTarget?.viewMode ?: initialWidgetLaunchTarget?.viewMode,
    )
    private val selectedDateOverride = MutableStateFlow<LocalDate?>(
        initialCalendarLaunchTarget?.date ?: initialWidgetLaunchTarget?.date,
    )
    private val dateNavigationSerial = MutableStateFlow(initialWidgetSerial)
    private val foregroundRecenterSerial = MutableStateFlow(0)
    private val widgetCreateEventDate = MutableStateFlow(if (initialWidgetCreatesEvent) initialSelectedDate else null)
    private val widgetCreateEventSerial = MutableStateFlow(if (initialWidgetCreatesEvent) initialWidgetSerial else 0)
    private val widgetCreateTaskDate = MutableStateFlow(if (initialWidgetCreatesTask) initialSelectedDate else null)
    private val widgetCreateTaskScheduled = MutableStateFlow(initialWidgetLaunchTarget?.createTaskScheduled ?: false)
    private val widgetCreateTaskSerial = MutableStateFlow(if (initialWidgetCreatesTask) initialWidgetSerial else 0)
    private val widgetOpenEventUid = MutableStateFlow(initialWidgetOpenEventUid)
    private val widgetOpenEventSerial = MutableStateFlow(if (initialWidgetOpenEventUid != null) initialWidgetSerial else 0)
    private val widgetOpenTaskUid = MutableStateFlow(initialWidgetOpenTaskUid)
    private val widgetOpenTaskSerial = MutableStateFlow(if (initialWidgetOpenTaskUid != null) initialWidgetSerial else 0)
    private val resolvedCalendarLaunch = MutableStateFlow<ResolvedCalendarLaunch?>(null)
    private var nextCalendarLaunchSerial = 0
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

    init {
        viewModelScope.launch {
            runCatching { repository.ensureLocalCalendar() }
                .onSuccess { message.value = null }
                .onFailure { message.value = it.message ?: "Could not prepare local calendar." }
        }
        initialCalendarLaunchTarget?.let(::openFromCalendarLaunch)
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
                        includeDisabledProviderCalendars = includeDisabledAndroidProviderCalendars(),
                    )
                    refreshAndroidProviderDiagnosticsInternal()
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
        (appContext.applicationContext as? KgsCalendarApplication)?.processForegroundedAt?.let { foregroundEvents ->
            viewModelScope.launch {
                foregroundEvents.collect { foregroundedAt ->
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
    }

    private val selectedView = combine(settingsStore.selectedView, selectedViewOverride) { stored, override -> override ?: stored }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSelectedView)
    private val storedSelectedDate = settingsStore.selectedDate
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSelectedDate)
    private val selectedDate = combine(storedSelectedDate, selectedDateOverride) { stored, override -> override ?: stored }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSelectedDate)
    private val hiddenCollectionHrefs = settingsStore.hiddenCollectionHrefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val multiDayCount = settingsStore.multiDayCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_MULTI_DAY_COUNT)
    private val visibleRange = combine(selectedDate, selectedView, multiDayCount) { date, view, dayCount ->
        visibleRangeFor(date, view, dayCount)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, visibleRangeFor(initialSelectedDate, initialSelectedView))
    private val dataRange = combine(selectedDate, selectedView, multiDayCount) { date, view, dayCount ->
        if (view == CalendarViewMode.ThreeDay) {
            date.multiDayDataRange(dayCount)
        } else {
            visibleRangeFor(date, view)
        }
    }.distinctUntilChanged()
    private val events = dataRange.flatMapLatest { range ->
        combine(
            repository.observeEvents(range.startMillis(zoneId), range.endMillis(zoneId)),
            hiddenCollectionHrefs,
        ) { events, hidden ->
            events.filterNot { it.collectionHref in hidden }
        }
    }
    private val datedTasks = dataRange.flatMapLatest { range ->
        combine(
            repository.observeDatedTasks(range.startMillis(zoneId), range.endMillis(zoneId)),
            hiddenCollectionHrefs,
        ) { tasks, hidden ->
            tasks.filterNot { it.collectionHref in hidden }
        }
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
    private val searchResults = searchQuery
        .flatMapLatest { query ->
            val trimmed = query.trim()
            if (trimmed.isBlank()) {
                flowOf(emptyList())
            } else {
                combine(repository.searchEvents(trimmed), hiddenCollectionHrefs) { events, hidden ->
                    events.filterNot { it.collectionHref in hidden }
                }
            }
        }
    private val searchTaskResults = searchQuery
        .flatMapLatest { query ->
            val trimmed = query.trim()
            if (trimmed.isBlank()) {
                flowOf(emptyList())
            } else {
                combine(repository.searchTasks(trimmed), hiddenCollectionHrefs) { tasks, hidden ->
                    tasks.filterNot { it.collectionHref in hidden }
                }
            }
        }

    private val initialUiState = CalendarUiState(
        selectedDate = initialSelectedDate,
        dateNavigationSerial = initialWidgetSerial,
        widgetCreateEventDate = if (initialWidgetCreatesEvent) initialSelectedDate else null,
        widgetCreateEventSerial = if (initialWidgetCreatesEvent) initialWidgetSerial else 0,
        widgetCreateTaskDate = if (initialWidgetCreatesTask) initialSelectedDate else null,
        widgetCreateTaskScheduled = initialWidgetLaunchTarget?.createTaskScheduled ?: false,
        widgetCreateTaskSerial = if (initialWidgetCreatesTask) initialWidgetSerial else 0,
        widgetOpenEventUid = initialWidgetOpenEventUid,
        widgetOpenEventSerial = if (initialWidgetOpenEventUid != null) initialWidgetSerial else 0,
        widgetOpenTaskUid = initialWidgetOpenTaskUid,
        widgetOpenTaskSerial = if (initialWidgetOpenTaskUid != null) initialWidgetSerial else 0,
        selectedView = initialSelectedView,
        visibleRange = visibleRangeFor(initialSelectedDate, initialSelectedView),
    )

    val uiState: StateFlow<CalendarUiState> = combine(
        repository.observeAccount(),
        repository.observeAccounts(),
        repository.observeCollections(),
        events,
        searchQuery,
        searchResults,
        searchTaskResults,
        datedTasks,
        inboxTasks,
        scheduledOpenTasks,
        repository.observePendingMutationCount(),
        repository.observePendingMutations(),
        repository.observeProblemResources(),
        selectedDate,
        selectedView,
        settingsStore.themeMode,
        settingsStore.colorMode,
        settingsStore.taskColorMode,
        settingsStore.focusTitleOnCreate,
        settingsStore.firstDayOfWeek,
        settingsStore.showCompletedTasksInCalendar,
        settingsStore.priorityAnimationsEnabled,
        settingsStore.autoLoadMapPreviews,
        settingsStore.maxVisibleAllDayItems,
        settingsStore.defaultEventDurationMinutes,
        settingsStore.defaultTaskHasDate,
        settingsStore.defaultTaskHasTime,
        settingsStore.taskStartNotificationsEnabled,
        settingsStore.taskEndNotificationsEnabled,
        settingsStore.eventStartNotificationsEnabled,
        settingsStore.eventEndNotificationsEnabled,
        settingsStore.defaultEventCollectionHref,
        settingsStore.defaultTaskCollectionHref,
        completedTasks,
        visibleRange,
        busy,
        message,
        externalLoginUrl,
        settingsStore.eventFieldOrder,
        settingsStore.taskFieldOrder,
        settingsStore.languageMode,
        manualSyncing,
        hiddenAndroidProviderCalendarNames,
        settingsStore.welcomeCompleted,
        settingsStore.showDisabledAndroidProviderCalendars,
        hiddenCollectionHrefs,
        settingsStore.subtasksExpandedByDefault,
        settingsStore.defaultEventReminderMinutes,
        settingsStore.defaultTaskReminderMinutes,
        repository.observeProblemEvents(),
        repository.observeProblemTasks(),
        multiDayCount,
        settingsStore.multiDaySidebarControlsEnabled,
        dateNavigationSerial,
        widgetCreateEventDate,
        widgetCreateEventSerial,
        settingsStore.monthWidgetColorMode,
        settingsStore.monthWidgetThemeMode,
        widgetCreateTaskDate,
        widgetCreateTaskScheduled,
        widgetCreateTaskSerial,
        widgetOpenEventUid,
        widgetOpenEventSerial,
        widgetOpenTaskUid,
        widgetOpenTaskSerial,
        settingsStore.tasksWidgetDisplayMode,
        settingsStore.tasksWidgetIncludeOverdue,
        settingsStore.tasksWidgetSortMode,
        settingsStore.tasksWidgetCreateMode,
        settingsStore.tasksWidgetSubtaskDefaultMode,
        settingsStore.dayWidgetScalePercent,
        settingsStore.dayWidgetStartHour,
        settingsStore.dayWidgetStartAtCurrentHour,
        settingsStore.agendaWidgetColorMode,
        settingsStore.agendaWidgetThemeMode,
        settingsStore.tasksWidgetColorMode,
        settingsStore.tasksWidgetThemeMode,
        settingsStore.dayWidgetColorMode,
        settingsStore.dayWidgetThemeMode,
        settingsStore.multiWidgetColorMode,
        settingsStore.multiWidgetThemeMode,
        settingsStore.multiWidgetMonthPercent,
    ) { values ->
        CalendarUiState(
            initialDataLoaded = true,
            account = values[0] as com.kgs.calendar.data.local.entity.AccountEntity?,
            accounts = values[1] as List<com.kgs.calendar.data.local.entity.AccountEntity>,
            collections = values[2] as List<com.kgs.calendar.data.local.entity.CollectionEntity>,
            events = values[3] as List<com.kgs.calendar.data.local.entity.EventEntity>,
            searchQuery = values[4] as String,
            searchResults = values[5] as List<com.kgs.calendar.data.local.entity.EventEntity>,
            searchTaskResults = values[6] as List<com.kgs.calendar.data.local.entity.TaskEntity>,
            datedTasks = values[7] as List<com.kgs.calendar.data.local.entity.TaskEntity>,
            inboxTasks = values[8] as List<com.kgs.calendar.data.local.entity.TaskEntity>,
            scheduledOpenTasks = values[9] as List<com.kgs.calendar.data.local.entity.TaskEntity>,
            pendingMutations = values[10] as Int,
            pendingMutationItems = values[11] as List<com.kgs.calendar.data.local.entity.PendingMutationEntity>,
            problemResources = values[12] as List<com.kgs.calendar.data.local.entity.CalendarResourceEntity>,
            selectedDate = values[13] as LocalDate,
            selectedView = values[14] as CalendarViewMode,
            themeMode = values[15] as AppThemeMode,
            colorMode = values[16] as AppColorMode,
            taskColorMode = values[17] as TaskColorMode,
            focusTitleOnCreate = values[18] as Boolean,
            firstDayOfWeek = values[19] as DayOfWeek,
            showCompletedTasksInCalendar = values[20] as Boolean,
            priorityAnimationsEnabled = values[21] as Boolean,
            autoLoadMapPreviews = values[22] as Boolean,
            maxVisibleAllDayItems = values[23] as Int,
            defaultEventDurationMinutes = values[24] as Int,
            defaultTaskHasDate = values[25] as Boolean,
            defaultTaskHasTime = values[26] as Boolean,
            taskStartNotificationsEnabled = values[27] as Boolean,
            taskEndNotificationsEnabled = values[28] as Boolean,
            eventStartNotificationsEnabled = values[29] as Boolean,
            eventEndNotificationsEnabled = values[30] as Boolean,
            defaultEventCollectionHref = values[31] as String?,
            defaultTaskCollectionHref = values[32] as String?,
            completedTasks = values[33] as List<com.kgs.calendar.data.local.entity.TaskEntity>,
            visibleRange = values[34] as com.kgs.calendar.domain.model.CalendarRange,
            isBusy = values[35] as Boolean,
            isManualSyncing = values[41] as Boolean,
            message = values[36] as String?,
            externalLoginUrl = values[37] as String?,
            eventFieldOrder = values[38] as List<String>,
            taskFieldOrder = values[39] as List<String>,
            languageMode = values[40] as AppLanguageMode,
            hiddenAndroidProviderCalendarNames = values[42] as List<String>,
            welcomeCompleted = values[43] as Boolean,
            showDisabledAndroidProviderCalendars = values[44] as Boolean,
            hiddenCollectionHrefs = values[45] as Set<String>,
            subtasksExpandedByDefault = values[46] as Boolean,
            defaultEventReminderMinutes = values[47] as Set<Int>,
            defaultTaskReminderMinutes = values[48] as Set<Int>,
            problemEvents = values[49] as List<com.kgs.calendar.data.local.entity.EventEntity>,
            problemTasks = values[50] as List<com.kgs.calendar.data.local.entity.TaskEntity>,
            multiDayCount = values[51] as Int,
            multiDaySidebarControlsEnabled = values[52] as Boolean,
            dateNavigationSerial = values[53] as Int,
            widgetCreateEventDate = values[54] as LocalDate?,
            widgetCreateEventSerial = values[55] as Int,
            monthWidgetColorMode = values[56] as WidgetColorMode,
            monthWidgetThemeMode = values[57] as WidgetThemeMode,
            widgetCreateTaskDate = values[58] as LocalDate?,
            widgetCreateTaskScheduled = values[59] as Boolean,
            widgetCreateTaskSerial = values[60] as Int,
            widgetOpenEventUid = values[61] as String?,
            widgetOpenEventSerial = values[62] as Int,
            widgetOpenTaskUid = values[63] as String?,
            widgetOpenTaskSerial = values[64] as Int,
            tasksWidgetDisplayMode = values[65] as WidgetTaskDisplayMode,
            tasksWidgetIncludeOverdue = values[66] as Boolean,
            tasksWidgetSortMode = values[67] as WidgetTaskSortMode,
            tasksWidgetCreateMode = values[68] as WidgetTaskCreateMode,
            tasksWidgetSubtaskDefaultMode = values[69] as WidgetTaskSubtaskDefaultMode,
            dayWidgetScalePercent = values[70] as Int,
            dayWidgetStartHour = values[71] as Int,
            dayWidgetStartAtCurrentHour = values[72] as Boolean,
            agendaWidgetColorMode = values[73] as WidgetColorMode,
            agendaWidgetThemeMode = values[74] as WidgetThemeMode,
            tasksWidgetColorMode = values[75] as WidgetColorMode,
            tasksWidgetThemeMode = values[76] as WidgetThemeMode,
            dayWidgetColorMode = values[77] as WidgetColorMode,
            dayWidgetThemeMode = values[78] as WidgetThemeMode,
            multiWidgetColorMode = values[79] as WidgetColorMode,
            multiWidgetThemeMode = values[80] as WidgetThemeMode,
            multiWidgetMonthPercent = values[81] as Int,
        )
    }
        .combine(initialDataReady) { uiState, ready ->
            uiState.copy(initialDataLoaded = ready)
        }
        .combine(resolvedCalendarLaunch) { uiState, resolved ->
            uiState.copy(
                calendarLaunchEvent = resolved?.resolution?.event,
                calendarLaunchTask = resolved?.resolution?.task,
                calendarLaunchSerial = resolved?.serial ?: 0,
            )
        }
        .combine(foregroundRecenterSerial) { uiState, serial ->
            uiState.copy(foregroundRecenterSerial = serial)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialUiState)

    fun selectView(viewMode: CalendarViewMode) {
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
        selectedDateOverride.value = date
        dateNavigationSerial.update { it + 1 }
        if (createEvent) {
            widgetCreateEventDate.value = date
            widgetCreateEventSerial.update { it + 1 }
        }
        if (createTaskScheduled != null) {
            widgetCreateTaskDate.value = date
            widgetCreateTaskScheduled.value = createTaskScheduled
            widgetCreateTaskSerial.update { it + 1 }
        }
        if (!openEventUid.isNullOrBlank()) {
            widgetOpenEventUid.value = openEventUid
            widgetOpenEventSerial.update { it + 1 }
        }
        if (!openTaskUid.isNullOrBlank()) {
            widgetOpenTaskUid.value = openTaskUid
            widgetOpenTaskSerial.update { it + 1 }
        }
        persistWidgetSelection(date, viewMode)
    }

    fun openFromCalendarLaunch(target: CalendarLaunchTarget) {
        suppressAutomaticRecenterForExplicitLaunch()
        viewModelScope.launch {
            val resolution = calendarLaunchResolver.resolve(target) ?: return@launch
            selectedViewOverride.value = resolution.viewMode
            selectedDateOverride.value = resolution.date
            dateNavigationSerial.update { it + 1 }
            nextCalendarLaunchSerial += 1
            resolvedCalendarLaunch.value = ResolvedCalendarLaunch(nextCalendarLaunchSerial, resolution)
            persistWidgetSelection(resolution.date, resolution.viewMode)
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
        if (selectedDate.value == date) return
        selectedDateOverride.value = date
        selectedDatePersistJob?.cancel()
        selectedDatePersistJob = viewModelScope.launch {
            delay(700)
            settingsStore.setSelectedDate(date)
        }
    }

    fun recenterToToday(today: LocalDate = LocalDate.now(zoneId)) {
        selectedDateOverride.value = today
        dateNavigationSerial.update { it + 1 }
        foregroundRecenterSerial.update { it + 1 }
        selectedDatePersistJob?.cancel()
        selectedDatePersistJob = viewModelScope.launch {
            settingsStore.setSelectedDate(today)
        }
    }

    private fun suppressAutomaticRecenterForExplicitLaunch() {
        explicitLaunchSuppressionUntilMillis = System.currentTimeMillis() + EXPLICIT_LAUNCH_SUPPRESSION_MILLIS
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            settingsStore.setThemeMode(mode)
            KgsWidgetUpdateScheduler.updateAll(appContext)
        }
    }

    fun setColorMode(mode: AppColorMode) {
        viewModelScope.launch {
            settingsStore.setColorMode(mode)
            KgsWidgetUpdateScheduler.updateAll(appContext)
        }
    }

    fun setMonthWidgetThemeMode(mode: WidgetThemeMode) {
        viewModelScope.launch {
            settingsStore.setMonthWidgetThemeMode(mode)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Month)
        }
    }

    fun setMonthWidgetColorMode(mode: WidgetColorMode) {
        viewModelScope.launch {
            settingsStore.setMonthWidgetColorMode(mode)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Month)
        }
    }

    fun setAgendaWidgetThemeMode(mode: WidgetThemeMode) {
        viewModelScope.launch {
            settingsStore.setAgendaWidgetThemeMode(mode)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Agenda)
        }
    }

    fun setAgendaWidgetColorMode(mode: WidgetColorMode) {
        viewModelScope.launch {
            settingsStore.setAgendaWidgetColorMode(mode)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Agenda)
        }
    }

    fun setTasksWidgetThemeMode(mode: WidgetThemeMode) {
        viewModelScope.launch {
            settingsStore.setTasksWidgetThemeMode(mode)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Tasks)
        }
    }

    fun setTasksWidgetColorMode(mode: WidgetColorMode) {
        viewModelScope.launch {
            settingsStore.setTasksWidgetColorMode(mode)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Tasks)
        }
    }

    fun setDayWidgetThemeMode(mode: WidgetThemeMode) {
        viewModelScope.launch {
            settingsStore.setDayWidgetThemeMode(mode)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Day, forceFullDayUpdate = true)
        }
    }

    fun setDayWidgetColorMode(mode: WidgetColorMode) {
        viewModelScope.launch {
            settingsStore.setDayWidgetColorMode(mode)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Day, forceFullDayUpdate = true)
        }
    }

    fun setMultiWidgetThemeMode(mode: WidgetThemeMode) {
        viewModelScope.launch {
            settingsStore.setMultiWidgetThemeMode(mode)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Multi)
        }
    }

    fun setMultiWidgetColorMode(mode: WidgetColorMode) {
        viewModelScope.launch {
            settingsStore.setMultiWidgetColorMode(mode)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Multi)
        }
    }

    fun setMultiWidgetMonthPercent(monthPercent: Int) {
        viewModelScope.launch {
            settingsStore.setMultiWidgetMonthPercent(monthPercent)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Multi)
        }
    }

    fun setTasksWidgetDisplayMode(mode: WidgetTaskDisplayMode) {
        viewModelScope.launch {
            settingsStore.setTasksWidgetDisplayMode(mode)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Tasks)
        }
    }

    fun setTasksWidgetIncludeOverdue(include: Boolean) {
        viewModelScope.launch {
            settingsStore.setTasksWidgetIncludeOverdue(include)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Tasks)
        }
    }

    fun setTasksWidgetSortMode(mode: WidgetTaskSortMode) {
        viewModelScope.launch {
            settingsStore.setTasksWidgetSortMode(mode)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Tasks)
        }
    }

    fun setTasksWidgetCreateMode(mode: WidgetTaskCreateMode) {
        viewModelScope.launch {
            settingsStore.setTasksWidgetCreateMode(mode)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Tasks)
        }
    }

    fun setTasksWidgetSubtaskDefaultMode(mode: WidgetTaskSubtaskDefaultMode) {
        viewModelScope.launch {
            settingsStore.setTasksWidgetSubtaskDefaultMode(mode)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Tasks)
        }
    }

    fun setDayWidgetScalePercent(scalePercent: Int) {
        viewModelScope.launch {
            settingsStore.setDayWidgetScalePercent(scalePercent)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Day, forceFullDayUpdate = true)
        }
    }

    fun setDayWidgetStartHour(startHour: Int) {
        viewModelScope.launch {
            settingsStore.setDayWidgetStartHour(startHour)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Day, forceFullDayUpdate = true)
        }
    }

    fun setDayWidgetStartAtCurrentHour(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setDayWidgetStartAtCurrentHour(enabled)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Day, forceFullDayUpdate = true)
        }
    }

    fun setLanguageMode(mode: AppLanguageMode) {
        viewModelScope.launch { settingsStore.setLanguageMode(mode) }
    }

    fun setTaskColorMode(mode: TaskColorMode) {
        viewModelScope.launch {
            settingsStore.setTaskColorMode(mode)
            KgsWidgetUpdateScheduler.updateAll(appContext)
        }
    }

    fun setFocusTitleOnCreate(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setFocusTitleOnCreate(enabled) }
    }

    fun setFirstDayOfWeek(dayOfWeek: DayOfWeek) {
        viewModelScope.launch { settingsStore.setFirstDayOfWeek(dayOfWeek) }
    }

    fun setShowCompletedTasksInCalendar(show: Boolean) {
        viewModelScope.launch { settingsStore.setShowCompletedTasksInCalendar(show) }
    }

    fun setPriorityAnimationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setPriorityAnimationsEnabled(enabled)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Tasks)
        }
    }

    fun setSubtasksExpandedByDefault(expanded: Boolean) {
        viewModelScope.launch {
            settingsStore.setSubtasksExpandedByDefault(expanded)
            KgsWidgetUpdateScheduler.update(appContext, KgsWidgetKind.Tasks)
        }
    }

    fun setAutoLoadMapPreviews(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoLoadMapPreviews(enabled) }
    }

    fun setMaxVisibleAllDayItems(maxItems: Int) {
        viewModelScope.launch { settingsStore.setMaxVisibleAllDayItems(maxItems) }
    }

    fun setMultiDayCount(count: Int) {
        viewModelScope.launch { settingsStore.setMultiDayCount(count.coerceMultiDayCount()) }
    }

    fun setMultiDaySidebarControlsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setMultiDaySidebarControlsEnabled(enabled) }
    }

    fun setDefaultEventDurationMinutes(minutes: Int) {
        viewModelScope.launch { settingsStore.setDefaultEventDurationMinutes(minutes) }
    }

    fun setDefaultTaskHasDate(hasDate: Boolean) {
        viewModelScope.launch { settingsStore.setDefaultTaskHasDate(hasDate) }
    }

    fun setDefaultTaskHasTime(hasTime: Boolean) {
        viewModelScope.launch { settingsStore.setDefaultTaskHasTime(hasTime) }
    }

    fun setDefaultEventReminderMinutes(reminders: Set<Int>) {
        viewModelScope.launch { settingsStore.setDefaultEventReminderMinutes(reminders) }
    }

    fun setDefaultTaskReminderMinutes(reminders: Set<Int>) {
        viewModelScope.launch { settingsStore.setDefaultTaskReminderMinutes(reminders) }
    }

    fun setTaskStartNotificationsEnabled(enabled: Boolean) {
        updateNotificationSetting { settingsStore.setTaskStartNotificationsEnabled(enabled) }
    }

    fun setTaskEndNotificationsEnabled(enabled: Boolean) {
        updateNotificationSetting { settingsStore.setTaskEndNotificationsEnabled(enabled) }
    }

    fun setEventStartNotificationsEnabled(enabled: Boolean) {
        updateNotificationSetting { settingsStore.setEventStartNotificationsEnabled(enabled) }
    }

    fun setEventEndNotificationsEnabled(enabled: Boolean) {
        updateNotificationSetting { settingsStore.setEventEndNotificationsEnabled(enabled) }
    }

    fun setDefaultEventCollectionHref(href: String?) {
        viewModelScope.launch { settingsStore.setDefaultEventCollectionHref(href) }
    }

    fun setDefaultTaskCollectionHref(href: String?) {
        viewModelScope.launch { settingsStore.setDefaultTaskCollectionHref(href) }
    }

    fun setEventFieldOrder(order: List<String>) {
        viewModelScope.launch { settingsStore.setEventFieldOrder(order) }
    }

    fun setTaskFieldOrder(order: List<String>) {
        viewModelScope.launch { settingsStore.setTaskFieldOrder(order) }
    }

    fun completeWelcome() {
        viewModelScope.launch { settingsStore.setWelcomeCompleted(true) }
    }

    fun refreshAndroidProviderDiagnostics() {
        viewModelScope.launch {
            runCatching { refreshAndroidProviderDiagnosticsInternal() }
        }
    }

    fun setDisabledAndroidProviderCalendarsVisible(visible: Boolean) {
        runBusy(rescheduleReminders = true) {
            settingsStore.setShowDisabledAndroidProviderCalendars(visible)
            if (visible) {
                repository.enableAndroidCalendars(includeDisabledProviderCalendars = true)
            } else {
                repository.refreshAndroidCalendarsIfEnabled(
                    removeStale = true,
                    includeDisabledProviderCalendars = false,
                )
            }
            refreshAndroidProviderDiagnosticsInternal()
            message.value = if (visible) {
                "Disabled Android provider calendars are visible."
            } else {
                "Disabled Android provider calendars are hidden."
            }
        }
    }

    fun applyCollectionOrder(hrefs: List<String>) {
        viewModelScope.launch { repository.applyCollectionOrder(hrefs) }
    }

    fun today() {
        selectDate(LocalDate.now())
    }

    fun movePeriod(delta: Long) {
        val currentDate = uiState.value.selectedDate
        val currentView = uiState.value.selectedView
        val next = when (currentView) {
            CalendarViewMode.Month -> currentDate.plusMonths(delta)
            CalendarViewMode.ThreeDay -> currentDate.plusDays(uiState.value.multiDayCount.coerceMultiDayCount().toLong() * delta)
            CalendarViewMode.Day -> currentDate.plusDays(delta)
            CalendarViewMode.Agenda -> currentDate.plusWeeks(delta)
            CalendarViewMode.Tasks -> currentDate.plusWeeks(delta)
        }
        selectDate(next)
    }

    fun manualLogin(
        serverUrl: String,
        username: String,
        appPassword: String,
        onResult: ((Boolean, String?) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            busy.value = true
            val saved = runCatching {
                sourceCalendarMutationCoordinator.run(CalendarStructuralMutation.AddSource) {
                    repository.saveManualAccount(serverUrl, username, appPassword)
                }
            }
            saved.onFailure {
                val errorMessage = it.message ?: "Could not verify this CalDAV login."
                message.value = errorMessage
                busy.value = false
                onResult?.invoke(false, errorMessage)
            }
            if (saved.isFailure) {
                return@launch
            }
            refreshAndroidProviderDiagnosticsInternal()
            val resultMessage = structuralMutationMessage(saved.getOrThrow(), "CalDAV account synced.")
            message.value = resultMessage
            busy.value = false
            onResult?.invoke(true, resultMessage)
        }
    }

    fun startBrowserLogin(serverUrl: String) {
        runStructuralMutation(CalendarStructuralMutation.AddSource, "Login complete.") {
            val login = repository.startLoginFlow(serverUrl)
            externalLoginUrl.value = login.loginUrl
            repository.completeLoginFlow(login.pollEndpoint, login.token)
        }
    }

    fun addReadOnlyCalendar(url: String) {
        runStructuralMutation(CalendarStructuralMutation.AddSource, "Read-only calendar added.", showManualSync = true) {
            repository.addReadOnlyCalendar(url)
        }
    }

    fun addAndroidDeviceCalendars() {
        runStructuralMutation(CalendarStructuralMutation.AddSource, "Android device calendars added.", showManualSync = true) {
            repository.enableAndroidCalendars(
                includeDisabledProviderCalendars = includeDisabledAndroidProviderCalendars(),
            )
            (appContext.applicationContext as? KgsCalendarApplication)?.registerAndroidCalendarObserverIfPermitted()
        }
    }

    fun renameAccount(accountId: String, displayName: String) {
        runStructuralMutation(CalendarStructuralMutation.EditSource, "Source renamed.") {
            repository.renameAccount(accountId, displayName)
        }
    }

    fun updateAccount(accountId: String, displayName: String, serverUrl: String, username: String, appPassword: String?) {
        runStructuralMutation(CalendarStructuralMutation.EditSource, "Source updated.") {
            repository.updateAccount(accountId, displayName, serverUrl, username, appPassword)
        }
    }

    fun deleteAccount(accountId: String) {
        runStructuralMutation(CalendarStructuralMutation.RemoveSource, "Source removed.") {
            repository.deleteAccount(accountId)
        }
    }

    fun externalLoginUrlConsumed() {
        externalLoginUrl.value = null
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun syncNow() {
        runBusy(rescheduleReminders = true, showManualSync = true) {
            repository.syncNow(
                includeDisabledProviderCalendars = includeDisabledAndroidProviderCalendars(),
                forceFullCalDavRefresh = true,
            )
            refreshAndroidProviderDiagnosticsInternal()
            message.value = "Sync complete."
        }
    }

    fun createEvent(payload: EventEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.createEvent(payload)
        }
    }

    fun updateEvent(uid: String, payload: EventEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.updateEvent(uid, payload)
        }
    }

    fun updateEventOccurrence(uid: String, occurrenceStartMillis: Long, payload: EventEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.updateEventOccurrence(uid, occurrenceStartMillis, payload)
        }
    }

    fun updateEventFollowing(uid: String, occurrenceStartMillis: Long, payload: EventEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.updateEventFollowing(uid, occurrenceStartMillis, payload)
        }
    }

    fun updateEventManualColor(uid: String, manualColor: Int?) {
        runEdit {
            repository.updateEventManualColor(uid, manualColor)
            message.value = "Event color updated."
        }
    }

    fun moveTimedEvent(uid: String, occurrenceStartMillis: Long, date: LocalDate, start: LocalTime, end: LocalTime) {
        runEdit(rescheduleReminders = true) {
            repository.moveTimedEvent(uid, occurrenceStartMillis, date, start, end)
        }
    }

    fun moveAllDayEvent(uid: String, occurrenceStartMillis: Long, date: LocalDate) {
        runEdit(rescheduleReminders = true) {
            repository.moveAllDayEvent(uid, occurrenceStartMillis, date)
        }
    }

    fun copyEventTo(uid: String, collectionHref: String) {
        runEdit(rescheduleReminders = true) {
            repository.copyEventTo(uid, collectionHref)
        }
    }

    fun setEventParticipation(uid: String, partstat: String) {
        val attendeeEmails = uiState.value.accounts.map { it.username } + listOfNotNull(uiState.value.account?.username)
        runEdit(rescheduleReminders = true) {
            repository.setEventParticipation(uid, attendeeEmails, partstat)
        }
    }

    fun createTask(payload: TaskEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.createTask(payload)
        }
    }

    fun convertEventToTask(eventUid: String, payload: TaskEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.createTask(payload)
            repository.deleteEvent(eventUid)
        }
    }

    fun convertTaskToEvent(taskUid: String, payload: EventEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.createEvent(payload)
            repository.deleteTask(taskUid)
        }
    }

    fun updateTask(resourceHref: String, payload: TaskEditPayload) {
        runTaskStatusMutation {
            taskMutationCoordinator.mutateStatus(resourceHref, effectiveTaskStatus(resourceHref, payload)) {
                repository.updateTask(resourceHref, payload)
            }
        }
    }

    fun updateTaskOccurrence(resourceHref: String, occurrenceStartMillis: Long, payload: TaskEditPayload) {
        runTaskStatusMutation {
            taskMutationCoordinator.mutateStatus(
                resourceHref = resourceHref,
                status = effectiveTaskStatus(resourceHref, payload),
                occurrenceId = CalendarOccurrenceId.Task(resourceHref, occurrenceStartMillis),
            ) {
                repository.updateTaskOccurrence(resourceHref, occurrenceStartMillis, payload)
            }
        }
    }

    fun updateTaskFollowing(resourceHref: String, occurrenceStartMillis: Long, payload: TaskEditPayload) {
        runTaskStatusMutation {
            taskMutationCoordinator.mutateStatus(resourceHref, effectiveTaskStatus(resourceHref, payload)) {
                repository.updateTaskFollowing(resourceHref, occurrenceStartMillis, payload)
            }
        }
    }

    fun updateTaskManualColor(uid: String, manualColor: Int?) {
        runEdit {
            repository.updateTaskManualColor(uid, manualColor)
            message.value = "Task color updated."
        }
    }

    fun setTaskCompleted(resourceHref: String, completed: Boolean) {
        runTaskStatusMutation {
            taskMutationCoordinator.setStatus(
                resourceHref,
                if (completed) "COMPLETED" else "NEEDS-ACTION",
            )
        }
    }

    fun setTaskStatus(resourceHref: String, status: String) {
        runTaskStatusMutation {
            taskMutationCoordinator.setStatus(resourceHref, status)
        }
    }

    fun setTaskPriority(uid: String, priority: Int) {
        runEdit(rescheduleReminders = true) {
            repository.setTaskPriority(uid, priority)
        }
    }

    fun setTaskProgress(uid: String, progress: Int) {
        runEdit(rescheduleReminders = true) {
            repository.setTaskProgress(uid, progress)
        }
    }

    fun moveTimedTask(uid: String, occurrenceStartMillis: Long, date: LocalDate, start: LocalTime, end: LocalTime) {
        runEdit(rescheduleReminders = true) {
            repository.moveTimedTask(uid, occurrenceStartMillis, date, start, end)
        }
    }

    fun moveAllDayTask(uid: String, occurrenceStartMillis: Long, date: LocalDate) {
        runEdit(rescheduleReminders = true) {
            repository.moveAllDayTask(uid, occurrenceStartMillis, date)
        }
    }

    fun copyTaskTo(uid: String, collectionHref: String) {
        runEdit(rescheduleReminders = true) {
            repository.copyTaskTo(uid, collectionHref)
        }
    }

    fun setCollectionEnabled(href: String, enabled: Boolean) {
        val kind = if (enabled) {
            CalendarStructuralMutation.EnableCalendar
        } else {
            CalendarStructuralMutation.DisableCalendar
        }
        runStructuralMutation(kind) {
            repository.setCollectionEnabled(href, enabled)
        }
    }

    fun setCollectionVisibleInViews(href: String, visible: Boolean) {
        viewModelScope.launch {
            settingsStore.setCollectionHiddenInViews(href, hidden = !visible)
        }
    }

    fun updateCollectionAppearance(href: String, displayName: String, customColor: Int?) {
        runStructuralMutation(CalendarStructuralMutation.EditCalendar) {
            repository.updateCollectionAppearance(href, displayName, customColor)
        }
    }

    fun createCalDavCalendar(
        accountId: String,
        displayName: String,
        supportsEvents: Boolean,
        supportsTasks: Boolean,
    ) {
        runStructuralMutation(CalendarStructuralMutation.AddCalendar, "CalDAV calendar created.") {
            repository.createCalDavCalendar(
                accountId = accountId,
                displayName = displayName,
                color = null,
                supportsEvents = supportsEvents,
                supportsTasks = supportsTasks,
            )
        }
    }

    fun deleteCalDavCalendar(href: String) {
        runStructuralMutation(CalendarStructuralMutation.RemoveCalendar, "CalDAV calendar deleted.") {
            repository.deleteCalDavCalendar(href)
        }
    }

    fun deleteTask(uid: String) {
        runEdit(rescheduleReminders = true) {
            repository.deleteTask(uid)
        }
    }

    fun deleteEvent(uid: String) {
        runEdit(rescheduleReminders = true) {
            repository.deleteEvent(uid)
        }
    }

    fun deleteEventOccurrence(uid: String, occurrenceStartMillis: Long) {
        runEdit(rescheduleReminders = true) {
            repository.deleteEventOccurrence(uid, occurrenceStartMillis)
        }
    }

    fun deleteEventFollowing(uid: String, occurrenceStartMillis: Long) {
        runEdit(rescheduleReminders = true) {
            repository.deleteEventFollowing(uid, occurrenceStartMillis)
        }
    }

    fun messageShown() {
        message.update { null }
    }

    private fun runBusy(rescheduleReminders: Boolean = false, showManualSync: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            if (showManualSync) manualSyncing.value = true
            runCatching { block() }
                .onSuccess { message.value = null }
                .onFailure { message.value = it.message ?: "Something went wrong." }
            if (rescheduleReminders) {
                runCatching { ReminderScheduler.reschedule(appContext) }
            }
            if (showManualSync) manualSyncing.value = false
            busy.value = false
        }
    }

    private fun runEdit(rescheduleReminders: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            runCatching {
                block()
                repository.pushPendingChangesCreatedSince(startedAt)
            }.onSuccess {
                message.value = null
                KgsWidgetUpdateScheduler.updateAll(appContext)
            }.onFailure {
                message.value = it.message ?: "Could not save changes."
            }
            if (rescheduleReminders) {
                runCatching { ReminderScheduler.reschedule(appContext) }
            }
        }
    }

    private fun runTaskStatusMutation(mutation: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { mutation() }
                .onSuccess { message.value = null }
                .onFailure { message.value = it.message ?: "Could not save changes." }
        }
    }

    private fun effectiveTaskStatus(resourceHref: String, payload: TaskEditPayload): String {
        payload.status?.takeIf { it.isNotBlank() }?.let { return it }
        val existing = uiState.value.allTasks.firstOrNull { it.resourceHref == resourceHref }
        existing?.status?.takeIf { payload.isCompleted == existing.isCompleted }?.let { return it }
        return if (payload.isCompleted) "COMPLETED" else "NEEDS-ACTION"
    }

    private fun runStructuralMutation(
        kind: CalendarStructuralMutation,
        successMessage: String? = null,
        showManualSync: Boolean = false,
        mutation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            busy.value = true
            if (showManualSync) manualSyncing.value = true
            runCatching {
                sourceCalendarMutationCoordinator.run(kind, mutation)
            }.onSuccess { result ->
                refreshAndroidProviderDiagnosticsInternal()
                message.value = structuralMutationMessage(result, successMessage)
            }.onFailure {
                message.value = it.message ?: "Could not update calendar settings."
            }
            if (showManualSync) manualSyncing.value = false
            busy.value = false
        }
    }

    private fun structuralMutationMessage(
        result: StructuralMutationResult,
        successMessage: String?,
    ): String? = when (result) {
        StructuralMutationResult.Complete -> successMessage
        is StructuralMutationResult.SavedWithFollowUpFailure -> when (result.stage) {
            PostMutationStage.Refresh -> appContext.getString(R.string.calendar_settings_saved_refresh_failed)
            PostMutationStage.Reconciliation -> appContext.getString(R.string.calendar_settings_saved_reconciliation_failed)
        }
    }

    private fun updateNotificationSetting(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching {
                block()
                ReminderScheduler.reschedule(appContext)
                message.value = null
            }.onFailure {
                message.value = it.message ?: "Benachrichtigungen konnten nicht aktualisiert werden."
            }
        }
    }

    private suspend fun includeDisabledAndroidProviderCalendars(): Boolean =
        settingsStore.showDisabledAndroidProviderCalendars.first()

    private suspend fun refreshAndroidProviderDiagnosticsInternal() {
        hiddenAndroidProviderCalendarNames.value = repository.hiddenOrNotSyncedAndroidCalendars()
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

@Suppress("UNCHECKED_CAST")
class CalendarViewModelFactory(
    private val graph: AppGraph,
    private val initialWidgetLaunchTarget: CalendarWidgetLaunchTarget? = null,
    private val initialCalendarLaunchTarget: CalendarLaunchTarget? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CalendarViewModel(
            repository = graph.repository,
            settingsStore = graph.settingsStore,
            sourceCalendarMutationCoordinator = graph.sourceCalendarMutationCoordinator,
            taskMutationCoordinator = graph.taskMutationCoordinator,
            calendarLaunchResolver = graph.calendarLaunchResolver,
            appContext = graph.appContext,
            initialWidgetLaunchTarget = initialWidgetLaunchTarget,
            initialCalendarLaunchTarget = initialCalendarLaunchTarget,
        ) as T
    }
}

private data class ResolvedCalendarLaunch(
    val serial: Int,
    val resolution: CalendarLaunchResolution,
)
