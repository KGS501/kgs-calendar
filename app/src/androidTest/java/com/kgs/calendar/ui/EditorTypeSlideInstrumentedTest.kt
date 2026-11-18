package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.test.platform.app.InstrumentationRegistry
import com.kgs.calendar.R
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditorTypeSlideInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun switchingToTheTaskSlidesOnlyTheFieldsBelowTheSwitch() {
        var isEvent by mutableStateOf(true)
        composeRule.setContent {
            KgsCalendarTheme {
                // The same wiring as the editor sheet's type transition.
                AnimatedContent(
                    targetState = isEvent,
                    transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    label = "editorType",
                ) { event ->
                    val slide = remember(this, event) { EditorTypeSlide(this, event) }
                    CompositionLocalProvider(LocalEditorTypeSlide provides slide) {
                        EditorContainer(
                            title = if (event) "New event" else "New task",
                            action = "Save",
                            onAction = {},
                            onClose = {},
                            typeSwitch = {
                                EditorTypeSwitch(
                                    eventSelected = event,
                                    onSelectEvent = { isEvent = true },
                                    onSelectTask = { isEvent = false },
                                )
                            },
                        ) {
                            Text(if (event) "Event fields" else "Task fields")
                        }
                    }
                }
            }
        }
        val task = context.getString(R.string.task)
        val switchBefore = composeRule.onNodeWithText(task).getBoundsInRoot()
        val fieldsBefore = composeRule.onNodeWithText("Event fields").getBoundsInRoot()

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText(task).performClick()
        composeRule.mainClock.advanceTimeBy(MotionLong / 3L)

        // Both editors' switches sit exactly where the switch was.
        val switches = composeRule.onAllNodesWithText(task).fetchSemanticsNodes()
        assertEquals(2, switches.size)
        composeRule.onAllNodesWithText(task).apply {
            for (index in switches.indices) assertSameBounds(switchBefore, get(index).getBoundsInRoot())
        }
        // The fields move: the event's out to the left, the task's in from the right. Bounds are
        // clipped to the screen, so the fields leaving on the left are measured by their right edge.
        assertTrue(composeRule.onNodeWithText("Event fields").getBoundsInRoot().right < fieldsBefore.right)
        assertTrue(composeRule.onNodeWithText("Task fields").getBoundsInRoot().left > fieldsBefore.left)

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithText("Event fields").fetchSemanticsNodes().size)
        assertEquals(fieldsBefore.left, composeRule.onNodeWithText("Task fields").getBoundsInRoot().left)
    }

    private fun assertSameBounds(expected: DpRect, actual: DpRect) {
        assertEquals(expected.left.value, actual.left.value, 0.5f)
        assertEquals(expected.top.value, actual.top.value, 0.5f)
    }
}
