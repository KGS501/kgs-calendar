package com.kgs.calendar.widget

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.YearMonth
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
    fun fullRefreshApplyIsRejectedAfterNewerCachedNavigationCommits() {
        val refreshTarget = coordinator.navigate(widgetId, MonthCommand.Next)
        val refreshReady = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)
        var refreshApplied = false
        val executor = Executors.newSingleThreadExecutor()

        try {
            val refreshResult = executor.submit<Boolean> {
                refreshReady.countDown()
                assertTrue(releaseRefresh.await(5, TimeUnit.SECONDS))
                coordinator.applyIfRevisionCurrent(widgetId, refreshTarget.revision) {
                    refreshApplied = true
                }
            }
            assertTrue(refreshReady.await(5, TimeUnit.SECONDS))

            val cachedNavigation = coordinator.navigate(widgetId, MonthCommand.Next)
            releaseRefresh.countDown()

            assertFalse(refreshResult.get(5, TimeUnit.SECONDS))
            assertFalse(refreshApplied)
            assertTrue(coordinator.isCurrent(cachedNavigation))
        } finally {
            releaseRefresh.countDown()
            executor.shutdownNow()
        }
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
    fun applyForOneWidgetDoesNotBlockNavigationForAnotherWidget() {
        val firstWidget = coordinator.navigate(widgetId, MonthCommand.Next)
        val applyEntered = CountDownLatch(1)
        val releaseApply = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val applyResult = executor.submit<Boolean> {
                coordinator.applyIfCurrent(firstWidget) {
                    applyEntered.countDown()
                    assertTrue(releaseApply.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(applyEntered.await(5, TimeUnit.SECONDS))

            val otherWidget = executor.submit<MonthNavSnapshot> {
                coordinator.navigate(widgetId + 1, MonthCommand.Next)
            }.get(1, TimeUnit.SECONDS)

            assertEquals(widgetId + 1, otherWidget.widgetId)
            releaseApply.countDown()
            assertTrue(applyResult.get(5, TimeUnit.SECONDS))
        } finally {
            releaseApply.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun sameWidgetIsSerializedAcrossStorageInstances() {
        val snapshots = mutableMapOf<Int, MonthNavSnapshot>()
        val synchronization = MonthNavSynchronizationDomain()
        val firstCoordinator = WidgetMonthNavigation(
            InMemoryMonthNavStorage(snapshots, synchronization),
            clock,
        )
        val secondCoordinator = WidgetMonthNavigation(
            InMemoryMonthNavStorage(snapshots, synchronization),
            clock,
        )
        val current = firstCoordinator.navigate(widgetId, MonthCommand.Next)
        val applyEntered = CountDownLatch(1)
        val releaseApply = CountDownLatch(1)
        val navigationCommitted = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val applyResult = executor.submit<Boolean> {
                firstCoordinator.applyIfCurrent(current) {
                    applyEntered.countDown()
                    assertTrue(releaseApply.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(applyEntered.await(5, TimeUnit.SECONDS))

            val navigationResult = executor.submit<MonthNavSnapshot> {
                secondCoordinator.navigate(widgetId, MonthCommand.Next).also {
                    navigationCommitted.countDown()
                }
            }
            assertFalse(navigationCommitted.await(200, TimeUnit.MILLISECONDS))

            releaseApply.countDown()
            assertTrue(applyResult.get(5, TimeUnit.SECONDS))
            assertEquals(2L, navigationResult.get(5, TimeUnit.SECONDS).revision)
        } finally {
            releaseApply.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun broadcastCompletionFinishesExactlyOnceWhenJobCompletes() {
        val finishes = AtomicInteger()
        val completion = OnceCompletion(finishes::incrementAndGet)

        assertEquals(0, finishes.get())
        completion.complete()
        completion.complete()

        assertEquals(1, finishes.get())
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
    fun cachedTargetUsesCompletePageImmediately() {
        val cached = page(YearMonth.of(2026, 2))

        val initial = selectMonthNavigationInitialPage(cached)

        assertTrue(initial === cached)
    }

    @Test
    fun cacheMissKeepsCurrentWidgetUntilCompleteTargetIsLoaded() {
        assertEquals(null, selectMonthNavigationInitialPage(null))
    }

    @Test
    fun currentNavigationAppliesLatestPageWithoutCachingDuringDataChurn() {
        assertEquals(
            MonthAuthoritativePageDecision(apply = true, cache = false),
            authoritativeMonthPageDecision(
                navigationCurrent = true,
                loadedGeneration = 12,
                currentGeneration = 13,
            ),
        )
    }

    @Test
    fun stableCurrentNavigationAppliesAndCachesLatestPage() {
        assertEquals(
            MonthAuthoritativePageDecision(apply = true, cache = true),
            authoritativeMonthPageDecision(
                navigationCurrent = true,
                loadedGeneration = 13,
                currentGeneration = 13,
            ),
        )
    }

    @Test
    fun supersededNavigationNeitherAppliesNorCachesLoadedPage() {
        assertEquals(
            MonthAuthoritativePageDecision(apply = false, cache = false),
            authoritativeMonthPageDecision(
                navigationCurrent = false,
                loadedGeneration = 13,
                currentGeneration = 13,
            ),
        )
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

    private class InMemoryMonthNavStorage(
        private val snapshots: MutableMap<Int, MonthNavSnapshot> = mutableMapOf(),
        private val synchronization: MonthNavSynchronizationDomain = MonthNavSynchronizationDomain(),
    ) : MonthNavStorage {

        override fun update(
            widgetId: Int,
            transform: (MonthNavSnapshot?) -> MonthNavSnapshot,
        ): MonthNavSnapshot = synchronization.withWidgetLock(widgetId) {
            transform(snapshots[widgetId]).also { snapshots[widgetId] = it }
        }

        override fun read(widgetId: Int): MonthNavSnapshot? =
            synchronization.withWidgetLock(widgetId) { snapshots[widgetId] }

        override fun applyIfCurrent(
            snapshot: MonthNavSnapshot,
            block: () -> Unit,
        ): Boolean = synchronization.withWidgetLock(snapshot.widgetId) {
            if (snapshots[snapshot.widgetId]?.revision != snapshot.revision) {
                false
            } else {
                block()
                true
            }
        }

        override fun applyIfRevisionCurrent(
            widgetId: Int,
            revision: Long,
            block: () -> Unit,
        ): Boolean = synchronization.withWidgetLock(widgetId) {
            if (snapshots[widgetId]?.revision != revision) {
                false
            } else {
                block()
                true
            }
        }
    }
}
