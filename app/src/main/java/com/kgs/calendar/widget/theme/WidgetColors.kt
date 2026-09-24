package com.kgs.calendar.widget.theme

import kotlin.math.roundToInt

internal fun Int.isDarkColor(): Boolean {
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF
    return (0.299 * r + 0.587 * g + 0.114 * b) < 140
}

internal fun Int.withAlpha(alpha: Float): Int =
    (((alpha.coerceIn(0f, 1f) * 255).roundToInt() and 0xFF) shl 24) or (this and 0x00FFFFFF)

internal fun Int.blendWith(other: Int, fraction: Float): Int {
    val clamped = fraction.coerceIn(0f, 1f)
    val inverse = 1f - clamped
    val a = (((this ushr 24) and 0xFF) * inverse + ((other ushr 24) and 0xFF) * clamped).roundToInt()
    val r = (((this ushr 16) and 0xFF) * inverse + ((other ushr 16) and 0xFF) * clamped).roundToInt()
    val g = (((this ushr 8) and 0xFF) * inverse + ((other ushr 8) and 0xFF) * clamped).roundToInt()
    val b = ((this and 0xFF) * inverse + (other and 0xFF) * clamped).roundToInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

internal fun Int.greyedOut(amount: Float): Int {
    val mix = amount.coerceIn(0f, 1f)
    val a = (this ushr 24) and 0xFF
    val r = (this ushr 16) and 0xFF
    val g = (this ushr 8) and 0xFF
    val b = this and 0xFF
    val gray = (r * 0.299f + g * 0.587f + b * 0.114f).roundToInt().coerceIn(0, 255)
    val nextR = (r + (gray - r) * mix).roundToInt().coerceIn(0, 255)
    val nextG = (g + (gray - g) * mix).roundToInt().coerceIn(0, 255)
    val nextB = (b + (gray - b) * mix).roundToInt().coerceIn(0, 255)
    return (a shl 24) or (nextR shl 16) or (nextG shl 8) or nextB
}
