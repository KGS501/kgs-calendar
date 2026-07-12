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
private const val EXTRA_WIDGET_TASK_CREATE_MODE = "kgs_widget_task_create_mode"
private const val WIDGET_ACTION_CREATE_EVENT = "create_event"
private const val WIDGET_ACTION_CREATE_TASK = "create_task"
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
private const val WIDGET_MULTI_CONTENT_PADDING_DP = 12
internal const val WIDGET_MONTH_RENDER_SIGNATURE_VERSION = 41
internal const val WIDGET_DAY_RENDER_SIGNATURE_VERSION = 10
internal const val WIDGET_DAY_START_HOUR = 0
internal const val WIDGET_DAY_END_HOUR = 23
internal const val WIDGET_DAY_DEFAULT_TASK_DURATION_MILLIS = 30L * 60L * 1000L
private const val WIDGET_DAY_ROOT_PADDING_DP = 12
private const val WIDGET_DAY_LIST_SIDE_BLEED_DP = 0f
private const val WIDGET_DAY_ROW_SIDE_INSET_DP = 0f
private const val WIDGET_DAY_HEADER_HEIGHT_DP = 38
private const val WIDGET_DAY_TIMELINE_TOP_MARGIN_DP = 6
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
private const val WIDGET_DAY_MAX_PRIORITY_MOTION_ROWS = WIDGET_PRIORITY_MOTION_ITEM_LIMIT
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
private const val WIDGET_AGENDA_LIST_SIDE_BLEED_DP = 12
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

