package com.kgs.calendar.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.time.CalendarTimeSnapshot
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LandscapeCalendarShellInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactActionIsTextualPillAndDayCountIconsKeepTheirHeight() {
        setShell()

        composeRule.onNodeWithTag("landscapeTimelineCompactToggle")
            .assertHeightIsEqualTo(28.dp)
        composeRule.onNodeWithText("Hide all-day section").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Show more days")
            .assertHeightIsEqualTo(24.dp)
        composeRule.onNodeWithContentDescription("Show fewer days")
            .assertHeightIsEqualTo(24.dp)

        composeRule.onNodeWithTag("landscapeTimelineCompactToggle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Show all-day section").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Show more days")
            .assertHeightIsEqualTo(24.dp)
        composeRule.onNodeWithContentDescription("Show fewer days")
            .assertHeightIsEqualTo(24.dp)
    }

    @Test
    fun compactActionUsesLightUnoutlinedSurfaceInLightTheme() {
        setShell()

        val image = composeRule.onNodeWithTag("landscapeTimelineCompactToggle").captureToImage()
        val pixels = image.toPixelMap()
        val sampled = pixels[(pixels.width / 20).coerceAtLeast(2), pixels.height / 2]

        assertColorNear(Color.White, sampled)
    }

    @Test
    fun compactActionMatchesDayCountRailInDarkTheme() {
        setShell(darkTheme = true)

        val pillImage = composeRule.onNodeWithTag("landscapeTimelineCompactToggle").captureToImage()
        val pillPixels = pillImage.toPixelMap()
        val pillColor = pillPixels[(pillPixels.width / 20).coerceAtLeast(2), pillPixels.height / 2]
        val railImage = composeRule.onNodeWithTag("multiDayCountRail").captureToImage()
        val railPixels = railImage.toPixelMap()
        val railColor = railPixels[railPixels.width / 2, railPixels.height / 2]

        assertColorNear(railColor, pillColor)
    }

    @Test
    fun tasksIndicatorIsSubtleSixDpDot() {
        setShell(hasOpenTask = true)

        composeRule.onNodeWithTag("tasks-open-indicator", useUnmergedTree = true)
            .assertWidthIsEqualTo(6.dp)
            .assertHeightIsEqualTo(6.dp)
    }

    @Test
    fun calendarWeekLabelIsNotPaintedOverByTheToolbar() {
        setShell(showCalendarWeeks = true)

        val image = composeRule
            .onNodeWithTag("timeline-calendar-week-2026-08-17", useUnmergedTree = true)
            .captureToImage()
            .toPixelMap()
        val background = image[image.width - 1, 0]
        val inspectedRows = (10f * composeRule.density.density).toInt().coerceAtMost(image.height)
        var inkPixels = 0
        for (y in 0 until inspectedRows) {
            for (x in 0 until image.width) {
                if (!colorsAreNear(background, image[x, y], tolerance = 18)) inkPixels++
            }
        }

        assertTrue(
            "The toolbar painted over the top of the calendar-week label (inkPixels=$inkPixels)",
            inkPixels > 6,
        )
    }

    @Test
    fun narrowPortraitWeekHeaderUsesTheNumberPill() {
        setShell(
            showCalendarWeeks = true,
            orientation = Configuration.ORIENTATION_PORTRAIT,
            screenWidthDp = 360,
            screenHeightDp = 800,
            multiDayCount = 5,
        )

        val weekTag = "timeline-calendar-week-2026-08-17"
        composeRule.onNode(
            hasText("34") and hasAnyAncestor(hasTestTag(weekTag)),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText("Week 34") and hasAnyAncestor(hasTestTag(weekTag)),
            useUnmergedTree = true,
        ).assertDoesNotExist()
    }

    @Test
    fun rotationUsesItsOwnZoomAndKeepsTheVisibleTimeAnchored() {
        val orientation = mutableIntStateOf(Configuration.ORIENTATION_PORTRAIT)
        setShell(
            orientationState = orientation,
            portraitHourHeightDp = 72f,
            landscapeHourHeightDp = 60f,
        )

        val portrait = timelineViewportProbe(expectedHourHeightDp = 72f)

        composeRule.runOnIdle {
            orientation.intValue = Configuration.ORIENTATION_LANDSCAPE
        }
        val landscape = timelineViewportProbe(expectedHourHeightDp = 60f)

        composeRule.runOnIdle {
            orientation.intValue = Configuration.ORIENTATION_PORTRAIT
        }
        val restoredPortrait = timelineViewportProbe(expectedHourHeightDp = 72f)

        assertEquals(portrait.topVisibleMinute, landscape.topVisibleMinute, 2f)
        assertEquals(portrait.topVisibleMinute, restoredPortrait.topVisibleMinute, 2f)
    }

    @Test
    fun configurationRecreationKeepsTheVisibleTimeAcrossBothOrientations() {
        val orientation = AtomicInteger(Configuration.ORIENTATION_PORTRAIT)
        val restorationTester = StateRestorationTester(composeRule)
        setShell(
            orientationProvider = orientation::get,
            stateRestorationTester = restorationTester,
            portraitHourHeightDp = 72f,
            landscapeHourHeightDp = 60f,
        )

        val portrait = timelineViewportProbe(expectedHourHeightDp = 72f)

        orientation.set(Configuration.ORIENTATION_LANDSCAPE)
        restorationTester.emulateSavedInstanceStateRestore()
        val landscape = timelineViewportProbe(expectedHourHeightDp = 60f)

        orientation.set(Configuration.ORIENTATION_PORTRAIT)
        restorationTester.emulateSavedInstanceStateRestore()
        val restoredPortrait = timelineViewportProbe(expectedHourHeightDp = 72f)

        assertEquals(portrait.topVisibleMinute, landscape.topVisibleMinute, 2f)
        assertEquals(portrait.topVisibleMinute, restoredPortrait.topVisibleMinute, 2f)
    }

    @Test
    fun upwardSwipeOnLandscapeDayGridDismissesOverview() {
        setShell()
        composeRule.onNodeWithTag("calendar-toolbar-month").performClick()
        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onNodeWithTag("calendar-month-overview-container")
                .fetchSemanticsNode().boundsInRoot.height > 100f
        }

        composeRule.onNodeWithTag("month-overview-grid", useUnmergedTree = true)
            .performTouchInput { swipeUp() }

        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onNodeWithTag("calendar-month-overview-container")
                .fetchSemanticsNode().boundsInRoot.height < 2f
        }
    }

    @Test
    fun verticalMonthStripScrollDoesNotDismissLandscapeOverview() {
        setShell()
        composeRule.onNodeWithTag("calendar-toolbar-month").performClick()
        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onNodeWithTag("calendar-month-overview-container")
                .fetchSemanticsNode().boundsInRoot.height > 100f
        }

        composeRule.onNodeWithTag("month-overview-strip", useUnmergedTree = true)
            .performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        val overviewHeight = composeRule.onNodeWithTag("calendar-month-overview-container")
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue("Vertical month scrolling dismissed the landscape overview", overviewHeight > 100f)
    }

    private fun setShell(
        darkTheme: Boolean = false,
        hasOpenTask: Boolean = false,
        showCalendarWeeks: Boolean = false,
        orientation: Int = Configuration.ORIENTATION_LANDSCAPE,
        screenWidthDp: Int = 900,
        screenHeightDp: Int = 400,
        multiDayCount: Int = 3,
        orientationState: MutableIntState? = null,
        orientationProvider: (() -> Int)? = null,
        stateRestorationTester: StateRestorationTester? = null,
        portraitHourHeightDp: Float = 46f,
        landscapeHourHeightDp: Float = 46f,
    ) {
        val date = LocalDate.of(2026, 8, 21)
        val content: @Composable () -> Unit = {
            val currentOrientation =
                orientationProvider?.invoke() ?: orientationState?.intValue ?: orientation
            val orientationIsDynamic = orientationProvider != null || orientationState != null
            val landscapeConfiguration = Configuration(LocalConfiguration.current).apply {
                this.orientation = currentOrientation
                this.screenWidthDp =
                    if (orientationIsDynamic && currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                        screenHeightDp
                    } else {
                        screenWidthDp
                    }
                this.screenHeightDp =
                    if (orientationIsDynamic && currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                        screenWidthDp
                    } else {
                        screenHeightDp
                    }
            }
            CompositionLocalProvider(
                LocalConfiguration provides landscapeConfiguration,
                LocalCalendarTimeSnapshot provides CalendarTimeSnapshot(date, LocalTime.NOON),
            ) {
                KgsCalendarTheme(
                    themeMode = AppThemeMode.KgsBlue,
                    darkTheme = darkTheme,
                    priorityAnimationsEnabled = false,
                ) {
                    CalendarShell(
                        state = CalendarUiState(
                            selectedDate = date,
                            selectedView = CalendarViewMode.ThreeDay,
                            multiDayCount = multiDayCount,
                            multiDaySidebarControlsEnabled = true,
                            showCalendarWeeks = showCalendarWeeks,
                            priorityAnimationsEnabled = false,
                            portraitTimelineHourHeightDp = portraitHourHeightDp,
                            landscapeTimelineHourHeightDp = landscapeHourHeightDp,
                            inboxTasks = if (hasOpenTask) listOf(openTask()) else emptyList(),
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
        if (stateRestorationTester != null) {
            stateRestorationTester.setContent(content)
        } else {
            composeRule.setContent(content)
        }
        composeRule.waitForIdle()
    }

    private fun timelineViewportProbe(expectedHourHeightDp: Float): TimelineViewportProbe {
        composeRule.waitUntil(timeoutMillis = 3_000) {
            val semantics = composeRule.onNodeWithTag("timeline-gesture-surface")
                .fetchSemanticsNode().config
            kotlin.math.abs(semantics[TimelineHourHeightDpSemanticsKey] - expectedHourHeightDp) < 0.01f
        }
        composeRule.waitForIdle()
        val semantics = composeRule.onNodeWithTag("timeline-gesture-surface")
            .fetchSemanticsNode().config
        val hourHeightDp = semantics[TimelineHourHeightDpSemanticsKey]
        val scrollPx = semantics[TimelineScrollPxSemanticsKey]
        val hourHeightPx = with(composeRule.density) { hourHeightDp.dp.toPx() }
        return TimelineViewportProbe(
            hourHeightDp = hourHeightDp,
            topVisibleMinute = scrollPx / hourHeightPx * 60f,
        )
    }

    private data class TimelineViewportProbe(
        val hourHeightDp: Float,
        val topVisibleMinute: Float,
    )

    private fun openTask() = TaskEntity(
        uid = "open-task",
        collectionHref = "local",
        resourceHref = "local://open-task",
        title = "Open task",
        notes = null,
        dueAtMillis = null,
        startAtMillis = null,
        completedAtMillis = null,
        isCompleted = false,
        priority = null,
        color = 0xFF2563A8.toInt(),
    )

    private fun assertColorNear(expected: Color, actual: Color, tolerance: Int = 8) {
        assertTrue(
            "Expected $expected but sampled $actual",
            colorsAreNear(expected, actual, tolerance),
        )
    }

    private fun colorsAreNear(expected: Color, actual: Color, tolerance: Int): Boolean {
        val expectedArgb = expected.toArgb()
        val actualArgb = actual.toArgb()
        fun channel(argb: Int, shift: Int) = (argb shr shift) and 0xFF
        return listOf(16, 8, 0).all { shift ->
            kotlin.math.abs(channel(expectedArgb, shift) - channel(actualArgb, shift)) <= tolerance
        }
    }
}
