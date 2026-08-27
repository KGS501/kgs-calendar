package com.kgs.calendar.ui

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimelineRotationActivityInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TimelineRotationTestActivity>()

    @Test
    fun realActivityRotationKeepsTheVisibleTimeAcrossBothOrientations() {
        requestOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        viewportProbe(expectedHourHeightDp = 72f)
        composeRule.onNodeWithTag("timeline-gesture-surface", useUnmergedTree = true)
            .performTouchInput { swipeUp(durationMillis = 350) }
        composeRule.waitForIdle()
        val portrait = viewportProbe(expectedHourHeightDp = 72f)
        assertTrue("The test did not move away from the default 09:00 anchor", abs(portrait - 540f) > 30f)
        assertRememberedTopMinute(isLandscape = false, expected = portrait)

        requestOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        viewportProbe(expectedHourHeightDp = 60f)
        composeRule.onNodeWithTag("timeline-gesture-surface", useUnmergedTree = true)
            .performTouchInput { swipeUp(durationMillis = 350) }
        composeRule.waitForIdle()
        val landscape = viewportProbe(expectedHourHeightDp = 60f)
        assertTrue("The test did not create a distinct landscape anchor", abs(landscape - portrait) > 30f)
        assertRememberedTopMinute(isLandscape = true, expected = landscape)

        requestOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        val restoredPortrait = viewportProbe(expectedHourHeightDp = 72f)

        requestOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        val restoredLandscape = viewportProbe(expectedHourHeightDp = 60f)

        assertEquals(portrait, restoredPortrait, 2f)
        assertEquals(landscape, restoredLandscape, 2f)
    }

    private fun assertRememberedTopMinute(isLandscape: Boolean, expected: Float) {
        composeRule.activityRule.scenario.onActivity {
            assertEquals(expected, it.rememberedTopMinute(isLandscape), 2f)
        }
    }

    private fun requestOrientation(requestedOrientation: Int) {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = requestedOrientation
        }
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag("timeline-gesture-surface", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 30_000) {
            val expectedLandscape = requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            val width = composeRule.onNodeWithTag("timeline-gesture-surface", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.width
            val height = composeRule.onNodeWithTag("timeline-gesture-surface", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.height
            (width > height) == expectedLandscape
        }
    }

    private fun viewportProbe(expectedHourHeightDp: Float): Float {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag("timeline-gesture-surface", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 30_000) {
            val semantics = composeRule.onNodeWithTag("timeline-gesture-surface", useUnmergedTree = true)
                .fetchSemanticsNode().config
            abs(semantics[TimelineHourHeightDpSemanticsKey] - expectedHourHeightDp) < 0.01f
        }
        composeRule.waitForIdle()
        val semantics = composeRule.onNodeWithTag("timeline-gesture-surface", useUnmergedTree = true)
            .fetchSemanticsNode().config
        val hourHeightDp = semantics[TimelineHourHeightDpSemanticsKey]
        val scrollPx = semantics[TimelineScrollPxSemanticsKey]
        val hourHeightPx = with(composeRule.density) { hourHeightDp.dp.toPx() }
        return scrollPx / hourHeightPx * 60f
    }
}
