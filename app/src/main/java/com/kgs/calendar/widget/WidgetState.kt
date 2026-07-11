package com.kgs.calendar.widget

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

internal object KgsWidgetMonthState {
    private const val PREFS_NAME = "kgs_widget_state"
    private const val MONTH_PREFIX = "month_"
    private const val PAGE_PREFIX = "month_page_"
    private const val DIRECTION_PREFIX = "month_direction_"
    private const val REVISION_PREFIX = "month_revision_"
    private val synchronization = MonthNavSynchronizationDomain()

    fun apply(
        context: Context,
        appWidgetId: Int,
        command: MonthCommand,
        clock: Clock = Clock.systemDefaultZone(),
    ): MonthNavSnapshot =
        WidgetMonthNavigation(PreferencesMonthNavStorage(preferences(context)), clock)
            .navigate(appWidgetId, command)

    fun isCurrent(context: Context, snapshot: MonthNavSnapshot): Boolean =
        WidgetMonthNavigation(
            storage = PreferencesMonthNavStorage(preferences(context)),
            clock = Clock.systemDefaultZone(),
        ).isCurrent(snapshot)

    fun applyIfCurrent(
        context: Context,
        snapshot: MonthNavSnapshot,
        block: () -> Unit,
    ): Boolean =
        WidgetMonthNavigation(
            storage = PreferencesMonthNavStorage(preferences(context)),
            clock = Clock.systemDefaultZone(),
        ).applyIfCurrent(snapshot, block)

    fun applyIfRevisionCurrent(
        context: Context,
        appWidgetId: Int,
        revision: Long,
        block: () -> Unit,
    ): Boolean =
        WidgetMonthNavigation(
            storage = PreferencesMonthNavStorage(preferences(context)),
            clock = Clock.systemDefaultZone(),
        ).applyIfRevisionCurrent(appWidgetId, revision, block)

    fun month(context: Context, appWidgetId: Int, fallback: YearMonth): YearMonth =
        PreferencesMonthNavStorage(preferences(context)).read(appWidgetId)?.month ?: fallback

    fun revision(context: Context, appWidgetId: Int): Long =
        PreferencesMonthNavStorage(preferences(context)).read(appWidgetId)?.revision ?: 0L

