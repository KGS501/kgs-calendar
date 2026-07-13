package com.kgs.calendar.ui

import app.cash.turbine.test
import com.kgs.calendar.domain.model.CalendarViewMode
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelSettingsPolicyTest {
    @Test
    fun persistedSelectionRestoreConsumesOnlyTheInitialSettingsSnapshot() = runTest {
        val storedDate = MutableStateFlow(LocalDate.of(2026, 7, 15))
        val storedView = MutableStateFlow(CalendarViewMode.ThreeDay)
        val weekViewEnabled = MutableStateFlow(true)
        val firstDayOfWeek = MutableStateFlow(DayOfWeek.MONDAY)

        restorePersistedSelectedDate(
            storedDate = storedDate,
            storedView = storedView,
            weekViewEnabled = weekViewEnabled,
            firstDayOfWeek = firstDayOfWeek,
        ).test {
            assertEquals(LocalDate.of(2026, 7, 13), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun firstDayChangeWaitsForPublishedPolicyBeforeAligningActiveWeek() = runTest {
        val oldDate = LocalDate.of(2026, 7, 15)
        val alignedDate = LocalDate.of(2026, 7, 12)
        val publishedFirstDay = MutableStateFlow(DayOfWeek.MONDAY)
        val selectedDate = MutableStateFlow(oldDate)
        val observedStates = mutableListOf<Pair<DayOfWeek, LocalDate>>()
        var persisted = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            combine(publishedFirstDay, selectedDate) { firstDay, date -> firstDay to date }
                .collect(observedStates::add)
        }

        val transition = launch {
            applyFirstDayOfWeekChange(
                dayOfWeek = DayOfWeek.SUNDAY,
                activeWeekDate = { oldDate },
                persistFirstDayOfWeek = { persisted = true },
                publishedFirstDayOfWeek = publishedFirstDay,
                selectDate = { selectedDate.value = it },
            )
        }
        runCurrent()

        assertTrue(persisted)
        assertTrue(transition.isActive)
        assertEquals(oldDate, selectedDate.value)

        publishedFirstDay.value = DayOfWeek.SUNDAY
        runCurrent()

        assertFalse(observedStates.contains(DayOfWeek.MONDAY to alignedDate))
        assertEquals(DayOfWeek.SUNDAY to alignedDate, observedStates.last())
        assertEquals(alignedDate, selectedDate.value)
    }
}
