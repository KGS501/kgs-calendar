package com.kgs.calendar.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.layout.ProvideFoldPosture
import com.kgs.calendar.ui.layout.rememberWindowFoldFeatures
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.time.CalendarTimeSnapshot
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

/**
 * The tabletop posture from a fake WindowManager fold: the month overview above the hinge, the selected
 * view's header at the hinge, and the detail sheet as a panel in the top half.
 */
class TabletopPostureInstrumentedTest {
    private val publisherRule = WindowLayoutInfoPublisherRule()
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(publisherRule).around(composeRule)

    @Test
    fun tabletopPutsTheMonthOverviewAboveTheHingeAndTheViewBelowIt() {
        var selected: LocalDate? = null
        setShell(CalendarViewMode.ThreeDay, onDateSelected = { selected = it })
        val hinge = publishFold(FoldingFeature.State.HALF_OPENED)

        composeRule.waitUntil(timeoutMillis = 3_000) { overviewBounds().height > 100f }
        composeRule.waitForIdle()

        val overview = overviewBounds()
        val view = composeRule.onNodeWithTag(CalendarViewContainerTag).fetchSemanticsNode().boundsInRoot
        val timeline = composeRule.onNodeWithTag("timeline-gesture-surface").fetchSemanticsNode().boundsInRoot
        assertEquals("overview ends at the hinge", hinge.top, overview.bottom, 1.5f)
        assertEquals("the view (and its date header) starts at the hinge", hinge.bottom, view.top, 1.5f)
        assertTrue("the timeline grid is below the hinge", timeline.top >= hinge.bottom - 1f)

        // The overview still navigates the view below it.
        composeRule.onNodeWithTag("month-overview-day-2026-08-25", useUnmergedTree = true).performClick()
        composeRule.runOnIdle { assertEquals(LocalDate.of(2026, 8, 25), selected) }
    }

    @Test
    fun agendaStartsRightBelowTheHinge() {
        setShell(CalendarViewMode.Agenda)
        val hinge = publishFold(FoldingFeature.State.HALF_OPENED)

        composeRule.waitUntil(timeoutMillis = 3_000) { overviewBounds().height > 100f }
        composeRule.waitForIdle()

        val view = composeRule.onNodeWithTag(CalendarViewContainerTag).fetchSemanticsNode().boundsInRoot
        assertEquals(hinge.bottom, view.top, 1.5f)
    }

    @Test
    fun aFlatFoldKeepsTheRegularLayout() {
        setShell(CalendarViewMode.ThreeDay)
        publishFold(FoldingFeature.State.FLAT)
        composeRule.waitForIdle()

        assertTrue("the month drop-down stays closed", overviewBounds().height < 1f)
    }

    @Test
    fun theDetailSheetOpensAsAPanelAboveTheHinge() {
        setSheet(followFoldPosture = true)
        val hinge = publishFold(FoldingFeature.State.HALF_OPENED)

        composeRule.waitUntil(timeoutMillis = 3_000) {
            val surface = sheetBounds()
            surface.top >= -1f && kotlin.math.abs(surface.bottom - hinge.top) < 1.5f
        }
        val surface = sheetBounds()
        assertEquals("the panel starts at the top", 0f, surface.top, 1.5f)
        assertEquals("the panel ends at the hinge", hinge.top, surface.bottom, 1.5f)
        composeRule.onNodeWithTag(TabletopTopPanelHandleTag, useUnmergedTree = true).assertExists()
        val content = contentBounds()
        assertTrue("the panel's content is above the hinge, was $content", content.bottom <= hinge.top)
    }

    @Test
    fun otherSheetsStayBottomSheetsInTabletop() {
        setSheet(followFoldPosture = false)
        val hinge = publishFold(FoldingFeature.State.HALF_OPENED)
        composeRule.waitForIdle()

        // The surface's own bounds ignore the sheet offset; its content shows where the sheet really is.
        val content = contentBounds()
        assertTrue("a regular sheet opens from the bottom, below the hinge, was $content", content.top > hinge.bottom)
        composeRule.onNodeWithTag(TabletopTopPanelHandleTag, useUnmergedTree = true).assertDoesNotExist()
    }

    private fun overviewBounds(): Rect =
        composeRule.onNodeWithTag("calendar-month-overview-container").fetchSemanticsNode().boundsInRoot

    private fun contentBounds(): Rect =
        composeRule.onNodeWithText("Sheet content").fetchSemanticsNode().boundsInRoot

    private fun sheetBounds(): Rect =
        composeRule.onNodeWithTag(KgsModalBottomSheetSurfaceTag).fetchSemanticsNode().boundsInRoot

    /** Publishes a horizontal fold through the middle of the window; returns the hinge in root coordinates. */
    private fun publishFold(state: FoldingFeature.State): HingeInRoot {
        val fold = FoldingFeature(
            activity = composeRule.activity,
            state = state,
            orientation = FoldingFeature.Orientation.HORIZONTAL,
        )
        publisherRule.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(fold)))
        composeRule.waitForIdle()
        val rootTopInWindow = composeRule.onRoot().fetchSemanticsNode().positionInWindow.y
        return HingeInRoot(top = fold.bounds.top - rootTopInWindow, bottom = fold.bounds.bottom - rootTopInWindow)
    }

    private data class HingeInRoot(val top: Float, val bottom: Float)

    private fun setSheet(followFoldPosture: Boolean) {
        composeRule.setContent {
            val features by rememberWindowFoldFeatures()
            ProvideFoldPosture(features) {
                KgsCalendarTheme(themeMode = AppThemeMode.KgsBlue, darkTheme = false, priorityAnimationsEnabled = false) {
                    KgsModalBottomSheet(onDismissRequest = {}, followFoldPosture = followFoldPosture) {
                        Text("Sheet content")
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setShell(view: CalendarViewMode, onDateSelected: (LocalDate) -> Unit = {}) {
        val date = LocalDate.of(2026, 8, 21)
        composeRule.setContent {
            val features by rememberWindowFoldFeatures()
            var selectedDate by androidx.compose.runtime.remember { mutableStateOf(date) }
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
                                selectedDate = selectedDate,
                                selectedView = view,
                                multiDayCount = 3,
                                priorityAnimationsEnabled = false,
                            ),
                            onMenu = {},
                            onDateSelected = {
                                selectedDate = it
                                onDateSelected(it)
                            },
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
