package com.kgs.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.RequiresApi
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.MainActivity
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.EventEntity
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

class KgsAgendaWidgetProvider : KgsWidgetProvider(KgsWidgetKind.Agenda)

class KgsMonthWidgetProvider : KgsWidgetProvider(KgsWidgetKind.Month)

class KgsTasksWidgetProvider : KgsWidgetProvider(KgsWidgetKind.Tasks)

class KgsMultiWidgetProvider : KgsWidgetProvider(KgsWidgetKind.Multi)

class KgsDayWidgetProvider : KgsWidgetProvider(KgsWidgetKind.Day)

abstract class KgsWidgetProvider(
    private val kind: KgsWidgetKind,
) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        KgsWidgetUpdateScheduler.update(context, kind, appWidgetIds, cause = WidgetUpdateCause.Periodic)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        KgsWidgetUpdateScheduler.update(
            context,
            kind,
            intArrayOf(appWidgetId),
            debounceMillis = if (kind == KgsWidgetKind.Month) WIDGET_MONTH_RESIZE_DEBOUNCE_MS else 160,
            cause = WidgetUpdateCause.Resize,
        )
    }

    override fun onEnabled(context: Context) {
        KgsWidgetUpdateScheduler.update(context, kind)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        if (kind == KgsWidgetKind.Month || kind == KgsWidgetKind.Multi) {
            appWidgetIds.forEach { appWidgetId ->
                KgsWidgetMonthState.clear(context.applicationContext, appWidgetId)
            }
        }
        if (kind == KgsWidgetKind.Day) {
            appWidgetIds.forEach { appWidgetId ->
                KgsWidgetDayState.clear(context.applicationContext, appWidgetId)
            }
        }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action?.startsWith("com.kgs.calendar.widget.") == true) {
            WidgetLog.d(context, "Received widget action ${intent.action}")
        }
        when (intent.action) {
            ACTION_MONTH_PREVIOUS,
            ACTION_MONTH_NEXT,
            ACTION_MONTH_TODAY -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if ((kind == KgsWidgetKind.Month || kind == KgsWidgetKind.Multi) && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val command = when (intent.action) {
                        ACTION_MONTH_PREVIOUS -> MonthCommand.Previous
                        ACTION_MONTH_NEXT -> MonthCommand.Next
                        else -> MonthCommand.Today
                    }
                    val snapshot = KgsWidgetMonthState.apply(context.applicationContext, appWidgetId, command)
                    navigateMonthAsync(context, appWidgetId, snapshot)
                } else {
                    super.onReceive(context, intent)
                }
            }

            ACTION_DAY_PREVIOUS,
            ACTION_DAY_NEXT,
            ACTION_DAY_TODAY -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (kind == KgsWidgetKind.Day && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    if (intent.action == ACTION_DAY_TODAY) {
                        KgsWidgetDayState.resetToToday(context.applicationContext, appWidgetId)
                    } else {
                        val direction = if (intent.action == ACTION_DAY_PREVIOUS) -1 else 1
                        KgsWidgetDayState.offset(context.applicationContext, appWidgetId, direction)
                    }
                    navigateDayAsync(context, appWidgetId)
                } else {
                    super.onReceive(context, intent)
                }
            }

            ACTION_DAY_TOGGLE_ALL_DAY -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (kind == KgsWidgetKind.Day && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    toggleDayAllDayAsync(context, appWidgetId)
                } else {
                    super.onReceive(context, intent)
                }
            }

            ACTION_TASKS_SORT_NEXT -> {
                if (kind == KgsWidgetKind.Tasks) {
                    cycleTasksSortAsync(
                        context = context,
                        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
                    )
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
                    cause = WidgetUpdateCause.Manual,
                )
            }

            Intent.ACTION_CONFIGURATION_CHANGED -> {
                updateAsync(
                    context = context,
                    forceFullDayUpdate = kind == KgsWidgetKind.Day,
                    cause = WidgetUpdateCause.Configuration,
                )
            }

            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                updateAsync(
                    context = context,
                    appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS),
                    cause = WidgetUpdateCause.Periodic,
                )
            }

            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    KgsWidgetUpdateScheduler.update(
                        context,
                        kind,
                        intArrayOf(appWidgetId),
                        debounceMillis = if (kind == KgsWidgetKind.Month) WIDGET_MONTH_RESIZE_DEBOUNCE_MS else 160,
                        cause = WidgetUpdateCause.Resize,
                    )
                } else {
                    super.onReceive(context, intent)
                }
            }

            else -> super.onReceive(context, intent)
        }
    }

    private fun updateAsync(
        context: Context,
        appWidgetIds: IntArray? = null,
        forceFullDayUpdate: Boolean = false,
        cause: WidgetUpdateCause = WidgetUpdateCause.Unknown,
    ) {
        val pendingResult = goAsync()
        KgsWidgetUpdateScheduler.update(
            context = context.applicationContext,
            kind = kind,
            appWidgetIds = appWidgetIds,
            forceFullDayUpdate = forceFullDayUpdate,
            cause = cause,
            onCompletion = pendingResult::finish,
        )
    }

    private fun navigateDayAsync(context: Context, appWidgetId: Int) {
        val pendingResult = goAsync()
        KgsWidgetUpdateScheduler.launchWidgetLatest(
            context = context.applicationContext,
            kind = KgsWidgetKind.Day,
            appWidgetId = appWidgetId,
            cause = WidgetUpdateCause.Navigation,
            onCompletion = pendingResult::finish,
        ) {
            KgsWidgetUpdater.navigateDay(context.applicationContext, appWidgetId)
        }
    }

    private fun toggleDayAllDayAsync(context: Context, appWidgetId: Int) {
        val pendingResult = goAsync()
        KgsWidgetUpdateScheduler.launchWidgetLatest(
            context = context.applicationContext,
            kind = KgsWidgetKind.Day,
            appWidgetId = appWidgetId,
            cause = WidgetUpdateCause.Interaction,
            onCompletion = pendingResult::finish,
        ) {
            KgsWidgetUpdater.toggleDayAllDay(context.applicationContext, appWidgetId)
        }
    }

    private fun cycleTasksSortAsync(context: Context, appWidgetId: Int) {
        val pendingResult = goAsync()
        KgsWidgetUpdateScheduler.launch {
            try {
                val targetIds = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    intArrayOf(appWidgetId)
                } else {
                    AppWidgetManager.getInstance(context.applicationContext)
                        .getAppWidgetIds(ComponentName(context.applicationContext, KgsWidgetKind.Tasks.providerClass))
                }
                val graph = KgsCalendarApplication.graph(context.applicationContext)
                val currentMode = graph.settingsStore.tasksWidgetSortMode.first()
                val nextMode = currentMode.next()
                graph.settingsStore.setTasksWidgetSortMode(nextMode)
                val textContext = context.applicationContext.withWidgetLocale(
                    graph.settingsStore.languageMode.first().toLocale(context.applicationContext),
                )
                if (KgsWidgetKind.Tasks.usesDirectCollectionItems()) {
                    val createMode = graph.settingsStore.tasksWidgetCreateMode.first()
                    val sortButtonTokens = targetIds.associateWith { KgsWidgetInteractionTokens.next(tasksSortButtonTokenKey(it)) }
                    val listTokens = targetIds.associateWith { KgsWidgetInteractionTokens.next(tasksListTokenKey(it)) }
                    prepareTasksSortButtonForModeChange(
                        context = textContext,
                        appWidgetIds = targetIds,
                        fromMode = currentMode,
                        toMode = nextMode,
                        createMode = createMode,
                        tokens = sortButtonTokens,
                    )
                    refreshTasksWidgetRows(
                        context = context.applicationContext,
                        appWidgetIds = targetIds,
                        shouldApply = { id -> listTokens[id]?.let { KgsWidgetInteractionTokens.isCurrent(tasksListTokenKey(id), it) } == true },
                    )
                    animateTasksSortButton(
                        context = textContext,
                        appWidgetIds = targetIds,
                        fromMode = currentMode,
                        toMode = nextMode,
                        createMode = createMode,
                        tokens = sortButtonTokens,
                        startStep = 1,
                    )
                } else {
                    bindTasksSortLabel(textContext, targetIds, nextMode)
                    delay(180)
                    refreshTasksWidgetRows(context.applicationContext, targetIds)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun bindTasksSortLabel(context: Context, appWidgetIds: IntArray, mode: WidgetTaskSortMode) {
        if (appWidgetIds.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_calendar_tasks)
            views.bindTasksCollectionActions(context, appWidgetId)
            views.bindTasksSortButtonState(context, appWidgetId, mode, mode.widgetButtonWidthDp(context))
            manager.partiallyUpdateAppWidget(appWidgetId, views)
        }
    }

    private fun RemoteViews.bindTasksCollectionActions(context: Context, appWidgetId: Int) {
        setRemoteAdapter(
            R.id.widget_list,
            tasksCollectionAdapterIntent(context, appWidgetId),
        )
        setPendingIntentTemplate(R.id.widget_list, tasksCollectionClickPendingIntent(context, appWidgetId))
    }

    private fun navigateMonthAsync(
        context: Context,
        appWidgetId: Int,
        snapshot: MonthNavSnapshot,
    ) {
        val pendingResult = goAsync()
        KgsWidgetUpdateScheduler.launchWidgetLatest(
            context = context.applicationContext,
            kind = kind,
            appWidgetId = appWidgetId,
            cause = WidgetUpdateCause.Navigation,
            onCompletion = pendingResult::finish,
        ) {
            KgsWidgetUpdater.navigateMonth(
                context = context.applicationContext,
                kind = kind,
                appWidgetId = appWidgetId,
                snapshot = snapshot,
            )
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.kgs.calendar.widget.REFRESH"
        const val ACTION_MONTH_PREVIOUS = "com.kgs.calendar.widget.MONTH_PREVIOUS"
        const val ACTION_MONTH_NEXT = "com.kgs.calendar.widget.MONTH_NEXT"
        const val ACTION_MONTH_TODAY = "com.kgs.calendar.widget.MONTH_TODAY"
        const val ACTION_DAY_PREVIOUS = "com.kgs.calendar.widget.DAY_PREVIOUS"
        const val ACTION_DAY_NEXT = "com.kgs.calendar.widget.DAY_NEXT"
        const val ACTION_DAY_TODAY = "com.kgs.calendar.widget.DAY_TODAY"
        const val ACTION_DAY_TOGGLE_ALL_DAY = "com.kgs.calendar.widget.DAY_TOGGLE_ALL_DAY"
        const val ACTION_TASKS_SORT_NEXT = "com.kgs.calendar.widget.TASKS_SORT_NEXT"
        const val ACTION_COLLECTION_CLICK = "com.kgs.calendar.widget.COLLECTION_CLICK"
        const val EXTRA_TASK_RESOURCE_HREF = "com.kgs.calendar.widget.TASK_RESOURCE_HREF"
    }
}
