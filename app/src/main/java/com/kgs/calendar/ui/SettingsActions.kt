package com.kgs.calendar.ui

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
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.coerceMultiDayCount
import com.kgs.calendar.domain.model.startOfWeek
import com.kgs.calendar.widget.KgsWidgetKind
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** App, editor-default and widget settings mutations, including their widget/reminder refreshes. */
class SettingsActions internal constructor(
    private val scope: CoroutineScope,
    private val settingsStore: SettingsStore,
    private val widgetRefresher: WidgetRefresher,
    private val reminderRescheduler: ReminderRescheduler,
    private val message: MutableStateFlow<String?>,
    private val currentState: () -> CalendarUiState,
    private val isLandscape: () -> Boolean,
    private val publishedFirstDayOfWeek: Flow<DayOfWeek>,
    private val selectDate: (LocalDate) -> Unit,
) {
    fun setThemeMode(mode: AppThemeMode) {
        scope.launch {
            settingsStore.setThemeMode(mode)
            widgetRefresher.updateAll()
        }
    }

    fun setColorMode(mode: AppColorMode) {
        scope.launch {
            settingsStore.setColorMode(mode)
            widgetRefresher.updateAll()
        }
    }

    fun setWidgetThemeMode(kind: KgsWidgetKind, mode: WidgetThemeMode) {
        scope.launch {
            when (kind) {
                KgsWidgetKind.Month -> settingsStore.setMonthWidgetThemeMode(mode)
                KgsWidgetKind.Agenda -> settingsStore.setAgendaWidgetThemeMode(mode)
                KgsWidgetKind.Tasks -> settingsStore.setTasksWidgetThemeMode(mode)
                KgsWidgetKind.Day -> settingsStore.setDayWidgetThemeMode(mode)
                KgsWidgetKind.Multi -> settingsStore.setMultiWidgetThemeMode(mode)
            }
            widgetRefresher.update(kind, forceFullDayUpdate = kind == KgsWidgetKind.Day)
        }
    }

    fun setWidgetColorMode(kind: KgsWidgetKind, mode: WidgetColorMode) {
        scope.launch {
            when (kind) {
                KgsWidgetKind.Month -> settingsStore.setMonthWidgetColorMode(mode)
                KgsWidgetKind.Agenda -> settingsStore.setAgendaWidgetColorMode(mode)
                KgsWidgetKind.Tasks -> settingsStore.setTasksWidgetColorMode(mode)
                KgsWidgetKind.Day -> settingsStore.setDayWidgetColorMode(mode)
                KgsWidgetKind.Multi -> settingsStore.setMultiWidgetColorMode(mode)
            }
            widgetRefresher.update(kind, forceFullDayUpdate = kind == KgsWidgetKind.Day)
        }
    }

    fun setMultiWidgetMonthPercent(monthPercent: Int) {
        scope.launch {
            settingsStore.setMultiWidgetMonthPercent(monthPercent)
            widgetRefresher.update(KgsWidgetKind.Multi)
        }
    }

    fun setTasksWidgetDisplayMode(mode: WidgetTaskDisplayMode) {
        scope.launch {
            settingsStore.setTasksWidgetDisplayMode(mode)
            widgetRefresher.update(KgsWidgetKind.Tasks)
        }
    }

    fun setTasksWidgetIncludeOverdue(include: Boolean) {
        scope.launch {
            settingsStore.setTasksWidgetIncludeOverdue(include)
            widgetRefresher.update(KgsWidgetKind.Tasks)
        }
    }

    fun setTasksWidgetSortMode(mode: WidgetTaskSortMode) {
        scope.launch {
            settingsStore.setTasksWidgetSortMode(mode)
            widgetRefresher.update(KgsWidgetKind.Tasks)
        }
    }

    fun setTasksWidgetCreateMode(mode: WidgetTaskCreateMode) {
        scope.launch {
            settingsStore.setTasksWidgetCreateMode(mode)
            widgetRefresher.update(KgsWidgetKind.Tasks)
        }
    }

    fun setTasksWidgetSubtaskDefaultMode(mode: WidgetTaskSubtaskDefaultMode) {
        scope.launch {
            settingsStore.setTasksWidgetSubtaskDefaultMode(mode)
            widgetRefresher.update(KgsWidgetKind.Tasks)
        }
    }

    fun setDayWidgetScalePercent(scalePercent: Int) {
        scope.launch {
            settingsStore.setDayWidgetScalePercent(scalePercent)
            widgetRefresher.update(KgsWidgetKind.Day, forceFullDayUpdate = true)
        }
    }

    fun setDayWidgetStartHour(startHour: Int) {
        scope.launch {
            settingsStore.setDayWidgetStartHour(startHour)
            widgetRefresher.update(KgsWidgetKind.Day, forceFullDayUpdate = true)
        }
    }

    fun setDayWidgetStartAtCurrentHour(enabled: Boolean) {
        scope.launch {
            settingsStore.setDayWidgetStartAtCurrentHour(enabled)
            widgetRefresher.update(KgsWidgetKind.Day, forceFullDayUpdate = true)
        }
    }

    fun setLanguageMode(mode: AppLanguageMode) {
        scope.launch { settingsStore.setLanguageMode(mode) }
    }

    fun setTaskColorMode(mode: TaskColorMode) {
        scope.launch {
            settingsStore.setTaskColorMode(mode)
            widgetRefresher.updateAll()
        }
    }

    fun setFocusTitleOnCreate(enabled: Boolean) {
        scope.launch { settingsStore.setFocusTitleOnCreate(enabled) }
    }

    fun setFirstDayOfWeek(dayOfWeek: DayOfWeek) {
        scope.launch {
            applyFirstDayOfWeekChange(
                dayOfWeek = dayOfWeek,
                activeWeekDate = {
                    currentState().takeIf {
                        it.weekViewEnabled && it.selectedView == CalendarViewMode.ThreeDay
                    }?.selectedDate
                },
                persistFirstDayOfWeek = settingsStore::setFirstDayOfWeek,
                publishedFirstDayOfWeek = publishedFirstDayOfWeek,
                selectDate = selectDate,
            )
        }
    }

    fun setShowCompletedTasksInCalendar(show: Boolean) {
        scope.launch { settingsStore.setShowCompletedTasksInCalendar(show) }
    }

    fun setShowCalendarWeeks(show: Boolean) {
        scope.launch { settingsStore.setShowCalendarWeeks(show) }
    }

    fun setPriorityAnimationsEnabled(enabled: Boolean) {
        scope.launch {
            settingsStore.setPriorityAnimationsEnabled(enabled)
            widgetRefresher.update(KgsWidgetKind.Tasks)
        }
    }

    fun setOverdueSummaryPriorityAnimationEnabled(enabled: Boolean) {
        scope.launch {
            settingsStore.setOverdueSummaryPriorityAnimationEnabled(enabled)
        }
    }

    fun setSubtasksExpandedByDefault(expanded: Boolean) {
        scope.launch {
            settingsStore.setSubtasksExpandedByDefault(expanded)
            widgetRefresher.update(KgsWidgetKind.Tasks)
        }
    }

    fun setAutoLoadMapPreviews(enabled: Boolean) {
        scope.launch { settingsStore.setAutoLoadMapPreviews(enabled) }
    }

    fun setMaxVisibleAllDayItems(maxItems: Int) {
        scope.launch { settingsStore.setMaxVisibleAllDayItems(maxItems) }
    }

    fun setMultiDayCount(count: Int) {
        if (isLandscape()) {
            setLandscapeMultiDayCount(count)
        } else {
            setPortraitMultiDayCount(count)
        }
    }

    fun setPortraitMultiDayCount(count: Int) {
        scope.launch { settingsStore.setPortraitMultiDayCount(count.coerceMultiDayCount()) }
    }

    fun setLandscapeMultiDayCount(count: Int) {
        scope.launch { settingsStore.setLandscapeMultiDayCount(count.coerceMultiDayCount()) }
    }

    fun setTimelineHourHeight(isLandscape: Boolean, hourHeightDp: Float) {
        scope.launch {
            if (isLandscape) {
                settingsStore.setLandscapeTimelineHourHeightDp(hourHeightDp)
            } else {
                settingsStore.setPortraitTimelineHourHeightDp(hourHeightDp)
            }
        }
    }

    fun setWeekViewEnabled(enabled: Boolean) {
        val state = currentState()
        if (enabled && state.selectedView == CalendarViewMode.ThreeDay) {
            selectDate(state.selectedDate.startOfWeek(state.firstDayOfWeek))
        }
        scope.launch { settingsStore.setWeekViewEnabled(enabled) }
    }

    fun setFullWeekSwipeEnabled(enabled: Boolean) {
        val state = currentState()
        if (enabled && state.weekViewEnabled && state.selectedView == CalendarViewMode.ThreeDay) {
            selectDate(state.selectedDate.startOfWeek(state.firstDayOfWeek))
        }
        scope.launch { settingsStore.setFullWeekSwipeEnabled(enabled) }
    }

    fun setMultiDaySidebarControlsEnabled(enabled: Boolean) {
        scope.launch { settingsStore.setMultiDaySidebarControlsEnabled(enabled) }
    }

    fun setDefaultEventDurationMinutes(minutes: Int) {
        scope.launch { settingsStore.setDefaultEventDurationMinutes(minutes) }
    }

    fun setDefaultTaskHasDate(hasDate: Boolean) {
        scope.launch { settingsStore.setDefaultTaskHasDate(hasDate) }
    }

    fun setDefaultTaskHasTime(hasTime: Boolean) {
        scope.launch { settingsStore.setDefaultTaskHasTime(hasTime) }
    }

    fun setDefaultEventReminderMinutes(reminders: Set<Int>) {
        scope.launch { settingsStore.setDefaultEventReminderMinutes(reminders) }
    }

    fun setDefaultTaskReminderMinutes(reminders: Set<Int>) {
        scope.launch { settingsStore.setDefaultTaskReminderMinutes(reminders) }
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
        scope.launch { settingsStore.setDefaultEventCollectionHref(href) }
    }

    fun setDefaultTaskCollectionHref(href: String?) {
        scope.launch { settingsStore.setDefaultTaskCollectionHref(href) }
    }

    fun setEventFieldOrder(order: List<String>) {
        scope.launch { settingsStore.setEventFieldOrder(order) }
    }

    fun setTaskFieldOrder(order: List<String>) {
        scope.launch { settingsStore.setTaskFieldOrder(order) }
    }

    fun setCollectionVisibleInViews(href: String, visible: Boolean) {
        scope.launch {
            settingsStore.setCollectionHiddenInViews(href, hidden = !visible)
        }
    }

    fun completeWelcome() {
        scope.launch { settingsStore.setWelcomeCompleted(true) }
    }

    private fun updateNotificationSetting(block: suspend () -> Unit) {
        scope.launch {
            runCatching {
                block()
                reminderRescheduler.reschedule()
                message.value = null
            }.onFailure {
                message.value = it.message ?: "Benachrichtigungen konnten nicht aktualisiert werden."
            }
        }
    }
}
