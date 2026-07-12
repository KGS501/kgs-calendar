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

internal suspend fun refreshTasksWidgetRows(
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

internal fun tasksListTokenKey(appWidgetId: Int): String =
    "tasks-list:$appWidgetId"

internal fun tasksSortButtonTokenKey(appWidgetId: Int): String =
    "tasks-sort-button:$appWidgetId"

internal fun dayAllDayTokenKey(appWidgetId: Int): String =
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

internal suspend fun prepareTasksSortButtonForModeChange(
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

internal suspend fun animateTasksSortButton(
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

internal fun Throwable.isRemoteViewsBitmapMemoryError(): Boolean =
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

internal fun tasksCollectionAdapterIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, KgsWidgetCollectionService::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        putExtra(EXTRA_WIDGET_KIND, KgsWidgetKind.Tasks.name)
        data = Uri.parse("kgs-calendar://widget-collection/${KgsWidgetKind.Tasks.name}/$appWidgetId")
    }

internal fun tasksCollectionClickPendingIntent(context: Context, appWidgetId: Int): PendingIntent =
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
