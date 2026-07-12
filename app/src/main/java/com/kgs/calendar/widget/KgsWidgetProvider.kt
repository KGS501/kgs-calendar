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

internal const val TAG = "KgsWidget"
internal const val EXTRA_WIDGET_KIND = "kgs_widget_kind"
internal const val EXTRA_WIDGET_DATE = "kgs_widget_date"
internal const val EXTRA_WIDGET_ACTION = "kgs_widget_action"
internal const val EXTRA_WIDGET_TASK_UID = "kgs_widget_task_uid"
internal const val EXTRA_WIDGET_EVENT_UID = "kgs_widget_event_uid"
internal const val EXTRA_WIDGET_TASK_CREATE_MODE = "kgs_widget_task_create_mode"
internal const val WIDGET_ACTION_CREATE_EVENT = "create_event"
internal const val WIDGET_ACTION_CREATE_TASK = "create_task"
internal const val WIDGET_ACTION_OPEN_TASK = "open_task"
internal const val WIDGET_ACTION_OPEN_EVENT = "open_event"
private const val WIDGET_TASK_CREATE_UNPLANNED = "Unplanned"
internal const val EXTRA_COLLECTION_ACTION = "kgs_widget_collection_action"
internal const val COLLECTION_ACTION_OPEN = "open"
internal const val COLLECTION_ACTION_TOGGLE_TASK = "toggle_task"
internal const val COLLECTION_ACTION_TOGGLE_SUBTASKS = "toggle_subtasks"
internal const val WIDGET_MONTH_DOT_SIZE_DP = 3.5f
internal const val WIDGET_MONTH_SPAN_FADE_WIDTH_DP = 14f
internal const val WIDGET_MONTH_SPAN_FADE_TEXT_INSET_DP = 3f
private const val WIDGET_MONTH_RESIZE_DEBOUNCE_MS = 320L
internal const val WIDGET_MULTI_CONTENT_PADDING_DP = 12
internal const val WIDGET_MONTH_RENDER_SIGNATURE_VERSION = 41
internal const val WIDGET_DAY_RENDER_SIGNATURE_VERSION = 10
internal const val WIDGET_DAY_START_HOUR = 0
internal const val WIDGET_DAY_END_HOUR = 23
internal const val WIDGET_DAY_DEFAULT_TASK_DURATION_MILLIS = 30L * 60L * 1000L
internal const val WIDGET_DAY_ROOT_PADDING_DP = 12
internal const val WIDGET_DAY_LIST_SIDE_BLEED_DP = 0f
private const val WIDGET_DAY_ROW_SIDE_INSET_DP = 0f
internal const val WIDGET_DAY_HEADER_HEIGHT_DP = 38
internal const val WIDGET_DAY_TIMELINE_TOP_MARGIN_DP = 6
internal const val WIDGET_DAY_HOUR_ROW_HEIGHT_DP = 46
private const val WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP = 24
private const val WIDGET_DAY_ALL_DAY_TOP_PADDING_DP = 7f
private const val WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP = 29f
private const val WIDGET_DAY_ALL_DAY_EXPANSION_STEPS = 5
private const val WIDGET_DAY_ALL_DAY_EXPANSION_FRAME_DELAY_MS = 40L
private const val WIDGET_DAY_CARD_RADIUS_DP = 10f
private const val WIDGET_DAY_TIME_COLUMN_WIDTH_DP = 30f
private const val WIDGET_DAY_GRID_GAP_DP = 0f
private const val WIDGET_DAY_CARD_MIN_HEIGHT_DP = 6f
private const val WIDGET_DAY_CARD_MIN_TOUCH_HEIGHT_DP = 22f
private const val WIDGET_DAY_PRIORITY_OVERDRAW_DP = 10f
private const val WIDGET_DAY_PRIORITY_BITMAP_SCALE = 1.15f
private const val WIDGET_PRIORITY_MOTION_ITEM_LIMIT = 15
internal const val WIDGET_DAY_MAX_PRIORITY_MOTION_ROWS = WIDGET_PRIORITY_MOTION_ITEM_LIMIT
private const val WIDGET_DAY_TITLE_FADE_WIDTH_DP = 14f
private const val WIDGET_DAY_BOUNDARY_ROW_HEIGHT_DP = 18f
internal const val WIDGET_COLLECTION_RENDER_SIGNATURE_VERSION = 11
internal const val WIDGET_TASK_MAX_DEPTH = WidgetTaskCardRenderer.MAX_DEPTH
internal const val WIDGET_TASK_ART_WIDTH_DP = 360
internal const val WIDGET_TASK_MIN_CARD_WIDTH_DP = WidgetTaskCardRenderer.MIN_CARD_WIDTH_DP
internal const val WIDGET_TASK_ROW_HEIGHT_DP = WidgetTaskCardRenderer.ROW_HEIGHT_DP
internal const val WIDGET_TASK_CARD_HEIGHT_DP = WidgetTaskCardRenderer.CARD_HEIGHT_DP
internal const val WIDGET_TASK_CARD_SIDE_INSET_DP = WidgetTaskCardRenderer.CARD_SIDE_INSET_DP
internal const val WIDGET_TASK_STATUS_RADIUS_DP = WidgetTaskCardRenderer.STATUS_RADIUS_DP
internal const val WIDGET_TASK_STATUS_STROKE_DP = WidgetTaskCardRenderer.STATUS_STROKE_DP
internal const val WIDGET_TASK_SUBTASK_ARROW_HALF_WIDTH_DP = WidgetTaskCardRenderer.CHEVRON_HALF_WIDTH_DP
internal const val WIDGET_TASK_SUBTASK_ARROW_HALF_HEIGHT_DP = WidgetTaskCardRenderer.CHEVRON_HALF_HEIGHT_DP
internal const val WIDGET_TASK_SUBTASK_ARROW_STROKE_DP = WidgetTaskCardRenderer.CHEVRON_STROKE_DP
internal val WIDGET_TASK_CARD_RENDERER = WidgetTaskCardRenderer()
private const val WIDGET_TASKS_PRIORITY_FRAME_COUNT = 20
private const val WIDGET_AGENDA_PRIORITY_FRAME_COUNT = 30
internal const val WIDGET_COLLECTION_VIEW_TYPE_COUNT = 8
internal const val WIDGET_AGENDA_ART_BITMAP_SCALE = 1.15f
internal const val WIDGET_TASK_PRIORITY_BITMAP_SCALE = 1.15f
internal const val WIDGET_TASK_TRANSITION_BITMAP_SCALE = 0.78f
internal const val WIDGET_AGENDA_DATE_COLUMN_WIDTH_DP = 50
internal const val WIDGET_AGENDA_COLUMN_GAP_DP = 12
internal const val WIDGET_AGENDA_EVENT_ROW_HEIGHT_DP = 64
internal const val WIDGET_AGENDA_SPAN_EVENT_ROW_HEIGHT_DP = 96
internal const val WIDGET_DAY_EVENT_ROW_HEIGHT_DP = 52
internal const val WIDGET_AGENDA_EVENT_CARD_RADIUS_DP = 13f
internal const val WIDGET_DAY_EVENT_CARD_RADIUS_DP = 10f
internal const val WIDGET_AGENDA_MAX_ROWS = 60
internal const val WIDGET_AGENDA_LIST_SIDE_BLEED_DP = 12
internal const val WIDGET_TASK_PRIORITY_OVERDRAW_DP = 20f
private const val WIDGET_TASK_SORT_MORPH_STEPS = 5
private const val WIDGET_TASK_SORT_MORPH_FRAME_DELAY_MS = 42L
private const val WIDGET_TASK_SORT_LABEL_TRANSITION_MS = 155L
private const val WIDGET_TASK_EXPANSION_STEPS = 5
private const val WIDGET_TASK_EXPANSION_FRAME_DELAY_MS = 34L
internal const val WIDGET_TASK_TRANSITION_MIN_ROW_HEIGHT_DP = 1f
private val WIDGET_TASK_PRIORITY_MOTION_FRAME_IDS = intArrayOf(
    R.id.widget_task_priority_motion_a,
    R.id.widget_task_priority_motion_b,
    R.id.widget_task_priority_motion_c,
    R.id.widget_task_priority_motion_d,
    R.id.widget_task_priority_motion_e,
    R.id.widget_task_priority_motion_f,
    R.id.widget_task_priority_motion_g,
    R.id.widget_task_priority_motion_h,
    R.id.widget_task_priority_motion_i,
    R.id.widget_task_priority_motion_j,
    R.id.widget_task_priority_motion_k,
    R.id.widget_task_priority_motion_l,
    R.id.widget_task_priority_motion_m,
    R.id.widget_task_priority_motion_n,
    R.id.widget_task_priority_motion_o,
    R.id.widget_task_priority_motion_p,
    R.id.widget_task_priority_motion_q,
    R.id.widget_task_priority_motion_r,
    R.id.widget_task_priority_motion_s,
    R.id.widget_task_priority_motion_t,
)
private val WIDGET_AGENDA_PRIORITY_MOTION_FRAME_IDS = WIDGET_TASK_PRIORITY_MOTION_FRAME_IDS + intArrayOf(
    R.id.widget_task_priority_motion_u,
    R.id.widget_task_priority_motion_v,
    R.id.widget_task_priority_motion_w,
    R.id.widget_task_priority_motion_x,
    R.id.widget_task_priority_motion_y,
    R.id.widget_task_priority_motion_z,
    R.id.widget_task_priority_motion_aa,
    R.id.widget_task_priority_motion_ab,
    R.id.widget_task_priority_motion_ac,
    R.id.widget_task_priority_motion_ad,
)

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
                    intent.getStringExtra(EXTRA_WIDGET_EVENT_UID)?.let { putExtra(EXTRA_WIDGET_EVENT_UID, it) }
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
                            .firstOrNull { it.resourceHref == taskId }
                        if (task != null) {
                            val nextStatus = if (task.isCompleted || task.status.equals("COMPLETED", ignoreCase = true)) {
                                "NEEDS-ACTION"
                            } else {
                                "COMPLETED"
                            }
                            graph.taskMutationCoordinator.setStatus(task.resourceHref, nextStatus)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }

            COLLECTION_ACTION_TOGGLE_SUBTASKS -> {
                val taskId = intent.getStringExtra(KgsWidgetProvider.EXTRA_TASK_RESOURCE_HREF) ?: return
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val pending = goAsync()
                KgsWidgetUpdateScheduler.launch {
                    try {
                        val graph = KgsCalendarApplication.graph(context.applicationContext)
                        val defaultExpanded = graph.settingsStore.tasksWidgetSubtaskDefaultMode.first()
                            .resolveSubtasksExpandedByDefault(graph.settingsStore.subtasksExpandedByDefault.first())
                        val renderer = KgsWidgetRenderer(context.applicationContext)
                        val before = if (KgsWidgetKind.Tasks.usesDirectCollectionItems()) {
                            KgsWidgetCollectionRowsCache.get(KgsWidgetKind.Tasks, appWidgetId)
                        } else {
                            null
                        }
                        val wasExpanded = KgsWidgetTaskExpansionState.isExpanded(context.applicationContext, appWidgetId, taskId, defaultExpanded)
                        KgsWidgetTaskExpansionState.setExpanded(context.applicationContext, appWidgetId, taskId, !wasExpanded)
                        val animated = if (
                            before != null &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            KgsWidgetKind.Tasks.usesDirectCollectionItems()
                        ) {
                            val snapshots = loadSubtaskTransitionSnapshots(before) {
                                runCatching {
                                    renderer.collectionSnapshot(KgsWidgetKind.Tasks, appWidgetId)
                                }.getOrNull()
                            }
                            snapshots.target?.let { target ->
                                animateTasksSubtaskToggle(context.applicationContext, before, target, taskId)
                            } == true
                        } else {
                            false
                        }
                        if (!animated) {
                            refreshTasksWidgetRows(context.applicationContext, intArrayOf(appWidgetId))
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}

class KgsWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            KgsWidgetUpdateScheduler.updateAll(context.applicationContext)
        }
    }
}

