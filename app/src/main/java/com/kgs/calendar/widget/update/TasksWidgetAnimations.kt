package com.kgs.calendar.widget.update

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.kgs.calendar.R
import com.kgs.calendar.data.settings.WidgetTaskCreateMode
import com.kgs.calendar.data.settings.WidgetTaskSortMode
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.TAG
import com.kgs.calendar.widget.WIDGET_TASK_EXPANSION_FRAME_DELAY_MS
import com.kgs.calendar.widget.WIDGET_TASK_EXPANSION_STEPS
import com.kgs.calendar.widget.WIDGET_TASK_SORT_LABEL_TRANSITION_MS
import com.kgs.calendar.widget.WIDGET_TASK_SORT_MORPH_FRAME_DELAY_MS
import com.kgs.calendar.widget.WIDGET_TASK_SORT_MORPH_STEPS
import com.kgs.calendar.widget.WidgetLog
import com.kgs.calendar.widget.model.WidgetCollectionRenderOptions
import com.kgs.calendar.widget.model.WidgetCollectionSnapshot
import com.kgs.calendar.widget.model.WidgetListRow
import com.kgs.calendar.widget.model.WidgetTaskRowRenderOptions
import com.kgs.calendar.widget.model.lerpFloat
import com.kgs.calendar.widget.model.motionStandardEasing
import com.kgs.calendar.widget.model.next
import com.kgs.calendar.widget.model.usesDirectCollectionItems
import com.kgs.calendar.widget.render.KgsWidgetRenderer
import com.kgs.calendar.widget.render.WidgetPendingIntents
import com.kgs.calendar.widget.render.bindCollectionBottomFade
import com.kgs.calendar.widget.render.bindDirectCollectionItems
import com.kgs.calendar.widget.render.bindTasksSortButtonState
import com.kgs.calendar.widget.render.bindTasksSortButtonWidth
import com.kgs.calendar.widget.render.widgetButtonWidthDp
import com.kgs.calendar.widget.state.KgsWidgetCollectionRowsCache
import com.kgs.calendar.widget.state.KgsWidgetCollectionUpdateSignatures
import com.kgs.calendar.widget.state.KgsWidgetInteractionTokens
import com.kgs.calendar.widget.state.tasksListTokenKey
import com.kgs.calendar.widget.state.tasksSortButtonTokenKey
import com.kgs.calendar.widget.withWidgetLocale
import kotlinx.coroutines.delay

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
            views.setPendingIntentTemplate(R.id.widget_list, WidgetPendingIntents(appContext).collectionClickPendingIntent(KgsWidgetKind.Tasks, snapshot.appWidgetId))
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
                    fallback.setRemoteAdapter(R.id.widget_list, WidgetPendingIntents(appContext).collectionAdapterIntent(KgsWidgetKind.Tasks, snapshot.appWidgetId))
                    fallback.setPendingIntentTemplate(R.id.widget_list, WidgetPendingIntents(appContext).collectionClickPendingIntent(KgsWidgetKind.Tasks, snapshot.appWidgetId))
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
    views.setPendingIntentTemplate(R.id.widget_list, WidgetPendingIntents(context).collectionClickPendingIntent(KgsWidgetKind.Tasks, snapshot.appWidgetId))
    return runCatching {
        AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(snapshot.appWidgetId, views)
    }.onFailure { error ->
        Log.e(TAG, "Failed to apply Tasks widget ${snapshot.appWidgetId} animation frame", error)
    }.isSuccess
}

@RequiresApi(Build.VERSION_CODES.S)
internal suspend fun animateTasksSubtaskToggle(
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
