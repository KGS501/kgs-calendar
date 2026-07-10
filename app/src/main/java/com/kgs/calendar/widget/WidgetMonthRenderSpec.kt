package com.kgs.calendar.widget

import android.util.SizeF
import com.kgs.calendar.R

private const val WIDGET_MONTH_MAX_TEXT_LANES = 10
private const val WIDGET_MONTH_VERTICAL_CHROME_DP = 67
private const val WIDGET_MONTH_TEXT_FIXED_CHROME_DP = 15
private const val WIDGET_MONTH_TEXT_ROW_STRIDE_DP = 15
private const val WIDGET_MONTH_MIN_TEXT_CELL_HEIGHT_DP = 46
private const val WIDGET_MONTH_MINI_FIXED_HEIGHT_DP = 19
private const val WIDGET_MONTH_MINI_DOT_ROW_HEIGHT_DP = 9
internal const val WIDGET_MONTH_DOTS_PER_ROW = 5
private const val WIDGET_MONTH_MAX_DOTS = 10
private const val WIDGET_MONTH_DOT_SLOT_WIDTH_DP = 10f
private const val WIDGET_MONTH_CELL_HORIZONTAL_MARGIN_DP = 2f

internal enum class WidgetSizeBucket(
    val remoteSize: SizeF,
    val layoutRes: Int,
    val textItemCapacity: Int,
    val miniDotCapacity: Int,
    val rootPaddingDp: Int,
    val titleTextSp: Float,
    val weekdayTextSp: Float,
    val dayTextSp: Float,
    val chipTextSp: Float,
) {
    Tiny(SizeF(170f, 220f), R.layout.widget_month_cell_mini, 0, 3, 5, 13f, 7.5f, 9f, 0f),
    Mini(SizeF(220f, 285f), R.layout.widget_month_cell_mini, 0, 6, 6, 14f, 8f, 10f, 0f),
    Compact(
        SizeF(260f, 350f),
        R.layout.widget_month_cell_dynamic,
        WIDGET_MONTH_MAX_TEXT_LANES,
        0,
        6,
        15f,
        8.5f,
        10f,
        9f,
    ),
    Standard(
        SizeF(300f, 390f),
        R.layout.widget_month_cell_dynamic,
        WIDGET_MONTH_MAX_TEXT_LANES,
        0,
        6,
        15f,
        8.5f,
        10f,
        9f,
    ),
    Comfortable(
        SizeF(320f, 470f),
        R.layout.widget_month_cell_dynamic,
        WIDGET_MONTH_MAX_TEXT_LANES,
        0,
        6,
        15f,
        8.5f,
        10f,
        9f,
    ),
    Expanded(
        SizeF(360f, 560f),
        R.layout.widget_month_cell_dynamic,
        WIDGET_MONTH_MAX_TEXT_LANES,
        0,
        6,
        15f,
        8.5f,
        10f,
        9f,
    ),
    Max(
        SizeF(360f, 680f),
        R.layout.widget_month_cell_dynamic,
        WIDGET_MONTH_MAX_TEXT_LANES,
        0,
        6,
        15f,
        8.5f,
        10f,
        9f,
    );

    val usesDotCells: Boolean
        get() = textItemCapacity == 0

    fun visibleItemCount(totalCount: Int): Int =
        minOf(totalCount.coerceAtLeast(0), if (usesDotCells) miniDotCapacity else textItemCapacity)

    fun overflowCount(totalCount: Int): Int =
        (totalCount.coerceAtLeast(0) - visibleItemCount(totalCount)).coerceAtLeast(0)

    companion object {
        fun from(size: WidgetSize): WidgetSizeBucket = from(size, rowCount = 5)

        fun from(size: WidgetSize, rowCount: Int): WidgetSizeBucket {
            val rows = rowCount.coerceIn(5, 6)
            val availableHeight = (size.heightDp - WIDGET_MONTH_VERTICAL_CHROME_DP).coerceAtLeast(0)
            val weekCellHeight = availableHeight / rows
            val dayWidth = size.widthDp / 7
            return when {
                dayWidth < 30 || weekCellHeight < 29 -> Tiny
                dayWidth < 35 || weekCellHeight < 42 -> Mini
                weekCellHeight < 58 -> Compact
                weekCellHeight < 74 -> Standard
                weekCellHeight < 92 -> Comfortable
                weekCellHeight < 118 -> Expanded
                else -> Max
            }
        }
    }
}

