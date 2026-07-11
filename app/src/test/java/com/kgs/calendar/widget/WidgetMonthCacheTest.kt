package com.kgs.calendar.widget

import com.kgs.calendar.data.settings.AppThemeMode
import java.time.DayOfWeek
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WidgetMonthCacheTest {
    @Test
    fun pageModelNamespaceIgnoresPresentationOnlySettings() {
        val settings = WidgetRenderSettings()
        val changedTheme = settings.copy(themeMode = AppThemeMode.SystemDynamic)

        assertEquals(
            widgetMonthPageModelNamespace(settings, "Europe/Berlin"),
            widgetMonthPageModelNamespace(changedTheme, "Europe/Berlin"),
        )
    }

    @Test
    fun pageModelNamespaceChangesWithModelInputs() {
        val settings = WidgetRenderSettings()
        val changedFirstDay = settings.copy(firstDayOfWeek = DayOfWeek.SUNDAY)

        assertNotEquals(
            widgetMonthPageModelNamespace(settings, "Europe/Berlin"),
            widgetMonthPageModelNamespace(changedFirstDay, "Europe/Berlin"),
        )
    }

    @Test
    fun navigationLookupPrefersCurrentGenerationThenFallsBackToLatestKnown() {
        val month = YearMonth.of(2027, 2)
        val settings = WidgetRenderSettings(firstDayOfWeek = DayOfWeek.MONDAY)
        val page = WidgetMonthPage(month, 5, emptyList())
        KgsWidgetMonthPageCache.put(month, settings, "cache-test-a", page, generation = 7)

        val current = KgsWidgetMonthPageCache.getForNavigation(
            month,
            settings,
            "cache-test-a",
            generation = 7,
        )
        val latest = KgsWidgetMonthPageCache.getForNavigation(
            month,
            settings,
            "cache-test-a",
            generation = 8,
        )

        assertEquals(page, current?.page)
        assertEquals(WidgetMonthPageFreshness.CurrentGeneration, current?.freshness)
        assertEquals(page, latest?.page)
        assertEquals(WidgetMonthPageFreshness.LatestKnown, latest?.freshness)
    }

    @Test
    fun latestKnownNavigationPageDoesNotCrossModelNamespace() {
        val month = YearMonth.of(2027, 3)
        val monday = WidgetRenderSettings(firstDayOfWeek = DayOfWeek.MONDAY)
        val sunday = monday.copy(firstDayOfWeek = DayOfWeek.SUNDAY)
        KgsWidgetMonthPageCache.put(
            month,
            monday,
            "cache-test-b",
            WidgetMonthPage(month, 5, emptyList()),
            generation = 4,
        )

        assertEquals(
            null,
            KgsWidgetMonthPageCache.getForNavigation(month, sunday, "cache-test-b", generation = 5),
        )
        assertEquals(
            null,
            KgsWidgetMonthPageCache.getForNavigation(month, monday, "cache-test-c", generation = 5),
        )
    }

    @Test
    fun warmerRechecksCacheImmediatelyBeforeEachLoad() = runTest {
        val may = YearMonth.of(2026, 5)
        val june = may.plusMonths(1)
        val cached = mutableSetOf<YearMonth>()
        val loaded = mutableListOf<YearMonth>()

        forEachUncachedWidgetMonth(
            months = listOf(may, june),
            isCached = cached::contains,
        ) { month ->
            loaded += month
            cached += month
            if (month == may) cached += june
        }

        assertEquals(listOf(may), loaded)
    }
}
