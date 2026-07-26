package com.kgs.calendar.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.kgs.calendar.R
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.ui.TaskScheduleEditor
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TaskScheduleEditorInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun removingStartAndEndClearsEachCompleteEndpoint() {
        val date = LocalDate.of(2026, 7, 23)
        val initial = EditorScheduleState.fromPreview(
            EditorSchedulePreview(
                date = date,
                start = LocalTime.of(9, 0),
                end = LocalTime.of(10, 0),
            ),
        )
        val latest = AtomicReference(initial)
        composeRule.setContent {
            var schedule by remember { mutableStateOf(initial) }
            KgsCalendarTheme(
                themeMode = AppThemeMode.KgsBlue,
                darkTheme = false,
                priorityAnimationsEnabled = false,
            ) {
                TaskScheduleEditor(
                    schedule = schedule,
                    onScheduleChange = {
                        schedule = it
                        latest.set(it)
                    },
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val removeStart = context.getString(
            R.string.remove_field,
            context.getString(R.string.start_date),
        )
        val removeEnd = context.getString(
            R.string.remove_field,
            context.getString(R.string.end_date),
        )

        composeRule.onNodeWithContentDescription(removeStart).performClick()
        composeRule.waitForIdle()

        assertFalse(latest.get().hasStartDate)
        assertFalse(latest.get().hasStartTime)
        assertTrue(latest.get().hasEndDate)
        assertTrue(latest.get().hasEndTime)

        composeRule.onNodeWithContentDescription(removeEnd).performClick()
        composeRule.waitForIdle()

        assertFalse(latest.get().hasEndDate)
        assertFalse(latest.get().hasEndTime)
        assertNull(latest.get().lastValidPreview)
    }
}
