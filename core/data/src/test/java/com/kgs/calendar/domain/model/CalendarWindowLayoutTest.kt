package com.kgs.calendar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarWindowLayoutTest {
    private val phonePortraitSizes = listOf(
        360 to 640, 360 to 800, 393 to 852, 411 to 891, 448 to 998,
        // Large, high-resolution phones with the smallest display size setting.
        512 to 1138, 540 to 1170,
    )
    private val phoneLandscapeSizes = phonePortraitSizes.map { (w, h) -> h to w } +
        // Smaller landscape heights with system bars excluded (API 34 and older).
        listOf(592 to 360, 640 to 336, 891 to 387)

    @Test
    fun phonesNeverCountAsLargeScreens() {
        phonePortraitSizes.forEach { (w, h) ->
            assertFalse("$w x $h", CalendarWindowLayout(isLandscape = false, widthDp = w, heightDp = h).isLargeScreen)
        }
        phoneLandscapeSizes.forEach { (w, h) ->
            assertFalse("$w x $h", CalendarWindowLayout(isLandscape = true, widthDp = w, heightDp = h).isLargeScreen)
        }
    }

    @Test
    fun phonesKeepTheOrientationDecisions() {
        phonePortraitSizes.forEach { (w, h) ->
            val layout = CalendarWindowLayout(isLandscape = false, widthDp = w, heightDp = h)
            assertFalse(layout.usesSideBySideMonthOverview)
            assertEquals(MultiDayCountBucket.Portrait, layout.multiDayCountBucket)
        }
        phoneLandscapeSizes.forEach { (w, h) ->
            val layout = CalendarWindowLayout(isLandscape = true, widthDp = w, heightDp = h)
            assertTrue(layout.usesSideBySideMonthOverview)
            assertEquals(MultiDayCountBucket.Landscape, layout.multiDayCountBucket)
        }
    }

    @Test
    fun tabletsAndUnfoldedFoldablesAreLargeScreens() {
        val tabletLandscape = CalendarWindowLayout(isLandscape = true, widthDp = 1280, heightDp = 800)
        val tabletPortrait = CalendarWindowLayout(isLandscape = false, widthDp = 800, heightDp = 1280)
        val foldableLandscape = CalendarWindowLayout(isLandscape = true, widthDp = 841, heightDp = 701)
        val foldablePortrait = CalendarWindowLayout(isLandscape = false, widthDp = 673, heightDp = 841)

        listOf(tabletLandscape, tabletPortrait, foldableLandscape, foldablePortrait).forEach { layout ->
            assertTrue("$layout", layout.isLargeScreen)
            assertFalse("$layout", layout.usesSideBySideMonthOverview)
        }
        assertEquals(MultiDayCountBucket.LargeLandscape, tabletLandscape.multiDayCountBucket)
        assertEquals(MultiDayCountBucket.LargeLandscape, foldableLandscape.multiDayCountBucket)
        assertEquals(MultiDayCountBucket.LargePortrait, tabletPortrait.multiDayCountBucket)
        assertEquals(MultiDayCountBucket.LargePortrait, foldablePortrait.multiDayCountBucket)
    }

    @Test
    fun splitScreenWindowsFollowTheirOwnSize() {
        // Half of a landscape tablet: a tall large window.
        assertEquals(
            MultiDayCountBucket.LargePortrait,
            CalendarWindowLayout(isLandscape = false, widthDp = 636, heightDp = 800).multiDayCountBucket,
        )
        // Half of an upright phone: a small, low window keeps the phone behaviour.
        val phoneHalf = CalendarWindowLayout(isLandscape = true, widthDp = 411, heightDp = 400)
        assertFalse(phoneHalf.isLargeScreen)
        assertEquals(MultiDayCountBucket.Landscape, phoneHalf.multiDayCountBucket)
        assertTrue(phoneHalf.usesSideBySideMonthOverview)
    }

    @Test
    fun lowWindowsKeepThePhoneLayouts() {
        // A landscape tablet split top and bottom, and a low freeform window.
        listOf(1280 to 396, 900 to 520).forEach { (w, h) ->
            val layout = CalendarWindowLayout(isLandscape = true, widthDp = w, heightDp = h)
            assertFalse("$w x $h", layout.isLargeScreen)
            assertTrue("$w x $h", layout.usesSideBySideMonthOverview)
            assertEquals(MultiDayCountBucket.Landscape, layout.multiDayCountBucket)
        }
    }

    @Test
    fun thresholdIsTheInclusiveSmallestWidth() {
        assertTrue(CalendarWindowLayout(isLandscape = false, widthDp = 600, heightDp = 960).isLargeScreen)
        assertTrue(CalendarWindowLayout(isLandscape = true, widthDp = 960, heightDp = 600).isLargeScreen)
        assertFalse(CalendarWindowLayout(isLandscape = false, widthDp = 599, heightDp = 960).isLargeScreen)
        assertFalse(CalendarWindowLayout(isLandscape = true, widthDp = 960, heightDp = 599).isLargeScreen)
    }

    @Test
    fun multiDayCountsPickTheBucketAndClampIt() {
        val counts = MultiDayCounts(portrait = 2, landscape = 3, largePortrait = 4, largeLandscape = 99)
        assertEquals(2, counts.countFor(MultiDayCountBucket.Portrait))
        assertEquals(3, counts.countFor(MultiDayCountBucket.Landscape))
        assertEquals(4, counts.countFor(MultiDayCountBucket.LargePortrait))
        assertEquals(MAX_MULTI_DAY_COUNT, counts.countFor(MultiDayCountBucket.LargeLandscape))
    }

    @Test
    fun largeScreensDefaultToMoreDaysThanPhones() {
        val defaults = MultiDayCounts()
        assertEquals(DEFAULT_MULTI_DAY_COUNT, defaults.countFor(MultiDayCountBucket.Portrait))
        assertEquals(DEFAULT_MULTI_DAY_COUNT, defaults.countFor(MultiDayCountBucket.Landscape))
        assertEquals(4, defaults.countFor(MultiDayCountBucket.LargePortrait))
        assertEquals(5, defaults.countFor(MultiDayCountBucket.LargeLandscape))
    }
}
