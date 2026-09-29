package com.kgs.calendar.ui

import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppLanguageMode
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.data.settings.WidgetColorMode
import com.kgs.calendar.data.settings.WidgetTaskCreateMode
import com.kgs.calendar.data.settings.WidgetTaskDisplayMode
import com.kgs.calendar.data.settings.WidgetTaskSortMode
import com.kgs.calendar.data.settings.WidgetTaskSubtaskDefaultMode
import com.kgs.calendar.data.settings.WidgetThemeMode
import com.kgs.calendar.domain.model.CalendarRange
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.MultiDayCounts
import com.kgs.calendar.domain.source.CollectionVisibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.LocalDate

// Typed groups that make up CalendarUiState. Each group is combined from at most five flows, so
// adding a field only touches its own group and the final mapping in calendarUiState().

internal data class CalendarSourceData(
    val account: AccountEntity?,
    val accounts: List<AccountEntity>,
    val collections: List<CollectionEntity>,
)

internal data class CalendarItemData(
    val events: List<EventEntity>,
    val datedTasks: List<TaskEntity>,
    val inboxTasks: List<TaskEntity>,
    val scheduledOpenTasks: List<TaskEntity>,
    val completedTasks: List<TaskEntity>,
    val missedTaskOccurrences: List<TaskEntity> = emptyList(),
)

internal data class SyncStatusData(
    val pendingMutations: Int,
    val pendingMutationItems: List<PendingMutationEntity>,
    val problemResources: List<CalendarResourceEntity>,
    val problemEvents: List<EventEntity>,
    val problemTasks: List<TaskEntity>,
)

internal data class CalendarDataState(
    val sources: CalendarSourceData,
    val items: CalendarItemData,
    val syncStatus: SyncStatusData,
    val loadedDataRange: CalendarRange?,
    val requestedDataRange: CalendarRange,
)

internal data class NavigationUiState(
    val selectedDate: LocalDate,
    val selectedView: CalendarViewMode,
    val visibleRange: CalendarRange,
    val dateNavigationSerial: Int,
    val multiDayCount: Int,
)

internal data class AppearanceSettings(
    val themeMode: AppThemeMode,
    val colorMode: AppColorMode,
    val languageMode: AppLanguageMode,
    val taskColorMode: TaskColorMode,
    val showCalendarWeeks: Boolean,
)

internal data class DisplaySettings(
    val showCompletedTasksInCalendar: Boolean,
    val priorityAnimationsEnabled: Boolean,
    val overdueSummaryPriorityAnimationEnabled: Boolean,
    val subtasksExpandedByDefault: Boolean,
    val autoLoadMapPreviews: Boolean,
)

internal data class AppBehaviourSettings(
    val focusTitleOnCreate: Boolean,
    val maxVisibleAllDayItems: Int,
    val welcomeCompleted: Boolean,
    val showDisabledAndroidProviderCalendars: Boolean,
    val collectionVisibility: CollectionVisibility,
)

internal data class TimelineZoomSettings(
    val multiDayCounts: MultiDayCounts,
    val portraitTimelineHourHeightDp: Float,
    val landscapeTimelineHourHeightDp: Float,
    val landscapeTimelineCompact: Boolean,
)

internal data class TimelineSettings(
    val firstDayOfWeek: DayOfWeek,
    val weekViewEnabled: Boolean,
    val fullWeekSwipeEnabled: Boolean,
    val multiDaySidebarControlsEnabled: Boolean,
    val zoom: TimelineZoomSettings,
)

internal data class GeneralSettings(
    val appearance: AppearanceSettings,
    val display: DisplaySettings,
    val behaviour: AppBehaviourSettings,
    val timeline: TimelineSettings,
    val hapticFeedbackEnabled: Boolean,
    val syncIntervalMinutes: Int,
)

internal data class NewItemDefaults(
    val eventDurationMinutes: Int,
    val taskHasDate: Boolean,
    val taskHasTime: Boolean,
    val eventReminderMinutes: Set<Int>,
    val taskReminderMinutes: Set<Int>,
)

internal data class EditorLayoutDefaults(
    val eventCollectionHref: String?,
    val taskCollectionHref: String?,
    val eventFieldOrder: List<String>,
    val taskFieldOrder: List<String>,
)

internal data class NotificationSettings(
    val taskStartEnabled: Boolean,
    val taskEndEnabled: Boolean,
    val eventStartEnabled: Boolean,
    val eventEndEnabled: Boolean,
)

