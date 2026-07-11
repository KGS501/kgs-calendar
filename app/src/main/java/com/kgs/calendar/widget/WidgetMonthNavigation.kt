package com.kgs.calendar.widget

import java.time.Clock
import java.time.YearMonth
import java.util.concurrent.ConcurrentHashMap

internal enum class MonthCommand {
    Previous,
    Next,
    Today,
}

internal data class MonthNavSnapshot(
    val widgetId: Int,
    val month: YearMonth,
    val page: Int,
    val direction: Int,
    val revision: Long,
)

internal enum class MonthNavigationPageStage { Complete, Skeleton }

internal data class MonthNavigationInitialPage(
    val page: WidgetMonthPage,
    val stage: MonthNavigationPageStage,
)

internal fun selectMonthNavigationInitialPage(
    cached: WidgetMonthPage?,
    skeleton: WidgetMonthPage,
): MonthNavigationInitialPage = if (cached != null) {
    MonthNavigationInitialPage(cached, MonthNavigationPageStage.Complete)
} else {
    MonthNavigationInitialPage(skeleton, MonthNavigationPageStage.Skeleton)
}

internal fun monthCacheWindow(center: YearMonth): List<YearMonth> =
    (-6L..6L).map(center::plusMonths)

internal class MonthNavSynchronizationDomain {
    private val locks = ConcurrentHashMap<Int, Any>()

    fun <T> withWidgetLock(widgetId: Int, block: () -> T): T =
        synchronized(locks.computeIfAbsent(widgetId) { Any() }) {
            block()
        }
}

internal interface MonthNavStorage {
    fun update(
        widgetId: Int,
        transform: (MonthNavSnapshot?) -> MonthNavSnapshot,
    ): MonthNavSnapshot

    fun read(widgetId: Int): MonthNavSnapshot?

    fun applyIfCurrent(
        snapshot: MonthNavSnapshot,
        block: () -> Unit,
    ): Boolean

    fun applyIfRevisionCurrent(
        widgetId: Int,
        revision: Long,
        block: () -> Unit,
    ): Boolean
}

internal class WidgetMonthNavigation(
    private val storage: MonthNavStorage,
    private val clock: Clock,
) {
    fun navigate(widgetId: Int, command: MonthCommand): MonthNavSnapshot =
        storage.update(widgetId) { current ->
            val currentMonth = current?.month ?: YearMonth.now(clock)
            val currentPage = current?.page?.coerceIn(0, 1) ?: 0
            val direction = when (command) {
                MonthCommand.Previous -> -1
                MonthCommand.Next -> 1
                MonthCommand.Today -> 0
            }
            MonthNavSnapshot(
                widgetId = widgetId,
                month = when (command) {
                    MonthCommand.Previous -> currentMonth.minusMonths(1)
                    MonthCommand.Next -> currentMonth.plusMonths(1)
                    MonthCommand.Today -> YearMonth.now(clock)
                },
                page = if (command == MonthCommand.Today) currentPage else 1 - currentPage,
                direction = direction,
                revision = (current?.revision ?: 0L) + 1L,
            )
        }

    fun isCurrent(snapshot: MonthNavSnapshot): Boolean =
        storage.read(snapshot.widgetId)?.revision == snapshot.revision

    fun applyIfCurrent(
        snapshot: MonthNavSnapshot,
        block: () -> Unit,
    ): Boolean = storage.applyIfCurrent(snapshot, block)

    fun applyIfRevisionCurrent(
        widgetId: Int,
        revision: Long,
        block: () -> Unit,
    ): Boolean = storage.applyIfRevisionCurrent(widgetId, revision, block)
}
