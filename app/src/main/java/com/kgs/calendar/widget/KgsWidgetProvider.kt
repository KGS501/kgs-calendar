package com.kgs.calendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.R
import com.kgs.calendar.data.settings.WidgetTaskSortMode
import com.kgs.calendar.widget.model.MonthCommand
import com.kgs.calendar.widget.model.MonthNavSnapshot
import com.kgs.calendar.widget.model.next
import com.kgs.calendar.widget.model.usesDirectCollectionItems
import com.kgs.calendar.widget.render.WidgetPendingIntents
import com.kgs.calendar.widget.render.bindTasksSortButtonState
import com.kgs.calendar.widget.render.widgetButtonWidthDp
import com.kgs.calendar.widget.state.tasksListTokenKey
import com.kgs.calendar.widget.state.tasksSortButtonTokenKey
import com.kgs.calendar.widget.update.WidgetUpdateCause
import com.kgs.calendar.widget.update.animateTasksSortButton
import com.kgs.calendar.widget.update.prepareTasksSortButtonForModeChange
import com.kgs.calendar.widget.update.refreshTasksWidgetRows
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class KgsAgendaWidgetProvider : KgsWidgetProvider(KgsWidgetKind.Agenda)

class KgsMonthWidgetProvider : KgsWidgetProvider(KgsWidgetKind.Month)

class KgsTasksWidgetProvider : KgsWidgetProvider(KgsWidgetKind.Tasks)

class KgsMultiWidgetProvider : KgsWidgetProvider(KgsWidgetKind.Multi)

class KgsDayWidgetProvider : KgsWidgetProvider(KgsWidgetKind.Day)

