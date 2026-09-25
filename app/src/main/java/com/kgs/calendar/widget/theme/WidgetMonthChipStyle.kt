package com.kgs.calendar.widget.theme

import com.kgs.calendar.R
import com.kgs.calendar.widget.WIDGET_MONTH_SPAN_FADE_TEXT_INSET_DP
import com.kgs.calendar.widget.WIDGET_MONTH_SPAN_FADE_WIDTH_DP
import com.kgs.calendar.widget.model.WidgetMonthChipMask
import com.kgs.calendar.widget.model.WidgetMonthItem
import com.kgs.calendar.widget.model.monthChipEdges
import com.kgs.calendar.widget.model.monthChipMask

internal data class WidgetMonthChipStyle(
    val backgroundRes: Int,
    val fillColor: Int,
    val textColor: Int,
) {
    fun cellBackgroundRes(item: WidgetMonthItem): Int =
        if (item.fadesFromPrevious || item.fadesToNext) {
            backgroundRes(item)
        } else {
            backgroundRes
        }

    fun backgroundRes(item: WidgetMonthItem): Int {
        val suffix = when {
            item.fadesFromPrevious && item.fadesToNext -> "middle"
            item.fadesFromPrevious -> "fade_start"
            item.fadesToNext -> "fade_end"
            item.continuesFromPrevious && item.continuesToNext -> "middle"
            item.continuesFromPrevious -> "end"
            item.continuesToNext -> "start"
            else -> "rounded"
        }
        return when (backgroundRes) {
            R.drawable.widget_month_chip_red -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_red_start
                "middle" -> R.drawable.widget_month_chip_red_middle
                "end" -> R.drawable.widget_month_chip_red_end
                "fade_start" -> R.drawable.widget_month_chip_red_fade_start
                "fade_end" -> R.drawable.widget_month_chip_red_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_orange -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_orange_start
                "middle" -> R.drawable.widget_month_chip_orange_middle
                "end" -> R.drawable.widget_month_chip_orange_end
                "fade_start" -> R.drawable.widget_month_chip_orange_fade_start
                "fade_end" -> R.drawable.widget_month_chip_orange_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_yellow -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_yellow_start
                "middle" -> R.drawable.widget_month_chip_yellow_middle
                "end" -> R.drawable.widget_month_chip_yellow_end
                "fade_start" -> R.drawable.widget_month_chip_yellow_fade_start
                "fade_end" -> R.drawable.widget_month_chip_yellow_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_green -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_green_start
                "middle" -> R.drawable.widget_month_chip_green_middle
                "end" -> R.drawable.widget_month_chip_green_end
                "fade_start" -> R.drawable.widget_month_chip_green_fade_start
                "fade_end" -> R.drawable.widget_month_chip_green_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_teal -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_teal_start
                "middle" -> R.drawable.widget_month_chip_teal_middle
                "end" -> R.drawable.widget_month_chip_teal_end
                "fade_start" -> R.drawable.widget_month_chip_teal_fade_start
                "fade_end" -> R.drawable.widget_month_chip_teal_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_blue -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_blue_start
                "middle" -> R.drawable.widget_month_chip_blue_middle
                "end" -> R.drawable.widget_month_chip_blue_end
                "fade_start" -> R.drawable.widget_month_chip_blue_fade_start
                "fade_end" -> R.drawable.widget_month_chip_blue_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_purple -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_purple_start
                "middle" -> R.drawable.widget_month_chip_purple_middle
                "end" -> R.drawable.widget_month_chip_purple_end
                "fade_start" -> R.drawable.widget_month_chip_purple_fade_start
                "fade_end" -> R.drawable.widget_month_chip_purple_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_pink -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_pink_start
                "middle" -> R.drawable.widget_month_chip_pink_middle
                "end" -> R.drawable.widget_month_chip_pink_end
                "fade_start" -> R.drawable.widget_month_chip_pink_fade_start
                "fade_end" -> R.drawable.widget_month_chip_pink_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_neutral_light -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_neutral_light_start
                "middle" -> R.drawable.widget_month_chip_neutral_light_middle
                "end" -> R.drawable.widget_month_chip_neutral_light_end
                "fade_start" -> R.drawable.widget_month_chip_neutral_light_fade_start
                "fade_end" -> R.drawable.widget_month_chip_neutral_light_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_neutral_dark -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_neutral_dark_start
                "middle" -> R.drawable.widget_month_chip_neutral_dark_middle
                "end" -> R.drawable.widget_month_chip_neutral_dark_end
                "fade_start" -> R.drawable.widget_month_chip_neutral_dark_fade_start
                "fade_end" -> R.drawable.widget_month_chip_neutral_dark_fade_end
                else -> backgroundRes
            }
            else -> backgroundRes
        }
    }
}

