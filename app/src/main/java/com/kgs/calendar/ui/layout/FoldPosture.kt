package com.kgs.calendar.ui.layout

/**
 * A folding feature of the app window reduced to plain values, so the posture mapping stays a pure
 * function. The bounds are in window pixels, as Jetpack WindowManager reports them.
 */
internal data class FoldFeatureSnapshot(
    /** `FoldingFeature.State.HALF_OPENED`; a flat fold does not change the layout. */
    val halfOpened: Boolean,
    /** `FoldingFeature.Orientation.HORIZONTAL`: the hinge runs from the left to the right edge. */
    val horizontal: Boolean,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * How a foldable is held, with the hinge in the Compose root's pixel coordinates.
 *
 * The hinge band is never used for content: a half-opened fold always separates the two halves
 * (`FoldingFeature.isSeparating`), and on devices with a physical hinge the band is occluded.
 */
internal sealed interface FoldPosture {
    /** Phones, tablets, flat or closed foldables: the regular layouts. */
    data object Normal : FoldPosture

    /** Half opened with a horizontal hinge, like a laptop: a top half and a bottom half. */
    data class Tabletop(val hingeTopPx: Float, val hingeBottomPx: Float) : FoldPosture

    /** Half opened with a vertical hinge, like a book: a left pane and a right pane. */
    data class Book(val hingeLeftPx: Float, val hingeRightPx: Float) : FoldPosture
}

/**
 * Each side of the hinge must keep at least this share of the window; a fold that only clips an edge
 * of the window (e.g. a small split-screen window) keeps the regular layout.
 */
internal const val FoldPostureMinPaneFraction = 0.2f

/**
 * The posture for the reported folding [features]. [rootLeftInWindowPx]/[rootTopInWindowPx] locate the
 * Compose root in the window, so the returned hinge bounds are root coordinates.
 *
 * Tabletop is a half-opened horizontal fold, Book a half-opened vertical fold, and everything else
 * (flat, no fold, a fold outside the window) is [FoldPosture.Normal].
 */
internal fun foldPostureOf(
    features: List<FoldFeatureSnapshot>,
    rootLeftInWindowPx: Float,
    rootTopInWindowPx: Float,
    rootWidthPx: Int,
    rootHeightPx: Int,
): FoldPosture {
    if (rootWidthPx <= 0 || rootHeightPx <= 0) return FoldPosture.Normal
    val fold = features.firstOrNull { it.halfOpened } ?: return FoldPosture.Normal
    return if (fold.horizontal) {
        val top = fold.top - rootTopInWindowPx
        val bottom = fold.bottom - rootTopInWindowPx
        if (hingeSplitsPane(top, bottom, rootHeightPx)) {
            FoldPosture.Tabletop(hingeTopPx = top, hingeBottomPx = bottom)
        } else {
            FoldPosture.Normal
        }
    } else {
        val left = fold.left - rootLeftInWindowPx
        val right = fold.right - rootLeftInWindowPx
        if (hingeSplitsPane(left, right, rootWidthPx)) {
            FoldPosture.Book(hingeLeftPx = left, hingeRightPx = right)
        } else {
            FoldPosture.Normal
        }
    }
}

private fun hingeSplitsPane(start: Float, end: Float, extentPx: Int): Boolean {
    if (end < start) return false
    val minPane = extentPx * FoldPostureMinPaneFraction
    return start >= minPane && extentPx - end >= minPane
}
