package com.kgs.calendar.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performTextInput
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.search.CalendarSearchMode
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.time.CalendarTimeSnapshot
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CalendarSearchOverlayInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 8, 20)
    private val zone = ZoneId.systemDefault()

    @Test
    fun searchOptionsChangeModeWithoutExposingManualOccurrenceRangeControls() {
        val selectedMode = AtomicReference(CalendarSearchMode.TextAndLabels)

        setSearch(
            onModeChange = selectedMode::set,
        )

        composeRule.onNodeWithTag("searchMode-LabelsOnly").performClick()
        composeRule.waitForIdle()

        assertEquals(CalendarSearchMode.LabelsOnly, selectedMode.get())
        composeRule.onAllNodesWithTag("searchLoadEarlier").assertCountEquals(0)
        composeRule.onAllNodesWithTag("searchLoadLater").assertCountEquals(0)
    }

    @Test
    fun repeatedEventOccurrencesWithTheSameResourceAreAllRendered() {
        val first = event(today.minusDays(1))
        val second = event(today.plusDays(1))

        setSearch(events = listOf(first, second))

        composeRule.onAllNodesWithText("Team sync").assertCountEquals(2)
    }

    @Test
    fun searchResultsUseTheAgendaDateMonthYearAndWeekHeader() {
        setSearch(
            events = listOf(event(today)),
            showCalendarWeeks = true,
        )

        composeRule.onNodeWithTag("agenda-header-day").assertExists()
        composeRule.onNodeWithTag("agenda-header-month").assertExists()
        composeRule.onNodeWithTag("agenda-header-year").assertExists()
        composeRule.onNodeWithTag("agenda-header-week").assertExists()
    }

    @Test
    fun agendaHeaderSpacingStartsBelowTheSearchOptionsDivider() {
        setSearch(
            events = listOf(event(today)),
            showCalendarWeeks = true,
        )

        val optionsBottom = composeRule.onNodeWithTag("search-options-bar")
            .fetchSemanticsNode().boundsInRoot.bottom
        val dividerBottom = composeRule.onNodeWithTag("search-options-divider")
            .fetchSemanticsNode().boundsInRoot.bottom
        val agendaHeaderTop = composeRule.onNodeWithTag("agenda-header-month")
            .fetchSemanticsNode().boundsInRoot.top

        assertEquals(optionsBottom, dividerBottom, 1f)
        assertTrue(agendaHeaderTop > dividerBottom)
    }

    @Test
    fun typingDoesNotWaitForTheExternalSearchStateRoundTrip() {
        setSearch(
            initialQuery = "",
            mirrorQueryChanges = false,
        )

        composeRule.onNodeWithTag("search-query-field")
            .performTextInput("calendar")
        composeRule.onNodeWithTag("search-query-field")
            .assertTextEquals("calendar")
    }

    @Test
    fun scrollingAgendaResultsDoesNotRecomposeTheEntireResultsList() {
        val compositions = AtomicInteger(0)
        val events = (0 until 360).map { index ->
            event(today.plusDays(index.toLong())).copy(
                uid = "event-$index",
                resourceHref = "/events/event-$index.ics",
            )
        }
        composeRule.setContent {
            CompositionLocalProvider(
                LocalCalendarTimeSnapshot provides CalendarTimeSnapshot(today, LocalTime.NOON),
            ) {
                KgsCalendarTheme(
                    themeMode = AppThemeMode.KgsBlue,
                    darkTheme = false,
                    priorityAnimationsEnabled = false,
                ) {
                    SearchResultsList(
                        query = "sync",
                        eventResults = events,
                        taskResults = emptyList(),
                        taskColorMode = TaskColorMode.Collection,
                        subtasksExpandedByDefault = true,
                        onEventClick = {},
                        onTaskClick = {},
                        onTaskStatusChanged = { _, _ -> },
                        agendaDateHierarchy = true,
                        showCalendarWeeks = true,
                        firstDayOfWeek = DayOfWeek.MONDAY,
                        compositionObserver = compositions::incrementAndGet,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val initialCompositions = compositions.get()

        composeRule.onNodeWithTag("search-results-list").performScrollToIndex(300)
        composeRule.waitForIdle()

        assertEquals(initialCompositions, compositions.get())
    }

    @Test
    fun explicitAgendaJumpSurvivesTheFollowingResultRefresh() {
        val targetDate = today.minusDays(90)
        val initialEvents = (0L..90L).map { offset ->
            val date = targetDate.plusDays(offset)
            event(date).copy(
                uid = "event-$date",
                resourceHref = "/events/event-$date.ics",
            )
        }
        val earlierEvents = (1L..10L).map { offset ->
            val date = targetDate.minusDays(offset)
            event(date).copy(
                uid = "earlier-event-$date",
                resourceHref = "/events/earlier-event-$date.ics",
            )
        }
        lateinit var jumpToTarget: () -> Unit
        lateinit var refreshResults: () -> Unit

        composeRule.setContent {
            var events by remember { mutableStateOf(initialEvents) }
            var scrollTarget by remember { mutableStateOf<LocalDate?>(null) }
            var scrollRequest by remember { mutableStateOf(0) }
            jumpToTarget = {
                scrollTarget = targetDate
                scrollRequest += 1
            }
            refreshResults = { events = earlierEvents + events }
            CompositionLocalProvider(
                LocalCalendarTimeSnapshot provides CalendarTimeSnapshot(today, LocalTime.NOON),
            ) {
                KgsCalendarTheme(
                    themeMode = AppThemeMode.KgsBlue,
                    darkTheme = false,
                    priorityAnimationsEnabled = false,
                ) {
                    SearchResultsList(
                        query = "",
                        eventResults = events,
                        taskResults = emptyList(),
                        taskColorMode = TaskColorMode.Collection,
                        subtasksExpandedByDefault = true,
                        onEventClick = {},
                        onTaskClick = {},
                        onTaskStatusChanged = { _, _ -> },
                        showSearchIntro = false,
                        autoScrollToNow = true,
                        scrollRequestKey = scrollRequest,
                        scrollTargetDate = scrollTarget,
                        agendaDateHierarchy = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle(jumpToTarget)
        composeRule.waitForIdle()
        assertTrue(agendaHeaderShowsDay(targetDate.dayOfMonth))

        composeRule.runOnIdle(refreshResults)
        composeRule.waitForIdle()

        assertTrue(agendaHeaderShowsDay(targetDate.dayOfMonth))
    }

    private fun agendaHeaderShowsDay(dayOfMonth: Int): Boolean =
        composeRule.onAllNodes(
            hasText(dayOfMonth.toString()) and
                hasAnyAncestor(hasTestTag("agenda-header-day")),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    private fun setSearch(
        events: List<EventEntity> = emptyList(),
        onModeChange: (CalendarSearchMode) -> Unit = {},
        onEarlier: () -> Unit = {},
        onLater: () -> Unit = {},
        showCalendarWeeks: Boolean = false,
        initialQuery: String = "sync",
        mirrorQueryChanges: Boolean = true,
    ) {
        composeRule.setContent {
            var mode by remember { mutableStateOf(CalendarSearchMode.TextAndLabels) }
            var query by remember { mutableStateOf(initialQuery) }
            CompositionLocalProvider(
                LocalCalendarTimeSnapshot provides CalendarTimeSnapshot(today, LocalTime.NOON),
            ) {
                KgsCalendarTheme(
                    themeMode = AppThemeMode.KgsBlue,
                    darkTheme = false,
                    priorityAnimationsEnabled = false,
                ) {
                    CalendarSearchOverlay(
                        visible = true,
                        query = query,
                        searchMode = mode,
                        results = events,
                        taskResults = emptyList(),
                        allTasksForHierarchy = emptyList(),
                        taskColorMode = TaskColorMode.Collection,
                        subtasksExpandedByDefault = true,
                        onQueryChange = { changed ->
                            if (mirrorQueryChanges) query = changed
                        },
                        onSearchModeChange = {
                            mode = it
                            onModeChange(it)
                        },
                        onLoadEarlierOccurrences = onEarlier,
                        onLoadLaterOccurrences = onLater,
                        onTaskStatusChanged = { _, _ -> },
                        onEventClick = {},
                        onTaskClick = {},
                        onClose = {},
                        showCalendarWeeks = showCalendarWeeks,
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun event(date: LocalDate): EventEntity {
        val start = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        return EventEntity(
            uid = "team-sync",
            collectionHref = "/events/",
            resourceHref = "/events/team-sync.ics",
            title = "Team sync",
            description = null,
            location = null,
            startsAtMillis = start,
            endsAtMillis = start + 60L * 60L * 1000L,
            allDay = false,
            recurrenceRule = "FREQ=DAILY",
            isRecurring = true,
            timezoneId = zone.id,
            color = 0,
        )
    }
}
