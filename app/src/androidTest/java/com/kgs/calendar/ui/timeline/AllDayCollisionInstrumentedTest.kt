package com.kgs.calendar.ui.timeline

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.ui.AllDayViewportOverlay
import com.kgs.calendar.ui.calendar.toDayPage
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AllDayCollisionInstrumentedTest {
    @get:Rule val rule = createComposeRule()
    private val october1 = LocalDate.of(2026, 10, 1)
    private val offset = mutableFloatStateOf(0f)
    private val expanded = mutableStateOf(false)
    private val events = listOf(event("1", -20, 20), event("2", -10, 10), event("3", -4, 0),
        event("4", 0, 8), event("5", 0, 0))
    private val collisionTag = "timeline-all-day-overflow:${october1.toDayPage()}:2"
    private fun tag(title: String): String = events.single { it.title == title }.let {
        "timeline-all-day-item-event:${it.resourceHref}:${it.startsAtMillis}"
    }

    @Test fun collisionCardSeparatesBothEventsAndOpensTheFullLayout() {
        show(initialDays = -1f)
        rule.onNodeWithTag(collisionTag).assertHasClickAction()
        val marker = rule.onNodeWithTag(collisionTag).fetchSemanticsNode().boundsInRoot
        val left = rule.onNodeWithTag(tag("3")).fetchSemanticsNode().boundsInRoot
        val right = rule.onNodeWithTag(tag("4")).fetchSemanticsNode().boundsInRoot
        assertTrue(left.right < marker.left)
        assertTrue(marker.right < right.left)
        assertTrue(abs(left.top - right.top) <= 1f)
        capture("collapsed")
        rule.onNodeWithTag(collisionTag).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(collisionTag).assertDoesNotExist()
        val expandedLeft = rule.onNodeWithTag(tag("3")).fetchSemanticsNode().boundsInRoot
        val expandedRight = rule.onNodeWithTag(tag("4")).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(expandedLeft.top - expandedRight.top) > 10f)
        assertTrue(expandedLeft.right > left.right)
        assertTrue(expandedRight.left < right.left)
        capture("expanded")
    }

    @Test fun enteringAndReversingTheCollisionAnimatesCardRetraction() {
        val day = show()
        capture("before-entry")
        rule.onNodeWithTag(collisionTag).assertDoesNotExist()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { offset.floatValue = -day }
        rule.mainClock.advanceTimeByFrame()
        val initialWidth = rule.onNodeWithTag(tag("3")).fetchSemanticsNode().boundsInRoot.width
        rule.mainClock.advanceTimeBy(100)
        val midWidth = rule.onNodeWithTag(tag("3")).fetchSemanticsNode().boundsInRoot.width
        capture("mid-retraction")
        rule.mainClock.advanceTimeBy(600)
        val finalWidth = rule.onNodeWithTag(tag("3")).fetchSemanticsNode().boundsInRoot.width
        assertTrue("Retraction must have intermediate geometry", initialWidth > midWidth && midWidth > finalWidth)
        rule.onNodeWithTag(collisionTag).assertHasClickAction()
        rule.runOnIdle { offset.floatValue = 0f }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag(collisionTag).assertDoesNotExist()
        assertTrue(rule.onNodeWithTag(tag("3")).fetchSemanticsNode().boundsInRoot.width > finalWidth * 2f)
    }

    @Test fun outgoingDayRetainsCollisionUntilItsLastVisiblePixel() {
        val day = show(initialDays = -1f, dark = true)
        rule.runOnIdle { offset.floatValue = -day * 1.75f }
        rule.waitForIdle()
        rule.onNodeWithTag(collisionTag).assertHasClickAction()
        capture("dark-partial-day")
        rule.runOnIdle { offset.floatValue = -day * 2f }
        rule.waitForIdle()
        rule.onNodeWithTag(collisionTag).assertDoesNotExist()
        rule.onNodeWithTag(tag("3")).assertDoesNotExist()
        assertTrue(rule.onNodeWithTag(tag("4")).fetchSemanticsNode().boundsInRoot.width > day * 2f)
    }

    private fun show(initialDays: Float = 0f, dark: Boolean = false): Float {
        val day = with(rule.density) { 100.dp.toPx() }
        offset.floatValue = day * initialDays
        rule.setContent {
            KgsCalendarTheme(themeMode = AppThemeMode.KgsBlue, darkTheme = dark, priorityAnimationsEnabled = false) {
                Box(Modifier.width(300.dp).height(180.dp).background(MaterialTheme.colorScheme.background)) {
                    AllDayViewportOverlay(events = events, tasks = emptyList(), taskColorMode = TaskColorMode.Collection,
                        anchorPage = october1.minusDays(2).toDayPage(), anchorOffsetPx = offset.floatValue,
                        dayWidthPx = day, dayStepPx = day, viewportWidthPx = day * 3,
                        topOffset = 0.dp, height = 180.dp, timedGridTopPadding = 0.dp,
                        hourHeightDp = 60f, timeScrollPx = 0, defaultEventDurationMinutes = 60,
                        draftEvent = null, onDraftTap = {}, onAllDaySlotSelected = {},
                        onEventMoved = { _, _, _, _, _ -> }, onTaskMoved = { _, _, _, _, _ -> },
                        onEventMovedAllDay = { _, _, _ -> }, onTaskMovedAllDay = { _, _, _ -> },
                        maxVisibleItems = 4, expanded = expanded.value, onExpandedChange = { expanded.value = it },
                        onTaskStatusChanged = { _, _ -> }, onDetail = {}, priorityPageCount = 3)
                }
            }
        }
        rule.waitForIdle()
        return day
    }

    /** Optional review artifacts; screenshot hardware is not part of the layout assertions. */
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureAllDay") != "true") return
        var bitmap: Bitmap? = null
        for (attempt in 0..2) {
            try {
                bitmap = rule.onNodeWithTag("timeline-all-day-overlay").captureToImage().asAndroidBitmap()
                break
            } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
                if (attempt == 2) throw error
                rule.waitForIdle()
            }
        }
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(directory, "all-day-$name.png").outputStream().use {
            requireNotNull(bitmap).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun event(title: String, start: Int, end: Int): EventEntity {
        val zone = ZoneId.systemDefault()
        return EventEntity(uid = title, collectionHref = "test", resourceHref = "test/$title.ics",
            title = title, description = null, location = null,
            startsAtMillis = october1.plusDays(start.toLong()).atStartOfDay(zone).toInstant().toEpochMilli(),
            endsAtMillis = october1.plusDays(end.toLong() + 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            allDay = true, recurrenceRule = null, isRecurring = false, color = 0xFF2088E5.toInt())
    }
}
