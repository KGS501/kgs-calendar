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

    @Test
    fun anEventMayEndWhenItStartsButNotBefore() {
        fun invalid(end: String) = eventDateTimeRangeInvalid("2026-10-24", "2026-10-24", "15:00", end, allDay = false)

        assertEquals(false, invalid("15:00"))
        assertEquals(false, invalid("16:00"))
        assertEquals(true, invalid("14:59"))
    }

    @Test
    fun anEditorNeverSavesToACalendarOutsideItsList() {
        val events = listOf(collection("/work/", readOnly = false), collection("/home/", readOnly = false))

        // A draft carried over from the task editor names a task list: the default is used instead.
        assertEquals("/home/", events.editorCollectionHref("/tasks/", ownHref = null, defaultHref = "/home/"))
        assertEquals("/work/", events.editorCollectionHref(null, ownHref = null, defaultHref = "/gone/"))
        assertEquals("/home/", events.editorCollectionHref("/home/", ownHref = null, defaultHref = "/work/"))
        // An item keeps its own calendar even when that calendar is not offered.
        assertEquals("/hidden/", events.editorCollectionHref("/hidden/", ownHref = "/hidden/", defaultHref = "/work/"))
        assertEquals(null, emptyList<CollectionEntity>().editorCollectionHref("/tasks/", ownHref = null, defaultHref = null))
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
