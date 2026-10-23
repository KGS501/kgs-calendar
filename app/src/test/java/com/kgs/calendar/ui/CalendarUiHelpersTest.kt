package com.kgs.calendar.ui

import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.source.CategorySync
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarUiHelpersTest {
    @Test
    fun weekViewFabUsesTodayWhenTodayIsInsideAlignedWeek() {
        val state = CalendarUiState(
            selectedView = CalendarViewMode.ThreeDay,
            selectedDate = LocalDate.of(2026, 7, 15),
            weekViewEnabled = true,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(
            LocalDate.of(2026, 7, 19),
            state.defaultFabCreationDate(LocalDate.of(2026, 7, 19)),
        )
        assertEquals(
            LocalDate.of(2026, 7, 13),
            state.defaultFabCreationDate(LocalDate.of(2026, 7, 20)),
        )
    }

    @Test
    fun seamlessWeekFabUsesActualSettledDayWindow() {
        val state = CalendarUiState(
            selectedView = CalendarViewMode.ThreeDay,
            selectedDate = LocalDate.of(2026, 7, 15),
            weekViewEnabled = true,
            fullWeekSwipeEnabled = false,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(
            LocalDate.of(2026, 7, 21),
            state.defaultFabCreationDate(LocalDate.of(2026, 7, 21)),
        )
        assertEquals(
            LocalDate.of(2026, 7, 15),
            state.defaultFabCreationDate(LocalDate.of(2026, 7, 22)),
        )
    }

    @Test
    fun multipleDaysFabKeepsConfiguredVisibleWindow() {
        val state = CalendarUiState(
            selectedView = CalendarViewMode.ThreeDay,
            selectedDate = LocalDate.of(2026, 7, 15),
            multiDayCount = 4,
            weekViewEnabled = false,
        )

        assertEquals(
            LocalDate.of(2026, 7, 18),
            state.defaultFabCreationDate(LocalDate.of(2026, 7, 18)),
        )
        assertEquals(
            LocalDate.of(2026, 7, 15),
            state.defaultFabCreationDate(LocalDate.of(2026, 7, 19)),
        )
    }

    @Test
    fun categorySuggestionsLeaveOutReadOnlyCalendarsUnlessTheUserIncludesThem() {
        val own = collection("/own/", readOnly = false)
        val shared = collection("/shared/", readOnly = true)
        val state = CalendarUiState(
            collections = listOf(own, shared),
            events = listOf(event(own, "Work, Family"), event(shared, "Ann's project")),
        )

        assertEquals(listOf("Family", "Work"), state.allKnownCategoryTags())
        assertEquals(
            listOf("Ann's project", "Family", "Work"),
            state.copy(categorySync = CategorySync(enabledIn = setOf(shared.href))).allKnownCategoryTags(),
        )
        assertEquals(
            emptyList<String>(),
            state.copy(categorySync = CategorySync(disabledIn = setOf(own.href))).allKnownCategoryTags(),
        )
    }

    private fun collection(href: String, readOnly: Boolean) = CollectionEntity(
        href = href,
        accountId = "account",
        displayName = href,
        color = 0,
        supportsEvents = true,
        supportsTasks = true,
        syncToken = null,
        ctag = null,
        readOnly = readOnly,
    )

    private fun event(collection: CollectionEntity, categories: String) = EventEntity(
        uid = "${collection.href}event",
        collectionHref = collection.href,
        resourceHref = "${collection.href}event.ics",
        title = "Event",
        description = null,
        location = null,
        startsAtMillis = 0,
        endsAtMillis = 3_600_000,
        allDay = false,
        recurrenceRule = null,
        isRecurring = false,
        color = 0,
        categories = categories,
    )
}
