package com.kgs.calendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.kgs.calendar.AppGraph
import com.kgs.calendar.KgsCalendarApplication
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
import com.kgs.calendar.data.settings.WidgetThemeMode
import com.kgs.calendar.domain.model.CalendarRange
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.DEFAULT_MULTI_DAY_COUNT
import com.kgs.calendar.domain.model.EventEditPayload
import com.kgs.calendar.domain.model.TaskEditPayload
import com.kgs.calendar.domain.model.coerceMultiDayCount
import com.kgs.calendar.domain.model.visibleRangeFor
import com.kgs.calendar.reminder.ReminderScheduler
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.KgsWidgetUpdateScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: CalendarRepository,
    private val settingsStore: SettingsStore,
    private val appContext: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val manualSyncing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val externalLoginUrl = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val hiddenAndroidProviderCalendarNames = MutableStateFlow<List<String>>(emptyList())
    private val selectedViewOverride = MutableStateFlow<CalendarViewMode?>(null)
    private val selectedDateOverride = MutableStateFlow<LocalDate?>(null)
    private val dateNavigationSerial = MutableStateFlow(0)
    private val widgetCreateEventDate = MutableStateFlow<LocalDate?>(null)
    private val widgetCreateEventSerial = MutableStateFlow(0)
    private val widgetCreateTaskDate = MutableStateFlow<LocalDate?>(null)
    private val widgetCreateTaskScheduled = MutableStateFlow(false)
    private val widgetCreateTaskSerial = MutableStateFlow(0)
    private val widgetOpenTaskUid = MutableStateFlow<String?>(null)
    private val widgetOpenTaskSerial = MutableStateFlow(0)
    private var selectedDatePersistJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { repository.ensureLocalCalendar() }
                .onSuccess { message.value = null }
                .onFailure { message.value = it.message ?: "Could not prepare local calendar." }
        }
        viewModelScope.launch {
            runCatching {
                repository.refreshAndroidCalendarsIfEnabled(
                    includeDisabledProviderCalendars = includeDisabledAndroidProviderCalendars(),
                )
                refreshAndroidProviderDiagnosticsInternal()
            }
                .onFailure { /* Missing permission should not show a startup error. */ }
        }
    }

    private val selectedView = combine(settingsStore.selectedView, selectedViewOverride) { stored, override -> override ?: stored }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, CalendarViewMode.ThreeDay)
    private val storedSelectedDate = settingsStore.selectedDate
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())
    private val selectedDate = combine(storedSelectedDate, selectedDateOverride) { stored, override -> override ?: stored }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())
    private val hiddenCollectionHrefs = settingsStore.hiddenCollectionHrefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val multiDayCount = settingsStore.multiDayCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_MULTI_DAY_COUNT)
    private val visibleRange = combine(selectedDate, selectedView, multiDayCount) { date, view, dayCount ->
        visibleRangeFor(date, view, dayCount)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, visibleRangeFor(LocalDate.now(), CalendarViewMode.ThreeDay))
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
        widgetOpenTaskUid,
        widgetOpenTaskSerial,
        settingsStore.tasksWidgetDisplayMode,
        settingsStore.tasksWidgetIncludeOverdue,
        settingsStore.tasksWidgetSortMode,
        settingsStore.tasksWidgetCreateMode,
    ) { values ->
        CalendarUiState(
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
            widgetOpenTaskUid = values[61] as String?,
            widgetOpenTaskSerial = values[62] as Int,
            tasksWidgetDisplayMode = values[63] as WidgetTaskDisplayMode,
            tasksWidgetIncludeOverdue = values[64] as Boolean,
            tasksWidgetSortMode = values[65] as WidgetTaskSortMode,
            tasksWidgetCreateMode = values[66] as WidgetTaskCreateMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun selectView(viewMode: CalendarViewMode) {
        selectedViewOverride.value = viewMode
        viewModelScope.launch { settingsStore.setSelectedView(viewMode) }
    }

    fun openFromWidget(
        date: LocalDate,
        viewMode: CalendarViewMode,
        createEvent: Boolean = false,
        createTaskScheduled: Boolean? = null,
        openTaskUid: String? = null,
    ) {
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
        if (!openTaskUid.isNullOrBlank()) {
            widgetOpenTaskUid.value = openTaskUid
            widgetOpenTaskSerial.update { it + 1 }
        }
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

    fun setLanguageMode(mode: AppLanguageMode) {
        viewModelScope.launch { settingsStore.setLanguageMode(mode) }
    }

    fun setTaskColorMode(mode: TaskColorMode) {
        viewModelScope.launch { settingsStore.setTaskColorMode(mode) }
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
        viewModelScope.launch { settingsStore.setPriorityAnimationsEnabled(enabled) }
    }

    fun setSubtasksExpandedByDefault(expanded: Boolean) {
        viewModelScope.launch { settingsStore.setSubtasksExpandedByDefault(expanded) }
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
                repository.saveManualAccount(serverUrl, username, appPassword)
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

            message.value = "CalDAV account added."
            busy.value = false
            onResult?.invoke(true, null)

            runCatching {
                repository.syncNow(
                    includeDisabledProviderCalendars = includeDisabledAndroidProviderCalendars(),
                    forceFullCalDavRefresh = true,
                )
            }.onSuccess {
                message.value = "CalDAV account synced."
            }.onFailure {
                val errorMessage = it.message ?: "The CalDAV account was added, but its first sync failed."
                message.value = errorMessage
            }
            runCatching { ReminderScheduler.reschedule(appContext) }
        }
    }

    fun startBrowserLogin(serverUrl: String) {
        runBusy(rescheduleReminders = true) {
            val start = repository.startLoginFlow(serverUrl)
            externalLoginUrl.value = start.loginUrl
            repository.completeLoginFlow(start.pollEndpoint, start.token)
            repository.syncNow(
                includeDisabledProviderCalendars = includeDisabledAndroidProviderCalendars(),
                forceFullCalDavRefresh = true,
            )
            message.value = "Login complete."
        }
    }

    fun addReadOnlyCalendar(url: String) {
        runBusy(rescheduleReminders = true, showManualSync = true) {
            repository.addReadOnlyCalendar(url)
            syncAllSourcesAfterAddingSource()
            message.value = "Read-only calendar added."
        }
    }

    fun addAndroidDeviceCalendars() {
        runBusy(rescheduleReminders = true, showManualSync = true) {
            repository.enableAndroidCalendars(
                includeDisabledProviderCalendars = includeDisabledAndroidProviderCalendars(),
            )
            (appContext.applicationContext as? KgsCalendarApplication)?.registerAndroidCalendarObserverIfPermitted()
            syncAllSourcesAfterAddingSource()
            message.value = "Android device calendars added."
        }
    }

    fun renameAccount(accountId: String, displayName: String) {
        runBusy {
            repository.renameAccount(accountId, displayName)
            message.value = "Source renamed."
        }
    }

    fun updateAccount(accountId: String, displayName: String, serverUrl: String, username: String, appPassword: String?) {
        runBusy(rescheduleReminders = true) {
            repository.updateAccount(accountId, displayName, serverUrl, username, appPassword)
            repository.syncNow(
                includeDisabledProviderCalendars = includeDisabledAndroidProviderCalendars(),
                forceFullCalDavRefresh = true,
            )
            message.value = "Source updated."
        }
    }

    fun deleteAccount(accountId: String) {
        runBusy(rescheduleReminders = true) {
            repository.deleteAccount(accountId)
            message.value = "Source removed."
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

    fun updateTask(uid: String, payload: TaskEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.updateTask(uid, payload)
            if (payload.isCompleted) {
                ReminderScheduler.cancelTaskNotifications(appContext, uid)
            }
        }
    }

    fun updateTaskOccurrence(uid: String, occurrenceStartMillis: Long, payload: TaskEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.updateTaskOccurrence(uid, occurrenceStartMillis, payload)
            if (payload.isCompleted) {
                ReminderScheduler.cancelTaskNotifications(appContext, uid)
            }
        }
    }

    fun updateTaskFollowing(uid: String, occurrenceStartMillis: Long, payload: TaskEditPayload) {
        runEdit(rescheduleReminders = true) {
            repository.updateTaskFollowing(uid, occurrenceStartMillis, payload)
            if (payload.isCompleted) {
                ReminderScheduler.cancelTaskNotifications(appContext, uid)
            }
        }
    }

    fun updateTaskManualColor(uid: String, manualColor: Int?) {
        runEdit {
            repository.updateTaskManualColor(uid, manualColor)
            message.value = "Task color updated."
        }
    }

    fun setTaskCompleted(uid: String, completed: Boolean) {
        runEdit(rescheduleReminders = true) {
            repository.setTaskCompleted(uid, completed)
            if (completed) {
                ReminderScheduler.cancelTaskNotifications(appContext, uid)
            }
        }
    }

    fun setTaskStatus(uid: String, status: String) {
        runEdit(rescheduleReminders = true) {
            repository.setTaskStatus(uid, status)
            if (status.equals("COMPLETED", ignoreCase = true)) {
                ReminderScheduler.cancelTaskNotifications(appContext, uid)
            }
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

    fun copyTaskTo(uid: String, collectionHref: String) {
        runEdit(rescheduleReminders = true) {
            repository.copyTaskTo(uid, collectionHref)
        }
    }

    fun setCollectionEnabled(href: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                repository.setCollectionEnabled(href, enabled)
                message.value = null
            }.onFailure {
                message.value = it.message ?: "Could not update calendar."
            }
        }
    }

    fun setCollectionVisibleInViews(href: String, visible: Boolean) {
        viewModelScope.launch {
            settingsStore.setCollectionHiddenInViews(href, hidden = !visible)
        }
    }

    fun updateCollectionAppearance(href: String, displayName: String, customColor: Int?) {
        viewModelScope.launch {
            runCatching {
                repository.updateCollectionAppearance(href, displayName, customColor)
                message.value = null
            }.onFailure {
                message.value = it.message ?: "Could not update calendar."
            }
        }
    }

    fun createCalDavCalendar(
        accountId: String,
        displayName: String,
        supportsEvents: Boolean,
        supportsTasks: Boolean,
    ) {
        runBusy(rescheduleReminders = true) {
            repository.createCalDavCalendar(
                accountId = accountId,
                displayName = displayName,
                color = null,
                supportsEvents = supportsEvents,
                supportsTasks = supportsTasks,
            )
            message.value = "CalDAV calendar created."
        }
    }

    fun deleteCalDavCalendar(href: String) {
        runBusy(rescheduleReminders = true) {
            repository.deleteCalDavCalendar(href)
            message.value = "CalDAV calendar deleted."
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
            }.onFailure {
                message.value = it.message ?: "Could not save changes."
            }
            if (rescheduleReminders) {
                runCatching { ReminderScheduler.reschedule(appContext) }
            }
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

    private suspend fun syncAllSourcesAfterAddingSource() {
        repository.syncNow(
            includeDisabledProviderCalendars = includeDisabledAndroidProviderCalendars(),
            forceFullCalDavRefresh = true,
        )
        refreshAndroidProviderDiagnosticsInternal()
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
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CalendarViewModel(graph.repository, graph.settingsStore, graph.appContext) as T
    }
}
