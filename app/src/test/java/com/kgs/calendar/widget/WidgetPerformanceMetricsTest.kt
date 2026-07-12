package com.kgs.calendar.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPerformanceMetricsTest {
    @Test
    fun completedSnapshotContainsRecordedPhaseAndCacheCounters() {
        val metrics = WidgetPerformanceMetrics(
            updateId = 7L,
            kind = KgsWidgetKind.Month,
            appWidgetId = 42,
            cause = WidgetUpdateCause.Manual,
            concurrentAtStart = 2,
            startedAtMillis = 1_000L,
        )

        metrics.recordDataLoad(durationMillis = 12L, rowsBuilt = 31)
        metrics.recordRemoteViewsBuild(18L)
        metrics.recordCacheHit()
        metrics.recordCacheMiss()
        metrics.recordBitmapRendered()
        metrics.recordBitmapEncoded(bytes = 4_096L)
        metrics.recordFileWrite()
        metrics.recordBinderApply(9L)

        val snapshot = metrics.complete(1_075L)

        requireNotNull(snapshot)
        assertEquals(7L, snapshot.updateId)
        assertEquals(KgsWidgetKind.Month, snapshot.kind)
        assertEquals(42, snapshot.appWidgetId)
        assertEquals(WidgetUpdateCause.Manual, snapshot.cause)
        assertEquals(2, snapshot.concurrentAtStart)
        assertEquals(12L, snapshot.dataLoadMillis)
        assertEquals(31, snapshot.rowsBuilt)
        assertEquals(18L, snapshot.remoteViewsBuildMillis)
        assertEquals(1, snapshot.cacheHits)
        assertEquals(1, snapshot.cacheMisses)
        assertEquals(1, snapshot.bitmapsRendered)
        assertEquals(1, snapshot.bitmapsEncoded)
        assertEquals(4_096L, snapshot.encodedBytes)
        assertEquals(1, snapshot.fileWrites)
        assertEquals(9L, snapshot.binderApplyMillis)
        assertEquals(75L, snapshot.totalMillis)
    }

    @Test
    fun completionIsIdempotent() {
        val metrics = WidgetPerformanceMetrics(
            updateId = 1L,
            kind = KgsWidgetKind.Tasks,
            appWidgetId = 9,
            cause = WidgetUpdateCause.DataChange,
            concurrentAtStart = 1,
            startedAtMillis = 100L,
        )

        assertEquals(20L, metrics.complete(120L)?.totalMillis)
        assertNull(metrics.complete(140L))
    }
}
