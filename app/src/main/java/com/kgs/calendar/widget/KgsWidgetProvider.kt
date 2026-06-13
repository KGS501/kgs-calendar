package com.kgs.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.MainActivity
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppLanguageMode
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.data.settings.WidgetColorMode
import com.kgs.calendar.data.settings.WidgetTaskCreateMode
import com.kgs.calendar.data.settings.WidgetTaskDisplayMode
import com.kgs.calendar.data.settings.WidgetTaskSortMode
import com.kgs.calendar.data.settings.WidgetThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "KgsWidget"
private const val EXTRA_WIDGET_KIND = "kgs_widget_kind"
private const val EXTRA_WIDGET_DATE = "kgs_widget_date"
private const val EXTRA_WIDGET_ACTION = "kgs_widget_action"
private const val EXTRA_WIDGET_TASK_UID = "kgs_widget_task_uid"
private const val EXTRA_WIDGET_TASK_CREATE_MODE = "kgs_widget_task_create_mode"
private const val WIDGET_ACTION_CREATE_EVENT = "create_event"
private const val WIDGET_ACTION_CREATE_TASK = "create_task"
private const val WIDGET_ACTION_OPEN_TASK = "open_task"
private const val WIDGET_TASK_CREATE_UNPLANNED = "Unplanned"
private const val EXTRA_COLLECTION_ACTION = "kgs_widget_collection_action"
private const val COLLECTION_ACTION_OPEN = "open"
private const val COLLECTION_ACTION_TOGGLE_TASK = "toggle_task"
private const val WIDGET_MONTH_MAX_LANES = 8

class KgsAgendaWidgetProvider : KgsWidgetProvider(KgsWidgetKind.Agenda)

class KgsMonthWidgetProvider : KgsWidgetProvider(KgsWidgetKind.Month)

class KgsTasksWidgetProvider : KgsWidgetProvider(KgsWidgetKind.Tasks)

class KgsMultiWidgetProvider : KgsWidgetProvider(KgsWidgetKind.Multi)

class KgsDayWidgetProvider : KgsWidgetProvider(KgsWidgetKind.Day)

