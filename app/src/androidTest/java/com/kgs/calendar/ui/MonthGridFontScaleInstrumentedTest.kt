package com.kgs.calendar.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.ui.theme.CappedFontScale
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.theme.MaxCalendarFontScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MonthGridFontScaleInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theLargestSystemFontKeepsDayNumbersAndPillTitlesWhole() {
        val zone = ZoneId.systemDefault()
        val holiday = LocalDate.of(2026, 10, 3)
        val event = EventEntity(
            uid = "unity",
            collectionHref = "/local/",
            resourceHref = "/local/unity.ics",
            title = "Unity Day",
            description = null,
            location = null,
            startsAtMillis = holiday.atStartOfDay(zone).toInstant().toEpochMilli(),
            endsAtMillis = holiday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            allDay = true,
            recurrenceRule = null,
            isRecurring = false,
            color = 0xFFB0503C.toInt(),
        )
        composeRule.setContent {
            // Android's largest font size.
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                KgsCalendarTheme {
                    CappedFontScale(MaxCalendarFontScale) {
                        MonthBlock(
                            month = YearMonth.of(2026, 10),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                            showCalendarWeeks = false,
                            eventsByDay = mapOf(holiday to listOf(event)),
                            tasksByDay = emptyMap(),
                            taskColorMode = TaskColorMode.Collection,
                            morphDay = LocalDate.of(2000, 1, 1),
                            onOpenDay = {},
                        )
                    }
                }
            }
        }

        // Two-digit days used to wrap inside their fixed box, which showed only the first digit.
        listOf("15", "28").forEach { day ->
            val layout = composeRule.onNodeWithText(day).textLayout()
            assertEquals("Day $day wraps", 1, layout.lineCount)
            assertTrue("Day $day is cut off at the bottom", layout.fitsVertically())
            assertTrue("Day $day is wider than its box", layout.size.width <= layout.layoutInput.constraints.maxWidth)
        }
        // Pill titles used to be cut off at the bottom. Long titles still fade out at the right edge.
        val pill = composeRule.onNodeWithText("Unity Day").textLayout()
        assertTrue("The pill title is cut off at the bottom", pill.fitsVertically())
    }

    // didOverflowHeight is also set when one unwrapped line is wider than its box, so compare the
    // line itself with the height the text was given.
    private fun TextLayoutResult.fitsVertically(): Boolean =
        getLineBottom(lineCount - 1) <= size.height && size.height <= layoutInput.constraints.maxHeight

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }
}
