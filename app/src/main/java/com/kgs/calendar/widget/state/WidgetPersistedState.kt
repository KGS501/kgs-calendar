package com.kgs.calendar.widget.state

import android.content.Context
import android.content.SharedPreferences
import com.kgs.calendar.widget.model.MonthCommand
import com.kgs.calendar.widget.model.MonthNavSnapshot
import com.kgs.calendar.widget.model.MonthNavStorage
import com.kgs.calendar.widget.model.MonthNavSynchronizationDomain
import com.kgs.calendar.widget.model.WidgetMonthNavigation
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

internal class WidgetMonthState(private val context: Context) {
    private val synchronization = MonthNavSynchronizationDomain()

    fun apply(
        appWidgetId: Int,
        command: MonthCommand,
        clock: Clock = Clock.systemDefaultZone(),
    ): MonthNavSnapshot =
        WidgetMonthNavigation(PreferencesMonthNavStorage(preferences()), clock)
            .navigate(appWidgetId, command)

    fun isCurrent(snapshot: MonthNavSnapshot): Boolean =
        WidgetMonthNavigation(
            storage = PreferencesMonthNavStorage(preferences()),
            clock = Clock.systemDefaultZone(),
        ).isCurrent(snapshot)

    fun applyIfCurrent(
        snapshot: MonthNavSnapshot,
        block: () -> Unit,
    ): Boolean =
        WidgetMonthNavigation(
            storage = PreferencesMonthNavStorage(preferences()),
            clock = Clock.systemDefaultZone(),
        ).applyIfCurrent(snapshot, block)

    fun applyIfRevisionCurrent(
        appWidgetId: Int,
        revision: Long,
        block: () -> Unit,
    ): Boolean =
        WidgetMonthNavigation(
            storage = PreferencesMonthNavStorage(preferences()),
            clock = Clock.systemDefaultZone(),
        ).applyIfRevisionCurrent(appWidgetId, revision, block)

    fun month(appWidgetId: Int, fallback: YearMonth): YearMonth =
        PreferencesMonthNavStorage(preferences()).read(appWidgetId)?.month ?: fallback

    fun revision(appWidgetId: Int): Long =
        PreferencesMonthNavStorage(preferences()).read(appWidgetId)?.revision ?: 0L

    fun clear(appWidgetId: Int) {
        synchronization.withWidgetLock(appWidgetId) {
            preferences().edit()
                .remove("$MONTH_PREFIX$appWidgetId")
                .remove("$PAGE_PREFIX$appWidgetId")
                .remove("$DIRECTION_PREFIX$appWidgetId")
                .remove("$REVISION_PREFIX$appWidgetId")
                .commit()
        }
    }

    private fun preferences(): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS_NAME = "kgs_widget_state"
        const val MONTH_PREFIX = "month_"
        const val PAGE_PREFIX = "month_page_"
        const val DIRECTION_PREFIX = "month_direction_"
        const val REVISION_PREFIX = "month_revision_"
    }

    private inner class PreferencesMonthNavStorage(
        private val preferences: SharedPreferences,
    ) : MonthNavStorage {
        override fun update(
            widgetId: Int,
            transform: (MonthNavSnapshot?) -> MonthNavSnapshot,
        ): MonthNavSnapshot = synchronization.withWidgetLock(widgetId) {
            val updated = transform(readLocked(widgetId))
            check(
                preferences.edit()
                    .putString("$MONTH_PREFIX$widgetId", updated.month.toString())
                    .putInt("$PAGE_PREFIX$widgetId", updated.page.coerceIn(0, 1))
                    .putInt("$DIRECTION_PREFIX$widgetId", updated.direction.coerceIn(-1, 1))
                    .putLong("$REVISION_PREFIX$widgetId", updated.revision)
                    .commit(),
            ) { "Failed to commit month widget navigation state" }
            updated
        }

        override fun read(widgetId: Int): MonthNavSnapshot? = synchronization.withWidgetLock(widgetId) {
            readLocked(widgetId)
        }

        override fun applyIfCurrent(
            snapshot: MonthNavSnapshot,
            block: () -> Unit,
        ): Boolean = synchronization.withWidgetLock(snapshot.widgetId) {
            if (readLocked(snapshot.widgetId)?.revision != snapshot.revision) {
                false
            } else {
                block()
                true
            }
        }

        override fun applyIfRevisionCurrent(
            widgetId: Int,
            revision: Long,
            block: () -> Unit,
        ): Boolean = synchronization.withWidgetLock(widgetId) {
            if ((readLocked(widgetId)?.revision ?: 0L) != revision) {
                false
            } else {
                block()
                true
            }
        }

        private fun readLocked(widgetId: Int): MonthNavSnapshot? {
            val month = preferences.getString("$MONTH_PREFIX$widgetId", null)
                ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                ?: return null
            return MonthNavSnapshot(
                widgetId = widgetId,
                month = month,
                page = preferences.getInt("$PAGE_PREFIX$widgetId", 0).coerceIn(0, 1),
                direction = preferences.getInt("$DIRECTION_PREFIX$widgetId", 0).coerceIn(-1, 1),
                revision = preferences.getLong("$REVISION_PREFIX$widgetId", 0L),
            )
        }
    }
}