abstract class KgsWidgetProvider(
    private val kind: KgsWidgetKind,
) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        KgsWidgetUpdateScheduler.update(context, kind, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        KgsWidgetUpdateScheduler.update(context, kind, intArrayOf(appWidgetId), debounceMillis = if (kind == KgsWidgetKind.Month) 80 else 160)
    }

    override fun onEnabled(context: Context) {
        KgsWidgetUpdateScheduler.update(context, kind)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MONTH_PREVIOUS,
            ACTION_MONTH_NEXT -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val direction = if (intent.action == ACTION_MONTH_PREVIOUS) -1 else 1
                    KgsWidgetMonthState.offset(context.applicationContext, appWidgetId, direction)
                    navigateMonthAsync(context, appWidgetId)
                } else {
                    super.onReceive(context, intent)
                }
            }

            ACTION_REFRESH -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                updateAsync(
                    context = context,
                    appWidgetIds = appWidgetId
                        .takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
                        ?.let { intArrayOf(it) },
                )
            }

            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                updateAsync(
                    context = context,
                    appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS),
                )
            }

            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    KgsWidgetUpdateScheduler.update(context, kind, intArrayOf(appWidgetId), debounceMillis = if (kind == KgsWidgetKind.Month) 80 else 160)
                } else {
                    super.onReceive(context, intent)
                }
            }

            else -> super.onReceive(context, intent)
        }
    }

    private fun updateAsync(context: Context, appWidgetIds: IntArray? = null) {
        val pendingResult = goAsync()
        KgsWidgetUpdateScheduler.launch {
            try {
                if (appWidgetIds == null) {
                    KgsWidgetUpdater.updateKind(context.applicationContext, kind)
                } else {
                    KgsWidgetUpdater.update(context.applicationContext, kind, appWidgetIds)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun navigateMonthAsync(context: Context, appWidgetId: Int) {
        val pendingResult = goAsync()
        KgsWidgetUpdateScheduler.launch {
            try {
                KgsWidgetUpdater.navigateMonth(context.applicationContext, appWidgetId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.kgs.calendar.widget.REFRESH"
        const val ACTION_MONTH_PREVIOUS = "com.kgs.calendar.widget.MONTH_PREVIOUS"
        const val ACTION_MONTH_NEXT = "com.kgs.calendar.widget.MONTH_NEXT"
        const val ACTION_COLLECTION_CLICK = "com.kgs.calendar.widget.COLLECTION_CLICK"
        const val EXTRA_TASK_RESOURCE_HREF = "com.kgs.calendar.widget.TASK_RESOURCE_HREF"
    }
}

class KgsWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != KgsWidgetProvider.ACTION_COLLECTION_CLICK) return
        when (intent.getStringExtra(EXTRA_COLLECTION_ACTION)) {
            COLLECTION_ACTION_OPEN -> context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_WIDGET_KIND, intent.getStringExtra(EXTRA_WIDGET_KIND))
                    putExtra(EXTRA_WIDGET_DATE, intent.getStringExtra(EXTRA_WIDGET_DATE))
                    intent.getStringExtra(EXTRA_WIDGET_ACTION)?.let { putExtra(EXTRA_WIDGET_ACTION, it) }
                    intent.getStringExtra(EXTRA_WIDGET_TASK_UID)?.let { putExtra(EXTRA_WIDGET_TASK_UID, it) }
                    data = Uri.parse("kgs-calendar://widget-router/${SystemClock.elapsedRealtime()}")
                },
            )

            COLLECTION_ACTION_TOGGLE_TASK -> {
                val taskId = intent.getStringExtra(KgsWidgetProvider.EXTRA_TASK_RESOURCE_HREF) ?: return
                val pending = goAsync()
                KgsWidgetUpdateScheduler.launch {
                    try {
                        val graph = KgsCalendarApplication.graph(context.applicationContext)
                        val task = graph.repository.allTasksSnapshot()
                            .firstOrNull { it.resourceHref == taskId || it.uid == taskId }
                        if (task != null) {
                            val nextStatus = if (task.isCompleted || task.status.equals("COMPLETED", ignoreCase = true)) {
                                "NEEDS-ACTION"
                            } else {
                                "COMPLETED"
                            }
                            graph.repository.setTaskStatus(task.resourceHref, nextStatus)
                        }
                        KgsWidgetUpdateScheduler.updateAll(context.applicationContext)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}

enum class KgsWidgetKind {
    Agenda,
    Month,
    Tasks,
    Multi,
    Day;

    val providerClass: Class<out AppWidgetProvider>
        get() = when (this) {
            Agenda -> KgsAgendaWidgetProvider::class.java
            Month -> KgsMonthWidgetProvider::class.java
            Tasks -> KgsTasksWidgetProvider::class.java
            Multi -> KgsMultiWidgetProvider::class.java
            Day -> KgsDayWidgetProvider::class.java
        }

    fun title(context: Context): String = when (this) {
        Agenda -> context.getString(R.string.agenda)
        Month -> context.getString(R.string.month)
        Tasks -> context.getString(R.string.tasks)
        Multi -> context.getString(R.string.widget_multi_title)
        Day -> context.getString(R.string.day)
    }
}

object KgsWidgetUpdateScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingResizeJobs = mutableMapOf<String, Job>()

    fun update(context: Context, kind: KgsWidgetKind, appWidgetIds: IntArray? = null, debounceMillis: Long = 0L) {
        if (debounceMillis > 0 && appWidgetIds != null) {
            val appContext = context.applicationContext
            val key = "${kind.name}:${appWidgetIds.sorted().joinToString(",")}"
            synchronized(pendingResizeJobs) {
                pendingResizeJobs.remove(key)?.cancel()
                val resizeJob = scope.launch {
                    delay(debounceMillis)
                    try {
                        KgsWidgetUpdater.update(appContext, kind, appWidgetIds)
                    } finally {
                        synchronized(pendingResizeJobs) {
                            if (pendingResizeJobs[key] == coroutineContext[Job]) {
                                pendingResizeJobs.remove(key)
                            }
                        }
                    }
                }
                pendingResizeJobs[key] = resizeJob
            }
            return
        }
        scope.launch {
            if (appWidgetIds == null) {
                KgsWidgetUpdater.updateKind(context.applicationContext, kind)
            } else {
                KgsWidgetUpdater.update(context.applicationContext, kind, appWidgetIds)
            }
        }
    }

    fun updateAll(context: Context) {
        scope.launch {
            KgsWidgetUpdater.updateAll(context.applicationContext)
        }
    }

    internal fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}

private object KgsWidgetMonthState {
    private const val PREFS_NAME = "kgs_widget_state"
    private const val MONTH_PREFIX = "month_"
    private const val PAGE_PREFIX = "month_page_"
    private const val DIRECTION_PREFIX = "month_direction_"

    fun month(context: Context, appWidgetId: Int, fallback: YearMonth): YearMonth {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$MONTH_PREFIX$appWidgetId", null)
        return raw?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: fallback
    }

    fun offset(context: Context, appWidgetId: Int, months: Int) {
        val current = month(context, appWidgetId, YearMonth.now())
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentPage = prefs.getInt("$PAGE_PREFIX$appWidgetId", 0)
        prefs.edit()
            .putString("$MONTH_PREFIX$appWidgetId", current.plusMonths(months.toLong()).toString())
            .putInt("$PAGE_PREFIX$appWidgetId", 1 - currentPage.coerceIn(0, 1))
            .putInt("$DIRECTION_PREFIX$appWidgetId", months.coerceIn(-1, 1))
            .apply()
    }

    fun page(context: Context, appWidgetId: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("$PAGE_PREFIX$appWidgetId", 0)
            .coerceIn(0, 1)

    fun consumeDirection(context: Context, appWidgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val direction = prefs.getInt("$DIRECTION_PREFIX$appWidgetId", 0)
        if (direction != 0) {
            prefs.edit().putInt("$DIRECTION_PREFIX$appWidgetId", 0).apply()
        }
        return direction
    }
}

private object KgsWidgetMonthPageCache {
    private const val MAX_ENTRIES = 18
    private val pages = LinkedHashMap<String, WidgetMonthPage>(MAX_ENTRIES, 0.75f, true)

    fun get(month: YearMonth, settings: WidgetRenderSettings): WidgetMonthPage? =
        synchronized(pages) { pages[cacheKey(month, settings)] }

    fun put(month: YearMonth, settings: WidgetRenderSettings, page: WidgetMonthPage) {
        synchronized(pages) {
            pages[cacheKey(month, settings)] = page
            while (pages.size > MAX_ENTRIES) {
                val firstKey = pages.entries.firstOrNull()?.key ?: break
                pages.remove(firstKey)
            }
        }
    }

    fun warm(context: Context, zoneId: ZoneId, centerMonth: YearMonth, settings: WidgetRenderSettings) {
        val appContext = context.applicationContext
        val months = listOf(centerMonth.minusMonths(1), centerMonth.plusMonths(1))
            .filter { get(it, settings) == null }
        if (months.isEmpty()) return
        KgsWidgetUpdateScheduler.launch {
            val source = KgsWidgetDataSource(appContext, zoneId)
            months.forEach { month ->
                runCatching {
                    source.monthPage(month, settings).also { put(month, settings, it) }
                }.onFailure { error ->
                    if (error !is CancellationException) {
                        Log.d(TAG, "Failed to warm Month widget cache for $month", error)
                    }
                }
            }
        }
    }

    private fun cacheKey(month: YearMonth, settings: WidgetRenderSettings): String =
        buildString {
            append(month)
            append('|')
            append(settings.locale.toLanguageTag())
            append('|')
            append(settings.firstDayOfWeek.name)
            append('|')
            append(settings.taskColorMode.name)
            append('|')
            append(settings.showCompletedTasks)
            append('|')
            append(settings.hiddenCollectionHrefs.sorted().joinToString(","))
        }
}

object KgsWidgetUpdater {
    suspend fun updateAll(context: Context) {
        KgsWidgetKind.entries.forEach { kind ->
            updateKind(context, kind)
        }
    }

    suspend fun updateKind(context: Context, kind: KgsWidgetKind) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, kind.providerClass))
        if (ids.isNotEmpty()) {
            update(context, kind, ids)
        }
    }

    suspend fun update(context: Context, kind: KgsWidgetKind, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        val renderer = KgsWidgetRenderer(context)
        appWidgetIds.forEach { appWidgetId ->
            val options = manager.getAppWidgetOptions(appWidgetId)
            val views = try {
                renderer.render(kind, appWidgetId, options)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to render ${kind.name} widget $appWidgetId", error)
                renderer.error(kind, appWidgetId, error.message ?: "Widget update failed.")
            }
            runCatching {
                manager.updateAppWidget(appWidgetId, views)
                if (kind.usesCollectionList) {
                    manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to update ${kind.name} widget $appWidgetId", error)
            }
        }
    }

    suspend fun navigateMonth(context: Context, appWidgetId: Int) {
        val manager = AppWidgetManager.getInstance(context)
        val renderer = KgsWidgetRenderer(context)
        val options = manager.getAppWidgetOptions(appWidgetId)
        val views = try {
            renderer.renderMonthNavigation(appWidgetId, options)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to animate Month widget $appWidgetId", error)
            renderer.render(KgsWidgetKind.Month, appWidgetId, options)
        }
        runCatching {
            manager.updateAppWidget(appWidgetId, views)
        }.onFailure { error ->
            Log.e(TAG, "Failed to update Month widget $appWidgetId after navigation", error)
            manager.updateAppWidget(appWidgetId, renderer.render(KgsWidgetKind.Month, appWidgetId, options))
        }
    }
}

private class KgsWidgetRenderer(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val packageName = context.packageName
    private val dataSource = KgsWidgetDataSource(context, zoneId)

    suspend fun render(kind: KgsWidgetKind, appWidgetId: Int, options: Bundle): RemoteViews {
        val start = SystemClock.elapsedRealtime()
        val result = if (kind == KgsWidgetKind.Month) {
            renderMonthWidget(appWidgetId, options, navigationDirection = 0)
        } else {
            renderCollectionWidget(kind, appWidgetId, options)
        }
        if (context.isDebuggable) {
            Log.d(TAG, "Rendered ${kind.name} widget $appWidgetId in ${SystemClock.elapsedRealtime() - start}ms")
        }
        return result
    }

    suspend fun renderMonthNavigation(appWidgetId: Int, options: Bundle): RemoteViews {
        val direction = KgsWidgetMonthState.consumeDirection(context, appWidgetId)
        return renderMonthWidget(
            appWidgetId = appWidgetId,
            options = options,
            navigationDirection = direction,
            preferCachedPage = direction != 0,
        )
    }

    fun error(kind: KgsWidgetKind, appWidgetId: Int, message: String): RemoteViews {
        val settings = WidgetRenderSettings(locale = Locale.getDefault())
        val palette = WidgetPalette.from(context, AppThemeMode.KgsBlue, AppColorMode.Auto)
        val views = RemoteViews(packageName, R.layout.widget_calendar)
        views.bindBaseShell(kind, appWidgetId, settings, palette, LocalDate.now(zoneId))
        views.setViewVisibility(R.id.widget_month_section, View.GONE)
        views.setViewVisibility(R.id.widget_list, View.GONE)
        views.showEmpty(message, palette)
        return views
    }

    private suspend fun renderMonthWidget(
        appWidgetId: Int,
        options: Bundle,
        navigationDirection: Int,
        preferCachedPage: Boolean = false,
    ): RemoteViews {
        val today = LocalDate.now(zoneId)
        val settings = dataSource.loadSettings(KgsWidgetKind.Month)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val month = KgsWidgetMonthState.month(context, appWidgetId, YearMonth.from(today))
        val cachedPage = if (preferCachedPage || navigationDirection != 0) {
            KgsWidgetMonthPageCache.get(month, settings)
        } else {
            null
        }
        val page = cachedPage ?: dataSource.monthPage(month, settings)
        KgsWidgetMonthPageCache.put(month, settings, page)
        KgsWidgetMonthPageCache.warm(context, zoneId, month, settings)
        val currentSize = WidgetSize.from(context, options, KgsWidgetKind.Month)

        if (context.isDebuggable) {
            var itemCount = 0
            for (cell in page.cells) itemCount += cell.items.size
            Log.d(TAG, "Month widget $appWidgetId bucket=${WidgetSizeBucket.from(currentSize, page.rowCount).name} rows=${page.rowCount} items=$itemCount cacheHit=${cachedPage != null}")
        }

        return renderMonthRoot(
            appWidgetId = appWidgetId,
            settings = settings,
            palette = palette,
            today = today,
            page = page,
            bucket = WidgetSizeBucket.from(currentSize, page.rowCount),
        )
    }

    private fun renderMonthRoot(
        appWidgetId: Int,
        settings: WidgetRenderSettings,
        palette: WidgetPalette,
        today: LocalDate,
        page: WidgetMonthPage,
        bucket: WidgetSizeBucket,
    ): RemoteViews {
        val views = RemoteViews(packageName, monthRenderedLayout(page.month))
        views.setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        val padding = context.dpToPx(bucket.rootPaddingDp)
        views.setViewPadding(R.id.widget_root, padding, padding, padding, padding)
        views.setTextViewText(R.id.widget_title, page.title(settings.locale))
        views.setTextColor(R.id.widget_title, palette.text)
        views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, bucket.titleTextSp)
        views.setTextViewText(R.id.widget_badge, "+")
        views.setTextColor(R.id.widget_badge, palette.onAccent)
        views.setInt(R.id.widget_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setOnClickPendingIntent(R.id.widget_badge, createEventPendingIntent(appWidgetId, today))
        views.setTextViewText(R.id.widget_month_prev, "\u2039")
        views.setTextViewText(R.id.widget_month_next, "\u203A")
        views.setTextColor(R.id.widget_month_prev, palette.accent)
        views.setTextColor(R.id.widget_month_next, palette.accent)
        views.setOnClickPendingIntent(R.id.widget_month_prev, monthNavigationPendingIntent(appWidgetId, previous = true))
        views.setOnClickPendingIntent(R.id.widget_month_next, monthNavigationPendingIntent(appWidgetId, previous = false))

        views.bindMonthPage(R.id.widget_month_page_a, page, settings, palette, bucket, appWidgetId)
        views.removeAllViews(R.id.widget_month_page_b)
        views.setDisplayedChild(R.id.widget_month_flipper, 0)
        return views
    }

    private fun RemoteViews.bindMonthPage(
        pageContainerId: Int,
        page: WidgetMonthPage,
        settings: WidgetRenderSettings,
        palette: WidgetPalette,
        bucket: WidgetSizeBucket,
        appWidgetId: Int,
    ) {
        removeAllViews(pageContainerId)
        val header = RemoteViews(packageName, R.layout.widget_month_grid_header_row)
        for (dayOfWeek in settings.weekDays()) {
            val weekday = RemoteViews(packageName, R.layout.widget_weekday_cell)
            weekday.setTextViewText(R.id.widget_weekday_text, dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, settings.locale))
            weekday.setTextColor(R.id.widget_weekday_text, palette.muted)
            weekday.setTextViewTextSize(R.id.widget_weekday_text, TypedValue.COMPLEX_UNIT_SP, bucket.weekdayTextSp)
            header.addView(R.id.widget_month_grid_row, weekday)
        }
        addView(pageContainerId, header)

        var index = 0
        repeat(page.rowCount) {
            val rowCells = page.cells.subList(index, index + 7)
            val row = RemoteViews(packageName, R.layout.widget_month_grid_week_row)
            repeat(7) { column ->
                val cell = rowCells[column]
                row.addView(
                    R.id.widget_month_grid_row,
                    if (bucket.usesDotCells) {
                        renderMonthCell(cell, palette, bucket, appWidgetId)
                    } else {
                        renderMonthCellShell(cell, palette, bucket, appWidgetId)
                    },
                )
            }
            if (bucket.usesDotCells) {
                row.setViewVisibility(R.id.widget_month_week_chip_layers, View.GONE)
            } else {
                row.bindMonthWeekChips(rowCells, palette, bucket, appWidgetId)
            }
            addView(pageContainerId, row)
            index += 7
        }
    }

    private fun monthRenderedLayout(month: YearMonth): Int =
        if (month.monthValue % 2 == 0) {
            R.layout.widget_month_calendar_rendered_a
        } else {
            R.layout.widget_month_calendar_rendered_b
        }

    private fun RemoteViews.bindMonthWeekChips(
        rowCells: List<WidgetMonthCellContent>,
        palette: WidgetPalette,
        bucket: WidgetSizeBucket,
        appWidgetId: Int,
    ) {
        removeAllViews(R.id.widget_month_week_chip_layers)
        setViewVisibility(R.id.widget_month_week_chip_layers, View.VISIBLE)
        repeat(bucket.textItemCapacity) { lane ->
            val laneRow = RemoteViews(packageName, R.layout.widget_month_chip_lane_row)
            var column = 0
            for (segment in rowCells.monthWeekSegments(lane)) {
                val leading = segment.startColumn - column
                if (leading > 0) {
                    laneRow.addDaySpacers(leading)
                }
                val span = segment.columnSpan.coerceIn(1, 7)
                val chip = RemoteViews(packageName, monthSpanChipLayout(span))
                val style = segment.item.color.monthChipStyle()
                chip.setTextViewText(R.id.widget_month_span_chip_text, segment.title)
                chip.setTextColor(R.id.widget_month_span_chip_text, style.textColor)
                chip.setInt(R.id.widget_month_span_chip_text, "setBackgroundResource", style.backgroundRes(segment.item))
                chip.setTextViewTextSize(R.id.widget_month_span_chip_text, TypedValue.COMPLEX_UNIT_SP, bucket.chipTextSp)
                chip.setViewPadding(
                    R.id.widget_month_span_chip_text,
                    context.dpToPx(if (segment.item.fadesFromPrevious) 8 else 4),
                    0,
                    context.dpToPx(if (segment.item.fadesToNext) 8 else 4),
                    0,
                )
                chip.setOnClickPendingIntent(
                    R.id.widget_month_span_chip_text,
                    openAppPendingIntent(
                        KgsWidgetKind.Day,
                        dayPendingIntentRequestCode(appWidgetId, rowCells[segment.startColumn].date),
                        rowCells[segment.startColumn].date,
                    ),
                )
                laneRow.addView(R.id.widget_month_chip_lane_row, chip)
                column = segment.endColumn + 1
            }
            if (column < 7) {
                laneRow.addDaySpacers(7 - column)
            }
            addView(R.id.widget_month_week_chip_layers, laneRow)
        }
        val overflowByColumn = rowCells.map { cell ->
            if (!cell.inCurrentMonth) {
                0
            } else {
                val visibleCount = cell.items.count { it.lane < bucket.textItemCapacity }
                (cell.totalItemCount - visibleCount).coerceAtLeast(0)
            }
        }
        if (overflowByColumn.any { it > 0 }) {
            val overflowRow = RemoteViews(packageName, R.layout.widget_month_chip_lane_row)
            overflowByColumn.forEachIndexed { column, overflow ->
                if (overflow > 0) {
                    val cell = rowCells[column]
                    val overflowView = RemoteViews(packageName, R.layout.widget_month_overflow_cell)
                    val isToday = cell.date == LocalDate.now(zoneId)
                    overflowView.setTextViewText(R.id.widget_month_overflow_text, "+$overflow")
                    overflowView.setTextColor(R.id.widget_month_overflow_text, if (isToday) palette.onAccent else palette.muted)
                    overflowRow.addView(R.id.widget_month_chip_lane_row, overflowView)
                } else {
                    overflowRow.addView(R.id.widget_month_chip_lane_row, RemoteViews(packageName, R.layout.widget_month_span_spacer))
                }
            }
            addView(R.id.widget_month_week_chip_layers, overflowRow)
        }
    }

    private fun RemoteViews.addDaySpacers(count: Int) {
        repeat(count.coerceAtLeast(0)) {
            addView(R.id.widget_month_chip_lane_row, RemoteViews(packageName, R.layout.widget_month_span_spacer))
        }
    }

    private fun renderMonthCellShell(
        cell: WidgetMonthCellContent,
        palette: WidgetPalette,
        bucket: WidgetSizeBucket,
        appWidgetId: Int,
    ): RemoteViews {
        val views = RemoteViews(packageName, bucket.layoutRes)
        if (!cell.inCurrentMonth) {
            views.setViewVisibility(R.id.widget_month_day_root, View.INVISIBLE)
            return views
        }
        val isToday = cell.date == LocalDate.now(zoneId)
        views.setInt(
            R.id.widget_month_day_card_background,
            "setBackgroundResource",
            when {
                isToday -> palette.daySelectedBackgroundRes
                bucket.usesDotCells -> palette.compactItemBackgroundRes
                else -> palette.itemBackgroundRes
            },
        )
        views.setTextViewText(R.id.widget_month_day_text, cell.date.dayOfMonth.toString())
        views.setTextColor(R.id.widget_month_day_text, if (isToday) palette.onAccent else palette.text)
        views.setTextViewTextSize(R.id.widget_month_day_text, TypedValue.COMPLEX_UNIT_SP, bucket.dayTextSp)
        val dayPendingIntent = openAppPendingIntent(KgsWidgetKind.Day, dayPendingIntentRequestCode(appWidgetId, cell.date), cell.date)
        views.setOnClickPendingIntent(R.id.widget_month_day_root, dayPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_month_day_text, dayPendingIntent)
        return views
    }

    private fun renderMonthCell(
        cell: WidgetMonthCellContent,
        palette: WidgetPalette,
        bucket: WidgetSizeBucket,
        appWidgetId: Int,
    ): RemoteViews {
        val views = renderMonthCellShell(cell, palette, bucket, appWidgetId)
        if (!cell.inCurrentMonth) return views
        val isToday = cell.date == LocalDate.now(zoneId)
        val dayPendingIntent = openAppPendingIntent(KgsWidgetKind.Day, dayPendingIntentRequestCode(appWidgetId, cell.date), cell.date)

        when (bucket) {
            WidgetSizeBucket.Tiny,
            WidgetSizeBucket.Mini -> views.bindMiniMonthCell(cell, palette, bucket, isToday)
            else -> views.bindTextMonthCell(cell, palette, bucket, isToday, dayPendingIntent)
        }
        return views
    }

    private fun RemoteViews.bindMiniMonthCell(
        cell: WidgetMonthCellContent,
        palette: WidgetPalette,
        bucket: WidgetSizeBucket,
        isToday: Boolean,
    ) {
        val dotIds = listOf(
            R.id.widget_month_dot_1,
            R.id.widget_month_dot_2,
            R.id.widget_month_dot_3,
            R.id.widget_month_dot_4,
            R.id.widget_month_dot_5,
            R.id.widget_month_dot_6,
            R.id.widget_month_dot_7,
            R.id.widget_month_dot_8,
        )
        val sortedItems = cell.items.sortedBy { it.lane }
        val visibleCount = minOf(sortedItems.size, bucket.miniDotCapacity)
        for (index in dotIds.indices) {
            val id = dotIds[index]
            val item = if (index < visibleCount) sortedItems[index] else null
            setViewVisibility(id, if (item == null) View.GONE else View.VISIBLE)
            setTextViewText(id, "\u25CF")
            setTextColor(id, item?.color ?: palette.faint)
        }
        val hidden = (cell.totalItemCount - visibleCount).coerceAtLeast(0)
        setViewVisibility(R.id.widget_month_dot_row_1, if (visibleCount > 0) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_month_dot_row_2, if (visibleCount > 4) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_month_overflow_text, if (hidden > 0) View.VISIBLE else View.GONE)
        setTextViewText(R.id.widget_month_overflow_text, if (hidden > 0) "+$hidden" else "")
        setTextColor(R.id.widget_month_overflow_text, if (isToday) palette.onAccent else palette.muted)
    }

    private fun RemoteViews.bindTextMonthCell(
        cell: WidgetMonthCellContent,
        palette: WidgetPalette,
        bucket: WidgetSizeBucket,
        isToday: Boolean,
        clickPendingIntent: PendingIntent,
    ) {
        val textIds = listOf(
            R.id.widget_month_chip_1_text,
            R.id.widget_month_chip_2_text,
            R.id.widget_month_chip_3_text,
        ) + if (bucket.textItemCapacity >= 4) {
            listOf(R.id.widget_month_chip_4_text, R.id.widget_month_chip_5_text)
        } else emptyList()
        for (index in textIds.indices) {
            val id = textIds[index]
            val item = cell.items.firstOrNull { it.lane == index }
            val visible = item != null && index < bucket.textItemCapacity
            setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)
            if (visible && item != null) {
                val style = item.color.monthChipStyle()
                setTextViewText(id, item.title)
                setTextColor(id, style.textColor)
                setInt(id, "setBackgroundResource", style.backgroundRes(item))
                setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, bucket.chipTextSp)
                setViewPadding(
                    id,
                    context.dpToPx(if (item.fadesFromPrevious) 8 else 4),
                    0,
                    context.dpToPx(if (item.fadesToNext) 8 else 4),
                    0,
                )
                setOnClickPendingIntent(id, clickPendingIntent)
            }
        }
        val visibleCount = cell.items.count { it.lane < bucket.textItemCapacity }
        val hidden = (cell.totalItemCount - visibleCount).coerceAtLeast(0)
        setViewVisibility(R.id.widget_month_overflow_text, if (hidden > 0) View.VISIBLE else View.GONE)
        setTextViewText(R.id.widget_month_overflow_text, if (hidden > 0) "+$hidden" else "")
        setTextColor(R.id.widget_month_overflow_text, if (isToday) palette.onAccent else palette.muted)
    }

    private suspend fun renderCollectionWidget(
        kind: KgsWidgetKind,
        appWidgetId: Int,
        options: Bundle,
    ): RemoteViews {
        val today = LocalDate.now(zoneId)
        val settings = dataSource.loadSettings(kind)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val size = WidgetSize.from(context, options, kind)
        val views = RemoteViews(packageName, R.layout.widget_calendar)
        views.bindBaseShell(kind, appWidgetId, settings, palette, today)
        if (kind == KgsWidgetKind.Multi) {
            val month = YearMonth.from(today)
            val page = dataSource.monthPage(month, settings)
            views.setViewVisibility(R.id.widget_month_section, View.VISIBLE)
            views.bindMonthPage(
                pageContainerId = R.id.widget_month_section,
                page = page,
                settings = settings,
                palette = palette,
                bucket = if (size.compact) WidgetSizeBucket.Mini else WidgetSizeBucket.Compact,
                appWidgetId = appWidgetId,
            )
        } else {
            views.setViewVisibility(R.id.widget_month_section, View.GONE)
        }
        views.setTextViewText(R.id.widget_empty, kind.emptyText(context))
        views.setTextColor(R.id.widget_empty, palette.muted)
        views.setRemoteAdapter(R.id.widget_list, collectionAdapterIntent(kind, appWidgetId))
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)
        views.setPendingIntentTemplate(R.id.widget_list, collectionClickPendingIntent(kind, appWidgetId))
        return views
    }

    private fun RemoteViews.bindBaseShell(
        kind: KgsWidgetKind,
        appWidgetId: Int,
        settings: WidgetRenderSettings,
        palette: WidgetPalette,
        today: LocalDate,
    ) {
        setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        setTextViewText(R.id.widget_title, kind.title(context))
        setTextColor(R.id.widget_title, palette.text)
        setTextViewText(R.id.widget_subtitle, headerSubtitle(kind, today, settings))
        setTextColor(R.id.widget_subtitle, palette.muted)
        setTextColor(R.id.widget_badge, palette.onAccent)
        setInt(R.id.widget_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(kind, appWidgetId, today))
        if (kind == KgsWidgetKind.Tasks) {
            setTextViewText(R.id.widget_badge, "+")
            setOnClickPendingIntent(R.id.widget_badge, createTaskPendingIntent(appWidgetId, today, settings.tasksWidgetCreateMode))
        } else {
            setTextViewText(R.id.widget_badge, "\u21BB")
            setOnClickPendingIntent(R.id.widget_badge, refreshPendingIntent(kind, appWidgetId))
        }
        setViewVisibility(R.id.widget_empty, View.GONE)
        setViewVisibility(R.id.widget_list, View.VISIBLE)
    }

    private fun RemoteViews.showEmpty(text: String, palette: WidgetPalette) {
        setViewVisibility(R.id.widget_empty, View.VISIBLE)
        setTextViewText(R.id.widget_empty, text)
        setTextColor(R.id.widget_empty, palette.muted)
    }

    private fun headerSubtitle(kind: KgsWidgetKind, today: LocalDate, settings: WidgetRenderSettings): String {
        val shortDate = today.format(DateTimeFormatter.ofPattern("EEE, d. MMM", settings.locale))
        return when (kind) {
            KgsWidgetKind.Month, KgsWidgetKind.Multi -> YearMonth.from(today).format(DateTimeFormatter.ofPattern("MMMM yyyy", settings.locale))
            KgsWidgetKind.Day -> shortDate
            KgsWidgetKind.Tasks -> settings.tasksWidgetDisplayMode.widgetLabel(context)
            KgsWidgetKind.Agenda -> shortDate
        }
    }

    private fun collectionAdapterIntent(kind: KgsWidgetKind, appWidgetId: Int): Intent =
        Intent(context, KgsWidgetCollectionService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(EXTRA_WIDGET_KIND, kind.name)
            data = Uri.parse("kgs-calendar://widget-collection/${kind.name}/$appWidgetId")
        }

    private fun collectionClickPendingIntent(kind: KgsWidgetKind, appWidgetId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            50_000 + appWidgetId * 10 + kind.ordinal,
            Intent(context, KgsWidgetActionReceiver::class.java).apply {
                action = KgsWidgetProvider.ACTION_COLLECTION_CLICK
                data = Uri.parse("kgs-calendar://widget-collection-click/${kind.name}/$appWidgetId")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    private fun openAppPendingIntent(kind: KgsWidgetKind, requestCode: Int, date: LocalDate): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WIDGET_KIND, kind.name)
            putExtra(EXTRA_WIDGET_DATE, date.toString())
            data = Uri.parse("kgs-calendar://widget/${kind.name}/$requestCode/$date")
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createEventPendingIntent(appWidgetId: Int, date: LocalDate): PendingIntent {
        val requestCode = 30_000 + appWidgetId
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WIDGET_KIND, KgsWidgetKind.Day.name)
            putExtra(EXTRA_WIDGET_DATE, date.toString())
            putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_CREATE_EVENT)
            data = Uri.parse("kgs-calendar://widget-create-event/$appWidgetId/$date")
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createTaskPendingIntent(appWidgetId: Int, date: LocalDate, mode: WidgetTaskCreateMode): PendingIntent {
        val requestCode = 35_000 + appWidgetId
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WIDGET_KIND, KgsWidgetKind.Tasks.name)
            putExtra(EXTRA_WIDGET_DATE, date.toString())
            putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_CREATE_TASK)
            putExtra(EXTRA_WIDGET_TASK_CREATE_MODE, mode.name)
            data = Uri.parse("kgs-calendar://widget-create-task/$appWidgetId/$date/${mode.name}")
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun monthNavigationPendingIntent(appWidgetId: Int, previous: Boolean): PendingIntent {
        val intent = Intent(context, KgsWidgetKind.Month.providerClass).apply {
            action = if (previous) KgsWidgetProvider.ACTION_MONTH_PREVIOUS else KgsWidgetProvider.ACTION_MONTH_NEXT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-month/${if (previous) "previous" else "next"}/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            20_000 + appWidgetId * 2 + if (previous) 0 else 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun refreshPendingIntent(kind: KgsWidgetKind, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, kind.providerClass).apply {
            action = KgsWidgetProvider.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-refresh/${kind.name}/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            10_000 + appWidgetId + kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class KgsWidgetCollectionService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val kind = intent.getStringExtra(EXTRA_WIDGET_KIND)
            ?.let { runCatching { KgsWidgetKind.valueOf(it) }.getOrNull() }
            ?: KgsWidgetKind.Agenda
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return KgsWidgetCollectionFactory(applicationContext, kind, appWidgetId)
    }
}

private class KgsWidgetCollectionFactory(
    private val context: Context,
    private val kind: KgsWidgetKind,
    private val appWidgetId: Int,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : RemoteViewsService.RemoteViewsFactory {
    private val packageName = context.packageName
    private var rows: List<WidgetListRow> = emptyList()
    private var settings = WidgetRenderSettings()
    private var palette = WidgetPalette.from(context, AppThemeMode.KgsBlue, AppColorMode.Auto)

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val startedAt = SystemClock.elapsedRealtime()
        runCatching {
            runBlocking {
                val dataSource = KgsWidgetDataSource(context, zoneId)
                settings = dataSource.loadSettings(kind)
                palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
                rows = dataSource.listRows(kind, settings)
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to load ${kind.name} widget rows", error)
            rows = emptyList()
        }
        if (context.isDebuggable) {
            Log.d(TAG, "Loaded ${rows.size} ${kind.name} widget rows in ${SystemClock.elapsedRealtime() - startedAt}ms")
        }
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews? =
        rows.getOrNull(position)?.toRemoteViews(context, packageName, palette, kind, appWidgetId)

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 3

    override fun getItemId(position: Int): Long = rows.getOrNull(position)?.stableId ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

private class KgsWidgetDataSource(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun loadSettings(kind: KgsWidgetKind): WidgetRenderSettings {
        val graph = KgsCalendarApplication.graph(context)
        val appThemeMode = graph.settingsStore.themeMode.first()
        val appColorMode = graph.settingsStore.colorMode.first()
        val monthWidgetThemeMode = graph.settingsStore.monthWidgetThemeMode.first()
        val monthWidgetColorMode = graph.settingsStore.monthWidgetColorMode.first()
        return WidgetRenderSettings(
            locale = graph.settingsStore.languageMode.first().toLocale(),
            firstDayOfWeek = graph.settingsStore.firstDayOfWeek.first(),
            hiddenCollectionHrefs = graph.settingsStore.hiddenCollectionHrefs.first(),
            showCompletedTasks = graph.settingsStore.showCompletedTasksInCalendar.first(),
            themeMode = if (kind == KgsWidgetKind.Month) monthWidgetThemeMode.resolve(appThemeMode) else appThemeMode,
            colorMode = if (kind == KgsWidgetKind.Month) monthWidgetColorMode.resolve(appColorMode) else appColorMode,
            taskColorMode = graph.settingsStore.taskColorMode.first(),
            tasksWidgetDisplayMode = graph.settingsStore.tasksWidgetDisplayMode.first(),
            tasksWidgetIncludeOverdue = graph.settingsStore.tasksWidgetIncludeOverdue.first(),
            tasksWidgetSortMode = graph.settingsStore.tasksWidgetSortMode.first(),
            tasksWidgetCreateMode = graph.settingsStore.tasksWidgetCreateMode.first(),
        )
    }

    suspend fun monthPage(month: YearMonth, settings: WidgetRenderSettings): WidgetMonthPage {
        val start = WidgetMonthModel.gridStart(month, settings.firstDayOfWeek)
        val rowCount = WidgetMonthModel.rowCount(month, settings.firstDayOfWeek)
        val endExclusive = start.plusDays((rowCount * 7).toLong())
        val monthLayout = monthLayout(month, start, rowCount, endExclusive, settings)
        return WidgetMonthModel.page(
            month = month,
            start = start,
            rowCount = rowCount,
            monthLayout = monthLayout,
        )
    }

    suspend fun listRows(kind: KgsWidgetKind, settings: WidgetRenderSettings): List<WidgetListRow> = when (kind) {
        KgsWidgetKind.Agenda -> loadAgendaItems(days = 45, settings = settings)
            .filter { it.sortMillis >= todayStartMillis() }
            .sortedWith(compareBy<WidgetListRow> { it.sortMillis }.thenBy { it.title.lowercase(settings.locale) })
            .take(100)

        KgsWidgetKind.Day -> loadDayItems(LocalDate.now(zoneId), settings).take(80)

        KgsWidgetKind.Multi -> buildList {
            add(WidgetListRow.section(context.getString(R.string.agenda)))
            addAll(
                loadAgendaItems(days = 30, settings = settings)
                    .filter { it.sortMillis >= todayStartMillis() }
                    .sortedWith(compareBy<WidgetListRow> { it.sortMillis }.thenBy { it.title.lowercase(settings.locale) })
                    .take(80),
            )
        }

        KgsWidgetKind.Tasks -> loadTaskItems(settings).take(150)
        KgsWidgetKind.Month -> emptyList()
    }

    private suspend fun loadAgendaItems(days: Long, settings: WidgetRenderSettings): List<WidgetListRow> {
        val today = LocalDate.now(zoneId)
        val start = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = today.plusDays(days).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return loadCalendarItems(start, end, settings, KgsWidgetKind.Agenda)
    }

    private suspend fun loadDayItems(day: LocalDate, settings: WidgetRenderSettings): List<WidgetListRow> {
        val start = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return loadCalendarItems(start, end, settings, KgsWidgetKind.Day)
            .sortedWith(compareBy<WidgetListRow> { it.allDaySort }.thenBy { it.sortMillis }.thenBy { it.title.lowercase(settings.locale) })
    }

    private suspend fun loadCalendarItems(
        startMillis: Long,
        endMillis: Long,
        settings: WidgetRenderSettings,
        launchKind: KgsWidgetKind,
    ): List<WidgetListRow> {
        val graph = KgsCalendarApplication.graph(context)
        val events = graph.repository.eventsSnapshot(startMillis, endMillis)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
            .map { event -> event.toListRow(settings.locale, launchKind) }
        val tasks = graph.repository.datedTasksSnapshot(startMillis, endMillis)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
            .filter { settings.showCompletedTasks || !it.isCompleted }
            .map { task -> task.toListRow(settings.locale, settings.taskColorMode, launchKind) }
        return events + tasks
    }

    private suspend fun loadTaskItems(settings: WidgetRenderSettings): List<WidgetListRow> {
        val graph = KgsCalendarApplication.graph(context)
        val today = LocalDate.now(zoneId)
        val allOpenTasks = graph.repository.allTasksSnapshot()
            .distinctBy { it.resourceHref }
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
            .filterNot { it.isCompleted }
        val selectedTasks = when (settings.tasksWidgetDisplayMode) {
            WidgetTaskDisplayMode.Planned -> allOpenTasks.filter { it.widgetTaskDate(zoneId) != null }
            WidgetTaskDisplayMode.Unplanned -> allOpenTasks.filter { it.widgetTaskDate(zoneId) == null }
            WidgetTaskDisplayMode.Today -> allOpenTasks.filter { task ->
                val date = task.widgetTaskDate(zoneId) ?: return@filter false
                date == today || (settings.tasksWidgetIncludeOverdue && date.isBefore(today))
            }
        }
        val visibleTasks = includeDescendantTasks(selectedTasks, allOpenTasks)
        return visibleTasks.toTaskHierarchy(settings)
    }

    private fun includeDescendantTasks(selectedTasks: List<TaskEntity>, allOpenTasks: List<TaskEntity>): List<TaskEntity> {
        val selectedByResource = selectedTasks.associateBy { it.resourceHref }
        if (selectedByResource.isEmpty()) return emptyList()
        val included = selectedByResource.toMutableMap()
        val childrenByParent = allOpenTasks
            .filter { !it.parentUid.isNullOrBlank() }
            .groupBy { it.collectionHref to it.parentUid.orEmpty() }
        val queue = ArrayDeque(selectedTasks)
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            childrenByParent[parent.collectionHref to parent.uid].orEmpty().forEach { child ->
                if (included.putIfAbsent(child.resourceHref, child) == null) {
                    queue.add(child)
                }
            }
        }
        return included.values.toList()
    }

    private fun List<TaskEntity>.toTaskHierarchy(settings: WidgetRenderSettings): List<WidgetListRow> {
        if (isEmpty()) return emptyList()
        val byResource = associateBy { it.resourceHref }
        val comparator = settings.taskComparator()
        val childrenByParent = filter { !it.parentUid.isNullOrBlank() }
            .groupBy { it.collectionHref to it.parentUid.orEmpty() }
        val roots = filter { task ->
            task.parentUid.isNullOrBlank() || byResource.values.none { it.collectionHref == task.collectionHref && it.uid == task.parentUid }
        }.sortedWith(comparator)
        return buildList {
            fun append(task: TaskEntity, depth: Int) {
                val children = childrenByParent[task.collectionHref to task.uid].orEmpty().sortedWith(comparator)
                add(task.toTaskRow(settings.locale, settings.taskColorMode, depth, children.size))
                children.forEach { append(it, (depth + 1).coerceAtMost(5)) }
            }
            roots.forEach { append(it, 0) }
        }
    }

    private suspend fun monthLayout(
        month: YearMonth,
        start: LocalDate,
        rowCount: Int,
        endExclusive: LocalDate,
        settings: WidgetRenderSettings,
    ): WidgetMonthLayout {
        val startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = endExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val graph = KgsCalendarApplication.graph(context)
        val candidates = mutableListOf<WidgetMonthCandidate>()
        graph.repository.eventsSnapshot(startMillis, endMillis)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
            .forEach { event ->
                val itemStart = event.startsAtMillis.toDate(zoneId)
                val itemEnd = event.endDateInclusive(zoneId).coerceAtLeast(itemStart)
                if (itemEnd < start || !itemStart.isBefore(endExclusive)) return@forEach
                candidates += WidgetMonthCandidate(
                    id = "event:${event.monthOccurrenceKey()}",
                    title = event.title.ifBlank { context.getString(R.string.no_title) },
                    color = event.displayColor(),
                    sortMillis = event.startsAtMillis,
                    start = itemStart,
                    end = itemEnd,
                    completed = false,
                )
            }
        graph.repository.datedTasksSnapshot(startMillis, endMillis)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
            .filter { settings.showCompletedTasks || !it.isCompleted }
            .forEach { task ->
                val itemStart = task.startAtMillis?.toDate(zoneId) ?: task.dueAtMillis?.toDate(zoneId) ?: return@forEach
                val itemEnd = (task.dueAtMillis?.toDate(zoneId) ?: task.startAtMillis?.toDate(zoneId) ?: itemStart).coerceAtLeast(itemStart)
                if (itemEnd < start || !itemStart.isBefore(endExclusive)) return@forEach
                candidates += WidgetMonthCandidate(
                    id = "task:${task.resourceHref.ifBlank { task.uid }}",
                    title = task.title.ifBlank { context.getString(R.string.no_title) },
                    color = task.displayColor(settings.taskColorMode),
                    sortMillis = task.startAtMillis ?: task.dueAtMillis ?: Long.MAX_VALUE,
                    start = itemStart,
                    end = itemEnd,
                    completed = task.isCompleted,
                )
            }
        return WidgetMonthModel.layout(month, start, rowCount, candidates, settings.locale)
    }

    private fun todayStartMillis(): Long =
        LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()

    private fun EventEntity.toListRow(locale: Locale, launchKind: KgsWidgetKind): WidgetListRow {
        val start = Instant.ofEpochMilli(startsAtMillis).atZone(zoneId)
        val end = Instant.ofEpochMilli(endsAtMillis).atZone(zoneId)
        val titleText = title.ifBlank { context.getString(R.string.no_title) }
        val metaText = if (allDay) {
            "${start.toLocalDate().relativeDateLabel(locale)} - ${context.getString(R.string.all_day)}"
        } else {
            "${start.toLocalDate().relativeDateLabel(locale)} - ${start.toLocalTime().timeText()}${if (start.toLocalDate() == end.toLocalDate()) " - ${end.toLocalTime().timeText()}" else ""}"
        }
        return WidgetListRow.item(
            title = titleText,
            meta = metaText,
            color = displayColor(),
            sortMillis = startsAtMillis,
            date = start.toLocalDate(),
            completed = false,
            allDaySort = if (allDay) 0 else 1,
            launchKind = launchKind,
            stableKey = "event:$resourceHref",
        )
    }

    private fun TaskEntity.toListRow(locale: Locale, taskColorMode: TaskColorMode, launchKind: KgsWidgetKind): WidgetListRow {
        val millis = startAtMillis ?: dueAtMillis
        val date = millis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
        val time = millis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime() }
        val hasTime = if (startAtMillis != null) startHasTime else dueHasTime
        val dateText = date?.relativeDateLabel(locale) ?: context.getString(R.string.no_date)
        val timeText = if (time != null && hasTime) " - ${time.timeText()}" else ""
        return WidgetListRow.item(
            title = title.ifBlank { context.getString(R.string.no_title) },
            meta = "${context.getString(R.string.task)} - $dateText$timeText",
            color = displayColor(taskColorMode),
            sortMillis = millis ?: Long.MAX_VALUE,
            date = date ?: LocalDate.now(zoneId),
            completed = isCompleted,
            allDaySort = if (hasTime) 1 else 0,
            launchKind = launchKind,
            stableKey = "task:$resourceHref",
            taskResourceHref = resourceHref,
        )
    }

    private fun TaskEntity.toTaskRow(
        locale: Locale,
        taskColorMode: TaskColorMode,
        depth: Int,
        childCount: Int,
    ): WidgetListRow {
        val millis = startAtMillis ?: dueAtMillis
        val date = widgetTaskDate(zoneId) ?: LocalDate.now(zoneId)
        val time = millis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime() }
        val hasTime = if (startAtMillis != null) startHasTime else dueHasTime
        val dateText = if (millis == null) context.getString(R.string.no_date) else date.relativeDateLabel(locale)
        val timeText = if (time != null && hasTime) " - ${time.timeText()}" else ""
        val priorityText = priority?.let { "P$it" }
        val statusText = statusText()
        val metaText = listOfNotNull(
            "$dateText$timeText",
            priorityText,
            statusText.takeUnless { it == context.getString(R.string.status_open) },
        ).joinToString(" - ")
        return WidgetListRow.task(
            title = title.ifBlank { context.getString(R.string.no_title) },
            meta = metaText,
            color = displayColor(taskColorMode),
            sortMillis = millis ?: Long.MAX_VALUE,
            date = date,
            completed = isCompleted,
            taskResourceHref = resourceHref,
            statusGlyph = when (effectiveStatus()) {
                "COMPLETED" -> "\u2713"
                "IN-PROCESS" -> "\u25D0"
                "CANCELLED" -> "\u00D7"
                else -> "\u25CB"
            },
            depth = depth,
            childCount = childCount,
        )
    }

    private fun TaskEntity.statusText(): String = when (effectiveStatus()) {
        "IN-PROCESS" -> context.getString(R.string.in_progress)
        "COMPLETED" -> context.getString(R.string.status_completed)
        "CANCELLED" -> context.getString(R.string.aborted)
        else -> context.getString(R.string.status_open)
    }

    private fun LocalDate.relativeDateLabel(locale: Locale): String {
        val today = LocalDate.now(zoneId)
        return when (this) {
            today -> context.getString(R.string.today)
            else -> format(DateTimeFormatter.ofPattern("EEE, d. MMM", locale))
        }
    }

    private fun LocalTime.timeText(): String =
        format(DateTimeFormatter.ofPattern("HH:mm"))
}

internal object WidgetMonthModel {
    fun gridStart(month: YearMonth, firstDayOfWeek: DayOfWeek): LocalDate {
        val first = month.atDay(1)
        val offset = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        return first.minusDays(offset.toLong())
    }

    fun rowCount(month: YearMonth, firstDayOfWeek: DayOfWeek): Int {
        val leadingDays = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        return maxOf(5, (leadingDays + month.lengthOfMonth() + 6) / 7)
    }

    fun page(
        month: YearMonth,
        start: LocalDate,
        rowCount: Int,
        monthLayout: WidgetMonthLayout,
    ): WidgetMonthPage {
        val cells = (0 until 42).map { offset ->
            val date = start.plusDays(offset.toLong())
            WidgetMonthCellContent(
                date = date,
                inCurrentMonth = YearMonth.from(date) == month,
                items = monthLayout.itemsByDay[date].orEmpty(),
                totalItemCount = monthLayout.totalByDay[date] ?: 0,
            )
        }
        return WidgetMonthPage(month, rowCount, cells)
    }

    fun layout(
        month: YearMonth,
        gridStart: LocalDate,
        rowCount: Int,
        candidates: List<WidgetMonthCandidate>,
        locale: Locale,
    ): WidgetMonthLayout {
        val itemsByDay = mutableMapOf<LocalDate, MutableList<WidgetMonthItem>>()
        val totalByDay = mutableMapOf<LocalDate, Int>()

        repeat(rowCount) { row ->
            val rowStartDate = gridStart.plusDays((row * 7).toLong())
            val rowEndDate = rowStartDate.plusDays(6)
            val rowDays = (0 until 7)
                .map { rowStartDate.plusDays(it.toLong()) }
                .filter { YearMonth.from(it) == month }
            if (rowDays.isEmpty()) return@repeat
            val rowStart = rowDays.first()
            val rowEnd = rowDays.last()
            val rowCandidates = candidates
                .filter { it.end >= rowStart && it.start <= rowEnd }
                .distinctBy { it.id }
            rowDays.forEach { day ->
                totalByDay[day] = rowCandidates.count { day in it.start..it.end }
            }

            data class MutableSegment(
                val candidate: WidgetMonthCandidate,
                val visualStart: LocalDate,
                val visualEnd: LocalDate,
                val lane: Int,
            )

            val laneSegments = mutableListOf<MutableList<MutableSegment>>()
            val segments = mutableListOf<MutableSegment>()
            val sortedCandidates = rowCandidates.sortedWith(
                compareBy<WidgetMonthCandidate> { maxOf(it.start, rowStart) }
                    .thenByDescending { it.spanDays }
                    .thenBy { it.title.lowercase(locale) },
            )

            sortedCandidates.forEach { candidate ->
                fun doesNotOverlap(existing: MutableSegment): Boolean =
                    existing.candidate.end < candidate.start || candidate.end < existing.candidate.start

                val existingLane = laneSegments.indexOfFirst { lane -> lane.all(::doesNotOverlap) }
                val targetLane = if (existingLane >= 0) existingLane else laneSegments.size
                val segment = MutableSegment(
                    candidate = candidate,
                    visualStart = maxOf(candidate.start, rowStart),
                    visualEnd = minOf(candidate.end, rowEnd),
                    lane = targetLane,
                )
                if (targetLane < laneSegments.size) {
                    laneSegments[targetLane] += segment
                } else {
                    laneSegments += mutableListOf(segment)
                }
                segments += segment
            }

            fun WidgetMonthCandidate.visuallyConnectsTo(other: WidgetMonthCandidate): Boolean =
                color == other.color && title.trim().equals(other.title.trim(), ignoreCase = true)

            segments
                .filter { it.lane < WIDGET_MONTH_MAX_LANES && !it.visualEnd.isBefore(it.visualStart) }
                .forEach { segment ->
                    var day = segment.visualStart
                    while (!day.isAfter(segment.visualEnd)) {
                        val previousSameLaneSegment = segments.firstOrNull { other ->
                            other !== segment &&
                                other.lane == segment.lane &&
                                other.visualEnd == day.minusDays(1) &&
                                other.candidate.visuallyConnectsTo(segment.candidate)
                        }
                        val nextSameLaneSegment = segments.firstOrNull { other ->
                            other !== segment &&
                                other.lane == segment.lane &&
                                other.visualStart == day.plusDays(1) &&
                                segment.candidate.visuallyConnectsTo(other.candidate)
                        }
                        val continuesFromPrevious = segment.candidate.start < day || previousSameLaneSegment != null
                        val continuesToNext = segment.candidate.end > day || nextSameLaneSegment != null
                        itemsByDay.getOrPut(day) { mutableListOf() } += WidgetMonthItem(
                            id = segment.candidate.id,
                            title = if (day == segment.visualStart && previousSameLaneSegment == null) segment.candidate.title else "",
                            color = segment.candidate.color,
                            sortMillis = segment.candidate.sortMillis,
                            lane = segment.lane,
                            continuesFromPrevious = continuesFromPrevious,
                            continuesToNext = continuesToNext,
                            fadesFromPrevious = day == segment.visualStart && segment.candidate.start < segment.visualStart,
                            fadesToNext = day == segment.visualEnd && segment.candidate.end > segment.visualEnd,
                            completed = segment.candidate.completed,
                        )
                        day = day.plusDays(1)
                    }
                }
        }

        return WidgetMonthLayout(
            itemsByDay = itemsByDay.mapValues { (_, items) ->
                items.sortedWith(compareBy<WidgetMonthItem> { it.lane }.thenBy { it.sortMillis }.thenBy { it.title.lowercase(locale) })
            },
            totalByDay = totalByDay,
        )
    }
}

internal enum class WidgetSizeBucket(
    val remoteSize: SizeF,
    val layoutRes: Int,
    val textItemCapacity: Int,
    val miniDotCapacity: Int,
    val rootPaddingDp: Int,
    val titleTextSp: Float,
    val weekdayTextSp: Float,
    val dayTextSp: Float,
    val chipTextSp: Float,
) {
    Tiny(SizeF(170f, 220f), R.layout.widget_month_cell_mini, 0, 3, 5, 13f, 7.5f, 9f, 0f),
    Mini(SizeF(220f, 285f), R.layout.widget_month_cell_mini, 0, 6, 6, 14f, 8f, 10f, 0f),
    Compact(SizeF(260f, 350f), R.layout.widget_month_cell_compact_native, 1, 0, 6, 15f, 8.5f, 10f, 8f),
    Standard(SizeF(300f, 390f), R.layout.widget_month_cell_standard, 2, 0, 6, 15f, 8.5f, 10f, 8f),
    Comfortable(SizeF(320f, 470f), R.layout.widget_month_cell_standard, 3, 0, 6, 15f, 8.5f, 10f, 8f),
    Expanded(SizeF(360f, 560f), R.layout.widget_month_cell_expanded, 4, 0, 6, 15f, 8.5f, 10f, 8f),
    Max(SizeF(360f, 680f), R.layout.widget_month_cell_expanded, 5, 0, 6, 15f, 8.5f, 10f, 8f);

    val usesDotCells: Boolean
        get() = textItemCapacity == 0

    fun visibleItemCount(totalCount: Int): Int =
        minOf(totalCount.coerceAtLeast(0), if (usesDotCells) miniDotCapacity else textItemCapacity)

    fun overflowCount(totalCount: Int): Int =
        (totalCount.coerceAtLeast(0) - visibleItemCount(totalCount)).coerceAtLeast(0)

    companion object {
        fun from(size: WidgetSize): WidgetSizeBucket = from(size, rowCount = 5)

        fun from(size: WidgetSize, rowCount: Int): WidgetSizeBucket {
            val rows = rowCount.coerceIn(5, 6)
            val availableHeight = (size.heightDp - MONTH_VERTICAL_CHROME_DP).coerceAtLeast(0)
            val weekCellHeight = availableHeight / rows
            val dayWidth = size.widthDp / 7
            return when {
                dayWidth < 30 || weekCellHeight < 29 -> Tiny
                dayWidth < 35 || weekCellHeight < 42 -> Mini
                weekCellHeight < 58 -> Compact
                weekCellHeight < 74 -> Standard
                weekCellHeight < 92 -> Comfortable
                weekCellHeight < 118 -> Expanded
                else -> Max
            }
        }

        private const val MONTH_VERTICAL_CHROME_DP = 67
    }
}

internal data class WidgetMonthPage(
    val month: YearMonth,
    val rowCount: Int,
    val cells: List<WidgetMonthCellContent>,
) {
    fun title(locale: Locale): String =
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
}

internal data class WidgetMonthCellContent(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val items: List<WidgetMonthItem>,
    val totalItemCount: Int,
)

internal data class WidgetMonthLayout(
    val itemsByDay: Map<LocalDate, List<WidgetMonthItem>>,
    val totalByDay: Map<LocalDate, Int>,
)

internal data class WidgetMonthCandidate(
    val id: String,
    val title: String,
    val color: Int,
    val sortMillis: Long,
    val start: LocalDate,
    val end: LocalDate,
    val completed: Boolean,
) {
    val spanDays: Long = ChronoUnit.DAYS.between(start, end).coerceAtLeast(0) + 1
}

internal data class WidgetMonthItem(
    val id: String,
    val title: String,
    val color: Int,
    val sortMillis: Long,
    val lane: Int,
    val continuesFromPrevious: Boolean,
    val continuesToNext: Boolean,
    val fadesFromPrevious: Boolean,
    val fadesToNext: Boolean,
    val completed: Boolean,
)

private data class WidgetMonthWeekSegment(
    val startColumn: Int,
    val endColumn: Int,
    val title: String,
    val item: WidgetMonthItem,
) {
    val columnSpan: Int = endColumn - startColumn + 1
}

private fun List<WidgetMonthCellContent>.monthWeekSegments(lane: Int): List<WidgetMonthWeekSegment> {
    val segments = mutableListOf<WidgetMonthWeekSegment>()
    var column = 0
    while (column < size) {
        if (!this[column].inCurrentMonth) {
            column += 1
            continue
        }
        val firstItem = this[column].items.firstOrNull { it.lane == lane } ?: run {
            column += 1
            continue
        }

        var endColumn = column
        var last = firstItem
        while (endColumn + 1 < size) {
            val next = this[endColumn + 1].items.firstOrNull { it.lane == lane }
            if (next == null || !this[endColumn + 1].inCurrentMonth || !last.visuallyContinuesInto(next)) {
                break
            }
            endColumn += 1
            last = next
        }

        val title = (column..endColumn)
            .asSequence()
            .mapNotNull { index -> this[index].items.firstOrNull { it.lane == lane }?.title?.takeIf(String::isNotBlank) }
            .firstOrNull()
            .orEmpty()
        segments += WidgetMonthWeekSegment(
            startColumn = column,
            endColumn = endColumn,
            title = title,
            item = firstItem.copy(
                title = title,
                continuesToNext = last.continuesToNext,
                fadesToNext = last.fadesToNext,
            ),
        )
        column = endColumn + 1
    }
    return segments
}

private fun WidgetMonthItem.visuallyContinuesInto(next: WidgetMonthItem): Boolean =
    id == next.id &&
        lane == next.lane &&
        color == next.color &&
        continuesToNext &&
        next.continuesFromPrevious

private fun monthSpanChipLayout(span: Int): Int = when (span.coerceIn(1, 7)) {
    1 -> R.layout.widget_month_span_chip_1
    2 -> R.layout.widget_month_span_chip_2
    3 -> R.layout.widget_month_span_chip_3
    4 -> R.layout.widget_month_span_chip_4
    5 -> R.layout.widget_month_span_chip_5
    6 -> R.layout.widget_month_span_chip_6
    else -> R.layout.widget_month_span_chip_7
}

private data class WidgetRenderSettings(
    val locale: Locale = Locale.getDefault(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val hiddenCollectionHrefs: Set<String> = emptySet(),
    val showCompletedTasks: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.KgsBlue,
    val colorMode: AppColorMode = AppColorMode.Auto,
    val taskColorMode: TaskColorMode = TaskColorMode.Collection,
    val tasksWidgetDisplayMode: WidgetTaskDisplayMode = WidgetTaskDisplayMode.Planned,
    val tasksWidgetIncludeOverdue: Boolean = true,
    val tasksWidgetSortMode: WidgetTaskSortMode = WidgetTaskSortMode.Date,
    val tasksWidgetCreateMode: WidgetTaskCreateMode = WidgetTaskCreateMode.Today,
) {
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

private data class WidgetListRow(
    val type: WidgetListRowType,
    val title: String,
    val meta: String,
    val color: Int,
    val sortMillis: Long,
    val date: LocalDate,
    val completed: Boolean,
    val allDaySort: Int,
    val launchKind: KgsWidgetKind,
    val stableId: Long,
    val taskResourceHref: String?,
    val statusGlyph: String,
    val depth: Int,
    val childCount: Int,
) {
    fun toRemoteViews(context: Context, packageName: String, palette: WidgetPalette, sourceKind: KgsWidgetKind, appWidgetId: Int): RemoteViews {
        if (type == WidgetListRowType.Section) {
            val views = RemoteViews(packageName, R.layout.widget_section_title)
            views.setTextViewText(R.id.widget_section_title_text, title)
            views.setTextColor(R.id.widget_section_title_text, palette.muted)
            return views
        }
        if (type == WidgetListRowType.Item) {
            val views = RemoteViews(packageName, R.layout.widget_list_item)
            views.setInt(R.id.widget_item_root, "setBackgroundResource", palette.itemBackgroundRes)
            views.setInt(R.id.widget_item_accent, "setBackgroundColor", color)
            views.setTextViewText(R.id.widget_item_title, title)
            views.setTextColor(R.id.widget_item_title, if (completed) palette.muted else palette.text)
            views.setTextViewText(R.id.widget_item_meta, meta)
            views.setTextColor(R.id.widget_item_meta, palette.muted)
            views.setOnClickFillInIntent(R.id.widget_item_root, openFillInIntent())
            return views
        }

        val views = RemoteViews(packageName, R.layout.widget_task_item)
        val indent = context.dpToPx(8 + depth * 10)
        views.setInt(R.id.widget_task_root, "setBackgroundResource", palette.itemBackgroundRes)
        views.setViewPadding(R.id.widget_task_root, indent, 0, context.dpToPx(8), 0)
        views.setInt(R.id.widget_task_accent, "setBackgroundColor", color)
        views.setTextViewText(R.id.widget_task_status, statusGlyph)
        views.setTextColor(R.id.widget_task_status, if (completed) palette.accent else palette.muted)
        views.setTextViewText(R.id.widget_task_title, title)
        views.setTextColor(R.id.widget_task_title, if (completed) palette.muted else palette.text)
        views.setTextViewText(R.id.widget_task_meta, meta)
        views.setTextColor(R.id.widget_task_meta, palette.muted)
        views.setTextViewText(R.id.widget_task_subtasks, if (childCount > 0) childCount.toString() else "")
        views.setTextColor(R.id.widget_task_subtasks, palette.accent)
        views.setViewVisibility(R.id.widget_task_subtasks, if (childCount > 0) View.VISIBLE else View.GONE)
        views.setOnClickFillInIntent(R.id.widget_task_root, openFillInIntent(openTask = true))
        views.setOnClickFillInIntent(R.id.widget_task_title, openFillInIntent(openTask = true))
        views.setOnClickFillInIntent(R.id.widget_task_status, toggleFillInIntent())
        return views
    }

    private fun openFillInIntent(openTask: Boolean = taskResourceHref != null): Intent {
        val intent = Intent()
        intent.putExtra(EXTRA_COLLECTION_ACTION, COLLECTION_ACTION_OPEN)
        intent.putExtra(EXTRA_WIDGET_KIND, if (openTask) KgsWidgetKind.Tasks.name else launchKind.name)
        intent.putExtra(EXTRA_WIDGET_DATE, date.toString())
        if (openTask && !taskResourceHref.isNullOrBlank()) {
            intent.putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_OPEN_TASK)
            intent.putExtra(EXTRA_WIDGET_TASK_UID, taskResourceHref)
        }
        intent.data = Uri.parse("kgs-calendar://widget-row-open/$stableId")
        return intent
    }

    private fun toggleFillInIntent(): Intent {
        val intent = Intent()
        intent.putExtra(EXTRA_COLLECTION_ACTION, COLLECTION_ACTION_TOGGLE_TASK)
        intent.putExtra(KgsWidgetProvider.EXTRA_TASK_RESOURCE_HREF, taskResourceHref)
        intent.data = Uri.parse("kgs-calendar://widget-row-toggle/$stableId")
        return intent
    }

    companion object {
        fun section(title: String): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Section,
            title = title,
            meta = "",
            color = 0,
            sortMillis = Long.MIN_VALUE,
            date = LocalDate.now(),
            completed = false,
            allDaySort = 0,
            launchKind = KgsWidgetKind.Agenda,
            stableId = stableId("section:$title"),
            taskResourceHref = null,
            statusGlyph = "",
            depth = 0,
            childCount = 0,
        )

        fun item(
            title: String,
            meta: String,
            color: Int,
            sortMillis: Long,
            date: LocalDate,
            completed: Boolean,
            allDaySort: Int,
            launchKind: KgsWidgetKind,
            stableKey: String,
            taskResourceHref: String? = null,
        ): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Item,
            title = title,
            meta = meta,
            color = color,
            sortMillis = sortMillis,
            date = date,
            completed = completed,
            allDaySort = allDaySort,
            launchKind = launchKind,
            stableId = stableId(stableKey),
            taskResourceHref = taskResourceHref,
            statusGlyph = "",
            depth = 0,
            childCount = 0,
        )

        fun task(
            title: String,
            meta: String,
            color: Int,
            sortMillis: Long,
            date: LocalDate,
            completed: Boolean,
            taskResourceHref: String,
            statusGlyph: String,
            depth: Int,
            childCount: Int,
        ): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Task,
            title = title,
            meta = meta,
            color = color,
            sortMillis = sortMillis,
            date = date,
            completed = completed,
            allDaySort = 1,
            launchKind = KgsWidgetKind.Tasks,
            stableId = stableId("task-row:$taskResourceHref"),
            taskResourceHref = taskResourceHref,
            statusGlyph = statusGlyph,
            depth = depth,
            childCount = childCount,
        )

        private fun stableId(value: String): Long =
            value.fold(1125899906842597L) { acc, char -> acc * 31 + char.code }
    }
}

