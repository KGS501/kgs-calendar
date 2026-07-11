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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

private const val TAG = "KgsWidget"
private const val EXTRA_WIDGET_KIND = "kgs_widget_kind"
private const val EXTRA_WIDGET_DATE = "kgs_widget_date"
private const val EXTRA_WIDGET_ACTION = "kgs_widget_action"
private const val EXTRA_WIDGET_TASK_UID = "kgs_widget_task_uid"
private const val EXTRA_WIDGET_EVENT_UID = "kgs_widget_event_uid"
private const val EXTRA_WIDGET_TASK_CREATE_MODE = "kgs_widget_task_create_mode"
private const val WIDGET_ACTION_CREATE_EVENT = "create_event"
private const val WIDGET_ACTION_CREATE_TASK = "create_task"
private const val WIDGET_ACTION_OPEN_TASK = "open_task"
private const val WIDGET_ACTION_OPEN_EVENT = "open_event"
private const val WIDGET_TASK_CREATE_UNPLANNED = "Unplanned"
private const val EXTRA_COLLECTION_ACTION = "kgs_widget_collection_action"
private const val COLLECTION_ACTION_OPEN = "open"
private const val COLLECTION_ACTION_TOGGLE_TASK = "toggle_task"
private const val COLLECTION_ACTION_TOGGLE_SUBTASKS = "toggle_subtasks"
private const val WIDGET_MONTH_DOT_SIZE_DP = 3.5f
private const val WIDGET_MONTH_SPAN_CHIP_HEIGHT_DP = 14f
private const val WIDGET_MONTH_SPAN_CHIP_HORIZONTAL_INSET_DP = 2f
private const val WIDGET_MONTH_SPAN_FADE_WIDTH_DP = 14f
private const val WIDGET_MONTH_SPAN_FADE_OVERLAP_DP = 1f
private const val WIDGET_MONTH_SPAN_FADE_TEXT_INSET_DP = 3f
private const val WIDGET_MONTH_SPAN_BITMAP_SCALE = 3.0f
private const val WIDGET_MONTH_RESIZE_DEBOUNCE_MS = 320L
internal const val WIDGET_MONTH_RENDER_SIGNATURE_VERSION = 40
private const val WIDGET_DAY_RENDER_SIGNATURE_VERSION = 10
private const val WIDGET_DAY_START_HOUR = 0
private const val WIDGET_DAY_END_HOUR = 23
private const val WIDGET_DAY_DEFAULT_TASK_DURATION_MILLIS = 30L * 60L * 1000L
private const val WIDGET_DAY_ROOT_PADDING_DP = 12
private const val WIDGET_DAY_LIST_SIDE_BLEED_DP = 0f
private const val WIDGET_DAY_ROW_SIDE_INSET_DP = 0f
private const val WIDGET_DAY_HEADER_HEIGHT_DP = 38
private const val WIDGET_DAY_TIMELINE_TOP_MARGIN_DP = 6
private const val WIDGET_DAY_HOUR_ROW_HEIGHT_DP = 46
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
private const val WIDGET_COLLECTION_RENDER_SIGNATURE_VERSION = 11
private const val WIDGET_TASK_MAX_DEPTH = WidgetTaskCardRenderer.MAX_DEPTH
internal const val WIDGET_TASK_ART_WIDTH_DP = 360
private const val WIDGET_TASK_MIN_CARD_WIDTH_DP = WidgetTaskCardRenderer.MIN_CARD_WIDTH_DP
private const val WIDGET_TASK_ROW_HEIGHT_DP = WidgetTaskCardRenderer.ROW_HEIGHT_DP
private const val WIDGET_TASK_CARD_HEIGHT_DP = WidgetTaskCardRenderer.CARD_HEIGHT_DP
private const val WIDGET_TASK_CARD_SIDE_INSET_DP = WidgetTaskCardRenderer.CARD_SIDE_INSET_DP
private const val WIDGET_TASK_STATUS_RADIUS_DP = WidgetTaskCardRenderer.STATUS_RADIUS_DP
private const val WIDGET_TASK_STATUS_STROKE_DP = WidgetTaskCardRenderer.STATUS_STROKE_DP
private const val WIDGET_TASK_SUBTASK_ARROW_HALF_WIDTH_DP = WidgetTaskCardRenderer.CHEVRON_HALF_WIDTH_DP
private const val WIDGET_TASK_SUBTASK_ARROW_HALF_HEIGHT_DP = WidgetTaskCardRenderer.CHEVRON_HALF_HEIGHT_DP
private const val WIDGET_TASK_SUBTASK_ARROW_STROKE_DP = WidgetTaskCardRenderer.CHEVRON_STROKE_DP
private val WIDGET_TASK_CARD_RENDERER = WidgetTaskCardRenderer()
private const val WIDGET_TASKS_PRIORITY_FRAME_COUNT = 20
private const val WIDGET_AGENDA_PRIORITY_FRAME_COUNT = 30
private const val WIDGET_COLLECTION_VIEW_TYPE_COUNT = 8
private const val WIDGET_AGENDA_ART_BITMAP_SCALE = 1.15f
private const val WIDGET_TASK_PRIORITY_BITMAP_SCALE = 1.15f
private const val WIDGET_TASK_TRANSITION_BITMAP_SCALE = 0.78f
private const val WIDGET_AGENDA_DATE_COLUMN_WIDTH_DP = 50
private const val WIDGET_AGENDA_COLUMN_GAP_DP = 12
private const val WIDGET_AGENDA_EVENT_ROW_HEIGHT_DP = 64
private const val WIDGET_AGENDA_SPAN_EVENT_ROW_HEIGHT_DP = 96
private const val WIDGET_DAY_EVENT_ROW_HEIGHT_DP = 52
private const val WIDGET_AGENDA_EVENT_CARD_RADIUS_DP = 13f
private const val WIDGET_DAY_EVENT_CARD_RADIUS_DP = 10f
private const val WIDGET_AGENDA_MAX_ROWS = 60
private const val WIDGET_AGENDA_LIST_SIDE_BLEED_DP = 12
private const val WIDGET_TASK_PRIORITY_OVERDRAW_DP = 20f
private const val WIDGET_TASK_SORT_MORPH_STEPS = 5
private const val WIDGET_TASK_SORT_MORPH_FRAME_DELAY_MS = 42L
private const val WIDGET_TASK_SORT_LABEL_TRANSITION_MS = 155L
private const val WIDGET_TASK_EXPANSION_STEPS = 5
private const val WIDGET_TASK_EXPANSION_FRAME_DELAY_MS = 34L
private const val WIDGET_TASK_TRANSITION_MIN_ROW_HEIGHT_DP = 1f
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
        KgsWidgetUpdateScheduler.update(context, kind, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        KgsWidgetUpdateScheduler.update(context, kind, intArrayOf(appWidgetId), debounceMillis = if (kind == KgsWidgetKind.Month) WIDGET_MONTH_RESIZE_DEBOUNCE_MS else 160)
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
                )
            }

            Intent.ACTION_CONFIGURATION_CHANGED -> {
                updateAsync(
                    context = context,
                    forceFullDayUpdate = kind == KgsWidgetKind.Day,
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
                    KgsWidgetUpdateScheduler.update(context, kind, intArrayOf(appWidgetId), debounceMillis = if (kind == KgsWidgetKind.Month) WIDGET_MONTH_RESIZE_DEBOUNCE_MS else 160)
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
    ) {
        val pendingResult = goAsync()
        KgsWidgetUpdateScheduler.launch {
            try {
                if (appWidgetIds == null) {
                    KgsWidgetUpdater.updateKind(
                        context.applicationContext,
                        kind,
                        forceFullDayUpdate = forceFullDayUpdate,
                    )
                } else {
                    KgsWidgetUpdater.update(
                        context.applicationContext,
                        kind,
                        appWidgetIds,
                        forceFullDayUpdate = forceFullDayUpdate,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun navigateDayAsync(context: Context, appWidgetId: Int) {
        val pendingResult = goAsync()
        KgsWidgetUpdateScheduler.launch {
            try {
                KgsWidgetUpdater.navigateDay(context.applicationContext, appWidgetId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun toggleDayAllDayAsync(context: Context, appWidgetId: Int) {
        val pendingResult = goAsync()
        KgsWidgetUpdateScheduler.launch {
            try {
                KgsWidgetUpdater.toggleDayAllDay(context.applicationContext, appWidgetId)
            } finally {
                pendingResult.finish()
            }
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
        val finished = AtomicBoolean(false)
        val finishBroadcast = {
            if (finished.compareAndSet(false, true)) {
                pendingResult.finish()
            }
        }
        KgsWidgetUpdateScheduler.launchLatest(
            key = "month-navigation:${kind.name}:$appWidgetId",
            onCompletion = finishBroadcast,
        ) {
            KgsWidgetUpdater.navigateMonth(
                context = context.applicationContext,
                kind = kind,
                appWidgetId = appWidgetId,
                snapshot = snapshot,
                onAcknowledged = finishBroadcast,
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
                            runCatching { renderer.collectionSnapshot(KgsWidgetKind.Tasks, appWidgetId) }.getOrNull()
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
                            val target = runCatching { renderer.collectionSnapshot(KgsWidgetKind.Tasks, appWidgetId) }.getOrNull()
                            target?.let {
                                animateTasksSubtaskToggle(context.applicationContext, before, it, taskId)
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

private fun KgsWidgetKind.priorityMotionFrameIds(): IntArray =
    if (usesAgendaCollectionStyle()) WIDGET_AGENDA_PRIORITY_MOTION_FRAME_IDS else WIDGET_TASK_PRIORITY_MOTION_FRAME_IDS

private fun KgsWidgetKind.priorityMotionFrameCount(): Int =
    if (usesAgendaCollectionStyle()) WIDGET_AGENDA_PRIORITY_FRAME_COUNT else WIDGET_TASKS_PRIORITY_FRAME_COUNT

private fun KgsWidgetKind.usesAgendaCollectionStyle(): Boolean =
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
            updateKind(context, kind)
        }
    }

    suspend fun updateKind(context: Context, kind: KgsWidgetKind, forceFullDayUpdate: Boolean = false) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, kind.providerClass))
        if (ids.isNotEmpty()) {
            update(context, kind, ids, forceFullDayUpdate = forceFullDayUpdate)
        }
    }

    suspend fun update(
        context: Context,
        kind: KgsWidgetKind,
        appWidgetIds: IntArray,
        forceFullDayUpdate: Boolean = false,
    ) {
        if (appWidgetIds.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        val renderer = KgsWidgetRenderer(context)
        appWidgetIds.forEach { appWidgetId ->
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
            val collectionSnapshot = if (kind.usesCollectionList && kind != KgsWidgetKind.Multi) {
                runCatching {
                    renderer.collectionSnapshot(kind, appWidgetId)
                }.onFailure { error ->
                    Log.w(TAG, "Failed to calculate ${kind.name} widget $appWidgetId snapshot", error)
                }.getOrNull()
            } else {
                null
            }
            val collectionSignature = collectionSnapshot?.signature
            if (collectionSignature != null && KgsWidgetCollectionUpdateSignatures.matches(kind, appWidgetId, collectionSignature)) {
                WidgetLog.d(context, "Skipped unchanged ${kind.name} widget $appWidgetId")
                return@forEach
            }
            val views = try {
                if (kind == KgsWidgetKind.Month) {
                    renderer.renderMonthUpdate(appWidgetId, options)
                        .also { monthResult = it }
                        .views
                } else if (incrementalDayUpdate) {
                    renderer.renderDayNavigationUpdate(
                        appWidgetId = appWidgetId,
                        options = options,
                    )
                } else {
                    renderer.render(kind, appWidgetId, options)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to render ${kind.name} widget $appWidgetId", error)
                renderer.error(kind, appWidgetId, error.message ?: "Widget update failed.")
            }
            val signature = monthResult?.signature
            if (
                targetMonthRevision != null &&
                KgsWidgetMonthState.revision(context, appWidgetId) != targetMonthRevision
            ) {
                return@forEach
            }
            if (signature != null && KgsWidgetMonthUpdateSignatures.matches(appWidgetId, signature)) {
                WidgetLog.d(context, "Skipped unchanged Month widget $appWidgetId")
                return@forEach
            }
            runCatching {
                if (
                    targetMonthRevision != null &&
                    KgsWidgetMonthState.revision(context, appWidgetId) != targetMonthRevision
                ) {
                    return@runCatching
                }
                if (collectionSnapshot != null) {
                    KgsWidgetCollectionRowsCache.put(collectionSnapshot)
                }
                if (incrementalDayUpdate) {
                    manager.partiallyUpdateAppWidget(appWidgetId, views)
                } else {
                    manager.updateAppWidget(appWidgetId, views)
                }
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
            }.onFailure { error ->
                Log.e(TAG, "Failed to update ${kind.name} widget $appWidgetId", error)
                if (
                    (kind.usesDirectCollectionItems() || (kind == KgsWidgetKind.Day && usesDirectDayGridItems())) &&
                    error.isRemoteViewsBitmapMemoryError()
                ) {
                    runCatching {
                        if (
                            targetMonthRevision != null &&
                            KgsWidgetMonthState.revision(context, appWidgetId) != targetMonthRevision
                        ) {
                            return@runCatching
                        }
                        val fallbackViews = renderer.render(
                            kind = kind,
                            appWidgetId = appWidgetId,
                            options = options,
                            forceServiceCollection = true,
                        )
                        manager.updateAppWidget(appWidgetId, fallbackViews)
                        manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
                    }.onFailure { fallbackError ->
                        Log.e(TAG, "Failed to update ${kind.name} widget $appWidgetId with service collection fallback", fallbackError)
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
        onAcknowledged: () -> Unit = {},
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

        fun applyIfCurrent(result: MonthWidgetRenderResult): Boolean {
            if (!KgsWidgetMonthState.isCurrent(context, snapshot)) return false
            return runCatching {
                if (!KgsWidgetMonthState.isCurrent(context, snapshot)) return false
                if (kind == KgsWidgetKind.Multi) {
                    manager.partiallyUpdateAppWidget(appWidgetId, result.views)
                } else {
                    manager.updateAppWidget(appWidgetId, result.views)
                }
                result.signature?.let { signature ->
                    KgsWidgetMonthUpdateSignatures.markApplied(appWidgetId, signature)
                }
                true
            }.onFailure { error ->
                Log.e(TAG, "Failed to apply ${kind.name} month page $appWidgetId", error)
            }.getOrDefault(false)
        }

        var generation = WidgetDataGeneration.current()
        val cachedPage = KgsWidgetMonthPageCache.get(snapshot.month, settings, zoneId.id, generation)
        val skeletonPage = pageSource.empty(snapshot.month, settings)
        val initialPage = selectMonthNavigationInitialPage(cachedPage, skeletonPage)

        if (!applyIfCurrent(
                renderer.render(
                    kind = kind,
                    snapshot = snapshot,
                    options = options,
                    settings = settings,
                    page = initialPage.page,
                    hasCompleteData = initialPage.stage == MonthNavigationPageStage.Complete,
                ),
            )
        ) {
            return
        }
        onAcknowledged()

        if (!KgsWidgetMonthState.isCurrent(context, snapshot)) return
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
            if (WidgetDataGeneration.current() != generation) {
                return
            }
        }
        if (!KgsWidgetMonthState.isCurrent(context, snapshot)) return
        if (WidgetDataGeneration.current() != generation) return
        KgsWidgetMonthPageCache.put(
            month = snapshot.month,
            settings = settings,
            zoneId = zoneId.id,
            page = authoritativePage,
            generation = generation,
        )
        warmWidgetMonthPageCache(context, zoneId, snapshot.month, settings)
        if (cachedPage != authoritativePage) {
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
            bitmap = dayGridRowBitmap(
                context = context,
                palette = palette,
                widthDp = widthDp,
                heightDp = rowHeightDp,
                omitPriorityCards = hasPriorityMotion,
            ),
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
                bitmap = dayPriorityMotionBitmap(
                    context = context,
                    palette = palette,
                    widthDp = widthDp,
                    heightDp = rowHeightDp,
                    frame = frame,
                    frameCount = frameIds.size,
                    frameIntervalMillis = frameIntervalMillis,
                    motionBounds = motionBounds,
                ),
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
            bitmap = dayNowLineOverlayBitmap(
                context = context,
                palette = palette,
                widthDp = widthDp,
                heightDp = rowHeightDp,
            ),
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
        bitmap: Bitmap,
    ) {
        if (!useImageUri) {
            setImageViewBitmap(viewId, bitmap)
            return
        }
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

private fun layoutWidgetDayTimedItems(items: List<WidgetDayTimedItem>): List<WidgetDayTimedLayout> {
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
    ): RemoteViews {
        val start = SystemClock.elapsedRealtime()
        val result = when (kind) {
            KgsWidgetKind.Month -> renderMonthUpdate(appWidgetId, options).views
            KgsWidgetKind.Day -> renderDayWidget(appWidgetId, options, forceServiceCollection = forceServiceCollection)
            KgsWidgetKind.Multi -> renderMultiWidget(appWidgetId, options, forceServiceCollection = forceServiceCollection)
            else -> renderCollectionWidget(kind, appWidgetId, options, forceServiceCollection)
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

    suspend fun renderMonthUpdate(appWidgetId: Int, options: Bundle): MonthWidgetRenderResult =
        renderMonthWidget(appWidgetId, options)

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
            signature = "$dataSignature\nrender|$WIDGET_COLLECTION_RENDER_SIGNATURE_VERSION|${renderSize.widthDp}x${renderSize.heightDp}|$taskArtWidthDp|$priorityFrameCount|$WIDGET_TASK_PRIORITY_OVERDRAW_DP|$WIDGET_TASK_PRIORITY_BITMAP_SCALE|$WIDGET_AGENDA_LIST_SIDE_BLEED_DP",
        )
    }

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

    private suspend fun renderMonthWidget(
        appWidgetId: Int,
        options: Bundle,
    ): MonthWidgetRenderResult {
        val settings = dataSource.loadSettings(KgsWidgetKind.Month)
        val month = KgsWidgetMonthState.month(context, appWidgetId, YearMonth.now(zoneId))
        val generation = WidgetDataGeneration.current()
        val page = monthPageSource.load(month, settings)
        if (WidgetDataGeneration.current() == generation) {
            KgsWidgetMonthPageCache.put(month, settings, zoneId.id, page, generation)
            warmWidgetMonthPageCache(context, zoneId, month, settings)
        }
        return renderMonthPageResult(appWidgetId, options, settings, page, hasCompleteData = true)
    }

    private fun renderMonthPageResult(
        appWidgetId: Int,
        options: Bundle,
        settings: WidgetRenderSettings,
        page: WidgetMonthPage,
        hasCompleteData: Boolean,
    ): MonthWidgetRenderResult {
        val today = LocalDate.now(zoneId)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val renderedPage = if (hasCompleteData) page else page.loadingSkeleton(palette.muted)
        val currentSize = WidgetSize.from(context, options, KgsWidgetKind.Month)
        val renderSpec = WidgetMonthRenderSpec.from(currentSize, renderedPage.rowCount)

        var itemCount = 0
        for (cell in renderedPage.cells) itemCount += cell.items.size
        WidgetLog.d(
            context,
            "Month widget $appWidgetId bucket=${renderSpec.bucket.name} rows=${renderedPage.rowCount} weekHeight=${renderSpec.weekCellHeightDp} items=$itemCount complete=$hasCompleteData",
        )

        return MonthWidgetRenderResult(
            views = renderMonthRoot(
                appWidgetId = appWidgetId,
                settings = settings,
                palette = palette,
                today = today,
                page = renderedPage,
                renderSpec = renderSpec,
            ),
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
                chip.setMonthSpanChipImage(
                    appWidgetId = appWidgetId,
                    viewId = R.id.widget_month_span_chip_background,
                    cacheKey = monthSpanChipCacheKey(
                        segment = segment,
                        style = style,
                        widthDp = renderSpec.spanChipWidthDp(span),
                        textSp = renderSpec.chipTextSp,
                    ),
                    bitmap = monthSpanChipBitmap(
                        context = context,
                        style = style,
                        item = segment.item,
                        title = segment.title,
                        widthDp = renderSpec.spanChipWidthDp(span),
                        textSp = renderSpec.chipTextSp,
                    ),
                )
                chip.setTextViewText(R.id.widget_month_span_chip_text, "")
                chip.setViewVisibility(R.id.widget_month_span_chip_text, View.GONE)
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

    private fun RemoteViews.setMonthSpanChipImage(
        appWidgetId: Int,
        viewId: Int,
        cacheKey: String,
        bitmap: Bitmap,
    ) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            setImageViewBitmap(viewId, bitmap)
            return
        }
        val uri = runCatching {
            KgsWidgetBitmapUriStore.put(context, appWidgetId, cacheKey, bitmap)
        }.onFailure { error ->
            Log.w(TAG, "Failed to cache Month widget chip image; falling back to an inline bitmap", error)
        }.getOrNull()
        if (uri != null) {
            setImageViewUri(viewId, uri)
            bitmap.recycle()
        } else {
            setImageViewBitmap(viewId, bitmap)
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
    ): RemoteViews {
        val today = LocalDate.now(zoneId)
        val settings = dataSource.loadSettings(KgsWidgetKind.Multi)
        val textContext = context.withWidgetLocale(settings.locale)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val size = WidgetSize.from(context, options, KgsWidgetKind.Multi)
        val contentPaddingDp = 12
        val contentHeightDp = (size.heightDp - contentPaddingDp).coerceAtLeast(2)
        val monthPercent = SettingsStore.normalizeMultiWidgetMonthPercent(settings.multiWidgetMonthPercent)
        val monthPanelHeightDp = ((contentHeightDp * monthPercent) / 100f)
            .roundToInt()
            .coerceIn(1, contentHeightDp - 1)
        val agendaPanelHeightDp = (contentHeightDp - monthPanelHeightDp).coerceAtLeast(1)
        val month = KgsWidgetMonthState.month(context, appWidgetId, YearMonth.from(today))
        val generation = WidgetDataGeneration.current()
        val page = monthPageSource.load(month, settings)
        if (WidgetDataGeneration.current() == generation) {
            KgsWidgetMonthPageCache.put(month, settings, zoneId.id, page, generation)
            warmWidgetMonthPageCache(context, zoneId, month, settings)
        }
        val monthSpec = WidgetMonthRenderSpec.from(
            WidgetSize(widthDp = size.widthDp, heightDp = monthPanelHeightDp),
            page.rowCount,
        )
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
        val showingCurrentMonth = month == YearMonth.from(today)
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
            openAppPendingIntent(KgsWidgetKind.Month, 66_000 + appWidgetId, month.atDay(1)),
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
                rows = dataSource.listRows(KgsWidgetKind.Multi, settings, appWidgetId),
                sourceKind = KgsWidgetKind.Multi,
                appWidgetId = appWidgetId,
                renderOptions = WidgetCollectionRenderOptions(taskArtWidthDp = size.collectionArtWidthDp(KgsWidgetKind.Multi)),
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
    ): RemoteViews {
        val today = LocalDate.now(zoneId)
        val settings = dataSource.loadSettings(kind)
        val textContext = context.withWidgetLocale(settings.locale)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val size = WidgetSize.from(context, options, kind)
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
            views.bindDirectCollectionItems(
                context = textContext,
                packageName = packageName,
                palette = palette,
                rows = dataSource.listRows(kind, settings, appWidgetId),
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

class KgsWidgetCollectionService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val kind = intent.getStringExtra(EXTRA_WIDGET_KIND)
            ?.let { runCatching { KgsWidgetKind.valueOf(it) }.getOrNull() }
            ?: KgsWidgetKind.Agenda
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return if (kind == KgsWidgetKind.Day) {
            KgsWidgetDayCollectionFactory(applicationContext, appWidgetId)
        } else {
            KgsWidgetCollectionFactory(applicationContext, kind, appWidgetId)
        }
    }
}

private class KgsWidgetDayCollectionFactory(
    private val context: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {
    private val packageName = context.packageName
    private var rows: List<WidgetDayGridRow> = emptyList()
    private var rowViews: List<RemoteViews> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val startedAt = SystemClock.elapsedRealtime()
        runCatching {
            val snapshot = runBlocking {
                KgsWidgetRenderer(context).dayGridCollectionSnapshot(appWidgetId)
            }
            val textContext = context.withWidgetLocale(snapshot.settings.locale)
            rows = snapshot.rows
            rowViews = rows.map { row ->
                row.toRemoteViews(
                    context = textContext,
                    packageName = packageName,
                    palette = snapshot.palette,
                    widthDp = snapshot.widthDp,
                    appWidgetId = appWidgetId,
                )
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to load Day widget grid rows", error)
            rows = emptyList()
            rowViews = emptyList()
        }
        WidgetLog.d(context, "Loaded ${rows.size} Day widget grid rows in ${SystemClock.elapsedRealtime() - startedAt}ms")
    }

    override fun onDestroy() {
        rows = emptyList()
        rowViews = emptyList()
    }

    override fun getCount(): Int = rowViews.size

    override fun getViewAt(position: Int): RemoteViews {
        return rowViews.getOrNull(position)
            ?: RemoteViews(packageName, R.layout.widget_collection_spacer)
    }

    override fun getLoadingView(): RemoteViews = RemoteViews(packageName, R.layout.widget_collection_spacer)

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long = rows.getOrNull(position)?.stableId ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

private class KgsWidgetCollectionFactory(
    private val context: Context,
    private val kind: KgsWidgetKind,
    private val appWidgetId: Int,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : RemoteViewsService.RemoteViewsFactory {
    private val packageName = context.packageName
    private var rows: List<WidgetListRow> = emptyList()
    private var rowViews: List<RemoteViews> = emptyList()
    private var settings = WidgetRenderSettings()
    private var palette = WidgetPalette.from(context, AppThemeMode.KgsBlue, AppColorMode.Auto)
    private var renderOptions = WidgetCollectionRenderOptions(
        taskArtWidthDp = WidgetSize.from(
            context,
            AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId),
            kind,
        ).collectionArtWidthDp(kind),
    )

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val startedAt = SystemClock.elapsedRealtime()
        runCatching {
            val cached = KgsWidgetCollectionRowsCache.get(kind, appWidgetId)
            if (cached != null) {
                settings = cached.settings
                palette = cached.palette
                rows = cached.rows
                renderOptions = renderOptions.withTaskArtWidth(cached.taskArtWidthDp)
            } else {
                runBlocking {
                    val dataSource = KgsWidgetDataSource(context, zoneId)
                    settings = dataSource.loadSettings(kind)
                    palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
                    rows = dataSource.listRows(kind, settings, appWidgetId)
                    renderOptions = renderOptions.withTaskArtWidth(
                        WidgetSize.from(
                            context,
                            AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId),
                            kind,
                        ).collectionArtWidthDp(kind),
                    )
                }
            }
            val textContext = context.withWidgetLocale(settings.locale)
            rowViews = rows.map { row ->
                row.toRemoteViews(textContext, packageName, palette, kind, appWidgetId, renderOptions)
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to load ${kind.name} widget rows", error)
            rows = emptyList()
            rowViews = emptyList()
        }
        WidgetLog.d(context, "Loaded ${rows.size} ${kind.name} widget rows in ${SystemClock.elapsedRealtime() - startedAt}ms")
    }

    override fun onDestroy() {
        rows = emptyList()
        rowViews = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews =
        rowViews.getOrNull(position)
            ?: RemoteViews(packageName, R.layout.widget_collection_spacer)

    override fun getLoadingView(): RemoteViews = RemoteViews(packageName, R.layout.widget_collection_spacer)

    override fun getViewTypeCount(): Int = WIDGET_COLLECTION_VIEW_TYPE_COUNT

    override fun getItemId(position: Int): Long = rows.getOrNull(position)?.stableId ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

internal class KgsWidgetDataSource(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private fun textContext(settings: WidgetRenderSettings): Context =
        context.withWidgetLocale(settings.locale)

    private fun textContext(locale: Locale): Context =
        context.withWidgetLocale(locale)

    suspend fun loadSettings(kind: KgsWidgetKind): WidgetRenderSettings {
        val graph = KgsCalendarApplication.graph(context)
        val appThemeMode = graph.settingsStore.themeMode.first()
        val appColorMode = graph.settingsStore.colorMode.first()
        val monthWidgetThemeMode = graph.settingsStore.monthWidgetThemeMode.first()
        val monthWidgetColorMode = graph.settingsStore.monthWidgetColorMode.first()
        val widgetThemeMode = when (kind) {
            KgsWidgetKind.Agenda -> graph.settingsStore.agendaWidgetThemeMode.first()
            KgsWidgetKind.Month -> monthWidgetThemeMode
            KgsWidgetKind.Tasks -> graph.settingsStore.tasksWidgetThemeMode.first()
            KgsWidgetKind.Multi -> graph.settingsStore.multiWidgetThemeMode.first()
            KgsWidgetKind.Day -> graph.settingsStore.dayWidgetThemeMode.first()
        }
        val widgetColorMode = when (kind) {
            KgsWidgetKind.Agenda -> graph.settingsStore.agendaWidgetColorMode.first()
            KgsWidgetKind.Month -> monthWidgetColorMode
            KgsWidgetKind.Tasks -> graph.settingsStore.tasksWidgetColorMode.first()
            KgsWidgetKind.Multi -> graph.settingsStore.multiWidgetColorMode.first()
            KgsWidgetKind.Day -> graph.settingsStore.dayWidgetColorMode.first()
        }
        val systemNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return WidgetRenderSettings(
            locale = graph.settingsStore.languageMode.first().toLocale(context),
            firstDayOfWeek = graph.settingsStore.firstDayOfWeek.first(),
            hiddenCollectionHrefs = graph.settingsStore.hiddenCollectionHrefs.first(),
            showCompletedTasks = graph.settingsStore.showCompletedTasksInCalendar.first(),
            themeMode = widgetThemeMode.resolve(appThemeMode),
            colorMode = widgetColorMode.resolve(appColorMode),
            systemNightMode = systemNightMode,
            taskColorMode = graph.settingsStore.taskColorMode.first(),
            priorityAnimationsEnabled = graph.settingsStore.priorityAnimationsEnabled.first(),
            subtasksExpandedByDefault = graph.settingsStore.subtasksExpandedByDefault.first(),
            tasksWidgetDisplayMode = graph.settingsStore.tasksWidgetDisplayMode.first(),
            tasksWidgetIncludeOverdue = graph.settingsStore.tasksWidgetIncludeOverdue.first(),
            tasksWidgetSortMode = graph.settingsStore.tasksWidgetSortMode.first(),
            tasksWidgetCreateMode = graph.settingsStore.tasksWidgetCreateMode.first(),
            tasksWidgetSubtaskDefaultMode = graph.settingsStore.tasksWidgetSubtaskDefaultMode.first(),
            maxVisibleAllDayItems = graph.settingsStore.maxVisibleAllDayItems.first(),
            dayWidgetScalePercent = graph.settingsStore.dayWidgetScalePercent.first(),
            dayWidgetStartHour = graph.settingsStore.dayWidgetStartHour.first(),
            dayWidgetStartAtCurrentHour = graph.settingsStore.dayWidgetStartAtCurrentHour.first(),
            multiWidgetMonthPercent = graph.settingsStore.multiWidgetMonthPercent.first(),
        )
    }

    suspend fun dayTimeline(day: LocalDate, settings: WidgetRenderSettings): WidgetDayTimeline {
        val graph = KgsCalendarApplication.graph(context)
        val labels = textContext(settings)
        val start = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val events = graph.repository.eventsSnapshot(start, end)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
        val tasks = graph.repository.datedTasksSnapshot(start, end)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
            .filter { settings.showCompletedTasks || !it.isCompleted }
        val allDayItems = buildList {
            events
                .filter { it.isWidgetAllDayTopItemOn(day, zoneId) }
                .sortedWith(compareBy<EventEntity> { it.startsAtMillis }.thenBy { it.title.lowercase(settings.locale) })
                .forEach { event ->
                    add(
                        WidgetDayItem(
                            title = event.title.ifBlank { labels.getString(R.string.no_title) },
                            meta = labels.getString(R.string.all_day),
                            color = event.displayColor(),
                            completed = false,
                            isTask = false,
                            stableKey = "event:${event.resourceHref}",
                            location = event.location,
                            eventStatus = event.status,
                            eventResourceHref = event.resourceHref,
                        ),
                    )
                }
            tasks
                .filter { it.isWidgetFullDayTaskOn(day, zoneId) }
                .sortedWith(compareBy<TaskEntity> { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }.thenBy { it.title.lowercase(settings.locale) })
                .forEach { task ->
                    add(
                        WidgetDayItem(
                            title = task.title.ifBlank { labels.getString(R.string.no_title) },
                            meta = labels.getString(R.string.task),
                            color = task.displayColor(settings.taskColorMode),
                            completed = task.isCompleted,
                            isTask = true,
                            stableKey = "task:${task.resourceHref}",
                            location = task.location,
                            taskResourceHref = task.resourceHref,
                            statusGlyph = task.widgetStatusGlyph(),
                            priority = task.priority,
                        ),
                    )
                }
        }
        val timedItems = buildList {
            events.forEach { event ->
                event.widgetTimedPlacementOn(day, zoneId)?.let { placement ->
                    val item = WidgetDayItem(
                        title = event.title.ifBlank { labels.getString(R.string.no_title) },
                        meta = placement.timeRangeText(),
                        color = event.displayColor(),
                        completed = false,
                        isTask = false,
                        stableKey = "event:${event.resourceHref}",
                        location = event.location,
                        eventStatus = event.status,
                        eventResourceHref = event.resourceHref,
                    )
                    add(WidgetDayTimedItem(item, placement.first, placement.second))
                }
            }
            tasks.forEach { task ->
                task.widgetTimedPlacementOn(day, zoneId)?.let { placement ->
                    val item = WidgetDayItem(
                        title = task.title.ifBlank { labels.getString(R.string.no_title) },
                        meta = placement.timeRangeText(),
                        color = task.displayColor(settings.taskColorMode),
                        completed = task.isCompleted,
                        isTask = true,
                        stableKey = "task:${task.resourceHref}",
                        location = task.location,
                        taskResourceHref = task.resourceHref,
                        statusGlyph = task.widgetStatusGlyph(),
                        priority = task.priority,
                    )
                    add(WidgetDayTimedItem(item, placement.first, placement.second))
                }
            }
        }
        val timedLayouts = layoutWidgetDayTimedItems(timedItems)
        return WidgetDayTimeline(
            day = day,
            allDayItems = allDayItems,
            timedItems = timedLayouts,
            signature = buildString {
                append(WIDGET_DAY_RENDER_SIGNATURE_VERSION)
                append('|').append(day.toEpochDay())
                append('|').append(settings.locale.toLanguageTag())
                append('|').append(settings.hiddenCollectionHrefs.sorted().joinToString(","))
                append('|').append(settings.showCompletedTasks)
                append('|').append(settings.themeMode.name)
                append('|').append(settings.colorMode.name)
                append('|').append(settings.systemNightMode)
                append('|').append(settings.taskColorMode.name)
                allDayItems.forEach { item ->
                    append("\na|").append(item.stableKey).append('|').append(item.title).append('|').append(item.color).append('|').append(item.completed)
                        .append('|').append(item.location.orEmpty()).append('|').append(item.eventStatus.orEmpty())
                }
                timedLayouts.forEach { item ->
                    append("\nt|").append(item.item.stableKey).append('|').append(item.startMinute).append('|').append(item.endMinute)
                        .append('|').append(item.item.title).append('|').append(item.item.color).append('|').append(item.item.completed)
                        .append('|').append(item.item.location.orEmpty()).append('|').append(item.item.eventStatus.orEmpty())
                }
            },
        )
    }

    suspend fun listRows(kind: KgsWidgetKind, settings: WidgetRenderSettings, appWidgetId: Int): List<WidgetListRow> = when (kind) {
        KgsWidgetKind.Agenda -> loadAgendaItems(days = 45, settings = settings)
            .filter { it.sortMillis >= todayStartMillis() }
            .sortedWith(compareBy<WidgetListRow> { it.sortMillis }.thenBy { it.title.lowercase(settings.locale) })
            .take(WIDGET_AGENDA_MAX_ROWS)
            .withAgendaSections()

        KgsWidgetKind.Day -> loadDayItems(LocalDate.now(zoneId), settings).take(80)

        KgsWidgetKind.Multi -> loadAgendaItems(days = 45, settings = settings)
            .filter { it.sortMillis >= todayStartMillis() }
            .sortedWith(compareBy<WidgetListRow> { it.sortMillis }.thenBy { it.title.lowercase(settings.locale) })
            .take(WIDGET_AGENDA_MAX_ROWS)
            .withAgendaSections()

        KgsWidgetKind.Tasks -> loadTaskItems(settings, appWidgetId)
            .take(150)
            .ifEmpty { listOf(WidgetListRow.empty(kind.emptyText(textContext(settings)))) }
        KgsWidgetKind.Month -> emptyList()
    }

    suspend fun collectionSignature(
        kind: KgsWidgetKind,
        settings: WidgetRenderSettings,
        appWidgetId: Int,
        rows: List<WidgetListRow>? = null,
    ): String =
        buildString {
            append(kind.name)
            append('|').append(LocalDate.now(zoneId).toEpochDay())
            append('|').append(settings.locale.toLanguageTag())
            append('|').append(settings.hiddenCollectionHrefs.sorted().joinToString(","))
            append('|').append(settings.showCompletedTasks)
            append('|').append(settings.themeMode.name)
            append('|').append(settings.colorMode.name)
            append('|').append(settings.systemNightMode)
            append('|').append(settings.taskColorMode.name)
            append('|').append(settings.priorityAnimationsEnabled)
            append('|').append(settings.subtasksExpandedByDefault)
            append('|').append(settings.tasksWidgetDisplayMode.name)
            append('|').append(settings.tasksWidgetIncludeOverdue)
            append('|').append(settings.tasksWidgetSortMode.name)
            append('|').append(settings.tasksWidgetCreateMode.name)
            append('|').append(settings.tasksWidgetSubtaskDefaultMode.name)
            append('|').append(settings.dayWidgetScalePercent)
            append('|').append(settings.dayWidgetStartHour)
            append('|').append(settings.dayWidgetStartAtCurrentHour)
            append('|').append(settings.multiWidgetMonthPercent)
            if (kind == KgsWidgetKind.Day && settings.dayWidgetStartAtCurrentHour) {
                append('|').append(LocalTime.now(zoneId).hour)
            }
            (rows ?: listRows(kind, settings, appWidgetId)).forEach { row ->
                append('\n')
                row.appendSignatureTo(this)
            }
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
        val labels = textContext(settings)
        val startDate = Instant.ofEpochMilli(startMillis).atZone(zoneId).toLocalDate()
        val endDateExclusive = Instant.ofEpochMilli(endMillis).atZone(zoneId).toLocalDate()
        val eventSnapshot = graph.repository.eventsSnapshot(startMillis, endMillis)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
        val taskSnapshot = graph.repository.datedTasksSnapshot(startMillis, endMillis)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
            .filter { settings.showCompletedTasks || !it.isCompleted }
        val events = if (launchKind == KgsWidgetKind.Agenda) {
            buildAgendaEventRows(
                events = eventSnapshot,
                tasks = taskSnapshot,
                labels = labels,
                rangeStart = startDate,
                rangeEndExclusive = endDateExclusive,
            )
        } else {
            eventSnapshot.map { event -> event.toListRow(settings.locale, launchKind, labels) }
        }
        val tasks = taskSnapshot
            .map { task ->
                if (launchKind == KgsWidgetKind.Agenda) {
                    task.toAgendaTaskRow(settings, labels)
                } else {
                    task.toListRow(settings.locale, settings.taskColorMode, launchKind, labels)
                }
            }
        return events + tasks
    }

    private fun List<WidgetListRow>.withAgendaSections(): List<WidgetListRow> =
        buildList {
            var currentYear: Int? = null
            var currentDate: LocalDate? = null
            this@withAgendaSections.forEach { row ->
                if (row.date.year != currentYear) {
                    currentYear = row.date.year
                    currentDate = null
                    add(WidgetListRow.section(row.date.year.toString()))
                }
                val showDate = row.date != currentDate
                add(row.copy(showAgendaDate = showDate))
                currentDate = row.date
            }
        }

    private suspend fun loadTaskItems(settings: WidgetRenderSettings, appWidgetId: Int): List<WidgetListRow> {
        val graph = KgsCalendarApplication.graph(context)
        val today = LocalDate.now(zoneId)
        val allTasks = graph.repository.allTasksSnapshot()
            .distinctBy { it.resourceHref }
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
        val activeTasks = allTasks.filter { it.isWidgetActiveTask() }
        val selectedTasks = when (settings.tasksWidgetDisplayMode) {
            WidgetTaskDisplayMode.Planned -> activeTasks.filter { it.widgetTaskDate(zoneId) != null }
            WidgetTaskDisplayMode.Unplanned -> activeTasks.filter { it.widgetTaskDate(zoneId) == null }
            WidgetTaskDisplayMode.Today -> activeTasks.filter { task ->
                val date = task.widgetTaskDate(zoneId) ?: return@filter false
                date == today || (settings.tasksWidgetIncludeOverdue && date.isBefore(today))
            }
        }
        val visibleActiveTasks = includeDescendantTasks(selectedTasks, allTasks)
        val visibleTasks = includeAncestorTasks(visibleActiveTasks, allTasks)
        return visibleTasks.toTaskHierarchy(settings, appWidgetId)
    }

    private fun includeDescendantTasks(selectedTasks: List<TaskEntity>, allTasks: List<TaskEntity>): List<TaskEntity> {
        val selectedByResource = selectedTasks.associateBy { it.resourceHref }
        if (selectedByResource.isEmpty()) return emptyList()
        val included = LinkedHashMap(selectedByResource)
        val childrenByParent = allTasks
            .filter { !it.parentUid.isNullOrBlank() }
            .groupBy { it.collectionHref to it.parentUid.orEmpty() }
        val queue = ArrayDeque(selectedTasks)
        val traversed = selectedTasks.mapTo(mutableSetOf()) { it.resourceHref }
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            childrenByParent[parent.collectionHref to parent.uid].orEmpty().forEach { child ->
                if (traversed.add(child.resourceHref)) {
                    queue.add(child)
                }
                if (child.isWidgetActiveTask()) {
                    included.putIfAbsent(child.resourceHref, child)
                }
            }
        }
        return included.values.toList()
    }

    private fun includeAncestorTasks(selectedTasks: List<TaskEntity>, allTasks: List<TaskEntity>): List<TaskEntity> {
        if (selectedTasks.isEmpty()) return emptyList()
        val byCollectionUid = allTasks.associateBy { it.collectionHref to it.uid }
        val globallyUniqueByUid = allTasks.groupBy { it.uid }
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
        val included = LinkedHashMap<String, TaskEntity>()

        fun candidateParent(task: TaskEntity): TaskEntity? {
            val parentUid = task.parentUid?.takeIf { it.isNotBlank() } ?: return null
            return byCollectionUid[task.collectionHref to parentUid] ?: globallyUniqueByUid[parentUid]
        }

        selectedTasks.forEach { task ->
            var cursor: TaskEntity? = task
            val seen = mutableSetOf<String>()
            while (cursor != null && seen.add(cursor.resourceHref)) {
                included.putIfAbsent(cursor.resourceHref, cursor)
                cursor = candidateParent(cursor)
            }
        }
        return included.values.toList()
    }

    private fun List<TaskEntity>.toTaskHierarchy(settings: WidgetRenderSettings, appWidgetId: Int): List<WidgetListRow> {
        if (isEmpty()) return emptyList()
        val distinctTasks = distinctBy { it.resourceHref }
        val byCollectionUid = distinctTasks.associateBy { it.collectionHref to it.uid }
        val globallyUniqueByUid = distinctTasks.groupBy { it.uid }
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
        val comparator = settings.taskComparator()

        fun candidateParent(task: TaskEntity): TaskEntity? {
            val parentUid = task.parentUid?.takeIf { it.isNotBlank() } ?: return null
            return byCollectionUid[task.collectionHref to parentUid] ?: globallyUniqueByUid[parentUid]
        }

        val parentByResource = distinctTasks.associate { task ->
            var parent = candidateParent(task)
            val seen = mutableSetOf(task.resourceHref)
            while (parent != null && seen.add(parent.resourceHref)) {
                parent = candidateParent(parent)
            }
            task.resourceHref to if (parent == null) candidateParent(task) else null
        }
        val childrenByParent = distinctTasks
            .mapNotNull { child -> parentByResource[child.resourceHref]?.resourceHref?.let { it to child } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, children) -> children.sortedWith(comparator) }
        val roots = distinctTasks.filter { task ->
            parentByResource[task.resourceHref] == null
        }.sortedWith(comparator)
        val defaultExpanded = settings.tasksWidgetSubtaskDefaultMode
            .resolveSubtasksExpandedByDefault(settings.subtasksExpandedByDefault)
        return buildList {
            val emitted = mutableSetOf<String>()
            fun append(task: TaskEntity, depth: Int, continuationLevels: Set<Int>, lastSibling: Boolean) {
                if (!emitted.add(task.resourceHref)) return
                val children = childrenByParent[task.resourceHref].orEmpty()
                val boundedDepth = depth.coerceAtMost(WIDGET_TASK_MAX_DEPTH)
                val expanded = KgsWidgetTaskExpansionState.isExpanded(
                    context = context,
                    appWidgetId = appWidgetId,
                    taskResourceHref = task.resourceHref,
                    defaultExpanded = defaultExpanded,
                )
                add(
                    task.toTaskRow(
                        settings = settings,
                        depth = boundedDepth,
                        childCount = children.size,
                        continuationLevels = continuationLevels.filter { it < WIDGET_TASK_MAX_DEPTH }.toSet(),
                        lastSibling = lastSibling,
                        subtasksExpanded = expanded,
                    ),
                )
                if (!expanded) return
                children.forEachIndexed { index, child ->
                    val childLast = index == children.lastIndex
                    val childContinuationLevels = if (childLast) continuationLevels else continuationLevels + boundedDepth
                    append(child, boundedDepth + 1, childContinuationLevels, childLast)
                }
            }
            roots.forEachIndexed { index, root ->
                append(root, 0, emptySet(), index == roots.lastIndex)
            }
            distinctTasks
                .filterNot { it.resourceHref in emitted }
                .filter { parentByResource[it.resourceHref] == null }
                .forEach {
                append(it, 0, emptySet(), true)
            }
        }
    }

    private fun todayStartMillis(): Long =
        LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()

    private fun EventEntity.toListRow(locale: Locale, launchKind: KgsWidgetKind, labels: Context): WidgetListRow {
        val start = Instant.ofEpochMilli(startsAtMillis).atZone(zoneId)
        val end = Instant.ofEpochMilli(endsAtMillis).atZone(zoneId)
        val titleText = title.ifBlank { labels.getString(R.string.no_title) }
        val metaText = if (allDay) {
            if (launchKind == KgsWidgetKind.Agenda) {
                labels.getString(R.string.all_day)
            } else {
                "${start.toLocalDate().relativeDateLabel(locale)} - ${labels.getString(R.string.all_day)}"
            }
        } else {
            val timeText = "${start.toLocalTime().timeText()} - ${end.toLocalTime().timeText()}"
            if (launchKind == KgsWidgetKind.Agenda) {
                timeText
            } else {
                "${start.toLocalDate().relativeDateLabel(locale)} - $timeText"
            }
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
            eventResourceHref = resourceHref,
            location = location?.takeIf { it.isNotBlank() },
            eventStatus = status,
            endMillis = endsAtMillis,
        )
    }

    private fun buildAgendaEventRows(
        events: List<EventEntity>,
        tasks: List<TaskEntity>,
        labels: Context,
        rangeStart: LocalDate,
        rangeEndExclusive: LocalDate,
    ): List<WidgetListRow> {
        val taskDates = tasks
            .mapNotNull { it.widgetTaskDate(zoneId) }
            .toSet()
        val eventDatesByResource = events.associate { event ->
            event.resourceHref to event.visibleWidgetAgendaDates(rangeStart, rangeEndExclusive)
        }
        return events.flatMap { event ->
            val dates = eventDatesByResource[event.resourceHref].orEmpty()
            if (dates.size <= 1) {
                dates.firstOrNull()?.let { date ->
                    listOf(event.toAgendaSpanRow(labels, date, date))
                }.orEmpty()
            } else {
                val interruptionDates = dates.filterTo(mutableSetOf()) { date ->
                    date in taskDates || events.any { other ->
                        other.resourceHref != event.resourceHref &&
                            eventDatesByResource[other.resourceHref].orEmpty().contains(date)
                    }
                }
                event.toAgendaSpanRows(labels, dates, interruptionDates)
            }
        }
    }

    private fun EventEntity.visibleWidgetAgendaDates(
        rangeStart: LocalDate,
        rangeEndExclusive: LocalDate,
    ): List<LocalDate> {
        val startDate = startsAtMillis.toDate(zoneId)
        val endDate = endDateInclusive(zoneId).coerceAtLeast(startDate)
        val first = maxOf(startDate, rangeStart)
        val last = minOf(endDate, rangeEndExclusive.minusDays(1))
        if (last.isBefore(first)) return emptyList()
        val dates = mutableListOf<LocalDate>()
        var date = first
        var guard = 0
        while (!date.isAfter(last) && guard < 370) {
            dates += date
            date = date.plusDays(1)
            guard++
        }
        return dates
    }

    private fun EventEntity.toAgendaSpanRows(
        labels: Context,
        dates: List<LocalDate>,
        interruptionDates: Set<LocalDate>,
    ): List<WidgetListRow> {
        val sortedDates = dates.sorted()
        if (sortedDates.isEmpty()) return emptyList()
        val rows = mutableListOf<WidgetListRow>()
        var segmentStart: LocalDate? = null
        var previous: LocalDate? = null

        fun flushSegment(end: LocalDate) {
            val start = segmentStart ?: return
            if (!end.isBefore(start)) {
                rows += toAgendaSpanRow(labels, start, end)
            }
            segmentStart = null
        }

        sortedDates.forEach { date ->
            val last = previous
            if (last != null && last.plusDays(1) != date) {
                flushSegment(last)
            }
            if (date in interruptionDates) {
                flushSegment(date.minusDays(1))
                rows += toAgendaSpanRow(labels, date, date)
            } else if (segmentStart == null) {
                segmentStart = date
            }
            previous = date
        }
        previous?.let(::flushSegment)
        return rows
    }

    private fun EventEntity.toAgendaSpanRow(
        labels: Context,
        startDate: LocalDate,
        endDate: LocalDate,
    ): WidgetListRow {
        val start = Instant.ofEpochMilli(startsAtMillis).atZone(zoneId)
        val end = Instant.ofEpochMilli(endsAtMillis).atZone(zoneId)
        val titleText = title.ifBlank { labels.getString(R.string.no_title) }
        val spansDays = endDate.isAfter(startDate)
        val metaText = when {
            spansDays && allDay -> "${startDate.widgetSpanDateText(labels)} - ${endDate.widgetSpanDateText(labels)}, ${labels.getString(R.string.all_day)}"
            spansDays -> "${startDate.widgetSpanDateText(labels)} - ${endDate.widgetSpanDateText(labels)}"
            allDay -> labels.getString(R.string.all_day)
            else -> "${start.toLocalTime().timeText()} - ${end.toLocalTime().timeText()}"
        }
        val rowStartMillis = if (startDate == startsAtMillis.toDate(zoneId)) {
            startsAtMillis
        } else {
            startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        }
        return WidgetListRow.item(
            title = titleText,
            meta = metaText,
            color = displayColor(),
            sortMillis = rowStartMillis,
            date = startDate,
            completed = false,
            allDaySort = if (allDay) 0 else 1,
            launchKind = KgsWidgetKind.Agenda,
            stableKey = "event:$resourceHref:${startDate.toEpochDay()}:${endDate.toEpochDay()}",
            eventResourceHref = resourceHref,
            location = location?.takeIf { it.isNotBlank() },
            eventStatus = status,
            endMillis = endsAtMillis,
            spanEndDate = endDate.takeIf { spansDays },
        )
    }

    private fun TaskEntity.toListRow(locale: Locale, taskColorMode: TaskColorMode, launchKind: KgsWidgetKind, labels: Context): WidgetListRow {
        val millis = startAtMillis ?: dueAtMillis
        val date = millis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
        val time = millis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime() }
        val hasTime = if (startAtMillis != null) startHasTime else dueHasTime
        val dateText = date?.relativeDateLabel(locale) ?: labels.getString(R.string.no_date)
        val timeText = if (time != null && hasTime) " - ${time.timeText()}" else ""
        return WidgetListRow.item(
            title = title.ifBlank { labels.getString(R.string.no_title) },
            meta = "${labels.getString(R.string.task)} - $dateText$timeText",
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

    private fun TaskEntity.toAgendaTaskRow(settings: WidgetRenderSettings, labels: Context): WidgetListRow {
        val millis = startAtMillis ?: dueAtMillis
        val date = widgetTaskDate(zoneId) ?: LocalDate.now(zoneId)
        return WidgetListRow.task(
            title = title.ifBlank { labels.getString(R.string.no_title) },
            meta = localizedWidgetTaskTimeLabel(settings.locale, labels),
            color = displayColor(settings.taskColorMode),
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
            depth = 0,
            childCount = 0,
            continuationLevels = emptySet(),
            lastSibling = true,
            subtasksExpanded = false,
            priority = priority,
            priorityMotionEnabled = settings.priorityAnimationsEnabled,
            launchKind = KgsWidgetKind.Agenda,
        )
    }

    private fun TaskEntity.localizedWidgetTaskTimeLabel(locale: Locale, labels: Context): String {
        val startDate = startAtMillis?.toDate(zoneId)
        val dueDate = dueAtMillis?.toDate(zoneId)
        val date = dueDate ?: startDate ?: return labels.getString(R.string.inbox)
        val dateText = date.format(DateTimeFormatter.ofPattern("EEE, d. MMM", locale))
        val startTimed = startAtMillis?.takeIf { startHasTime }
        val dueTimed = dueAtMillis?.takeIf { dueHasTime }
        return when {
            startTimed != null && dueTimed != null && startDate == dueDate ->
                "$dateText, ${startTimed.toTimeText(zoneId)}-${dueTimed.toTimeText(zoneId)}"
            startTimed != null && dueTimed != null ->
                "${startTimed.toDate(zoneId).format(DateTimeFormatter.ofPattern("d. MMM", locale))} ${startTimed.toTimeText(zoneId)} - " +
                    "${dueTimed.toDate(zoneId).format(DateTimeFormatter.ofPattern("d. MMM", locale))} ${dueTimed.toTimeText(zoneId)}"
            startTimed != null ->
                "$dateText, ${labels.getString(R.string.from_time, startTimed.toTimeText(zoneId))}"
            dueTimed != null ->
                "$dateText, ${labels.getString(R.string.until_time, dueTimed.toTimeText(zoneId))}"
            else ->
                "$dateText, ${labels.getString(R.string.all_day)}"
        }
    }

    private fun TaskEntity.toTaskRow(
        settings: WidgetRenderSettings,
        depth: Int,
        childCount: Int,
        continuationLevels: Set<Int>,
        lastSibling: Boolean,
        subtasksExpanded: Boolean,
    ): WidgetListRow {
        val labels = textContext(settings)
        val millis = startAtMillis ?: dueAtMillis
        val date = widgetTaskDate(zoneId) ?: LocalDate.now(zoneId)
        val time = millis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime() }
        val hasTime = if (startAtMillis != null) startHasTime else dueHasTime
        val dateText = if (millis == null) labels.getString(R.string.no_date) else date.relativeDateLabel(settings.locale)
        val timeText = if (time != null && hasTime) " - ${time.timeText()}" else ""
        val statusText = statusText(labels)
        val metaText = listOfNotNull(
            "$dateText$timeText",
            statusText.takeUnless { it == labels.getString(R.string.status_open) },
        ).joinToString(" - ")
        return WidgetListRow.task(
            title = title.ifBlank { labels.getString(R.string.no_title) },
            meta = metaText,
            color = displayColor(settings.taskColorMode),
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
            continuationLevels = continuationLevels,
            lastSibling = lastSibling,
            subtasksExpanded = subtasksExpanded,
            priority = priority,
            priorityMotionEnabled = settings.priorityAnimationsEnabled,
        )
    }

    private fun TaskEntity.statusText(labels: Context): String = when (effectiveStatus()) {
        "IN-PROCESS" -> labels.getString(R.string.in_progress)
        "COMPLETED" -> labels.getString(R.string.status_completed)
        "CANCELLED" -> labels.getString(R.string.aborted)
        else -> labels.getString(R.string.status_open)
    }

    private fun LocalDate.relativeDateLabel(locale: Locale): String {
        val today = LocalDate.now(zoneId)
        val labels = textContext(locale)
        return when (this) {
            today -> labels.getString(R.string.today)
            else -> format(DateTimeFormatter.ofPattern("EEE, d. MMM", locale))
        }
    }

    private fun LocalDate.widgetSpanDateText(labels: Context): String {
        val locale = labels.resources.configuration.locales[0] ?: Locale.getDefault()
        return format(DateTimeFormatter.ofPattern("d. MMM", locale))
    }

    private fun LocalTime.timeText(): String =
        format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun monthSpanChipLayout(span: Int): Int = when (span.coerceIn(1, 7)) {
    1 -> R.layout.widget_month_span_chip_1
    2 -> R.layout.widget_month_span_chip_2
    3 -> R.layout.widget_month_span_chip_3
    4 -> R.layout.widget_month_span_chip_4
    5 -> R.layout.widget_month_span_chip_5
    6 -> R.layout.widget_month_span_chip_6
    else -> R.layout.widget_month_span_chip_7
}

private fun monthBottomFadeSpanLayout(span: Int): Int = when (span.coerceIn(1, 7)) {
    1 -> R.layout.widget_month_bottom_fade_span_1
    2 -> R.layout.widget_month_bottom_fade_span_2
    3 -> R.layout.widget_month_bottom_fade_span_3
    4 -> R.layout.widget_month_bottom_fade_span_4
    5 -> R.layout.widget_month_bottom_fade_span_5
    6 -> R.layout.widget_month_bottom_fade_span_6
    else -> R.layout.widget_month_bottom_fade_span_7
}

private fun WidgetMonthItem.monthSpanTextStartPaddingDp(): Float =
    if (fadesFromPrevious) {
        WIDGET_MONTH_SPAN_FADE_WIDTH_DP + WIDGET_MONTH_SPAN_FADE_TEXT_INSET_DP
    } else {
        4f
    }

private fun monthSpanChipCacheKey(
    segment: WidgetMonthWeekSegment,
    style: WidgetMonthChipStyle,
    widthDp: Float,
    textSp: Float,
): String = buildString {
    append("month-chip|").append(WIDGET_MONTH_RENDER_SIGNATURE_VERSION)
    append('|').append(segment.startColumn).append('-').append(segment.endColumn)
    append('|').append(widthDp)
    append('|').append(textSp)
    append('|').append(segment.title)
    append('|').append(style.fillColor).append('/').append(style.textColor)
    append('|').append(segment.item.id)
    append('|').append(segment.item.color)
    append('|').append(segment.item.lane)
    append('|').append(segment.item.continuesFromPrevious)
    append('|').append(segment.item.continuesToNext)
    append('|').append(segment.item.fadesFromPrevious)
    append('|').append(segment.item.fadesToNext)
}

private fun monthTodayLabel(text: String, isToday: Boolean): CharSequence =
    if (isToday && text.isNotEmpty()) {
        SpannableString(text).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    } else {
        text
    }

private fun monthDotBitmap(context: Context, color: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (WIDGET_MONTH_DOT_SIZE_DP * density).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = sizePx / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    canvas.drawCircle(radius, radius, radius, paint)
    return bitmap
}

private fun monthSpanChipBitmap(
    context: Context,
    style: WidgetMonthChipStyle,
    item: WidgetMonthItem,
    title: String,
    widthDp: Float,
    textSp: Float,
): Bitmap {
    val density = context.resources.displayMetrics.density * WIDGET_MONTH_SPAN_BITMAP_SCALE
    val heightDp = WIDGET_MONTH_SPAN_CHIP_HEIGHT_DP
    val actualWidthDp = (widthDp - WIDGET_MONTH_SPAN_CHIP_HORIZONTAL_INSET_DP * 2f).coerceAtLeast(1f)
    val bitmap = Bitmap.createBitmap(
        (actualWidthDp * density).roundToInt().coerceAtLeast(1),
        (heightDp * density).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    ).apply {
        setDensity((context.resources.displayMetrics.densityDpi * WIDGET_MONTH_SPAN_BITMAP_SCALE).roundToInt())
    }
    val canvas = Canvas(bitmap)
    canvas.scale(density, density)

    val fillColor = style.fillColor
    val radius = heightDp / 2f
    val fadeWidth = WIDGET_MONTH_SPAN_FADE_WIDTH_DP.coerceAtMost(actualWidthDp / 2f)
    val fadeOverlap = WIDGET_MONTH_SPAN_FADE_OVERLAP_DP.coerceAtMost(fadeWidth)
    val solidLeft = if (item.fadesFromPrevious) (fadeWidth - fadeOverlap).coerceAtLeast(0f) else 0f
    val solidRight = actualWidthDp - if (item.fadesToNext) (fadeWidth - fadeOverlap).coerceAtLeast(0f) else 0f
    val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        this.style = Paint.Style.FILL
    }
    if (solidRight > solidLeft) {
        canvas.drawMonthChipRect(
            rect = RectF(solidLeft, 0f, solidRight, heightDp),
            radius = radius,
            roundStart = !item.continuesFromPrevious && !item.fadesFromPrevious,
            roundEnd = !item.continuesToNext && !item.fadesToNext,
            paint = solidPaint,
        )
    }
    if (item.fadesFromPrevious && fadeWidth > 0f) {
        val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                fadeWidth,
                0f,
                fillColor.withAlpha(0f),
                fillColor,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, fadeWidth + fadeOverlap, heightDp, fadePaint)
    }
    if (item.fadesToNext && fadeWidth > 0f) {
        val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                actualWidthDp - fadeWidth,
                0f,
                actualWidthDp,
                0f,
                fillColor,
                fillColor.withAlpha(0f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(actualWidthDp - fadeWidth - fadeOverlap, 0f, actualWidthDp, heightDp, fadePaint)
    }
    if (title.isNotBlank()) {
        val textStart = item.monthSpanTextStartPaddingDp()
        val textEndPadding = if (item.fadesToNext) WIDGET_MONTH_SPAN_FADE_WIDTH_DP else 4f
        val textRight = (actualWidthDp - textEndPadding).coerceAtLeast(textStart)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = style.textColor
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSp,
                context.resources.displayMetrics,
            ) / context.resources.displayMetrics.density
            isDither = true
            isLinearText = false
            isSubpixelText = true
            setHinting(Paint.HINTING_ON)
        }
        val metrics = textPaint.fontMetrics
        val baseline = heightDp / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawMonthChipTitle(
            title = title,
            paint = textPaint,
            startX = textStart,
            baseline = baseline,
            rightEdge = textRight,
            heightDp = heightDp,
        )
    }
    return bitmap
}

private fun Canvas.drawMonthChipTitle(
    title: String,
    paint: Paint,
    startX: Float,
    baseline: Float,
    rightEdge: Float,
    heightDp: Float,
) {
    if (rightEdge <= startX) return
    val save = saveLayer(startX, 0f, rightEdge, heightDp, null)
    drawText(title, startX, baseline, paint)
    val availableWidth = rightEdge - startX
    val fadeWidth = min(WIDGET_MONTH_SPAN_FADE_WIDTH_DP * 0.72f, availableWidth * 0.42f)
    if (fadeWidth > 0f && paint.measureText(title) > availableWidth) {
        val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        }
        drawRect(rightEdge - fadeWidth, 0f, rightEdge, heightDp, fadePaint)
    }
    restoreToCount(save)
}

private fun Canvas.drawMonthChipRect(
    rect: RectF,
    radius: Float,
    roundStart: Boolean,
    roundEnd: Boolean,
    paint: Paint,
) {
    if (rect.width() <= 0f || rect.height() <= 0f) return
    val radii = floatArrayOf(
        if (roundStart) radius else 0f,
        if (roundStart) radius else 0f,
        if (roundEnd) radius else 0f,
        if (roundEnd) radius else 0f,
        if (roundEnd) radius else 0f,
        if (roundEnd) radius else 0f,
        if (roundStart) radius else 0f,
        if (roundStart) radius else 0f,
    )
    val path = Path().apply {
        addRoundRect(rect, radii, Path.Direction.CW)
    }
    drawPath(path, paint)
}

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

internal data class WidgetListRow(
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
    val location: String?,
    val eventStatus: String?,
    val endMillis: Long,
    val spanEndDate: LocalDate?,
    val showAgendaDate: Boolean,
    val eventResourceHref: String?,
    val taskResourceHref: String?,
    val statusGlyph: String,
    val depth: Int,
    val childCount: Int,
    val continuationLevels: Set<Int>,
    val lastSibling: Boolean,
    val subtasksExpanded: Boolean,
    val priority: Int?,
    val priorityMotionEnabled: Boolean,
) {
    fun appendSignatureTo(builder: StringBuilder) {
        builder
            .append(type.name)
            .append('|').append(stableId)
            .append('|').append(title)
            .append('|').append(meta)
            .append('|').append(color)
            .append('|').append(sortMillis)
            .append('|').append(date.toEpochDay())
            .append('|').append(completed)
            .append('|').append(allDaySort)
            .append('|').append(launchKind.name)
            .append('|').append(location.orEmpty())
            .append('|').append(eventStatus.orEmpty())
            .append('|').append(endMillis)
            .append('|').append(spanEndDate?.toEpochDay() ?: Long.MIN_VALUE)
            .append('|').append(showAgendaDate)
            .append('|').append(eventResourceHref.orEmpty())
            .append('|').append(taskResourceHref.orEmpty())
            .append('|').append(statusGlyph)
            .append('|').append(depth)
            .append('|').append(childCount)
            .append('|').append(continuationLevels.sorted().joinToString(","))
            .append('|').append(lastSibling)
            .append('|').append(subtasksExpanded)
            .append('|').append(priority ?: 0)
            .append('|').append(priorityMotionEnabled)
    }

    fun toRemoteViews(
        context: Context,
        packageName: String,
        palette: WidgetPalette,
        sourceKind: KgsWidgetKind,
        appWidgetId: Int,
        renderOptions: WidgetCollectionRenderOptions = WidgetCollectionRenderOptions(),
    ): RemoteViews {
        if (type == WidgetListRowType.Empty) {
            val views = RemoteViews(packageName, R.layout.widget_tasks_empty_row)
            views.setTextViewText(R.id.widget_tasks_empty_text, title)
            views.setTextColor(R.id.widget_tasks_empty_text, palette.muted)
            return views
        }
        if (type == WidgetListRowType.Section) {
            val views = RemoteViews(packageName, R.layout.widget_section_title)
            views.setTextViewText(R.id.widget_section_title_text, title)
            views.setTextColor(R.id.widget_section_title_text, if (title.startsWith("\u25CF")) palette.accent else palette.muted)
            if (sourceKind.usesAgendaCollectionStyle()) {
                views.setViewPadding(R.id.widget_section_title_text, context.dpToPx(62), 0, 0, 0)
                views.setTextViewTextSize(R.id.widget_section_title_text, TypedValue.COMPLEX_UNIT_SP, if (sourceKind == KgsWidgetKind.Day) 12f else 15f)
            }
            return views
        }
        if (type == WidgetListRowType.Now) {
            val views = RemoteViews(packageName, R.layout.widget_day_now_row)
            views.setTextViewText(R.id.widget_day_now_time, title)
            views.setTextColor(R.id.widget_day_now_time, palette.onAccent)
            views.setInt(R.id.widget_day_now_time, "setBackgroundResource", palette.sortBackgroundRes)
            views.setInt(R.id.widget_day_now_line, "setBackgroundColor", palette.text.withAlpha(if (palette.rootBackgroundColor.isDarkColor()) 0.62f else 0.52f))
            return views
        }
        if (type == WidgetListRowType.Item) {
            if (sourceKind.usesAgendaCollectionStyle()) {
                return toAgendaEventRemoteViews(context, packageName, palette, appWidgetId, renderOptions)
            }
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

        val agendaRow = sourceKind.usesAgendaCollectionStyle()
        val views = RemoteViews(packageName, if (agendaRow) R.layout.widget_agenda_task_item else R.layout.widget_task_item)
        if (agendaRow) {
            if (sourceKind == KgsWidgetKind.Day) {
                views.bindDayTimeColumn(palette, meta)
            } else {
                val rowLocale = context.resources.configuration.locales[0] ?: Locale.getDefault()
                views.bindAgendaDate(palette, muted = completed, visible = showAgendaDate, locale = rowLocale)
            }
            views.setOnClickFillInIntent(R.id.widget_agenda_task_row, openFillInIntent(openTask = true))
        }
        val taskArtWidthDp = renderOptions.taskArtWidthDp.coerceAtLeast(1f)
        val cardMeta = if (launchKind == KgsWidgetKind.Day) location.orEmpty() else meta
        val baseSpec = WIDGET_TASK_CARD_RENDERER.baseSpec(
            kind = launchKind,
            priority = priority,
            widthDp = taskArtWidthDp,
            depth = depth,
            childCount = childCount,
            hasMeta = cardMeta.isNotBlank(),
            completed = completed,
        )
        val rowRenderOptions = renderOptions.taskRows[stableId]
        val rowProgress = rowRenderOptions?.rowProgress?.coerceIn(0f, 1f) ?: 1f
        val rowEasedProgress = rowProgress
        val subtaskExpansionProgress = rowRenderOptions?.subtaskExpansionProgress?.coerceIn(0f, 1f)
            ?: if (subtasksExpanded) 1f else 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val rowHeight = max(WIDGET_TASK_TRANSITION_MIN_ROW_HEIGHT_DP, baseSpec.rowHeightDp * rowEasedProgress)
            val cardHeight = max(WIDGET_TASK_TRANSITION_MIN_ROW_HEIGHT_DP, baseSpec.cardHeightDp * rowEasedProgress)
            views.setViewLayoutHeight(R.id.widget_task_root, rowHeight, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_task_background_art, rowHeight, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_task_priority_motion, rowHeight, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_task_content, cardHeight, TypedValue.COMPLEX_UNIT_DIP)
        }
        views.setFloat(R.id.widget_task_root, "setAlpha", rowEasedProgress)
        val contentStart = context.dpToPx(baseSpec.contentStartDp).roundToInt()
        val cardColor = if (completed) color.blendWith(palette.rootBackgroundColor, 0.48f) else color
        val contentColor = if (cardColor.isDarkColor()) 0xFFFFFFFF.toInt() else 0xFF1C1A18.toInt()
        val secondaryColor = contentColor.withAlpha(if (completed) 0.52f else 0.74f)
        val priorityIntensity = if (!completed && priorityMotionEnabled && !renderOptions.suppressPriorityMotion) {
            WIDGET_TASK_CARD_RENDERER.priorityIntensity(priority)
        } else {
            0f
        }
        if (priorityIntensity > 0f) {
            val priorityFrameIds = sourceKind.priorityMotionFrameIds()
            val priorityFrameCount = priorityFrameIds.size
            val priorityFrameIntervalMillis = priorityMotionFrameIntervalMillis(priority, priorityIntensity, priorityFrameCount)
            priorityFrameIds.forEachIndexed { frame, viewId ->
                views.setWidgetRowImage(
                    context = context,
                    appWidgetId = appWidgetId,
                    viewId = viewId,
                    cacheKey = taskPriorityMotionCacheKey(
                        palette = palette,
                        taskArtWidthDp = taskArtWidthDp,
                        cardColor = cardColor,
                        contentColor = contentColor,
                        secondaryColor = secondaryColor,
                        priority = priority,
                        intensity = priorityIntensity,
                        frame = frame,
                        frameCount = priorityFrameCount,
                        subtaskExpansionProgress = subtaskExpansionProgress,
                    ),
                    bitmap = taskPriorityMotionBitmap(
                        context = context,
                        palette = palette,
                        taskArtWidthDp = taskArtWidthDp,
                        cardColor = cardColor,
                        contentColor = contentColor,
                        secondaryColor = secondaryColor,
                        priority = priority,
                        frame = frame,
                        frameCount = priorityFrameCount,
                        subtaskExpansionProgress = subtaskExpansionProgress,
                        baseSpec = baseSpec,
                        cardMeta = cardMeta,
                    ),
                )
            }
            views.setInt(
                R.id.widget_task_priority_motion,
                "setFlipInterval",
                priorityFrameIntervalMillis,
            )
            if (agendaRow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val motionHeight = max(
                    WIDGET_TASK_TRANSITION_MIN_ROW_HEIGHT_DP,
                    WIDGET_TASK_ROW_HEIGHT_DP * rowEasedProgress + WIDGET_TASK_PRIORITY_OVERDRAW_DP * 2f,
                )
                views.setViewLayoutHeight(R.id.widget_task_priority_motion, motionHeight, TypedValue.COMPLEX_UNIT_DIP)
            }
            views.setViewVisibility(R.id.widget_task_background_art, View.GONE)
        } else {
            views.setImageViewBitmap(
                R.id.widget_task_background_art,
                taskRowBackgroundBitmap(
                    context = context,
                    palette = palette,
                    taskArtWidthDp = taskArtWidthDp,
                    cardColor = cardColor,
                    contentColor = contentColor.withAlpha(if (completed) 0.62f else 1f),
                    secondaryColor = secondaryColor,
                    subtaskExpansionProgress = subtaskExpansionProgress,
                    lightweight = renderOptions.lightweightTaskTransition,
                    baseSpec = baseSpec,
                    cardMeta = cardMeta,
                ),
            )
            views.setViewVisibility(R.id.widget_task_background_art, View.VISIBLE)
        }
        views.setViewVisibility(
            R.id.widget_task_priority_motion,
            if (priorityIntensity > 0f) View.VISIBLE else View.GONE,
        )
        val contentEndPadding = (taskArtWidthDp - baseSpec.textEndDp).coerceAtLeast(0f)
        views.setViewPadding(R.id.widget_task_content, contentStart, 0, context.dpToPx(contentEndPadding).roundToInt(), 0)
        val overlayContentColor = 0x00000000
        views.setTextViewTextSize(R.id.widget_task_title, TypedValue.COMPLEX_UNIT_SP, baseSpec.titleTextSizeSp)
        views.setTextViewTextSize(R.id.widget_task_meta, TypedValue.COMPLEX_UNIT_SP, baseSpec.metaTextSizeSp)
        views.setImageViewBitmap(
            R.id.widget_task_status,
            taskStatusIconBitmap(context, statusGlyph, overlayContentColor),
        )
        views.setTextViewText(R.id.widget_task_title, title)
        views.setTextColor(R.id.widget_task_title, overlayContentColor)
        views.setTextViewText(R.id.widget_task_meta, cardMeta)
        views.setTextColor(R.id.widget_task_meta, overlayContentColor)
        if (childCount > 0) {
            views.setImageViewBitmap(R.id.widget_task_subtasks, taskSubtasksArrowBitmap(context, overlayContentColor, subtaskExpansionProgress))
            views.setOnClickFillInIntent(R.id.widget_task_subtasks, toggleSubtasksFillInIntent(appWidgetId))
        }
        views.setViewVisibility(R.id.widget_task_subtasks, if (childCount > 0) View.VISIBLE else View.GONE)
        views.setOnClickFillInIntent(R.id.widget_task_root, openFillInIntent(openTask = true))
        views.setOnClickFillInIntent(R.id.widget_task_background_art, openFillInIntent(openTask = true))
        views.setOnClickFillInIntent(R.id.widget_task_content, openFillInIntent(openTask = true))
        views.setOnClickFillInIntent(R.id.widget_task_title, openFillInIntent(openTask = true))
        views.setOnClickFillInIntent(R.id.widget_task_status, toggleFillInIntent())
        return views
    }

    private fun RemoteViews.setWidgetRowImage(
        context: Context,
        appWidgetId: Int,
        viewId: Int,
        cacheKey: String,
        bitmap: Bitmap,
    ) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            setImageViewBitmap(viewId, bitmap)
            return
        }
        val uri = runCatching {
            KgsWidgetBitmapUriStore.put(context, appWidgetId, cacheKey, bitmap)
        }.onFailure { error ->
            Log.w(TAG, "Failed to cache widget row image; falling back to an inline bitmap", error)
        }.getOrNull()
        if (uri != null) {
            setImageViewUri(viewId, uri)
            bitmap.recycle()
        } else {
            setImageViewBitmap(viewId, bitmap)
        }
    }

    private fun taskPriorityMotionCacheKey(
        palette: WidgetPalette,
        taskArtWidthDp: Float,
        cardColor: Int,
        contentColor: Int,
        secondaryColor: Int,
        priority: Int?,
        intensity: Float,
        frame: Int,
        frameCount: Int,
        subtaskExpansionProgress: Float,
    ): String =
        "task-priority|$launchKind|$stableId|$title|$meta|${location.orEmpty()}|$statusGlyph|$completed|$depth|$childCount|${continuationLevels.sorted().joinToString(",")}|$lastSibling|$subtasksExpanded|$palette|$taskArtWidthDp|$cardColor|$contentColor|$secondaryColor|$priority|$intensity|$frame|$frameCount|$subtaskExpansionProgress|$WIDGET_TASK_PRIORITY_BITMAP_SCALE|$WIDGET_TASK_PRIORITY_OVERDRAW_DP|$WIDGET_COLLECTION_RENDER_SIGNATURE_VERSION"

    private fun toAgendaEventRemoteViews(
        context: Context,
        packageName: String,
        palette: WidgetPalette,
        appWidgetId: Int,
        renderOptions: WidgetCollectionRenderOptions,
    ): RemoteViews {
        val views = RemoteViews(packageName, R.layout.widget_agenda_event_item)
        val dayRow = sourceKindIsDay()
        val rowHeightDp = when {
            dayRow -> WIDGET_DAY_EVENT_ROW_HEIGHT_DP
            spanEndDate != null -> WIDGET_AGENDA_SPAN_EVENT_ROW_HEIGHT_DP
            else -> WIDGET_AGENDA_EVENT_ROW_HEIGHT_DP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutHeight(R.id.widget_agenda_event_root, rowHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_agenda_date_column, rowHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_agenda_event_art, rowHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        }
        val muted = !sourceKindIsDay() && endMillis > 0L && endMillis < System.currentTimeMillis()
        val rowLocale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        if (dayRow) {
            views.bindDayTimeColumn(palette, meta)
        } else {
            views.bindAgendaDate(palette, muted, visible = showAgendaDate, locale = rowLocale, endDate = spanEndDate)
        }
        val cardWidthDp = renderOptions.taskArtWidthDp
        views.setWidgetRowImage(
            context = context,
            appWidgetId = appWidgetId,
            viewId = R.id.widget_agenda_event_art,
            cacheKey = agendaEventCardCacheKey(
                palette = palette,
                cardWidthDp = cardWidthDp,
                rowHeightDp = rowHeightDp,
                dayCard = dayRow,
                muted = muted,
            ),
            bitmap = agendaEventCardBitmap(
                context = context,
                palette = palette,
                cardWidthDp = cardWidthDp,
                rowHeightDp = rowHeightDp,
                dayCard = dayRow,
                muted = muted,
            ),
        )
        views.setOnClickFillInIntent(R.id.widget_agenda_event_root, openFillInIntent(openTask = false))
        views.setOnClickFillInIntent(R.id.widget_agenda_event_art, openFillInIntent(openTask = false))
        return views
    }

    private fun sourceKindIsDay(): Boolean =
        launchKind == KgsWidgetKind.Day

    private fun agendaEventCardCacheKey(
        palette: WidgetPalette,
        cardWidthDp: Float,
        rowHeightDp: Int,
        dayCard: Boolean,
        muted: Boolean,
    ): String =
        "agenda-event|$launchKind|$stableId|$title|$meta|${location.orEmpty()}|$eventStatus|$color|$completed|$endMillis|${spanEndDate?.toEpochDay() ?: Long.MIN_VALUE}|$palette|$cardWidthDp|$rowHeightDp|$dayCard|$muted|$WIDGET_AGENDA_ART_BITMAP_SCALE|$WIDGET_COLLECTION_RENDER_SIGNATURE_VERSION"

    private fun RemoteViews.bindAgendaDate(
        palette: WidgetPalette,
        muted: Boolean,
        visible: Boolean,
        locale: Locale,
        endDate: LocalDate? = null,
    ) {
        if (!visible) {
            setTextViewText(R.id.widget_agenda_date_month, "")
            setTextViewText(R.id.widget_agenda_date_day, "")
            setViewVisibility(R.id.widget_agenda_date_month, View.INVISIBLE)
            setViewVisibility(R.id.widget_agenda_date_day, View.INVISIBLE)
            setViewVisibility(R.id.widget_agenda_date_line, View.GONE)
            setViewVisibility(R.id.widget_agenda_end_month, View.GONE)
            setViewVisibility(R.id.widget_agenda_end_day, View.GONE)
            return
        }
        val month = date.format(DateTimeFormatter.ofPattern("MMM", locale)).replace(".", "")
        val color = palette.text.withAlpha(if (muted) 0.58f else 1f)
        setViewVisibility(R.id.widget_agenda_date_month, View.VISIBLE)
        setViewVisibility(R.id.widget_agenda_date_day, View.VISIBLE)
        setViewVisibility(R.id.widget_agenda_date_line, if (endDate != null) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_agenda_end_month, if (endDate != null) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_agenda_end_day, if (endDate != null) View.VISIBLE else View.GONE)
        setTextViewText(R.id.widget_agenda_date_month, month)
        setTextViewText(R.id.widget_agenda_date_day, date.dayOfMonth.toString())
        setTextColor(R.id.widget_agenda_date_month, color)
        setTextColor(R.id.widget_agenda_date_day, color)
        setTextViewTextSize(R.id.widget_agenda_date_month, TypedValue.COMPLEX_UNIT_SP, 11f)
        setTextViewTextSize(R.id.widget_agenda_date_day, TypedValue.COMPLEX_UNIT_SP, 20f)
        if (endDate != null) {
            val endMonth = endDate.format(DateTimeFormatter.ofPattern("MMM", locale)).replace(".", "")
            val lineColor = palette.text.withAlpha(if (muted) 0.22f else 0.42f)
            setInt(R.id.widget_agenda_date_line, "setBackgroundColor", lineColor)
            setTextViewText(R.id.widget_agenda_end_month, endMonth)
            setTextViewText(R.id.widget_agenda_end_day, endDate.dayOfMonth.toString())
            setTextColor(R.id.widget_agenda_end_month, color)
            setTextColor(R.id.widget_agenda_end_day, color)
            setTextViewTextSize(R.id.widget_agenda_end_month, TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextViewTextSize(R.id.widget_agenda_end_day, TypedValue.COMPLEX_UNIT_SP, 17f)
        }
    }

    private fun RemoteViews.bindDayTimeColumn(palette: WidgetPalette, timeText: String) {
        val rawPrimary = timeText.substringBefore('-').trim().ifBlank { timeText }
        val secondary = timeText.substringAfter('-', "").trim()
        val primary = if (secondary.isBlank() && rawPrimary.length > 7) {
            "${rawPrimary.take(5)}."
        } else {
            rawPrimary
        }
        val compactPrimary = secondary.isBlank() && rawPrimary.length > 6
        setViewVisibility(R.id.widget_agenda_date_month, View.VISIBLE)
        setViewVisibility(R.id.widget_agenda_date_day, View.VISIBLE)
        setViewVisibility(R.id.widget_agenda_date_line, View.GONE)
        setViewVisibility(R.id.widget_agenda_end_month, View.GONE)
        setViewVisibility(R.id.widget_agenda_end_day, View.GONE)
        setTextViewText(R.id.widget_agenda_date_month, primary)
        setTextViewText(R.id.widget_agenda_date_day, secondary)
        setTextColor(R.id.widget_agenda_date_month, palette.muted)
        setTextColor(R.id.widget_agenda_date_day, palette.muted.withAlpha(0.72f))
        setTextViewTextSize(R.id.widget_agenda_date_month, TypedValue.COMPLEX_UNIT_SP, if (compactPrimary) 8.8f else 10f)
        setTextViewTextSize(R.id.widget_agenda_date_day, TypedValue.COMPLEX_UNIT_SP, 10f)
    }

    private fun agendaEventCardBitmap(
        context: Context,
        palette: WidgetPalette,
        cardWidthDp: Float,
        rowHeightDp: Int,
        dayCard: Boolean,
        muted: Boolean,
    ): Bitmap {
        val widthDp = cardWidthDp.coerceAtLeast(WIDGET_TASK_MIN_CARD_WIDTH_DP)
        val bitmapScale = context.resources.displayMetrics.density * WIDGET_AGENDA_ART_BITMAP_SCALE
        val bitmap = Bitmap.createBitmap(
            (widthDp * bitmapScale).roundToInt().coerceAtLeast(1),
            (rowHeightDp * bitmapScale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.rootBackgroundColor)
        canvas.scale(bitmapScale, bitmapScale)

        val baseColor = color
        val tentative = eventStatus.equals("TENTATIVE", ignoreCase = true)
        val backgroundColor = when {
            tentative -> palette.rootBackgroundColor
            muted -> baseColor.greyedOut(0.62f).blendWith(palette.rootBackgroundColor, 0.28f)
            else -> baseColor
        }
        val contentBase = when {
            tentative -> palette.text
            baseColor.isDarkColor() && !muted -> 0xFFFFFFFF.toInt()
            else -> 0xFF1C1A18.toInt()
        }
        val contentColor = contentBase.withAlpha(if (muted) 0.64f else 1f)
        val secondaryColor = contentBase.withAlpha(if (muted) 0.72f else 0.92f)
        val tertiaryColor = contentBase.withAlpha(if (muted) 0.66f else 0.86f)
        val radius = if (dayCard) WIDGET_DAY_EVENT_CARD_RADIUS_DP else WIDGET_AGENDA_EVENT_CARD_RADIUS_DP
        val cardRect = RectF(0.5f, 0.5f, widthDp - 0.5f, rowHeightDp - 0.5f)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = backgroundColor
        }
        canvas.drawRoundRect(cardRect, radius, radius, backgroundPaint)
        if (tentative || muted) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = if (tentative) baseColor.withAlpha(0.95f) else baseColor.greyedOut(0.7f).withAlpha(0.9f)
                if (tentative) {
                    pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
                }
            }
            canvas.drawRoundRect(cardRect, radius, radius, borderPaint)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = contentColor
            textSize = if (dayCard) 12f else 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, if (dayCard) Typeface.BOLD else Typeface.BOLD)
            isStrikeThruText = eventStatus.equals("CANCELLED", ignoreCase = true)
        }
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = if (dayCard) 10.5f else 10.8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isStrikeThruText = titlePaint.isStrikeThruText
        }
        val locationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tertiaryColor
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isStrikeThruText = titlePaint.isStrikeThruText
        }
        val textStart = 12f
        val textWidth = (widthDp - 24f).coerceAtLeast(0f)
        val locationText = location?.takeIf { it.isNotBlank() }
        if (dayCard) {
            val titleBaseline = if (locationText == null) rowHeightDp / 2f + 4f else 20f
            canvas.drawText(ellipsizeForPaint(title, titlePaint, textWidth), textStart, titleBaseline, titlePaint)
            locationText?.let {
                canvas.drawText(ellipsizeForPaint(it, locationPaint, textWidth), textStart, 34.5f, locationPaint)
            }
        } else {
            canvas.drawText(ellipsizeForPaint(title, titlePaint, textWidth), textStart, 19f, titlePaint)
            canvas.drawText(ellipsizeForPaint(meta, timePaint, textWidth), textStart, 34.5f, timePaint)
            locationText?.let {
                canvas.drawText(ellipsizeForPaint(it, locationPaint, textWidth), textStart, 48.5f, locationPaint)
            }
        }
        return bitmap
    }

    private fun taskRowBackgroundBitmap(
        context: Context,
        palette: WidgetPalette,
        taskArtWidthDp: Float,
        cardColor: Int,
        contentColor: Int,
        secondaryColor: Int,
        subtaskExpansionProgress: Float,
        lightweight: Boolean,
        baseSpec: TaskCardBaseSpec,
        cardMeta: String,
    ): Bitmap {
        val bitmapScale = context.resources.displayMetrics.density *
            if (lightweight) WIDGET_TASK_TRANSITION_BITMAP_SCALE else WIDGET_TASK_PRIORITY_BITMAP_SCALE
        val bitmap = Bitmap.createBitmap(
            (taskArtWidthDp * bitmapScale).roundToInt().coerceAtLeast(1),
            (baseSpec.rowHeightDp * bitmapScale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.RGB_565,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.rootBackgroundColor)
        canvas.scale(bitmapScale, bitmapScale)
        drawTaskHierarchy(canvas, palette.hierarchyLine, subtaskExpansionProgress, baseSpec)
        drawTaskCard(canvas, baseSpec, cardColor, TaskPriorityEffect.None)
        drawTaskContent(
            context = context,
            canvas = canvas,
            baseSpec = baseSpec,
            contentColor = contentColor,
            secondaryColor = secondaryColor,
            effect = TaskPriorityEffect.None,
            cardMeta = cardMeta,
            subtaskExpansionProgress = subtaskExpansionProgress,
        )
        return bitmap
    }

    private fun taskPriorityMotionBitmap(
        context: Context,
        palette: WidgetPalette,
        taskArtWidthDp: Float,
        cardColor: Int,
        contentColor: Int,
        secondaryColor: Int,
        priority: Int?,
        frame: Int,
        frameCount: Int,
        subtaskExpansionProgress: Float,
        baseSpec: TaskCardBaseSpec,
        cardMeta: String,
    ): Bitmap {
        val overdrawDp = if (launchKind == KgsWidgetKind.Agenda || launchKind == KgsWidgetKind.Day) WIDGET_TASK_PRIORITY_OVERDRAW_DP else 0f
        val bitmapScale = context.resources.displayMetrics.density * WIDGET_TASK_PRIORITY_BITMAP_SCALE
        val bitmap = Bitmap.createBitmap(
            ((taskArtWidthDp + overdrawDp * 2f) * bitmapScale).roundToInt().coerceAtLeast(1),
            ((baseSpec.rowHeightDp + overdrawDp * 2f) * bitmapScale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.rootBackgroundColor)
        canvas.scale(bitmapScale, bitmapScale)
        canvas.translate(overdrawDp, overdrawDp)
        val effect = WIDGET_TASK_CARD_RENDERER.effect(priority, frame, frameCount, bitmapScale)
        val glowOutset = effect.glowSpread / 2f
        drawTaskHierarchy(canvas, palette.hierarchyLine, subtaskExpansionProgress, baseSpec)
        drawTaskCard(
            canvas = canvas,
            baseSpec = baseSpec,
            color = cardColor,
            effect = effect.copy(scale = 1f),
            horizontalSpread = glowOutset,
            verticalSpread = glowOutset,
            alpha = effect.glowAlpha,
            radius = baseSpec.cornerRadiusDp + effect.glowSpread / 3f,
        )
        drawTaskCard(
            canvas = canvas,
            baseSpec = baseSpec,
            color = cardColor,
            effect = effect,
        )
        drawTaskContent(
            context = context,
            canvas = canvas,
            baseSpec = baseSpec,
            contentColor = contentColor.withAlpha(if (completed) 0.62f else 1f),
            secondaryColor = secondaryColor,
            effect = effect,
            cardMeta = cardMeta,
            subtaskExpansionProgress = subtaskExpansionProgress,
        )
        return bitmap
    }

    private fun drawTaskHierarchy(
        canvas: Canvas,
        lineColor: Int,
        subtaskExpansionProgress: Float,
        baseSpec: TaskCardBaseSpec,
    ) {
        val boundedDepth = baseSpec.hierarchyDepth
        if (boundedDepth <= 0 && childCount <= 0 && continuationLevels.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            strokeWidth = baseSpec.hierarchyLineStrokeDp
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val centerY = baseSpec.rowHeightDp / 2f
        val height = baseSpec.rowHeightDp
        continuationLevels.forEach { level ->
            if (level in 0 until WIDGET_TASK_MAX_DEPTH) {
                val x = baseSpec.hierarchySideInsetDp + level * baseSpec.hierarchyIndentDp + baseSpec.hierarchyStemDp
                canvas.drawLine(x, -baseSpec.hierarchyOverlapDp, x, height + baseSpec.hierarchyOverlapDp, paint)
            }
        }
        if (boundedDepth > 0) {
            val branchLevel = boundedDepth - 1
            val branchX = baseSpec.hierarchySideInsetDp + branchLevel * baseSpec.hierarchyIndentDp + baseSpec.hierarchyStemDp
            val branchBottom = if (lastSibling) centerY else height + baseSpec.hierarchyOverlapDp
            canvas.drawLine(branchX, -baseSpec.hierarchyOverlapDp, branchX, branchBottom, paint)
            val branchEndX = baseSpec.hierarchySideInsetDp + boundedDepth * baseSpec.hierarchyIndentDp + baseSpec.hierarchyOverlapDp + 2f
            canvas.drawLine(branchX, centerY, branchEndX, centerY, paint)
        }
        if (childCount > 0 && subtaskExpansionProgress > 0.01f) {
            val x = baseSpec.hierarchySideInsetDp + boundedDepth * baseSpec.hierarchyIndentDp + baseSpec.hierarchyStemDp
            val cardBottom = (baseSpec.rowHeightDp + baseSpec.cardHeightDp) / 2f
            val tailEnd = lerpFloat(cardBottom - 1f, height + baseSpec.hierarchyOverlapDp, subtaskExpansionProgress.coerceIn(0f, 1f))
            canvas.drawLine(x, cardBottom - 1f, x, tailEnd, paint)
        }
    }

    private fun drawTaskCard(
        canvas: Canvas,
        baseSpec: TaskCardBaseSpec,
        color: Int,
        effect: TaskPriorityEffect,
        horizontalSpread: Float = 0f,
        verticalSpread: Float = horizontalSpread,
        alpha: Float = 1f,
        radius: Float = baseSpec.cornerRadiusDp + horizontalSpread * 0.45f,
    ) {
        val cardTop = (baseSpec.rowHeightDp - baseSpec.cardHeightDp) / 2f
        val cardBottom = cardTop + baseSpec.cardHeightDp
        val centerX = (baseSpec.cardLeftDp + baseSpec.cardRightDp) / 2f + effect.translationX
        val centerY = (cardTop + cardBottom) / 2f + effect.translationY
        val halfWidth = ((baseSpec.cardRightDp - baseSpec.cardLeftDp) * effect.scale) / 2f
        val halfHeight = (baseSpec.cardHeightDp * effect.scale) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = if (alpha >= 0.999f) color else color.withAlpha(alpha)
        }
        val rect = RectF(
            centerX - halfWidth - horizontalSpread,
            centerY - halfHeight - verticalSpread,
            centerX + halfWidth + horizontalSpread,
            centerY + halfHeight + verticalSpread,
        )
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    private fun drawTaskContent(
        context: Context,
        canvas: Canvas,
        baseSpec: TaskCardBaseSpec,
        contentColor: Int,
        secondaryColor: Int,
        effect: TaskPriorityEffect,
        cardMeta: String,
        subtaskExpansionProgress: Float,
    ) {
        val cardCenterX = (baseSpec.cardLeftDp + baseSpec.cardRightDp) / 2f
        fun transformedX(value: Float): Float =
            cardCenterX + (value - cardCenterX) * effect.scale + effect.translationX

        val centerY = baseSpec.rowHeightDp / 2f + effect.translationY
        drawTaskStatusGlyph(
            canvas = canvas,
            statusGlyph = statusGlyph,
            tint = contentColor,
            centerX = transformedX(baseSpec.statusCenterXDp),
            centerY = centerY,
            radius = baseSpec.statusRadiusDp * effect.scale,
            strokeWidth = baseSpec.statusStrokeDp * effect.scale,
        )

        val hasMeta = cardMeta.isNotBlank()
        val textStart = transformedX(baseSpec.textStartDp)
        val textEnd = transformedX(baseSpec.textEndDp)
        val fontScale = context.resources.displayMetrics.scaledDensity / context.resources.displayMetrics.density
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = contentColor
            textSize = baseSpec.titleTextSizeSp * fontScale * effect.scale
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = baseSpec.metaTextSizeSp * fontScale * effect.scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val maxTextWidth = (textEnd - textStart).coerceAtLeast(0f)
        canvas.drawText(
            ellipsizeForPaint(title, titlePaint, maxTextWidth),
            textStart,
            centerY + baseSpec.titleBaselineOffsetDp * effect.scale,
            titlePaint,
        )
        if (hasMeta) {
            canvas.drawText(
                ellipsizeForPaint(cardMeta, metaPaint, maxTextWidth),
                textStart,
                centerY + baseSpec.metaBaselineOffsetDp * effect.scale,
                metaPaint,
            )
        }

        if (childCount > 0) {
            drawTaskSubtasksChevron(
                canvas = canvas,
                tint = contentColor,
                centerX = transformedX(baseSpec.chevronCenterXDp),
                centerY = centerY,
                expandedProgress = subtaskExpansionProgress,
                scale = effect.scale,
            )
        }
    }

    private fun taskStatusIconBitmap(context: Context, statusGlyph: String, tint: Int): Bitmap {
        val width = context.dpToPx(30)
        val height = context.dpToPx(WIDGET_TASK_CARD_HEIGHT_DP)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = width / 2f
        val centerY = height / 2f
        drawTaskStatusGlyph(
            canvas = canvas,
            statusGlyph = statusGlyph,
            tint = tint,
            centerX = centerX,
            centerY = centerY,
            radius = context.dpToPx(WIDGET_TASK_STATUS_RADIUS_DP),
            strokeWidth = context.dpToPx(WIDGET_TASK_STATUS_STROKE_DP),
        )
        return bitmap
    }

    private fun drawTaskStatusGlyph(
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
        }
        when (statusGlyph) {
            "\u2713" -> {
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(centerX, centerY, radius, paint)
                val path = Path().apply {
                    moveTo(centerX - radius * 0.48f, centerY + radius * 0.01f)
                    lineTo(centerX - radius * 0.11f, centerY + radius * 0.38f)
                    lineTo(centerX + radius * 0.56f, centerY - radius * 0.48f)
                }
                canvas.drawPath(path, paint)
            }
            "\u25D0" -> {
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(centerX, centerY, radius, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(centerX, centerY, radius * 0.34f, paint)
            }
            "\u00D7" -> {
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(centerX, centerY, radius, paint)
                canvas.drawLine(
                    centerX - radius * 0.44f,
                    centerY - radius * 0.44f,
                    centerX + radius * 0.44f,
                    centerY + radius * 0.44f,
                    paint,
                )
                canvas.drawLine(
                    centerX + radius * 0.44f,
                    centerY - radius * 0.44f,
                    centerX - radius * 0.44f,
                    centerY + radius * 0.44f,
                    paint,
                )
            }
            else -> {
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(centerX, centerY, radius, paint)
            }
        }
    }

    private fun taskSubtasksArrowBitmap(context: Context, tint: Int, expandedProgress: Float): Bitmap {
        val size = context.dpToPx(30)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawTaskSubtasksChevron(
            canvas = canvas,
            tint = tint,
            centerX = size / 2f,
            centerY = size / 2f,
            expandedProgress = expandedProgress,
            scale = context.resources.displayMetrics.density,
        )
        return bitmap
    }

    private fun drawTaskSubtasksChevron(
        canvas: Canvas,
        tint: Int,
        centerX: Float,
        centerY: Float,
        expandedProgress: Float,
        scale: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            strokeWidth = WIDGET_TASK_SUBTASK_ARROW_STROKE_DP * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        val progress = expandedProgress.coerceIn(0f, 1f)
        canvas.save()
        canvas.rotate(-90f * (1f - progress), centerX, centerY)
        val path = Path().apply {
            moveTo(
                centerX - WIDGET_TASK_SUBTASK_ARROW_HALF_WIDTH_DP * scale,
                centerY - WIDGET_TASK_SUBTASK_ARROW_HALF_HEIGHT_DP * scale,
            )
            lineTo(centerX, centerY + WIDGET_TASK_SUBTASK_ARROW_HALF_HEIGHT_DP * scale)
            lineTo(
                centerX + WIDGET_TASK_SUBTASK_ARROW_HALF_WIDTH_DP * scale,
                centerY - WIDGET_TASK_SUBTASK_ARROW_HALF_HEIGHT_DP * scale,
            )
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun openFillInIntent(openTask: Boolean = taskResourceHref != null): Intent {
        val intent = Intent()
        intent.putExtra(EXTRA_COLLECTION_ACTION, COLLECTION_ACTION_OPEN)
        intent.putExtra(EXTRA_WIDGET_KIND, if (openTask) KgsWidgetKind.Tasks.name else launchKind.name)
        intent.putExtra(EXTRA_WIDGET_DATE, date.toString())
        if (openTask && !taskResourceHref.isNullOrBlank()) {
            intent.putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_OPEN_TASK)
            intent.putExtra(EXTRA_WIDGET_TASK_UID, taskResourceHref)
        } else if (!eventResourceHref.isNullOrBlank()) {
            intent.putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_OPEN_EVENT)
            intent.putExtra(EXTRA_WIDGET_EVENT_UID, eventResourceHref)
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

    private fun toggleSubtasksFillInIntent(appWidgetId: Int): Intent {
        val intent = Intent()
        intent.putExtra(EXTRA_COLLECTION_ACTION, COLLECTION_ACTION_TOGGLE_SUBTASKS)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        intent.putExtra(KgsWidgetProvider.EXTRA_TASK_RESOURCE_HREF, taskResourceHref)
        intent.data = Uri.parse("kgs-calendar://widget-row-toggle-subtasks/$stableId")
        return intent
    }

    companion object {
        fun empty(title: String): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Empty,
            title = title,
            meta = "",
            color = 0,
            sortMillis = Long.MIN_VALUE,
            date = LocalDate.now(),
            completed = false,
            allDaySort = 0,
            launchKind = KgsWidgetKind.Tasks,
            stableId = stableId("tasks-empty:$title"),
            location = null,
            eventStatus = null,
            endMillis = Long.MIN_VALUE,
            spanEndDate = null,
            showAgendaDate = true,
            eventResourceHref = null,
            taskResourceHref = null,
            statusGlyph = "",
            depth = 0,
            childCount = 0,
            continuationLevels = emptySet(),
            lastSibling = true,
            subtasksExpanded = true,
            priority = null,
            priorityMotionEnabled = false,
        )

        fun section(title: String, sortValue: Long = Long.MIN_VALUE): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Section,
            title = title,
            meta = "",
            color = 0,
            sortMillis = sortValue,
            date = LocalDate.now(),
            completed = false,
            allDaySort = 0,
            launchKind = KgsWidgetKind.Agenda,
            stableId = stableId("section:$title"),
            location = null,
            eventStatus = null,
            endMillis = Long.MIN_VALUE,
            spanEndDate = null,
            showAgendaDate = true,
            eventResourceHref = null,
            taskResourceHref = null,
            statusGlyph = "",
            depth = 0,
            childCount = 0,
            continuationLevels = emptySet(),
            lastSibling = true,
            subtasksExpanded = true,
            priority = null,
            priorityMotionEnabled = false,
        )

        fun now(date: LocalDate, timeLabel: String, sortValue: Long): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Now,
            title = timeLabel,
            meta = "",
            color = 0,
            sortMillis = sortValue,
            date = date,
            completed = false,
            allDaySort = 0,
            launchKind = KgsWidgetKind.Day,
            stableId = stableId("day-now:${date.toEpochDay()}:$sortValue"),
            location = null,
            eventStatus = null,
            endMillis = Long.MIN_VALUE,
            spanEndDate = null,
            showAgendaDate = true,
            eventResourceHref = null,
            taskResourceHref = null,
            statusGlyph = "",
            depth = 0,
            childCount = 0,
            continuationLevels = emptySet(),
            lastSibling = true,
            subtasksExpanded = true,
            priority = null,
            priorityMotionEnabled = false,
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
            eventResourceHref: String? = null,
            taskResourceHref: String? = null,
            location: String? = null,
            eventStatus: String? = null,
            endMillis: Long = sortMillis,
            spanEndDate: LocalDate? = null,
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
            location = location,
            eventStatus = eventStatus,
            endMillis = endMillis,
            spanEndDate = spanEndDate,
            showAgendaDate = true,
            eventResourceHref = eventResourceHref,
            taskResourceHref = taskResourceHref,
            statusGlyph = "",
            depth = 0,
            childCount = 0,
            continuationLevels = emptySet(),
            lastSibling = true,
            subtasksExpanded = true,
            priority = null,
            priorityMotionEnabled = false,
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
            location: String? = null,
            depth: Int,
            childCount: Int,
            continuationLevels: Set<Int>,
            lastSibling: Boolean,
            subtasksExpanded: Boolean,
            priority: Int?,
            priorityMotionEnabled: Boolean,
            launchKind: KgsWidgetKind = KgsWidgetKind.Tasks,
        ): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Task,
            title = title,
            meta = meta,
            color = color,
            sortMillis = sortMillis,
            date = date,
            completed = completed,
            allDaySort = 1,
            launchKind = launchKind,
            stableId = stableId("task-row:$taskResourceHref"),
            location = location,
            eventStatus = null,
            endMillis = sortMillis,
            spanEndDate = null,
            showAgendaDate = true,
            eventResourceHref = null,
            taskResourceHref = taskResourceHref,
            statusGlyph = statusGlyph,
            depth = depth,
            childCount = childCount,
            continuationLevels = continuationLevels,
            lastSibling = lastSibling,
            subtasksExpanded = subtasksExpanded,
            priority = priority,
            priorityMotionEnabled = priorityMotionEnabled,
        )

        private fun stableId(value: String): Long =
            value.fold(1125899906842597L) { acc, char -> acc * 31 + char.code }
    }
}

internal enum class WidgetListRowType {
    Empty,
    Section,
    Now,
    Item,
    Task,
}

private fun WidgetSize.collectionArtWidthDp(kind: KgsWidgetKind): Float =
    when (kind) {
        KgsWidgetKind.Agenda,
        KgsWidgetKind.Multi,
        KgsWidgetKind.Day -> (
            widthDp -
                WIDGET_TASK_CARD_SIDE_INSET_DP * 2 -
                WIDGET_AGENDA_DATE_COLUMN_WIDTH_DP -
                WIDGET_AGENDA_COLUMN_GAP_DP
            ).toFloat().coerceAtLeast(WIDGET_TASK_MIN_CARD_WIDTH_DP)
        else -> widthDp.toFloat().coerceAtLeast(1f)
    }

private fun WidgetSize.dayGridContentWidthDp(): Float =
    widthDp.toFloat().coerceAtLeast(120f)

internal data class WidgetPalette(
    val accent: Int,
    val text: Int,
    val muted: Int,
    val faint: Int,
    val onAccent: Int,
    val rootBackgroundColor: Int,
    val rootBackgroundRes: Int,
    val bottomFadeRes: Int,
    val topFadeRes: Int,
    val sortBackgroundRes: Int,
    val hierarchyLine: Int,
    val itemBackgroundRes: Int,
    val compactItemBackgroundRes: Int,
    val badgeBackgroundRes: Int,
    val daySelectedBackgroundRes: Int,
    val monthTodayTextColor: Int,
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
                    rootBackgroundColor = 0xFF101923.toInt(),
                    rootBackgroundRes = R.drawable.widget_background_dark,
                    bottomFadeRes = R.drawable.widget_bottom_fade_dark,
                    topFadeRes = R.drawable.widget_top_fade_dark,
                    sortBackgroundRes = R.drawable.widget_sort_background_dark,
                    hierarchyLine = 0xC8AEBBCC.toInt(),
                    itemBackgroundRes = R.drawable.widget_item_background_dark,
                    compactItemBackgroundRes = R.drawable.widget_month_cell_compact_dark,
                    badgeBackgroundRes = R.drawable.widget_badge_background_dark,
                    daySelectedBackgroundRes = R.drawable.widget_month_day_selected_dark,
                    monthTodayTextColor = 0xFF9BCAFF.toInt(),
                )
            } else {
                val root = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_background_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_background_fresh
                    else -> R.drawable.widget_background
                }
                val rootColor = when (themeMode) {
                    AppThemeMode.KgsWarm -> 0xFFFFEFE8.toInt()
                    AppThemeMode.KgsFresh -> 0xFFECF8F4.toInt()
                    else -> 0xFFEEF6FF.toInt()
                }
                val bottomFade = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_bottom_fade_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_bottom_fade_fresh
                    else -> R.drawable.widget_bottom_fade
                }
                val topFade = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_top_fade_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_top_fade_fresh
                    else -> R.drawable.widget_top_fade
                }
                val badge = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_badge_background_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_badge_background_fresh
                    else -> R.drawable.widget_badge_background
                }
                val sortBackground = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_sort_background_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_sort_background_fresh
                    else -> R.drawable.widget_sort_background
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
                    rootBackgroundColor = rootColor,
                    rootBackgroundRes = root,
                    bottomFadeRes = bottomFade,
                    topFadeRes = topFade,
                    sortBackgroundRes = sortBackground,
                    hierarchyLine = 0xFF707780.toInt(),
                    itemBackgroundRes = R.drawable.widget_item_background,
                    compactItemBackgroundRes = R.drawable.widget_month_cell_compact,
                    badgeBackgroundRes = badge,
                    daySelectedBackgroundRes = selected,
                    monthTodayTextColor = 0xFF1E5F9F.toInt(),
                )
            }
        }
    }
}

private data class WidgetMonthChipStyle(
    val backgroundRes: Int,
    val fillColor: Int,
    val textColor: Int,
) {
    fun cellBackgroundRes(item: WidgetMonthItem): Int =
        if (item.fadesFromPrevious || item.fadesToNext) {
            backgroundRes(item)
        } else {
            backgroundRes
        }

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
    get() = this != KgsWidgetKind.Month && this != KgsWidgetKind.Day

private fun KgsWidgetKind.emptyText(context: Context): String = when (this) {
    KgsWidgetKind.Tasks -> context.getString(R.string.no_scheduled_open_tasks)
    else -> context.getString(R.string.no_events_or_tasks)
}

private fun WidgetTaskDisplayMode.widgetLabel(context: Context): String = when (this) {
    WidgetTaskDisplayMode.Planned -> context.getString(R.string.planned_tasks)
    WidgetTaskDisplayMode.Unplanned -> context.getString(R.string.unplanned_tasks)
    WidgetTaskDisplayMode.Today -> context.getString(R.string.tasks_for_today)
}

private fun WidgetTaskSortMode.widgetLabel(context: Context): String = when (this) {
    WidgetTaskSortMode.Date -> context.getString(R.string.date)
    WidgetTaskSortMode.Priority -> context.getString(R.string.priority)
    WidgetTaskSortMode.Status -> context.getString(R.string.status)
}

private fun WidgetTaskSortMode.next(): WidgetTaskSortMode =
    WidgetTaskSortMode.entries[(ordinal + 1) % WidgetTaskSortMode.entries.size]

private fun taskPriorityIntensity(priority: Int?): Float {
    val value = priority?.coerceIn(1, 9) ?: 9
    return ((9 - value) / 8f).coerceIn(0f, 1f)
}

private fun priorityMotionFrameIntervalMillis(priority: Int?, intensity: Float, frameCount: Int): Int {
    val cycleMillis = (1050 - (intensity * 420f)).roundToInt().coerceAtLeast(520)
    return if (priority == 1) {
        42
    } else {
        ((cycleMillis * 2f) / frameCount.toFloat())
            .roundToInt()
            .coerceAtLeast(48)
    }
}

private fun ellipsizeForPaint(text: String, paint: Paint, maxWidth: Float): String {
    if (maxWidth <= 0f || text.isEmpty()) return ""
    if (paint.measureText(text) <= maxWidth) return text
    val ellipsis = "\u2026"
    val ellipsisWidth = paint.measureText(ellipsis)
    if (ellipsisWidth >= maxWidth) return ""
    var low = 0
    var high = text.length
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (paint.measureText(text, 0, mid) + ellipsisWidth <= maxWidth) {
            low = mid
        } else {
            high = mid - 1
        }
    }
    return text.take(low).trimEnd() + ellipsis
}

private fun shouldHideWidgetTitle(
    widthDp: Number,
    title: String,
    reservedDp: Float,
    textSp: Float,
): Boolean {
    val available = (widthDp.toFloat() - reservedDp).coerceAtLeast(0f)
    if (available < 28f) return true
    val estimatedTitleWidth = title.length * textSp * 0.54f
    if (estimatedTitleWidth <= 0f) return false
    return available / estimatedTitleWidth < 0.38f
}

private fun motionStandardEasing(fraction: Float): Float =
    cubicBezierEasing(fraction, x1 = 0.2f, y1 = 0f, x2 = 0f, y2 = 1f)

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)

private fun cubicBezierEasing(fraction: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val target = fraction.coerceIn(0f, 1f)
    var low = 0f
    var high = 1f
    repeat(14) {
        val mid = (low + high) / 2f
        if (cubicBezierCoordinate(mid, x1, x2) < target) {
            low = mid
        } else {
            high = mid
        }
    }
    return cubicBezierCoordinate((low + high) / 2f, y1, y2)
}

private fun cubicBezierCoordinate(t: Float, p1: Float, p2: Float): Float {
    val inverse = 1f - t
    return 3f * inverse * inverse * t * p1 + 3f * inverse * t * t * p2 + t * t * t
}

private fun TaskEntity.widgetTaskDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? =
    (startAtMillis ?: dueAtMillis)?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }

private fun TaskEntity.effectiveStatus(): String =
    status?.uppercase(Locale.ROOT) ?: if (isCompleted) "COMPLETED" else "NEEDS-ACTION"

private fun TaskEntity.widgetStatusGlyph(): String = when (effectiveStatus()) {
    "COMPLETED" -> "\u2713"
    "IN-PROCESS" -> "\u25D0"
    "CANCELLED" -> "\u00D7"
    else -> "\u25CB"
}

private fun TaskEntity.isWidgetActiveTask(): Boolean =
    when (effectiveStatus()) {
        "COMPLETED", "CANCELLED" -> false
        else -> !isCompleted
    }

private fun TaskEntity.statusSortRank(): Int = when (effectiveStatus()) {
    "IN-PROCESS" -> 0
    "NEEDS-ACTION" -> 1
    "COMPLETED" -> 2
    "CANCELLED" -> 3
    else -> 4
}

private fun AppLanguageMode.toLocale(context: Context): Locale =
    localeTag?.let(Locale::forLanguageTag) ?: context.resources.configuration.locales[0] ?: Locale.getDefault()

internal fun Context.withWidgetLocale(locale: Locale): Context {
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    return createConfigurationContext(configuration)
}

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

private fun WidgetTaskSubtaskDefaultMode.resolveSubtasksExpandedByDefault(appDefault: Boolean): Boolean = when (this) {
    WidgetTaskSubtaskDefaultMode.FollowApp -> appDefault
    WidgetTaskSubtaskDefaultMode.Open -> true
    WidgetTaskSubtaskDefaultMode.Closed -> false
}

internal fun EventEntity.displayColor(): Int = manualColor ?: color

internal fun TaskEntity.displayColor(mode: TaskColorMode): Int =
    manualColor ?: when (mode) {
        TaskColorMode.Priority -> priority?.let(::priorityColor) ?: color
        TaskColorMode.Collection -> color
    }

internal fun EventEntity.monthOccurrenceKey(): String =
    "${resourceHref.ifBlank { uid }}:$startsAtMillis"

internal fun EventEntity.endDateInclusive(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
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
    val fillColor = this or 0xFF000000.toInt()
    val textColor = if (fillColor.isDarkColor()) 0xFFFFFFFF.toInt() else 0xFF1C1A18.toInt()
    if (saturation < 0.14f) {
        return if (value >= 0.58f) {
            WidgetMonthChipStyle(R.drawable.widget_month_chip_neutral_light, fillColor, textColor)
        } else {
            WidgetMonthChipStyle(R.drawable.widget_month_chip_neutral_dark, fillColor, textColor)
        }
    }

    val hue = rgbHue(r, g, b, max, min)
    return when {
        hue < 15f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_red, fillColor, textColor)
        hue < 45f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_orange, fillColor, textColor)
        hue < 75f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_yellow, fillColor, textColor)
        hue < 155f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_green, fillColor, textColor)
        hue < 185f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_teal, fillColor, textColor)
        hue < 235f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_blue, fillColor, textColor)
        hue < 285f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_purple, fillColor, textColor)
        hue < 345f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_pink, fillColor, textColor)
        else -> WidgetMonthChipStyle(R.drawable.widget_month_chip_red, fillColor, textColor)
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

