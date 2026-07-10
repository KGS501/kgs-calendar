package com.kgs.calendar.ui.month

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.MonthOverview
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MonthOverviewGestureInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val initialMonth = YearMonth.of(2026, 7)

    @Test
    fun interruptedConsecutiveSwipesAdvanceFromPendingMonth() {
        val selectedMonth = AtomicReference(initialMonth)
        setMonthOverview(onMonthSelected = selectedMonth::set)
        composeRule.mainClock.autoAdvance = false

        swipeMonth(horizontalFraction = -0.7f, verticalFraction = 0f)
        composeRule.mainClock.advanceTimeBy(32)
        swipeMonth(horizontalFraction = -0.7f, verticalFraction = 0f)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        assertEquals(initialMonth.plusMonths(2), selectedMonth.get())
    }

    @Test
    fun diagonalHorizontalSwipePagesWithoutTriggeringDismiss() {
        val selectedMonth = AtomicReference(initialMonth)
        val dismissDragCount = AtomicInteger()
        setMonthOverview(
            onMonthSelected = selectedMonth::set,
            onDismissDrag = { dismissDragCount.incrementAndGet() },
        )

        swipeMonth(horizontalFraction = -0.7f, verticalFraction = -0.18f)
        composeRule.waitForIdle()

        assertEquals(initialMonth.plusMonths(1), selectedMonth.get())
        assertEquals(0, dismissDragCount.get())
    }

    @Test
    fun upwardSwipeDismissesAndDayTapSelectsExactDate() {
        val selectedDay = AtomicReference<LocalDate?>()
        val dismissDragCount = AtomicInteger()
        val dismissEndCount = AtomicInteger()
        setMonthOverview(
            onDaySelected = selectedDay::set,
            onDismissDrag = { dismissDragCount.incrementAndGet() },
            onDismissDragEnd = { dismissEndCount.incrementAndGet() },
        )

        composeRule.onNodeWithTag("month-overview-day-2026-07-16").performTouchInput { click() }
        swipeMonth(horizontalFraction = 0.08f, verticalFraction = -0.65f)
        composeRule.waitForIdle()

        assertEquals(LocalDate.of(2026, 7, 16), selectedDay.get())
        assertTrue(dismissDragCount.get() > 0)
        assertEquals(1, dismissEndCount.get())
    }

    private fun setMonthOverview(
        onMonthSelected: (YearMonth) -> Unit = {},
        onDaySelected: (LocalDate) -> Unit = {},
        onDismissDrag: (Float) -> Unit = {},
        onDismissDragEnd: () -> Unit = {},
    ) {
        composeRule.setContent {
            var month by remember { mutableStateOf(initialMonth) }
            KgsCalendarTheme(
                themeMode = AppThemeMode.KgsBlue,
                darkTheme = false,
                priorityAnimationsEnabled = false,
            ) {
                MonthOverview(
                    month = month,
                    state = CalendarUiState(selectedDate = initialMonth.atDay(10)),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    onDaySelected = onDaySelected,
                    onMonthSelected = {
                        month = it
                        onMonthSelected(it)
                    },
                    onMonthOffset = { offset -> month = month.plusMonths(offset) },
                    onDismissDrag = onDismissDrag,
                    onDismissDragEnd = onDismissDragEnd,
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun swipeMonth(horizontalFraction: Float, verticalFraction: Float) {
        composeRule.onNodeWithTag("month-overview-grid", useUnmergedTree = true).performTouchInput {
            val start = Offset(width * 0.5f, height * 0.6f)
            val end = Offset(
                x = start.x + width * horizontalFraction,
                y = start.y + height * verticalFraction,
            )
            swipe(start, end, durationMillis = 180)
        }
    }
}
