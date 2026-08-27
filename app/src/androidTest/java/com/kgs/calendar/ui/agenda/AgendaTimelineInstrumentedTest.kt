package com.kgs.calendar.ui.agenda

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.domain.model.CalendarRange
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.time.CalendarTimeSnapshot
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AgendaTimelineInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 8, 23)
    private val loadedRange = CalendarRange(LocalDate.of(2025, 8, 1), LocalDate.of(2028, 9, 1))

    @Test
    fun sparseNavigationLandsOnTheRequestedJanuaryInsteadOfDecember() {
        val january = LocalDate.of(2026, 1, 1)
        setAgenda(
            events = listOf(
                event(LocalDate.of(2025, 12, 1), "December item"),
                event(january, "January item"),
                event(LocalDate.of(2026, 6, 12), "June item"),
                event(today, "Today item"),
            ),
            request = AgendaNavigationRequest(1L, january),
        )

        composeRule.waitUntil(timeoutMillis = 4_000) { headerShows(january, "January") }
        composeRule.onNodeWithText("January item").assertIsDisplayed()
    }

    @Test
    fun emptyRequestedDateStillHasAnExactAgendaPosition() {
        val january = LocalDate.of(2026, 1, 1)
        setAgenda(
            events = listOf(
                event(LocalDate.of(2025, 12, 1), "December item"),
                event(LocalDate.of(2026, 5, 1), "May item"),
            ),
            request = AgendaNavigationRequest(1L, january),
        )

        composeRule.waitUntil(timeoutMillis = 4_000) { headerShows(january, "January") }
    }

    @Test
    fun currentDayDateBlockUsesTheTimelineHighlight() {
        setAgenda(
            events = listOf(event(today, "Today item")),
            request = AgendaNavigationRequest(1L, today),
        )

        composeRule.waitUntil(timeoutMillis = 4_000) { headerShows(today, "August") }
        composeRule.onNodeWithTag("agenda-current-day", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(38.dp)
    }

    @Test
    fun currentDayHighlightUsesWhiteTextInDarkMode() {
        setAgenda(
            events = listOf(event(today, "Today item")),
            request = AgendaNavigationRequest(1L, today),
            darkTheme = true,
        )

        composeRule.waitUntil(timeoutMillis = 4_000) { headerShows(today, "August") }
        val pixels = composeRule
            .onNodeWithTag("agenda-current-day", useUnmergedTree = true)
            .captureToImage()
            .toPixelMap()
        var nearWhitePixels = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (pixel.red > 0.92f && pixel.green > 0.92f && pixel.blue > 0.92f) {
                    nearWhitePixels++
                }
            }
        }
        assertTrue("The dark-mode current-day label contains no white text", nearWhitePixels > 4)
    }

    @Test
    fun navigationWaitsForTheNewLoadedRangeInsteadOfUsingStaleRows() {
        val january = LocalDate.of(2031, 1, 1)
        lateinit var publishTargetWindow: () -> Unit
        composeRule.setContent {
            var events by remember { mutableStateOf(listOf(event(today, "Old window"))) }
            var range by remember { mutableStateOf(loadedRange) }
            publishTargetWindow = {
                range = CalendarRange(LocalDate.of(2030, 1, 1), LocalDate.of(2033, 1, 1))
                events = listOf(event(january, "New window"))
            }
            AgendaTestContent(events, range, AgendaNavigationRequest(9L, january))
        }
        composeRule.waitForIdle()
        assertFalse(headerShows(january, "January"))

        composeRule.runOnIdle(publishTargetWindow)
        composeRule.waitUntil(timeoutMillis = 4_000) { headerShows(january, "January") }
        composeRule.onNodeWithText("New window").assertIsDisplayed()
    }

    @Test
    fun laterNavigationUsesAQuickDirectionalScrollCueBeforeSettling() {
        val january = LocalDate.of(2026, 1, 1)
        lateinit var navigateToJanuary: () -> Unit
        composeRule.setContent {
            var request by remember { mutableStateOf(AgendaNavigationRequest(1L, today)) }
            navigateToJanuary = { request = AgendaNavigationRequest(2L, january) }
            AgendaTestContent(
                events = List(34) { index ->
                    val date = january.plusWeeks(index.toLong())
                    event(date, "Weekly item $index")
                } + event(today, "Today item"),
                range = loadedRange,
                request = request,
            )
        }
        composeRule.waitUntil(timeoutMillis = 4_000) { headerShows(today, "August") }
        val initialScroll = agendaScrollValue()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle(navigateToJanuary)
        var stagedScroll = initialScroll
        var attempts = 0
        while (stagedScroll >= initialScroll - 2f && attempts < 120) {
            composeRule.mainClock.advanceTimeByFrame()
            Thread.sleep(10L)
            stagedScroll = agendaScrollValue()
            attempts++
        }
        assertTrue("Agenda navigation animation did not start", stagedScroll < initialScroll - 2f)
        val viewportHeight = composeRule.onNodeWithTag("agenda-timeline-list", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "The first visible motion must leave from the current position instead of teleporting near the target " +
                "($initialScroll -> $stagedScroll across a $viewportHeight px viewport)",
            initialScroll - stagedScroll < viewportHeight * 0.35f,
        )
        composeRule.mainClock.advanceTimeBy(190L)
        val middleScroll = agendaScrollValue()
        composeRule.mainClock.advanceTimeBy(210L)
        val settledScroll = agendaScrollValue()

        assertTrue(
            "Backward navigation should visibly scroll toward the target ($stagedScroll, $middleScroll, $settledScroll)",
            stagedScroll > middleScroll && middleScroll > settledScroll,
        )
        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(timeoutMillis = 4_000) { headerShows(january, "January") }
    }

    private fun setAgenda(
        events: List<EventEntity>,
        request: AgendaNavigationRequest,
        darkTheme: Boolean = false,
    ) {
        composeRule.setContent { AgendaTestContent(events, loadedRange, request, darkTheme) }
    }

    @androidx.compose.runtime.Composable
    private fun AgendaTestContent(
        events: List<EventEntity>,
        range: CalendarRange,
        request: AgendaNavigationRequest,
        darkTheme: Boolean = false,
    ) {
        CompositionLocalProvider(
            LocalCalendarTimeSnapshot provides CalendarTimeSnapshot(today, LocalTime.NOON),
        ) {
            KgsCalendarTheme(
                themeMode = AppThemeMode.KgsBlue,
                darkTheme = darkTheme,
                priorityAnimationsEnabled = false,
            ) {
                AgendaTimeline(
                    events = events,
                    tasks = emptyList(),
                    loadedRange = range,
                    navigationRequest = request,
                    today = today,
                    taskColorMode = TaskColorMode.Collection,
                    showCalendarWeeks = true,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    stickyHeaderBackground = MaterialTheme.colorScheme.background,
                    emptyMessage = "No items",
                    onTaskStatusChanged = { _, _ -> },
                    onDetail = {},
                    onLoadEarlier = {},
                    onLoadLater = {},
                )
            }
        }
    }

    private fun headerShows(date: LocalDate, month: String): Boolean {
        val dayVisible = composeRule.onAllNodes(
            hasText(date.dayOfMonth.toString()) and hasAnyAncestor(hasTestTag("agenda-header-day")),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()
        val monthVisible = composeRule.onAllNodes(
            hasText(month) and hasAnyAncestor(hasTestTag("agenda-header-month")),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()
        return dayVisible && monthVisible
    }

    private fun agendaScrollValue(): Float {
        val node = composeRule.onNodeWithTag("agenda-timeline-list", useUnmergedTree = true)
            .fetchSemanticsNode()
        return node.config[SemanticsProperties.VerticalScrollAxisRange].value()
    }

    private fun event(date: LocalDate, title: String): EventEntity {
        val start = date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return EventEntity(
            uid = title,
            collectionHref = "/events/",
            resourceHref = "/events/${title.replace(' ', '-')}.ics",
            title = title,
            description = null,
            location = null,
            startsAtMillis = start,
            endsAtMillis = start + 60L * 60L * 1000L,
            allDay = false,
            recurrenceRule = null,
            isRecurring = false,
            timezoneId = ZoneId.systemDefault().id,
            color = 0,
        )
    }
}