private suspend fun refreshTasksWidgetRows(
    context: Context,
    appWidgetIds: IntArray,
    displayedSortMode: WidgetTaskSortMode? = null,
    createMode: WidgetTaskCreateMode? = null,
    shouldApply: ((Int) -> Boolean)? = null,
) {
    if (appWidgetIds.isEmpty()) return
    val appContext = context.applicationContext
    val manager = AppWidgetManager.getInstance(appContext)
    val renderer = KgsWidgetRenderer(appContext)
    val changedSnapshots = mutableListOf<WidgetCollectionSnapshot>()
    for (appWidgetId in appWidgetIds) {
        val snapshot = runCatching {
            renderer.collectionSnapshot(KgsWidgetKind.Tasks, appWidgetId)
        }.onFailure { error ->
            Log.w(TAG, "Failed to calculate Tasks widget $appWidgetId list snapshot", error)
        }.getOrNull()
        if (snapshot != null && KgsWidgetCollectionUpdateSignatures.matches(KgsWidgetKind.Tasks, appWidgetId, snapshot.signature)) {
            WidgetLog.d(appContext, "Skipped unchanged Tasks widget $appWidgetId list refresh")
        } else if (snapshot != null) {
            changedSnapshots += snapshot
        }
    }
    if (changedSnapshots.isEmpty()) return
    if (KgsWidgetKind.Tasks.usesDirectCollectionItems()) {
        changedSnapshots.forEach { snapshot ->
            if (shouldApply != null && !shouldApply(snapshot.appWidgetId)) return@forEach
            val textContext = appContext.withWidgetLocale(snapshot.settings.locale)
            val views = RemoteViews(appContext.packageName, R.layout.widget_calendar_tasks)
            views.bindCollectionBottomFade(snapshot.palette, visible = true)
            displayedSortMode?.let { mode ->
                views.bindTasksSortButtonState(
                    context = textContext,
                    appWidgetId = snapshot.appWidgetId,
                    mode = mode,
                    widthDp = mode.widgetButtonWidthDp(textContext),
                    createMode = createMode,
                )
            }
            views.bindDirectCollectionItems(
                context = textContext,
                packageName = appContext.packageName,
                palette = snapshot.palette,
                rows = snapshot.rows,
                sourceKind = KgsWidgetKind.Tasks,
                appWidgetId = snapshot.appWidgetId,
                renderOptions = WidgetCollectionRenderOptions(taskArtWidthDp = snapshot.taskArtWidthDp),
            )
            views.setPendingIntentTemplate(R.id.widget_list, tasksCollectionClickPendingIntent(appContext, snapshot.appWidgetId))
            runCatching {
                manager.partiallyUpdateAppWidget(snapshot.appWidgetId, views)
                KgsWidgetCollectionUpdateSignatures.markApplied(KgsWidgetKind.Tasks, snapshot.appWidgetId, snapshot.signature)
            }.onFailure { error ->
                Log.e(TAG, "Failed to directly refresh Tasks widget ${snapshot.appWidgetId}", error)
                if (error.isRemoteViewsBitmapMemoryError()) {
                    val fallback = RemoteViews(appContext.packageName, R.layout.widget_calendar_tasks)
                    fallback.bindCollectionBottomFade(snapshot.palette, visible = true)
                    displayedSortMode?.let { mode ->
                        fallback.bindTasksSortButtonState(
                            context = textContext,
                            appWidgetId = snapshot.appWidgetId,
                            mode = mode,
                            widthDp = mode.widgetButtonWidthDp(textContext),
                            createMode = createMode,
                        )
                    }
                    fallback.setRemoteAdapter(R.id.widget_list, tasksCollectionAdapterIntent(appContext, snapshot.appWidgetId))
                    fallback.setPendingIntentTemplate(R.id.widget_list, tasksCollectionClickPendingIntent(appContext, snapshot.appWidgetId))
                    manager.partiallyUpdateAppWidget(snapshot.appWidgetId, fallback)
                    KgsWidgetCollectionRowsCache.put(snapshot)
                    manager.notifyAppWidgetViewDataChanged(snapshot.appWidgetId, R.id.widget_list)
                }
            }
        }
        return
    }
    val snapshotsToApply = if (shouldApply == null) {
        changedSnapshots
    } else {
        changedSnapshots.filter { shouldApply(it.appWidgetId) }
    }
    if (snapshotsToApply.isEmpty()) return
    snapshotsToApply.forEach { snapshot ->
        KgsWidgetCollectionRowsCache.put(snapshot)
    }
    manager.notifyAppWidgetViewDataChanged(snapshotsToApply.map { it.appWidgetId }.toIntArray(), R.id.widget_list)
    snapshotsToApply.forEach { snapshot ->
        KgsWidgetCollectionUpdateSignatures.markApplied(KgsWidgetKind.Tasks, snapshot.appWidgetId, snapshot.signature)
    }
}

private fun tasksListTokenKey(appWidgetId: Int): String =
    "tasks-list:$appWidgetId"

private fun tasksSortButtonTokenKey(appWidgetId: Int): String =
    "tasks-sort-button:$appWidgetId"

private fun dayAllDayTokenKey(appWidgetId: Int): String =
    "day-all-day:$appWidgetId"

private fun applyTasksSortButtonFrame(
    context: Context,
    appWidgetIds: IntArray,
    mode: WidgetTaskSortMode,
    widthDp: Float,
    createMode: WidgetTaskCreateMode,
    tokens: Map<Int, Long>,
    updateDisplayedChild: Boolean = true,
) {
    if (appWidgetIds.isEmpty()) return
    val manager = AppWidgetManager.getInstance(context)
    appWidgetIds.forEach { appWidgetId ->
        val token = tokens[appWidgetId] ?: return@forEach
        if (!KgsWidgetInteractionTokens.isCurrent(tasksSortButtonTokenKey(appWidgetId), token)) return@forEach
        val views = RemoteViews(context.packageName, R.layout.widget_calendar_tasks)
        if (updateDisplayedChild) {
            views.bindTasksSortButtonState(context, appWidgetId, mode, widthDp, createMode)
        } else {
            views.bindTasksSortButtonWidth(widthDp)
        }
        manager.partiallyUpdateAppWidget(appWidgetId, views)
    }
}

private suspend fun prepareTasksSortButtonForModeChange(
    context: Context,
    appWidgetIds: IntArray,
    fromMode: WidgetTaskSortMode,
    toMode: WidgetTaskSortMode,
    createMode: WidgetTaskCreateMode,
    tokens: Map<Int, Long>,
) {
    val fromWidth = fromMode.widgetButtonWidthDp(context)
    val toWidth = toMode.widgetButtonWidthDp(context)
    if (toWidth <= fromWidth) {
        applyTasksSortButtonFrame(context, appWidgetIds, toMode, fromWidth, createMode, tokens)
        delay(WIDGET_TASK_SORT_LABEL_TRANSITION_MS)
        return
    }
    for (step in 0 until WIDGET_TASK_SORT_MORPH_STEPS) {
        val progress = motionStandardEasing(step.toFloat() / WIDGET_TASK_SORT_MORPH_STEPS.toFloat())
        applyTasksSortButtonFrame(
            context = context,
            appWidgetIds = appWidgetIds,
            mode = fromMode,
            widthDp = lerpFloat(fromWidth, toWidth, progress),
            createMode = createMode,
            tokens = tokens,
            updateDisplayedChild = false,
        )
        delay(WIDGET_TASK_SORT_MORPH_FRAME_DELAY_MS)
    }
    applyTasksSortButtonFrame(context, appWidgetIds, toMode, toWidth, createMode, tokens)
    delay(WIDGET_TASK_SORT_LABEL_TRANSITION_MS)
}

