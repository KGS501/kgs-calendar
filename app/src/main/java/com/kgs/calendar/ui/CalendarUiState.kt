package com.kgs.calendar.ui

import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppLanguageMode
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.data.settings.WidgetColorMode
import com.kgs.calendar.data.settings.WidgetTaskCreateMode
import com.kgs.calendar.data.settings.WidgetTaskDisplayMode
import com.kgs.calendar.data.settings.WidgetTaskSortMode
import com.kgs.calendar.data.settings.WidgetTaskSubtaskDefaultMode
import com.kgs.calendar.data.settings.WidgetThemeMode
import com.kgs.calendar.domain.model.CalendarRange
import com.kgs.calendar.domain.model.CalendarViewMode
import java.time.DayOfWeek
import java.time.LocalDate

data class WidgetSettingsUiState(
    val monthWidgetColorMode: WidgetColorMode = WidgetColorMode.FollowApp,
    val monthWidgetThemeMode: WidgetThemeMode = WidgetThemeMode.FollowApp,
    val agendaWidgetColorMode: WidgetColorMode = WidgetColorMode.FollowApp,
    val agendaWidgetThemeMode: WidgetThemeMode = WidgetThemeMode.FollowApp,
    val tasksWidgetColorMode: WidgetColorMode = WidgetColorMode.FollowApp,
    val tasksWidgetThemeMode: WidgetThemeMode = WidgetThemeMode.FollowApp,
    val dayWidgetColorMode: WidgetColorMode = WidgetColorMode.FollowApp,
    val dayWidgetThemeMode: WidgetThemeMode = WidgetThemeMode.FollowApp,
    val multiWidgetColorMode: WidgetColorMode = WidgetColorMode.FollowApp,
    val multiWidgetThemeMode: WidgetThemeMode = WidgetThemeMode.FollowApp,
    val multiWidgetMonthPercent: Int = SettingsStore.DEFAULT_MULTI_WIDGET_MONTH_PERCENT,
    val tasksWidgetDisplayMode: WidgetTaskDisplayMode = WidgetTaskDisplayMode.Planned,
    val tasksWidgetIncludeOverdue: Boolean = true,
    val tasksWidgetSortMode: WidgetTaskSortMode = WidgetTaskSortMode.Date,
    val tasksWidgetCreateMode: WidgetTaskCreateMode = WidgetTaskCreateMode.Today,
    val tasksWidgetSubtaskDefaultMode: WidgetTaskSubtaskDefaultMode = WidgetTaskSubtaskDefaultMode.FollowApp,
    val dayWidgetScalePercent: Int = SettingsStore.DEFAULT_DAY_WIDGET_SCALE_PERCENT,
    val dayWidgetStartHour: Int = SettingsStore.DEFAULT_DAY_WIDGET_START_HOUR,
    val dayWidgetStartAtCurrentHour: Boolean = SettingsStore.DEFAULT_DAY_WIDGET_START_AT_CURRENT_HOUR,
)

data class CalendarUiState(
    val initialDataLoaded: Boolean = false,
    val account: AccountEntity? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val collections: List<CollectionEntity> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<EventEntity> = emptyList(),
    val searchTaskResults: List<TaskEntity> = emptyList(),
    val datedTasks: List<TaskEntity> = emptyList(),
    val inboxTasks: List<TaskEntity> = emptyList(),
    val scheduledOpenTasks: List<TaskEntity> = emptyList(),
    val pendingMutations: Int = 0,
    val pendingMutationItems: List<PendingMutationEntity> = emptyList(),
    val problemResources: List<CalendarResourceEntity> = emptyList(),
    val problemEvents: List<EventEntity> = emptyList(),
    val problemTasks: List<TaskEntity> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val dateNavigationSerial: Int = 0,
    val foregroundRecenterSerial: Int = 0,
    val widgetCreateEventDate: LocalDate? = null,
    val widgetCreateEventSerial: Int = 0,
    val widgetCreateTaskDate: LocalDate? = null,
    val widgetCreateTaskScheduled: Boolean = false,
    val widgetCreateTaskSerial: Int = 0,
    val widgetOpenEventUid: String? = null,
    val widgetOpenEventSerial: Int = 0,
    val widgetOpenTaskUid: String? = null,
    val widgetOpenTaskSerial: Int = 0,
    val calendarLaunchEvent: EventEntity? = null,
    val calendarLaunchTask: TaskEntity? = null,
    val calendarLaunchSerial: Int = 0,
    val selectedView: CalendarViewMode = CalendarViewMode.ThreeDay,
    val themeMode: AppThemeMode = AppThemeMode.KgsBlue,
    val colorMode: AppColorMode = AppColorMode.Auto,
    val widgetSettings: WidgetSettingsUiState = WidgetSettingsUiState(),
    val languageMode: AppLanguageMode = AppLanguageMode.System,
    val taskColorMode: TaskColorMode = TaskColorMode.Collection,
    val focusTitleOnCreate: Boolean = false,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val showCompletedTasksInCalendar: Boolean = true,
    val priorityAnimationsEnabled: Boolean = true,
    val subtasksExpandedByDefault: Boolean = true,
    val autoLoadMapPreviews: Boolean = false,
    val maxVisibleAllDayItems: Int = 3,
    val multiDayCount: Int = 3,
    val multiDaySidebarControlsEnabled: Boolean = true,
    val defaultEventDurationMinutes: Int = 60,
    val defaultTaskHasDate: Boolean = false,
    val defaultTaskHasTime: Boolean = false,
    val defaultEventReminderMinutes: Set<Int> = emptySet(),
    val defaultTaskReminderMinutes: Set<Int> = emptySet(),
    val taskStartNotificationsEnabled: Boolean = true,
    val taskEndNotificationsEnabled: Boolean = false,
    val eventStartNotificationsEnabled: Boolean = false,
    val eventEndNotificationsEnabled: Boolean = false,
    val defaultEventCollectionHref: String? = null,
    val defaultTaskCollectionHref: String? = null,
    val eventFieldOrder: List<String> = SettingsStore.DEFAULT_EVENT_FIELD_ORDER,
    val taskFieldOrder: List<String> = SettingsStore.DEFAULT_TASK_FIELD_ORDER,
    val completedTasks: List<TaskEntity> = emptyList(),
    val visibleRange: CalendarRange = CalendarRange(LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(1).plusMonths(1)),
    val isBusy: Boolean = false,
    val isManualSyncing: Boolean = false,
    val message: String? = null,
    val externalLoginUrl: String? = null,
    val hiddenAndroidProviderCalendarNames: List<String> = emptyList(),
    val welcomeCompleted: Boolean = true,
    val showDisabledAndroidProviderCalendars: Boolean = false,
    val hiddenCollectionHrefs: Set<String> = emptySet(),
) {
    val hasAccount: Boolean = accounts.isNotEmpty() || account != null
    val taskHierarchyTasks: List<TaskEntity>
        get() = (inboxTasks + scheduledOpenTasks + completedTasks + datedTasks)
            .distinctBy { it.resourceHref }
    val allTasks: List<TaskEntity>
        get() = (taskHierarchyTasks + searchTaskResults + problemTasks)
            .distinctBy { it.resourceHref }
}
