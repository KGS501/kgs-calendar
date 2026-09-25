package com.kgs.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.MainActivity
import com.kgs.calendar.widget.model.loadSubtaskTransitionSnapshots
import com.kgs.calendar.widget.model.resolveSubtasksExpandedByDefault
import com.kgs.calendar.widget.model.usesDirectCollectionItems
import com.kgs.calendar.widget.update.animateTasksSubtaskToggle
import com.kgs.calendar.widget.update.refreshTasksWidgetRows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
                val graph = KgsCalendarApplication.graph(context.applicationContext)
                graph.widgets.scheduler.launch {
                    try {
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
                val widgets = KgsCalendarApplication.graph(context.applicationContext).widgets
                widgets.scheduler.launch {
                    try {
                        val defaultExpanded = widgets.settingsStore.tasksWidgetSubtaskDefaultMode.first()
                            .resolveSubtasksExpandedByDefault(widgets.settingsStore.subtasksExpandedByDefault.first())
                        val renderer = widgets.renderer()
                        val before = if (KgsWidgetKind.Tasks.usesDirectCollectionItems()) {
                            widgets.state.collectionRows.get(KgsWidgetKind.Tasks, appWidgetId)
                        } else {
                            null
                        }
                        val wasExpanded = widgets.state.taskExpansion.isExpanded(appWidgetId, taskId, defaultExpanded)
                        widgets.state.taskExpansion.setExpanded(appWidgetId, taskId, !wasExpanded)
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
                                animateTasksSubtaskToggle(widgets, before, target, taskId)
                            } == true
                        } else {
                            false
                        }
                        if (!animated) {
                            refreshTasksWidgetRows(widgets, intArrayOf(appWidgetId))
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
            KgsCalendarApplication.graph(context.applicationContext).widgets.scheduler.updateAll()
        }
    }
}