    fun clear(context: Context, appWidgetId: Int) {
        synchronization.withWidgetLock(appWidgetId) {
            preferences(context).edit()
                .remove("$MONTH_PREFIX$appWidgetId")
                .remove("$PAGE_PREFIX$appWidgetId")
                .remove("$DIRECTION_PREFIX$appWidgetId")
                .remove("$REVISION_PREFIX$appWidgetId")
                .commit()
        }
    }

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private class PreferencesMonthNavStorage(
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

internal object KgsWidgetDayState {
    private const val PREFS_NAME = "kgs_widget_state"
    private const val DAY_PREFIX = "day_"
    private const val INITIAL_SCROLL_PREFIX = "day_initial_scroll_v10_ordered_"
    private const val INITIALIZED_PREFIX = "day_initialized_v14_ordered_"
    private const val ALL_DAY_EXPANDED_PREFIX = "day_all_day_expanded_"

    fun day(context: Context, appWidgetId: Int, fallback: LocalDate): LocalDate {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$DAY_PREFIX$appWidgetId", null)
        return raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: fallback
    }

    fun offset(context: Context, appWidgetId: Int, days: Int) {
        val current = day(context, appWidgetId, LocalDate.now())
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$DAY_PREFIX$appWidgetId", current.plusDays(days.toLong()).toString())
            .apply()
    }

    fun resetToToday(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("$DAY_PREFIX$appWidgetId")
            .remove("$INITIAL_SCROLL_PREFIX$appWidgetId")
            .apply()
    }

    fun needsInitialScroll(context: Context, appWidgetId: Int, startHour: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "$INITIAL_SCROLL_PREFIX$appWidgetId"
        val value = startHour.coerceIn(0, 23).toString()
        return prefs.getString(key, null) != value
    }

    fun markInitialScrollApplied(context: Context, appWidgetId: Int, startHour: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$INITIAL_SCROLL_PREFIX$appWidgetId", startHour.coerceIn(0, 23).toString())
            .apply()
    }

    fun requestConfiguredScroll(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("$INITIAL_SCROLL_PREFIX$appWidgetId")
            .apply()
    }

    fun isInitialized(context: Context, appWidgetId: Int): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("$INITIALIZED_PREFIX$appWidgetId", false)

    fun markInitialized(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("$INITIALIZED_PREFIX$appWidgetId", true)
            .apply()
    }

    fun isAllDayExpanded(context: Context, appWidgetId: Int): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("$ALL_DAY_EXPANDED_PREFIX$appWidgetId", false)

    fun setAllDayExpanded(context: Context, appWidgetId: Int, expanded: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("$ALL_DAY_EXPANDED_PREFIX$appWidgetId", expanded)
            .apply()
    }

    fun clear(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("$DAY_PREFIX$appWidgetId")
            .remove("$INITIAL_SCROLL_PREFIX$appWidgetId")
            .remove("$INITIALIZED_PREFIX$appWidgetId")
            .remove("$ALL_DAY_EXPANDED_PREFIX$appWidgetId")
            .apply()
    }
}

internal object KgsWidgetTaskExpansionState {
    private const val PREFS_NAME = "kgs_widget_state"
    private const val EXPANDED_PREFIX = "task_expanded_"

    fun isExpanded(context: Context, appWidgetId: Int, taskResourceHref: String, defaultExpanded: Boolean): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(key(appWidgetId, taskResourceHref), defaultExpanded)

    fun toggle(context: Context, appWidgetId: Int, taskResourceHref: String, defaultExpanded: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = key(appWidgetId, taskResourceHref)
        prefs.edit()
            .putBoolean(key, !prefs.getBoolean(key, defaultExpanded))
            .apply()
    }

    fun setExpanded(context: Context, appWidgetId: Int, taskResourceHref: String, expanded: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key(appWidgetId, taskResourceHref), expanded)
            .apply()
    }

    private fun key(appWidgetId: Int, taskResourceHref: String): String =
        "$EXPANDED_PREFIX$appWidgetId:${taskResourceHref.hashCode()}"
}

internal object KgsWidgetInteractionTokens {
    private val tokens = mutableMapOf<String, Long>()

    fun next(key: String): Long =
        synchronized(tokens) {
            val next = (tokens[key] ?: 0L) + 1L
            tokens[key] = next
            next
        }

    fun isCurrent(key: String, token: Long): Boolean =
        synchronized(tokens) { tokens[key] == token }
}

internal object KgsWidgetMonthPageCache {
    private const val MAX_ENTRIES = 64
    private val pages = LinkedHashMap<String, WidgetMonthPage>(MAX_ENTRIES, 0.75f, true)

    fun get(
        month: YearMonth,
        settings: WidgetRenderSettings,
        zoneId: String,
        generation: Long = WidgetDataGeneration.current(),
    ): WidgetMonthPage? =
        synchronized(pages) { pages[cacheKey(month, settings, zoneId, generation)] }

    fun put(
        month: YearMonth,
        settings: WidgetRenderSettings,
        zoneId: String,
        page: WidgetMonthPage,
        generation: Long = WidgetDataGeneration.current(),
    ) {
        synchronized(pages) {
            pages[cacheKey(month, settings, zoneId, generation)] = page
            while (pages.size > MAX_ENTRIES) {
                val firstKey = pages.entries.firstOrNull()?.key ?: break
                pages.remove(firstKey)
            }
        }
    }

