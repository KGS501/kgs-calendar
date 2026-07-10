package com.kgs.calendar.widget

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

internal data class TaskCardBaseSpec(
    val rowHeightDp: Float,
    val cardHeightDp: Float,
    val cornerRadiusDp: Float,
    val cardLeftDp: Float,
    val cardRightDp: Float,
    val hierarchyDepth: Int,
    val hierarchySideInsetDp: Float,
    val hierarchyIndentDp: Float,
    val hierarchyStemDp: Float,
    val hierarchyOverlapDp: Float,
    val hierarchyLineStrokeDp: Float,
    val contentPaddingStartDp: Float,
    val contentPaddingEndDp: Float,
    val contentStartDp: Float,
    val statusCenterXDp: Float,
    val statusRadiusDp: Float,
    val statusStrokeDp: Float,
    val textStartDp: Float,
    val textEndDp: Float,
    val titleTextSizeSp: Float,
    val metaTextSizeSp: Float,
    val titleBaselineOffsetDp: Float,
    val metaBaselineOffsetDp: Float,
    val chevronCenterXDp: Float,
    val chevronHalfWidthDp: Float,
    val chevronHalfHeightDp: Float,
    val chevronStrokeDp: Float,
)

internal data class TaskPriorityEffect(
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val glowSpread: Float,
    val glowAlpha: Float,
) {
    companion object {
        val None = TaskPriorityEffect(
            translationX = 0f,
            translationY = 0f,
            scale = 1f,
            glowSpread = 0f,
            glowAlpha = 0f,
        )
    }
}

internal class WidgetTaskCardRenderer {
    @Suppress("UNUSED_PARAMETER")
    fun baseSpec(
        kind: KgsWidgetKind,
        priority: Int?,
        widthDp: Float = DEFAULT_WIDTH_DP,
        depth: Int = 0,
        childCount: Int = 0,
        hasMeta: Boolean = true,
        completed: Boolean = false,
    ): TaskCardBaseSpec {
        val agendaStyle = kind == KgsWidgetKind.Agenda || kind == KgsWidgetKind.Day || kind == KgsWidgetKind.Multi
        val sideInset = if (agendaStyle) 0f else CARD_SIDE_INSET_DP.toFloat()
        val boundedDepth = depth.coerceIn(0, MAX_DEPTH)
        val preferredLeft = sideInset + boundedDepth * HIERARCHY_INDENT_DP.toFloat()
        val maxLeft = widthDp - sideInset - MIN_CARD_WIDTH_DP
        val cardLeft = min(preferredLeft, maxLeft).coerceAtLeast(sideInset)
        val cardRight = max(widthDp - sideInset, cardLeft + MIN_CARD_WIDTH_DP)
        val contentStart = cardLeft + CARD_PADDING_START_DP.toFloat()
        val arrowSpace = if (childCount > 0) CHEVRON_RESERVED_WIDTH_DP else 0f
        val compactTypography = agendaStyle
        return TaskCardBaseSpec(
            rowHeightDp = ROW_HEIGHT_DP.toFloat(),
            cardHeightDp = CARD_HEIGHT_DP.toFloat(),
            cornerRadiusDp = CORNER_RADIUS_DP.toFloat(),
            cardLeftDp = cardLeft,
            cardRightDp = cardRight,
            hierarchyDepth = boundedDepth,
            hierarchySideInsetDp = sideInset,
            hierarchyIndentDp = HIERARCHY_INDENT_DP.toFloat(),
            hierarchyStemDp = HIERARCHY_STEM_DP.toFloat(),
            hierarchyOverlapDp = HIERARCHY_OVERLAP_DP.toFloat(),
            hierarchyLineStrokeDp = HIERARCHY_LINE_STROKE_DP,
            contentPaddingStartDp = CARD_PADDING_START_DP.toFloat(),
            contentPaddingEndDp = CARD_PADDING_END_DP.toFloat(),
            contentStartDp = contentStart,
            statusCenterXDp = contentStart + STATUS_CENTER_OFFSET_DP,
            statusRadiusDp = STATUS_RADIUS_DP,
            statusStrokeDp = STATUS_STROKE_DP,
            textStartDp = contentStart + STATUS_SLOT_WIDTH_DP + TEXT_GAP_DP,
            textEndDp = cardRight - CARD_PADDING_END_DP.toFloat() - arrowSpace,
            titleTextSizeSp = if (compactTypography) 12.3f else 13f,
            metaTextSizeSp = if (compactTypography) 10.2f else 11f,
            titleBaselineOffsetDp = when {
                !hasMeta -> 4.2f
                compactTypography -> -4.3f
                else -> -3.6f
            },
            metaBaselineOffsetDp = if (compactTypography) 10.8f else 13f,
            chevronCenterXDp = cardRight - CARD_PADDING_END_DP.toFloat() - CHEVRON_CENTER_INSET_DP,
            chevronHalfWidthDp = CHEVRON_HALF_WIDTH_DP,
            chevronHalfHeightDp = CHEVRON_HALF_HEIGHT_DP,
            chevronStrokeDp = CHEVRON_STROKE_DP,
        )
    }

