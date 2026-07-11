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
