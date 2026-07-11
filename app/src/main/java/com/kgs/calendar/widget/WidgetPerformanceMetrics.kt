package com.kgs.calendar.widget

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

enum class WidgetUpdateCause {
    Navigation,
    Interaction,
    DataChange,
    Periodic,
    Resize,
    Configuration,
    Manual,
    Unknown,
}

internal data class WidgetPerformanceSnapshot(
    val updateId: Long,
    val kind: KgsWidgetKind,
    val appWidgetId: Int,
    val cause: WidgetUpdateCause,
    val concurrentAtStart: Int,
    val dataLoadMillis: Long,
    val rowsBuilt: Int,
    val remoteViewsBuildMillis: Long,
    val cacheHits: Int,
    val cacheMisses: Int,
    val bitmapsRendered: Int,
    val bitmapsEncoded: Int,
    val encodedBytes: Long,
    val fileWrites: Int,
    val binderApplyMillis: Long,
    val totalMillis: Long,
) {
    fun logLine(): String =
        "WidgetPerf update=$updateId kind=${kind.name} widget=$appWidgetId cause=${cause.name} " +
            "concurrent=$concurrentAtStart dataMs=$dataLoadMillis rows=$rowsBuilt " +
            "viewsMs=$remoteViewsBuildMillis hits=$cacheHits misses=$cacheMisses " +
            "renders=$bitmapsRendered encodes=$bitmapsEncoded bytes=$encodedBytes writes=$fileWrites " +
            "binderMs=$binderApplyMillis totalMs=$totalMillis"
}

internal class WidgetPerformanceMetrics(
    val updateId: Long,
    val kind: KgsWidgetKind,
    val appWidgetId: Int,
    val cause: WidgetUpdateCause,
    val concurrentAtStart: Int,
    private val startedAtMillis: Long,
) {
    private var completed = false
    private var dataLoadMillis = 0L
    private var rowsBuilt = 0
    private var remoteViewsBuildMillis = 0L
    private var cacheHits = 0
    private var cacheMisses = 0
    private var bitmapsRendered = 0
    private var bitmapsEncoded = 0
    private var encodedBytes = 0L
    private var fileWrites = 0
    private var binderApplyMillis = 0L

    @Synchronized
    fun recordDataLoad(durationMillis: Long, rowsBuilt: Int = 0) {
        dataLoadMillis += durationMillis.coerceAtLeast(0L)
        this.rowsBuilt += rowsBuilt.coerceAtLeast(0)
    }

    @Synchronized
    fun recordRemoteViewsBuild(durationMillis: Long) {
        remoteViewsBuildMillis += durationMillis.coerceAtLeast(0L)
    }

    @Synchronized
    fun recordCacheHit() {
        cacheHits += 1
    }

    @Synchronized
    fun recordCacheMiss() {
        cacheMisses += 1
    }

    @Synchronized
    fun recordBitmapRendered(count: Int = 1) {
        bitmapsRendered += count.coerceAtLeast(0)
    }

    @Synchronized
    fun recordBitmapEncoded(bytes: Long) {
        bitmapsEncoded += 1
        encodedBytes += bytes.coerceAtLeast(0L)
    }

    @Synchronized
    fun recordFileWrite() {
        fileWrites += 1
    }

    @Synchronized
    fun recordBinderApply(durationMillis: Long) {
        binderApplyMillis += durationMillis.coerceAtLeast(0L)
    }

    @Synchronized
    fun complete(finishedAtMillis: Long): WidgetPerformanceSnapshot? {
        if (completed) return null
        completed = true
        return WidgetPerformanceSnapshot(
            updateId = updateId,
            kind = kind,
            appWidgetId = appWidgetId,
            cause = cause,
            concurrentAtStart = concurrentAtStart,
            dataLoadMillis = dataLoadMillis,
            rowsBuilt = rowsBuilt,
            remoteViewsBuildMillis = remoteViewsBuildMillis,
            cacheHits = cacheHits,
            cacheMisses = cacheMisses,
            bitmapsRendered = bitmapsRendered,
            bitmapsEncoded = bitmapsEncoded,
            encodedBytes = encodedBytes,
            fileWrites = fileWrites,
            binderApplyMillis = binderApplyMillis,
            totalMillis = (finishedAtMillis - startedAtMillis).coerceAtLeast(0L),
        )
    }
}

internal object WidgetPerformanceMonitor {
    private val nextUpdateId = AtomicLong()
    private val activeByWidget = ConcurrentHashMap<String, AtomicInteger>()
    private val currentMetrics = ThreadLocal<WidgetPerformanceMetrics?>()

    fun start(
        kind: KgsWidgetKind,
        appWidgetId: Int,
        cause: WidgetUpdateCause,
    ): WidgetPerformanceMetrics {
        val concurrent = activeByWidget
            .computeIfAbsent(key(kind, appWidgetId)) { AtomicInteger() }
            .incrementAndGet()
        return WidgetPerformanceMetrics(
            updateId = nextUpdateId.incrementAndGet(),
            kind = kind,
            appWidgetId = appWidgetId,
            cause = cause,
            concurrentAtStart = concurrent,
            startedAtMillis = SystemClock.elapsedRealtime(),
        )
    }

    fun contextElement(metrics: WidgetPerformanceMetrics): ThreadContextElement<WidgetPerformanceMetrics?> =
        currentMetrics.asContextElement(metrics)

    fun current(): WidgetPerformanceMetrics? = currentMetrics.get()

    suspend fun <T> trace(
        context: Context,
        kind: KgsWidgetKind,
        appWidgetId: Int,
        cause: WidgetUpdateCause,
        block: suspend (WidgetPerformanceMetrics) -> T,
    ): T {
        val metrics = start(kind, appWidgetId, cause)
        return try {
            withContext(contextElement(metrics)) {
                block(metrics)
            }
        } finally {
            KgsWidgetBitmapUriStore.endUpdate(context, appWidgetId)
            finish(context, metrics)
        }
    }

    fun finish(context: Context, metrics: WidgetPerformanceMetrics) {
        val active = activeByWidget[key(metrics.kind, metrics.appWidgetId)]
        if (active?.decrementAndGet() == 0) {
            activeByWidget.remove(key(metrics.kind, metrics.appWidgetId), active)
        }
        metrics.complete(SystemClock.elapsedRealtime())?.let { snapshot ->
            WidgetLog.d(context, snapshot.logLine())
        }
    }

    private fun key(kind: KgsWidgetKind, appWidgetId: Int): String =
        "${kind.name}:$appWidgetId"
}
