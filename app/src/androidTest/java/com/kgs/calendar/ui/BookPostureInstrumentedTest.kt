package com.kgs.calendar.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.layout.BookPane
import com.kgs.calendar.ui.layout.ProvideFoldPosture
import com.kgs.calendar.ui.layout.rememberWindowFoldFeatures
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.time.CalendarTimeSnapshot
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

/**
 * The book posture from a fake WindowManager fold (half opened, vertical hinge): details and editors fill
 * the pane beside the hinge they are opened in, and the toolbar has no task button next to the task pane.
 */
class BookPostureInstrumentedTest {
    private val publisherRule = WindowLayoutInfoPublisherRule()
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(publisherRule).around(composeRule)

    @Test
    fun aDetailFromTheCalendarFillsTheRightPane() {
        setSheet(followFoldPosture = true, bookPane = BookPane.Right)
        val hinge = publishFold(FoldingFeature.State.HALF_OPENED)
        val rootWidth = rootBounds().width

        composeRule.waitUntil(timeoutMillis = 3_000) { abs(sheetBounds().left - hinge.right) < 1.5f }
        val surface = sheetBounds()
        assertEquals("the pane starts at the hinge's right edge", hinge.right, surface.left, 1.5f)
        assertEquals("the pane reaches the right screen edge", rootWidth, surface.right, 1.5f)
        assertEquals("the pane is full height", 0f, surface.top, 1.5f)
        assertEquals(rootBounds().height, surface.bottom, 1.5f)
        val content = contentBounds()
        assertTrue("the content is right of the hinge, was $content", content.left >= hinge.right)
    }

    @Test
    fun aTaskFromTheTaskPaneFillsTheLeftPane() {
        setSheet(followFoldPosture = true, bookPane = BookPane.Left)
        val hinge = publishFold(FoldingFeature.State.HALF_OPENED)

        composeRule.waitUntil(timeoutMillis = 3_000) {
            val surface = sheetBounds()
            abs(surface.left) < 1.5f && abs(surface.right - hinge.left) < 1.5f
        }
        val surface = sheetBounds()
        assertEquals("the pane starts at the left screen edge", 0f, surface.left, 1.5f)
        assertEquals("the pane ends at the hinge's left edge", hinge.left, surface.right, 1.5f)
        val content = contentBounds()
        assertTrue("the content is left of the hinge, was $content", content.right <= hinge.left)
    }

    @Test
    fun otherSheetsStayBottomSheetsInABook() {
        setSheet(followFoldPosture = false, bookPane = BookPane.Right)
        val hinge = publishFold(FoldingFeature.State.HALF_OPENED)
        composeRule.waitForIdle()

        val surface = sheetBounds()
        assertTrue("a regular sheet spans the hinge, was $surface", surface.left < hinge.left && surface.right > hinge.right)
        val content = contentBounds()
        assertTrue("a regular sheet opens from the bottom, was $content", content.top > rootBounds().height * 0.3f)
    }

    @Test
    fun aFlatFoldKeepsTheRegularSheet() {
        setSheet(followFoldPosture = true, bookPane = BookPane.Right)
        publishFold(FoldingFeature.State.FLAT)
        composeRule.waitForIdle()

        val content = contentBounds()
        assertTrue("a flat fold keeps the bottom sheet, was $content", content.top > rootBounds().height * 0.3f)
    }

    @Test
    fun theToolbarHidesTheTaskButtonBesideTheTaskPane() {
        setShell()
        composeRule.onNodeWithContentDescription("Tasks").assertExists()

        publishFold(FoldingFeature.State.HALF_OPENED)
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithContentDescriptionCount("Tasks") == 0
        }

        publishFold(FoldingFeature.State.FLAT)
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithContentDescriptionCount("Tasks") == 1
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithContentDescriptionCount(
        label: String,
    ): Int = onAllNodes(androidx.compose.ui.test.hasContentDescription(label)).fetchSemanticsNodes().size

    private fun rootBounds(): Rect = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

    private fun contentBounds(): Rect =
        composeRule.onNodeWithText("Sheet content").fetchSemanticsNode().boundsInRoot

    private fun sheetBounds(): Rect =
        composeRule.onNodeWithTag(KgsModalBottomSheetSurfaceTag).fetchSemanticsNode().boundsInRoot

    /** Publishes a vertical fold through the middle of the window; returns the hinge in root coordinates. */
    private fun publishFold(state: FoldingFeature.State): HingeInRoot {
        val fold = FoldingFeature(
            activity = composeRule.activity,
            state = state,
            orientation = FoldingFeature.Orientation.VERTICAL,
        )
        publisherRule.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(fold)))
        composeRule.waitForIdle()
        val rootLeftInWindow = composeRule.onRoot().fetchSemanticsNode().positionInWindow.x
        return HingeInRoot(left = fold.bounds.left - rootLeftInWindow, right = fold.bounds.right - rootLeftInWindow)
    }

    private data class HingeInRoot(val left: Float, val right: Float)

    private fun setSheet(followFoldPosture: Boolean, bookPane: BookPane) {
        composeRule.setContent {
            val features by rememberWindowFoldFeatures()
            ProvideFoldPosture(features) {
                KgsCalendarTheme(themeMode = AppThemeMode.KgsBlue, darkTheme = false, priorityAnimationsEnabled = false) {
                    KgsModalBottomSheet(
                        onDismissRequest = {},
                        followFoldPosture = followFoldPosture,
                        bookPane = bookPane,
                    ) {
                        Text("Sheet content")
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setShell() {
        val date = LocalDate.of(2026, 8, 21)
        composeRule.setContent {
            val features by rememberWindowFoldFeatures()
            ProvideFoldPosture(features) {
                CompositionLocalProvider(
                    LocalCalendarTimeSnapshot provides CalendarTimeSnapshot(date, LocalTime.NOON),
                ) {
                    KgsCalendarTheme(
                        themeMode = AppThemeMode.KgsBlue,
                        darkTheme = false,
                        priorityAnimationsEnabled = false,
                    ) {
                        CalendarShell(
                            state = CalendarUiState(
                                selectedDate = date,
                                selectedView = CalendarViewMode.ThreeDay,
                                multiDayCount = 3,
                                priorityAnimationsEnabled = false,
                            ),
                            onMenu = {},
                            onDateSelected = {},
                            onViewSelected = {},
                            onMultiDayCountChanged = {},
                            onToday = {},
                            onSearch = {},
                            onTasks = {},
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
                            overdueTasksExpanded = false,
                            onOverdueTasksExpandedChange = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
