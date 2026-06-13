package com.kgs.calendar.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kgs.calendar.domain.model.REMINDER_AT_END
import com.kgs.calendar.domain.model.REMINDER_AT_START
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.DEFAULT_MULTI_DAY_COUNT
import com.kgs.calendar.domain.model.coerceMultiDayCount
import com.kgs.calendar.domain.model.normalizedReminderOffsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore(name = "kgs_settings")

class SettingsStore(private val context: Context) {
    val selectedView: Flow<CalendarViewMode> = context.dataStore.data.map { prefs ->
        if (prefs[KEY_VIEW] == "Week") return@map CalendarViewMode.ThreeDay
        runCatching {
            CalendarViewMode.valueOf(prefs[KEY_VIEW] ?: CalendarViewMode.ThreeDay.name)
        }.getOrDefault(CalendarViewMode.ThreeDay)
    }

    val selectedDate: Flow<LocalDate> = context.dataStore.data.map { prefs ->
        runCatching {
            LocalDate.parse(prefs[KEY_DATE] ?: LocalDate.now().toString())
        }.getOrDefault(LocalDate.now())
    }

    val themeMode: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        runCatching {
            AppThemeMode.valueOf(prefs[KEY_THEME] ?: AppThemeMode.KgsBlue.name)
        }.getOrDefault(AppThemeMode.KgsBlue)
    }

    val colorMode: Flow<AppColorMode> = context.dataStore.data.map { prefs ->
        runCatching {
            AppColorMode.valueOf(prefs[KEY_COLOR_MODE] ?: AppColorMode.Auto.name)
        }.getOrDefault(AppColorMode.Auto)
    }

    val monthWidgetThemeMode: Flow<WidgetThemeMode> = context.dataStore.data.map { prefs ->
        runCatching {
            WidgetThemeMode.valueOf(prefs[KEY_MONTH_WIDGET_THEME] ?: WidgetThemeMode.FollowApp.name)
        }.getOrDefault(WidgetThemeMode.FollowApp)
    }

    val monthWidgetColorMode: Flow<WidgetColorMode> = context.dataStore.data.map { prefs ->
        runCatching {
            WidgetColorMode.valueOf(prefs[KEY_MONTH_WIDGET_COLOR_MODE] ?: WidgetColorMode.FollowApp.name)
        }.getOrDefault(WidgetColorMode.FollowApp)
    }

    val tasksWidgetDisplayMode: Flow<WidgetTaskDisplayMode> = context.dataStore.data.map { prefs ->
        runCatching {
            WidgetTaskDisplayMode.valueOf(prefs[KEY_TASKS_WIDGET_DISPLAY_MODE] ?: WidgetTaskDisplayMode.Planned.name)
        }.getOrDefault(WidgetTaskDisplayMode.Planned)
    }

    val tasksWidgetIncludeOverdue: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_TASKS_WIDGET_INCLUDE_OVERDUE] ?: true
    }

    val tasksWidgetSortMode: Flow<WidgetTaskSortMode> = context.dataStore.data.map { prefs ->
        runCatching {
            WidgetTaskSortMode.valueOf(prefs[KEY_TASKS_WIDGET_SORT_MODE] ?: WidgetTaskSortMode.Date.name)
        }.getOrDefault(WidgetTaskSortMode.Date)
    }

    val tasksWidgetCreateMode: Flow<WidgetTaskCreateMode> = context.dataStore.data.map { prefs ->
        runCatching {
            WidgetTaskCreateMode.valueOf(prefs[KEY_TASKS_WIDGET_CREATE_MODE] ?: WidgetTaskCreateMode.Today.name)
        }.getOrDefault(WidgetTaskCreateMode.Today)
    }

    val languageMode: Flow<AppLanguageMode> = context.dataStore.data.map { prefs ->
        runCatching {
            AppLanguageMode.valueOf(prefs[KEY_LANGUAGE_MODE] ?: AppLanguageMode.System.name)
        }.getOrDefault(AppLanguageMode.System)
    }

    val taskColorMode: Flow<TaskColorMode> = context.dataStore.data.map { prefs ->
        runCatching {
            TaskColorMode.valueOf(prefs[KEY_TASK_COLOR_MODE] ?: TaskColorMode.Collection.name)
        }.getOrDefault(TaskColorMode.Collection)
    }

    val focusTitleOnCreate: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FOCUS_TITLE_ON_CREATE] ?: false
    }

    val firstDayOfWeek: Flow<DayOfWeek> = context.dataStore.data.map { prefs ->
        runCatching {
            DayOfWeek.of((prefs[KEY_FIRST_DAY_OF_WEEK] ?: DayOfWeek.MONDAY.value).coerceIn(1, 7))
        }.getOrDefault(DayOfWeek.MONDAY)
    }

    val showCompletedTasksInCalendar: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHOW_COMPLETED_TASKS] ?: true
    }

    val priorityAnimationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PRIORITY_ANIMATIONS_ENABLED] ?: true
    }

    val subtasksExpandedByDefault: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SUBTASKS_EXPANDED_BY_DEFAULT] ?: true
    }

    val autoLoadMapPreviews: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_LOAD_MAP_PREVIEWS] ?: false
    }

    val maxVisibleAllDayItems: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[KEY_MAX_VISIBLE_ALL_DAY_ITEMS] ?: 3).coerceIn(0, 10)
    }

    val multiDayCount: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[KEY_MULTI_DAY_COUNT] ?: DEFAULT_MULTI_DAY_COUNT).coerceMultiDayCount()
    }

    val multiDaySidebarControlsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MULTI_DAY_SIDEBAR_CONTROLS_ENABLED] ?: true
    }

    val defaultEventDurationMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[KEY_DEFAULT_EVENT_DURATION] ?: 60).coerceIn(15, 24 * 60)
    }

    val defaultTaskHasDate: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_TASK_HAS_DATE] ?: false
    }

    val defaultTaskHasTime: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_TASK_HAS_TIME] ?: false
    }

    val defaultEventReminderMinutes: Flow<Set<Int>> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_EVENT_REMINDERS]?.toReminderSet()
            ?: buildSet {
                if (prefs[KEY_EVENT_START_NOTIFICATIONS] == true) add(REMINDER_AT_START)
                if (prefs[KEY_EVENT_END_NOTIFICATIONS] == true) add(REMINDER_AT_END)
            }
    }

    val defaultTaskReminderMinutes: Flow<Set<Int>> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_TASK_REMINDERS]?.toReminderSet()
            ?: buildSet {
                if (prefs[KEY_TASK_START_NOTIFICATIONS] ?: true) add(REMINDER_AT_START)
                if (prefs[KEY_TASK_END_NOTIFICATIONS] == true) add(REMINDER_AT_END)
            }
    }

    val taskStartNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_TASK_START_NOTIFICATIONS] ?: true
    }

    val taskEndNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_TASK_END_NOTIFICATIONS] ?: false
    }

    val eventStartNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_EVENT_START_NOTIFICATIONS] ?: false
    }

    val eventEndNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_EVENT_END_NOTIFICATIONS] ?: false
    }

    val eventFieldOrder: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_EVENT_FIELD_ORDER].toFieldOrder(DEFAULT_EVENT_FIELD_ORDER)
    }

    val taskFieldOrder: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_TASK_FIELD_ORDER].toFieldOrder(DEFAULT_TASK_FIELD_ORDER)
    }

    val welcomeCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_WELCOME_COMPLETED] ?: false
    }

    val showDisabledAndroidProviderCalendars: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHOW_DISABLED_ANDROID_PROVIDER_CALENDARS] ?: false
    }

    val hiddenCollectionHrefs: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_HIDDEN_COLLECTION_HREFS].orEmpty()
    }

    suspend fun setSelectedView(viewMode: CalendarViewMode) {
        context.dataStore.edit { it[KEY_VIEW] = viewMode.name }
    }

    suspend fun setSelectedDate(date: LocalDate) {
        context.dataStore.edit { it[KEY_DATE] = date.toString() }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setColorMode(mode: AppColorMode) {
        context.dataStore.edit { it[KEY_COLOR_MODE] = mode.name }
    }

    suspend fun setMonthWidgetThemeMode(mode: WidgetThemeMode) {
        context.dataStore.edit { it[KEY_MONTH_WIDGET_THEME] = mode.name }
    }

    suspend fun setMonthWidgetColorMode(mode: WidgetColorMode) {
        context.dataStore.edit { it[KEY_MONTH_WIDGET_COLOR_MODE] = mode.name }
    }

    suspend fun setTasksWidgetDisplayMode(mode: WidgetTaskDisplayMode) {
        context.dataStore.edit { it[KEY_TASKS_WIDGET_DISPLAY_MODE] = mode.name }
    }

    suspend fun setTasksWidgetIncludeOverdue(include: Boolean) {
        context.dataStore.edit { it[KEY_TASKS_WIDGET_INCLUDE_OVERDUE] = include }
    }

    suspend fun setTasksWidgetSortMode(mode: WidgetTaskSortMode) {
        context.dataStore.edit { it[KEY_TASKS_WIDGET_SORT_MODE] = mode.name }
    }

    suspend fun setTasksWidgetCreateMode(mode: WidgetTaskCreateMode) {
        context.dataStore.edit { it[KEY_TASKS_WIDGET_CREATE_MODE] = mode.name }
    }

    suspend fun setLanguageMode(mode: AppLanguageMode) {
        context.dataStore.edit { it[KEY_LANGUAGE_MODE] = mode.name }
    }

    suspend fun setTaskColorMode(mode: TaskColorMode) {
        context.dataStore.edit { it[KEY_TASK_COLOR_MODE] = mode.name }
    }

    suspend fun setFocusTitleOnCreate(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FOCUS_TITLE_ON_CREATE] = enabled }
    }

    suspend fun setFirstDayOfWeek(dayOfWeek: DayOfWeek) {
        context.dataStore.edit { it[KEY_FIRST_DAY_OF_WEEK] = dayOfWeek.value }
    }

    suspend fun setShowCompletedTasksInCalendar(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_COMPLETED_TASKS] = show }
    }

    suspend fun setPriorityAnimationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PRIORITY_ANIMATIONS_ENABLED] = enabled }
    }

    suspend fun setSubtasksExpandedByDefault(expanded: Boolean) {
        context.dataStore.edit { it[KEY_SUBTASKS_EXPANDED_BY_DEFAULT] = expanded }
    }

    suspend fun setAutoLoadMapPreviews(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_LOAD_MAP_PREVIEWS] = enabled }
    }

    suspend fun setMaxVisibleAllDayItems(maxItems: Int) {
        context.dataStore.edit { it[KEY_MAX_VISIBLE_ALL_DAY_ITEMS] = maxItems.coerceIn(0, 10) }
    }

    suspend fun setMultiDayCount(count: Int) {
        context.dataStore.edit { it[KEY_MULTI_DAY_COUNT] = count.coerceMultiDayCount() }
    }

    suspend fun setMultiDaySidebarControlsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MULTI_DAY_SIDEBAR_CONTROLS_ENABLED] = enabled }
    }

    suspend fun setDefaultEventDurationMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_DEFAULT_EVENT_DURATION] = minutes.coerceIn(15, 24 * 60) }
    }

    suspend fun setDefaultTaskHasDate(hasDate: Boolean) {
        context.dataStore.edit { it[KEY_DEFAULT_TASK_HAS_DATE] = hasDate }
    }

    suspend fun setDefaultTaskHasTime(hasTime: Boolean) {
        context.dataStore.edit { it[KEY_DEFAULT_TASK_HAS_TIME] = hasTime }
    }

    suspend fun setDefaultEventReminderMinutes(reminders: Set<Int>) {
        context.dataStore.edit { it[KEY_DEFAULT_EVENT_REMINDERS] = reminders.toReminderCsv() }
    }

    suspend fun setDefaultTaskReminderMinutes(reminders: Set<Int>) {
        context.dataStore.edit { it[KEY_DEFAULT_TASK_REMINDERS] = reminders.toReminderCsv() }
    }

    suspend fun setTaskStartNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TASK_START_NOTIFICATIONS] = enabled }
    }

    suspend fun setTaskEndNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TASK_END_NOTIFICATIONS] = enabled }
    }

    suspend fun setEventStartNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EVENT_START_NOTIFICATIONS] = enabled }
    }

    suspend fun setEventEndNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EVENT_END_NOTIFICATIONS] = enabled }
    }

    suspend fun setEventFieldOrder(order: List<String>) {
        context.dataStore.edit { it[KEY_EVENT_FIELD_ORDER] = order.joinToString(",") }
    }

    suspend fun setTaskFieldOrder(order: List<String>) {
        context.dataStore.edit { it[KEY_TASK_FIELD_ORDER] = order.joinToString(",") }
    }

    suspend fun setWelcomeCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_WELCOME_COMPLETED] = completed }
    }

    suspend fun setShowDisabledAndroidProviderCalendars(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_DISABLED_ANDROID_PROVIDER_CALENDARS] = show }
    }

    suspend fun setCollectionHiddenInViews(href: String, hidden: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_HIDDEN_COLLECTION_HREFS].orEmpty()
            val next = if (hidden) current + href else current - href
            if (next.isEmpty()) prefs.remove(KEY_HIDDEN_COLLECTION_HREFS) else prefs[KEY_HIDDEN_COLLECTION_HREFS] = next
        }
    }

    val parserReparseVersion: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_PARSER_REPARSE_VERSION] ?: 0
    }

    suspend fun setParserReparseVersion(version: Int) {
        context.dataStore.edit { it[KEY_PARSER_REPARSE_VERSION] = version }
    }

    val defaultEventCollectionHref: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_EVENT_COLLECTION]?.takeIf { it.isNotBlank() }
    }

    suspend fun setDefaultEventCollectionHref(href: String?) {
        context.dataStore.edit { prefs ->
            if (href.isNullOrBlank()) prefs.remove(KEY_DEFAULT_EVENT_COLLECTION) else prefs[KEY_DEFAULT_EVENT_COLLECTION] = href
        }
    }

    val defaultTaskCollectionHref: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_TASK_COLLECTION]?.takeIf { it.isNotBlank() }
    }

    suspend fun setDefaultTaskCollectionHref(href: String?) {
        context.dataStore.edit { prefs ->
            if (href.isNullOrBlank()) prefs.remove(KEY_DEFAULT_TASK_COLLECTION) else prefs[KEY_DEFAULT_TASK_COLLECTION] = href
        }
    }

    companion object {
        private val KEY_VIEW = stringPreferencesKey("selected_view")
        private val KEY_DATE = stringPreferencesKey("selected_date")
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_COLOR_MODE = stringPreferencesKey("color_mode")
        private val KEY_MONTH_WIDGET_THEME = stringPreferencesKey("month_widget_theme_mode")
        private val KEY_MONTH_WIDGET_COLOR_MODE = stringPreferencesKey("month_widget_color_mode")
        private val KEY_TASKS_WIDGET_DISPLAY_MODE = stringPreferencesKey("tasks_widget_display_mode")
        private val KEY_TASKS_WIDGET_INCLUDE_OVERDUE = booleanPreferencesKey("tasks_widget_include_overdue")
        private val KEY_TASKS_WIDGET_SORT_MODE = stringPreferencesKey("tasks_widget_sort_mode")
        private val KEY_TASKS_WIDGET_CREATE_MODE = stringPreferencesKey("tasks_widget_create_mode")
        private val KEY_LANGUAGE_MODE = stringPreferencesKey("language_mode")
        private val KEY_TASK_COLOR_MODE = stringPreferencesKey("task_color_mode")
        private val KEY_FOCUS_TITLE_ON_CREATE = booleanPreferencesKey("focus_title_on_create")
        private val KEY_FIRST_DAY_OF_WEEK = intPreferencesKey("first_day_of_week")
        private val KEY_SHOW_COMPLETED_TASKS = booleanPreferencesKey("show_completed_tasks_in_calendar")
        private val KEY_PRIORITY_ANIMATIONS_ENABLED = booleanPreferencesKey("priority_animations_enabled")
        private val KEY_SUBTASKS_EXPANDED_BY_DEFAULT = booleanPreferencesKey("subtasks_expanded_by_default")
        private val KEY_AUTO_LOAD_MAP_PREVIEWS = booleanPreferencesKey("auto_load_map_previews")
        private val KEY_MAX_VISIBLE_ALL_DAY_ITEMS = intPreferencesKey("max_visible_all_day_items")
        private val KEY_MULTI_DAY_COUNT = intPreferencesKey("multi_day_count")
        private val KEY_MULTI_DAY_SIDEBAR_CONTROLS_ENABLED = booleanPreferencesKey("multi_day_sidebar_controls_enabled")
        private val KEY_DEFAULT_EVENT_DURATION = intPreferencesKey("default_event_duration_minutes")
        private val KEY_DEFAULT_TASK_HAS_DATE = booleanPreferencesKey("default_task_has_date")
        private val KEY_DEFAULT_TASK_HAS_TIME = booleanPreferencesKey("default_task_has_time")
        private val KEY_DEFAULT_EVENT_REMINDERS = stringPreferencesKey("default_event_reminders_csv")
        private val KEY_DEFAULT_TASK_REMINDERS = stringPreferencesKey("default_task_reminders_csv")
        private val KEY_TASK_START_NOTIFICATIONS = booleanPreferencesKey("task_start_notifications_enabled")
        private val KEY_TASK_END_NOTIFICATIONS = booleanPreferencesKey("task_end_notifications_enabled")
        private val KEY_EVENT_START_NOTIFICATIONS = booleanPreferencesKey("event_start_notifications_enabled")
        private val KEY_EVENT_END_NOTIFICATIONS = booleanPreferencesKey("event_end_notifications_enabled")
        private val KEY_EVENT_FIELD_ORDER = stringPreferencesKey("event_field_order")
        private val KEY_TASK_FIELD_ORDER = stringPreferencesKey("task_field_order")
        private val KEY_WELCOME_COMPLETED = booleanPreferencesKey("welcome_completed")
        private val KEY_SHOW_DISABLED_ANDROID_PROVIDER_CALENDARS = booleanPreferencesKey("show_disabled_android_provider_calendars")
        private val KEY_HIDDEN_COLLECTION_HREFS = stringSetPreferencesKey("hidden_collection_hrefs")
        private val KEY_PARSER_REPARSE_VERSION = intPreferencesKey("parser_reparse_version")
        private val KEY_DEFAULT_EVENT_COLLECTION = stringPreferencesKey("default_event_collection_href")
        private val KEY_DEFAULT_TASK_COLLECTION = stringPreferencesKey("default_task_collection_href")
        val DEFAULT_EVENT_FIELD_ORDER = listOf("time", "recurrence", "reminders", "location", "notes", "status", "categories", "color", "participants")
        val DEFAULT_TASK_FIELD_ORDER = listOf("status", "time", "recurrence", "reminders", "location", "notes", "url", "priority", "progress", "color", "tags")
    }
}

private fun String?.toFieldOrder(defaultOrder: List<String>): List<String> {
    val saved = this?.split(',')?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
    return (saved.filter { it in defaultOrder } + defaultOrder.filterNot { it in saved }).distinct()
}

private fun String.toReminderSet(): Set<Int> =
    split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .normalizedReminderOffsets()
        .toSet()

private fun Set<Int>.toReminderCsv(): String =
    normalizedReminderOffsets().joinToString(",")
