package com.kgs.calendar.ui.timeline

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.R
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.CalendarRange
import com.kgs.calendar.ui.CalendarShell
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.LocalAppLocale
import com.kgs.calendar.ui.calendar.toDayPage
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.time.CalendarTimeSnapshot
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AllDayContinuationFadeInstrumentedTest {
    @get:Rule val rule = createComposeRule()
    private val july26 = LocalDate.of(2026, 7, 26)
    private val events = listOf(
        event("Semesterferien", LocalDate.of(2026, 7, 25), LocalDate.of(2026, 10, 1)),
        event("Schulferien", LocalDate.of(2026, 7, 20), LocalDate.of(2026, 9, 2)),
        event("Rabska Fjera", LocalDate.of(2026, 7, 25), LocalDate.of(2026, 7, 28)),
        event("Urlaub Familie", LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 18)),
        event("Weiterer Termin", LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 28)),
    )

    @Test fun julyExampleFadesSquareContinuationsBehindTheAbstraction() {
        show(dark = false)
        assertContinuationPixels()
        capture("july-light")
    }

    @Test fun julyExampleUsesTheDarkBackgroundForItsTransparentContinuation() {
        show(dark = true)
        assertContinuationPixels()
        capture("july-dark")
    }

    @Test fun julyExampleKeepsItsContinuationWhileHoldingAPartialSwipe() {
        show(dark = false)
        val width = rule.onNodeWithTag("timeline-all-day-overlay").fetchSemanticsNode().boundsInRoot.width
        val markerTag = "timeline-all-day-overflow:${july26.plusDays(1).toDayPage()}:2"
        val fullWidth = rule.onNodeWithTag(markerTag).fetchSemanticsNode().boundsInRoot.width
        rule.onNodeWithTag("timeline-gesture-surface").performTouchInput {
            down(center)
            moveBy(Offset(-width / 6f, 0f), delayMillis = 300)
        }
        rule.waitForIdle()
        val partialWidth = rule.onNodeWithTag(markerTag).fetchSemanticsNode().boundsInRoot.width
        assertEquals("An existing abstraction keeps its normal width", fullWidth, partialWidth, 1f)
        val progress = collisionProgress()
        assertTrue("The held swipe must leave the abstraction between rows", progress > 0f && progress < 1f)
        assertTitleClearOfFade(progress)
        capture("july-partial-swipe")
        rule.onNodeWithTag("timeline-gesture-surface").performTouchInput { up() }
    }

    @Test fun leadingCornerRoundsWhileTheAbstractionRetractsToTheLeft() {
        assertRetractingCorner(leading = true)
    }

    @Test fun trailingCornerRoundsWhileTheAbstractionRetractsToTheRight() {
        assertRetractingCorner(leading = false)
    }

    private fun assertRetractingCorner(leading: Boolean) {
        show(dark = false)
        if (InstrumentationRegistry.getArguments().getString("captureCornerSequence") == "true") {
            captureCornerSequence(leading)
            return
        }
        val overlay = rule.onNodeWithTag("timeline-all-day-overlay")
        val width = overlay.fetchSemanticsNode().boundsInRoot.width
        val markerTag = "timeline-all-day-overflow:${july26.plusDays(1).toDayPage()}:2"
        rule.onNodeWithTag("timeline-gesture-surface").performTouchInput {
            down(center)
            moveBy(Offset(width * 0.31f * (if (leading) -1f else 1f), 0f), delayMillis = 300)
        }
        rule.waitForIdle()
        val progress = collisionProgress()
        assertTrue("The gesture must leave a small, still visible abstraction", progress > 0f && progress < 0.25f)
        val card = rule.onNodeWithTag(tag(if (leading) "Urlaub Familie" else "Rabska Fjera"))
            .fetchSemanticsNode().boundsInRoot
        val dp = rule.density.density
        val x = if (leading) card.left + 0.5f * dp else card.right - 0.5f * dp
        val origin = overlay.fetchSemanticsNode().boundsInRoot
        val bitmap = overlay.captureToImage().asAndroidBitmap()
        fun pixel(y: Float) = bitmap.getPixel((x - origin.left).roundToInt(), (y - origin.top).roundToInt())
        assertTrue("The corner must round before the last pixel of the abstraction disappears",
            colorDistance(pixel(card.top + 0.5f * dp), pixel(card.center.y)) > 20)
        if (leading) assertTitleClearOfFade(progress)
        capture(if (leading) "july-leading-corner-morph" else "july-trailing-corner-morph")
        rule.onNodeWithTag("timeline-gesture-surface").performTouchInput { up() }
    }

    /** Review frames sampled from one continuous held gesture, using actual card width as feedback. */
    private fun captureCornerSequence(leading: Boolean) {
        val markerTag = "timeline-all-day-overflow:${july26.plusDays(1).toDayPage()}:2"
        val fullWidth = rule.onNodeWithTag(markerTag).fetchSemanticsNode().boundsInRoot.width
        fun remaining() = collisionProgress()
        val surface = rule.onNodeWithTag("timeline-gesture-surface")
        val direction = if (leading) -1f else 1f
        val side = if (leading) "leading" else "trailing"
        val samples = mutableListOf<String>()
        var fingerDown = false
        for (percent in listOf(0, 25, 50, 75, 90, 100)) {
            val target = 1f - percent / 100f
            if (percent > 0 && !fingerDown) {
                // Capture the resting frame before touching; begin the swipe
                // immediately so screenshot capture cannot trigger a long press.
                surface.performTouchInput {
                    down(center)
                    moveBy(Offset(direction * fullWidth * (1f - target), 0f), delayMillis = 160)
                }
                fingerDown = true
                rule.waitForIdle()
            }
            // Correct for touch slop and pixel rounding without releasing the finger.
            repeat(4) {
                val delta = remaining() - target
                if (abs(delta) > 0.003f) {
                    surface.performTouchInput { moveBy(Offset(direction * fullWidth * delta, 0f), delayMillis = 160) }
                    rule.waitForIdle()
                }
            }
            val actual = remaining()
            assertEquals("The captured frame must match its labelled swipe position", target, actual, 0.015f)
            val name = "july-morph-$side-${percent.toString().padStart(3, '0')}"
            capture(name, focusSection = true)
            capture("$name-full")
            samples += "{\"swipePercent\":$percent,\"remainingAbstractionFraction\":$actual,\"file\":\"$name.png\"}"
        }
        surface.performTouchInput { up() }
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(dir, "july-morph-$side.json").writeText(samples.joinToString(prefix = "[", postfix = "]"))
    }

    private val dayAbstraction = SemanticsMatcher("The single abstraction on July 27") {
        it.config.getOrNull(SemanticsProperties.TestTag)
            ?.startsWith("timeline-all-day-overflow:${july26.plusDays(1).toDayPage()}:") == true
    }

    private fun collisionProgress(): Float {
        val origin = rule.onNodeWithTag("timeline-all-day-overlay").fetchSemanticsNode().boundsInRoot.top
        val top = rule.onNode(dayAbstraction).fetchSemanticsNode().boundsInRoot.top
        return ((origin + 94f * rule.density.density - top) / (29f * rule.density.density)).coerceIn(0f, 1f)
    }

    @Test fun oneAbstractionTravelsUpAndBackWithTheGesture() {
        show(dark = false, initialDate = july26.minusDays(1))
        val surface = rule.onNodeWithTag("timeline-gesture-surface")
        val fullWidth = rule.onNode(dayAbstraction).fetchSemanticsNode().boundsInRoot.width
        val initialTop = rule.onNode(dayAbstraction).fetchSemanticsNode().boundsInRoot.top
        val overlay = rule.onNodeWithTag("timeline-all-day-overlay")
        val initialHeight = overlay.fetchSemanticsNode().boundsInRoot.height
        var fingerDown = false
        for ((direction, percents) in listOf("up" to listOf(0, 25, 50, 75, 100), "down" to listOf(75, 50, 25, 0))) {
            for (percent in percents) {
                val target = percent / 100f
                if (!fingerDown && percent > 0) {
                    surface.performTouchInput {
                        down(center)
                        moveBy(Offset(-fullWidth * target, 0f), delayMillis = 160)
                    }
                    fingerDown = true
                    rule.waitForIdle()
                }
                repeat(4) {
                    val delta = target - collisionProgress()
                    if (abs(delta) > 0.003f) {
                        surface.performTouchInput { moveBy(Offset(-fullWidth * delta, 0f), delayMillis = 160) }
                        rule.waitForIdle()
                    }
                }
                assertEquals("Exactly one abstraction must represent all hidden items", 1,
                    rule.onAllNodes(dayAbstraction).fetchSemanticsNodes().size)
                val marker = rule.onNode(dayAbstraction).fetchSemanticsNode().boundsInRoot
                assertEquals("The travelling abstraction keeps its full width", fullWidth, marker.width, 1f)
                assertEquals("Vertical movement follows the held swipe", target, collisionProgress(), 0.02f)
                if (percent == 100) assertEquals(initialTop - 29f * rule.density.density, marker.top, 2f)
                val section = overlay.fetchSemanticsNode().boundsInRoot
                assertEquals("The unused row shrinks directly with the held swipe",
                    initialHeight - 29f * rule.density.density * target, section.height, 2f)
                assertTrue("The travelling card must remain inside the shrinking section", marker.bottom <= section.bottom)
                val heldHeight = section.height
                val heldTop = marker.top
                rule.mainClock.advanceTimeBy(500)
                assertEquals("Holding must not play a timed section-height movement", heldHeight,
                    overlay.fetchSemanticsNode().boundsInRoot.height, 1f)
                assertEquals("Holding must not play a timed movement", heldTop,
                    rule.onNode(dayAbstraction).fetchSemanticsNode().boundsInRoot.top, 1f)
                val name = "july-single-abstraction-$direction-${percent.toString().padStart(3, '0')}"
                capture(name, focusSection = true)
                capture("$name-full")
            }
        }
        surface.performTouchInput { up() }
    }

    @Test fun expandingTheMovedAbstractionRestoresEveryRowAndCollapseRemovesTheEmptyRow() {
        show(dark = false)
        val overlay = rule.onNodeWithTag("timeline-all-day-overlay")
        assertEquals("Fully raised abstraction uses exactly three rows", 97f * rule.density.density,
            overlay.fetchSemanticsNode().boundsInRoot.height, 2f)
        rule.onNode(dayAbstraction).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Weiterer Termin", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("Expanded section must reserve all five rows", 155f * rule.density.density,
            overlay.fetchSemanticsNode().boundsInRoot.height, 2f)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithContentDescription(context.getString(R.string.collapse_all_day_items)).performClick()
        rule.waitForIdle()
        assertEquals("Collapse must remove the empty fourth row again", 97f * rule.density.density,
            overlay.fetchSemanticsNode().boundsInRoot.height, 2f)
        assertEquals(1, rule.onAllNodes(dayAbstraction).fetchSemanticsNodes().size)
    }

    private fun assertTitleClearOfFade(progress: Float) {
        val card = rule.onNodeWithTag(tag("Urlaub Familie")).fetchSemanticsNode().boundsInRoot
        val title = rule.onNodeWithText("Urlaub Familie", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val dp = rule.density.density
        assertEquals("The title must follow the fade back to the normal six-dp inset",
            (6f + 24f * progress) * dp, title.left - card.left, 2f)
        assertTrue("The title must start beyond the still fading pixels", title.left > card.left + 24f * progress * dp)
    }

    private fun show(dark: Boolean, initialDate: LocalDate = july26) {
        val range = CalendarRange(july26.minusDays(2), july26.plusDays(5))
        val state = CalendarUiState(initialDataLoaded = true, selectedDate = initialDate,
            selectedView = CalendarViewMode.ThreeDay, events = events, showCalendarWeeks = true,
            maxVisibleAllDayItems = 4, priorityAnimationsEnabled = false,
            visibleRange = range, loadedDataRange = range, requestedDataRange = range)
        rule.setContent {
            CompositionLocalProvider(LocalAppLocale provides Locale.GERMAN,
                LocalCalendarTimeSnapshot provides CalendarTimeSnapshot(LocalDate.of(2026, 9, 29), LocalTime.of(9, 0))) {
                KgsCalendarTheme(themeMode = AppThemeMode.KgsBlue, darkTheme = dark, priorityAnimationsEnabled = false) {
                    Box(Modifier.fillMaxSize()) {
                        CalendarShell(state = state, onMenu = {}, onDateSelected = {}, onViewSelected = {},
                            onMultiDayCountChanged = {}, onToday = {}, onSearch = {}, onTasks = {},
                            onTaskStatusChanged = { _, _ -> }, onEventMoved = { _, _, _, _, _ -> },
                            onTaskMoved = { _, _, _, _, _ -> }, onEventMovedAllDay = { _, _, _ -> },
                            onTaskMovedAllDay = { _, _, _ -> }, onSlotSelected = { _, _ -> },
                            onAllDaySlotSelected = {}, draftEvent = null, onDraftEventChanged = {},
                            onDraftInteraction = {}, onDraftTap = {}, timelineBottomInset = 0.dp,
                            onDetail = {}, overdueTasksExpanded = false, onOverdueTasksExpandedChange = {})
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun assertContinuationPixels() {
        assertTitleClearOfFade(1f)
        val overlay = rule.onNodeWithTag("timeline-all-day-overlay")
        val origin = overlay.fetchSemanticsNode().boundsInRoot
        val bitmap = overlay.captureToImage().asAndroidBitmap()
        val marker = rule.onNodeWithTag("timeline-all-day-overflow:${july26.plusDays(1).toDayPage()}:2")
            .fetchSemanticsNode().boundsInRoot
        val left = rule.onNodeWithTag(tag("Rabska Fjera")).fetchSemanticsNode().boundsInRoot
        val right = rule.onNodeWithTag(tag("Urlaub Familie")).fetchSemanticsNode().boundsInRoot
        val dp = rule.density.density
        fun pixel(x: Float, y: Float) = bitmap.getPixel((x - origin.left).roundToInt(), (y - origin.top).roundToInt())
        // A rounded artificial end has a background-coloured top pixel and a tinted centre.
        val x = left.right - 2f * dp
        assertTrue("The fading continuation must have a straight edge, without a rounded tip",
            colorDistance(pixel(x, left.top + 2f * dp), pixel(x, left.center.y)) < 8)
        val background = pixel(marker.left - 2f * dp, left.bottom + 3f * dp)
        assertTrue("The left event must continue across the spacing and underneath the abstraction",
            colorDistance(pixel(marker.left - dp, left.center.y), background) > 12)
        assertTrue("The right event must continue across the spacing and underneath the abstraction",
            colorDistance(pixel(marker.right + dp, right.center.y), background) > 12)
    }

    private fun colorDistance(a: Int, b: Int) = listOf(16, 8, 0).maxOf { shift ->
        abs((a shr shift and 255) - (b shr shift and 255))
    }

    private fun capture(name: String, focusSection: Boolean = false) {
        if (InstrumentationRegistry.getArguments().getString("captureAllDay") != "true") return
        val node = if (focusSection) rule.onNodeWithTag("timeline-all-day-overlay") else rule.onRoot()
        val bitmap = node.captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tag(title: String): String = events.single { it.title == title }.let {
        "timeline-all-day-item-event:${it.resourceHref}:${it.startsAtMillis}"
    }

    private fun event(title: String, start: LocalDate, end: LocalDate) = EventEntity(
        uid = title, collectionHref = "test", resourceHref = "test/$title.ics", title = title,
        description = null, location = null,
        startsAtMillis = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        endsAtMillis = end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        allDay = true, recurrenceRule = null, isRecurring = false, color = 0xFF0088C7.toInt(),
    )
}
