package com.kgs.calendar.widget

import android.content.Context
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException

internal object WidgetDataGeneration {
    private val generation = AtomicLong()

    fun current(): Long = generation.get()

    fun increment(): Long = generation.incrementAndGet()
}

internal class WidgetMonthPageSource(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun load(month: YearMonth, settings: WidgetRenderSettings): WidgetMonthPage {
        val start = WidgetMonthModel.gridStart(month, settings.firstDayOfWeek)
        val rowCount = WidgetMonthModel.rowCount(month, settings.firstDayOfWeek)
        val endExclusive = start.plusDays((rowCount * 7).toLong())
        val monthLayout = loadLayout(month, start, rowCount, endExclusive, settings)
        return WidgetMonthModel.page(
            month = month,
            start = start,
            rowCount = rowCount,
            monthLayout = monthLayout,
        )
    }

    fun empty(month: YearMonth, settings: WidgetRenderSettings): WidgetMonthPage {
        val start = WidgetMonthModel.gridStart(month, settings.firstDayOfWeek)
        val rowCount = WidgetMonthModel.rowCount(month, settings.firstDayOfWeek)
        return WidgetMonthModel.page(
            month = month,
            start = start,
            rowCount = rowCount,
            monthLayout = WidgetMonthLayout(emptyMap(), emptyMap()),
        )
    }

    private suspend fun loadLayout(
        month: YearMonth,
        start: LocalDate,
        rowCount: Int,
        endExclusive: LocalDate,
        settings: WidgetRenderSettings,
    ): WidgetMonthLayout {
        val startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = endExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val graph = KgsCalendarApplication.graph(context)
        val labels = context.withWidgetLocale(settings.locale)
        val candidates = mutableListOf<WidgetMonthCandidate>()
        graph.repository.eventsSnapshot(startMillis, endMillis)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
            .forEach { event ->
                val itemStart = event.startsAtMillis.toDate(zoneId)
                val itemEnd = event.endDateInclusive(zoneId).coerceAtLeast(itemStart)
                if (itemEnd < start || !itemStart.isBefore(endExclusive)) return@forEach
                candidates += WidgetMonthCandidate(
                    id = "event:${event.monthOccurrenceKey()}",
                    title = event.title.ifBlank { labels.getString(R.string.no_title) },
                    color = event.displayColor(),
                    sortMillis = event.startsAtMillis,
                    start = itemStart,
                    end = itemEnd,
                    completed = false,
                )
            }
        graph.repository.datedTasksSnapshot(startMillis, endMillis)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
            .filter { settings.showCompletedTasks || !it.isCompleted }
            .forEach { task ->
                val itemStart = task.startAtMillis?.toDate(zoneId)
                    ?: task.dueAtMillis?.toDate(zoneId)
                    ?: return@forEach
                val itemEnd = (
                    task.dueAtMillis?.toDate(zoneId)
                        ?: task.startAtMillis?.toDate(zoneId)
                        ?: itemStart
                    ).coerceAtLeast(itemStart)
                if (itemEnd < start || !itemStart.isBefore(endExclusive)) return@forEach
                candidates += WidgetMonthCandidate(
                    id = "task:${task.resourceHref.ifBlank { task.uid }}",
                    title = task.title.ifBlank { labels.getString(R.string.no_title) },
                    color = task.displayColor(settings.taskColorMode),
                    sortMillis = task.startAtMillis ?: task.dueAtMillis ?: Long.MAX_VALUE,
                    start = itemStart,
                    end = itemEnd,
                    completed = task.isCompleted,
                )
            }
        return WidgetMonthModel.layout(month, start, rowCount, candidates, settings.locale)
    }
}

internal fun warmWidgetMonthPageCache(
    context: Context,
    zoneId: ZoneId,
    centerMonth: YearMonth,
    settings: WidgetRenderSettings,
) {
    val generation = WidgetDataGeneration.current()
    val months = listOf(centerMonth.minusMonths(1), centerMonth.plusMonths(1))
        .filter { month -> KgsWidgetMonthPageCache.get(month, settings, zoneId.id, generation) == null }
    if (months.isEmpty()) return
    val appContext = context.applicationContext
    KgsWidgetUpdateScheduler.launch {
        val source = WidgetMonthPageSource(appContext, zoneId)
        months.forEach { month ->
            try {
                val page = source.load(month, settings)
                if (WidgetDataGeneration.current() == generation) {
                    KgsWidgetMonthPageCache.put(month, settings, zoneId.id, page, generation)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                WidgetLog.d(appContext, "Failed to warm Month widget cache", error)
            }
        }
    }
}
