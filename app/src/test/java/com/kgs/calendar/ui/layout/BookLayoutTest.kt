package com.kgs.calendar.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookLayoutTest {
    // 7.6" foldable held like a book: 1768 px wide, a zero-width hinge at x = 884.
    private val book = FoldPosture.Book(hingeLeftPx = 884f, hingeRightPx = 884f)

    @Test
    fun detailsOpenAcrossTheHingeFromWhereTheyWereTapped() {
        assertEquals(BookPane.Right, bookPaneFor(SheetOrigin.Calendar))
        assertEquals(BookPane.Left, bookPaneFor(SheetOrigin.TaskPane))
    }

    @Test
    fun thePanesMeetAtTheHinge() {
        assertEquals(BookPaneBounds(leftPx = 0f, widthPx = 884f), book.paneBounds(BookPane.Left, 1768f))
        assertEquals(BookPaneBounds(leftPx = 884f, widthPx = 884f), book.paneBounds(BookPane.Right, 1768f))
    }

    @Test
    fun anOccludingHingeIsLeftEmpty() {
        val occluding = FoldPosture.Book(hingeLeftPx = 860f, hingeRightPx = 908f)

        val left = occluding.paneBounds(BookPane.Left, 1768f)
        val right = occluding.paneBounds(BookPane.Right, 1768f)

        assertEquals(860f, left.rightPx, 0.01f)
        assertEquals(908f, right.leftPx, 0.01f)
        assertEquals(1768f, right.rightPx, 0.01f)
    }

    @Test
    fun aHingeOutsideTheRootNeverGivesANegativePane() {
        val outside = FoldPosture.Book(hingeLeftPx = 2000f, hingeRightPx = 2000f)

        assertEquals(1768f, outside.paneBounds(BookPane.Left, 1768f).widthPx, 0.01f)
        assertEquals(0f, outside.paneBounds(BookPane.Right, 1768f).widthPx, 0.01f)
    }

    @Test
    fun theCalendarNarrowsToTheHingeWithTheAnimation() {
        assertEquals(1768f, bookCalendarWidthPx(1768f, 884f, progress = 0f), 0.01f)
        assertEquals(1326f, bookCalendarWidthPx(1768f, 884f, progress = 0.5f), 0.01f)
        assertEquals(884f, bookCalendarWidthPx(1768f, 884f, progress = 1f), 0.01f)
        // An emphasized easing may overshoot slightly; the calendar never passes the hinge.
        assertEquals(884f, bookCalendarWidthPx(1768f, 884f, progress = 1.1f), 0.01f)
    }

    @Test
    fun paneSheetsSlideInFromTheirOuterEdge() {
        assertEquals(300f, bookPaneSlideOffsetPx(BookPane.Right, hiddenPx = 300f), 0.01f)
        assertEquals(-300f, bookPaneSlideOffsetPx(BookPane.Left, hiddenPx = 300f), 0.01f)
    }

    @Test
    fun draggingTowardsTheOuterEdgePutsAPaneSheetAway() {
        assertEquals(40f, bookPaneHideDeltaPx(BookPane.Right, deltaPx = 40f), 0.01f)
        assertEquals(-40f, bookPaneHideDeltaPx(BookPane.Right, deltaPx = -40f), 0.01f)
        assertEquals(40f, bookPaneHideDeltaPx(BookPane.Left, deltaPx = -40f), 0.01f)
    }

    @Test
    fun eachFoldPanelHasItsOwnPlace() {
        assertNull(foldPanelSlot(FoldPosture.Normal, BookPane.Left))
        assertEquals(FoldPanelSlot.TabletopTop, foldPanelSlot(FoldPosture.Tabletop(300f, 300f), BookPane.Left))
        assertEquals(FoldPanelSlot.BookLeft, foldPanelSlot(book, BookPane.Left))
        assertEquals(FoldPanelSlot.BookRight, foldPanelSlot(book, BookPane.Right))
    }
}