internal data class EditorDefaults(
    val newItems: NewItemDefaults,
    val layout: EditorLayoutDefaults,
    val notifications: NotificationSettings,
)

internal data class CalendarSettingsState(
    val general: GeneralSettings,
    val editor: EditorDefaults,
    val widgets: WidgetSettingsUiState,
)

internal data class TransientUiState(
    val isBusy: Boolean,
    val isManualSyncing: Boolean,
    val message: String?,
    val externalLoginUrl: String?,
    val hiddenAndroidProviderCalendarNames: List<String>,
)

/** Busy/message/sync flags shared by the ViewModel and its action helpers. */
internal class CalendarTransientState {
    val busy = MutableStateFlow(false)
    val manualSyncing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val externalLoginUrl = MutableStateFlow<String?>(null)
    val hiddenAndroidProviderCalendarNames = MutableStateFlow<List<String>>(emptyList())

    val state: Flow<TransientUiState> = combine(
        busy,
        manualSyncing,
        message,
        externalLoginUrl,
        hiddenAndroidProviderCalendarNames,
        ::TransientUiState,
    )
}

internal fun calendarDataState(
    repository: CalendarRepository,
    items: Flow<CalendarItemData>,
    loadedDataRange: Flow<CalendarRange?>,
    requestedDataRange: Flow<CalendarRange>,
): Flow<CalendarDataState> = combine(
    combine(
        repository.observeAccount(),
        repository.observeAccounts(),
        repository.observeCollections(),
        ::CalendarSourceData,
    ),
    items,
    combine(
        repository.observePendingMutationCount(),
        repository.observePendingMutations(),
        repository.observeProblemResources(),
        repository.observeProblemEvents(),
        repository.observeProblemTasks(),
        ::SyncStatusData,
    ),
    loadedDataRange,
    requestedDataRange,
    ::CalendarDataState,
)

/**
 * The ViewModel passes its own eagerly shared copies of the settings it also uses for navigation,
 * so the UI and the range policy always see the same values.
 */
internal fun generalSettings(
    settingsStore: SettingsStore,
    collectionVisibility: Flow<CollectionVisibility>,
    firstDayOfWeek: Flow<DayOfWeek>,
    weekViewEnabled: Flow<Boolean>,
    fullWeekSwipeEnabled: Flow<Boolean>,
    multiDayCounts: Flow<MultiDayCounts>,
): Flow<GeneralSettings> = combine(
    combine(
        settingsStore.themeMode,
        settingsStore.colorMode,
        settingsStore.languageMode,
        settingsStore.taskColorMode,
        settingsStore.showCalendarWeeks,
        ::AppearanceSettings,
    ),
    combine(
        settingsStore.showCompletedTasksInCalendar,
        settingsStore.priorityAnimationsEnabled,
        settingsStore.overdueSummaryPriorityAnimationEnabled,
        settingsStore.subtasksExpandedByDefault,
        settingsStore.autoLoadMapPreviews,
        ::DisplaySettings,
    ),
    combine(
        settingsStore.focusTitleOnCreate,
        settingsStore.maxVisibleAllDayItems,
        settingsStore.welcomeCompleted,
        settingsStore.showDisabledAndroidProviderCalendars,
        collectionVisibility,
        ::AppBehaviourSettings,
    ),
    combine(
        firstDayOfWeek,
        weekViewEnabled,
        fullWeekSwipeEnabled,
        settingsStore.multiDaySidebarControlsEnabled,
        combine(
            multiDayCounts,
            settingsStore.portraitTimelineHourHeightDp,
            settingsStore.landscapeTimelineHourHeightDp,
            settingsStore.landscapeTimelineCompact,
            ::TimelineZoomSettings,
        ),
        ::TimelineSettings,
    ),
    combine(settingsStore.hapticFeedbackEnabled, settingsStore.syncIntervalMinutes) { haptics, interval -> haptics to interval },
) { appearance, display, behaviour, timeline, background ->
    GeneralSettings(appearance, display, behaviour, timeline, background.first, background.second)
}

internal fun SettingsStore.editorDefaults(): Flow<EditorDefaults> = combine(
    combine(
        defaultEventDurationMinutes,
        defaultTaskHasDate,
        defaultTaskHasTime,
        defaultEventReminderMinutes,
        defaultTaskReminderMinutes,
        ::NewItemDefaults,
    ),
    combine(
        defaultEventCollectionHref,
        defaultTaskCollectionHref,
        eventFieldOrder,
        taskFieldOrder,
        ::EditorLayoutDefaults,
    ),
    combine(
        taskStartNotificationsEnabled,
        taskEndNotificationsEnabled,
        eventStartNotificationsEnabled,
        eventEndNotificationsEnabled,
        ::NotificationSettings,
    ),
    ::EditorDefaults,
)

