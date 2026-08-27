package com.kgs.calendar.ui.month

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.MonthOverview
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
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
        setMonthOverview(onMonthSelected = selectedMonth::set)

        swipeMonth(horizontalFraction = -0.7f, verticalFraction = -0.18f)
        composeRule.waitForIdle()

        assertEquals(initialMonth.plusMonths(1), selectedMonth.get())
    }

    @Test
    fun upwardSwipeDoesNothingAndDayTapSelectsExactDate() {
        val selectedDay = AtomicReference<LocalDate?>()
        val selectedMonth = AtomicReference(initialMonth)
        setMonthOverview(onDaySelected = selectedDay::set, onMonthSelected = selectedMonth::set)

        composeRule.onNodeWithTag("month-overview-day-2026-07-16").performTouchInput { click() }
        swipeMonth(horizontalFraction = 0.08f, verticalFraction = -0.65f)
        composeRule.waitForIdle()

        assertEquals(LocalDate.of(2026, 7, 16), selectedDay.get())
        assertEquals(initialMonth, selectedMonth.get())
    }

    @Test
    fun landscapePlacesTheVerticalMonthStripToTheRightOfTheGrid() {
        composeRule.setContent {
            KgsCalendarTheme(
                themeMode = AppThemeMode.KgsBlue,
                darkTheme = false,
                priorityAnimationsEnabled = false,
            ) {
                MonthOverview(
                    month = initialMonth,
                    state = CalendarUiState(selectedDate = initialMonth.atDay(10)),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    isLandscape = true,
                    onDaySelected = {},
                    onMonthSelected = {},
                )
            }
        }
        composeRule.waitForIdle()

        val gridBounds = composeRule.onNodeWithTag("month-overview-grid", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val stripBounds = composeRule.onNodeWithTag("month-overview-strip", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val mayBounds = composeRule.onNodeWithTag("month-overview-month-2026-05", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val juneBounds = composeRule.onNodeWithTag("month-overview-month-2026-06", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        assertTrue("Month strip should sit to the right of the grid", gridBounds.right <= stripBounds.left)
        assertTrue("Month strip should begin alongside the grid", stripBounds.top <= gridBounds.top + 4f)
        assertTrue("Month choices should stack vertically", juneBounds.top > mayBounds.top)
    }

    @Test
    fun overflowPlusInkIsVerticallyCenteredWithTheEventDots() {
        val day = LocalDate.of(2026, 7, 16)
        composeRule.setContent {
            KgsCalendarTheme(
                themeMode = AppThemeMode.KgsBlue,
                darkTheme = false,
                priorityAnimationsEnabled = false,
            ) {
                MonthOverview(
                    month = initialMonth,
                    state = CalendarUiState(
                        selectedDate = initialMonth.atDay(10),
                        events = listOf(
                            event(day, "one", 0xFFFF0000.toInt()),
                            event(day, "two", 0xFF00AA00.toInt()),
                            event(day, "three", 0xFF0000FF.toInt()),
                            event(day, "four", 0xFFFF00FF.toInt()),
                        ),
                    ),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    onDaySelected = {},
                    onMonthSelected = {},
                )
            }
        }
        composeRule.waitForIdle()

        val dotCenterY = composeRule.onNodeWithTag("month-overview-dot-$day-0", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center.y
        val plusNode = composeRule.onNodeWithTag("month-overview-more-$day", useUnmergedTree = true)
        val plusTop = plusNode.fetchSemanticsNode().boundsInRoot.top
        val pixels = plusNode.captureToImage().toPixelMap()
        val inkRows = buildList {
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) {
                    val color = pixels[x, y]
                    if (color.alpha > 0.2f && color.luminance() < 0.5f) add(y)
                }
            }
        }
        assertTrue("The overflow plus must contain visible ink", inkRows.isNotEmpty())
        val plusInkCenterY = plusTop + (inkRows.min() + inkRows.max()) / 2f

        assertTrue(
            "Overflow plus center $plusInkCenterY should match dot center $dotCenterY",
            abs(plusInkCenterY - dotCenterY) <= 0.25f,
        )
    }

    private fun setMonthOverview(
        onMonthSelected: (YearMonth) -> Unit = {},
        onDaySelected: (LocalDate) -> Unit = {},
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

    private fun event(day: LocalDate, title: String, color: Int): EventEntity {
        val start = day.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return EventEntity(
            uid = title,
            collectionHref = "/events/",
            resourceHref = "/events/$title.ics",
            title = title,
            description = null,
            location = null,
            startsAtMillis = start,
            endsAtMillis = start + 60L * 60L * 1000L,
            allDay = false,
            recurrenceRule = null,
            isRecurring = false,
            timezoneId = ZoneId.systemDefault().id,
            color = color,
        )
    }
}