    private fun cacheKey(
        month: YearMonth,
        settings: WidgetRenderSettings,
        zoneId: String,
        generation: Long,
    ): String = "${widgetMonthPageModelNamespace(settings, zoneId)}|$generation|$month"
}

internal fun widgetMonthPageModelNamespace(
    settings: WidgetRenderSettings,
    zoneId: String,
): String = buildString {
    append(WIDGET_MONTH_RENDER_SIGNATURE_VERSION)
    append('|').append(settings.locale.toLanguageTag())
    append('|').append(settings.firstDayOfWeek.name)
    append('|').append(settings.taskColorMode.name)
    append('|').append(settings.showCompletedTasks)
    append('|').append(settings.hiddenCollectionHrefs.sorted().joinToString(","))
    append('|').append(zoneId)
}

internal object KgsWidgetMonthUpdateSignatures {
    private val appliedSignatures = mutableMapOf<Int, String>()

    fun matches(appWidgetId: Int, signature: String): Boolean =
        synchronized(appliedSignatures) { appliedSignatures[appWidgetId] == signature }

    fun markApplied(appWidgetId: Int, signature: String) {
        synchronized(appliedSignatures) {
            appliedSignatures[appWidgetId] = signature
        }
    }
}

internal object KgsWidgetCollectionUpdateSignatures {
    private val appliedSignatures = mutableMapOf<String, String>()

    fun matches(kind: KgsWidgetKind, appWidgetId: Int, signature: String): Boolean =
        synchronized(appliedSignatures) { appliedSignatures[key(kind, appWidgetId)] == signature }

    fun markApplied(kind: KgsWidgetKind, appWidgetId: Int, signature: String) {
        synchronized(appliedSignatures) {
            appliedSignatures[key(kind, appWidgetId)] = signature
        }
    }

    private fun key(kind: KgsWidgetKind, appWidgetId: Int): String =
        "${kind.name}:$appWidgetId"
}

internal data class WidgetCollectionSnapshot(
    val kind: KgsWidgetKind,
    val appWidgetId: Int,
    val settings: WidgetRenderSettings,
    val palette: WidgetPalette,
    val renderSize: WidgetSize,
    val taskArtWidthDp: Float,
    val rows: List<WidgetListRow>,
    val signature: String,
    val createdAtMillis: Long = SystemClock.elapsedRealtime(),
)

internal data class WidgetCollectionRenderOptions(
    val taskRows: Map<Long, WidgetTaskRowRenderOptions> = emptyMap(),
    val suppressPriorityMotion: Boolean = false,
    val lightweightTaskTransition: Boolean = false,
    val taskArtWidthDp: Float = WIDGET_TASK_ART_WIDTH_DP.toFloat(),
) {
    fun withTaskArtWidth(widthDp: Float): WidgetCollectionRenderOptions =
        copy(taskArtWidthDp = widthDp.coerceAtLeast(1f))
}

internal data class WidgetTaskRowRenderOptions(
    val rowProgress: Float = 1f,
    val subtaskExpansionProgress: Float? = null,
)

internal object KgsWidgetCollectionRowsCache {
    private const val MAX_AGE_MS = 30 * 60 * 1_000L
    private val snapshots = mutableMapOf<String, WidgetCollectionSnapshot>()

    fun put(snapshot: WidgetCollectionSnapshot) {
        synchronized(snapshots) {
            snapshots[key(snapshot.kind, snapshot.appWidgetId)] = snapshot
        }
    }

    fun get(kind: KgsWidgetKind, appWidgetId: Int): WidgetCollectionSnapshot? {
        val now = SystemClock.elapsedRealtime()
        return synchronized(snapshots) {
            val key = key(kind, appWidgetId)
            val snapshot = snapshots[key] ?: return@synchronized null
            if (now - snapshot.createdAtMillis <= MAX_AGE_MS) {
                snapshot
            } else {
                snapshots.remove(key)
                null
            }
        }
    }

    private fun key(kind: KgsWidgetKind, appWidgetId: Int): String =
        "${kind.name}:$appWidgetId"
}