private enum class WidgetListRowType {
    Section,
    Item,
    Task,
}

internal data class WidgetSize(
    val widthDp: Int,
    val heightDp: Int,
) {
    val compact: Boolean = widthDp < 280 || heightDp < 300

    companion object {
        fun from(context: Context, options: Bundle, kind: KgsWidgetKind): WidgetSize {
            val fallback = when (kind) {
                KgsWidgetKind.Month -> 320 to 320
                KgsWidgetKind.Multi -> 320 to 460
                else -> 320 to 180
            }
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val currentWidth = if (portrait) {
                minWidth.takeIf { it > 0 } ?: maxWidth
            } else {
                maxWidth.takeIf { it > 0 } ?: minWidth
            }
            val currentHeight = if (portrait) {
                maxHeight.takeIf { it > 0 } ?: minHeight
            } else {
                minHeight.takeIf { it > 0 } ?: maxHeight
            }
            return WidgetSize(
                widthDp = currentWidth.takeIf { it > 0 } ?: fallback.first,
                heightDp = currentHeight.takeIf { it > 0 } ?: fallback.second,
            )
        }

    }
}

private data class WidgetPalette(
    val accent: Int,
    val text: Int,
    val muted: Int,
    val faint: Int,
    val onAccent: Int,
    val rootBackgroundRes: Int,
    val itemBackgroundRes: Int,
    val compactItemBackgroundRes: Int,
    val badgeBackgroundRes: Int,
    val daySelectedBackgroundRes: Int,
) {
    companion object {
        fun from(context: Context, themeMode: AppThemeMode, colorMode: AppColorMode): WidgetPalette {
            val dark = when (colorMode) {
                AppColorMode.Light -> false
                AppColorMode.Dark -> true
                AppColorMode.Auto -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            val accent = if (themeMode == AppThemeMode.SystemDynamic) {
                context.systemAccentColor(dark)
            } else {
                when (themeMode) {
                    AppThemeMode.KgsWarm -> if (dark) 0xFFFFB68B.toInt() else 0xFF9E572B.toInt()
                    AppThemeMode.KgsFresh -> if (dark) 0xFF76DCC4.toInt() else 0xFF0E7C66.toInt()
                    else -> if (dark) 0xFF9BCAFF.toInt() else 0xFF2563A8.toInt()
                }
            }
            return if (dark) {
                WidgetPalette(
                    accent = accent,
                    text = 0xFFE6EEF7.toInt(),
                    muted = 0xFFD6E0EC.toInt(),
                    faint = 0xFF7B8B9B.toInt(),
                    onAccent = if (accent.isDarkColor()) 0xFFFFFFFF.toInt() else 0xFF111827.toInt(),
                    rootBackgroundRes = R.drawable.widget_background_dark,
                    itemBackgroundRes = R.drawable.widget_item_background_dark,
                    compactItemBackgroundRes = R.drawable.widget_month_cell_compact_dark,
                    badgeBackgroundRes = R.drawable.widget_badge_background_dark,
                    daySelectedBackgroundRes = R.drawable.widget_month_day_selected_dark,
                )
            } else {
                val root = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_background_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_background_fresh
                    else -> R.drawable.widget_background
                }
                val badge = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_badge_background_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_badge_background_fresh
                    else -> R.drawable.widget_badge_background
                }
                val selected = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_month_day_selected_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_month_day_selected_fresh
                    else -> R.drawable.widget_month_day_selected
                }
                WidgetPalette(
                    accent = accent,
                    text = 0xFF17202A.toInt(),
                    muted = 0xFF526173.toInt(),
                    faint = 0xFFA4AFBA.toInt(),
                    onAccent = 0xFFFFFFFF.toInt(),
                    rootBackgroundRes = root,
                    itemBackgroundRes = R.drawable.widget_item_background,
                    compactItemBackgroundRes = R.drawable.widget_month_cell_compact,
                    badgeBackgroundRes = badge,
                    daySelectedBackgroundRes = selected,
                )
            }
        }
    }
}

