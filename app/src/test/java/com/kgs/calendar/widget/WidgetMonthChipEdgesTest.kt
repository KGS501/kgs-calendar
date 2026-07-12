package com.kgs.calendar.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetMonthChipEdgesTest {
    @Test
    fun isolatedChipRoundsBothEdges() {
        assertEquals(
            WidgetMonthChipEdges(
                start = WidgetMonthChipEdge.Rounded,
                end = WidgetMonthChipEdge.Rounded,
            ),
            monthChipEdges(
                continuesFromPrevious = false,
                continuesToNext = false,
                fadesFromPrevious = false,
                fadesToNext = false,
            ),
        )
    }

    @Test
    fun continuationSquaresOnlyTheConnectedEdge() {
        assertEquals(
            WidgetMonthChipEdges(
                start = WidgetMonthChipEdge.Square,
                end = WidgetMonthChipEdge.Rounded,
            ),
            monthChipEdges(
                continuesFromPrevious = true,
                continuesToNext = false,
                fadesFromPrevious = false,
                fadesToNext = false,
            ),
        )
        assertEquals(
            WidgetMonthChipEdges(
                start = WidgetMonthChipEdge.Rounded,
                end = WidgetMonthChipEdge.Square,
            ),
            monthChipEdges(
                continuesFromPrevious = false,
                continuesToNext = true,
                fadesFromPrevious = false,
                fadesToNext = false,
            ),
        )
    }

    @Test
    fun clippingFadesOnlyTheClippedEdge() {
        assertEquals(
            WidgetMonthChipEdges(
                start = WidgetMonthChipEdge.Fade,
                end = WidgetMonthChipEdge.Rounded,
            ),
            monthChipEdges(
                continuesFromPrevious = true,
                continuesToNext = false,
                fadesFromPrevious = true,
                fadesToNext = false,
            ),
        )
        assertEquals(
            WidgetMonthChipEdges(
                start = WidgetMonthChipEdge.Rounded,
                end = WidgetMonthChipEdge.Fade,
            ),
            monthChipEdges(
                continuesFromPrevious = false,
                continuesToNext = true,
                fadesFromPrevious = false,
                fadesToNext = true,
            ),
        )
    }

    @Test
    fun fadeTakesPrecedenceOverContinuationAtEachEdge() {
        assertEquals(
            WidgetMonthChipEdges(
                start = WidgetMonthChipEdge.Fade,
                end = WidgetMonthChipEdge.Fade,
            ),
            monthChipEdges(
                continuesFromPrevious = true,
                continuesToNext = true,
                fadesFromPrevious = true,
                fadesToNext = true,
            ),
        )
    }

    @Test
    fun everyEdgePairMapsToItsNativeMask() {
        val expected = mapOf(
            WidgetMonthChipEdges(WidgetMonthChipEdge.Rounded, WidgetMonthChipEdge.Rounded) to WidgetMonthChipMask.RoundRound,
            WidgetMonthChipEdges(WidgetMonthChipEdge.Rounded, WidgetMonthChipEdge.Square) to WidgetMonthChipMask.RoundSquare,
            WidgetMonthChipEdges(WidgetMonthChipEdge.Rounded, WidgetMonthChipEdge.Fade) to WidgetMonthChipMask.RoundFade,
            WidgetMonthChipEdges(WidgetMonthChipEdge.Square, WidgetMonthChipEdge.Rounded) to WidgetMonthChipMask.SquareRound,
            WidgetMonthChipEdges(WidgetMonthChipEdge.Square, WidgetMonthChipEdge.Square) to WidgetMonthChipMask.SquareSquare,
            WidgetMonthChipEdges(WidgetMonthChipEdge.Square, WidgetMonthChipEdge.Fade) to WidgetMonthChipMask.SquareFade,
            WidgetMonthChipEdges(WidgetMonthChipEdge.Fade, WidgetMonthChipEdge.Rounded) to WidgetMonthChipMask.FadeRound,
            WidgetMonthChipEdges(WidgetMonthChipEdge.Fade, WidgetMonthChipEdge.Square) to WidgetMonthChipMask.FadeSquare,
            WidgetMonthChipEdges(WidgetMonthChipEdge.Fade, WidgetMonthChipEdge.Fade) to WidgetMonthChipMask.FadeFade,
        )

        assertEquals(
            expected,
            expected.keys.associateWith(::monthChipMask),
        )
    }
}
