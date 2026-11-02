package com.kgs.calendar.ui.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenDotLayoutTest {
    // 5 px dots 7 px apart; a count takes 4 px per character.
    private fun layout(count: Int, available: Float) =
        hiddenDotLayout(count, available, dotSize = 5f, dotStep = 7f) { rest -> 4f * "+$rest".length }

    @Test fun allDotsWhenTheyFit() {
        assertEquals(3 to 0, layout(3, 19f))
        assertEquals(1 to 0, layout(1, 5f))
    }

    @Test fun theCountMakesRoomAndNeverStandsForASingleItem() {
        for (available in 5..60) for (count in 1..20) {
            val (dots, rest) = layout(count, available.toFloat())
            if (rest > 0) {
                assertTrue("count +1 for $count in $available", rest >= 2)
                assertTrue("overflows for $count in $available", dots * 7f + 4f * "+$rest".length <= available)
            }
            assertTrue(dots + rest == count || (dots == 0 && rest == 0))
        }
    }

    @Test fun dotsGiveWayToTheCount() {
        // Three dots fit in 19 px; four items show one dot and "+3".
        assertEquals(1 to 3, layout(4, 19f))
    }
}