private data class WidgetMonthChipStyle(
    val backgroundRes: Int,
    val textColor: Int,
) {
    fun backgroundRes(item: WidgetMonthItem): Int {
        val suffix = when {
            item.fadesFromPrevious && item.fadesToNext -> "middle"
            item.fadesFromPrevious -> "fade_start"
            item.fadesToNext -> "fade_end"
            item.continuesFromPrevious && item.continuesToNext -> "middle"
            item.continuesFromPrevious -> "end"
            item.continuesToNext -> "start"
            else -> "rounded"
        }
        return when (backgroundRes) {
            R.drawable.widget_month_chip_red -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_red_start
                "middle" -> R.drawable.widget_month_chip_red_middle
                "end" -> R.drawable.widget_month_chip_red_end
                "fade_start" -> R.drawable.widget_month_chip_red_fade_start
                "fade_end" -> R.drawable.widget_month_chip_red_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_orange -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_orange_start
                "middle" -> R.drawable.widget_month_chip_orange_middle
                "end" -> R.drawable.widget_month_chip_orange_end
                "fade_start" -> R.drawable.widget_month_chip_orange_fade_start
                "fade_end" -> R.drawable.widget_month_chip_orange_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_yellow -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_yellow_start
                "middle" -> R.drawable.widget_month_chip_yellow_middle
                "end" -> R.drawable.widget_month_chip_yellow_end
                "fade_start" -> R.drawable.widget_month_chip_yellow_fade_start
                "fade_end" -> R.drawable.widget_month_chip_yellow_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_green -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_green_start
                "middle" -> R.drawable.widget_month_chip_green_middle
                "end" -> R.drawable.widget_month_chip_green_end
                "fade_start" -> R.drawable.widget_month_chip_green_fade_start
                "fade_end" -> R.drawable.widget_month_chip_green_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_teal -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_teal_start
                "middle" -> R.drawable.widget_month_chip_teal_middle
                "end" -> R.drawable.widget_month_chip_teal_end
                "fade_start" -> R.drawable.widget_month_chip_teal_fade_start
                "fade_end" -> R.drawable.widget_month_chip_teal_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_blue -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_blue_start
                "middle" -> R.drawable.widget_month_chip_blue_middle
                "end" -> R.drawable.widget_month_chip_blue_end
                "fade_start" -> R.drawable.widget_month_chip_blue_fade_start
                "fade_end" -> R.drawable.widget_month_chip_blue_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_purple -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_purple_start
                "middle" -> R.drawable.widget_month_chip_purple_middle
                "end" -> R.drawable.widget_month_chip_purple_end
                "fade_start" -> R.drawable.widget_month_chip_purple_fade_start
                "fade_end" -> R.drawable.widget_month_chip_purple_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_pink -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_pink_start
                "middle" -> R.drawable.widget_month_chip_pink_middle
                "end" -> R.drawable.widget_month_chip_pink_end
                "fade_start" -> R.drawable.widget_month_chip_pink_fade_start
                "fade_end" -> R.drawable.widget_month_chip_pink_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_neutral_light -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_neutral_light_start
                "middle" -> R.drawable.widget_month_chip_neutral_light_middle
                "end" -> R.drawable.widget_month_chip_neutral_light_end
                "fade_start" -> R.drawable.widget_month_chip_neutral_light_fade_start
                "fade_end" -> R.drawable.widget_month_chip_neutral_light_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_neutral_dark -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_neutral_dark_start
                "middle" -> R.drawable.widget_month_chip_neutral_dark_middle
                "end" -> R.drawable.widget_month_chip_neutral_dark_end
                "fade_start" -> R.drawable.widget_month_chip_neutral_dark_fade_start
                "fade_end" -> R.drawable.widget_month_chip_neutral_dark_fade_end
                else -> backgroundRes
            }
            else -> backgroundRes
        }
    }
}

