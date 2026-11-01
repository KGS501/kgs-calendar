package com.kgs.calendar.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaWindowPolicyTest {
    @Test
    fun initialWindowCoversAReasonableMultiYearHorizon() {
        val anchor = LocalDate.of(2026, 8, 23)
        val range = AgendaWindowPolicy.around(anchor)

        assertEquals(LocalDate.of(2025, 8, 1), range.startDate)
        assertEquals(LocalDate.of(2028, 9, 1), range.endExclusiveDate)
        assertTrue(range.contains(LocalDate.of(2026, 1, 1)))
        assertTrue(range.contains(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun nearbyNavigationKeepsTheLoadedWindowAndFarNavigationRecentersIt() {
        val range = AgendaWindowPolicy.around(LocalDate.of(2026, 8, 23))

        assertSame(range, AgendaWindowPolicy.recenterIfNeeded(range, LocalDate.of(2026, 1, 1)))
        val recentered = AgendaWindowPolicy.recenterIfNeeded(range, LocalDate.of(2031, 4, 7))
        assertTrue(recentered.contains(LocalDate.of(2031, 4, 7)))
        assertTrue(!recentered.contains(LocalDate.of(2026, 8, 23)))
    }

    @Test
    fun edgeExtensionsPreserveTheOppositeBoundary() {
        val range = AgendaWindowPolicy.around(LocalDate.of(2026, 8, 23))

        val earlier = AgendaWindowPolicy.extendEarlier(range)
        val later = AgendaWindowPolicy.extendLater(range)
        assertEquals(range.endExclusiveDate, earlier.endExclusiveDate)
        assertEquals(range.startDate.minusMonths(12), earlier.startDate)
        assertEquals(range.startDate, later.startDate)
        assertEquals(range.endExclusiveDate.plusMonths(12), later.endExclusiveDate)
    }
}