private data class WidgetAppearance(
    val colorMode: WidgetColorMode,
    val themeMode: WidgetThemeMode,
)

private data class WidgetAppearances(
    val month: WidgetAppearance,
    val agenda: WidgetAppearance,
    val tasks: WidgetAppearance,
    val day: WidgetAppearance,
    val multi: WidgetAppearance,
)

private data class TasksWidgetBehaviour(
    val displayMode: WidgetTaskDisplayMode,
    val includeOverdue: Boolean,
    val sortMode: WidgetTaskSortMode,
    val createMode: WidgetTaskCreateMode,
    val subtaskDefaultMode: WidgetTaskSubtaskDefaultMode,
)

private data class DayWidgetBehaviour(
    val scalePercent: Int,
    val startHour: Int,
    val startAtCurrentHour: Boolean,
)

internal fun SettingsStore.widgetSettings(): Flow<WidgetSettingsUiState> = combine(
    combine(
        combine(monthWidgetColorMode, monthWidgetThemeMode, ::WidgetAppearance),
        combine(agendaWidgetColorMode, agendaWidgetThemeMode, ::WidgetAppearance),
        combine(tasksWidgetColorMode, tasksWidgetThemeMode, ::WidgetAppearance),
        combine(dayWidgetColorMode, dayWidgetThemeMode, ::WidgetAppearance),
        combine(multiWidgetColorMode, multiWidgetThemeMode, ::WidgetAppearance),
        ::WidgetAppearances,
    ),
    combine(
        tasksWidgetDisplayMode,
        tasksWidgetIncludeOverdue,
        tasksWidgetSortMode,
        tasksWidgetCreateMode,
        tasksWidgetSubtaskDefaultMode,
        ::TasksWidgetBehaviour,
    ),
    combine(
        dayWidgetScalePercent,
        dayWidgetStartHour,
        dayWidgetStartAtCurrentHour,
        ::DayWidgetBehaviour,
    ),
    multiWidgetMonthPercent,
) { appearances, tasks, day, multiMonthPercent ->
    WidgetSettingsUiState(
        monthWidgetColorMode = appearances.month.colorMode,
        monthWidgetThemeMode = appearances.month.themeMode,
        agendaWidgetColorMode = appearances.agenda.colorMode,
        agendaWidgetThemeMode = appearances.agenda.themeMode,
        tasksWidgetColorMode = appearances.tasks.colorMode,
        tasksWidgetThemeMode = appearances.tasks.themeMode,
        dayWidgetColorMode = appearances.day.colorMode,
        dayWidgetThemeMode = appearances.day.themeMode,
        multiWidgetColorMode = appearances.multi.colorMode,
        multiWidgetThemeMode = appearances.multi.themeMode,
        multiWidgetMonthPercent = multiMonthPercent,
        tasksWidgetDisplayMode = tasks.displayMode,
        tasksWidgetIncludeOverdue = tasks.includeOverdue,
        tasksWidgetSortMode = tasks.sortMode,
        tasksWidgetCreateMode = tasks.createMode,
        tasksWidgetSubtaskDefaultMode = tasks.subtaskDefaultMode,
        dayWidgetScalePercent = day.scalePercent,
        dayWidgetStartHour = day.startHour,
        dayWidgetStartAtCurrentHour = day.startAtCurrentHour,
    )
}

