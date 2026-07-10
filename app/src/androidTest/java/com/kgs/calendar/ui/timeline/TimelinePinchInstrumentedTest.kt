package com.kgs.calendar.ui.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.AbsoluteMinHourRowHeightDp
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.DayHeaderHeight
import com.kgs.calendar.ui.MaxHourRowHeightDp
import com.kgs.calendar.ui.TimelineView
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimelinePinchInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val date = LocalDate.of(2026, 7, 10)

    @Test
    fun movingBothFingersApartKeepsTheCentroidMinuteAnchored() = verifyPinch(
        initialUpperY = 400f,
        initialLowerY = 700f,
        finalUpperY = 250f,
        finalLowerY = 850f,
    )

    @Test
    fun movingBothFingersTogetherKeepsTheCentroidMinuteAnchored() = verifyPinch(
        initialUpperY = 250f,
        initialLowerY = 850f,
        finalUpperY = 400f,
        finalLowerY = 700f,
    )

    @Test
    fun stationaryUpperFingerKeepsItsMinuteAnchored() = verifyPinch(
        initialUpperY = 350f,
        initialLowerY = 650f,
        finalUpperY = 350f,
        finalLowerY = 950f,
    )

    @Test
    fun stationaryLowerFingerKeepsItsMinuteAnchored() = verifyPinch(
        initialUpperY = 350f,
        initialLowerY = 650f,
        finalUpperY = 50f,
        finalLowerY = 650f,
    )

    private fun verifyPinch(
        initialUpperY: Float,
        initialLowerY: Float,
        finalUpperY: Float,
        finalLowerY: Float,
    ) {
        val latestHourHeightDp = AtomicReference(60f)
        val hourHeightSamples = CopyOnWriteArrayList<Float>()
        val scrollReference = AtomicReference<ScrollState>()
        composeRule.setContent {
            var hourHeightDp by remember { mutableFloatStateOf(60f) }
            val scroll = rememberScrollState(initial = 360)
            scrollReference.set(scroll)
            KgsCalendarTheme(
                themeMode = AppThemeMode.KgsBlue,
                darkTheme = false,
                priorityAnimationsEnabled = false,
            ) {
                TimelineView(
                    state = CalendarUiState(selectedDate = date, selectedView = CalendarViewMode.Day),
                    selectedView = CalendarViewMode.Day,
                    onDateSelected = {},
                    onViewSelected = {},
                    onMultiDayCountChanged = {},
                    onTaskStatusChanged = { _, _ -> },
                    onEventMoved = { _, _, _, _, _ -> },
                    onTaskMoved = { _, _, _, _, _ -> },
                    onEventMovedAllDay = { _, _, _ -> },
                    onTaskMovedAllDay = { _, _, _ -> },
                    onSlotSelected = { _, _ -> },
                    onAllDaySlotSelected = {},
                    draftEvent = null,
                    onDraftEventChanged = {},
                    onDraftInteraction = {},
                    onDraftTap = {},
                    timelineBottomInset = 0.dp,
                    onDetail = {},
                    timeScroll = scroll,
                    hourHeightDp = hourHeightDp,
                    onHourHeightChange = { changed ->
                        latestHourHeightDp.set(changed)
                        hourHeightSamples += changed
                        hourHeightDp = changed
                    },
                    initialTimeScrollApplied = true,
                    onInitialTimeScrollApplied = {},
                    monthMorphDay = date,
                )
            }
        }
        composeRule.waitForIdle()

        val surface = composeRule.onNodeWithTag("timeline-gesture-surface")
        val bounds = surface.fetchSemanticsNode().boundsInRoot
        val density = composeRule.density
        val initialHourHeightPx = with(density) { 60.dp.toPx() }
        val initialScrollPx = scrollReference.get().value.toFloat()
        val contentTopPx = with(density) { (DayHeaderHeight + 22.dp).toPx() }
        val viewportHeightPx = (bounds.height - contentTopPx).coerceAtLeast(1f)
        val snapshot = PinchSnapshot.begin(
            upperY = initialUpperY,
            lowerY = initialLowerY,
            viewport = TimelineViewportState(initialHourHeightPx, initialScrollPx),
            contentStartMinute = 0,
            contentEndMinute = 24 * 60,
            viewportHeightPx = viewportHeightPx,
            minHourHeightPx = with(density) { AbsoluteMinHourRowHeightDp.dp.toPx() },
            maxHourHeightPx = with(density) { MaxHourRowHeightDp.dp.toPx() },
            contentTopY = contentTopPx,
        )
        val expected = updateVerticalPinch(snapshot, finalUpperY, finalLowerY)
        val x = bounds.width * 0.55f
        surface.performTouchInput {
            down(0, Offset(x, initialUpperY))
            down(1, Offset(x, initialLowerY))
            repeat(6) { index ->
                val fraction = (index + 1) / 6f
                moveTo(0, Offset(x, initialUpperY + (finalUpperY - initialUpperY) * fraction), delayMillis = 16)
                moveTo(1, Offset(x, initialLowerY + (finalLowerY - initialLowerY) * fraction), delayMillis = 16)
            }
        }
        composeRule.waitForIdle()

        val actualHourHeightPx = with(density) { latestHourHeightDp.get().dp.toPx() }
        val actualScrollPx = scrollReference.get().value.toFloat()
        assertEquals(expected.viewport.hourHeightPx, actualHourHeightPx, 2f)
        assertEquals(expected.viewport.scrollPx, actualScrollPx, 3f)
        val finalCentroid = (finalUpperY + finalLowerY) / 2f
        val actualMinute = ((actualScrollPx + finalCentroid - contentTopPx) / actualHourHeightPx) * 60f
        assertTrue(abs(snapshot.anchorMinute - actualMinute) <= 15f)
        assertTrue(hourHeightSamples.distinct().size >= 3)

        surface.performTouchInput {
            up(1)
            up(0)
        }
    }
}