private val KgsWidgetKind.usesCollectionList: Boolean
    get() = this != KgsWidgetKind.Month

private fun KgsWidgetKind.emptyText(context: Context): String = when (this) {
    KgsWidgetKind.Tasks -> context.getString(R.string.no_scheduled_open_tasks)
    else -> context.getString(R.string.no_events_or_tasks)
}

private fun WidgetTaskDisplayMode.widgetLabel(context: Context): String = when (this) {
    WidgetTaskDisplayMode.Planned -> context.getString(R.string.planned_tasks)
    WidgetTaskDisplayMode.Unplanned -> context.getString(R.string.unplanned_tasks)
    WidgetTaskDisplayMode.Today -> context.getString(R.string.tasks_for_today)
}

private fun TaskEntity.widgetTaskDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? =
    (startAtMillis ?: dueAtMillis)?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }

private fun TaskEntity.effectiveStatus(): String =
    status?.uppercase(Locale.ROOT) ?: if (isCompleted) "COMPLETED" else "NEEDS-ACTION"

private fun TaskEntity.statusSortRank(): Int = when (effectiveStatus()) {
    "IN-PROCESS" -> 0
    "NEEDS-ACTION" -> 1
    "COMPLETED" -> 2
    "CANCELLED" -> 3
    else -> 4
}

