package com.kgs.calendar.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal object KgsWidgetUpdateScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingResizeJobs = mutableMapOf<String, Job>()

    fun update(
        context: Context,
        kind: KgsWidgetKind,
        appWidgetIds: IntArray? = null,
        debounceMillis: Long = 0L,
        forceFullDayUpdate: Boolean = false,
    ) {
        if (debounceMillis > 0 && appWidgetIds != null) {
            val appContext = context.applicationContext
            val key = "${kind.name}:${appWidgetIds.sorted().joinToString(",")}"
            synchronized(pendingResizeJobs) {
                pendingResizeJobs.remove(key)?.cancel()
                val resizeJob = scope.launch {
                    delay(debounceMillis)
                    try {
                        KgsWidgetUpdater.update(appContext, kind, appWidgetIds, forceFullDayUpdate = forceFullDayUpdate)
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
                KgsWidgetUpdater.updateKind(context.applicationContext, kind, forceFullDayUpdate = forceFullDayUpdate)
            } else {
                KgsWidgetUpdater.update(context.applicationContext, kind, appWidgetIds, forceFullDayUpdate = forceFullDayUpdate)
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
