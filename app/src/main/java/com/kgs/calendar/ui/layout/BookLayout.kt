package com.kgs.calendar.ui.layout

/** The two panes of the book posture (half opened, vertical hinge), in physical screen order. */
internal enum class BookPane { Left, Right }

/** Where a detail or an editor was opened from. */
internal enum class SheetOrigin {
    /** The calendar, the create button, search, a widget or a notification. */
    Calendar,

    /** The task sidebar, which is the right pane in the book posture. */
    TaskPane,
}

/**
 * The pane a detail or an editor opens in: across the hinge from where it was opened, so the tapped
 * item stays visible. The calendar (left) opens into the right pane, over the task pane; the task pane
 * (right) opens into the left pane, over the calendar. An editor opened from a detail, and an editor
 * that switches between event and task, stays in the pane it already has.
 */
internal fun bookPaneFor(origin: SheetOrigin): BookPane = when (origin) {
    SheetOrigin.Calendar -> BookPane.Right
    SheetOrigin.TaskPane -> BookPane.Left
}

/** A pane's horizontal extent in root pixels. */
internal data class BookPaneBounds(val leftPx: Float, val widthPx: Float) {
    val rightPx: Float get() = leftPx + widthPx
}

/**
 * [pane]'s bounds in a root [rootWidthPx] wide. The left pane ends at the hinge's left edge and the right
 * pane starts at its right edge, so nothing is ever placed inside the hinge band.
 */
internal fun FoldPosture.Book.paneBounds(pane: BookPane, rootWidthPx: Float): BookPaneBounds {
    val width = rootWidthPx.coerceAtLeast(0f)
    return when (pane) {
        BookPane.Left -> BookPaneBounds(leftPx = 0f, widthPx = hingeLeftPx.coerceIn(0f, width))
        BookPane.Right -> {
            val left = hingeRightPx.coerceIn(0f, width)
            BookPaneBounds(leftPx = left, widthPx = width - left)
        }
    }
}

/**
 * The calendar's width while the book layout comes or goes: the full [rootWidthPx] at [progress] 0 and the
 * left pane, up to the hinge, at 1.
 */
internal fun bookCalendarWidthPx(rootWidthPx: Float, hingeLeftPx: Float, progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    val paneWidth = hingeLeftPx.coerceIn(0f, rootWidthPx)
    return rootWidthPx + (paneWidth - rootWidthPx) * p
}

/**
 * The horizontal translation of a pane sheet that is [hiddenPx] (0 = fully shown) away from its place. Like
 * the drawers, a sheet slides in from its pane's outer screen edge: the right pane's from the right, the
 * left pane's from the left.
 */
internal fun bookPaneSlideOffsetPx(pane: BookPane, hiddenPx: Float): Float = when (pane) {
    BookPane.Left -> -hiddenPx
    BookPane.Right -> hiddenPx
}

/**
 * How far a horizontal drag of [deltaPx] (positive = to the right) moves a pane sheet towards its outer edge,
 * i.e. towards hidden. Dragging the right pane's sheet to the right, or the left pane's to the left, puts
 * it away.
 */
internal fun bookPaneHideDeltaPx(pane: BookPane, deltaPx: Float): Float = when (pane) {
    BookPane.Left -> -deltaPx
    BookPane.Right -> deltaPx
}

/**
 * Where a sheet that follows the fold posture sits, so a sheet that replaces another one in the same place
 * (e.g. detail -> editor) fades in there instead of sliding in again.
 */
internal enum class FoldPanelSlot { TabletopTop, BookLeft, BookRight }

internal fun foldPanelSlot(posture: FoldPosture, bookPane: BookPane): FoldPanelSlot? = when (posture) {
    FoldPosture.Normal -> null
    is FoldPosture.Tabletop -> FoldPanelSlot.TabletopTop
    is FoldPosture.Book -> when (bookPane) {
        BookPane.Left -> FoldPanelSlot.BookLeft
        BookPane.Right -> FoldPanelSlot.BookRight
    }
}