internal class WidgetDayState(private val context: Context) {
    fun day(appWidgetId: Int, fallback: LocalDate): LocalDate {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$DAY_PREFIX$appWidgetId", null)
        return raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: fallback
    }

    fun offset(appWidgetId: Int, days: Int) {
        val current = day(appWidgetId, LocalDate.now())
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$DAY_PREFIX$appWidgetId", current.plusDays(days.toLong()).toString())
            .apply()
    }

    fun resetToToday(appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("$DAY_PREFIX$appWidgetId")
            .remove("$INITIAL_SCROLL_PREFIX$appWidgetId")
            .apply()
    }

    fun needsInitialScroll(appWidgetId: Int, startHour: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "$INITIAL_SCROLL_PREFIX$appWidgetId"
        val value = startHour.coerceIn(0, 23).toString()
        return prefs.getString(key, null) != value
    }

    fun markInitialScrollApplied(appWidgetId: Int, startHour: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$INITIAL_SCROLL_PREFIX$appWidgetId", startHour.coerceIn(0, 23).toString())
            .apply()
    }

    fun requestConfiguredScroll(appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("$INITIAL_SCROLL_PREFIX$appWidgetId")
            .apply()
    }

    fun isInitialized(appWidgetId: Int): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("$INITIALIZED_PREFIX$appWidgetId", false)

    fun markInitialized(appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("$INITIALIZED_PREFIX$appWidgetId", true)
            .apply()
    }

    fun isAllDayExpanded(appWidgetId: Int): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("$ALL_DAY_EXPANDED_PREFIX$appWidgetId", false)

    fun setAllDayExpanded(appWidgetId: Int, expanded: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("$ALL_DAY_EXPANDED_PREFIX$appWidgetId", expanded)
            .apply()
    }

    fun clear(appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("$DAY_PREFIX$appWidgetId")
            .remove("$INITIAL_SCROLL_PREFIX$appWidgetId")
            .remove("$INITIALIZED_PREFIX$appWidgetId")
            .remove("$ALL_DAY_EXPANDED_PREFIX$appWidgetId")
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "kgs_widget_state"
        const val DAY_PREFIX = "day_"
        const val INITIAL_SCROLL_PREFIX = "day_initial_scroll_v10_ordered_"
        const val INITIALIZED_PREFIX = "day_initialized_v14_ordered_"
        const val ALL_DAY_EXPANDED_PREFIX = "day_all_day_expanded_"
    }
}

internal class WidgetTaskExpansionState(private val context: Context) {
    fun isExpanded(appWidgetId: Int, taskResourceHref: String, defaultExpanded: Boolean): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(key(appWidgetId, taskResourceHref), defaultExpanded)

    fun toggle(appWidgetId: Int, taskResourceHref: String, defaultExpanded: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = key(appWidgetId, taskResourceHref)
        prefs.edit()
            .putBoolean(key, !prefs.getBoolean(key, defaultExpanded))
            .apply()
    }

    fun setExpanded(appWidgetId: Int, taskResourceHref: String, expanded: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key(appWidgetId, taskResourceHref), expanded)
            .apply()
    }

    private fun key(appWidgetId: Int, taskResourceHref: String): String =
        "$EXPANDED_PREFIX$appWidgetId:${taskResourceHref.hashCode()}"

    private companion object {
        const val PREFS_NAME = "kgs_widget_state"
        const val EXPANDED_PREFIX = "task_expanded_"
    }
}
