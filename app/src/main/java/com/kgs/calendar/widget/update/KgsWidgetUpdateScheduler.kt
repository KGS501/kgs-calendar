package com.kgs.calendar.widget.update

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.WidgetDependencies
import com.kgs.calendar.widget.model.next
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

internal class OnceCompletion(private val onComplete: () -> Unit) {
    private val completed = AtomicBoolean(false)

    fun complete() {
        if (completed.compareAndSet(false, true)) {
            onComplete()
        }
    }
}

private class CompletionCountdown(
    count: Int,
    private val onComplete: () -> Unit,
) {
    private val remaining = AtomicInteger(count)

    fun completeOne() {
        if (remaining.decrementAndGet() == 0) {
            onComplete()
        }
    }
}

internal suspend fun awaitScheduledCompletions(
    count: Int,
    enqueue: (() -> Unit) -> Unit,
) {
    if (count <= 0) return
    suspendCancellableCoroutine { continuation ->
        val countdown = CompletionCountdown(count) {
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }
        repeat(count) {
            enqueue(countdown::completeOne)
        }
    }
}

private data class WidgetWorkKey(
    val kind: KgsWidgetKind,
    val appWidgetId: Int,
)

private class ScheduledWidgetWork(
    val run: suspend () -> Unit,
    onCompletion: () -> Unit,
) {
    private val completion = OnceCompletion(onCompletion)

    fun complete() = completion.complete()
}

private data class DebouncedWidgetWork(
    val job: Job,
    val completion: OnceCompletion,
)

internal class KgsWidgetUpdateScheduler(private val widgets: WidgetDependencies) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingResizeJobs = mutableMapOf<String, DebouncedWidgetWork>()
    private val latestJobs = mutableMapOf<String, Job>()
    private val widgetWorkQueue = LatestPendingSerialQueue<WidgetWorkKey, ScheduledWidgetWork>(
        scope = scope,
        onSuperseded = ScheduledWidgetWork::complete,
    ) { work ->
        try {
            work.run()
        } finally {
            work.complete()
        }
    }

    fun update(
        kind: KgsWidgetKind,
        appWidgetIds: IntArray? = null,
        debounceMillis: Long = 0L,
        forceFullDayUpdate: Boolean = false,
        cause: WidgetUpdateCause = WidgetUpdateCause.Unknown,
        onCompletion: () -> Unit = {},
    ) {
        if (debounceMillis > 0L) {
            val debounceKey = "${kind.name}:${appWidgetIds?.sorted()?.joinToString(",") ?: "all"}"
            val completion = OnceCompletion(onCompletion)
            synchronized(pendingResizeJobs) {
                pendingResizeJobs.remove(debounceKey)?.let { previous ->
                    previous.job.cancel()
                    previous.completion.complete()
                }
                val job = scope.launch {
                    try {
                        delay(debounceMillis)
                        enqueueUpdates(
                            kind = kind,
                            appWidgetIds = appWidgetIds,
                            forceFullDayUpdate = forceFullDayUpdate,
                            cause = cause,
                            onCompletion = completion::complete,
                        )
                    } finally {
                        synchronized(pendingResizeJobs) {
                            if (pendingResizeJobs[debounceKey]?.job == coroutineContext[Job]) {
                                pendingResizeJobs.remove(debounceKey)
                            }
                        }
                    }
                }
                pendingResizeJobs[debounceKey] = DebouncedWidgetWork(job, completion)
            }
            return
        }
        enqueueUpdates(
            kind = kind,
            appWidgetIds = appWidgetIds,
            forceFullDayUpdate = forceFullDayUpdate,
            cause = cause,
            onCompletion = onCompletion,
        )
    }

    fun updateAll(cause: WidgetUpdateCause = WidgetUpdateCause.DataChange) {
        KgsWidgetKind.entries.forEach { kind ->
            update(kind, cause = cause)
        }
    }

    suspend fun updateAllAndAwait(
        cause: WidgetUpdateCause = WidgetUpdateCause.DataChange,
    ) {
        val kinds = KgsWidgetKind.entries.iterator()
        awaitScheduledCompletions(KgsWidgetKind.entries.size) { completion ->
            update(kinds.next(), cause = cause, onCompletion = completion)
        }
    }

    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    fun launchWidgetLatest(
        kind: KgsWidgetKind,
        appWidgetId: Int,
        cause: WidgetUpdateCause,
        onCompletion: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        val key = WidgetWorkKey(kind, appWidgetId)
        widgetWorkQueue.submit(
            key,
            ScheduledWidgetWork(
                run = {
                    WidgetPerformanceMonitor.trace(
                        context = widgets.appContext,
                        images = widgets.state.bitmapUris,
                        kind = kind,
                        appWidgetId = appWidgetId,
                        cause = cause,
                    ) {
                        block()
                    }
                },
                onCompletion = onCompletion,
            ),
        )
    }

    fun launchLatest(
        key: String,
        onCompletion: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        synchronized(latestJobs) {
            latestJobs.remove(key)?.cancel()
            val job = scope.launch { block() }
            latestJobs[key] = job
            job.invokeOnCompletion {
                onCompletion()
                synchronized(latestJobs) {
                    if (latestJobs[key] == job) {
                        latestJobs.remove(key)
                    }
                }
            }
        }
    }

    private fun enqueueUpdates(
        kind: KgsWidgetKind,
        appWidgetIds: IntArray?,
        forceFullDayUpdate: Boolean,
        cause: WidgetUpdateCause,
        onCompletion: () -> Unit,
    ) {
        val ids = appWidgetIds ?: AppWidgetManager.getInstance(widgets.appContext)
            .getAppWidgetIds(ComponentName(widgets.appContext, kind.providerClass))
        if (ids.isEmpty()) {
            onCompletion()
            return
        }
        val countdown = CompletionCountdown(ids.size, onCompletion)
        ids.forEach { appWidgetId ->
            val key = WidgetWorkKey(kind, appWidgetId)
            widgetWorkQueue.submit(
                key,
                ScheduledWidgetWork(
                    run = {
                        widgets.updater.update(
                            kind = kind,
                            appWidgetIds = intArrayOf(appWidgetId),
                            forceFullDayUpdate = forceFullDayUpdate,
                            cause = cause,
                        )
                    },
                    onCompletion = countdown::completeOne,
                ),
            )
        }
    }
}
