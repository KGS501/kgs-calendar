package com.kgs.calendar.widget.model

import android.content.res.Configuration
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.data.settings.WidgetTaskCreateMode
import com.kgs.calendar.data.settings.WidgetTaskDisplayMode
import com.kgs.calendar.data.settings.WidgetTaskSortMode
import com.kgs.calendar.data.settings.WidgetTaskSubtaskDefaultMode
import com.kgs.calendar.domain.task.statusSortRank
import com.kgs.calendar.widget.WIDGET_DAY_HOUR_ROW_HEIGHT_DP
import java.time.DayOfWeek
import java.util.Locale

internal data class WidgetRenderSettings(
    val locale: Locale = Locale.getDefault(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val hiddenCollectionHrefs: Set<String> = emptySet(),
    val showCompletedTasks: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.KgsBlue,
    val colorMode: AppColorMode = AppColorMode.Auto,
    val systemNightMode: Int = Configuration.UI_MODE_NIGHT_UNDEFINED,
    val taskColorMode: TaskColorMode = TaskColorMode.Collection,
    val priorityAnimationsEnabled: Boolean = true,
    val subtasksExpandedByDefault: Boolean = true,
    val tasksWidgetDisplayMode: WidgetTaskDisplayMode = WidgetTaskDisplayMode.Planned,
    val tasksWidgetIncludeOverdue: Boolean = true,
    val tasksWidgetSortMode: WidgetTaskSortMode = WidgetTaskSortMode.Date,
    val tasksWidgetCreateMode: WidgetTaskCreateMode = WidgetTaskCreateMode.Today,
    val tasksWidgetSubtaskDefaultMode: WidgetTaskSubtaskDefaultMode = WidgetTaskSubtaskDefaultMode.FollowApp,
    val maxVisibleAllDayItems: Int = 3,
    val dayWidgetScalePercent: Int = SettingsStore.DEFAULT_DAY_WIDGET_SCALE_PERCENT,
    val dayWidgetStartHour: Int = SettingsStore.DEFAULT_DAY_WIDGET_START_HOUR,
    val dayWidgetStartAtCurrentHour: Boolean = SettingsStore.DEFAULT_DAY_WIDGET_START_AT_CURRENT_HOUR,
    val multiWidgetMonthPercent: Int = SettingsStore.DEFAULT_MULTI_WIDGET_MONTH_PERCENT,
) {
    fun dayWidgetHourRowHeightDp(): Float =
        WIDGET_DAY_HOUR_ROW_HEIGHT_DP * SettingsStore.normalizeDayWidgetScalePercent(dayWidgetScalePercent) / 100f

    fun weekDays(): List<DayOfWeek> =
        (0..6).map { firstDayOfWeek.plus(it.toLong()) }

    fun taskComparator(): Comparator<TaskEntity> {
        val titleComparator = compareBy<TaskEntity> { it.title.lowercase(locale) }
        return when (tasksWidgetSortMode) {
            WidgetTaskSortMode.Priority -> compareBy<TaskEntity> { it.priority ?: 9 }
                .thenBy { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
                .then(titleComparator)
            WidgetTaskSortMode.Status -> compareBy<TaskEntity> { it.statusSortRank() }
                .thenBy { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
                .then(titleComparator)
            WidgetTaskSortMode.Date -> compareBy<TaskEntity> { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
                .thenBy { it.priority ?: 9 }
                .then(titleComparator)
        }
    }
}

private fun DayOfWeek.plus(days: Long): DayOfWeek =
    DayOfWeek.of(((value - 1 + days.toInt()) % 7) + 1)