private fun EventEntity.widgetTimedPlacementOn(day: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Pair<Int, Int>? {
    if (allDay || isWidgetTimedMultiDayMiddleOn(day, zoneId)) return null
    val visibleStart = day.atTime(WIDGET_DAY_START_HOUR, 0).atZone(zoneId).toInstant().toEpochMilli()
    val visibleEnd = day.atTime(WIDGET_DAY_END_HOUR, 0).plusHours(1).atZone(zoneId).toInstant().toEpochMilli()
    val overlapStart = max(startsAtMillis, visibleStart)
    val overlapEnd = min(endsAtMillis, visibleEnd)
    if (overlapEnd <= overlapStart) return null
    val startMinute = ((overlapStart - visibleStart) / 60_000.0).roundToInt().coerceIn(0, 24 * 60 - 1)
    val duration = max(1, ((overlapEnd - overlapStart) / 60_000.0).roundToInt())
    return startMinute to (startMinute + duration).coerceIn(startMinute + 1, 24 * 60)
}

private fun TaskEntity.widgetTimedPlacementOn(day: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Pair<Int, Int>? {
    val startTimed = startAtMillis?.takeIf { startHasTime }
    val dueTimed = dueAtMillis?.takeIf { dueHasTime }
    if (startTimed == null && dueTimed == null) return null
    val start = startTimed ?: (dueTimed!! - WIDGET_DAY_DEFAULT_TASK_DURATION_MILLIS)
    val end = when {
        startTimed != null && dueTimed != null && dueTimed > startTimed -> dueTimed
        startTimed != null -> startTimed + WIDGET_DAY_DEFAULT_TASK_DURATION_MILLIS
        else -> dueTimed!!
    }
    val visibleStart = day.atTime(WIDGET_DAY_START_HOUR, 0).atZone(zoneId).toInstant().toEpochMilli()
    val visibleEnd = day.atTime(WIDGET_DAY_END_HOUR, 0).plusHours(1).atZone(zoneId).toInstant().toEpochMilli()
    val overlapStart = max(start, visibleStart)
    val overlapEnd = min(max(end, start + WIDGET_DAY_DEFAULT_TASK_DURATION_MILLIS), visibleEnd)
    if (overlapEnd <= overlapStart) return null
    val startMinute = ((overlapStart - visibleStart) / 60_000.0).roundToInt().coerceIn(0, 24 * 60 - 1)
    val duration = max(1, ((overlapEnd - overlapStart) / 60_000.0).roundToInt())
    return startMinute to (startMinute + duration).coerceIn(startMinute + 1, 24 * 60)
}

private fun EventEntity.isWidgetAllDayTopItemOn(day: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean =
    if (allDay) day in visibleDates(day, day.plusDays(1), zoneId) else isWidgetTimedMultiDayMiddleOn(day, zoneId)

private fun EventEntity.isWidgetTimedMultiDayMiddleOn(day: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
    if (allDay) return false
    val start = startsAtMillis.toDate(zoneId)
    val end = endDateInclusive(zoneId)
    return start.isBefore(end) && day.isAfter(start) && day.isBefore(end)
}

private fun TaskEntity.isWidgetFullDayTaskOn(day: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
    if (startAtMillis == null && dueAtMillis == null) return false
    if ((startAtMillis != null && startHasTime) || (dueAtMillis != null && dueHasTime)) return false
    return day in visibleDates(day, day.plusDays(1), zoneId)
}

private fun Pair<Int, Int>.timeRangeText(): String =
    "${first.minuteOfDayText()}-${second.minuteOfDayText()}"

private fun Int.minuteOfDayText(): String {
    val bounded = coerceIn(0, 24 * 60)
    val hour = (bounded / 60).coerceAtMost(24)
    val minute = bounded % 60
    return "%02d:%02d".format(hour, minute)
}

internal fun Long.toDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

private fun Long.toTimeText(zoneId: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

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

private fun Int.withAlpha(alpha: Float): Int =
    (((alpha.coerceIn(0f, 1f) * 255).roundToInt() and 0xFF) shl 24) or (this and 0x00FFFFFF)

private fun Int.blendWith(other: Int, fraction: Float): Int {
    val clamped = fraction.coerceIn(0f, 1f)
    val inverse = 1f - clamped
    val a = (((this ushr 24) and 0xFF) * inverse + ((other ushr 24) and 0xFF) * clamped).roundToInt()
    val r = (((this ushr 16) and 0xFF) * inverse + ((other ushr 16) and 0xFF) * clamped).roundToInt()
    val g = (((this ushr 8) and 0xFF) * inverse + ((other ushr 8) and 0xFF) * clamped).roundToInt()
    val b = ((this and 0xFF) * inverse + (other and 0xFF) * clamped).roundToInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

private fun Int.greyedOut(amount: Float): Int {
    val mix = amount.coerceIn(0f, 1f)
    val a = (this ushr 24) and 0xFF
    val r = (this ushr 16) and 0xFF
    val g = (this ushr 8) and 0xFF
    val b = this and 0xFF
    val gray = (r * 0.299f + g * 0.587f + b * 0.114f).roundToInt().coerceIn(0, 255)
    val nextR = (r + (gray - r) * mix).roundToInt().coerceIn(0, 255)
    val nextG = (g + (gray - g) * mix).roundToInt().coerceIn(0, 255)
    val nextB = (b + (gray - b) * mix).roundToInt().coerceIn(0, 255)
    return (a shl 24) or (nextR shl 16) or (nextG shl 8) or nextB
}

private fun Context.dpToPx(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()

private fun Context.dpToPx(value: Float): Float =
    value * resources.displayMetrics.density

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
