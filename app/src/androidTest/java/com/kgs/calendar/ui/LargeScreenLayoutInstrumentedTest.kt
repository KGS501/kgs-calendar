package com.kgs.calendar.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.ui.layout.SettingsContentMaxWidth
import com.kgs.calendar.ui.layout.SheetMaxWidth
import com.kgs.calendar.ui.layout.centeredMaxWidth
import com.kgs.calendar.ui.layout.largeScreenMaxWidth
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Width caps for tablets and unfolded foldables, and that phones keep the full width in both orientations. */
class LargeScreenLayoutInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomSheetIsCentredAndCappedOnALandscapeTablet() {
        setSheet(widthDp = 1280, heightDp = 800)

        assertPlacedInWindow(KgsModalBottomSheetSurfaceTag, width = SheetMaxWidth, left = (1280.dp - SheetMaxWidth) / 2)
    }

    @Test
    fun bottomSheetIsCentredAndCappedOnAnUnfoldedFoldable() {
        setSheet(widthDp = 841, heightDp = 701)

        assertPlacedInWindow(KgsModalBottomSheetSurfaceTag, width = SheetMaxWidth, left = (841.dp - SheetMaxWidth) / 2)
    }

    @Test
    fun bottomSheetKeepsTheFullWidthOnAnUprightPhone() {
        setSheet(widthDp = 411, heightDp = 914)

        assertPlacedInWindow(KgsModalBottomSheetSurfaceTag, width = 411.dp, left = 0.dp)
    }

    @Test
    fun bottomSheetKeepsTheFullWidthOnARotatedPhone() {
        // A rotated phone is wider than SheetMaxWidth but is not a large screen.
        setSheet(widthDp = 914, heightDp = 411)

        assertPlacedInWindow(KgsModalBottomSheetSurfaceTag, width = 914.dp, left = 0.dp)
    }

    @Test
    fun settingsContentIsCentredAndCappedOnALandscapeTablet() {
        setCappedContent(widthDp = 1280, heightDp = 800)

        assertPlacedInWindow("content", width = SettingsContentMaxWidth, left = (1280.dp - SettingsContentMaxWidth) / 2)
    }

    @Test
    fun settingsContentKeepsTheFullWidthOnARotatedPhone() {
        setCappedContent(widthDp = 914, heightDp = 411)

        assertPlacedInWindow("content", width = 914.dp, left = 0.dp)
    }

    @Test
    fun settingsContentFillsAnUprightPhone() {
        setCappedContent(widthDp = 360, heightDp = 640)

        assertPlacedInWindow("content", width = 360.dp, left = 0.dp)
    }

    /** The window box may be larger than the test device, so positions are measured from its left edge. */
    private fun assertPlacedInWindow(tag: String, width: Dp, left: Dp) {
        composeRule.onNodeWithTag(tag).assertWidthIsEqualTo(width)
        // Unclipped positions: the window box starts left of the device edge when it is wider than the device.
        val windowLeftPx = composeRule.onNodeWithTag(WindowTag).fetchSemanticsNode().positionInRoot.x
        val nodeLeftPx = composeRule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.x
        val leftInWindow = with(composeRule.density) { (nodeLeftPx - windowLeftPx).toDp() }
        assertEquals("left edge of $tag", left.value, leftInWindow.value, 0.5f)
    }

    private fun setCappedContent(widthDp: Int, heightDp: Int) {
        composeRule.setContent {
            Window(widthDp, heightDp) {
                Box(Modifier.centeredMaxWidth(largeScreenMaxWidth(SettingsContentMaxWidth)).testTag("content"))
            }
        }
        composeRule.waitForIdle()
    }

    private fun setSheet(widthDp: Int, heightDp: Int) {
        composeRule.setContent {
            Window(widthDp, heightDp) {
                KgsCalendarTheme(themeMode = AppThemeMode.KgsBlue, darkTheme = false, priorityAnimationsEnabled = false) {
                    KgsModalBottomSheet(onDismissRequest = {}) {
                        Text("Sheet content")
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** A window of the given size: the layout bounds and the configuration the size class is read from. */
    @Composable
    private fun Window(widthDp: Int, heightDp: Int, content: @Composable () -> Unit) {
        val configuration = Configuration(LocalConfiguration.current).apply {
            orientation = if (widthDp > heightDp) {
                Configuration.ORIENTATION_LANDSCAPE
            } else {
                Configuration.ORIENTATION_PORTRAIT
            }
            screenWidthDp = widthDp
            screenHeightDp = heightDp
            smallestScreenWidthDp = minOf(widthDp, heightDp)
        }
        CompositionLocalProvider(LocalConfiguration provides configuration) {
            Box(Modifier.requiredSize(widthDp.dp, heightDp.dp).testTag(WindowTag)) {
                content()
            }
        }
    }

    private companion object {
        const val WindowTag = "test-window"
    }
}
