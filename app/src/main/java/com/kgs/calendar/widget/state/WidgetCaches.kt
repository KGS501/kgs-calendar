package com.kgs.calendar.widget.state

import android.os.SystemClock
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.WIDGET_MONTH_RENDER_SIGNATURE_VERSION
import com.kgs.calendar.widget.model.WidgetCollectionSnapshot
import com.kgs.calendar.widget.model.WidgetMonthPage
import com.kgs.calendar.widget.model.WidgetRenderSettings
import java.time.YearMonth

internal class WidgetInteractionTokens {
    private val tokens = mutableMapOf<String, Long>()

    fun next(key: String): Long =
        synchronized(tokens) {
            val next = (tokens[key] ?: 0L) + 1L
            tokens[key] = next
            next
        }

    fun isCurrent(key: String, token: Long): Boolean =
        synchronized(tokens) { tokens[key] == token }
}

internal enum class WidgetMonthPageFreshness {
    CurrentGeneration,
    LatestKnown,
}

internal data class WidgetMonthPageLookup(
    val page: WidgetMonthPage,
    val freshness: WidgetMonthPageFreshness,
)

internal class WidgetMonthPageCache(private val dataGeneration: WidgetDataGeneration) {
    private val pages = LinkedHashMap<String, WidgetMonthPage>(MAX_ENTRIES, 0.75f, true)

    fun get(
        month: YearMonth,
        settings: WidgetRenderSettings,
        zoneId: String,
        generation: Long = dataGeneration.current(),
    ): WidgetMonthPage? =
        synchronized(pages) { pages[generationKey(month, settings, zoneId, generation)] }

    fun getForNavigation(
        month: YearMonth,
        settings: WidgetRenderSettings,
        zoneId: String,
        generation: Long = dataGeneration.current(),
    ): WidgetMonthPageLookup? = synchronized(pages) {
        pages[generationKey(month, settings, zoneId, generation)]?.let { page ->
            return@synchronized WidgetMonthPageLookup(
                page = page,
                freshness = WidgetMonthPageFreshness.CurrentGeneration,
            )
        }
        pages[latestKey(month, settings, zoneId)]?.let { page ->
            WidgetMonthPageLookup(
                page = page,
                freshness = WidgetMonthPageFreshness.LatestKnown,
            )
        }
    }

    fun put(
        month: YearMonth,
        settings: WidgetRenderSettings,
        zoneId: String,
        page: WidgetMonthPage,
        generation: Long = dataGeneration.current(),
    ) {
        synchronized(pages) {
            pages[generationKey(month, settings, zoneId, generation)] = page
            pages[latestKey(month, settings, zoneId)] = page
            while (pages.size > MAX_ENTRIES) {
                val firstKey = pages.entries.firstOrNull()?.key ?: break
                pages.remove(firstKey)
            }
        }
    }

    private fun generationKey(
        month: YearMonth,
        settings: WidgetRenderSettings,
        zoneId: String,
        generation: Long,
    ): String = "${monthKey(month, settings, zoneId)}|generation=$generation"

    private fun latestKey(
        month: YearMonth,
        settings: WidgetRenderSettings,
        zoneId: String,
    ): String = "${monthKey(month, settings, zoneId)}|latest"

    private fun monthKey(
        month: YearMonth,
        settings: WidgetRenderSettings,
        zoneId: String,
    ): String = "${widgetMonthPageModelNamespace(settings, zoneId)}|month=$month"

    private companion object {
        const val MAX_ENTRIES = 64
    }
}

internal fun widgetMonthPageModelNamespace(
    settings: WidgetRenderSettings,
    zoneId: String,
): String = buildString {
    append(WIDGET_MONTH_RENDER_SIGNATURE_VERSION)
    append('|').append(settings.locale.toLanguageTag())
    append('|').append(settings.firstDayOfWeek.name)
    append('|').append(settings.taskColorMode.name)
    append('|').append(settings.showCompletedTasks)
    append('|').append(settings.hiddenCollectionHrefs.sorted().joinToString(","))
    append('|').append(zoneId)
}

internal class WidgetMonthUpdateSignatures {
    private val appliedSignatures = mutableMapOf<Int, String>()

    fun matches(appWidgetId: Int, signature: String): Boolean =
        synchronized(appliedSignatures) { appliedSignatures[appWidgetId] == signature }

    fun markApplied(appWidgetId: Int, signature: String) {
        synchronized(appliedSignatures) {
            appliedSignatures[appWidgetId] = signature
        }
    }
}

internal class WidgetCollectionUpdateSignatures {
    private val appliedSignatures = mutableMapOf<String, String>()

    fun matches(kind: KgsWidgetKind, appWidgetId: Int, signature: String): Boolean =
        synchronized(appliedSignatures) { appliedSignatures[key(kind, appWidgetId)] == signature }

    fun markApplied(kind: KgsWidgetKind, appWidgetId: Int, signature: String) {
        synchronized(appliedSignatures) {
            appliedSignatures[key(kind, appWidgetId)] = signature
        }
    }

    private fun key(kind: KgsWidgetKind, appWidgetId: Int): String =
        "${kind.name}:$appWidgetId"
}

internal class WidgetCollectionRowsCache {
    private val snapshots = mutableMapOf<String, WidgetCollectionSnapshot>()

    fun put(snapshot: WidgetCollectionSnapshot) {
        synchronized(snapshots) {
            snapshots[key(snapshot.kind, snapshot.appWidgetId)] = snapshot
        }
    }

    fun get(kind: KgsWidgetKind, appWidgetId: Int): WidgetCollectionSnapshot? {
        val now = SystemClock.elapsedRealtime()
        return synchronized(snapshots) {
            val key = key(kind, appWidgetId)
            val snapshot = snapshots[key] ?: return@synchronized null
            if (now - snapshot.createdAtMillis <= MAX_AGE_MS) {
                snapshot
            } else {
                snapshots.remove(key)
                null
            }
        }
    }

    private fun key(kind: KgsWidgetKind, appWidgetId: Int): String =
        "${kind.name}:$appWidgetId"

    private companion object {
        const val MAX_AGE_MS = 30 * 60 * 1_000L
    }
}
