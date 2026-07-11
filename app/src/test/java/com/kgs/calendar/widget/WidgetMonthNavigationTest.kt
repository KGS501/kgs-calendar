package com.kgs.calendar.widget

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.YearMonth
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetMonthNavigationTest {
    private val widgetId = 42
    private val clock = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC)
    private val storage = InMemoryMonthNavStorage()
    private val coordinator = WidgetMonthNavigation(storage, clock)

    @Test
    fun staleRenderCannotOverwriteLatestTap() {
        val february = coordinator.navigate(widgetId, MonthCommand.Next)
        val march = coordinator.navigate(widgetId, MonthCommand.Next)

        assertFalse(coordinator.isCurrent(february))
        assertTrue(coordinator.isCurrent(march))
        assertEquals(YearMonth.of(2026, 3), march.month)
        assertEquals(2L, march.revision)
    }

    @Test
    fun newerNavigationCannotCommitDuringCurrentApply() {
        val current = coordinator.navigate(widgetId, MonthCommand.Next)
        val applyEntered = CountDownLatch(1)
        val releaseApply = CountDownLatch(1)
        val navigationStarted = CountDownLatch(1)
        val navigationCommitted = CountDownLatch(1)
        val order = Collections.synchronizedList(mutableListOf<String>())
        val executor = Executors.newFixedThreadPool(2)

        try {
            val applyResult = executor.submit<Boolean> {
                coordinator.applyIfCurrent(current) {
                    applyEntered.countDown()
                    assertTrue(releaseApply.await(5, TimeUnit.SECONDS))
                    order += "apply"
                }
            }
            assertTrue(applyEntered.await(5, TimeUnit.SECONDS))

            val navigationResult = executor.submit<MonthNavSnapshot> {
                navigationStarted.countDown()
                coordinator.navigate(widgetId, MonthCommand.Next).also {
                    order += "navigate"
                    navigationCommitted.countDown()
                }
            }
            assertTrue(navigationStarted.await(5, TimeUnit.SECONDS))
            assertFalse(navigationCommitted.await(200, TimeUnit.MILLISECONDS))

            releaseApply.countDown()

            assertTrue(applyResult.get(5, TimeUnit.SECONDS))
            assertEquals(2L, navigationResult.get(5, TimeUnit.SECONDS).revision)
            assertEquals(listOf("apply", "navigate"), order)
        } finally {
            releaseApply.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun oppositeTapUsesCurrentTargetNotLastRenderedMonth() {
        coordinator.navigate(widgetId, MonthCommand.Next)
        val target = coordinator.navigate(widgetId, MonthCommand.Previous)

        assertEquals(YearMonth.now(clock), target.month)
        assertEquals(-1, target.direction)
    }

    @Test
    fun todaySupersedesPendingNavigationWithoutChangingPageSlot() {
        val next = coordinator.navigate(widgetId, MonthCommand.Next)
        val today = coordinator.navigate(widgetId, MonthCommand.Today)

        assertEquals(YearMonth.now(clock), today.month)
        assertEquals(next.page, today.page)
        assertEquals(0, today.direction)
        assertFalse(coordinator.isCurrent(next))
        assertTrue(coordinator.isCurrent(today))
    }

    @Test
    fun cachedTargetUsesCompletePageWithoutSkeleton() {
        val cached = page(YearMonth.of(2026, 2))
        val skeleton = page(YearMonth.of(2026, 2))

        val initial = selectMonthNavigationInitialPage(cached, skeleton)

        assertEquals(MonthNavigationPageStage.Complete, initial.stage)
        assertTrue(initial.page === cached)
    }

    @Test
    fun cacheMissUsesTargetSkeleton() {
        val skeleton = page(YearMonth.of(2026, 2))

        val initial = selectMonthNavigationInitialPage(null, skeleton)

        assertEquals(MonthNavigationPageStage.Skeleton, initial.stage)
        assertTrue(initial.page === skeleton)
    }

    @Test
    fun warmWindowCoversSixMonthsInBothDirections() {
        val center = YearMonth.of(2026, 7)
        val months = monthCacheWindow(center)

        assertEquals(13, months.size)
        assertEquals(center.minusMonths(6), months.first())
        assertEquals(center.plusMonths(6), months.last())
    }

    private fun page(month: YearMonth): WidgetMonthPage = WidgetMonthPage(month, 5, emptyList())

    private class InMemoryMonthNavStorage : MonthNavStorage {
        private val snapshots = mutableMapOf<Int, MonthNavSnapshot>()

        override fun update(
            widgetId: Int,
            transform: (MonthNavSnapshot?) -> MonthNavSnapshot,
        ): MonthNavSnapshot = synchronized(snapshots) {
            transform(snapshots[widgetId]).also { snapshots[widgetId] = it }
        }

        override fun read(widgetId: Int): MonthNavSnapshot? =
            synchronized(snapshots) { snapshots[widgetId] }

        override fun applyIfCurrent(
            snapshot: MonthNavSnapshot,
            block: () -> Unit,
        ): Boolean = synchronized(snapshots) {
            if (snapshots[snapshot.widgetId]?.revision != snapshot.revision) {
                false
            } else {
                block()
                true
            }
        }
    }
}
