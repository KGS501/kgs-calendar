package com.kgs.calendar.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.ui.DraftEventWireframe
import com.kgs.calendar.ui.EventScheduleEditor
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import java.util.concurrent.atomic.AtomicReference

class EditorWireframeInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun editorAndWireframeShareCanonicalSchedule() {
        val date = LocalDate.of(2026, 7, 10)
        val latestSchedule = AtomicReference<EditorScheduleState>()
        composeRule.setContent {
            var editorVisible by remember { mutableStateOf(true) }
            var schedule by remember {
                mutableStateOf(
                    EditorScheduleState.fromPreview(
                        EditorSchedulePreview(
                            date = date,
                            start = LocalTime.of(9, 0),
                            end = LocalTime.of(10, 0),
                        ),
                    ),
                )
            }
            fun updateSchedule(updated: EditorScheduleState) {
                latestSchedule.set(updated)
                schedule = updated
            }
            KgsCalendarTheme(themeMode = AppThemeMode.KgsBlue, darkTheme = false) {
                Column(Modifier.fillMaxSize()) {
                    if (editorVisible) {
                        EventScheduleEditor(
                            schedule = schedule,
                            onScheduleChange = ::updateSchedule,
                        )
                        Button(
                            onClick = {
                                updateSchedule(
                                    schedule.copy(
                                        startTimeText = "10:15",
                                        endTimeText = "11:15",
                                    ).recalculatePreview(),
                                )
                            },
                            modifier = Modifier.testTag("editor-test-change-times"),
                        ) {
                            Text("Change times")
                        }
                        Button(
                            onClick = { editorVisible = false },
                            modifier = Modifier.testTag("editor-test-collapse"),
                        ) {
                            Text("Collapse")
                        }
                    } else {
                        Box(Modifier.size(width = 300.dp, height = 900.dp)) {
                            schedule.lastValidPreview?.let { preview ->
                                DraftEventWireframe(
                                    draft = preview,
                                    color = Color(0xFF2563A8).toArgb(),
                                    hourHeightDp = 60f,
                                    dayWidthPx = with(composeRule.density) { 300.dp.toPx() },
                                    onDraftChanged = { updateSchedule(schedule.applyTimelineChange(it)) },
                                    onInteraction = {},
                                    onTap = { editorVisible = true },
                                    timeScroll = rememberScrollState(),
                                    gridViewportHeightPx = with(composeRule.density) { 900.dp.roundToPx() },
                                )
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("editor-test-change-times").performClick()
        composeRule.waitForIdle()
        assertEquals("10:15", latestSchedule.get().startTimeText)
        assertEquals("11:15", latestSchedule.get().endTimeText)
        composeRule.onNodeWithTag("editor-test-collapse").performClick()

        val hourPx = with(composeRule.density) { 60.dp.toPx() }
        val quarterHourPx = hourPx / 4f
        composeRule.onNodeWithTag("editor-wireframe-body").performTouchInput {
            swipe(center, center + Offset(0f, hourPx), durationMillis = 300)
        }
        composeRule.onNodeWithTag("editor-wireframe-start-handle").performTouchInput {
            swipe(center, center + Offset(0f, quarterHourPx), durationMillis = 250)
        }
        composeRule.onNodeWithTag("editor-wireframe-end-handle").performTouchInput {
            swipe(center, center + Offset(0f, quarterHourPx), durationMillis = 250)
        }
        composeRule.waitForIdle()
        lateinit var finalSchedule: EditorScheduleState
        composeRule.runOnIdle {
            finalSchedule = latestSchedule.get()
            assertTrue(
                finalSchedule.startTimeText != "10:15" || finalSchedule.endTimeText != "11:15",
            )
        }
        composeRule.onNodeWithTag("editor-wireframe-body").performClick()

        listOf("editor-start-date", "editor-end-date", "editor-start-time", "editor-end-time").forEach { tag ->
            composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        }
        val dateCount = if (finalSchedule.startDateText == finalSchedule.endDateText) 2 else 1
        composeRule.onAllNodesWithText(finalSchedule.startDateText, substring = true, useUnmergedTree = true).assertCountEquals(dateCount)
        if (dateCount == 1) {
            composeRule.onAllNodesWithText(finalSchedule.endDateText, substring = true, useUnmergedTree = true).assertCountEquals(1)
        }
        val timeCount = if (finalSchedule.startTimeText == finalSchedule.endTimeText) 2 else 1
        composeRule.onAllNodesWithText(finalSchedule.startTimeText, substring = true, useUnmergedTree = true).assertCountEquals(timeCount)
        if (timeCount == 1) {
            composeRule.onAllNodesWithText(finalSchedule.endTimeText, substring = true, useUnmergedTree = true).assertCountEquals(1)
        }
    }
}