private fun AppLanguageMode.toLocale(): Locale =
    localeTag?.let(Locale::forLanguageTag) ?: Locale.getDefault()

private fun WidgetThemeMode.resolve(appThemeMode: AppThemeMode): AppThemeMode = when (this) {
    WidgetThemeMode.FollowApp -> appThemeMode
    WidgetThemeMode.KgsBlue -> AppThemeMode.KgsBlue
    WidgetThemeMode.KgsWarm -> AppThemeMode.KgsWarm
    WidgetThemeMode.KgsFresh -> AppThemeMode.KgsFresh
    WidgetThemeMode.SystemDynamic -> AppThemeMode.SystemDynamic
}

private fun WidgetColorMode.resolve(appColorMode: AppColorMode): AppColorMode = when (this) {
    WidgetColorMode.FollowApp -> appColorMode
    WidgetColorMode.FollowOs -> AppColorMode.Auto
    WidgetColorMode.Light -> AppColorMode.Light
    WidgetColorMode.Dark -> AppColorMode.Dark
}

private fun EventEntity.displayColor(): Int = manualColor ?: color

private fun TaskEntity.displayColor(mode: TaskColorMode): Int =
    manualColor ?: when (mode) {
        TaskColorMode.Priority -> priority?.let(::priorityColor) ?: color
        TaskColorMode.Collection -> color
    }