internal data class WidgetMonthRenderSpec(
    val bucket: WidgetSizeBucket,
    val weekCellHeightDp: Int,
    val cellContentWidthDp: Float = WIDGET_MONTH_DOT_SLOT_WIDTH_DP * WIDGET_MONTH_DOTS_PER_ROW,
    val weekContentWidthDp: Float = cellContentWidthDp * 7f,
) {
    val layoutRes: Int get() = if (usesDotCells) R.layout.widget_month_cell_mini else bucket.layoutRes
    val baseTextItemCapacity: Int get() = bucket.textItemCapacity
    val rootPaddingDp: Int get() = bucket.rootPaddingDp
    val titleTextSp: Float get() = bucket.titleTextSp
    val weekdayTextSp: Float get() = bucket.weekdayTextSp
    val dayTextSp: Float get() = bucket.dayTextSp
    val chipTextSp: Float get() = bucket.chipTextSp
    val totalWidthDp: Float get() = weekContentWidthDp + rootPaddingDp * 2f
    val usesDotCells: Boolean
        get() = bucket.usesDotCells ||
            weekCellHeightDp < WIDGET_MONTH_MIN_TEXT_CELL_HEIGHT_DP ||
            textRowsThatFit() <= 0

    fun textItemCapacityFor(rowCells: List<WidgetMonthCellContent>): Int {
        if (usesDotCells) return 0
        val candidateCapacity = minOf(baseTextItemCapacity, textRowsThatFit())
        if (candidateCapacity <= 0) return 0
        return candidateCapacity
    }

    fun miniDotCapacityFor(totalItemCount: Int): Int {
        if (!usesDotCells || totalItemCount <= 0) return 0
        val rowsThatFit = miniDotRowsThatFit()
        return minOf(WIDGET_MONTH_MAX_DOTS, rowsThatFit * miniDotsPerRow(), totalItemCount)
    }

    fun miniDotRowCountsFor(totalItemCount: Int): Pair<Int, Int> {
        val visibleCount = miniDotCapacityFor(totalItemCount)
        if (visibleCount <= 0) return 0 to 0
        val dotsPerRow = miniDotsPerRow()
        if (dotsPerRow <= 0 || miniDotRowsThatFit() <= 0) return 0 to 0
        if (miniDotRowsThatFit() == 1 || visibleCount <= dotsPerRow) {
            return visibleCount to 0
        }
        val secondRow = (visibleCount / 2).coerceAtMost(dotsPerRow)
        val firstRow = (visibleCount - secondRow).coerceAtMost(dotsPerRow)
        return firstRow to secondRow
    }

    fun miniDotsPerRow(): Int {
        val safeWidth = cellContentWidthDp.coerceAtLeast(0f)
        return (safeWidth / WIDGET_MONTH_DOT_SLOT_WIDTH_DP).toInt().coerceIn(0, WIDGET_MONTH_DOTS_PER_ROW)
    }

    fun spanChipWidthDp(span: Int): Float =
        (weekContentWidthDp / 7f * span.coerceIn(1, 7)).coerceAtLeast(1f)

    fun showMiniOverflow(hiddenItemCount: Int): Boolean = hiddenItemCount > 0

    fun showTextOverflow(textItemCapacity: Int): Boolean = textItemCapacity > 0

    private fun textRowsThatFit(): Int =
        ((weekCellHeightDp - WIDGET_MONTH_TEXT_FIXED_CHROME_DP) / WIDGET_MONTH_TEXT_ROW_STRIDE_DP)
            .coerceIn(0, WIDGET_MONTH_MAX_TEXT_LANES + 1)

    private fun miniDotRowsThatFit(): Int =
        ((weekCellHeightDp - WIDGET_MONTH_MINI_FIXED_HEIGHT_DP) / WIDGET_MONTH_MINI_DOT_ROW_HEIGHT_DP)
            .coerceIn(0, 2)

    companion object {
        fun from(size: WidgetSize, rowCount: Int): WidgetMonthRenderSpec {
            val bucket = WidgetSizeBucket.from(size, rowCount)
            val weekContentWidth = weekContentWidthDp(size, bucket)
            return WidgetMonthRenderSpec(
                bucket = bucket,
                weekCellHeightDp = weekCellHeightDp(size, rowCount),
                cellContentWidthDp =
                    (weekContentWidth / 7f - WIDGET_MONTH_CELL_HORIZONTAL_MARGIN_DP).coerceAtLeast(0f),
                weekContentWidthDp = weekContentWidth,
            )
        }

        fun weekCellHeightDp(size: WidgetSize, rowCount: Int): Int {
            val rows = rowCount.coerceIn(5, 6)
            return (size.heightDp - WIDGET_MONTH_VERTICAL_CHROME_DP).coerceAtLeast(0) / rows
        }

        fun cellContentWidthDp(size: WidgetSize, bucket: WidgetSizeBucket): Float {
            val gridWidth = weekContentWidthDp(size, bucket)
            return (gridWidth / 7f - WIDGET_MONTH_CELL_HORIZONTAL_MARGIN_DP).coerceAtLeast(0f)
        }

        fun weekContentWidthDp(size: WidgetSize, bucket: WidgetSizeBucket): Float {
            val gridWidth = (size.widthDp - bucket.rootPaddingDp * 2).coerceAtLeast(0)
            return gridWidth.toFloat().coerceAtLeast(0f)
        }
    }
}