private fun KgsWidgetKind.usesDirectCollectionItems(): Boolean =
    (this == KgsWidgetKind.Tasks || this == KgsWidgetKind.Agenda || this == KgsWidgetKind.Multi) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private fun usesDirectDayGridItems(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

internal fun KgsWidgetKind.priorityMotionFrameIds(): IntArray =
    if (usesAgendaCollectionStyle()) WIDGET_AGENDA_PRIORITY_MOTION_FRAME_IDS else WIDGET_TASK_PRIORITY_MOTION_FRAME_IDS

private fun KgsWidgetKind.priorityMotionFrameCount(): Int =
    if (usesAgendaCollectionStyle()) WIDGET_AGENDA_PRIORITY_FRAME_COUNT else WIDGET_TASKS_PRIORITY_FRAME_COUNT

internal fun KgsWidgetKind.usesAgendaCollectionStyle(): Boolean =
    this == KgsWidgetKind.Agenda || this == KgsWidgetKind.Day || this == KgsWidgetKind.Multi

private fun Throwable.isRemoteViewsBitmapMemoryError(): Boolean =
    this is IllegalArgumentException &&
        message?.contains("bitmap", ignoreCase = true) == true &&
        message?.contains("memory", ignoreCase = true) == true

@RequiresApi(Build.VERSION_CODES.S)
private fun RemoteViews.bindDirectCollectionItems(
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
private fun RemoteViews.bindDirectDayGridItems(
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

private fun RemoteViews.bindCollectionBottomFade(palette: WidgetPalette, visible: Boolean) {
    setInt(R.id.widget_bottom_fade, "setBackgroundResource", palette.bottomFadeRes)
    setViewVisibility(R.id.widget_bottom_fade, if (visible) View.VISIBLE else View.GONE)
}

private fun RemoteViews.bindTasksSortButtonState(
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

private fun WidgetTaskSortMode.widgetButtonWidthDp(context: Context): Float {
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

private fun widgetSortIconBitmap(context: Context, tint: Int): Bitmap {
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

private fun widgetTodayDateIconBitmap(context: Context, tint: Int, day: Int): Bitmap {
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

private fun WidgetDayItem.hasDayPriorityMotion(): Boolean =
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

private fun widgetRequestCode(value: String): Int =
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

internal class KgsWidgetRenderer(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val packageName = context.packageName
    private val dataSource = KgsWidgetDataSource(context, zoneId)
    private val monthPageSource = WidgetMonthPageSource(context, zoneId)

    suspend fun render(
        kind: KgsWidgetKind,
        appWidgetId: Int,
        options: Bundle,
        forceServiceCollection: Boolean = false,
        collectionSnapshot: WidgetCollectionSnapshot? = null,
        preparedMulti: PreparedMultiWidgetRender? = null,
    ): RemoteViews {
        val start = SystemClock.elapsedRealtime()
        val result = when (kind) {
            KgsWidgetKind.Month -> renderMonthUpdate(appWidgetId, options).views
            KgsWidgetKind.Day -> renderDayWidget(appWidgetId, options, forceServiceCollection = forceServiceCollection)
            KgsWidgetKind.Multi -> renderMultiWidget(
                appWidgetId = appWidgetId,
                options = options,
                forceServiceCollection = forceServiceCollection,
                prepared = preparedMulti,
            )
            else -> renderCollectionWidget(
                kind = kind,
                appWidgetId = appWidgetId,
                options = options,
                forceServiceCollection = forceServiceCollection,
                collectionSnapshot = collectionSnapshot,
            )
        }
        WidgetLog.d(context, "Rendered ${kind.name} widget $appWidgetId in ${SystemClock.elapsedRealtime() - start}ms")
        return result
    }

    suspend fun renderDayNavigationUpdate(
        appWidgetId: Int,
        options: Bundle,
    ): RemoteViews =
        renderDayWidget(
            appWidgetId = appWidgetId,
            options = options,
            applyInitialScroll = false,
        )

    suspend fun renderDayAllDaySectionFrame(
        frameData: WidgetDayAllDaySectionFrameData,
        expansionProgress: Float,
    ): RemoteViews {
        val textContext = context.withWidgetLocale(frameData.settings.locale)
        val views = RemoteViews(packageName, R.layout.widget_day_calendar)
        views.bindCollectionBottomFade(frameData.palette, visible = true)
        if (frameData.timeline.allDayItems.isNotEmpty()) {
            views.setViewVisibility(R.id.widget_day_all_day_section, View.VISIBLE)
            val settled = expansionProgress <= 0.001f || expansionProgress >= 0.999f
            WidgetDayGridRow(
                day = frameData.timeline.day,
                hour = null,
                allDayItems = frameData.timeline.allDayItems,
                priorityAnimationsEnabled = frameData.settings.priorityAnimationsEnabled && settled,
                maxVisibleAllDayItems = frameData.settings.maxVisibleAllDayItems,
                allDayExpanded = frameData.allDayExpanded,
                allDayExpansionProgress = expansionProgress,
            ).bindInto(
                target = views,
                context = textContext,
                packageName = packageName,
                palette = frameData.palette,
                widthDp = frameData.contentWidthDp,
                rootId = R.id.widget_day_all_day_section,
                artId = R.id.widget_day_all_day_art,
                motionId = R.id.widget_day_priority_motion,
                overlayId = R.id.widget_day_all_day_overlay,
                appWidgetId = frameData.appWidgetId,
                useImageUris = true,
            )
        } else {
            views.setViewVisibility(R.id.widget_day_all_day_section, View.GONE)
            views.removeAllViews(R.id.widget_day_all_day_overlay)
        }
        return views
    }

    suspend fun dayAllDaySectionFrameData(
        appWidgetId: Int,
        options: Bundle,
    ): WidgetDayAllDaySectionFrameData {
        val settings = dataSource.loadSettings(KgsWidgetKind.Day)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val today = LocalDate.now(zoneId)
        val day = KgsWidgetDayState.day(context, appWidgetId, today)
        val size = WidgetSize.from(context, options, KgsWidgetKind.Day)
        val timeline = dataSource.dayTimeline(day, settings)
        return WidgetDayAllDaySectionFrameData(
            appWidgetId = appWidgetId,
            settings = settings,
            palette = palette,
            timeline = timeline,
            contentWidthDp = size.dayGridContentWidthDp(),
            allDayExpanded = KgsWidgetDayState.isAllDayExpanded(context, appWidgetId),
        )
    }

    suspend fun prepareMonthUpdate(appWidgetId: Int, options: Bundle): PreparedMonthWidgetRender =
        prepareMonthWidget(appWidgetId, options)

    suspend fun prepareMultiUpdate(appWidgetId: Int, options: Bundle): PreparedMultiWidgetRender =
        prepareMultiWidget(appWidgetId, options)

    fun renderMonthUpdate(prepared: PreparedMonthWidgetRender): MonthWidgetRenderResult =
        renderPreparedMonthPage(prepared)

    suspend fun renderMonthUpdate(appWidgetId: Int, options: Bundle): MonthWidgetRenderResult =
        renderPreparedMonthPage(prepareMonthWidget(appWidgetId, options))

    internal fun renderMonthNavigationPage(
        kind: KgsWidgetKind,
        snapshot: MonthNavSnapshot,
        options: Bundle,
        settings: WidgetRenderSettings,
        page: WidgetMonthPage,
        hasCompleteData: Boolean,
    ): MonthWidgetRenderResult {
        return when (kind) {
            KgsWidgetKind.Month -> renderMonthPageResult(
                appWidgetId = snapshot.widgetId,
                options = options,
                settings = settings,
                page = page,
                hasCompleteData = hasCompleteData,
            )
            KgsWidgetKind.Multi -> {
                val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
                val renderedPage = if (hasCompleteData) page else page.loadingSkeleton(palette.muted)
                MonthWidgetRenderResult(
                    views = renderMultiMonthNavigationPage(snapshot.widgetId, options, settings, renderedPage),
                    hasCompleteData = hasCompleteData,
                    signature = null,
                )
            }
            else -> error("Month navigation is unsupported for ${kind.name}")
        }
    }

    private suspend fun renderDayWidget(
        appWidgetId: Int,
        options: Bundle,
        applyInitialScroll: Boolean = true,
        forceServiceCollection: Boolean = false,
    ): RemoteViews {
        val settings = dataSource.loadSettings(KgsWidgetKind.Day)
        val textContext = context.withWidgetLocale(settings.locale)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val today = LocalDate.now(zoneId)
        val day = KgsWidgetDayState.day(context, appWidgetId, today)
        val size = WidgetSize.from(context, options, KgsWidgetKind.Day)
        val timeline = dataSource.dayTimeline(day, settings)
        val useCurrentHourStart = settings.dayWidgetStartAtCurrentHour && day == today
        val nextTimeline = if (useCurrentHourStart) {
            dataSource.dayTimeline(day.plusDays(1), settings)
        } else {
            null
        }
        val contentWidthDp = size.dayGridContentWidthDp()
        val gridRows = dayGridRows(timeline, settings, nextTimeline)
        val views = RemoteViews(packageName, R.layout.widget_day_calendar)
        val contentPadding = context.dpToPx(WIDGET_DAY_ROOT_PADDING_DP)
        views.setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        views.setInt(R.id.widget_header, "setBackgroundResource", palette.rootBackgroundRes)
        views.setInt(R.id.widget_day_header_compact, "setBackgroundResource", palette.rootBackgroundRes)
        views.setViewPadding(R.id.widget_content, contentPadding, contentPadding, contentPadding, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutMargin(R.id.widget_day_list_viewport, RemoteViews.MARGIN_BOTTOM, 0f, TypedValue.COMPLEX_UNIT_DIP)
        }
        val dayTitle = day.format(DateTimeFormatter.ofPattern("EEE, d. MMM", settings.locale))
        val hideDayTitle = shouldHideWidgetTitle(size.widthDp, dayTitle, reservedDp = 174f, textSp = 16f)
        views.setViewVisibility(R.id.widget_header, if (hideDayTitle) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_day_header_compact, if (hideDayTitle) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_title, dayTitle)
        views.setTextColor(R.id.widget_title, palette.text)
        views.setViewVisibility(R.id.widget_title, View.VISIBLE)
        views.setInt(R.id.widget_header, "setGravity", Gravity.CENTER_VERTICAL)
        views.setImageViewBitmap(R.id.widget_day_today, widgetTodayDateIconBitmap(context, palette.onAccent, today.dayOfMonth))
        views.setImageViewBitmap(R.id.widget_day_today_compact, widgetTodayDateIconBitmap(context, palette.onAccent, today.dayOfMonth))
        views.setInt(R.id.widget_day_today, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setInt(R.id.widget_day_today_compact, "setBackgroundResource", palette.badgeBackgroundRes)
        val todayVisibility = if (day == today) View.GONE else View.VISIBLE
        views.setViewVisibility(R.id.widget_day_today, todayVisibility)
        views.setViewVisibility(R.id.widget_day_today_compact, todayVisibility)
        views.setOnClickPendingIntent(R.id.widget_day_today, dayTodayPendingIntent(appWidgetId))
        views.setOnClickPendingIntent(R.id.widget_day_today_compact, dayTodayPendingIntent(appWidgetId))
        views.setTextViewText(R.id.widget_badge, "+")
        views.setTextViewText(R.id.widget_badge_compact, "+")
        views.setTextViewTextSize(R.id.widget_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
        views.setTextViewTextSize(R.id.widget_badge_compact, TypedValue.COMPLEX_UNIT_SP, 17f)
        views.setTextColor(R.id.widget_badge, palette.onAccent)
        views.setTextColor(R.id.widget_badge_compact, palette.onAccent)
        views.setInt(R.id.widget_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setInt(R.id.widget_badge_compact, "setBackgroundResource", palette.badgeBackgroundRes)
        val createTaskIntent = createTaskPendingIntent(appWidgetId, day, WidgetTaskCreateMode.Today)
        views.setOnClickPendingIntent(R.id.widget_badge, createTaskIntent)
        views.setOnClickPendingIntent(R.id.widget_badge_compact, createTaskIntent)
        views.setTextViewText(R.id.widget_month_prev, "\u2039")
        views.setTextViewText(R.id.widget_month_next, "\u203A")
        views.setTextViewText(R.id.widget_month_prev_compact, "\u2039")
        views.setTextViewText(R.id.widget_month_next_compact, "\u203A")
        views.setTextColor(R.id.widget_month_prev, palette.accent)
        views.setTextColor(R.id.widget_month_next, palette.accent)
        views.setTextColor(R.id.widget_month_prev_compact, palette.accent)
        views.setTextColor(R.id.widget_month_next_compact, palette.accent)
        val previousDayIntent = dayNavigationPendingIntent(appWidgetId, previous = true)
        val nextDayIntent = dayNavigationPendingIntent(appWidgetId, previous = false)
        views.setOnClickPendingIntent(R.id.widget_month_prev, previousDayIntent)
        views.setOnClickPendingIntent(R.id.widget_month_prev_compact, previousDayIntent)
        views.setOnClickPendingIntent(R.id.widget_month_next, nextDayIntent)
        views.setOnClickPendingIntent(R.id.widget_month_next_compact, nextDayIntent)
        val openApp = openMainAppPendingIntent(appWidgetId)
        views.setOnClickPendingIntent(R.id.widget_root, openApp)
        views.setOnClickPendingIntent(R.id.widget_content, openApp)
        views.setOnClickPendingIntent(R.id.widget_title, openApp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewVisibility(R.id.widget_day_timeline_art, View.GONE)
            views.setViewVisibility(R.id.widget_list, View.VISIBLE)
            if (timeline.allDayItems.isNotEmpty()) {
                views.setViewVisibility(R.id.widget_day_all_day_section, View.VISIBLE)
                WidgetDayGridRow(
                    day = timeline.day,
                    hour = null,
                    allDayItems = timeline.allDayItems,
                    priorityAnimationsEnabled = settings.priorityAnimationsEnabled,
                    maxVisibleAllDayItems = settings.maxVisibleAllDayItems,
                    allDayExpanded = KgsWidgetDayState.isAllDayExpanded(context, appWidgetId),
                ).bindInto(
                    target = views,
                    context = textContext,
                    packageName = packageName,
                    palette = palette,
                    widthDp = contentWidthDp,
                    rootId = R.id.widget_day_all_day_section,
                    artId = R.id.widget_day_all_day_art,
                    motionId = R.id.widget_day_priority_motion,
                    overlayId = R.id.widget_day_all_day_overlay,
                    appWidgetId = appWidgetId,
                )
            } else {
                views.setViewVisibility(R.id.widget_day_all_day_section, View.GONE)
                views.removeAllViews(R.id.widget_day_all_day_overlay)
            }
            if (usesDirectDayGridItems() && !forceServiceCollection) {
                views.setPendingIntentTemplate(R.id.widget_list, collectionClickPendingIntent(KgsWidgetKind.Day, appWidgetId))
                views.bindDirectDayGridItems(
                    context = textContext,
                    packageName = packageName,
                    palette = palette,
                    rows = gridRows,
                    widthDp = contentWidthDp + WIDGET_DAY_LIST_SIDE_BLEED_DP * 2f,
                    appWidgetId = appWidgetId,
                )
            } else if (applyInitialScroll) {
                views.setRemoteAdapter(R.id.widget_list, collectionAdapterIntent(KgsWidgetKind.Day, appWidgetId))
                views.setPendingIntentTemplate(R.id.widget_list, collectionClickPendingIntent(KgsWidgetKind.Day, appWidgetId))
            }
        } else {
            views.setOnClickPendingIntent(R.id.widget_day_timeline_art, openApp)
            views.setViewVisibility(R.id.widget_day_all_day_section, View.GONE)
            views.setViewVisibility(R.id.widget_day_timeline_art, View.VISIBLE)
            views.setViewVisibility(R.id.widget_list, View.GONE)
            views.setImageViewBitmap(R.id.widget_day_timeline_art, dayTimelineBitmap(size, palette, timeline))
        }
        views.bindCollectionBottomFade(palette, visible = true)
        views.setViewVisibility(R.id.widget_empty, View.GONE)
        views.setViewVisibility(R.id.widget_month_section, View.GONE)
        WidgetLog.d(context, "Day widget $appWidgetId date=$day timed=${timeline.timedItems.size} allDay=${timeline.allDayItems.size}")
        return views
    }

    suspend fun dayGridCollectionSnapshot(appWidgetId: Int): WidgetDayGridCollectionSnapshot {
        val settings = dataSource.loadSettings(KgsWidgetKind.Day)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val day = KgsWidgetDayState.day(context, appWidgetId, LocalDate.now(zoneId))
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val size = WidgetSize.from(context, options, KgsWidgetKind.Day)
        val timeline = dataSource.dayTimeline(day, settings)
        val nextTimeline = if (settings.dayWidgetStartAtCurrentHour && day == LocalDate.now(zoneId)) {
            dataSource.dayTimeline(day.plusDays(1), settings)
        } else {
            null
        }
        return WidgetDayGridCollectionSnapshot(
            rows = dayGridRows(timeline, settings, nextTimeline),
            settings = settings,
            palette = palette,
            widthDp = size.dayGridContentWidthDp() + WIDGET_DAY_LIST_SIDE_BLEED_DP * 2f,
        )
    }

    private fun dayGridRows(
        timeline: WidgetDayTimeline,
        settings: WidgetRenderSettings,
        nextTimeline: WidgetDayTimeline? = null,
    ): List<WidgetDayGridRow> {
        val now = LocalTime.now(zoneId)
        val today = LocalDate.now(zoneId)
        val hourRowHeightDp = settings.dayWidgetHourRowHeightDp()
        val availableAnimatedBlocks = WIDGET_DAY_MAX_PRIORITY_MOTION_ROWS - if (
            settings.priorityAnimationsEnabled &&
            timeline.allDayItems.any { it.hasDayPriorityMotion() }
        ) {
            1
        } else {
            0
        }
        val useCurrentHourStart = settings.dayWidgetStartAtCurrentHour && timeline.day == today
        val firstVisibleHour = if (useCurrentHourStart) {
            now.hour.coerceIn(WIDGET_DAY_START_HOUR, WIDGET_DAY_END_HOUR)
        } else if (settings.dayWidgetStartAtCurrentHour) {
            WIDGET_DAY_START_HOUR
        } else {
            settings.dayWidgetStartHour.coerceIn(WIDGET_DAY_START_HOUR, WIDGET_DAY_END_HOUR)
        }
        val primaryRows = dayGridRowsForTimeline(
            timeline = timeline,
            settings = settings,
            now = now,
            today = today,
            hourRowHeightDp = hourRowHeightDp,
            firstVisibleHour = firstVisibleHour,
            lastVisibleHour = WIDGET_DAY_END_HOUR,
            availableAnimatedBlocks = availableAnimatedBlocks,
            daySwitchLabel = null,
        )
        if (!useCurrentHourStart || firstVisibleHour == WIDGET_DAY_START_HOUR || nextTimeline == null) {
            return primaryRows
        }
        val nextRows = dayGridRowsForTimeline(
            timeline = nextTimeline,
            settings = settings,
            now = now,
            today = today,
            hourRowHeightDp = hourRowHeightDp,
            firstVisibleHour = WIDGET_DAY_START_HOUR,
            lastVisibleHour = firstVisibleHour - 1,
            availableAnimatedBlocks = availableAnimatedBlocks,
            daySwitchLabel = null,
        )
        val boundaryRow = WidgetDayGridRow(
            day = nextTimeline.day,
            hour = null,
            hourRowHeightDp = hourRowHeightDp,
            daySwitchLabel = nextTimeline.day.format(DateTimeFormatter.ofPattern("EEE d.", settings.locale)),
            priorityAnimationsEnabled = false,
        )
        return primaryRows + boundaryRow + nextRows
    }

    private fun dayGridRowsForTimeline(
        timeline: WidgetDayTimeline,
        settings: WidgetRenderSettings,
        now: LocalTime,
        today: LocalDate,
        hourRowHeightDp: Float,
        firstVisibleHour: Int,
        lastVisibleHour: Int,
        availableAnimatedBlocks: Int,
        daySwitchLabel: String?,
    ): List<WidgetDayGridRow> {
        if (firstVisibleHour > lastVisibleHour) return emptyList()
        val allTimedItems = timeline.timedItems
        val protectedRanges = if (settings.priorityAnimationsEnabled && availableAnimatedBlocks > 0) {
            allTimedItems.asSequence()
                .filter { it.item.hasDayPriorityMotion() }
                .distinctBy { it.item.stableKey }
                .sortedWith(
                    compareBy<WidgetDayTimedLayout> { it.item.priority ?: 9 }
                        .thenBy { it.startMinute }
                        .thenBy { it.endMinute }
                        .thenBy { it.item.title.lowercase(settings.locale) },
                )
                .take(availableAnimatedBlocks)
                .map { layout ->
                    val firstHour = (layout.startMinute / 60 - 1).coerceIn(0, 23)
                    val occupiedLastHour = ((layout.endMinute - 1).coerceAtLeast(layout.startMinute) / 60).coerceIn(0, 23)
                    firstHour..(occupiedLastHour + 1).coerceAtMost(23)
                }
                .toList()
                .sortedBy { it.first }
                .fold(mutableListOf<IntRange>()) { merged, range ->
                    val previous = merged.lastOrNull()
                    if (previous != null && range.first <= previous.last + 1) {
                        merged[merged.lastIndex] = previous.first..max(previous.last, range.last)
                    } else {
                        merged += range
                    }
                    merged
                }
        } else {
            emptyList()
        }
        val spanningRanges = allTimedItems.asSequence()
            .mapNotNull { layout ->
                val firstHour = (layout.startMinute / 60).coerceIn(WIDGET_DAY_START_HOUR, WIDGET_DAY_END_HOUR)
                val occupiedLastHour = ((layout.endMinute - 1).coerceAtLeast(layout.startMinute) / 60)
                    .coerceIn(WIDGET_DAY_START_HOUR, WIDGET_DAY_END_HOUR)
                val clippedFirst = max(firstHour, firstVisibleHour)
                val clippedLast = min(occupiedLastHour, lastVisibleHour)
                if (clippedLast > clippedFirst) clippedFirst..clippedLast else null
            }
            .toList()
        val groupedRanges = (protectedRanges + spanningRanges)
            .sortedBy { it.first }
            .fold(mutableListOf<IntRange>()) { merged, range ->
                val clipped = max(range.first, firstVisibleHour)..min(range.last, lastVisibleHour)
                if (clipped.first > clipped.last) return@fold merged
                val previous = merged.lastOrNull()
                if (previous != null && clipped.first <= previous.last + 1) {
                    merged[merged.lastIndex] = previous.first..max(previous.last, clipped.last)
                } else {
                    merged += clipped
                }
                merged
            }

        fun row(
            startHour: Int,
            hourCount: Int,
            animatePriority: Boolean,
        ): WidgetDayGridRow {
            val startMinute = startHour * 60
            val endMinute = (startHour + hourCount) * 60
            return WidgetDayGridRow(
                day = timeline.day,
                hour = startHour,
                hourCount = hourCount,
                hourRowHeightDp = hourRowHeightDp,
                timedItems = allTimedItems.filter { it.startMinute < endMinute && it.endMinute > startMinute },
                nowMinute = if (
                    timeline.day == today &&
                    now.hour in startHour until (startHour + hourCount)
                ) {
                    now.hour * 60 + now.minute
                } else {
                    null
                },
                daySwitchLabel = daySwitchLabel.takeIf { startHour == firstVisibleHour },
                priorityAnimationsEnabled = animatePriority,
            )
        }

        return buildList {
            var hour = firstVisibleHour
            var groupedIndex = 0
            while (hour <= lastVisibleHour) {
                val grouped = groupedRanges.getOrNull(groupedIndex)
                if (grouped != null && hour in grouped) {
                    val groupedStart = max(hour, grouped.first)
                    val groupedLast = min(grouped.last, lastVisibleHour)
                    val animatePriority = protectedRanges.any { range ->
                        range.first <= groupedLast && range.last >= groupedStart
                    }
                    add(
                        row(
                            startHour = groupedStart,
                            hourCount = groupedLast - groupedStart + 1,
                            animatePriority = animatePriority,
                        ),
                    )
                    repeat(groupedLast - groupedStart) { offset ->
                        add(
                            WidgetDayGridRow(
                                day = timeline.day,
                                hour = groupedStart + offset + 1,
                                hourCount = 0,
                                hourRowHeightDp = hourRowHeightDp,
                                priorityAnimationsEnabled = false,
                            ),
                        )
                    }
                    hour = groupedLast + 1
                    groupedIndex++
                } else {
                    add(row(startHour = hour, hourCount = 1, animatePriority = false))
                    hour++
                }
            }
        }
    }

    private fun dayTimelineRows(timeline: WidgetDayTimeline, settings: WidgetRenderSettings): List<WidgetListRow> {
        val rows = mutableListOf<WidgetListRow>()
        if (timeline.allDayItems.isNotEmpty()) {
            timeline.allDayItems.forEach { item ->
                rows += item.toDayRow(
                    date = timeline.day,
                    startMinute = 0,
                    endMinute = 24 * 60,
                    settings = settings,
                    allDay = true,
                )
            }
        }
        val byHour = timeline.timedItems.groupBy { (it.startMinute / 60).coerceIn(0, 23) }
        val now = LocalTime.now(zoneId)
        for (hour in settings.dayWidgetHourOrder()) {
            rows += WidgetListRow.section("%02d:00".format(hour), sortValue = hour.toLong())
            if (timeline.day == LocalDate.now(zoneId) && now.hour == hour) {
                rows += WidgetListRow.now(
                    date = timeline.day,
                    timeLabel = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    sortValue = hour.toLong(),
                )
            }
            byHour[hour]
                .orEmpty()
                .sortedWith(compareBy<WidgetDayTimedLayout> { it.startMinute }.thenBy { it.lane }.thenBy { it.item.title.lowercase(settings.locale) })
                .forEach { layout ->
                    rows += layout.item.toDayRow(
                        date = timeline.day,
                        startMinute = layout.startMinute,
                        endMinute = layout.endMinute,
                        settings = settings,
                        allDay = false,
                    )
                }
        }
        return rows
    }

    private fun WidgetRenderSettings.dayWidgetHourOrder(): List<Int> {
        return (WIDGET_DAY_START_HOUR..WIDGET_DAY_END_HOUR).toList()
    }

    private fun WidgetDayItem.toDayRow(
        date: LocalDate,
        startMinute: Int,
        endMinute: Int,
        settings: WidgetRenderSettings,
        allDay: Boolean,
    ): WidgetListRow {
        val labels = context.withWidgetLocale(settings.locale)
        val rowMeta = if (allDay) labels.getString(R.string.all_day) else (startMinute to endMinute).timeRangeText()
        return if (isTask && !taskResourceHref.isNullOrBlank()) {
            WidgetListRow.task(
                title = title,
                meta = rowMeta,
                color = color,
                sortMillis = if (allDay) Long.MIN_VALUE else startMinute.toLong(),
                date = date,
                completed = completed,
                taskResourceHref = taskResourceHref,
                statusGlyph = statusGlyph,
                location = location,
                depth = 0,
                childCount = 0,
                continuationLevels = emptySet(),
                lastSibling = true,
                subtasksExpanded = false,
                priority = priority,
                priorityMotionEnabled = settings.priorityAnimationsEnabled,
                launchKind = KgsWidgetKind.Day,
            )
        } else {
            WidgetListRow.item(
                title = title,
                meta = rowMeta,
                color = color,
                sortMillis = if (allDay) Long.MIN_VALUE else startMinute.toLong(),
                date = date,
                completed = completed,
                allDaySort = if (allDay) 0 else 1,
                launchKind = KgsWidgetKind.Day,
                stableKey = stableKey,
                eventResourceHref = eventResourceHref,
                location = location,
                eventStatus = eventStatus,
                endMillis = if (allDay) Long.MAX_VALUE else endMinute.toLong(),
            )
        }
    }

    private fun dayTimelineBitmap(size: WidgetSize, palette: WidgetPalette, timeline: WidgetDayTimeline): Bitmap {
        val density = context.resources.displayMetrics.density
        val widthDp = size.widthDp.coerceAtLeast(120)
        val heightDp = (
            size.heightDp -
                WIDGET_DAY_ROOT_PADDING_DP * 2 -
                WIDGET_DAY_HEADER_HEIGHT_DP -
                WIDGET_DAY_TIMELINE_TOP_MARGIN_DP
            ).coerceAtLeast(96)
        val bitmap = Bitmap.createBitmap(
            (widthDp * density).roundToInt().coerceAtLeast(1),
            (heightDp * density).roundToInt().coerceAtLeast(1),
            Bitmap.Config.RGB_565,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.rootBackgroundColor)
        canvas.scale(density, density)

        val dark = palette.rootBackgroundColor.isDarkColor()
        val slotColor = if (dark) 0xFF182534.toInt() else 0xFFFAFCFF.toInt()
        val slotLine = if (dark) 0xFF35475B.toInt() else 0xFFD2E0EF.toInt()
        val timeColumnWidth = if (widthDp < 230) 28f else 30f
        val gridLeft = timeColumnWidth
        val gridWidth = (widthDp - gridLeft).coerceAtLeast(1f)
        val allDayRows = when {
            timeline.allDayItems.isEmpty() -> 0
            heightDp < 170 -> 1
            else -> 2
        }
        val allDayRowHeight = if (heightDp < 170) 17f else 20f
        val allDayHeight = if (allDayRows > 0) allDayRows * allDayRowHeight + 3f else 0f
        val timelineTop = allDayHeight + if (allDayHeight > 0f) 5f else 0f
        val timelineHeight = (heightDp - timelineTop).coerceAtLeast(60f)
        val hourCount = WIDGET_DAY_END_HOUR - WIDGET_DAY_START_HOUR + 1
        val rowHeight = timelineHeight / hourCount.toFloat()
        val slotRadius = min(10f, rowHeight / 2.7f).coerceAtLeast(3f)

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted
            textSize = when {
                rowHeight < 10f -> 5.8f
                rowHeight < 14f -> 6.6f
                else -> 7.5f
            }
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slotColor
            style = Paint.Style.FILL
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slotLine
            strokeWidth = 0.65f
        }

        drawDayAllDayItems(
            canvas = canvas,
            palette = palette,
            items = timeline.allDayItems,
            rows = allDayRows,
            rowHeight = allDayRowHeight,
            timeColumnWidth = timeColumnWidth,
            gridLeft = gridLeft,
            gridWidth = gridWidth,
        )

        val labelEvery = when {
            rowHeight < 9f -> 4
            rowHeight < 13f -> 2
            else -> 1
        }
        repeat(hourCount) { index ->
            val top = timelineTop + index * rowHeight + 1f
            val bottom = timelineTop + (index + 1) * rowHeight - 1f
            canvas.drawRoundRect(
                RectF(gridLeft, top, widthDp.toFloat(), bottom.coerceAtLeast(top + 1f)),
                slotRadius,
                slotRadius,
                slotPaint,
            )
            if (index % labelEvery == 0) {
                val hour = WIDGET_DAY_START_HOUR + index
                canvas.drawText("%02d:00".format(hour), timeColumnWidth / 2f, top + timePaint.textSize + 1f, timePaint)
            }
            if (index > 0) {
                canvas.drawLine(gridLeft, top - 1f, widthDp.toFloat(), top - 1f, linePaint)
            }
        }

        timeline.timedItems.forEach { layout ->
            drawDayTimedItem(
                canvas = canvas,
                palette = palette,
                layout = layout,
                gridLeft = gridLeft,
                gridWidth = gridWidth,
                timelineTop = timelineTop,
                rowHeight = rowHeight,
            )
        }

        if (timeline.day == LocalDate.now(zoneId)) {
            val now = LocalTime.now(zoneId)
            if (now.hour in WIDGET_DAY_START_HOUR..WIDGET_DAY_END_HOUR) {
                val minute = now.hour * 60 + now.minute - WIDGET_DAY_START_HOUR * 60
                val y = timelineTop + minute / 60f * rowHeight
                val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.accent
                    strokeWidth = 1.4f
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(gridLeft, y, widthDp.toFloat(), y, nowPaint)
                canvas.drawCircle(gridLeft, y, 2.4f, nowPaint)
            }
        }
        return bitmap
    }

    private fun drawDayAllDayItems(
        canvas: Canvas,
        palette: WidgetPalette,
        items: List<WidgetDayItem>,
        rows: Int,
        rowHeight: Float,
        timeColumnWidth: Float,
        gridLeft: Float,
        gridWidth: Float,
    ) {
        if (rows <= 0 || items.isEmpty()) return
        val shown = items.take(rows)
        val hidden = (items.size - shown.size).coerceAtLeast(0)
        shown.forEachIndexed { index, item ->
            val top = index * rowHeight
            val rightInset = if (hidden > 0 && index == shown.lastIndex) 31f else 0f
            drawDayChip(
                canvas = canvas,
                palette = palette,
                item = item,
                rect = RectF(gridLeft, top, gridLeft + gridWidth - rightInset, top + rowHeight - 2f),
                titleSize = 9.5f,
                showMeta = false,
            )
            if (hidden > 0 && index == shown.lastIndex) {
                val overflowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.muted
                    textSize = 9f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val centerY = top + (rowHeight - 2f) / 2f - (overflowPaint.fontMetrics.ascent + overflowPaint.fontMetrics.descent) / 2f
                canvas.drawText("+$hidden", gridLeft + gridWidth - 15f, centerY, overflowPaint)
            }
        }
    }

    private fun drawDayTimedItem(
        canvas: Canvas,
        palette: WidgetPalette,
        layout: WidgetDayTimedLayout,
        gridLeft: Float,
        gridWidth: Float,
        timelineTop: Float,
        rowHeight: Float,
    ) {
        val laneGap = 2f
        val laneWidth = ((gridWidth - laneGap * (layout.laneCount - 1)) / layout.laneCount).coerceAtLeast(14f)
        val left = gridLeft + layout.lane * (laneWidth + laneGap)
        val top = timelineTop + layout.startMinute / 60f * rowHeight + 1f
        val rawHeight = (layout.endMinute - layout.startMinute).coerceAtLeast(1) / 60f * rowHeight
        val height = rawHeight.coerceAtLeast(if (rowHeight < 13f) 10f else 16f)
        val rect = RectF(left, top, (left + laneWidth).coerceAtMost(gridLeft + gridWidth), top + height - 1f)
        val titleSize = when {
            rowHeight < 10f -> 7.5f
            rowHeight < 15f -> 8.8f
            else -> 10.4f
        }
        drawDayChip(
            canvas = canvas,
            palette = palette,
            item = layout.item,
            rect = rect,
            titleSize = titleSize,
            showMeta = rect.height() >= 23f,
        )
    }

    private fun drawDayChip(
        canvas: Canvas,
        palette: WidgetPalette,
        item: WidgetDayItem,
        rect: RectF,
        titleSize: Float,
        showMeta: Boolean,
    ) {
        val color = if (item.completed) item.color.blendWith(palette.rootBackgroundColor, 0.45f) else item.color
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val radius = min(9f, rect.height() / 2f).coerceAtLeast(4f)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        val contentColor = if (color.isDarkColor()) 0xFFFFFFFF.toInt() else 0xFF1C1A18.toInt()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = contentColor.withAlpha(if (item.completed) 0.68f else 1f)
            textSize = titleSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = contentColor.withAlpha(if (item.completed) 0.55f else 0.84f)
            textSize = (titleSize - 1.5f).coerceAtLeast(6.5f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val statusSpace = if (item.isTask) titleSize + 8f else 0f
        if (item.isTask) {
            val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = titlePaint.color
                style = Paint.Style.STROKE
                strokeWidth = 1.25f
            }
            canvas.drawCircle(rect.left + 8f, rect.top + rect.height() / 2f, (titleSize * 0.42f).coerceAtLeast(3f), statusPaint)
        }
        val textStart = rect.left + 7f + statusSpace
        val textEnd = rect.right - 7f
        val textWidth = (textEnd - textStart).coerceAtLeast(0f)
        val titleBaseline = if (showMeta) {
            rect.top + 8f + titleSize
        } else {
            rect.centerY() - (titlePaint.fontMetrics.ascent + titlePaint.fontMetrics.descent) / 2f
        }
        canvas.drawText(ellipsizeForPaint(item.title, titlePaint, textWidth), textStart, titleBaseline, titlePaint)
        if (showMeta && item.meta.isNotBlank()) {
            val metaBaseline = titleBaseline + titleSize + 1f
            if (metaBaseline < rect.bottom - 3f) {
                canvas.drawText(ellipsizeForPaint(item.meta, metaPaint, textWidth), textStart, metaBaseline, metaPaint)
            }
        }
    }

    suspend fun collectionSignature(kind: KgsWidgetKind, appWidgetId: Int): String? {
        if (!kind.usesCollectionList) return null
        val settings = dataSource.loadSettings(kind)
        return dataSource.collectionSignature(kind, settings, appWidgetId)
    }

    suspend fun collectionSnapshot(kind: KgsWidgetKind, appWidgetId: Int): WidgetCollectionSnapshot? {
        if (!kind.usesCollectionList) return null
        val settings = dataSource.loadSettings(kind)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val renderSize = WidgetSize.from(
            context,
            AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId),
            kind,
        )
        val taskArtWidthDp = renderSize.collectionArtWidthDp(kind)
        val priorityFrameCount = kind.priorityMotionFrameCount()
        val rows = dataSource.listRows(kind, settings, appWidgetId)
        val dataSignature = dataSource.collectionSignature(kind, settings, appWidgetId, rows)
        return WidgetCollectionSnapshot(
            kind = kind,
            appWidgetId = appWidgetId,
            settings = settings,
            palette = palette,
            renderSize = renderSize,
            taskArtWidthDp = taskArtWidthDp,
            rows = rows,
            signature = collectionRenderSignature(
                dataSignature = dataSignature,
                renderSize = renderSize,
                taskArtWidthDp = taskArtWidthDp,
                priorityFrameCount = priorityFrameCount,
            ),
        )
    }

    private fun collectionRenderSignature(
        dataSignature: String,
        renderSize: WidgetSize,
        taskArtWidthDp: Float,
        priorityFrameCount: Int,
    ): String =
        "$dataSignature\nrender|$WIDGET_COLLECTION_RENDER_SIGNATURE_VERSION|${renderSize.widthDp}x${renderSize.heightDp}|$taskArtWidthDp|$priorityFrameCount|$WIDGET_TASK_PRIORITY_OVERDRAW_DP|$WIDGET_TASK_PRIORITY_BITMAP_SCALE|$WIDGET_AGENDA_LIST_SIDE_BLEED_DP"

    fun error(kind: KgsWidgetKind, appWidgetId: Int, message: String): RemoteViews {
        val settings = WidgetRenderSettings(locale = Locale.getDefault())
        val palette = WidgetPalette.from(context, AppThemeMode.KgsBlue, AppColorMode.Auto)
        val views = RemoteViews(packageName, if (kind == KgsWidgetKind.Tasks) R.layout.widget_calendar_tasks else R.layout.widget_calendar)
        views.bindBaseShell(kind, appWidgetId, settings, palette, LocalDate.now(zoneId))
        views.setViewVisibility(R.id.widget_month_section, View.GONE)
        views.setViewVisibility(R.id.widget_list, View.GONE)
        views.showEmpty(message, palette)
        return views
    }

    private suspend fun prepareMonthWidget(
        appWidgetId: Int,
        options: Bundle,
    ): PreparedMonthWidgetRender {
        val settingsStarted = SystemClock.elapsedRealtime()
        val settings = dataSource.loadSettings(KgsWidgetKind.Month)
        val settingsMillis = SystemClock.elapsedRealtime() - settingsStarted
        val month = KgsWidgetMonthState.month(context, appWidgetId, YearMonth.now(zoneId))
        val generation = WidgetDataGeneration.current()
        val cachedPage = KgsWidgetMonthPageCache.get(month, settings, zoneId.id, generation)
        val pageStarted = SystemClock.elapsedRealtime()
        val page = preparedWidgetValue(cachedPage) {
            monthPageSource.load(month, settings)
        }
        WidgetLog.d(
            context,
            "MonthPrepare widget=$appWidgetId settingsMs=$settingsMillis pageMs=${SystemClock.elapsedRealtime() - pageStarted} " +
                "pageCacheHit=${cachedPage != null} generation=$generation currentGeneration=${WidgetDataGeneration.current()}",
        )
        if (WidgetDataGeneration.current() == generation) {
            KgsWidgetMonthPageCache.put(month, settings, zoneId.id, page, generation)
            warmWidgetMonthPageCache(context, zoneId, month, settings)
        }
        return prepareMonthPage(appWidgetId, options, settings, page, hasCompleteData = true)
    }

    private suspend fun prepareMultiWidget(
        appWidgetId: Int,
        options: Bundle,
    ): PreparedMultiWidgetRender {
        val settings = dataSource.loadSettings(KgsWidgetKind.Multi)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val today = LocalDate.now(zoneId)
        val size = WidgetSize.from(context, options, KgsWidgetKind.Multi)
        val contentHeightDp = (size.heightDp - WIDGET_MULTI_CONTENT_PADDING_DP).coerceAtLeast(2)
        val monthPercent = SettingsStore.normalizeMultiWidgetMonthPercent(settings.multiWidgetMonthPercent)
        val monthPanelHeightDp = ((contentHeightDp * monthPercent) / 100f)
            .roundToInt()
            .coerceIn(1, contentHeightDp - 1)
        val agendaPanelHeightDp = (contentHeightDp - monthPanelHeightDp).coerceAtLeast(1)
        val month = KgsWidgetMonthState.month(context, appWidgetId, YearMonth.from(today))
        val generation = WidgetDataGeneration.current()
        val cachedPage = KgsWidgetMonthPageCache.get(month, settings, zoneId.id, generation)
        val page = coroutineScope {
            val pageDeferred = async {
                preparedWidgetValue(cachedPage) { monthPageSource.load(month, settings) }
            }
            val rowsDeferred = async {
                dataSource.listRows(KgsWidgetKind.Multi, settings, appWidgetId)
            }
            pageDeferred.await() to rowsDeferred.await()
        }
        val monthPage = page.first
        val rows = page.second
        if (WidgetDataGeneration.current() == generation) {
            KgsWidgetMonthPageCache.put(month, settings, zoneId.id, monthPage, generation)
            warmWidgetMonthPageCache(context, zoneId, month, settings)
        }
        val monthSpec = WidgetMonthRenderSpec.from(
            WidgetSize(widthDp = size.widthDp, heightDp = monthPanelHeightDp),
            monthPage.rowCount,
        )
        val taskArtWidthDp = size.collectionArtWidthDp(KgsWidgetKind.Multi)
        val dataSignature = dataSource.collectionSignature(KgsWidgetKind.Multi, settings, appWidgetId, rows)
        val collectionSnapshot = WidgetCollectionSnapshot(
            kind = KgsWidgetKind.Multi,
            appWidgetId = appWidgetId,
            settings = settings,
            palette = palette,
            renderSize = size,
            taskArtWidthDp = taskArtWidthDp,
            rows = rows,
            signature = collectionRenderSignature(
                dataSignature = dataSignature,
                renderSize = size,
                taskArtWidthDp = taskArtWidthDp,
                priorityFrameCount = KgsWidgetKind.Multi.priorityMotionFrameCount(),
            ),
        )
        val monthSignature = monthRenderSignature(
            today = today,
            settings = settings,
            palette = palette,
            currentSize = size,
            renderSpec = monthSpec,
            page = monthPage,
        )
        return PreparedMultiWidgetRender(
            appWidgetId = appWidgetId,
            settings = settings,
            palette = palette,
            today = today,
            page = monthPage,
            size = size,
            monthSpec = monthSpec,
            monthPanelHeightDp = monthPanelHeightDp,
            agendaPanelHeightDp = agendaPanelHeightDp,
            collectionSnapshot = collectionSnapshot,
            signature = multiWidgetRenderSignature(
                collectionSignature = collectionSnapshot.signature,
                monthSignature = monthSignature,
                monthPanelHeightDp = monthPanelHeightDp,
                agendaPanelHeightDp = agendaPanelHeightDp,
            ),
        )
    }

    private fun renderMonthPageResult(
        appWidgetId: Int,
        options: Bundle,
        settings: WidgetRenderSettings,
        page: WidgetMonthPage,
        hasCompleteData: Boolean,
    ): MonthWidgetRenderResult = renderPreparedMonthPage(
        prepareMonthPage(appWidgetId, options, settings, page, hasCompleteData),
    )

    private fun prepareMonthPage(
        appWidgetId: Int,
        options: Bundle,
        settings: WidgetRenderSettings,
        page: WidgetMonthPage,
        hasCompleteData: Boolean,
    ): PreparedMonthWidgetRender {
        val today = LocalDate.now(zoneId)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val renderedPage = if (hasCompleteData) page else page.loadingSkeleton(palette.muted)
        val currentSize = WidgetSize.from(context, options, KgsWidgetKind.Month)
        val renderSpec = WidgetMonthRenderSpec.from(currentSize, renderedPage.rowCount)

        return PreparedMonthWidgetRender(
            appWidgetId = appWidgetId,
            settings = settings,
            palette = palette,
            today = today,
            page = renderedPage,
            currentSize = currentSize,
            renderSpec = renderSpec,
            hasCompleteData = hasCompleteData,
            signature = if (hasCompleteData) {
                monthRenderSignature(
                    today = today,
                    settings = settings,
                    palette = palette,
                    currentSize = currentSize,
                    renderSpec = renderSpec,
                    page = page,
                )
            } else {
                null
            },
        )
    }

    private fun renderPreparedMonthPage(prepared: PreparedMonthWidgetRender): MonthWidgetRenderResult {
        WidgetLog.d(
            context,
            "Month widget ${prepared.appWidgetId} bucket=${prepared.renderSpec.bucket.name} rows=${prepared.page.rowCount} " +
                "weekHeight=${prepared.renderSpec.weekCellHeightDp} items=${prepared.itemCount} complete=${prepared.hasCompleteData}",
        )

        return MonthWidgetRenderResult(
            views = renderMonthRoot(
                appWidgetId = prepared.appWidgetId,
                settings = prepared.settings,
                palette = prepared.palette,
                today = prepared.today,
                page = prepared.page,
                renderSpec = prepared.renderSpec,
            ),
            hasCompleteData = prepared.hasCompleteData,
            signature = prepared.signature,
        )
    }

    private fun monthRenderSignature(
        today: LocalDate,
        settings: WidgetRenderSettings,
        palette: WidgetPalette,
        currentSize: WidgetSize,
        renderSpec: WidgetMonthRenderSpec,
        page: WidgetMonthPage,
    ): String = buildString {
        append(WIDGET_MONTH_RENDER_SIGNATURE_VERSION)
        append('|').append(today.toEpochDay())
        append('|').append(settings.locale.toLanguageTag())
        append('|').append(settings.firstDayOfWeek.name)
        append('|').append(settings.hiddenCollectionHrefs.sorted().joinToString(","))
        append('|').append(settings.showCompletedTasks)
        append('|').append(settings.themeMode.name)
        append('|').append(settings.colorMode.name)
        append('|').append(settings.systemNightMode)
        append('|').append(settings.taskColorMode.name)
        append('|').append(palette.rootBackgroundRes)
        append('|').append(palette.itemBackgroundRes)
        append('|').append(palette.compactItemBackgroundRes)
        append('|').append(palette.daySelectedBackgroundRes)
        append('|').append(palette.text)
        append('|').append(palette.muted)
        append('|').append(palette.onAccent)
        append('|').append(palette.monthTodayTextColor)
        append('|').append(currentSize.widthDp).append('x').append(currentSize.heightDp)
        append('|').append(renderSpec.bucket.name)
        append('|').append(renderSpec.weekCellHeightDp)
        append('|').append(renderSpec.cellContentWidthDp)
        append('|').append(renderSpec.usesDotCells)
        append('|').append(page.month)
        append('|').append(page.rowCount)
        page.cells.forEach { cell ->
            append('|').append(cell.date.toEpochDay())
            append(',').append(cell.inCurrentMonth)
            append(',').append(cell.totalItemCount)
            cell.items.forEach { item ->
                append(';').append(item.id)
                append(',').append(item.title)
                append(',').append(item.color)
                append(',').append(item.sortMillis)
                append(',').append(item.lane)
                append(',').append(item.continuesFromPrevious)
                append(',').append(item.continuesToNext)
                append(',').append(item.fadesFromPrevious)
                append(',').append(item.fadesToNext)
                append(',').append(item.completed)
            }
        }
    }

    private fun renderMonthRoot(
        appWidgetId: Int,
        settings: WidgetRenderSettings,
        palette: WidgetPalette,
        today: LocalDate,
        page: WidgetMonthPage,
        renderSpec: WidgetMonthRenderSpec,
    ): RemoteViews {
        val views = RemoteViews(packageName, monthRenderedLayout())
        views.setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        val padding = context.dpToPx(renderSpec.rootPaddingDp)
        views.setViewPadding(R.id.widget_root, padding, padding, padding, padding)
        val monthTitle = page.title(settings.locale)
        val hideMonthTitle = shouldHideWidgetTitle(renderSpec.totalWidthDp, monthTitle, reservedDp = 178f, textSp = renderSpec.titleTextSp)
        val currentMonth = YearMonth.from(today)
        val showingCurrentMonth = page.month == currentMonth
        views.setTextViewText(R.id.widget_title, monthTitle)
        views.setTextColor(R.id.widget_title, palette.text)
        views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, renderSpec.titleTextSp)
        views.setViewVisibility(R.id.widget_title, if (hideMonthTitle) View.GONE else View.VISIBLE)
        views.setInt(R.id.widget_header, "setGravity", if (hideMonthTitle) Gravity.CENTER else Gravity.CENTER_VERTICAL)
        val monthOpenPendingIntent = openAppPendingIntent(
            kind = KgsWidgetKind.Month,
            requestCode = widgetRequestCode("month-open:$appWidgetId:${page.month}"),
            date = page.month.atDay(1),
            clearTask = true,
        )
        views.setOnClickPendingIntent(R.id.widget_header, monthOpenPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_title, monthOpenPendingIntent)
        views.setTextViewText(R.id.widget_badge, "+")
        views.setTextColor(R.id.widget_badge, palette.onAccent)
        views.setInt(R.id.widget_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setOnClickPendingIntent(R.id.widget_badge, createEventPendingIntent(appWidgetId, today))
        views.setImageViewBitmap(R.id.widget_day_today, widgetTodayDateIconBitmap(context, palette.onAccent, today.dayOfMonth))
        views.setInt(R.id.widget_day_today, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setViewVisibility(R.id.widget_day_today, if (showingCurrentMonth) View.GONE else View.VISIBLE)
        views.setOnClickPendingIntent(R.id.widget_day_today, monthTodayPendingIntent(appWidgetId))
        views.setTextViewText(R.id.widget_month_prev, "\u2039")
        views.setTextViewText(R.id.widget_month_next, "\u203A")
        views.setTextColor(R.id.widget_month_prev, palette.accent)
        views.setTextColor(R.id.widget_month_next, palette.accent)
        views.setOnClickPendingIntent(R.id.widget_month_prev, monthNavigationPendingIntent(appWidgetId, previous = true))
        views.setOnClickPendingIntent(R.id.widget_month_next, monthNavigationPendingIntent(appWidgetId, previous = false))

        views.bindMonthPage(R.id.widget_month_page_a, page, settings, palette, renderSpec, appWidgetId)
        views.removeAllViews(R.id.widget_month_page_b)
        views.setDisplayedChild(R.id.widget_month_flipper, 0)
        return views
    }

    private fun RemoteViews.bindMonthPage(
        pageContainerId: Int,
        page: WidgetMonthPage,
        settings: WidgetRenderSettings,
        palette: WidgetPalette,
        renderSpec: WidgetMonthRenderSpec,
        appWidgetId: Int,
    ) {
        removeAllViews(pageContainerId)
        val header = RemoteViews(packageName, R.layout.widget_month_grid_header_row)
        for (dayOfWeek in settings.weekDays()) {
            val weekday = RemoteViews(packageName, R.layout.widget_weekday_cell)
            weekday.setTextViewText(R.id.widget_weekday_text, dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, settings.locale))
            weekday.setTextColor(R.id.widget_weekday_text, palette.muted)
            weekday.setTextViewTextSize(R.id.widget_weekday_text, TypedValue.COMPLEX_UNIT_SP, renderSpec.weekdayTextSp)
            header.addView(R.id.widget_month_grid_row, weekday)
        }
        addView(pageContainerId, header)

        var index = 0
        repeat(page.rowCount) {
            val rowCells = page.cells.subList(index, index + 7)
            val row = RemoteViews(packageName, R.layout.widget_month_grid_week_row)
            val textItemCapacity = if (renderSpec.usesDotCells) 0 else renderSpec.textItemCapacityFor(rowCells)
            repeat(7) { column ->
                val cell = rowCells[column]
                val visibleCount = cell.items.count { it.lane < textItemCapacity }
                val textOverflow = if (!renderSpec.usesDotCells && cell.inCurrentMonth) {
                    (cell.totalItemCount - visibleCount).coerceAtLeast(0)
                } else {
                    0
                }
                row.addView(
                    R.id.widget_month_grid_row,
                    if (renderSpec.usesDotCells) {
                        renderMonthCell(cell, palette, renderSpec, appWidgetId)
                    } else {
                        renderMonthCellShell(cell, palette, renderSpec, appWidgetId, textOverflow)
                    },
                )
            }
            if (renderSpec.usesDotCells) {
                row.setViewVisibility(R.id.widget_month_week_chip_layers, View.GONE)
            } else {
                val connectedBottomFadeSegments = rowCells.monthBottomFadeSegments(textItemCapacity)
                row.bindMonthWeekChips(rowCells, palette, renderSpec, appWidgetId, textItemCapacity)
                if (rowCells.any { cell -> cell.items.any { it.lane < textItemCapacity } }) {
                    row.bindMonthWeekBottomFades(rowCells, palette, connectedBottomFadeSegments)
                    row.bindMonthWeekBottomSpanFades(palette, connectedBottomFadeSegments)
                }
            }
            row.bindMonthTodayBorder(rowCells, palette, appWidgetId)
            addView(pageContainerId, row)
            index += 7
        }
    }

    private fun monthRenderedLayout(): Int =
        R.layout.widget_month_calendar

    private fun RemoteViews.bindMonthWeekChips(
        rowCells: List<WidgetMonthCellContent>,
        palette: WidgetPalette,
        renderSpec: WidgetMonthRenderSpec,
        appWidgetId: Int,
        textItemCapacity: Int,
    ) {
        val segmentsByLane = (0 until textItemCapacity).map(rowCells::monthWeekSegments)
        val lastOccupiedLane = segmentsByLane.indexOfLast { it.isNotEmpty() }
        if (lastOccupiedLane < 0) return
        repeat(lastOccupiedLane + 1) { lane ->
            val laneRow = RemoteViews(packageName, R.layout.widget_month_chip_lane_row)
            var column = 0
            for (segment in segmentsByLane[lane]) {
                val leading = segment.startColumn - column
                if (leading > 0) {
                    laneRow.addDaySpacers(leading)
                }
                val span = segment.columnSpan.coerceIn(1, 7)
                val chip = RemoteViews(packageName, monthSpanChipLayout(span))
                val style = segment.item.color.monthChipStyle()
                chip.setImageViewResource(
                    R.id.widget_month_span_chip_background,
                    segment.item.monthChipMaskRes(),
                )
                chip.setInt(R.id.widget_month_span_chip_background, "setColorFilter", style.fillColor)
                chip.setTextViewText(R.id.widget_month_span_chip_text, segment.title)
                chip.setTextColor(R.id.widget_month_span_chip_text, style.textColor)
                chip.setTextViewTextSize(
                    R.id.widget_month_span_chip_text,
                    TypedValue.COMPLEX_UNIT_SP,
                    renderSpec.chipTextSp,
                )
                chip.setViewPadding(
                    R.id.widget_month_span_chip_text,
                    context.dpToPx(segment.item.monthSpanTextStartPaddingDp()).roundToInt(),
                    0,
                    context.dpToPx(
                        if (segment.item.fadesToNext) WIDGET_MONTH_SPAN_FADE_WIDTH_DP else 4f,
                    ).roundToInt(),
                    0,
                )
                chip.setViewVisibility(
                    R.id.widget_month_span_chip_text,
                    if (segment.title.isBlank()) View.GONE else View.VISIBLE,
                )
                val chipPendingIntent = openAppPendingIntent(
                    KgsWidgetKind.Day,
                    dayPendingIntentRequestCode(appWidgetId, rowCells[segment.startColumn].date),
                    rowCells[segment.startColumn].date,
                )
                chip.setOnClickPendingIntent(R.id.widget_month_span_chip_root, chipPendingIntent)
                chip.setOnClickPendingIntent(R.id.widget_month_span_chip_background, chipPendingIntent)
                chip.setOnClickPendingIntent(R.id.widget_month_span_chip_text, chipPendingIntent)
                laneRow.addView(R.id.widget_month_chip_lane_row, chip)
                column = segment.endColumn + 1
            }
            if (column < 7) {
                laneRow.addDaySpacers(7 - column)
            }
            addView(R.id.widget_month_week_chip_layers, laneRow)
        }
    }

    private fun RemoteViews.addDaySpacers(count: Int) {
        repeat(count.coerceAtLeast(0)) {
            addView(R.id.widget_month_chip_lane_row, RemoteViews(packageName, R.layout.widget_month_span_spacer))
        }
    }

    private fun RemoteViews.bindMonthWeekBottomFades(
        rowCells: List<WidgetMonthCellContent>,
        palette: WidgetPalette,
        connectedSegments: List<WidgetMonthWeekSegment>,
    ) {
        rowCells.forEachIndexed { column, cell ->
            val hasConnectedFade = connectedSegments.any { column in it.startColumn..it.endColumn }
            val fade = RemoteViews(
                packageName,
                if (cell.inCurrentMonth && !hasConnectedFade) {
                    R.layout.widget_month_bottom_fade_cell
                } else {
                    R.layout.widget_month_bottom_fade_spacer
                },
            )
            if (cell.inCurrentMonth && !hasConnectedFade) {
                fade.setInt(R.id.widget_month_bottom_fade_cell, "setBackgroundResource", cellBottomFadeRes(palette.itemBackgroundRes))
            }
            addView(R.id.widget_month_week_bottom_fade_row, fade)
        }
        setViewVisibility(R.id.widget_month_week_bottom_fade_row, View.VISIBLE)
    }

    private fun RemoteViews.bindMonthWeekBottomSpanFades(
        palette: WidgetPalette,
        connectedSegments: List<WidgetMonthWeekSegment>,
    ) {
        if (connectedSegments.isEmpty()) {
            return
        }
        val fadeRow = RemoteViews(packageName, R.layout.widget_month_bottom_span_fade_row)
        var column = 0
        for (segment in connectedSegments) {
            val leading = segment.startColumn - column
            if (leading > 0) {
                repeat(leading) {
                    fadeRow.addView(R.id.widget_month_bottom_span_fade_row, RemoteViews(packageName, R.layout.widget_month_bottom_fade_spacer))
                }
            }
            val fade = RemoteViews(packageName, monthBottomFadeSpanLayout(segment.columnSpan))
            fade.setInt(R.id.widget_month_bottom_fade_span, "setBackgroundResource", cellBottomFadeRes(palette.itemBackgroundRes))
            fadeRow.addView(R.id.widget_month_bottom_span_fade_row, fade)
            column = segment.endColumn + 1
        }
        if (column < 7) {
            repeat(7 - column) {
                fadeRow.addView(R.id.widget_month_bottom_span_fade_row, RemoteViews(packageName, R.layout.widget_month_bottom_fade_spacer))
            }
        }
        addView(R.id.widget_month_week_span_fade_layers, fadeRow)
        setViewVisibility(R.id.widget_month_week_span_fade_layers, View.VISIBLE)
    }

    private fun RemoteViews.bindMonthTodayBorder(
        rowCells: List<WidgetMonthCellContent>,
        palette: WidgetPalette,
        appWidgetId: Int,
    ) {
        val today = LocalDate.now(zoneId)
        if (rowCells.none { it.inCurrentMonth && it.date == today }) {
            setViewVisibility(R.id.widget_month_today_border_row, View.GONE)
            return
        }
        rowCells.forEach { cell ->
            val border = RemoteViews(
                packageName,
                if (cell.inCurrentMonth && cell.date == today) {
                    R.layout.widget_month_today_border_cell
                } else {
                    R.layout.widget_month_today_border_spacer
                },
            )
            if (cell.inCurrentMonth && cell.date == today) {
                border.setInt(R.id.widget_month_today_border, "setBackgroundResource", palette.daySelectedBackgroundRes)
                border.setOnClickPendingIntent(
                    R.id.widget_month_today_border,
                    openAppPendingIntent(KgsWidgetKind.Day, dayPendingIntentRequestCode(appWidgetId, cell.date), cell.date, clearTask = true),
                )
            }
            addView(R.id.widget_month_today_border_row, border)
        }
    }

    private fun renderMonthCellShell(
        cell: WidgetMonthCellContent,
        palette: WidgetPalette,
        renderSpec: WidgetMonthRenderSpec,
        appWidgetId: Int,
        textOverflowCount: Int = 0,
    ): RemoteViews {
        val views = RemoteViews(packageName, renderSpec.layoutRes)
        if (!cell.inCurrentMonth) {
            views.setViewVisibility(R.id.widget_month_day_root, View.INVISIBLE)
            return views
        }
        val isToday = cell.date == LocalDate.now(zoneId)
        val cellBackgroundRes = when {
                renderSpec.usesDotCells -> palette.compactItemBackgroundRes
                else -> palette.itemBackgroundRes
            }
        views.setInt(R.id.widget_month_day_card_background, "setBackgroundResource", cellBackgroundRes)
        views.setTextViewText(R.id.widget_month_day_text, monthTodayLabel(cell.date.dayOfMonth.toString(), isToday))
        views.setTextColor(R.id.widget_month_day_text, if (isToday) palette.monthTodayTextColor else palette.text)
        views.setTextViewTextSize(R.id.widget_month_day_text, TypedValue.COMPLEX_UNIT_SP, renderSpec.dayTextSp)
        if (!renderSpec.usesDotCells && textOverflowCount > 0) {
            views.setViewVisibility(R.id.widget_month_overflow_text, View.VISIBLE)
            views.setTextViewText(R.id.widget_month_overflow_text, monthTodayLabel("+$textOverflowCount", isToday))
            views.setTextColor(R.id.widget_month_overflow_text, if (isToday) palette.monthTodayTextColor else palette.muted)
        }
        val dayPendingIntent = openAppPendingIntent(KgsWidgetKind.Day, dayPendingIntentRequestCode(appWidgetId, cell.date), cell.date, clearTask = true)
        views.setOnClickPendingIntent(R.id.widget_month_day_root, dayPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_month_day_text, dayPendingIntent)
        if (!renderSpec.usesDotCells && textOverflowCount > 0) {
            views.setOnClickPendingIntent(R.id.widget_month_overflow_text, dayPendingIntent)
        }
        return views
    }

    private fun cellBottomFadeRes(cellBackgroundRes: Int): Int =
        when (cellBackgroundRes) {
            R.drawable.widget_month_cell_compact -> R.drawable.widget_month_cell_bottom_fade_compact
            R.drawable.widget_month_cell_compact_dark -> R.drawable.widget_month_cell_bottom_fade_compact_dark
            R.drawable.widget_item_background_dark -> R.drawable.widget_month_cell_bottom_fade_dark
            R.drawable.widget_month_day_selected -> R.drawable.widget_month_cell_bottom_fade_selected
            R.drawable.widget_month_day_selected_dark -> R.drawable.widget_month_cell_bottom_fade_selected_dark
            R.drawable.widget_month_day_selected_fresh -> R.drawable.widget_month_cell_bottom_fade_selected_fresh
            R.drawable.widget_month_day_selected_warm -> R.drawable.widget_month_cell_bottom_fade_selected_warm
            else -> R.drawable.widget_month_cell_bottom_fade
        }

    private fun renderMonthCell(
        cell: WidgetMonthCellContent,
        palette: WidgetPalette,
        renderSpec: WidgetMonthRenderSpec,
        appWidgetId: Int,
    ): RemoteViews {
        val views = renderMonthCellShell(cell, palette, renderSpec, appWidgetId)
        if (!cell.inCurrentMonth) return views
        val isToday = cell.date == LocalDate.now(zoneId)
        val dayPendingIntent = openAppPendingIntent(KgsWidgetKind.Day, dayPendingIntentRequestCode(appWidgetId, cell.date), cell.date, clearTask = true)

        if (renderSpec.usesDotCells) {
            views.bindMiniMonthCell(cell, palette, renderSpec, isToday)
        } else {
            views.bindTextMonthCell(cell, palette, renderSpec, isToday, dayPendingIntent)
        }
        return views
    }

    private fun RemoteViews.bindMiniMonthCell(
        cell: WidgetMonthCellContent,
        palette: WidgetPalette,
        renderSpec: WidgetMonthRenderSpec,
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
            R.id.widget_month_dot_9,
            R.id.widget_month_dot_10,
        )
        val sortedItems = cell.items.sortedBy { it.lane }
        val rowCounts = renderSpec.miniDotRowCountsFor(cell.totalItemCount)
        val visibleCount = minOf(sortedItems.size, rowCounts.first + rowCounts.second)
        val rowBreak = minOf(rowCounts.first, visibleCount)
        val secondRowVisibleCount = (visibleCount - rowBreak).coerceAtLeast(0)
        for (index in 0 until rowBreak) {
            val id = dotIds[index]
            val item = sortedItems[index]
            setViewVisibility(id, View.VISIBLE)
            setImageViewBitmap(id, monthDotBitmap(context, item.color))
        }
        for (index in 0 until secondRowVisibleCount) {
            val id = dotIds[WIDGET_MONTH_DOTS_PER_ROW + index]
            val itemIndex = rowBreak + index
            val item = sortedItems[itemIndex]
            setViewVisibility(id, View.VISIBLE)
            setImageViewBitmap(id, monthDotBitmap(context, item.color))
        }
        val hidden = (cell.totalItemCount - visibleCount).coerceAtLeast(0)
        if (rowBreak > 0) {
            setViewVisibility(R.id.widget_month_dot_row_1, View.VISIBLE)
        }
        if (secondRowVisibleCount > 0) {
            setViewVisibility(R.id.widget_month_dot_row_2, View.VISIBLE)
        }
        if (renderSpec.showMiniOverflow(hidden)) {
            setViewVisibility(R.id.widget_month_overflow_text, View.VISIBLE)
            setTextViewText(R.id.widget_month_overflow_text, monthTodayLabel("+$hidden", isToday))
            setTextColor(R.id.widget_month_overflow_text, if (isToday) palette.monthTodayTextColor else palette.muted)
        }
    }

    private fun RemoteViews.bindTextMonthCell(
        cell: WidgetMonthCellContent,
        palette: WidgetPalette,
        renderSpec: WidgetMonthRenderSpec,
        isToday: Boolean,
        clickPendingIntent: PendingIntent,
    ) {
        data class TextChipBinding(
            val containerId: Int,
            val textId: Int,
        )

        val chipBindings = listOf(
            TextChipBinding(R.id.widget_month_chip_1_container, R.id.widget_month_chip_1_text),
            TextChipBinding(R.id.widget_month_chip_2_container, R.id.widget_month_chip_2_text),
            TextChipBinding(R.id.widget_month_chip_3_container, R.id.widget_month_chip_3_text),
        ) + if (renderSpec.baseTextItemCapacity >= 4) {
            listOf(
                TextChipBinding(R.id.widget_month_chip_4_container, R.id.widget_month_chip_4_text),
                TextChipBinding(R.id.widget_month_chip_5_container, R.id.widget_month_chip_5_text),
                TextChipBinding(R.id.widget_month_chip_6_container, R.id.widget_month_chip_6_text),
                TextChipBinding(R.id.widget_month_chip_7_container, R.id.widget_month_chip_7_text),
                TextChipBinding(R.id.widget_month_chip_8_container, R.id.widget_month_chip_8_text),
            )
        } else emptyList()
        val textItemCapacity = renderSpec.textItemCapacityFor(listOf(cell))
        for (index in chipBindings.indices) {
            val binding = chipBindings[index]
            val item = cell.items.firstOrNull { it.lane == index }
            val visible = item != null && index < textItemCapacity
            if (visible) {
                setViewVisibility(binding.containerId, View.VISIBLE)
                val visibleItem = item
                val style = visibleItem.color.monthChipStyle()
                setInt(binding.containerId, "setBackgroundResource", style.cellBackgroundRes(visibleItem))
                setTextViewText(binding.textId, visibleItem.title)
                setTextColor(binding.textId, style.textColor)
                setTextViewTextSize(binding.textId, TypedValue.COMPLEX_UNIT_SP, renderSpec.chipTextSp)
                setViewPadding(
                    binding.textId,
                    context.dpToPx(if (visibleItem.fadesFromPrevious) 10 else 4),
                    0,
                    context.dpToPx(if (visibleItem.fadesToNext) 10 else 4),
                    0,
                )
                setOnClickPendingIntent(binding.containerId, clickPendingIntent)
                setOnClickPendingIntent(binding.textId, clickPendingIntent)
            }
        }
        val visibleCount = cell.items.count { it.lane < textItemCapacity }
        val hidden = (cell.totalItemCount - visibleCount).coerceAtLeast(0)
        if (hidden > 0 && renderSpec.showTextOverflow(textItemCapacity)) {
            setViewVisibility(R.id.widget_month_overflow_text, View.VISIBLE)
            setTextViewText(R.id.widget_month_overflow_text, monthTodayLabel("+$hidden", isToday))
            setTextColor(R.id.widget_month_overflow_text, if (isToday) palette.monthTodayTextColor else palette.muted)
        }
    }

    private fun renderMultiMonthNavigationPage(
        appWidgetId: Int,
        options: Bundle,
        settings: WidgetRenderSettings,
        page: WidgetMonthPage,
    ): RemoteViews {
        val today = LocalDate.now(zoneId)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val size = WidgetSize.from(context, options, KgsWidgetKind.Multi)
        val contentPaddingDp = 12
        val contentHeightDp = (size.heightDp - contentPaddingDp).coerceAtLeast(2)
        val monthPercent = SettingsStore.normalizeMultiWidgetMonthPercent(settings.multiWidgetMonthPercent)
        val monthPanelHeightDp = ((contentHeightDp * monthPercent) / 100f)
            .roundToInt()
            .coerceIn(1, contentHeightDp - 1)
        val agendaPanelHeightDp = (contentHeightDp - monthPanelHeightDp).coerceAtLeast(1)
        val monthSpec = WidgetMonthRenderSpec.from(
            WidgetSize(widthDp = size.widthDp, heightDp = monthPanelHeightDp),
            page.rowCount,
        )
        val views = RemoteViews(packageName, R.layout.widget_calendar_multi)
        val contentPadding = context.dpToPx(contentPaddingDp)
        views.setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        views.setViewPadding(R.id.widget_content, contentPadding, contentPadding, contentPadding, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutHeight(
                R.id.widget_multi_month_panel,
                monthPanelHeightDp.toFloat(),
                TypedValue.COMPLEX_UNIT_DIP,
            )
            views.setViewLayoutHeight(
                R.id.widget_multi_agenda_panel,
                agendaPanelHeightDp.toFloat(),
                TypedValue.COMPLEX_UNIT_DIP,
            )
        }
        views.setOnClickPendingIntent(R.id.widget_root, openMainAppPendingIntent(appWidgetId))

        val monthTitle = page.title(settings.locale)
        val hideTitle = shouldHideWidgetTitle(
            size.widthDp - contentPaddingDp * 2,
            monthTitle,
            reservedDp = 150f,
            textSp = monthSpec.titleTextSp,
        )
        val showingCurrentMonth = page.month == YearMonth.from(today)
        views.setTextViewText(R.id.widget_multi_month_title, monthTitle)
        views.setTextColor(R.id.widget_multi_month_title, palette.text)
        views.setTextViewTextSize(
            R.id.widget_multi_month_title,
            TypedValue.COMPLEX_UNIT_SP,
            monthSpec.titleTextSp,
        )
        views.setViewVisibility(R.id.widget_multi_month_title, if (hideTitle) View.GONE else View.VISIBLE)
        views.setInt(
            R.id.widget_multi_month_header,
            "setGravity",
            if (hideTitle) Gravity.CENTER else Gravity.CENTER_VERTICAL,
        )
        views.setTextViewText(R.id.widget_multi_month_badge, "+")
        views.setTextViewTextSize(R.id.widget_multi_month_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
        views.setTextColor(R.id.widget_multi_month_badge, palette.onAccent)
        views.setInt(R.id.widget_multi_month_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setOnClickPendingIntent(R.id.widget_multi_month_badge, createEventPendingIntent(appWidgetId, today))
        views.setImageViewBitmap(
            R.id.widget_day_today,
            widgetTodayDateIconBitmap(context, palette.onAccent, today.dayOfMonth),
        )
        views.setInt(R.id.widget_day_today, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setViewVisibility(R.id.widget_day_today, if (showingCurrentMonth) View.GONE else View.VISIBLE)
        views.setOnClickPendingIntent(
            R.id.widget_day_today,
            monthTodayPendingIntent(appWidgetId, targetKind = KgsWidgetKind.Multi),
        )
        views.setTextViewText(R.id.widget_month_prev, "\u2039")
        views.setTextViewText(R.id.widget_month_next, "\u203A")
        views.setTextColor(R.id.widget_month_prev, palette.accent)
        views.setTextColor(R.id.widget_month_next, palette.accent)
        views.setOnClickPendingIntent(
            R.id.widget_month_prev,
            monthNavigationPendingIntent(appWidgetId, previous = true, targetKind = KgsWidgetKind.Multi),
        )
        views.setOnClickPendingIntent(
            R.id.widget_month_next,
            monthNavigationPendingIntent(appWidgetId, previous = false, targetKind = KgsWidgetKind.Multi),
        )
        views.setOnClickPendingIntent(
            R.id.widget_multi_month_title,
            openAppPendingIntent(KgsWidgetKind.Month, 66_000 + appWidgetId, page.month.atDay(1)),
        )
        views.bindMonthPage(
            pageContainerId = R.id.widget_month_section,
            page = page,
            settings = settings,
            palette = palette,
            renderSpec = monthSpec,
            appWidgetId = appWidgetId,
        )
        views.setViewVisibility(R.id.widget_month_section, View.VISIBLE)
        return views
    }

    private suspend fun renderMultiWidget(
        appWidgetId: Int,
        options: Bundle,
        forceServiceCollection: Boolean = false,
        prepared: PreparedMultiWidgetRender? = null,
    ): RemoteViews {
        val renderData = preparedWidgetValue(prepared) {
            prepareMultiWidget(appWidgetId, options)
        }
        require(renderData.appWidgetId == appWidgetId)
        val today = renderData.today
        val settings = renderData.settings
        val textContext = context.withWidgetLocale(settings.locale)
        val palette = renderData.palette
        val size = renderData.size
        val contentPaddingDp = WIDGET_MULTI_CONTENT_PADDING_DP
        val monthPanelHeightDp = renderData.monthPanelHeightDp
        val agendaPanelHeightDp = renderData.agendaPanelHeightDp
        val page = renderData.page
        val monthSpec = renderData.monthSpec
        val views = RemoteViews(packageName, R.layout.widget_calendar_multi)
        val contentPadding = context.dpToPx(contentPaddingDp)
        val openApp = openMainAppPendingIntent(appWidgetId)

        views.setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        views.setViewPadding(R.id.widget_content, contentPadding, contentPadding, contentPadding, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutHeight(R.id.widget_multi_month_panel, monthPanelHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_multi_agenda_panel, agendaPanelHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp)

        val multiMonthTitle = page.title(settings.locale)
        val hideMultiMonthTitle = shouldHideWidgetTitle(size.widthDp - contentPaddingDp * 2, multiMonthTitle, reservedDp = 150f, textSp = monthSpec.titleTextSp)
        val showingCurrentMonth = page.month == YearMonth.from(today)
        views.setTextViewText(R.id.widget_multi_month_title, multiMonthTitle)
        views.setTextColor(R.id.widget_multi_month_title, palette.text)
        views.setTextViewTextSize(R.id.widget_multi_month_title, TypedValue.COMPLEX_UNIT_SP, monthSpec.titleTextSp)
        views.setViewVisibility(R.id.widget_multi_month_title, if (hideMultiMonthTitle) View.GONE else View.VISIBLE)
        views.setInt(R.id.widget_multi_month_header, "setGravity", if (hideMultiMonthTitle) Gravity.CENTER else Gravity.CENTER_VERTICAL)
        views.setTextViewText(R.id.widget_multi_month_badge, "+")
        views.setTextViewTextSize(R.id.widget_multi_month_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
        views.setTextColor(R.id.widget_multi_month_badge, palette.onAccent)
        views.setInt(R.id.widget_multi_month_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setOnClickPendingIntent(R.id.widget_multi_month_badge, createEventPendingIntent(appWidgetId, today))
        views.setImageViewBitmap(R.id.widget_day_today, widgetTodayDateIconBitmap(context, palette.onAccent, today.dayOfMonth))
        views.setInt(R.id.widget_day_today, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setViewVisibility(R.id.widget_day_today, if (showingCurrentMonth) View.GONE else View.VISIBLE)
        views.setOnClickPendingIntent(R.id.widget_day_today, monthTodayPendingIntent(appWidgetId, targetKind = KgsWidgetKind.Multi))
        views.setTextViewText(R.id.widget_month_prev, "\u2039")
        views.setTextViewText(R.id.widget_month_next, "\u203A")
        views.setTextColor(R.id.widget_month_prev, palette.accent)
        views.setTextColor(R.id.widget_month_next, palette.accent)
        views.setOnClickPendingIntent(R.id.widget_month_prev, monthNavigationPendingIntent(appWidgetId, previous = true, targetKind = KgsWidgetKind.Multi))
        views.setOnClickPendingIntent(R.id.widget_month_next, monthNavigationPendingIntent(appWidgetId, previous = false, targetKind = KgsWidgetKind.Multi))
        views.setOnClickPendingIntent(
            R.id.widget_multi_month_title,
            openAppPendingIntent(KgsWidgetKind.Month, 66_000 + appWidgetId, page.month.atDay(1)),
        )
        views.bindMonthPage(
            pageContainerId = R.id.widget_month_section,
            page = page,
            settings = settings,
            palette = palette,
            renderSpec = monthSpec,
            appWidgetId = appWidgetId,
        )
        views.setViewVisibility(R.id.widget_month_section, View.VISIBLE)

        views.bindCollectionBottomFade(palette, visible = true)
        views.setInt(R.id.widget_multi_agenda_top_fade, "setBackgroundResource", palette.topFadeRes)
        views.setTextViewText(R.id.widget_empty, KgsWidgetKind.Multi.emptyText(textContext))
        views.setTextColor(R.id.widget_empty, palette.muted)
        views.setViewVisibility(R.id.widget_empty, View.GONE)
        views.setViewVisibility(R.id.widget_list, View.VISIBLE)
        if (KgsWidgetKind.Multi.usesDirectCollectionItems() && !forceServiceCollection) {
            views.bindDirectCollectionItems(
                context = textContext,
                packageName = packageName,
                palette = palette,
                rows = renderData.collectionSnapshot.rows,
                sourceKind = KgsWidgetKind.Multi,
                appWidgetId = appWidgetId,
                renderOptions = WidgetCollectionRenderOptions(taskArtWidthDp = renderData.collectionSnapshot.taskArtWidthDp),
            )
        } else {
            views.setRemoteAdapter(R.id.widget_list, collectionAdapterIntent(KgsWidgetKind.Multi, appWidgetId))
        }
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)
        views.setPendingIntentTemplate(R.id.widget_list, collectionClickPendingIntent(KgsWidgetKind.Multi, appWidgetId))
        return views
    }

    private suspend fun renderCollectionWidget(
        kind: KgsWidgetKind,
        appWidgetId: Int,
        options: Bundle,
        forceServiceCollection: Boolean = false,
        collectionSnapshot: WidgetCollectionSnapshot? = null,
    ): RemoteViews {
        collectionSnapshot?.let { snapshot ->
            require(snapshot.kind == kind && snapshot.appWidgetId == appWidgetId)
        }
        val today = LocalDate.now(zoneId)
        val settings = preparedWidgetValue(collectionSnapshot?.settings) {
            dataSource.loadSettings(kind)
        }
        val textContext = context.withWidgetLocale(settings.locale)
        val palette = collectionSnapshot?.palette
            ?: WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val size = collectionSnapshot?.renderSize ?: WidgetSize.from(context, options, kind)
        val views = RemoteViews(packageName, if (kind == KgsWidgetKind.Tasks) R.layout.widget_calendar_tasks else R.layout.widget_calendar)
        views.bindBaseShell(kind, appWidgetId, settings, palette, today, size)
        if (kind == KgsWidgetKind.Multi) {
            val month = YearMonth.from(today)
            val page = monthPageSource.load(month, settings)
            val monthBucket = if (size.compact) WidgetSizeBucket.Mini else WidgetSizeBucket.Compact
            views.setViewVisibility(R.id.widget_month_section, View.VISIBLE)
            views.bindMonthPage(
                pageContainerId = R.id.widget_month_section,
                page = page,
                settings = settings,
                palette = palette,
                renderSpec = WidgetMonthRenderSpec(
                    bucket = monthBucket,
                    weekCellHeightDp = WidgetMonthRenderSpec.weekCellHeightDp(size, page.rowCount),
                    cellContentWidthDp = WidgetMonthRenderSpec.cellContentWidthDp(
                        size = size,
                        bucket = monthBucket,
                    ),
                ),
                appWidgetId = appWidgetId,
            )
        } else {
            views.setViewVisibility(R.id.widget_month_section, View.GONE)
        }
        views.setTextViewText(R.id.widget_empty, kind.emptyText(textContext))
        views.setTextColor(R.id.widget_empty, palette.muted)
        if (kind.usesDirectCollectionItems() && !forceServiceCollection) {
            val rows = preparedWidgetValue(collectionSnapshot?.rows) {
                dataSource.listRows(kind, settings, appWidgetId)
            }
            views.bindDirectCollectionItems(
                context = textContext,
                packageName = packageName,
                palette = palette,
                rows = rows,
                sourceKind = kind,
                appWidgetId = appWidgetId,
                renderOptions = WidgetCollectionRenderOptions(taskArtWidthDp = size.collectionArtWidthDp(kind)),
            )
        } else {
            views.setRemoteAdapter(R.id.widget_list, collectionAdapterIntent(kind, appWidgetId))
        }
        if (kind != KgsWidgetKind.Tasks) {
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
        }
        views.setPendingIntentTemplate(R.id.widget_list, collectionClickPendingIntent(kind, appWidgetId))
        return views
    }

    private fun RemoteViews.bindBaseShell(
        kind: KgsWidgetKind,
        appWidgetId: Int,
        settings: WidgetRenderSettings,
        palette: WidgetPalette,
        today: LocalDate,
        size: WidgetSize? = null,
        ) {
        setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        setInt(R.id.widget_header, "setBackgroundResource", palette.rootBackgroundRes)
        val contentPadding = context.dpToPx(12)
        val textContext = context.withWidgetLocale(settings.locale)
        val agendaEdgeFade = kind == KgsWidgetKind.Agenda
        setViewPadding(
            R.id.widget_content,
            if (kind == KgsWidgetKind.Tasks) 0 else contentPadding,
            contentPadding,
            if (kind == KgsWidgetKind.Tasks) 0 else contentPadding,
            if (kind == KgsWidgetKind.Tasks || agendaEdgeFade) 0 else contentPadding,
        )
        if (agendaEdgeFade) {
            setViewPadding(R.id.widget_header, 0, 0, 0, 0)
        }
        val titleText = kind.title(textContext)
        setTextViewText(R.id.widget_title, titleText)
        setTextColor(R.id.widget_title, palette.text)
        setTextViewText(R.id.widget_subtitle, headerSubtitle(kind, today, settings))
        setTextColor(R.id.widget_subtitle, palette.muted)
        val hideTitleText = size?.let { widgetSize ->
            val reservedDp = if (kind == KgsWidgetKind.Tasks) {
                56f + settings.tasksWidgetSortMode.widgetButtonWidthDp(textContext)
            } else {
                54f
            }
            shouldHideWidgetTitle(widgetSize.widthDp, titleText, reservedDp, textSp = 16f)
        } == true
        setViewVisibility(R.id.widget_title_group, if (hideTitleText) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.widget_title, if (hideTitleText) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.widget_subtitle, if (hideTitleText) View.GONE else View.VISIBLE)
        setInt(R.id.widget_header, "setGravity", if (hideTitleText) Gravity.CENTER else Gravity.CENTER_VERTICAL)
        if (kind == KgsWidgetKind.Tasks) {
            val openTasksIntent = openAppPendingIntent(KgsWidgetKind.Tasks, 45_000 + appWidgetId, today)
            setOnClickPendingIntent(R.id.widget_title, openTasksIntent)
            setOnClickPendingIntent(R.id.widget_subtitle, openTasksIntent)
        }
        setTextColor(R.id.widget_badge, palette.onAccent)
        setInt(R.id.widget_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        setInt(R.id.widget_sort, "setBackgroundResource", palette.sortBackgroundRes)
        setImageViewBitmap(R.id.widget_sort_icon, widgetSortIconBitmap(context, palette.onAccent))
        setTextColor(R.id.widget_sort_label_date, palette.onAccent)
        setTextColor(R.id.widget_sort_label_priority, palette.onAccent)
        setTextColor(R.id.widget_sort_label_status, palette.onAccent)
        setTextViewText(R.id.widget_sort_label_date, WidgetTaskSortMode.Date.widgetLabel(textContext))
        setTextViewText(R.id.widget_sort_label_priority, WidgetTaskSortMode.Priority.widgetLabel(textContext))
        setTextViewText(R.id.widget_sort_label_status, WidgetTaskSortMode.Status.widgetLabel(textContext))
        if (kind == KgsWidgetKind.Tasks) {
            bindTasksSortButtonState(
                context = textContext,
                appWidgetId = appWidgetId,
                mode = settings.tasksWidgetSortMode,
                widthDp = settings.tasksWidgetSortMode.widgetButtonWidthDp(textContext),
                createMode = settings.tasksWidgetCreateMode,
            )
        }
        setViewVisibility(R.id.widget_sort, if (kind == KgsWidgetKind.Tasks) View.VISIBLE else View.GONE)
        bindCollectionBottomFade(palette, visible = kind == KgsWidgetKind.Tasks || agendaEdgeFade)
        if (kind != KgsWidgetKind.Tasks) {
            setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(kind, appWidgetId, today))
        }
        when (kind) {
            KgsWidgetKind.Tasks -> {
                setTextViewText(R.id.widget_badge, "+")
                setTextViewTextSize(R.id.widget_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
                setOnClickPendingIntent(R.id.widget_badge, createTaskPendingIntent(appWidgetId, today, settings.tasksWidgetCreateMode))
            }
            KgsWidgetKind.Agenda -> {
                setTextViewText(R.id.widget_badge, "+")
                setTextViewTextSize(R.id.widget_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
                setOnClickPendingIntent(R.id.widget_badge, createEventPendingIntent(appWidgetId, today))
            }
            else -> {
                setTextViewText(R.id.widget_badge, "\u21BB")
                setTextViewTextSize(R.id.widget_badge, TypedValue.COMPLEX_UNIT_SP, 13f)
                setOnClickPendingIntent(R.id.widget_badge, refreshPendingIntent(kind, appWidgetId))
            }
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
        val textContext = context.withWidgetLocale(settings.locale)
        val shortDate = today.format(DateTimeFormatter.ofPattern("EEE, d. MMM", settings.locale))
        return when (kind) {
            KgsWidgetKind.Month, KgsWidgetKind.Multi -> YearMonth.from(today).format(DateTimeFormatter.ofPattern("MMMM yyyy", settings.locale))
            KgsWidgetKind.Day -> shortDate
            KgsWidgetKind.Tasks -> settings.tasksWidgetDisplayMode.widgetLabel(textContext)
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

    private fun openMainAppPendingIntent(appWidgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("kgs-calendar://widget-open/$appWidgetId/${SystemClock.elapsedRealtime()}")
        }
        return PendingIntent.getActivity(
            context,
            60_000 + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(kind: KgsWidgetKind, requestCode: Int, date: LocalDate, clearTask: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                (if (clearTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else Intent.FLAG_ACTIVITY_CLEAR_TOP)
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

    private fun monthNavigationPendingIntent(
        appWidgetId: Int,
        previous: Boolean,
        targetKind: KgsWidgetKind = KgsWidgetKind.Month,
    ): PendingIntent {
        val intent = Intent(context, targetKind.providerClass).apply {
            action = if (previous) KgsWidgetProvider.ACTION_MONTH_PREVIOUS else KgsWidgetProvider.ACTION_MONTH_NEXT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-month/${targetKind.name}/${if (previous) "previous" else "next"}/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            20_000 + appWidgetId * 10 + targetKind.ordinal * 2 + if (previous) 0 else 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun monthTodayPendingIntent(
        appWidgetId: Int,
        targetKind: KgsWidgetKind = KgsWidgetKind.Month,
    ): PendingIntent {
        val intent = Intent(context, targetKind.providerClass).apply {
            action = KgsWidgetProvider.ACTION_MONTH_TODAY
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-month/${targetKind.name}/today/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            23_000 + appWidgetId * 10 + targetKind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dayNavigationPendingIntent(appWidgetId: Int, previous: Boolean): PendingIntent {
        val intent = Intent(context, KgsWidgetKind.Day.providerClass).apply {
            action = if (previous) KgsWidgetProvider.ACTION_DAY_PREVIOUS else KgsWidgetProvider.ACTION_DAY_NEXT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-day/${if (previous) "previous" else "next"}/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            24_000 + appWidgetId * 2 + if (previous) 0 else 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dayTodayPendingIntent(appWidgetId: Int): PendingIntent {
        val intent = Intent(context, KgsWidgetKind.Day.providerClass).apply {
            action = KgsWidgetProvider.ACTION_DAY_TODAY
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-day/today/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            27_000 + appWidgetId,
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