private fun EventEntity.monthOccurrenceKey(): String =
    "${resourceHref.ifBlank { uid }}:$startsAtMillis"

private fun EventEntity.endDateInclusive(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli((endsAtMillis - 1).coerceAtLeast(startsAtMillis)).atZone(zoneId).toLocalDate()

private fun priorityColor(priority: Int): Int = when (priority.coerceIn(1, 9)) {
    1 -> 0xFFD93025.toInt()
    2 -> 0xFFE7602A.toInt()
    3 -> 0xFFF29900.toInt()
    4 -> 0xFFF8C542.toInt()
    5 -> 0xFFFFD84D.toInt()
    6 -> 0xFF55A8F5.toInt()
    7 -> 0xFF2E8FD8.toInt()
    8 -> 0xFF20A386.toInt()
    else -> 0xFF2E7D32.toInt()
}

private fun Int.monthChipStyle(): WidgetMonthChipStyle {
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val saturation = if (max == 0) 0f else (max - min).toFloat() / max.toFloat()
    val value = max / 255f
    if (saturation < 0.14f) {
        return if (value >= 0.58f) {
            WidgetMonthChipStyle(R.drawable.widget_month_chip_neutral_light, 0xFF1C1A18.toInt())
        } else {
            WidgetMonthChipStyle(R.drawable.widget_month_chip_neutral_dark, 0xFFFFFFFF.toInt())
        }
    }

    val hue = rgbHue(r, g, b, max, min)
    return when {
        hue < 15f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_red, 0xFFFFFFFF.toInt())
        hue < 45f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_orange, 0xFF1C1A18.toInt())
        hue < 75f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_yellow, 0xFF1C1A18.toInt())
        hue < 155f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_green, 0xFFFFFFFF.toInt())
        hue < 185f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_teal, 0xFFFFFFFF.toInt())
        hue < 235f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_blue, 0xFFFFFFFF.toInt())
        hue < 285f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_purple, 0xFFFFFFFF.toInt())
        hue < 345f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_pink, 0xFFFFFFFF.toInt())
        else -> WidgetMonthChipStyle(R.drawable.widget_month_chip_red, 0xFFFFFFFF.toInt())
    }
}

