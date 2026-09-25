package com.kgs.calendar.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kgs.calendar.widget.model.MonthCommand
import com.kgs.calendar.widget.state.WidgetStateStore
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Widget state written by earlier app versions must survive the move to [WidgetStateStore]. */
@RunWith(RobolectricTestRunner::class)
class WidgetStateStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val legacyPreferences = context.getSharedPreferences("kgs_widget_state", Context.MODE_PRIVATE)

    @Before
    fun clearPreferences() {
        legacyPreferences.edit().clear().commit()
    }

    @Test
    fun readsMonthNavigationStateWrittenUnderLegacyKeys() {
        legacyPreferences.edit()
            .putString("month_11", "2026-03")
            .putInt("month_page_11", 1)
            .putInt("month_direction_11", -1)
            .putLong("month_revision_11", 42L)
            .commit()

        val state = WidgetStateStore(context)

        assertEquals(YearMonth.of(2026, 3), state.month.month(11, fallback = YearMonth.of(2030, 1)))
        assertEquals(42L, state.month.revision(11))
        assertEquals(YearMonth.of(2030, 1), state.month.month(12, fallback = YearMonth.of(2030, 1)))

        val next = state.month.apply(11, MonthCommand.Next)
        assertEquals(YearMonth.of(2026, 4), next.month)
        assertEquals(43L, next.revision)
        assertEquals("2026-04", legacyPreferences.getString("month_11", null))
        assertEquals(43L, legacyPreferences.getLong("month_revision_11", 0L))

        state.month.clear(11)
        assertFalse(legacyPreferences.contains("month_11"))
        assertFalse(legacyPreferences.contains("month_page_11"))
        assertFalse(legacyPreferences.contains("month_direction_11"))
        assertFalse(legacyPreferences.contains("month_revision_11"))
    }

    @Test
    fun readsDayWidgetStateWrittenUnderLegacyKeys() {
        legacyPreferences.edit()
            .putString("day_7", "2026-10-05")
            .putString("day_initial_scroll_v10_ordered_7", "8")
            .putBoolean("day_initialized_v14_ordered_7", true)
            .putBoolean("day_all_day_expanded_7", true)
            .commit()

        val state = WidgetStateStore(context)

        assertEquals(LocalDate.of(2026, 10, 5), state.day.day(7, fallback = LocalDate.of(2030, 1, 1)))
        assertTrue(state.day.isInitialized(7))
        assertTrue(state.day.isAllDayExpanded(7))
        assertFalse(state.day.needsInitialScroll(7, startHour = 8))
        assertTrue(state.day.needsInitialScroll(7, startHour = 9))

        state.day.offset(7, 1)
        assertEquals("2026-10-06", legacyPreferences.getString("day_7", null))

        state.day.clear(7)
        assertFalse(legacyPreferences.contains("day_7"))
        assertFalse(legacyPreferences.contains("day_initial_scroll_v10_ordered_7"))
        assertFalse(legacyPreferences.contains("day_initialized_v14_ordered_7"))
        assertFalse(legacyPreferences.contains("day_all_day_expanded_7"))
    }

    @Test
    fun readsTaskExpansionWrittenUnderLegacyKeys() {
        val taskHref = "/calendars/tasks/parent.ics"
        legacyPreferences.edit()
            .putBoolean("task_expanded_5:${taskHref.hashCode()}", false)
            .commit()

        val state = WidgetStateStore(context)

        assertFalse(state.taskExpansion.isExpanded(5, taskHref, defaultExpanded = true))
        assertTrue(state.taskExpansion.isExpanded(6, taskHref, defaultExpanded = true))

        state.taskExpansion.setExpanded(5, taskHref, expanded = true)
        assertTrue(legacyPreferences.getBoolean("task_expanded_5:${taskHref.hashCode()}", false))
    }
}
