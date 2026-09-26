package com.kgs.calendar.domain.model

/** Smallest window width, in dp, that counts as a large screen (Material "medium" width class). */
const val LARGE_SCREEN_MIN_WIDTH_DP = 600

/** Smallest window height, in dp, that counts as a large screen (Material "medium" height class). */
const val LARGE_SCREEN_MIN_HEIGHT_DP = 480

/** Days in Multiple days on an upright large screen (tablet or unfolded foldable) until the user changes it. */
const val DEFAULT_LARGE_PORTRAIT_MULTI_DAY_COUNT = 4

/** Days in Multiple days on a rotated large screen until the user changes it. */
const val DEFAULT_LARGE_LANDSCAPE_MULTI_DAY_COUNT = 5

/** Which stored Multiple days count applies to a window. */
enum class MultiDayCountBucket {
    Portrait,
    Landscape,
    LargePortrait,
    LargeLandscape,
}

/** The four stored Multiple days counts. */
data class MultiDayCounts(
    val portrait: Int = DEFAULT_MULTI_DAY_COUNT,
    val landscape: Int = DEFAULT_MULTI_DAY_COUNT,
    val largePortrait: Int = DEFAULT_LARGE_PORTRAIT_MULTI_DAY_COUNT,
    val largeLandscape: Int = DEFAULT_LARGE_LANDSCAPE_MULTI_DAY_COUNT,
) {
    fun countFor(bucket: MultiDayCountBucket): Int = when (bucket) {
        MultiDayCountBucket.Portrait -> portrait
        MultiDayCountBucket.Landscape -> landscape
        MultiDayCountBucket.LargePortrait -> largePortrait
        MultiDayCountBucket.LargeLandscape -> largeLandscape
    }.coerceMultiDayCount()
}

/**
 * The size of the app window, which drives the layout decisions that used to depend on orientation alone.
 *
 * [isLandscape] is the window's orientation as Android reports it. Phones never qualify as a large screen:
 * upright they are narrower than [LARGE_SCREEN_MIN_WIDTH_DP], rotated they are lower than
 * [LARGE_SCREEN_MIN_HEIGHT_DP]. So on phones every decision below equals the old orientation-only one.
 * Tablets, unfolded foldables and big split-screen or freeform windows are large screens.
 */
data class CalendarWindowLayout(
    val isLandscape: Boolean,
    val widthDp: Int,
    val heightDp: Int,
) {
    val isLargeScreen: Boolean
        get() = widthDp >= LARGE_SCREEN_MIN_WIDTH_DP && heightDp >= LARGE_SCREEN_MIN_HEIGHT_DP

    /**
     * The month drop-down fills the whole height beside a vertical month strip only when the window is low
     * (a rotated phone). On a large screen that would hide the timeline behind a mostly empty panel, so the
     * drop-down stacks above the timeline as it does upright.
     */
    val usesSideBySideMonthOverview: Boolean
        get() = isLandscape && !isLargeScreen

    val multiDayCountBucket: MultiDayCountBucket
        get() = when {
            isLargeScreen && isLandscape -> MultiDayCountBucket.LargeLandscape
            isLargeScreen -> MultiDayCountBucket.LargePortrait
            isLandscape -> MultiDayCountBucket.Landscape
            else -> MultiDayCountBucket.Portrait
        }

    companion object {
        /** A typical upright phone; the state before the first window measurement arrives. */
        val PhonePortrait = CalendarWindowLayout(isLandscape = false, widthDp = 411, heightDp = 891)

        /** A typical rotated phone. */
        val PhoneLandscape = CalendarWindowLayout(isLandscape = true, widthDp = 891, heightDp = 411)
    }
}
