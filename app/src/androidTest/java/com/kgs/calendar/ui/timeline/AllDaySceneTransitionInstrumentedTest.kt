package com.kgs.calendar.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.ui.AllDayViewportOverlay
import com.kgs.calendar.ui.calendar.toDayPage
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AllDaySceneTransitionInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val july26 = LocalDate.of(2026, 7, 26)

    @Test
    fun abstractionTransitionKeepsOneTitleAndMovesPrimaryCardAsOnePiece() {
        val expanded = mutableStateOf(false)
        val dayWidthPx = with(composeRule.density) { 120.dp.toPx() }
        val events = julyFixture()
        composeRule.setContent {
            KgsCalendarTheme(
                themeMode = AppThemeMode.KgsBlue,
                darkTheme = false,
                priorityAnimationsEnabled = false,
            ) {
                Box(Modifier.width(360.dp).height(180.dp)) {
                    AllDayViewportOverlay(
                        events = events,
                        tasks = emptyList(),
                        taskColorMode = TaskColorMode.Collection,
                        anchorPage = july26.toDayPage(),
                        anchorOffsetPx = 0f,
                        dayWidthPx = dayWidthPx,
                        dayStepPx = dayWidthPx,
                        viewportWidthPx = dayWidthPx * 3f,
                        topOffset = 0.dp,
                        height = 180.dp,
                        timedGridTopPadding = 0.dp,
                        hourHeightDp = 60f,
                        timeScrollPx = 0,
                        defaultEventDurationMinutes = 60,
                        draftEvent = null,
                        onDraftTap = {},
                        onAllDaySlotSelected = {},
                        onEventMoved = { _, _, _, _, _ -> },
                        onTaskMoved = { _, _, _, _, _ -> },
                        onEventMovedAllDay = { _, _, _ -> },
                        onTaskMovedAllDay = { _, _, _ -> },
                        maxVisibleItems = 3,
                        expanded = expanded.value,
                        onExpandedChange = { expanded.value = it },
                        onTaskStatusChanged = { _, _ -> },
                        onDetail = {},
                        priorityPageCount = 3,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val rabska = events.single { it.title == "Rabska Fjera" }
        val urlaub = events.single { it.title == "Urlaub Familie" }
        val rabskaTag = primaryTag(rabska)
        val urlaubTag = primaryTag(urlaub)
        val collapsedRabskaY = composeRule.onNodeWithTag(rabskaTag).fetchSemanticsNode().boundsInRoot.top
        val collapsedUrlaubY = composeRule.onNodeWithTag(urlaubTag).fetchSemanticsNode().boundsInRoot.top
        assertTrue(abs(collapsedRabskaY - collapsedUrlaubY) <= 1f)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { expanded.value = true }
        composeRule.mainClock.advanceTimeBy(140)

        composeRule.onAllNodesWithTag(rabskaTag).assertCountEquals(1)
        composeRule.onAllNodesWithTag(urlaubTag).assertCountEquals(1)
        composeRule.onAllNodesWithText("Rabska Fjera").assertCountEquals(1)
        composeRule.onAllNodesWithText("Urlaub Familie").assertCountEquals(1)

        val overlayBounds = composeRule.onNodeWithTag("timeline-all-day-overlay").fetchSemanticsNode().boundsInRoot
        val movingUrlaubBounds = composeRule.onNodeWithTag(urlaubTag).fetchSemanticsNode().boundsInRoot
        assertTrue(movingUrlaubBounds.top > collapsedUrlaubY)
        assertTrue(movingUrlaubBounds.bottom <= overlayBounds.bottom + 1f)

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { expanded.value = false }
        composeRule.mainClock.advanceTimeBy(140)
        composeRule.onAllNodesWithTag(urlaubTag).assertCountEquals(1)
        composeRule.onAllNodesWithText("Urlaub Familie").assertCountEquals(1)
    }

    private fun primaryTag(event: EventEntity): String =
        "timeline-all-day-item-event:${event.uid}:${event.startsAtMillis}"

    private fun julyFixture(): List<EventEntity> = listOf(
        allDayEvent("Schulferien", LocalDate.of(2026, 7, 20), LocalDate.of(2026, 9, 2)),
        allDayEvent("Semesterferien", LocalDate.of(2026, 7, 25), LocalDate.of(2026, 10, 1)),
        allDayEvent("Rabska Fjera", LocalDate.of(2026, 7, 25), LocalDate.of(2026, 7, 28)),
        allDayEvent("Urlaub Familie", LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 18)),
    )

    private fun allDayEvent(
        title: String,
        start: LocalDate,
        endExclusive: LocalDate,
    ): EventEntity {
        val zone = ZoneId.systemDefault()
        return EventEntity(
            uid = title,
            collectionHref = "test",
            resourceHref = "test/$title.ics",
            title = title,
            description = null,
            location = null,
            startsAtMillis = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            endsAtMillis = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
            allDay = true,
            recurrenceRule = null,
            isRecurring = false,
            color = 0xFF5E7FA3.toInt(),
        )
    }
}