    fun effect(
        priority: Int?,
        frame: Int,
        frameCount: Int,
        bitmapScale: Float,
    ): TaskPriorityEffect {
        val intensity = priorityIntensity(priority)
        if (intensity <= 0f) return TaskPriorityEffect.None
        val safeFrameCount = frameCount.coerceAtLeast(1)
        val cycleFraction = ((frame.coerceIn(0, safeFrameCount - 1) + 0.5f) / safeFrameCount).coerceIn(0f, 1f)
        val halfFraction = if (cycleFraction < 0.5f) cycleFraction * 2f else (cycleFraction - 0.5f) * 2f
        val eased = standardEasing(halfFraction)
        val phase = if (cycleFraction < 0.5f) eased else 1f - eased
        val translationX = if (priority == 1) {
            val shakePhase = (((frame + 0.5f) * 42f) % 210f) / 210f
            cos(shakePhase.toDouble() * PI * 6.0).toFloat() * 1.05f / bitmapScale.coerceAtLeast(0.01f)
        } else {
            0f
        }
        return TaskPriorityEffect(
            translationX = translationX,
            translationY = (phase - 0.5f) * -2f * intensity,
            scale = 1f + intensity * 0.018f * phase,
            glowSpread = 8f * intensity * (0.45f + phase),
            glowAlpha = 0.18f * intensity * (0.45f + 0.55f * phase),
        )
    }

    fun priorityIntensity(priority: Int?): Float {
        val value = priority?.coerceIn(1, 9) ?: 9
        return ((9 - value) / 8f).coerceIn(0f, 1f)
    }

    companion object {
        const val MAX_DEPTH = 5
        const val ROW_HEIGHT_DP = 56
        const val CARD_HEIGHT_DP = 46
        const val CORNER_RADIUS_DP = 14
        const val CARD_SIDE_INSET_DP = 12
        const val CARD_PADDING_START_DP = 8
        const val CARD_PADDING_END_DP = 8
        const val STATUS_RADIUS_DP = 6.9f
        const val STATUS_STROKE_DP = 1.45f
        const val HIERARCHY_INDENT_DP = 18
        const val HIERARCHY_STEM_DP = 10
        const val HIERARCHY_OVERLAP_DP = 4
        const val HIERARCHY_LINE_STROKE_DP = 1.65f

        private const val DEFAULT_WIDTH_DP = 360f
        const val MIN_CARD_WIDTH_DP = 48f
        private const val STATUS_CENTER_OFFSET_DP = 15f
        private const val STATUS_SLOT_WIDTH_DP = 30f
        private const val TEXT_GAP_DP = 5f
        private const val CHEVRON_RESERVED_WIDTH_DP = 36f
        private const val CHEVRON_CENTER_INSET_DP = 15f
        const val CHEVRON_HALF_WIDTH_DP = 4.25f
        const val CHEVRON_HALF_HEIGHT_DP = 2.1f
        const val CHEVRON_STROKE_DP = 1.55f
    }
}

private fun standardEasing(fraction: Float): Float =
    cubicBezierEasing(fraction, x1 = 0.2f, y1 = 0f, x2 = 0f, y2 = 1f)

private fun cubicBezierEasing(fraction: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val target = fraction.coerceIn(0f, 1f)
    var low = 0f
    var high = 1f
    repeat(14) {
        val mid = (low + high) / 2f
        if (cubicBezierCoordinate(mid, x1, x2) < target) {
            low = mid
        } else {
            high = mid
        }
    }
    return cubicBezierCoordinate((low + high) / 2f, y1, y2)
}

private fun cubicBezierCoordinate(t: Float, p1: Float, p2: Float): Float {
    val inverse = 1f - t
    return 3f * inverse * inverse * t * p1 + 3f * inverse * t * t * p2 + t * t * t
}
