package com.kgs.calendar.ui.month

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.kgs.calendar.ui.monthOverviewTimelineDismissGesture
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MonthOverviewTimelineGestureInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun horizontalSwipeReachesTimelineWhileUpwardSwipeIsReservedForDismissal() {
        val horizontalDistance = AtomicReference(0f)
        val verticalDistance = AtomicReference(0f)
        val verticalEnds = AtomicInteger(0)
        val taps = AtomicInteger(0)
        composeRule.setContent {
            Box(
                Modifier
                    .size(320.dp)
                    .testTag("month-overview-timeline-surface")
                    .monthOverviewTimelineDismissGesture(
                        enabled = true,
                        onVerticalDrag = { delta ->
                            verticalDistance.set(verticalDistance.get() + delta)
                        },
                        onVerticalEnd = { verticalEnds.incrementAndGet() },
                    ),
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable { taps.incrementAndGet() }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                horizontalDistance.set(horizontalDistance.get() + dragAmount)
                            }
                        },
                )
            }
        }

        composeRule.onNodeWithTag("month-overview-timeline-surface").performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals(1, taps.get())

        composeRule.onNodeWithTag("month-overview-timeline-surface").performTouchInput {
            swipe(
                start = Offset(width * 0.8f, height * 0.5f),
                end = Offset(width * 0.2f, height * 0.5f),
                durationMillis = 180,
            )
        }
        composeRule.waitForIdle()

        assertTrue(abs(horizontalDistance.get()) > 100f)
        assertEquals(0f, verticalDistance.get(), 0.001f)
        assertEquals(0, verticalEnds.get())
        assertEquals(1, taps.get())

        composeRule.onNodeWithTag("month-overview-timeline-surface").performTouchInput {
            swipe(
                start = Offset(width * 0.5f, height * 0.8f),
                end = Offset(width * 0.5f, height * 0.2f),
                durationMillis = 180,
            )
        }
        composeRule.waitForIdle()

        assertTrue(verticalDistance.get() < -100f)
        assertEquals(1, verticalEnds.get())
        assertEquals(1, taps.get())
    }
}