internal fun Int.monthChipStyle(): WidgetMonthChipStyle {
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val saturation = if (max == 0) 0f else (max - min).toFloat() / max.toFloat()
    val value = max / 255f
    val fillColor = this or 0xFF000000.toInt()
    val textColor = if (fillColor.isDarkColor()) 0xFFFFFFFF.toInt() else 0xFF1C1A18.toInt()
    if (saturation < 0.14f) {
        return if (value >= 0.58f) {
            WidgetMonthChipStyle(R.drawable.widget_month_chip_neutral_light, fillColor, textColor)
        } else {
            WidgetMonthChipStyle(R.drawable.widget_month_chip_neutral_dark, fillColor, textColor)
        }
    }

    val hue = rgbHue(r, g, b, max, min)
    return when {
        hue < 15f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_red, fillColor, textColor)
        hue < 45f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_orange, fillColor, textColor)
        hue < 75f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_yellow, fillColor, textColor)
        hue < 155f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_green, fillColor, textColor)
        hue < 185f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_teal, fillColor, textColor)
        hue < 235f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_blue, fillColor, textColor)
        hue < 285f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_purple, fillColor, textColor)
        hue < 345f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_pink, fillColor, textColor)
        else -> WidgetMonthChipStyle(R.drawable.widget_month_chip_red, fillColor, textColor)
    }
}

private fun rgbHue(r: Int, g: Int, b: Int, max: Int, min: Int): Float {
    if (max == min) return 0f
    val delta = (max - min).toFloat()
    val hue = when (max) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return if (hue < 0f) hue + 360f else hue
}

internal fun monthSpanChipLayout(span: Int): Int = when (span.coerceIn(1, 7)) {
    1 -> R.layout.widget_month_span_chip_1
    2 -> R.layout.widget_month_span_chip_2
    3 -> R.layout.widget_month_span_chip_3
    4 -> R.layout.widget_month_span_chip_4
    5 -> R.layout.widget_month_span_chip_5
    6 -> R.layout.widget_month_span_chip_6
    else -> R.layout.widget_month_span_chip_7
}

internal fun monthBottomFadeSpanLayout(span: Int): Int = when (span.coerceIn(1, 7)) {
    1 -> R.layout.widget_month_bottom_fade_span_1
    2 -> R.layout.widget_month_bottom_fade_span_2
    3 -> R.layout.widget_month_bottom_fade_span_3
    4 -> R.layout.widget_month_bottom_fade_span_4
    5 -> R.layout.widget_month_bottom_fade_span_5
    6 -> R.layout.widget_month_bottom_fade_span_6
    else -> R.layout.widget_month_bottom_fade_span_7
}

internal fun WidgetMonthItem.monthSpanTextStartPaddingDp(): Float =
    if (fadesFromPrevious) {
        WIDGET_MONTH_SPAN_FADE_WIDTH_DP + WIDGET_MONTH_SPAN_FADE_TEXT_INSET_DP
    } else {
        4f
    }

internal fun WidgetMonthItem.monthChipMaskRes(): Int {
    val edges = monthChipEdges(
        continuesFromPrevious = continuesFromPrevious,
        continuesToNext = continuesToNext,
        fadesFromPrevious = fadesFromPrevious,
        fadesToNext = fadesToNext,
    )
    return when (monthChipMask(edges)) {
        WidgetMonthChipMask.RoundRound -> R.drawable.widget_month_chip_mask_round_round
        WidgetMonthChipMask.RoundSquare -> R.drawable.widget_month_chip_mask_round_square
        WidgetMonthChipMask.RoundFade -> R.drawable.widget_month_chip_mask_round_fade
        WidgetMonthChipMask.SquareRound -> R.drawable.widget_month_chip_mask_square_round
        WidgetMonthChipMask.SquareSquare -> R.drawable.widget_month_chip_mask_square_square
        WidgetMonthChipMask.SquareFade -> R.drawable.widget_month_chip_mask_square_fade
        WidgetMonthChipMask.FadeRound -> R.drawable.widget_month_chip_mask_fade_round
        WidgetMonthChipMask.FadeSquare -> R.drawable.widget_month_chip_mask_fade_square
        WidgetMonthChipMask.FadeFade -> R.drawable.widget_month_chip_mask_fade_fade
    }
}

internal fun cellBottomFadeRes(cellBackgroundRes: Int): Int =
    when (cellBackgroundRes) {
        R.drawable.widget_month_cell_compact -> R.drawable.widget_month_cell_bottom_fade_compact
        R.drawable.widget_month_cell_compact_dark -> R.drawable.widget_month_cell_bottom_fade_compact_dark
        R.drawable.widget_item_background_dark -> R.drawable.widget_month_cell_bottom_fade_dark
        R.drawable.widget_month_day_selected -> R.drawable.widget_month_cell_bottom_fade_selected
        R.drawable.widget_month_day_selected_dark -> R.drawable.widget_month_cell_bottom_fade_selected_dark
        R.drawable.widget_month_day_selected_fresh -> R.drawable.widget_month_cell_bottom_fade_selected_fresh
        R.drawable.widget_month_day_selected_warm -> R.drawable.widget_month_cell_bottom_fade_selected_warm
        else -> R.drawable.widget_month_cell_bottom_fade
    }
