package com.kgs.calendar.widget.data

import android.content.Context
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.R
import com.kgs.calendar.domain.event.displayColor
import com.kgs.calendar.domain.event.endDateInclusive
import com.kgs.calendar.domain.event.isCancelled
import com.kgs.calendar.domain.event.monthOccurrenceKey
import com.kgs.calendar.domain.model.isMonthSurfaceTaskVisible
import com.kgs.calendar.domain.task.displayColor
import com.kgs.calendar.domain.time.toDate
import com.kgs.calendar.widget.WidgetLog
import com.kgs.calendar.widget.model.WidgetMonthCandidate
import com.kgs.calendar.widget.model.WidgetMonthLayout
import com.kgs.calendar.widget.model.WidgetMonthModel
import com.kgs.calendar.widget.model.WidgetMonthPage
import com.kgs.calendar.widget.model.WidgetRenderSettings
import com.kgs.calendar.widget.model.monthCacheWindow
import com.kgs.calendar.widget.state.KgsWidgetMonthPageCache
import com.kgs.calendar.widget.state.WidgetDataGeneration
import com.kgs.calendar.widget.state.widgetMonthPageModelNamespace
import com.kgs.calendar.widget.update.KgsWidgetUpdateScheduler
import com.kgs.calendar.widget.withWidgetLocale
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

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
            .filterNot { it.isCancelled() }
            .forEach { event ->
                val itemStart = event.startsAtMillis.toDate(zoneId)
                val itemEnd = event.endDateInclusive(zoneId).coerceAtLeast(itemStart)
                if (itemEnd < start || !itemStart.isBefore(endExclusive)) return@forEach
                candidates += WidgetMonthCandidate(
                    id = "event:${event.monthOccurrenceKey(uidFallbackForBlankHref = true)}",
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
            .filter { task -> isMonthSurfaceTaskVisible(task.isCompleted, task.status) }
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
    val months = monthCacheWindow(centerMonth)
    if (months.all { month -> KgsWidgetMonthPageCache.get(month, settings, zoneId.id, generation) != null }) return
    val appContext = context.applicationContext
    KgsWidgetUpdateScheduler.launchLatest(
        key = "month-cache:${widgetMonthPageModelNamespace(settings, zoneId.id)}",
    ) {
        val source = WidgetMonthPageSource(appContext, zoneId)
        forEachUncachedWidgetMonth(
            months = months,
            isCached = { month -> KgsWidgetMonthPageCache.get(month, settings, zoneId.id, generation) != null },
            shouldContinue = { WidgetDataGeneration.current() == generation },
        ) { month ->
            try {
                val page = source.load(month, settings)
                if (WidgetDataGeneration.current() != generation) return@forEachUncachedWidgetMonth
                KgsWidgetMonthPageCache.put(month, settings, zoneId.id, page, generation)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                WidgetLog.d(appContext, "Failed to warm Month widget cache for $month", error)
            }
        }
    }
}

internal suspend fun forEachUncachedWidgetMonth(
    months: Iterable<YearMonth>,
    isCached: (YearMonth) -> Boolean,
    shouldContinue: () -> Boolean = { true },
    load: suspend (YearMonth) -> Unit,
) {
    for (month in months) {
        if (!shouldContinue()) return
        if (!isCached(month)) load(month)
    }
}