private fun rgbHue(r: Int, g: Int, b: Int, max: Int, min: Int): Float {
    if (max == min) return 0f
    val delta = (max - min).toFloat()
    val hue = when (max) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return if (hue < 0f) hue + 360f else hue
}

private fun EventEntity.visibleDates(start: LocalDate, endExclusive: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): List<LocalDate> {
    val first = startsAtMillis.toDate(zoneId)
    val last = endDateInclusive(zoneId)
    val from = if (first.isBefore(start)) start else first
    val to = if (!last.isBefore(endExclusive)) endExclusive.minusDays(1) else last
    if (to.isBefore(from)) return emptyList()
    val days = ChronoUnit.DAYS.between(from, to).toInt()
    return (0..days).map { from.plusDays(it.toLong()) }
}

private fun TaskEntity.visibleDates(start: LocalDate, endExclusive: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): List<LocalDate> {
    val first = startAtMillis?.toDate(zoneId) ?: dueAtMillis?.toDate(zoneId) ?: return emptyList()
    val last = (dueAtMillis?.toDate(zoneId) ?: startAtMillis?.toDate(zoneId) ?: first).coerceAtLeast(first)
    val from = if (first.isBefore(start)) start else first
    val to = if (!last.isBefore(endExclusive)) endExclusive.minusDays(1) else last
    if (to.isBefore(from)) return emptyList()
    val days = ChronoUnit.DAYS.between(from, to).toInt()
    return (0..days).map { from.plusDays(it.toLong()) }
}

private fun Long.toDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

private fun dayPendingIntentRequestCode(appWidgetId: Int, day: LocalDate): Int =
    appWidgetId * 10_000 + day.year % 100 * 400 + day.dayOfYear

private fun DayOfWeek.plus(days: Long): DayOfWeek =
    DayOfWeek.of(((value - 1 + days.toInt()) % 7) + 1)

private fun Int.isDarkColor(): Boolean {
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF
    return (0.299 * r + 0.587 * g + 0.114 * b) < 140
}

private fun Context.dpToPx(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()

private fun Context.systemAccentColor(dark: Boolean): Int {
    val names = if (dark) {
        listOf("system_accent1_200", "system_accent1_300")
    } else {
        listOf("system_accent1_600", "system_accent1_500")
    }
    names.forEach { name ->
        val id = resources.getIdentifier(name, "color", "android")
        if (id != 0) {
            val color = runCatching { getColor(id) }.getOrNull()
            if (color != null) return color
        }
    }
    return if (dark) 0xFF9BCAFF.toInt() else 0xFF2563A8.toInt()
}

private val Context.isDebuggable: Boolean
    get() = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