abstract class KgsWidgetProvider(
    private val kind: KgsWidgetKind,
) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        widgetDependencies(context).scheduler.update(kind, appWidgetIds, cause = WidgetUpdateCause.Periodic)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        widgetDependencies(context).scheduler.update(
            kind,
            intArrayOf(appWidgetId),
            debounceMillis = if (kind == KgsWidgetKind.Month) WIDGET_MONTH_RESIZE_DEBOUNCE_MS else 160,
            cause = WidgetUpdateCause.Resize,
        )
    }

    override fun onEnabled(context: Context) {
        widgetDependencies(context).scheduler.update(kind)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val state = widgetDependencies(context).state
        if (kind == KgsWidgetKind.Month || kind == KgsWidgetKind.Multi) {
            appWidgetIds.forEach { appWidgetId ->
                state.month.clear(appWidgetId)
            }
        }
        if (kind == KgsWidgetKind.Day) {
            appWidgetIds.forEach { appWidgetId ->
                state.day.clear(appWidgetId)
            }
        }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val widgets = widgetDependencies(context)
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
                    val snapshot = widgets.state.month.apply(appWidgetId, command)
                    navigateMonthAsync(widgets, appWidgetId, snapshot)
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
                        widgets.state.day.resetToToday(appWidgetId)
                    } else {
                        val direction = if (intent.action == ACTION_DAY_PREVIOUS) -1 else 1
                        widgets.state.day.offset(appWidgetId, direction)
                    }
                    navigateDayAsync(widgets, appWidgetId)
                } else {
                    super.onReceive(context, intent)
                }
            }

            ACTION_DAY_TOGGLE_ALL_DAY -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (kind == KgsWidgetKind.Day && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    toggleDayAllDayAsync(widgets, appWidgetId)
                } else {
                    super.onReceive(context, intent)
                }
            }

            ACTION_TASKS_SORT_NEXT -> {
                if (kind == KgsWidgetKind.Tasks) {
                    cycleTasksSortAsync(
                        widgets = widgets,
                        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
                    )
                } else {
                    super.onReceive(context, intent)
                }
            }

            ACTION_REFRESH -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                updateAsync(
                    widgets = widgets,
                    appWidgetIds = appWidgetId
                        .takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
                        ?.let { intArrayOf(it) },
                    cause = WidgetUpdateCause.Manual,
                )
            }

            Intent.ACTION_CONFIGURATION_CHANGED -> {
                updateAsync(
                    widgets = widgets,
                    forceFullDayUpdate = kind == KgsWidgetKind.Day,
                    cause = WidgetUpdateCause.Configuration,
                )
            }

            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                updateAsync(
                    widgets = widgets,
                    appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS),
                    cause = WidgetUpdateCause.Periodic,
                )
            }

            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    widgets.scheduler.update(
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
        widgets: WidgetDependencies,
        appWidgetIds: IntArray? = null,
        forceFullDayUpdate: Boolean = false,
        cause: WidgetUpdateCause = WidgetUpdateCause.Unknown,
    ) {
        val pendingResult = goAsync()
        widgets.scheduler.update(
            kind = kind,
            appWidgetIds = appWidgetIds,
            forceFullDayUpdate = forceFullDayUpdate,
            cause = cause,
            onCompletion = pendingResult::finish,
        )
    }

    private fun navigateDayAsync(widgets: WidgetDependencies, appWidgetId: Int) {
        val pendingResult = goAsync()
        widgets.scheduler.launchWidgetLatest(
            kind = KgsWidgetKind.Day,
            appWidgetId = appWidgetId,
            cause = WidgetUpdateCause.Navigation,
            onCompletion = pendingResult::finish,
        ) {
            widgets.updater.navigateDay(appWidgetId)
        }
    }

    private fun toggleDayAllDayAsync(widgets: WidgetDependencies, appWidgetId: Int) {
        val pendingResult = goAsync()
        widgets.scheduler.launchWidgetLatest(
            kind = KgsWidgetKind.Day,
            appWidgetId = appWidgetId,
            cause = WidgetUpdateCause.Interaction,
            onCompletion = pendingResult::finish,
        ) {
            widgets.updater.toggleDayAllDay(appWidgetId)
        }
    }

    private fun cycleTasksSortAsync(widgets: WidgetDependencies, appWidgetId: Int) {
        val pendingResult = goAsync()
        val context = widgets.appContext
        widgets.scheduler.launch {
            try {
                val targetIds = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    intArrayOf(appWidgetId)
                } else {
                    AppWidgetManager.getInstance(context)
                        .getAppWidgetIds(ComponentName(context, KgsWidgetKind.Tasks.providerClass))
                }
                val settingsStore = widgets.settingsStore
                val currentMode = settingsStore.tasksWidgetSortMode.first()
                val nextMode = currentMode.next()
                settingsStore.setTasksWidgetSortMode(nextMode)
                val textContext = context.withWidgetLocale(
                    settingsStore.languageMode.first().toLocale(context),
                )
                if (KgsWidgetKind.Tasks.usesDirectCollectionItems()) {
                    val createMode = settingsStore.tasksWidgetCreateMode.first()
                    val sortButtonTokens = targetIds.associateWith { widgets.state.interactionTokens.next(tasksSortButtonTokenKey(it)) }
                    val listTokens = targetIds.associateWith { widgets.state.interactionTokens.next(tasksListTokenKey(it)) }
                    prepareTasksSortButtonForModeChange(
                        widgets = widgets,
                        context = textContext,
                        appWidgetIds = targetIds,
                        fromMode = currentMode,
                        toMode = nextMode,
                        createMode = createMode,
                        tokens = sortButtonTokens,
                    )
                    refreshTasksWidgetRows(
                        widgets = widgets,
                        appWidgetIds = targetIds,
                        shouldApply = { id -> listTokens[id]?.let { widgets.state.interactionTokens.isCurrent(tasksListTokenKey(id), it) } == true },
                    )
                    animateTasksSortButton(
                        widgets = widgets,
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
                    refreshTasksWidgetRows(widgets, targetIds)
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
            WidgetPendingIntents(context).collectionAdapterIntent(KgsWidgetKind.Tasks, appWidgetId),
        )
        setPendingIntentTemplate(R.id.widget_list, WidgetPendingIntents(context).collectionClickPendingIntent(KgsWidgetKind.Tasks, appWidgetId))
    }

    private fun navigateMonthAsync(
        widgets: WidgetDependencies,
        appWidgetId: Int,
        snapshot: MonthNavSnapshot,
    ) {
        val pendingResult = goAsync()
        widgets.scheduler.launchWidgetLatest(
            kind = kind,
            appWidgetId = appWidgetId,
            cause = WidgetUpdateCause.Navigation,
            onCompletion = pendingResult::finish,
        ) {
            widgets.updater.navigateMonth(
                kind = kind,
                appWidgetId = appWidgetId,
                snapshot = snapshot,
            )
        }
    }

    private fun widgetDependencies(context: Context): WidgetDependencies =
        KgsCalendarApplication.graph(context).widgets

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