internal fun calendarUiState(
    initialDataLoaded: Boolean,
    data: CalendarDataState,
    navigation: NavigationUiState,
    search: SearchUiState,
    settings: CalendarSettingsState,
    transient: TransientUiState,
): CalendarUiState {
    val appearance = settings.general.appearance
    val display = settings.general.display
    val behaviour = settings.general.behaviour
    val timeline = settings.general.timeline
    val newItems = settings.editor.newItems
    val layout = settings.editor.layout
    val notifications = settings.editor.notifications
    return CalendarUiState(
        initialDataLoaded = initialDataLoaded,
        account = data.sources.account,
        accounts = data.sources.accounts,
        collections = data.sources.collections,
        events = data.items.events,
        searchQuery = search.query,
        searchMode = search.mode,
        searchOccurrenceRange = search.occurrenceRange,
        searchResults = search.results,
        searchTaskResults = search.taskResults,
        datedTasks = data.items.datedTasks,
        inboxTasks = data.items.inboxTasks,
        scheduledOpenTasks = data.items.scheduledOpenTasks,
        missedTaskOccurrences = data.items.missedTaskOccurrences,
        pendingMutations = data.syncStatus.pendingMutations,
        pendingMutationItems = data.syncStatus.pendingMutationItems,
        problemResources = data.syncStatus.problemResources,
        problemEvents = data.syncStatus.problemEvents,
        problemTasks = data.syncStatus.problemTasks,
        selectedDate = navigation.selectedDate,
        dateNavigationSerial = navigation.dateNavigationSerial,
        selectedView = navigation.selectedView,
        themeMode = appearance.themeMode,
        colorMode = appearance.colorMode,
        widgetSettings = settings.widgets,
        languageMode = appearance.languageMode,
        taskColorMode = appearance.taskColorMode,
        focusTitleOnCreate = behaviour.focusTitleOnCreate,
        firstDayOfWeek = timeline.firstDayOfWeek,
        showCompletedTasksInCalendar = display.showCompletedTasksInCalendar,
        showCalendarWeeks = appearance.showCalendarWeeks,
        priorityAnimationsEnabled = display.priorityAnimationsEnabled,
        overdueSummaryPriorityAnimationEnabled = display.overdueSummaryPriorityAnimationEnabled,
        subtasksExpandedByDefault = display.subtasksExpandedByDefault,
        autoLoadMapPreviews = display.autoLoadMapPreviews,
        hapticFeedbackEnabled = settings.general.hapticFeedbackEnabled,
        syncIntervalMinutes = settings.general.syncIntervalMinutes,
        maxVisibleAllDayItems = behaviour.maxVisibleAllDayItems,
        multiDayCount = navigation.multiDayCount,
        portraitMultiDayCount = timeline.zoom.multiDayCounts.portrait,
        landscapeMultiDayCount = timeline.zoom.multiDayCounts.landscape,
        largePortraitMultiDayCount = timeline.zoom.multiDayCounts.largePortrait,
        largeLandscapeMultiDayCount = timeline.zoom.multiDayCounts.largeLandscape,
        portraitTimelineHourHeightDp = timeline.zoom.portraitTimelineHourHeightDp,
        landscapeTimelineHourHeightDp = timeline.zoom.landscapeTimelineHourHeightDp,
        landscapeTimelineCompact = timeline.zoom.landscapeTimelineCompact,
        weekViewEnabled = timeline.weekViewEnabled,
        fullWeekSwipeEnabled = timeline.fullWeekSwipeEnabled,
        multiDaySidebarControlsEnabled = timeline.multiDaySidebarControlsEnabled,
        defaultEventDurationMinutes = newItems.eventDurationMinutes,
        defaultTaskHasDate = newItems.taskHasDate,
        defaultTaskHasTime = newItems.taskHasTime,
        defaultEventReminderMinutes = newItems.eventReminderMinutes,
        defaultTaskReminderMinutes = newItems.taskReminderMinutes,
        taskStartNotificationsEnabled = notifications.taskStartEnabled,
        taskEndNotificationsEnabled = notifications.taskEndEnabled,
        eventStartNotificationsEnabled = notifications.eventStartEnabled,
        eventEndNotificationsEnabled = notifications.eventEndEnabled,
        defaultEventCollectionHref = layout.eventCollectionHref,
        defaultTaskCollectionHref = layout.taskCollectionHref,
        eventFieldOrder = layout.eventFieldOrder,
        taskFieldOrder = layout.taskFieldOrder,
        completedTasks = data.items.completedTasks,
        visibleRange = navigation.visibleRange,
        loadedDataRange = data.loadedDataRange,
        requestedDataRange = data.requestedDataRange,
        isBusy = transient.isBusy,
        isManualSyncing = transient.isManualSyncing,
        message = transient.message,
        externalLoginUrl = transient.externalLoginUrl,
        hiddenAndroidProviderCalendarNames = transient.hiddenAndroidProviderCalendarNames,
        welcomeCompleted = behaviour.welcomeCompleted,
        showDisabledAndroidProviderCalendars = behaviour.showDisabledAndroidProviderCalendars,
        collectionVisibility = behaviour.collectionVisibility,
    )
}
