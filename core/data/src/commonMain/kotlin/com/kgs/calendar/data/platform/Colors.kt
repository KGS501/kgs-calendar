package com.kgs.calendar.data.platform

/** An opaque ARGB color from its red, green and blue parts (0..255). */
internal fun argbColor(red: Int, green: Int, blue: Int): Int =
    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

/**
 * Parses `#RRGGBB`, `#AARRGGBB` or a basic color name to an ARGB int, exactly as Android's
 * `Color.parseColor` does, so stored calendar colors stay the same on every platform.
 */
internal fun parseColorOrNull(value: String): Int? {
    if (value.startsWith("#")) {
        val digits = value.substring(1)
        if (digits.length != 6 && digits.length != 8) return null
        val parsed = digits.toLongOrNull(16) ?: return null
        return (if (digits.length == 6) parsed or 0xFF000000L else parsed).toInt()
    }
    return NamedColors[value.lowercase()]
}

private val NamedColors: Map<String, Int> = mapOf(
    "black" to 0xFF000000.toInt(),
    "darkgray" to 0xFF444444.toInt(),
    "gray" to 0xFF888888.toInt(),
    "lightgray" to 0xFFCCCCCC.toInt(),
    "white" to 0xFFFFFFFF.toInt(),
    "red" to 0xFFFF0000.toInt(),
    "green" to 0xFF00FF00.toInt(),
    "blue" to 0xFF0000FF.toInt(),
    "yellow" to 0xFFFFFF00.toInt(),
    "cyan" to 0xFF00FFFF.toInt(),
    "magenta" to 0xFFFF00FF.toInt(),
    "aqua" to 0xFF00FFFF.toInt(),
    "fuchsia" to 0xFFFF00FF.toInt(),
    "darkgrey" to 0xFF444444.toInt(),
    "grey" to 0xFF888888.toInt(),
    "lightgrey" to 0xFFCCCCCC.toInt(),
    "lime" to 0xFF00FF00.toInt(),
    "maroon" to 0xFF800000.toInt(),
    "navy" to 0xFF000080.toInt(),
    "olive" to 0xFF808000.toInt(),
    "purple" to 0xFF800080.toInt(),
    "silver" to 0xFFC0C0C0.toInt(),
    "teal" to 0xFF008080.toInt(),
)
