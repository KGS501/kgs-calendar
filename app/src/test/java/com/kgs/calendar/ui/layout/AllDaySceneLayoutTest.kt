package com.kgs.calendar.ui.layout

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.ui.buildAllDayOverlayItems
import com.kgs.calendar.ui.calendar.toDayPage
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllDaySceneLayoutTest {
    private val july22 = LocalDate.of(2026, 7, 22)
    private val july23 = LocalDate.of(2026, 7, 23)
    private val july24 = LocalDate.of(2026, 7, 24)
    private val july25 = LocalDate.of(2026, 7, 25)
    private val july26 = LocalDate.of(2026, 7, 26)
    private val july27 = LocalDate.of(2026, 7, 27)
    private val july28 = LocalDate.of(2026, 7, 28)

    @Test
    fun renderBleedDoesNotLetJuly25ParticipateBeforeItEntersViewport() {
        val beforeEntry = buildAllDayViewportWindow(
            anchorPage = july22.toDayPage(),
            anchorOffsetPx = 0f,
            dayWidthPx = 100f,
            dayStepPx = 100f,
            viewportWidthPx = 300f,
            bufferStartPage = july22.minusDays(2).toDayPage(),
            bufferEndPage = july28.toDayPage(),
            renderBleedPx = 20f,
        )

        assertEquals(
            listOf(july22, july23, july24).map { it.toDayPage() },
            beforeEntry.layoutPages,
        )
        assertFalse(july25.toDayPage() in beforeEntry.layoutPages)
        assertTrue(july25.toDayPage() in beforeEntry.renderPages)

        val afterFirstPixel = buildAllDayViewportWindow(
            anchorPage = july22.toDayPage(),
            anchorOffsetPx = -1f,
            dayWidthPx = 100f,
            dayStepPx = 100f,
            viewportWidthPx = 300f,
            bufferStartPage = july22.minusDays(2).toDayPage(),
            bufferEndPage = july28.toDayPage(),
            renderBleedPx = 20f,
        )

        assertTrue(july25.toDayPage() in afterFirstPixel.layoutPages)
    }

    @Test
    fun trailingPagerGutterCannotActivateTheFollowingDay() {
        val insideReservedGutter = buildAllDayViewportWindow(
            anchorPage = july22.toDayPage(),
            anchorOffsetPx = -1f,
            dayWidthPx = 100f,
            dayStepPx = 104f,
            viewportWidthPx = 312f,
            bufferStartPage = july22.toDayPage(),
            bufferEndPage = july28.toDayPage(),
            renderBleedPx = 20f,
            layoutViewportWidthPx = 308f,
        )

        assertFalse(july25.toDayPage() in insideReservedGutter.layoutPages)
        assertTrue(july25.toDayPage() in insideReservedGutter.renderPages)

        val firstRealGridPixel = buildAllDayViewportWindow(
            anchorPage = july22.toDayPage(),
            anchorOffsetPx = -5f,
            dayWidthPx = 100f,
            dayStepPx = 104f,
            viewportWidthPx = 312f,
            bufferStartPage = july22.toDayPage(),
            bufferEndPage = july28.toDayPage(),
            renderBleedPx = 20f,
            layoutViewportWidthPx = 308f,
        )

        assertTrue(july25.toDayPage() in firstRealGridPixel.layoutPages)
    }

    @Test
    fun firstVisiblePixelImmediatelyRaisesTheRequiredSceneRows() {
        fun sceneAt(anchorOffsetPx: Float): AllDayScene {
            val window = buildAllDayViewportWindow(
                anchorPage = july22.toDayPage(),
                anchorOffsetPx = anchorOffsetPx,
                dayWidthPx = 100f,
                dayStepPx = 100f,
                viewportWidthPx = 300f,
                bufferStartPage = july22.minusDays(1).toDayPage(),
                bufferEndPage = july28.toDayPage(),
                renderBleedPx = 20f,
            )
            val items = buildAllDayOverlayItems(
                events = julyFixture(),
                tasks = emptyList(),
                taskColorMode = TaskColorMode.Collection,
                visibleStartPage = window.layoutStartPage,
                visibleEndPage = window.layoutEndPage,
            )
            return buildAllDayScene(
                overlayItems = items,
                visibleStartPage = window.layoutStartPage,
                visibleEndPage = window.layoutEndPage,
                priorityStartPage = window.layoutStartPage,
                priorityEndPage = window.layoutEndPage,
                maxVisibleItems = 5,
            )
        }

        assertEquals(2, sceneAt(anchorOffsetPx = 0f).metrics.collapsedRowCount)
        assertEquals(3, sceneAt(anchorOffsetPx = -1f).metrics.collapsedRowCount)
    }

    @Test
    fun changingPagerAnchorKeepsPageGeometryAtTheSamePixel() {
        val beforeAnchorChange = allDayPageLeftX(
            page = july22.toDayPage(),
            anchorPage = 100,
            anchorOffsetPx = -99f,
            dayStepPx = 200f,
        )
        val afterAnchorChange = allDayPageLeftX(
            page = july22.toDayPage(),
            anchorPage = 101,
            anchorOffsetPx = 101f,
            dayStepPx = 200f,
        )

        assertEquals(beforeAnchorChange, afterAnchorChange, 0.0001f)
    }

    @Test
    fun leftmostVisibleFragmentRemainsThePersistentPrimaryPiece() {
        val item = AllDayOverlayItem(
            id = "spanning",
            title = "Spanning event",
            color = 0,
            startPage = 10,
            endPage = 20,
            lane = 0,
        )
        val left = AllDayOverlaySegment(item, startPage = 11, endPage = 11, lane = 0)
        val longerRight = AllDayOverlaySegment(item, startPage = 13, endPage = 17, lane = 0)

        assertEquals(left, selectAllDayPrimarySegment(listOf(longerRight, left)))
    }

    @Test
    fun july25EventsCannotMoveUntitledEventWhileTheyAreOutsideLayoutWindow() {
        val beforeEntry = buildAllDayOverlayItems(
            events = julyFixture(),
            tasks = emptyList(),
            taskColorMode = TaskColorMode.Collection,
            visibleStartPage = july22.toDayPage(),
            visibleEndPage = july24.toDayPage(),
            priorityStartPage = july22.toDayPage(),
            priorityEndPage = july24.toDayPage(),
        )
        val afterEntry = buildAllDayOverlayItems(
            events = julyFixture(),
            tasks = emptyList(),
            taskColorMode = TaskColorMode.Collection,
            visibleStartPage = july22.toDayPage(),
            visibleEndPage = july25.toDayPage(),
            priorityStartPage = july22.toDayPage(),
            priorityEndPage = july24.toDayPage(),
        )

        val beforeLane = beforeEntry.single { it.title == "Untitled event" }.lane
        val afterLane = afterEntry.single { it.title == "Untitled event" }.lane
        assertFalse(beforeEntry.any { it.title == "Rabska Fjera" })
        assertTrue(afterEntry.any { it.title == "Rabska Fjera" })
        assertEquals(beforeLane, afterLane)
    }

    @Test
    fun rabskaAndUrlaubReuseOneCollapsedLaneAcrossHiddenJuly27() {
        val items = buildAllDayOverlayItems(
            events = julyFixture(),
            tasks = emptyList(),
            taskColorMode = TaskColorMode.Collection,
            visibleStartPage = july26.toDayPage(),
            visibleEndPage = july28.toDayPage(),
        )
        val scene = buildAllDayScene(
            overlayItems = items,
            visibleStartPage = july26.toDayPage(),
            visibleEndPage = july28.toDayPage(),
            priorityStartPage = july26.toDayPage(),
            priorityEndPage = july28.toDayPage(),
            maxVisibleItems = 3,
        )

        val rabska = scene.collapsedLayout.segments.single { it.item.title == "Rabska Fjera" }
        val urlaub = scene.collapsedLayout.segments.single { it.item.title == "Urlaub Familie" }
        assertEquals(july26.toDayPage(), rabska.startPage)
        assertEquals(july28.toDayPage(), urlaub.startPage)
        assertEquals(rabska.lane, urlaub.lane)
        assertEquals(3, scene.metrics.collapsedRowCount)
        assertEquals(4, scene.metrics.expandedRowCount)
        assertEquals(2, scene.hiddenPages.getValue(july27.toDayPage()).size)
    }

    @Test
    fun eachEventHasOnePrimaryPieceAndOneSharedTransitionFrame() {
        val item = AllDayOverlayItem(
            id = "urlaub",
            title = "Urlaub Familie",
            color = 0,
            startPage = july27.toDayPage(),
            endPage = LocalDate.of(2026, 8, 17).toDayPage(),
            lane = 3,
        )
        val scene = buildAllDayScene(
            overlayItems = listOf(item),
            visibleStartPage = july27.toDayPage(),
            visibleEndPage = july28.toDayPage(),
            priorityStartPage = july27.toDayPage(),
            priorityEndPage = july28.toDayPage(),
            maxVisibleItems = 3,
        )

        assertEquals(1, scene.visualPieces.count { it.item.id == item.id && it.primary })
        val frame = interpolateAllDayVisualPieceFrame(
            collapsedLeftX = 240f,
            collapsedWidthPx = 220f,
            collapsedLane = 1f,
            collapsedLeadingContinuationProgress = 0f,
            expandedLeftX = 0f,
            expandedWidthPx = 680f,
            expandedLane = 3f,
            expandedLeadingContinuationProgress = 1f,
            expansionProgress = 0.5f,
            primary = true,
            visibleWhenCollapsed = true,
        )

        assertEquals(120f, frame.leftX, 0.0001f)
        assertEquals(450f, frame.widthPx, 0.0001f)
        assertEquals(2f, frame.lane, 0.0001f)
        assertEquals(1f, frame.alpha, 0.0001f)
        assertEquals(0.5f, frame.leadingContinuationProgress, 0.0001f)
    }

    @Test
    fun fullyHiddenPieceRevealsItsContinuationFadeWithTheCard() {
        val frame = interpolateAllDayVisualPieceFrame(
            collapsedLeftX = 0f,
            collapsedWidthPx = 0f,
            collapsedLane = 1f,
            collapsedLeadingContinuationProgress = 0f,
            expandedLeftX = 0f,
            expandedWidthPx = 620f,
            expandedLane = 2f,
            expandedLeadingContinuationProgress = 1f,
            expansionProgress = 0.4f,
            primary = true,
            visibleWhenCollapsed = false,
        )

        assertEquals(0.4f, frame.alpha, 0.0001f)
        assertEquals(0.4f, frame.leadingContinuationProgress, 0.0001f)
        assertEquals(
            0.4f,
            allDayContinuationFadeVisualProgress(
                continuationProgress = frame.leadingContinuationProgress,
                transitionProgress = 1f,
            ),
            0.0001f,
        )
    }

    private fun julyFixture(): List<EventEntity> = listOf(
        allDayEvent("Schulferien", LocalDate.of(2026, 7, 20), LocalDate.of(2026, 9, 2)),
        allDayEvent("Untitled event", july23, july24),
        allDayEvent("Semesterferien", july25, LocalDate.of(2026, 10, 1)),
        allDayEvent("Rabska Fjera", july25, july28),
        allDayEvent("Urlaub Familie", july27, LocalDate.of(2026, 8, 18)),
    )

    private fun allDayEvent(
        title: String,
        start: LocalDate,
        endExclusive: LocalDate,
    ): EventEntity {
        val zone = ZoneId.systemDefault()
        return EventEntity(
            uid = title,
            collectionHref = "test",
            resourceHref = "test/$title.ics",
            title = title,
            description = null,
            location = null,
            startsAtMillis = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            endsAtMillis = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
            allDay = true,
            recurrenceRule = null,
            isRecurring = false,
            color = 0,
        )
    }
}
