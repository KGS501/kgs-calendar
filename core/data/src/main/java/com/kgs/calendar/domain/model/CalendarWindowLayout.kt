package com.kgs.calendar.domain.model

/**
 * A window whose shorter side is at least this many dp is a large screen: Android's `sw600dp` tablet
 * boundary. No phone reaches it at any display size, in either orientation.
 */
const val LARGE_SCREEN_MIN_SMALLEST_WIDTH_DP = 600

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
 * their shorter side stays below [LARGE_SCREEN_MIN_SMALLEST_WIDTH_DP] even with the smallest display size
 * setting, so on phones every decision below equals the old orientation-only one. Tablets, unfolded
 * foldables and big split-screen or freeform windows are large screens; a window that is low in either
 * direction (e.g. a tablet split top and bottom) keeps the phone layouts, which are built for that.
 */
data class CalendarWindowLayout(
    val isLandscape: Boolean,
    val widthDp: Int,
    val heightDp: Int,
) {
    val isLargeScreen: Boolean
        get() = minOf(widthDp, heightDp) >= LARGE_SCREEN_MIN_SMALLEST_WIDTH_DP

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
