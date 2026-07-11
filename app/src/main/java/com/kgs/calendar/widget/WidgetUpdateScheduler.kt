package com.kgs.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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

internal object KgsWidgetUpdateScheduler {
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
        context: Context,
        kind: KgsWidgetKind,
        appWidgetIds: IntArray? = null,
        debounceMillis: Long = 0L,
        forceFullDayUpdate: Boolean = false,
        cause: WidgetUpdateCause = WidgetUpdateCause.Unknown,
        onCompletion: () -> Unit = {},
    ) {
        val appContext = context.applicationContext
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
                            context = appContext,
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
            context = appContext,
            kind = kind,
            appWidgetIds = appWidgetIds,
            forceFullDayUpdate = forceFullDayUpdate,
            cause = cause,
            onCompletion = onCompletion,
        )
    }

    fun updateAll(context: Context, cause: WidgetUpdateCause = WidgetUpdateCause.DataChange) {
        KgsWidgetKind.entries.forEach { kind ->
            update(context.applicationContext, kind, cause = cause)
        }
    }

    suspend fun updateAllAndAwait(
        context: Context,
        cause: WidgetUpdateCause = WidgetUpdateCause.DataChange,
    ) {
        val appContext = context.applicationContext
        val kinds = KgsWidgetKind.entries.iterator()
        awaitScheduledCompletions(KgsWidgetKind.entries.size) { completion ->
            update(appContext, kinds.next(), cause = cause, onCompletion = completion)
        }
    }

    internal fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    internal fun launchWidgetLatest(
        context: Context,
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
                        context = context.applicationContext,
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

    internal fun launchLatest(
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
        context: Context,
        kind: KgsWidgetKind,
        appWidgetIds: IntArray?,
        forceFullDayUpdate: Boolean,
        cause: WidgetUpdateCause,
        onCompletion: () -> Unit,
    ) {
        val ids = appWidgetIds ?: AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, kind.providerClass))
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
                        KgsWidgetUpdater.update(
                            context = context,
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