private suspend fun animateTasksSortButton(
    context: Context,
    appWidgetIds: IntArray,
    fromMode: WidgetTaskSortMode,
    toMode: WidgetTaskSortMode,
    createMode: WidgetTaskCreateMode,
    tokens: Map<Int, Long>,
    startStep: Int = 0,
) {
    val fromWidth = fromMode.widgetButtonWidthDp(context)
    val toWidth = toMode.widgetButtonWidthDp(context)
    if (toWidth >= fromWidth) return
    applyTasksSortButtonFrame(context, appWidgetIds, toMode, fromWidth, createMode, tokens, updateDisplayedChild = true)
    for (step in startStep..WIDGET_TASK_SORT_MORPH_STEPS) {
        val progress = motionStandardEasing(step.toFloat() / WIDGET_TASK_SORT_MORPH_STEPS.toFloat())
        val width = lerpFloat(fromWidth, toWidth, progress)
        applyTasksSortButtonFrame(context, appWidgetIds, toMode, width, createMode, tokens, updateDisplayedChild = false)
        if (step < WIDGET_TASK_SORT_MORPH_STEPS) {
            delay(WIDGET_TASK_SORT_MORPH_FRAME_DELAY_MS)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun applyTasksCollectionFrame(
    context: Context,
    snapshot: WidgetCollectionSnapshot,
    rows: List<WidgetListRow>,
    renderOptions: WidgetCollectionRenderOptions,
    token: Long,
): Boolean {
    if (!KgsWidgetInteractionTokens.isCurrent(tasksListTokenKey(snapshot.appWidgetId), token)) return false
    val textContext = context.withWidgetLocale(snapshot.settings.locale)
    val views = RemoteViews(context.packageName, R.layout.widget_calendar_tasks)
    views.bindCollectionBottomFade(snapshot.palette, visible = true)
    views.bindDirectCollectionItems(
        context = textContext,
        packageName = context.packageName,
        palette = snapshot.palette,
        rows = rows,
        sourceKind = KgsWidgetKind.Tasks,
        appWidgetId = snapshot.appWidgetId,
        renderOptions = renderOptions.withTaskArtWidth(snapshot.taskArtWidthDp),
    )
    views.setPendingIntentTemplate(R.id.widget_list, tasksCollectionClickPendingIntent(context, snapshot.appWidgetId))
    return runCatching {
        AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(snapshot.appWidgetId, views)
    }.onFailure { error ->
        Log.e(TAG, "Failed to apply Tasks widget ${snapshot.appWidgetId} animation frame", error)
    }.isSuccess
}

@RequiresApi(Build.VERSION_CODES.S)
private suspend fun animateTasksSubtaskToggle(
    context: Context,
    before: WidgetCollectionSnapshot,
    target: WidgetCollectionSnapshot,
    parentTaskResourceHref: String,
): Boolean {
    val appWidgetId = target.appWidgetId
    val token = KgsWidgetInteractionTokens.next(tasksListTokenKey(appWidgetId))
    val beforeIds = before.rows.map { it.stableId }.toSet()
    val targetIds = target.rows.map { it.stableId }.toSet()
    val parentStableId = target.rows.firstOrNull { it.taskResourceHref == parentTaskResourceHref }?.stableId
        ?: before.rows.firstOrNull { it.taskResourceHref == parentTaskResourceHref }?.stableId
    val expanding = target.rows.firstOrNull { it.taskResourceHref == parentTaskResourceHref }?.subtasksExpanded == true
    val transitionRows = if (expanding) target.rows else before.rows
    val changingRowIds = if (expanding) targetIds - beforeIds else beforeIds - targetIds

    if (changingRowIds.isEmpty() && parentStableId == null) {
        return applyTasksCollectionFrame(context, target, target.rows, WidgetCollectionRenderOptions(), token)
    }

    for (step in 1 until WIDGET_TASK_EXPANSION_STEPS) {
        val rawProgress = step.toFloat() / WIDGET_TASK_EXPANSION_STEPS.toFloat()
        val rowProgress = if (expanding) {
            motionStandardEasing(rawProgress)
        } else {
            1f - motionStandardEasing(rawProgress)
        }
        val parentProgress = if (expanding) {
            motionStandardEasing(rawProgress)
        } else {
            1f - motionStandardEasing(rawProgress)
        }
        val frameOptions = WidgetCollectionRenderOptions(
            taskRows = buildMap {
                changingRowIds.forEach { stableId ->
                    put(stableId, WidgetTaskRowRenderOptions(rowProgress = rowProgress))
                }
                parentStableId?.let { stableId ->
                    put(stableId, WidgetTaskRowRenderOptions(subtaskExpansionProgress = parentProgress))
                }
            },
            suppressPriorityMotion = true,
            lightweightTaskTransition = true,
        )
        if (!applyTasksCollectionFrame(context, target, transitionRows, frameOptions, token)) return false
        delay(WIDGET_TASK_EXPANSION_FRAME_DELAY_MS)
    }

    if (!applyTasksCollectionFrame(context, target, target.rows, WidgetCollectionRenderOptions(), token)) return false
    KgsWidgetCollectionRowsCache.put(target)
    KgsWidgetCollectionUpdateSignatures.markApplied(KgsWidgetKind.Tasks, appWidgetId, target.signature)
    return true
}

internal fun KgsWidgetKind.usesDirectCollectionItems(): Boolean =
    (this == KgsWidgetKind.Tasks || this == KgsWidgetKind.Agenda || this == KgsWidgetKind.Multi) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

internal fun usesDirectDayGridItems(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

internal fun KgsWidgetKind.priorityMotionFrameIds(): IntArray =
    if (usesAgendaCollectionStyle()) WIDGET_AGENDA_PRIORITY_MOTION_FRAME_IDS else WIDGET_TASK_PRIORITY_MOTION_FRAME_IDS

internal fun KgsWidgetKind.priorityMotionFrameCount(): Int =
    if (usesAgendaCollectionStyle()) WIDGET_AGENDA_PRIORITY_FRAME_COUNT else WIDGET_TASKS_PRIORITY_FRAME_COUNT

internal fun KgsWidgetKind.usesAgendaCollectionStyle(): Boolean =
    this == KgsWidgetKind.Agenda || this == KgsWidgetKind.Day || this == KgsWidgetKind.Multi

private fun Throwable.isRemoteViewsBitmapMemoryError(): Boolean =
    this is IllegalArgumentException &&
        message?.contains("bitmap", ignoreCase = true) == true &&
        message?.contains("memory", ignoreCase = true) == true

@RequiresApi(Build.VERSION_CODES.S)
internal fun RemoteViews.bindDirectCollectionItems(
    context: Context,
    packageName: String,
    palette: WidgetPalette,
    rows: List<WidgetListRow>,
    sourceKind: KgsWidgetKind,
    appWidgetId: Int,
    renderOptions: WidgetCollectionRenderOptions = WidgetCollectionRenderOptions(),
) {
    val builder = RemoteViews.RemoteCollectionItems.Builder()
        .setHasStableIds(true)
        .setViewTypeCount(WIDGET_COLLECTION_VIEW_TYPE_COUNT)
    rows.forEach { row ->
        builder.addItem(row.stableId, row.toRemoteViews(context, packageName, palette, sourceKind, appWidgetId, renderOptions))
    }
    setRemoteAdapter(R.id.widget_list, builder.build())
}

@RequiresApi(Build.VERSION_CODES.S)
internal fun RemoteViews.bindDirectDayGridItems(
    context: Context,
    packageName: String,
    palette: WidgetPalette,
    rows: List<WidgetDayGridRow>,
    widthDp: Float,
    appWidgetId: Int,
) {
    val builder = RemoteViews.RemoteCollectionItems.Builder()
        .setHasStableIds(true)
        .setViewTypeCount(2)
    rows.forEach { row ->
        builder.addItem(
            row.stableId,
            row.toRemoteViews(
                context = context,
                packageName = packageName,
                palette = palette,
                widthDp = widthDp,
                appWidgetId = appWidgetId,
            ),
        )
    }
    setRemoteAdapter(R.id.widget_list, builder.build())
}

internal fun RemoteViews.bindCollectionBottomFade(palette: WidgetPalette, visible: Boolean) {
    setInt(R.id.widget_bottom_fade, "setBackgroundResource", palette.bottomFadeRes)
    setViewVisibility(R.id.widget_bottom_fade, if (visible) View.VISIBLE else View.GONE)
}

internal fun RemoteViews.bindTasksSortButtonState(
    context: Context,
    appWidgetId: Int,
    mode: WidgetTaskSortMode,
    widthDp: Float,
    createMode: WidgetTaskCreateMode? = null,
    updateDisplayedChild: Boolean = true,
) {
    setViewVisibility(R.id.widget_sort, View.VISIBLE)
    setImageViewBitmap(R.id.widget_sort_icon, widgetSortIconBitmap(context, 0xFFFFFFFF.toInt()))
    setTextViewText(R.id.widget_sort_label_date, WidgetTaskSortMode.Date.widgetLabel(context))
    setTextViewText(R.id.widget_sort_label_priority, WidgetTaskSortMode.Priority.widgetLabel(context))
    setTextViewText(R.id.widget_sort_label_status, WidgetTaskSortMode.Status.widgetLabel(context))
    setTextColor(R.id.widget_sort_label_date, 0xFFFFFFFF.toInt())
    setTextColor(R.id.widget_sort_label_priority, 0xFFFFFFFF.toInt())
    setTextColor(R.id.widget_sort_label_status, 0xFFFFFFFF.toInt())
    if (updateDisplayedChild) {
        setDisplayedChild(R.id.widget_sort_label_flipper, mode.ordinal)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setViewLayoutWidth(R.id.widget_sort, widthDp, TypedValue.COMPLEX_UNIT_DIP)
    }
    setTextViewText(R.id.widget_badge, "+")
    setTextViewTextSize(R.id.widget_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
    setTextColor(R.id.widget_badge, 0xFFFFFFFF.toInt())
    createMode?.let {
        setOnClickPendingIntent(
            R.id.widget_badge,
            tasksWidgetCreatePendingIntent(context, appWidgetId, LocalDate.now(), it),
        )
    }
    val sortPendingIntent = tasksSortPendingIntent(context, appWidgetId)
    setOnClickPendingIntent(R.id.widget_sort, sortPendingIntent)
    setOnClickPendingIntent(R.id.widget_sort_icon, sortPendingIntent)
    setOnClickPendingIntent(R.id.widget_sort_label_flipper, sortPendingIntent)
    setOnClickPendingIntent(R.id.widget_sort_label_date, sortPendingIntent)
    setOnClickPendingIntent(R.id.widget_sort_label_priority, sortPendingIntent)
    setOnClickPendingIntent(R.id.widget_sort_label_status, sortPendingIntent)
}

private fun RemoteViews.bindTasksSortButtonWidth(widthDp: Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setViewLayoutWidth(R.id.widget_sort, widthDp, TypedValue.COMPLEX_UNIT_DIP)
    }
}

internal fun WidgetTaskSortMode.widgetButtonWidthDp(context: Context): Float {
    val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
    val textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, context.resources.displayMetrics)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSizePx
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val labelWidthDp = paint.measureText(widgetLabel(context)) / density
    return (13f + 17f + 5f + labelWidthDp + 13f + 8f).coerceAtLeast(70f)
}

private fun tasksSortPendingIntent(context: Context, appWidgetId: Int): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        60_000 + appWidgetId,
        Intent(context, KgsTasksWidgetProvider::class.java).apply {
            action = KgsWidgetProvider.ACTION_TASKS_SORT_NEXT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-tasks/sort/$appWidgetId")
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private fun tasksCollectionAdapterIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, KgsWidgetCollectionService::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        putExtra(EXTRA_WIDGET_KIND, KgsWidgetKind.Tasks.name)
        data = Uri.parse("kgs-calendar://widget-collection/${KgsWidgetKind.Tasks.name}/$appWidgetId")
    }

private fun tasksCollectionClickPendingIntent(context: Context, appWidgetId: Int): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        50_000 + appWidgetId * 10 + KgsWidgetKind.Tasks.ordinal,
        Intent(context, KgsWidgetActionReceiver::class.java).apply {
            action = KgsWidgetProvider.ACTION_COLLECTION_CLICK
            data = Uri.parse("kgs-calendar://widget-collection-click/${KgsWidgetKind.Tasks.name}/$appWidgetId")
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

private fun tasksWidgetCreatePendingIntent(
    context: Context,
    appWidgetId: Int,
    date: LocalDate,
    mode: WidgetTaskCreateMode,
): PendingIntent {
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

internal fun widgetSortIconBitmap(context: Context, tint: Int): Bitmap {
    val size = context.dpToPx(17)
    val density = context.resources.displayMetrics.density
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        strokeWidth = 1.85f * density
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    val start = 3f * density
    canvas.drawLine(start, 5f * density, 14f * density, 5f * density, paint)
    canvas.drawLine(start, 8.5f * density, 11f * density, 8.5f * density, paint)
    canvas.drawLine(start, 12f * density, 8f * density, 12f * density, paint)
    return bitmap
}

internal fun widgetTodayDateIconBitmap(context: Context, tint: Int, day: Int): Bitmap {
    val size = context.dpToPx(20)
    val density = context.resources.displayMetrics.density
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = 1.55f * density
    val iconRect = RectF(
        stroke / 2f,
        stroke / 2f,
        size - stroke / 2f,
        size - stroke / 2f,
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawRoundRect(iconRect, 3.8f * density, 3.8f * density, paint)
    canvas.drawLine(
        stroke * 2.2f,
        size * 0.28f,
        size - stroke * 2.2f,
        size * 0.28f,
        paint,
    )
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9.2f, context.resources.displayMetrics)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val metrics = textPaint.fontMetrics
    val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f + 1.6f * density
    canvas.drawText(day.coerceIn(1, 31).toString(), size / 2f, baseline, textPaint)
    return bitmap
}

object KgsWidgetUpdater {
    suspend fun updateAll(context: Context) {
        KgsWidgetKind.entries.forEach { kind ->
            updateKind(context, kind, cause = WidgetUpdateCause.DataChange)
        }
    }

    suspend fun updateKind(
        context: Context,
        kind: KgsWidgetKind,
        forceFullDayUpdate: Boolean = false,
        cause: WidgetUpdateCause = WidgetUpdateCause.Unknown,
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, kind.providerClass))
        if (ids.isNotEmpty()) {
            update(context, kind, ids, forceFullDayUpdate = forceFullDayUpdate, cause = cause)
        }
    }

    suspend fun update(
        context: Context,
        kind: KgsWidgetKind,
        appWidgetIds: IntArray,
        forceFullDayUpdate: Boolean = false,
        cause: WidgetUpdateCause = WidgetUpdateCause.Unknown,
    ) {
        if (appWidgetIds.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        val renderer = KgsWidgetRenderer(context)
        appWidgetIds.forEach { appWidgetId ->
            WidgetPerformanceMonitor.trace(context, kind, appWidgetId, cause) { metrics ->
            val options = manager.getAppWidgetOptions(appWidgetId)
            val targetMonthRevision = if (kind == KgsWidgetKind.Month || kind == KgsWidgetKind.Multi) {
                KgsWidgetMonthState.revision(context, appWidgetId)
            } else {
                null
            }
            val incrementalDayUpdate =
                kind == KgsWidgetKind.Day &&
                    !forceFullDayUpdate &&
                    KgsWidgetDayState.isInitialized(context, appWidgetId)
            var monthResult: MonthWidgetRenderResult? = null
            val preparedMulti = if (kind == KgsWidgetKind.Multi) {
                val dataLoadStarted = SystemClock.elapsedRealtime()
                runCatching {
                    renderer.prepareMultiUpdate(appWidgetId, options)
                }.onFailure { error ->
                    Log.w(TAG, "Failed to calculate Multi widget $appWidgetId snapshot", error)
                }.getOrNull().also { prepared ->
                    metrics.recordDataLoad(
                        durationMillis = SystemClock.elapsedRealtime() - dataLoadStarted,
                        rowsBuilt = prepared?.itemCount ?: 0,
                    )
                }
            } else {
                null
            }
            val collectionSnapshot = if (kind.usesCollectionList && kind != KgsWidgetKind.Multi) {
                val dataLoadStarted = SystemClock.elapsedRealtime()
                runCatching {
                    renderer.collectionSnapshot(kind, appWidgetId)
                }.onFailure { error ->
                    Log.w(TAG, "Failed to calculate ${kind.name} widget $appWidgetId snapshot", error)
                }.getOrNull().also { snapshot ->
                    metrics.recordDataLoad(
                        durationMillis = SystemClock.elapsedRealtime() - dataLoadStarted,
                        rowsBuilt = snapshot?.rows?.size ?: 0,
                    )
                }
            } else {
                preparedMulti?.collectionSnapshot
            }
            val preparedMonth = if (kind == KgsWidgetKind.Month) {
                val dataLoadStarted = SystemClock.elapsedRealtime()
                renderer.prepareMonthUpdate(appWidgetId, options).also { prepared ->
                    metrics.recordDataLoad(
                        durationMillis = SystemClock.elapsedRealtime() - dataLoadStarted,
                        rowsBuilt = prepared.itemCount,
                    )
                }
            } else {
                null
            }
            val collectionSignature = preparedMulti?.signature ?: collectionSnapshot?.signature
            if (collectionSignature != null && KgsWidgetCollectionUpdateSignatures.matches(kind, appWidgetId, collectionSignature)) {
                WidgetLog.d(context, "Skipped unchanged ${kind.name} widget $appWidgetId")
                return@trace
            }
            if (
                preparedMonth != null &&
                !shouldBuildMonthRemoteViews(preparedMonth.signature) { signature ->
                    KgsWidgetMonthUpdateSignatures.matches(appWidgetId, signature)
                }
            ) {
                WidgetLog.d(context, "Skipped unchanged Month widget $appWidgetId before RemoteViews build")
                return@trace
            }
            val renderStarted = SystemClock.elapsedRealtime()
            val views = try {
                if (kind == KgsWidgetKind.Month) {
                    renderer.renderMonthUpdate(requireNotNull(preparedMonth))
                        .also { monthResult = it }
                        .views
                } else if (incrementalDayUpdate) {
                    renderer.renderDayNavigationUpdate(
                        appWidgetId = appWidgetId,
                        options = options,
                    )
                } else {
                    renderer.render(
                        kind = kind,
                        appWidgetId = appWidgetId,
                        options = options,
                        collectionSnapshot = collectionSnapshot,
                        preparedMulti = preparedMulti,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to render ${kind.name} widget $appWidgetId", error)
                renderer.error(kind, appWidgetId, error.message ?: "Widget update failed.")
            }
            metrics.recordRemoteViewsBuild(SystemClock.elapsedRealtime() - renderStarted)
            val signature = monthResult?.signature
            if (
                targetMonthRevision != null &&
                KgsWidgetMonthState.revision(context, appWidgetId) != targetMonthRevision
            ) {
                return@trace
            }
            if (signature != null && KgsWidgetMonthUpdateSignatures.matches(appWidgetId, signature)) {
                WidgetLog.d(context, "Skipped unchanged Month widget $appWidgetId")
                return@trace
            }
            runCatching {
                if (collectionSnapshot != null) {
                    KgsWidgetCollectionRowsCache.put(collectionSnapshot)
                }

                val applyUpdate = {
                    val applyStarted = SystemClock.elapsedRealtime()
                    if (incrementalDayUpdate) {
                        manager.partiallyUpdateAppWidget(appWidgetId, views)
                    } else {
                        manager.updateAppWidget(appWidgetId, views)
                    }
                    metrics.recordBinderApply(SystemClock.elapsedRealtime() - applyStarted)
                    if (
                        (kind.usesCollectionList && !kind.usesDirectCollectionItems() && kind != KgsWidgetKind.Day) ||
                        (kind == KgsWidgetKind.Day && !usesDirectDayGridItems())
                    ) {
                        manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
                    }
                    if (kind == KgsWidgetKind.Day) {
                        KgsWidgetDayState.markInitialized(context, appWidgetId)
                    }
                    if (signature != null) {
                        KgsWidgetMonthUpdateSignatures.markApplied(appWidgetId, signature)
                    }
                    if (collectionSignature != null) {
                        KgsWidgetCollectionUpdateSignatures.markApplied(kind, appWidgetId, collectionSignature)
                    }
                }
                if (targetMonthRevision != null) {
                    KgsWidgetMonthState.applyIfRevisionCurrent(
                        context = context,
                        appWidgetId = appWidgetId,
                        revision = targetMonthRevision,
                        block = applyUpdate,
                    )
                } else {
                    applyUpdate()
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to update ${kind.name} widget $appWidgetId", error)
                if (
                    (kind.usesDirectCollectionItems() || (kind == KgsWidgetKind.Day && usesDirectDayGridItems())) &&
                    error.isRemoteViewsBitmapMemoryError()
                ) {
                    runCatching {
                        val fallbackViews = renderer.render(
                            kind = kind,
                            appWidgetId = appWidgetId,
                            options = options,
                            forceServiceCollection = true,
                            collectionSnapshot = collectionSnapshot,
                            preparedMulti = preparedMulti,
                        )
                        val applyFallback = {
                            manager.updateAppWidget(appWidgetId, fallbackViews)
                            manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
                        }
                        if (targetMonthRevision != null) {
                            KgsWidgetMonthState.applyIfRevisionCurrent(
                                context = context,
                                appWidgetId = appWidgetId,
                                revision = targetMonthRevision,
                                block = applyFallback,
                            )
                        } else {
                            applyFallback()
                        }
                    }.onFailure { fallbackError ->
                        Log.e(TAG, "Failed to update ${kind.name} widget $appWidgetId with service collection fallback", fallbackError)
                    }
                }
            }
            }
        }
    }

    internal suspend fun navigateMonth(
        context: Context,
        kind: KgsWidgetKind,
        appWidgetId: Int,
        snapshot: MonthNavSnapshot,
    ) {
        require(kind == KgsWidgetKind.Month || kind == KgsWidgetKind.Multi)
        require(snapshot.widgetId == appWidgetId)
        val manager = AppWidgetManager.getInstance(context)
        val zoneId = ZoneId.systemDefault()
        val dataSource = KgsWidgetDataSource(context, zoneId)
        val pageSource = WidgetMonthPageSource(context, zoneId)
        val renderer = WidgetMonthRemoteViewsRenderer(context, zoneId)
        val options = manager.getAppWidgetOptions(appWidgetId)
        val settings = dataSource.loadSettings(kind)

        fun applyIfCurrent(result: MonthWidgetRenderResult): Boolean =
            runCatching {
                KgsWidgetMonthState.applyIfCurrent(context, snapshot) {
                    if (kind == KgsWidgetKind.Multi) {
                        manager.partiallyUpdateAppWidget(appWidgetId, result.views)
                    } else {
                        manager.updateAppWidget(appWidgetId, result.views)
                    }
                    result.signature?.let { signature ->
                        KgsWidgetMonthUpdateSignatures.markApplied(appWidgetId, signature)
                    }
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to apply ${kind.name} month page $appWidgetId", error)
            }.getOrDefault(false)

        var generation = WidgetDataGeneration.current()
        val cachedLookup = KgsWidgetMonthPageCache.getForNavigation(
            snapshot.month,
            settings,
            zoneId.id,
            generation,
        )
        val initialPage = selectMonthNavigationInitialPage(cachedLookup?.page)

        if (initialPage != null) {
            if (!applyIfCurrent(
                    renderer.render(
                        kind = kind,
                        snapshot = snapshot,
                        options = options,
                        settings = settings,
                        page = initialPage,
                        hasCompleteData = true,
                    ),
                )
            ) {
                return
            }
        }
        if (!KgsWidgetMonthState.isCurrent(context, snapshot)) return
        if (
            cachedLookup?.freshness == WidgetMonthPageFreshness.CurrentGeneration &&
            WidgetDataGeneration.current() == generation
        ) {
            warmWidgetMonthPageCache(context, zoneId, snapshot.month, settings)
            return
        }
        var authoritativePage = try {
            pageSource.load(snapshot.month, settings)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to load ${kind.name} month page $appWidgetId", error)
            return
        }
        if (WidgetDataGeneration.current() != generation) {
            generation = WidgetDataGeneration.current()
            if (!KgsWidgetMonthState.isCurrent(context, snapshot)) return
            authoritativePage = try {
                pageSource.load(snapshot.month, settings)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to reload ${kind.name} month page $appWidgetId", error)
                return
            }
        }
        val authoritativeDecision = authoritativeMonthPageDecision(
            navigationCurrent = KgsWidgetMonthState.isCurrent(context, snapshot),
            loadedGeneration = generation,
            currentGeneration = WidgetDataGeneration.current(),
        )
        if (!authoritativeDecision.apply) return
        if (authoritativeDecision.cache) {
            KgsWidgetMonthPageCache.put(
                month = snapshot.month,
                settings = settings,
                zoneId = zoneId.id,
                page = authoritativePage,
                generation = generation,
            )
            warmWidgetMonthPageCache(context, zoneId, snapshot.month, settings)
        }
        if (cachedLookup?.page != authoritativePage) {
            applyIfCurrent(
                renderer.render(
                    kind = kind,
                    snapshot = snapshot,
                    options = options,
                    settings = settings,
                    page = authoritativePage,
                    hasCompleteData = true,
                ),
            )
        }
    }

    suspend fun navigateDay(context: Context, appWidgetId: Int) {
        val manager = AppWidgetManager.getInstance(context)
        val renderer = KgsWidgetRenderer(context)
        val options = manager.getAppWidgetOptions(appWidgetId)
        val views = try {
            renderer.renderDayNavigationUpdate(
                appWidgetId = appWidgetId,
                options = options,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to render Day widget navigation $appWidgetId", error)
            renderer.error(KgsWidgetKind.Day, appWidgetId, error.message ?: "Widget update failed.")
        }
        runCatching {
            manager.partiallyUpdateAppWidget(appWidgetId, views)
            if (!usesDirectDayGridItems()) {
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to navigate Day widget $appWidgetId", error)
            manager.updateAppWidget(appWidgetId, views)
            if (!usesDirectDayGridItems()) {
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
            }
        }
    }

    suspend fun toggleDayAllDay(context: Context, appWidgetId: Int) {
        if (!usesDirectDayGridItems()) {
            val nextExpanded = !KgsWidgetDayState.isAllDayExpanded(context, appWidgetId)
            KgsWidgetDayState.setAllDayExpanded(context, appWidgetId, nextExpanded)
            update(context, KgsWidgetKind.Day, intArrayOf(appWidgetId))
            return
        }
        val manager = AppWidgetManager.getInstance(context)
        val renderer = KgsWidgetRenderer(context)
        val options = manager.getAppWidgetOptions(appWidgetId)
        val targetExpanded = !KgsWidgetDayState.isAllDayExpanded(context, appWidgetId)
        KgsWidgetDayState.setAllDayExpanded(context, appWidgetId, targetExpanded)
        val token = KgsWidgetInteractionTokens.next(dayAllDayTokenKey(appWidgetId))
        val frameData = try {
            renderer.dayAllDaySectionFrameData(appWidgetId, options)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to prepare Day widget all-day expansion $appWidgetId", error)
            update(context, KgsWidgetKind.Day, intArrayOf(appWidgetId))
            return
        }
        for (step in 0..WIDGET_DAY_ALL_DAY_EXPANSION_STEPS) {
            if (!KgsWidgetInteractionTokens.isCurrent(dayAllDayTokenKey(appWidgetId), token)) return
            val rawProgress = step.toFloat() / WIDGET_DAY_ALL_DAY_EXPANSION_STEPS.toFloat()
            val eased = motionStandardEasing(rawProgress)
            val progress = if (targetExpanded) eased else 1f - eased
            val views = try {
                renderer.renderDayAllDaySectionFrame(
                    frameData = frameData,
                    expansionProgress = progress,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to render Day widget all-day expansion frame $appWidgetId", error)
                update(context, KgsWidgetKind.Day, intArrayOf(appWidgetId))
                return
            }
            runCatching {
                manager.partiallyUpdateAppWidget(appWidgetId, views)
            }.onFailure { error ->
                Log.e(TAG, "Failed to update Day widget all-day expansion frame $appWidgetId", error)
                update(context, KgsWidgetKind.Day, intArrayOf(appWidgetId))
                return
            }
            if (step < WIDGET_DAY_ALL_DAY_EXPANSION_STEPS) {
                delay(WIDGET_DAY_ALL_DAY_EXPANSION_FRAME_DELAY_MS)
            }
        }
    }
}

internal data class WidgetDayTimeline(
    val day: LocalDate,
    val allDayItems: List<WidgetDayItem>,
    val timedItems: List<WidgetDayTimedLayout>,
    val signature: String,
)

internal data class WidgetDayGridCollectionSnapshot(
    val rows: List<WidgetDayGridRow>,
    val settings: WidgetRenderSettings,
    val palette: WidgetPalette,
    val widthDp: Float,
)

internal data class WidgetDayAllDaySectionFrameData(
    val appWidgetId: Int,
    val settings: WidgetRenderSettings,
    val palette: WidgetPalette,
    val timeline: WidgetDayTimeline,
    val contentWidthDp: Float,
    val allDayExpanded: Boolean,
)

internal data class WidgetDayItem(
    val title: String,
    val meta: String,
    val color: Int,
    val completed: Boolean,
    val isTask: Boolean,
    val stableKey: String,
    val location: String? = null,
    val eventStatus: String? = null,
    val eventResourceHref: String? = null,
    val taskResourceHref: String? = null,
    val statusGlyph: String = "",
    val priority: Int? = null,
)

internal data class WidgetDayTimedItem(
    val item: WidgetDayItem,
    val startMinute: Int,
    val endMinute: Int,
)

internal data class WidgetDayTimedLayout(
    val item: WidgetDayItem,
    val startMinute: Int,
    val endMinute: Int,
    val lane: Int,
    val laneCount: Int,
)

private fun allDayRowsHeight(rows: Int): Float {
    val safeRows = rows.coerceAtLeast(1)
    return safeRows * WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP.toFloat() +
        (safeRows - 1).coerceAtLeast(0) * 5f +
        10f
}

internal data class WidgetDayGridRow(
    val day: LocalDate,
    val hour: Int?,
    val hourCount: Int = 1,
    val hourRowHeightDp: Float = WIDGET_DAY_HOUR_ROW_HEIGHT_DP.toFloat(),
    val allDayItems: List<WidgetDayItem> = emptyList(),
    val timedItems: List<WidgetDayTimedLayout> = emptyList(),
    val nowMinute: Int? = null,
    val daySwitchLabel: String? = null,
    val priorityAnimationsEnabled: Boolean = true,
    val maxVisibleAllDayItems: Int = 3,
    val allDayExpanded: Boolean = false,
    val allDayExpansionProgress: Float = if (allDayExpanded) 1f else 0f,
) {
    val stableId: Long = when (hour) {
        null -> if (daySwitchLabel != null) {
            widgetStableId("day-grid-boundary:${day.toEpochDay()}:$daySwitchLabel")
        } else {
            widgetStableId("day-grid-all-day:${day.toEpochDay()}")
        }
        else -> widgetStableId("day-grid-hour:${day.toEpochDay()}:$hour")
    }

    private val normalizedMaxVisibleAllDayItems: Int =
        maxVisibleAllDayItems.coerceIn(0, 10)

    private val allDayHasOverflow: Boolean =
        allDayItems.isNotEmpty() && allDayItems.size > normalizedMaxVisibleAllDayItems

    private val collapsedVisibleAllDayLimit: Int = when {
        !allDayHasOverflow -> normalizedMaxVisibleAllDayItems
        normalizedMaxVisibleAllDayItems <= 0 -> 0
        else -> (normalizedMaxVisibleAllDayItems - 1).coerceAtLeast(0)
    }

    private val collapsedAllDayRows: Int = when {
        allDayItems.isEmpty() -> 0
        normalizedMaxVisibleAllDayItems <= 0 -> 1
        allDayHasOverflow -> normalizedMaxVisibleAllDayItems
        else -> allDayItems.size.coerceAtMost(normalizedMaxVisibleAllDayItems)
    }

    private val expandedAllDayRows: Int =
        allDayItems.size

    private val allDayOverflowLane: Int =
        if (normalizedMaxVisibleAllDayItems <= 0) 0 else collapsedVisibleAllDayLimit

    private val renderExpandedAllDayItems: Boolean =
        allDayExpanded || allDayExpansionProgress > 0.01f

    private val rowHeightDp: Float = when {
        hour == null && daySwitchLabel != null -> WIDGET_DAY_BOUNDARY_ROW_HEIGHT_DP
        hour == null -> {
            val collapsedHeight = allDayRowsHeight(collapsedAllDayRows.coerceAtLeast(1))
            val expandedHeight = allDayRowsHeight(expandedAllDayRows.coerceAtLeast(1))
            lerpFloat(
                collapsedHeight,
                expandedHeight,
                allDayExpansionProgress.coerceIn(0f, 1f),
            )
        }
        hourCount <= 0 -> 0f
        else -> hourRowHeightDp * hourCount.toFloat()
    }

    private val hasPriorityMotion: Boolean =
        priorityAnimationsEnabled &&
            (allDayItems.asSequence() + timedItems.asSequence().map { it.item })
                .any { it.hasDayPriorityMotion() }

    fun toRemoteViews(
        context: Context,
        packageName: String,
        palette: WidgetPalette,
        widthDp: Float,
        appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    ): RemoteViews {
        val views = RemoteViews(packageName, R.layout.widget_day_hour_row)
        bindInto(
            target = views,
            context = context,
            packageName = packageName,
            palette = palette,
            widthDp = widthDp,
            rootId = R.id.widget_day_hour_root,
            artId = R.id.widget_day_hour_art,
            motionId = R.id.widget_day_priority_motion,
            nowOverlayId = R.id.widget_day_now_overlay,
            overlayId = R.id.widget_day_hour_overlay,
            appWidgetId = appWidgetId,
            useImageUris = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID,
            useFillInIntents = true,
        )
        return views
    }

    fun bindInto(
        target: RemoteViews,
        context: Context,
        packageName: String,
        palette: WidgetPalette,
        widthDp: Float,
        rootId: Int,
        artId: Int,
        motionId: Int,
        nowOverlayId: Int? = null,
        overlayId: Int,
        appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
        useImageUris: Boolean = false,
        useFillInIntents: Boolean = false,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            target.setViewLayoutHeight(rootId, rowHeightDp, TypedValue.COMPLEX_UNIT_DIP)
            target.setViewLayoutHeight(artId, rowHeightDp, TypedValue.COMPLEX_UNIT_DIP)
            target.setViewLayoutHeight(overlayId, rowHeightDp, TypedValue.COMPLEX_UNIT_DIP)
        }
        target.setDayGridImage(
            context = context,
            appWidgetId = appWidgetId,
            viewId = artId,
            cacheKey = imageCacheKey(palette, widthDp, "art"),
            useImageUri = useImageUris,
            bitmapProvider = { dayGridRowBitmap(
                context = context,
                palette = palette,
                widthDp = widthDp,
                heightDp = rowHeightDp,
                omitPriorityCards = hasPriorityMotion,
            ) },
        )
        bindPriorityMotion(
            target = target,
            context = context,
            palette = palette,
            widthDp = widthDp,
            motionId = motionId,
            appWidgetId = appWidgetId,
            useImageUris = useImageUris,
        )
        bindNowLineOverlay(
            target = target,
            context = context,
            palette = palette,
            widthDp = widthDp,
            nowOverlayId = nowOverlayId,
            appWidgetId = appWidgetId,
            useImageUris = useImageUris,
        )
        if (useFillInIntents) {
            target.setOnClickFillInIntent(rootId, openDayFillInIntent())
        } else if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            target.setOnClickPendingIntent(rootId, openDayBroadcastPendingIntent(context, appWidgetId))
        } else {
            target.setOnClickFillInIntent(rootId, openDayFillInIntent())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bindTouchTargets(target, context, packageName, widthDp, overlayId, appWidgetId, useFillInIntents)
        }
    }

    private fun bindPriorityMotion(
        target: RemoteViews,
        context: Context,
        palette: WidgetPalette,
        widthDp: Float,
        motionId: Int,
        appWidgetId: Int,
        useImageUris: Boolean,
    ) {
        if (!hasPriorityMotion) {
            target.setViewVisibility(motionId, View.GONE)
            return
        }
        val motionBounds = priorityMotionBounds(widthDp) ?: run {
            target.setViewVisibility(motionId, View.GONE)
            return
        }
        val frameIds = WIDGET_AGENDA_PRIORITY_MOTION_FRAME_IDS
        val fastestPriority = (allDayItems.asSequence() + timedItems.asSequence().map { it.item })
            .filter { it.hasDayPriorityMotion() }
            .mapNotNull { it.priority }
            .minOrNull()
        val intensity = taskPriorityIntensity(fastestPriority)
        val frameIntervalMillis = priorityMotionFrameIntervalMillis(fastestPriority, intensity, frameIds.size)
        frameIds.forEachIndexed { frame, viewId ->
            target.setDayGridImage(
                context = context,
                appWidgetId = appWidgetId,
                viewId = viewId,
                cacheKey = imageCacheKey(palette, widthDp, "priority-$frame-$frameIntervalMillis"),
                useImageUri = useImageUris,
                bitmapProvider = { dayPriorityMotionBitmap(
                    context = context,
                    palette = palette,
                    widthDp = widthDp,
                    heightDp = rowHeightDp,
                    frame = frame,
                    frameCount = frameIds.size,
                    frameIntervalMillis = frameIntervalMillis,
                    motionBounds = motionBounds,
                ) },
            )
        }
        target.setInt(
            motionId,
            "setFlipInterval",
            frameIntervalMillis,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val overdraw = WIDGET_DAY_PRIORITY_OVERDRAW_DP
            target.setViewLayoutWidth(motionId, motionBounds.width() + overdraw * 2f, TypedValue.COMPLEX_UNIT_DIP)
            target.setViewLayoutHeight(motionId, motionBounds.height() + overdraw * 2f, TypedValue.COMPLEX_UNIT_DIP)
            target.setViewLayoutMargin(motionId, RemoteViews.MARGIN_LEFT, motionBounds.left - overdraw, TypedValue.COMPLEX_UNIT_DIP)
            target.setViewLayoutMargin(motionId, RemoteViews.MARGIN_TOP, motionBounds.top - overdraw, TypedValue.COMPLEX_UNIT_DIP)
        }
        target.setViewVisibility(motionId, View.VISIBLE)
    }

    private fun bindNowLineOverlay(
        target: RemoteViews,
        context: Context,
        palette: WidgetPalette,
        widthDp: Float,
        nowOverlayId: Int?,
        appWidgetId: Int,
        useImageUris: Boolean,
    ) {
        if (nowOverlayId == null) return
        if (hour == null || nowMinute == null || rowHeightDp <= 0f) {
            target.setViewVisibility(nowOverlayId, View.GONE)
            return
        }
        target.setDayGridImage(
            context = context,
            appWidgetId = appWidgetId,
            viewId = nowOverlayId,
            cacheKey = imageCacheKey(palette, widthDp, "now-line-$nowMinute"),
            useImageUri = useImageUris,
            bitmapProvider = { dayNowLineOverlayBitmap(
                context = context,
                palette = palette,
                widthDp = widthDp,
                heightDp = rowHeightDp,
            ) },
        )
        target.setViewVisibility(nowOverlayId, View.VISIBLE)
    }

    private fun imageCacheKey(
        palette: WidgetPalette,
        widthDp: Float,
        suffix: String,
    ): String =
        "$this|$palette|$widthDp|$rowHeightDp|dayRender=$WIDGET_DAY_RENDER_SIGNATURE_VERSION|timeWidth=$WIDGET_DAY_TIME_COLUMN_WIDTH_DP|rowInset=$WIDGET_DAY_ROW_SIDE_INSET_DP|dayScale=$WIDGET_DAY_PRIORITY_BITMAP_SCALE|$suffix"

    private fun renderedAllDayItemsWithLanes(): List<WidgetDayAllDayRenderItem> {
        if (allDayItems.isEmpty()) return emptyList()
        return if (renderExpandedAllDayItems) {
            val progress = allDayExpansionProgress.coerceIn(0f, 1f)
            allDayItems.mapIndexed { index, item ->
                val lane = if (allDayHasOverflow && index >= collapsedVisibleAllDayLimit) {
                    allDayOverflowLane + (index - allDayOverflowLane) * progress
                } else {
                    index.toFloat()
                }
                WidgetDayAllDayRenderItem(item, lane, index)
            }
        } else {
            allDayItems
                .take(collapsedVisibleAllDayLimit.coerceAtLeast(0))
                .mapIndexed { index, item -> WidgetDayAllDayRenderItem(item, index.toFloat(), index) }
        }
    }

    private fun allDayItemAlpha(index: Int): Float =
        if (allDayHasOverflow && renderExpandedAllDayItems && index >= collapsedVisibleAllDayLimit) {
            allDayExpansionProgress.coerceIn(0f, 1f)
        } else {
            1f
        }

    private fun RemoteViews.setDayGridImage(
        context: Context,
        appWidgetId: Int,
        viewId: Int,
        cacheKey: String,
        useImageUri: Boolean,
        bitmapProvider: () -> Bitmap,
    ) {
        if (!useImageUri) {
            val bitmap = bitmapProvider()
            WidgetPerformanceMonitor.current()?.recordBitmapRendered()
            setImageViewBitmap(viewId, bitmap)
            return
        }
        val cachedUri = runCatching {
            KgsWidgetBitmapUriStore.getIfPresent(context, appWidgetId, cacheKey)
        }.onFailure { error ->
            Log.w(TAG, "Failed to read cached Day widget image", error)
        }.getOrNull()
        if (cachedUri != null) {
            setImageViewUri(viewId, cachedUri)
            return
        }
        val bitmap = bitmapProvider()
        WidgetPerformanceMonitor.current()?.recordBitmapRendered()
        val uri = runCatching {
            KgsWidgetBitmapUriStore.put(context, appWidgetId, cacheKey, bitmap)
        }.onFailure { error ->
            Log.w(TAG, "Failed to cache Day widget image; falling back to an inline bitmap", error)
        }.getOrNull()
        if (uri != null) {
            setImageViewUri(viewId, uri)
            bitmap.recycle()
        } else {
            setImageViewBitmap(viewId, bitmap)
        }
    }

    private fun priorityMotionBounds(widthDp: Float): RectF? {
        val rects = if (hour == null) {
            renderedAllDayItemsWithLanes().mapNotNull { renderItem ->
                val item = renderItem.item
                if (!item.hasDayPriorityMotion()) return@mapNotNull null
                val top = WIDGET_DAY_ALL_DAY_TOP_PADDING_DP + renderItem.lane * WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP
                RectF(
                    WIDGET_DAY_TIME_COLUMN_WIDTH_DP + WIDGET_DAY_GRID_GAP_DP,
                    top,
                    widthDp - 2f,
                    top + WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP,
                )
            }
        } else {
            val gridLeft = WIDGET_DAY_ROW_SIDE_INSET_DP + WIDGET_DAY_TIME_COLUMN_WIDTH_DP + WIDGET_DAY_GRID_GAP_DP
            val gridRight = widthDp - WIDGET_DAY_ROW_SIDE_INSET_DP - 2f
            val gridWidth = (gridRight - gridLeft).coerceAtLeast(1f)
            val hourStart = hour * 60
            val hourEnd = (hour + hourCount.coerceAtLeast(1)) * 60
            timedItems.mapNotNull { layout ->
                if (!layout.item.hasDayPriorityMotion()) return@mapNotNull null
                val topMinute = max(layout.startMinute, hourStart)
                val bottomMinute = min(layout.endMinute, hourEnd)
                if (bottomMinute <= topMinute) return@mapNotNull null
                val laneWidth = gridWidth / layout.laneCount.coerceAtLeast(1)
                val left = gridLeft + laneWidth * layout.lane + 1f
                val top = (topMinute - hourStart) / 60f * hourRowHeightDp + 1f
                val bottom = (bottomMinute - hourStart) / 60f * hourRowHeightDp - 1f
                RectF(
                    left,
                    top.coerceIn(0f, rowHeightDp),
                    left + laneWidth - 3f,
                    bottom.coerceIn(0f, rowHeightDp).coerceAtLeast(top + WIDGET_DAY_CARD_MIN_HEIGHT_DP),
                )
            }
        }
        val first = rects.firstOrNull() ?: return null
        return RectF(first).also { result ->
            rects.drop(1).forEach(result::union)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun bindTouchTargets(
        parent: RemoteViews,
        context: Context,
        packageName: String,
        widthDp: Float,
        overlayId: Int,
        appWidgetId: Int,
        useFillInIntents: Boolean,
    ) {
        parent.removeAllViews(overlayId)
        val targets = when (hour) {
            null -> allDayTouchTargets(widthDp)
            else -> timedTouchTargets(widthDp, hour)
        }
        targets.forEach { target ->
            val touch = RemoteViews(packageName, R.layout.widget_day_card_touch)
            touch.setViewLayoutWidth(R.id.widget_day_card_touch_root, target.widthDp, TypedValue.COMPLEX_UNIT_DIP)
            touch.setViewLayoutHeight(R.id.widget_day_card_touch_root, target.heightDp, TypedValue.COMPLEX_UNIT_DIP)
            touch.setViewLayoutMargin(R.id.widget_day_card_touch_root, RemoteViews.MARGIN_LEFT, target.leftDp, TypedValue.COMPLEX_UNIT_DIP)
            touch.setViewLayoutMargin(R.id.widget_day_card_touch_root, RemoteViews.MARGIN_TOP, target.topDp, TypedValue.COMPLEX_UNIT_DIP)
            touch.setViewVisibility(
                R.id.widget_day_card_checkbox_touch,
                if (target.item.isTask && !target.item.taskResourceHref.isNullOrBlank()) View.VISIBLE else View.GONE,
            )
            if (useFillInIntents) {
                touch.setOnClickFillInIntent(R.id.widget_day_card_click, target.item.openFillInIntent(day))
                touch.setOnClickFillInIntent(R.id.widget_day_card_touch_root, target.item.openFillInIntent(day))
                if (target.item.isTask && !target.item.taskResourceHref.isNullOrBlank()) {
                    touch.setOnClickFillInIntent(R.id.widget_day_card_checkbox_touch, target.item.toggleFillInIntent())
                }
            } else if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val openPendingIntent = target.item.openBroadcastPendingIntent(context, appWidgetId, day)
                touch.setOnClickPendingIntent(R.id.widget_day_card_click, openPendingIntent)
                touch.setOnClickPendingIntent(R.id.widget_day_card_touch_root, openPendingIntent)
                if (target.item.isTask && !target.item.taskResourceHref.isNullOrBlank()) {
                    touch.setOnClickPendingIntent(R.id.widget_day_card_checkbox_touch, target.item.toggleBroadcastPendingIntent(context, appWidgetId))
                }
            } else {
                touch.setOnClickFillInIntent(R.id.widget_day_card_click, target.item.openFillInIntent(day))
                touch.setOnClickFillInIntent(R.id.widget_day_card_touch_root, target.item.openFillInIntent(day))
                if (target.item.isTask && !target.item.taskResourceHref.isNullOrBlank()) {
                    touch.setOnClickFillInIntent(R.id.widget_day_card_checkbox_touch, target.item.toggleFillInIntent())
                }
            }
            parent.addView(overlayId, touch)
        }
        if (hour == null && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && !useFillInIntents) {
            val showToggle = allDayHasOverflow || allDayExpanded
            if (showToggle) {
                parent.addAllDayToggleTarget(
                    packageName = packageName,
                    context = context,
                    appWidgetId = appWidgetId,
                    overlayId = overlayId,
                    leftDp = 0f,
                    topDp = 0f,
                    widthDp = WIDGET_DAY_TIME_COLUMN_WIDTH_DP,
                    heightDp = rowHeightDp,
                )
            }
            if (allDayHasOverflow && !renderExpandedAllDayItems) {
                val gridLeft = WIDGET_DAY_TIME_COLUMN_WIDTH_DP + WIDGET_DAY_GRID_GAP_DP
                val gridWidth = (widthDp - gridLeft - 2f).coerceAtLeast(WIDGET_TASK_MIN_CARD_WIDTH_DP)
                parent.addAllDayToggleTarget(
                    packageName = packageName,
                    context = context,
                    appWidgetId = appWidgetId,
                    overlayId = overlayId,
                    leftDp = gridLeft,
                    topDp = WIDGET_DAY_ALL_DAY_TOP_PADDING_DP + allDayOverflowLane * WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP,
                    widthDp = gridWidth,
                    heightDp = WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP.toFloat(),
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun RemoteViews.addAllDayToggleTarget(
        packageName: String,
        context: Context,
        appWidgetId: Int,
        overlayId: Int,
        leftDp: Float,
        topDp: Float,
        widthDp: Float,
        heightDp: Float,
    ) {
        val toggle = RemoteViews(packageName, R.layout.widget_day_all_day_toggle_touch)
        toggle.setViewLayoutWidth(R.id.widget_day_all_day_toggle_root, widthDp, TypedValue.COMPLEX_UNIT_DIP)
        toggle.setViewLayoutHeight(R.id.widget_day_all_day_toggle_root, heightDp, TypedValue.COMPLEX_UNIT_DIP)
        toggle.setViewLayoutMargin(R.id.widget_day_all_day_toggle_root, RemoteViews.MARGIN_LEFT, leftDp, TypedValue.COMPLEX_UNIT_DIP)
        toggle.setViewLayoutMargin(R.id.widget_day_all_day_toggle_root, RemoteViews.MARGIN_TOP, topDp, TypedValue.COMPLEX_UNIT_DIP)
        toggle.setOnClickPendingIntent(R.id.widget_day_all_day_toggle_root, allDayTogglePendingIntent(context, appWidgetId))
        addView(overlayId, toggle)
    }

    private fun allDayTouchTargets(widthDp: Float): List<WidgetDayTouchTarget> {
        val gridLeft = WIDGET_DAY_TIME_COLUMN_WIDTH_DP + WIDGET_DAY_GRID_GAP_DP
        val gridWidth = (widthDp - gridLeft - 2f).coerceAtLeast(WIDGET_TASK_MIN_CARD_WIDTH_DP)
        return renderedAllDayItemsWithLanes().map { renderItem ->
            WidgetDayTouchTarget(
                item = renderItem.item,
                leftDp = gridLeft,
                topDp = WIDGET_DAY_ALL_DAY_TOP_PADDING_DP + renderItem.lane * WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP,
                widthDp = gridWidth,
                heightDp = WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP.toFloat(),
            )
        }
    }

    private fun timedTouchTargets(widthDp: Float, hour: Int): List<WidgetDayTouchTarget> {
        val gridLeft = WIDGET_DAY_ROW_SIDE_INSET_DP + WIDGET_DAY_TIME_COLUMN_WIDTH_DP + WIDGET_DAY_GRID_GAP_DP
        val gridRight = widthDp - WIDGET_DAY_ROW_SIDE_INSET_DP - 2f
        val gridWidth = (gridRight - gridLeft).coerceAtLeast(WIDGET_TASK_MIN_CARD_WIDTH_DP)
        val hourStart = hour * 60
        val hourEnd = (hour + hourCount.coerceAtLeast(1)) * 60
        return timedItems.mapNotNull { layout ->
            val topMinute = max(layout.startMinute, hourStart)
            val bottomMinute = min(layout.endMinute, hourEnd)
            if (bottomMinute <= topMinute) return@mapNotNull null
            val laneWidth = gridWidth / layout.laneCount.coerceAtLeast(1)
            val left = gridLeft + laneWidth * layout.lane + 1f
            val top = (topMinute - hourStart) / 60f * hourRowHeightDp + 1f
            val bottom = (bottomMinute - hourStart) / 60f * hourRowHeightDp - 1f
            WidgetDayTouchTarget(
                item = layout.item,
                leftDp = left,
                topDp = top.coerceIn(0f, rowHeightDp),
                widthDp = (laneWidth - 3f).coerceAtLeast(WIDGET_TASK_MIN_CARD_WIDTH_DP),
                heightDp = (bottom - top).coerceAtLeast(WIDGET_DAY_CARD_MIN_TOUCH_HEIGHT_DP),
            )
        }
    }

    private fun dayGridRowBitmap(
        context: Context,
        palette: WidgetPalette,
        widthDp: Float,
        heightDp: Float,
        omitPriorityCards: Boolean,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap(
            (widthDp * density).roundToInt().coerceAtLeast(1),
            (heightDp * density).roundToInt().coerceAtLeast(1),
            if (hour == null) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.rootBackgroundColor)
        canvas.scale(density, density)
        if (hour == null && daySwitchLabel != null) {
            drawDayBoundaryRow(
                canvas = canvas,
                palette = palette,
                widthDp = widthDp,
                heightDp = heightDp,
                label = daySwitchLabel,
            )
        } else if (hour == null) {
            drawAllDayRow(
                canvas = canvas,
                palette = palette,
                widthDp = widthDp,
                omitPriorityCards = omitPriorityCards,
            )
        } else {
            drawHourRow(
                canvas = canvas,
                palette = palette,
                widthDp = widthDp,
                heightDp = heightDp,
                hour = hour,
                omitPriorityCards = omitPriorityCards,
            )
        }
        return bitmap
    }

    private fun dayPriorityMotionBitmap(
        context: Context,
        palette: WidgetPalette,
        widthDp: Float,
        heightDp: Float,
        frame: Int,
        frameCount: Int,
        frameIntervalMillis: Int,
        motionBounds: RectF,
    ): Bitmap {
        val bitmapScale = context.resources.displayMetrics.density * WIDGET_DAY_PRIORITY_BITMAP_SCALE
        val overdraw = WIDGET_DAY_PRIORITY_OVERDRAW_DP
        val bitmap = Bitmap.createBitmap(
            ((motionBounds.width() + overdraw * 2f) * bitmapScale).roundToInt().coerceAtLeast(1),
            ((motionBounds.height() + overdraw * 2f) * bitmapScale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.scale(bitmapScale, bitmapScale)
        canvas.translate(overdraw - motionBounds.left, overdraw - motionBounds.top)
        if (hour == null) {
            drawAllDayRow(
                canvas = canvas,
                palette = palette,
                widthDp = widthDp,
                priorityOnly = true,
                frame = frame,
                frameCount = frameCount,
                frameIntervalMillis = frameIntervalMillis,
                priorityBitmapScale = bitmapScale,
            )
        } else {
            drawHourRow(
                canvas = canvas,
                palette = palette,
                widthDp = widthDp,
                heightDp = heightDp,
                hour = hour,
                priorityOnly = true,
                frame = frame,
                frameCount = frameCount,
                frameIntervalMillis = frameIntervalMillis,
                priorityBitmapScale = bitmapScale,
            )
        }
        return bitmap
    }

    private fun drawAllDayRow(
        canvas: Canvas,
        palette: WidgetPalette,
        widthDp: Float,
        omitPriorityCards: Boolean = false,
        priorityOnly: Boolean = false,
        frame: Int = 0,
        frameCount: Int = 1,
        frameIntervalMillis: Int = 0,
        priorityBitmapScale: Float = 1f,
    ) {
        val gridLeft = WIDGET_DAY_TIME_COLUMN_WIDTH_DP + WIDGET_DAY_GRID_GAP_DP
        if (!priorityOnly) {
            drawAllDayExpansionArrow(canvas, palette)
        }
        renderedAllDayItemsWithLanes().forEach { renderItem ->
            val item = renderItem.item
            val animated = item.hasDayPriorityMotion()
            if ((omitPriorityCards && animated) || (priorityOnly && !animated)) return@forEach
            val top = WIDGET_DAY_ALL_DAY_TOP_PADDING_DP + renderItem.lane * WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP
            val rect = RectF(
                gridLeft,
                top,
                widthDp - 2f,
                top + WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP,
            )
            drawDayGridCard(
                canvas = canvas,
                palette = palette,
                item = item,
                rect = rect,
                heightDp = WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP.toFloat(),
                showMeta = false,
                priorityMotion = if (priorityOnly) {
                    item.dayPriorityMotion(frame, frameCount, frameIntervalMillis, priorityBitmapScale)
                } else {
                    null
                },
                alpha = allDayItemAlpha(renderItem.sourceIndex),
            )
        }
        if (!priorityOnly && allDayHasOverflow && !renderExpandedAllDayItems) {
            drawAllDayOverflowChip(
                canvas = canvas,
                palette = palette,
                rect = RectF(
                    gridLeft,
                    WIDGET_DAY_ALL_DAY_TOP_PADDING_DP + allDayOverflowLane * WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP,
                    widthDp - 2f,
                    WIDGET_DAY_ALL_DAY_TOP_PADDING_DP + allDayOverflowLane * WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP + WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP,
                ),
            )
        }
    }

    private fun drawAllDayExpansionArrow(canvas: Canvas, palette: WidgetPalette) {
        if (!allDayHasOverflow && !allDayExpanded) return
        val centerX = WIDGET_DAY_TIME_COLUMN_WIDTH_DP / 2f
        val centerY = (rowHeightDp - 16f).coerceAtLeast(13f)
        val restore = canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(180f * allDayExpansionProgress.coerceIn(0f, 1f))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.text.withAlpha(0.82f)
            style = Paint.Style.STROKE
            strokeWidth = 1.75f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path().apply {
            moveTo(-4.8f, -1.6f)
            lineTo(0f, 2.15f)
            lineTo(4.8f, -1.6f)
        }
        canvas.drawPath(path, paint)
        canvas.restoreToCount(restore)
    }

    private fun drawAllDayOverflowChip(
        canvas: Canvas,
        palette: WidgetPalette,
        rect: RectF,
    ) {
        if (rect.width() <= 1f || rect.height() <= 1f) return
        val dark = palette.rootBackgroundColor.isDarkColor()
        val frontColor = if (dark) 0xFF7E8A96.toInt() else 0xFFA4AFBA.toInt()
        val rearColor = if (dark) 0xFF687683.toInt() else 0xFFB4BEC8.toInt()
        val frontHeight = min(20f, rect.height())
        val radius = 8f
        val rearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rearColor
            style = Paint.Style.FILL
        }
        val rearTop = rect.top + 3f
        canvas.drawRoundRect(
            RectF(rect.left + 4f, rearTop, rect.right - 4f, min(rect.bottom, rearTop + 18f)),
            radius,
            radius,
            rearPaint,
        )
        rearPaint.color = rearColor.withAlpha(0.92f)
        val secondRearTop = rect.top + 5f
        canvas.drawRoundRect(
            RectF(rect.left + 7f, secondRearTop, rect.right - 7f, min(rect.bottom, secondRearTop + 18f)),
            radius,
            radius,
            rearPaint,
        )
        val frontRect = RectF(rect.left, rect.top, rect.right, rect.top + frontHeight)
        val frontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = frontColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(frontRect, radius, radius, frontPaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (frontColor.isDarkColor()) 0xFFFFFFFF.toInt() else 0xFF1C1A18.toInt()
            textSize = 13f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val centerY = frontRect.centerY() - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
        canvas.drawText("\u2022\u2022\u2022", frontRect.centerX(), centerY, textPaint)
    }

    private fun drawDayBoundaryRow(
        canvas: Canvas,
        palette: WidgetPalette,
        widthDp: Float,
        heightDp: Float,
        label: String,
    ) {
        val dark = palette.rootBackgroundColor.isDarkColor()
        val centerY = heightDp / 2f
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent.withAlpha(if (dark) 0.16f else 0.1f)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, centerY - 2f, widthDp, centerY + 2f, bandPaint)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent.withAlpha(if (dark) 0.82f else 0.6f)
            strokeWidth = 1.2f
            strokeCap = Paint.Cap.ROUND
            pathEffect = DashPathEffect(floatArrayOf(4.5f, 3.5f), 0f)
        }
        canvas.drawLine(0f, centerY, widthDp, centerY, linePaint)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent.withAlpha(if (dark) 0.92f else 0.78f)
            textSize = 8f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(
            label.shortDayGutterLabel(),
            widthDp / 2f,
            (centerY - 3.2f).coerceAtLeast(6f),
            labelPaint,
        )
    }

    private fun drawHourRow(
        canvas: Canvas,
        palette: WidgetPalette,
        widthDp: Float,
        heightDp: Float,
        hour: Int,
        omitPriorityCards: Boolean = false,
        priorityOnly: Boolean = false,
        frame: Int = 0,
        frameCount: Int = 1,
        frameIntervalMillis: Int = 0,
        priorityBitmapScale: Float = 1f,
    ) {
        val contentLeft = WIDGET_DAY_ROW_SIDE_INSET_DP
        val gridLeft = contentLeft + WIDGET_DAY_TIME_COLUMN_WIDTH_DP + WIDGET_DAY_GRID_GAP_DP
        val gridRight = widthDp - WIDGET_DAY_ROW_SIDE_INSET_DP - 2f
        if (!priorityOnly) {
            val dark = palette.rootBackgroundColor.isDarkColor()
            val slotColor = if (dark) 0xFF182534.toInt() else 0xFFFAFCFF.toInt()
            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.muted
                textSize = 8.2f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = slotColor
                style = Paint.Style.FILL
            }
            repeat(hourCount.coerceAtLeast(1)) { index ->
                val slotTop = index * hourRowHeightDp
                canvas.drawText(
                    "%02d:00".format(hour + index),
                    contentLeft + WIDGET_DAY_TIME_COLUMN_WIDTH_DP / 2f,
                    slotTop + 11f,
                    timePaint,
                )
                canvas.drawRoundRect(
                    RectF(
                        gridLeft,
                        slotTop + 2f,
                        gridRight,
                        slotTop + hourRowHeightDp - 2f,
                    ),
                    10f,
                    10f,
                    slotPaint,
                )
            }
        }

        val hourStart = hour * 60
        val hourEnd = (hour + hourCount.coerceAtLeast(1)) * 60
        val gridWidth = (gridRight - gridLeft).coerceAtLeast(1f)
        timedItems.forEach { layout ->
            val animated = layout.item.hasDayPriorityMotion()
            if ((omitPriorityCards && animated) || (priorityOnly && !animated)) return@forEach
            val topMinute = max(layout.startMinute, hourStart)
            val bottomMinute = min(layout.endMinute, hourEnd)
            if (bottomMinute <= topMinute) return@forEach
            val laneWidth = gridWidth / layout.laneCount.coerceAtLeast(1)
            val left = gridLeft + laneWidth * layout.lane + 1f
            val top = (topMinute - hourStart) / 60f * hourRowHeightDp + 1f
            val bottom = (bottomMinute - hourStart) / 60f * hourRowHeightDp - 1f
            val rect = RectF(
                left,
                top.coerceIn(0f, heightDp),
                left + laneWidth - 3f,
                bottom.coerceIn(0f, heightDp).coerceAtLeast(top + WIDGET_DAY_CARD_MIN_HEIGHT_DP),
            )
            drawDayGridCard(
                canvas = canvas,
                palette = palette,
                item = layout.item,
                rect = rect,
                heightDp = rect.height(),
                showMeta = rect.height() >= 40f,
                priorityMotion = if (priorityOnly) {
                    layout.item.dayPriorityMotion(frame, frameCount, frameIntervalMillis, priorityBitmapScale)
                } else {
                    null
                },
            )
        }

    }

    private fun dayNowLineOverlayBitmap(
        context: Context,
        palette: WidgetPalette,
        widthDp: Float,
        heightDp: Float,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap(
            (widthDp * density).roundToInt().coerceAtLeast(1),
            (heightDp * density).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        val currentHour = hour ?: return bitmap
        val currentMinute = nowMinute ?: return bitmap
        val lineY = (currentMinute - currentHour * 60) / 60f * hourRowHeightDp
        if (lineY in 0f..heightDp) {
            val contentLeft = WIDGET_DAY_ROW_SIDE_INSET_DP
            val contentRight = widthDp - WIDGET_DAY_ROW_SIDE_INSET_DP - 2f
            drawDayNowLine(canvas, palette, contentRight, contentLeft, lineY, heightDp)
        }
        return bitmap
    }

    private fun drawDayNowLine(
        canvas: Canvas,
        palette: WidgetPalette,
        contentRight: Float,
        contentLeft: Float,
        lineY: Float,
        contentHeight: Float,
    ) {
        val dark = palette.rootBackgroundColor.isDarkColor()
        val indicatorColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF1D1511.toInt()
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = indicatorColor.withAlpha(if (dark) 0.86f else 0.72f)
            style = Paint.Style.FILL
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = indicatorColor.withAlpha(if (dark) 0.62f else 0.52f)
            strokeWidth = 1.2f
            strokeCap = Paint.Cap.ROUND
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0xFF151515.toInt() else 0xFFFFFFFF.toInt()
            textSize = 9.3f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val pillCenterY = lineY.coerceIn(9f, contentHeight - 9f)
        val pill = RectF(
            contentLeft + 2f,
            pillCenterY - 9f,
            contentLeft + WIDGET_DAY_TIME_COLUMN_WIDTH_DP - 2f,
            pillCenterY + 9f,
        )
        canvas.drawRoundRect(pill, 9f, 9f, pillPaint)
        val currentMinute = nowMinute ?: 0
        canvas.drawText(
            LocalTime.of((currentMinute / 60).coerceIn(0, 23), currentMinute.rem(60)).format(DateTimeFormatter.ofPattern("HH:mm")),
            pill.centerX(),
            pillCenterY + 3.8f,
            textPaint,
        )
        canvas.drawLine(pill.right, pillCenterY, contentRight, pillCenterY, linePaint)
    }

    fun containsHour(targetHour: Int): Boolean =
        hour != null && targetHour in hour until (hour + hourCount.coerceAtLeast(1))

    private fun drawDayGridCard(
        canvas: Canvas,
        palette: WidgetPalette,
        item: WidgetDayItem,
        rect: RectF,
        heightDp: Float,
        showMeta: Boolean,
        priorityMotion: WidgetDayPriorityMotion? = null,
        alpha: Float = 1f,
    ) {
        if (rect.width() <= 1f || rect.height() <= 1f) return
        val alphaRestore = if (alpha < 0.999f) {
            canvas.saveLayer(
                rect.left - WIDGET_DAY_PRIORITY_OVERDRAW_DP,
                rect.top - WIDGET_DAY_PRIORITY_OVERDRAW_DP,
                rect.right + WIDGET_DAY_PRIORITY_OVERDRAW_DP,
                rect.bottom + WIDGET_DAY_PRIORITY_OVERDRAW_DP,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt() },
            )
        } else {
            -1
        }
        val cardColor = if (item.completed) item.color.blendWith(palette.rootBackgroundColor, 0.48f) else item.color
        val contentColor = if (cardColor.isDarkColor()) 0xFFFFFFFF.toInt() else 0xFF1C1A18.toInt()
        priorityMotion?.let { motion ->
            val scaledWidth = rect.width() * motion.scale
            val scaledHeight = rect.height() * motion.scale
            val glowRect = RectF(
                rect.centerX() - scaledWidth / 2f + motion.translationX - motion.glowSpread / 2f,
                rect.centerY() - scaledHeight / 2f + motion.translationY - motion.glowSpread / 2f,
                rect.centerX() + scaledWidth / 2f + motion.translationX + motion.glowSpread / 2f,
                rect.centerY() + scaledHeight / 2f + motion.translationY + motion.glowSpread / 2f,
            )
            canvas.drawRoundRect(
                glowRect,
                WIDGET_DAY_CARD_RADIUS_DP + motion.glowSpread / 3f,
                WIDGET_DAY_CARD_RADIUS_DP + motion.glowSpread / 3f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = cardColor.withAlpha(motion.glowAlpha)
                    style = Paint.Style.FILL
                },
            )
        }
        val restore = canvas.save()
        priorityMotion?.let { motion ->
            canvas.translate(motion.translationX, motion.translationY)
            canvas.scale(motion.scale, motion.scale, rect.centerX(), rect.centerY())
        }
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, WIDGET_DAY_CARD_RADIUS_DP, WIDGET_DAY_CARD_RADIUS_DP, backgroundPaint)
        val titleScale = if (item.isTask) {
            ((heightDp - 18f) / 18f).coerceIn(0f, 1f)
        } else {
            ((heightDp - 13f) / 20f).coerceIn(0f, 1f)
        }
        val compactVerticalPadding = 2.6f + titleScale * 1.4f
        val contentTop = rect.top + compactVerticalPadding
        val textStart = rect.left + if (item.isTask) 23.5f else 7f
        if (item.isTask) {
            val checkboxRadius = 5.1f
            val checkboxStrokeWidth = 1.25f
            val topAlignedCenterY = contentTop + 7.9f
            val visualRadius = checkboxRadius + checkboxStrokeWidth / 2f
            if (rect.height() >= visualRadius * 2f) {
                val topSpace = topAlignedCenterY - visualRadius - rect.top
                val bottomSpace = rect.bottom - topAlignedCenterY - visualRadius
                val checkboxCenterY = if (bottomSpace < topSpace) rect.centerY() else topAlignedCenterY
                drawDayStatusGlyph(
                    canvas = canvas,
                    statusGlyph = item.statusGlyph,
                    tint = contentColor,
                    centerX = rect.left + 13.4f,
                    centerY = checkboxCenterY,
                    radius = checkboxRadius,
                    strokeWidth = checkboxStrokeWidth,
                )
            }
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = contentColor.withAlpha(if (item.completed) 0.62f else 1f)
            textSize = 12f * (0.9f + titleScale * 0.1f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val titleMetrics = titlePaint.fontMetrics
        val titleHeight = titleMetrics.descent - titleMetrics.ascent
        if (heightDp >= titleHeight) {
            val topAlignedBaseline = contentTop - titleMetrics.ascent
            val topSpace = contentTop - rect.top
            val bottomSpace = rect.bottom - (topAlignedBaseline + titleMetrics.descent)
            val titleBaseline = if (bottomSpace < topSpace) {
                rect.centerY() - (titleMetrics.ascent + titleMetrics.descent) / 2f
            } else {
                topAlignedBaseline
            }
            drawDayFadingText(
                canvas = canvas,
                text = item.title,
                paint = titlePaint,
                startX = textStart,
                baseline = titleBaseline,
                rightEdge = rect.right,
            )
            if (showMeta && !item.location.isNullOrBlank()) {
                val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = contentColor.withAlpha(0.82f)
                    textSize = 10.6f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                }
                val metaBaseline = titleBaseline + 12f
                if (metaBaseline + metaPaint.fontMetrics.descent <= rect.bottom - 2f) {
                    drawDayFadingText(
                        canvas = canvas,
                        text = item.location,
                        paint = metaPaint,
                        startX = textStart,
                        baseline = metaBaseline,
                        rightEdge = rect.right,
                    )
                }
            }
        }
        canvas.restoreToCount(restore)
        if (alphaRestore >= 0) {
            canvas.restoreToCount(alphaRestore)
        }
    }

    private fun drawDayFadingText(
        canvas: Canvas,
        text: String,
        paint: Paint,
        startX: Float,
        baseline: Float,
        rightEdge: Float,
    ) {
        val availableWidth = (rightEdge - startX).coerceAtLeast(0f)
        if (availableWidth <= 0f || text.isEmpty()) return
        val metrics = paint.fontMetrics
        val top = baseline + metrics.ascent - 1f
        val bottom = baseline + metrics.descent + 1f
        val restore = canvas.saveLayer(startX, top, rightEdge, bottom, null)
        canvas.clipRect(startX, top, rightEdge, bottom)
        canvas.drawText(text, startX, baseline, paint)
        val fadeWidth = min(WIDGET_DAY_TITLE_FADE_WIDTH_DP, availableWidth * 0.36f)
        if (fadeWidth > 0f) {
            canvas.drawRect(
                rightEdge - fadeWidth,
                top,
                rightEdge,
                bottom,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        rightEdge - fadeWidth,
                        0f,
                        rightEdge,
                        0f,
                        0xFFFFFFFF.toInt(),
                        0x00FFFFFF,
                        Shader.TileMode.CLAMP,
                    )
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                },
            )
        }
        canvas.restoreToCount(restore)
    }

    private fun drawDayStatusGlyph(
        canvas: Canvas,
        statusGlyph: String,
        tint: Int,
        centerX: Float,
        centerY: Float,
        radius: Float,
        strokeWidth: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(centerX, centerY, radius, paint)
        when (statusGlyph) {
            "\u2713" -> {
                val path = Path().apply {
                    moveTo(centerX - radius * 0.48f, centerY)
                    lineTo(centerX - radius * 0.1f, centerY + radius * 0.38f)
                    lineTo(centerX + radius * 0.56f, centerY - radius * 0.48f)
                }
                canvas.drawPath(path, paint)
            }
            "\u25D0" -> {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(centerX, centerY, radius * 0.34f, paint)
            }
            "\u00D7" -> {
                canvas.drawLine(centerX - radius * 0.44f, centerY - radius * 0.44f, centerX + radius * 0.44f, centerY + radius * 0.44f, paint)
                canvas.drawLine(centerX + radius * 0.44f, centerY - radius * 0.44f, centerX - radius * 0.44f, centerY + radius * 0.44f, paint)
            }
        }
    }

    private fun WidgetDayItem.openFillInIntent(date: LocalDate): Intent {
        val intent = Intent()
        intent.putExtra(EXTRA_COLLECTION_ACTION, COLLECTION_ACTION_OPEN)
        intent.putExtra(EXTRA_WIDGET_KIND, if (isTask) KgsWidgetKind.Tasks.name else KgsWidgetKind.Day.name)
        intent.putExtra(EXTRA_WIDGET_DATE, date.toString())
        if (isTask && !taskResourceHref.isNullOrBlank()) {
            intent.putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_OPEN_TASK)
            intent.putExtra(EXTRA_WIDGET_TASK_UID, taskResourceHref)
        } else if (!eventResourceHref.isNullOrBlank()) {
            intent.putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_OPEN_EVENT)
            intent.putExtra(EXTRA_WIDGET_EVENT_UID, eventResourceHref)
        }
        intent.data = Uri.parse("kgs-calendar://widget-day-grid-open/$stableKey")
        return intent
    }

    private fun WidgetDayItem.toggleFillInIntent(): Intent {
        val intent = Intent()
        intent.putExtra(EXTRA_COLLECTION_ACTION, COLLECTION_ACTION_TOGGLE_TASK)
        intent.putExtra(KgsWidgetProvider.EXTRA_TASK_RESOURCE_HREF, taskResourceHref)
        intent.data = Uri.parse("kgs-calendar://widget-day-grid-toggle/$stableKey")
        return intent
    }

    private fun WidgetDayItem.openBroadcastPendingIntent(context: Context, appWidgetId: Int, date: LocalDate): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            widgetRequestCode("day-grid-open:$appWidgetId:$stableKey"),
            openFillInIntent(date).apply {
                action = KgsWidgetProvider.ACTION_COLLECTION_CLICK
                setClass(context, KgsWidgetActionReceiver::class.java)
                data = Uri.parse("kgs-calendar://widget-day-grid-open/$appWidgetId/$stableKey")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun WidgetDayItem.toggleBroadcastPendingIntent(context: Context, appWidgetId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            widgetRequestCode("day-grid-toggle:$appWidgetId:$stableKey"),
            toggleFillInIntent().apply {
                action = KgsWidgetProvider.ACTION_COLLECTION_CLICK
                setClass(context, KgsWidgetActionReceiver::class.java)
                data = Uri.parse("kgs-calendar://widget-day-grid-toggle/$appWidgetId/$stableKey")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openDayFillInIntent(): Intent =
        Intent().apply {
            putExtra(EXTRA_COLLECTION_ACTION, COLLECTION_ACTION_OPEN)
            data = Uri.parse("kgs-calendar://widget-day-grid-day/${day.toEpochDay()}/${hour ?: "all-day"}")
        }

    private fun openDayBroadcastPendingIntent(context: Context, appWidgetId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            widgetRequestCode("day-grid-day:$appWidgetId:${day.toEpochDay()}:${hour ?: "all-day"}"),
            openDayFillInIntent().apply {
                action = KgsWidgetProvider.ACTION_COLLECTION_CLICK
                setClass(context, KgsWidgetActionReceiver::class.java)
                data = Uri.parse("kgs-calendar://widget-day-grid-day/$appWidgetId/${day.toEpochDay()}/${hour ?: "all-day"}")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun allDayTogglePendingIntent(context: Context, appWidgetId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            widgetRequestCode("day-all-day-toggle:$appWidgetId"),
            Intent(context, KgsWidgetKind.Day.providerClass).apply {
                action = KgsWidgetProvider.ACTION_DAY_TOGGLE_ALL_DAY
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("kgs-calendar://widget-day/all-day-toggle/$appWidgetId")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun String.shortDayGutterLabel(): String =
        if (length > 7) "${take(5)}." else this
}

private data class WidgetDayAllDayRenderItem(
    val item: WidgetDayItem,
    val lane: Float,
    val sourceIndex: Int,
)

private data class WidgetDayTouchTarget(
    val item: WidgetDayItem,
    val leftDp: Float,
    val topDp: Float,
    val widthDp: Float,
    val heightDp: Float,
)

private data class WidgetDayPriorityMotion(
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val glowSpread: Float,
    val glowAlpha: Float,
)

internal fun WidgetDayItem.hasDayPriorityMotion(): Boolean =
    isTask && !completed && taskPriorityIntensity(priority) > 0f

private fun WidgetDayItem.dayPriorityMotion(
    frame: Int,
    frameCount: Int,
    frameIntervalMillis: Int,
    bitmapScale: Float,
): WidgetDayPriorityMotion {
    val intensity = taskPriorityIntensity(priority)
    val elapsedMillis = (frame.coerceIn(0, frameCount.coerceAtLeast(1) - 1) + 0.5f) *
        frameIntervalMillis.coerceAtLeast(1)
    val pulseDuration = (1050 - intensity * 420f).roundToInt().coerceAtLeast(520)
    val fullPulseDuration = pulseDuration * 2f
    val cycleFraction = (elapsedMillis % fullPulseDuration) / fullPulseDuration
    val halfFraction = if (cycleFraction < 0.5f) cycleFraction * 2f else (cycleFraction - 0.5f) * 2f
    val eased = motionStandardEasing(halfFraction)
    val pulse = if (cycleFraction < 0.5f) eased else 1f - eased
    val shakePhase = (((frame + 0.5f) * 42f) % 210f) / 210f
    val shake = if (priority == 1) {
        cos(shakePhase.toDouble() * PI * 6.0).toFloat() * 1.05f / bitmapScale.coerceAtLeast(0.01f)
    } else {
        0f
    }
    return WidgetDayPriorityMotion(
        translationX = shake,
        translationY = (pulse - 0.5f) * -2f * intensity,
        scale = 1f + intensity * 0.018f * pulse,
        glowSpread = 8f * intensity * (0.45f + pulse),
        glowAlpha = 0.18f * intensity * (0.45f + 0.55f * pulse),
    )
}

private fun widgetStableId(value: String): Long =
    value.fold(1125899906842597L) { acc, char -> acc * 31 + char.code }

internal fun widgetRequestCode(value: String): Int =
    (widgetStableId(value) and 0x3FFFFFFF).toInt()

internal fun layoutWidgetDayTimedItems(items: List<WidgetDayTimedItem>): List<WidgetDayTimedLayout> {
    val pending = items.sortedWith(compareBy<WidgetDayTimedItem> { it.startMinute }.thenBy { it.endMinute }.thenBy { it.item.title })
    val result = mutableListOf<WidgetDayTimedLayout>()
    val group = mutableListOf<WidgetDayTimedItem>()
    var groupEnd = Int.MIN_VALUE

    fun flushGroup() {
        if (group.isEmpty()) return
        val laneEnds = mutableListOf<Int>()
        val assigned = group.map { item ->
            val lane = laneEnds.indexOfFirst { it <= item.startMinute }.let { index ->
                if (index >= 0) index else laneEnds.size.also { laneEnds.add(Int.MIN_VALUE) }
            }
            laneEnds[lane] = item.endMinute
            item to lane
        }
        val laneCount = max(1, laneEnds.size)
        assigned.forEach { (item, lane) ->
            result += WidgetDayTimedLayout(
                item = item.item,
                startMinute = item.startMinute,
                endMinute = item.endMinute,
                lane = lane,
                laneCount = laneCount,
            )
        }
        group.clear()
        groupEnd = Int.MIN_VALUE
    }

    pending.forEach { item ->
        if (group.isNotEmpty() && item.startMinute >= groupEnd) {
            flushGroup()
        }
        group += item
        groupEnd = max(groupEnd, item.endMinute)
    }
    flushGroup()
    return result
}
