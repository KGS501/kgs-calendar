package com.kgs.calendar.platform

import kotlin.random.Random

/** A random (version 4) UUID string, as `UUID.randomUUID().toString()`. */
fun randomUuidString(): String {
    val bytes = Random.Default.nextBytes(16)
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
    return bytes.toUuidString()
}

/** As java's `UUID.nameUUIDFromBytes`: a version 3 (MD5) UUID; stored ids depend on it matching. */
fun nameUuidFromBytes(name: ByteArray): String {
    val hash = md5(name)
    hash[6] = ((hash[6].toInt() and 0x0F) or 0x30).toByte()
    hash[8] = ((hash[8].toInt() and 0x3F) or 0x80).toByte()
    return hash.toUuidString()
}

private fun ByteArray.toUuidString(): String {
    val hex = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
}

/** MD5 (RFC 1321). Only for name-based UUIDs and cache keys, not for security. */
fun md5(input: ByteArray): ByteArray {
    val s = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )
    val k = IntArray(64) { i -> (kotlin.math.abs(kotlin.math.sin((i + 1).toDouble())) * 4294967296.0).toLong().toInt() }
    val bitLength = input.size.toLong() * 8
    val paddedLength = ((input.size + 8) / 64 + 1) * 64
    val message = input.copyOf(paddedLength)
    message[input.size] = 0x80.toByte()
    for (i in 0 until 8) message[paddedLength - 8 + i] = (bitLength ushr (8 * i)).toByte()
    var a0 = 0x67452301
    var b0 = 0xefcdab89.toInt()
    var c0 = 0x98badcfe.toInt()
    var d0 = 0x10325476
    for (chunk in 0 until paddedLength / 64) {
        val m = IntArray(16) { i ->
            val o = chunk * 64 + i * 4
            (message[o].toInt() and 0xFF) or ((message[o + 1].toInt() and 0xFF) shl 8) or
                ((message[o + 2].toInt() and 0xFF) shl 16) or ((message[o + 3].toInt() and 0xFF) shl 24)
        }
        var a = a0; var b = b0; var c = c0; var d = d0
        for (i in 0 until 64) {
            var f: Int
            val g: Int
            when (i / 16) {
                0 -> { f = (b and c) or (b.inv() and d); g = i }
                1 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                2 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
            }
            f += a + k[i] + m[g]
            a = d; d = c; c = b
            b += (f shl s[i]) or (f ushr (32 - s[i]))
        }
        a0 += a; b0 += b; c0 += c; d0 += d
    }
    val out = ByteArray(16)
    listOf(a0, b0, c0, d0).forEachIndexed { index, v -> for (i in 0 until 4) out[index * 4 + i] = (v ushr (8 * i)).toByte() }
    return out
}

/** As android.graphics.Color.rgb. */
fun argbOf(red: Int, green: Int, blue: Int, alpha: Int = 255): Int =
    (alpha shl 24) or (red shl 16) or (green shl 8) or blue

/**
 * As android.graphics.Color.parseColor: `#RRGGBB`, `#AARRGGBB` or a colour name; throws
 * IllegalArgumentException otherwise.
 */
fun parseAndroidColor(colorString: String): Int {
    if (colorString.startsWith("#")) {
        val hex = colorString.substring(1)
        val value = hex.toLongOrNull(16) ?: throw IllegalArgumentException("Unknown color")
        return when (hex.length) {
            6 -> (value or 0xFF000000L).toInt()
            8 -> value.toInt()
            else -> throw IllegalArgumentException("Unknown color")
        }
    }
    return NAMED_COLORS[colorString.lowercase()] ?: throw IllegalArgumentException("Unknown color")
}

private val NAMED_COLORS = mapOf(
    "black" to 0xFF000000.toInt(), "darkgray" to 0xFF444444.toInt(), "gray" to 0xFF888888.toInt(),
    "lightgray" to 0xFFCCCCCC.toInt(), "white" to 0xFFFFFFFF.toInt(), "red" to 0xFFFF0000.toInt(),
    "green" to 0xFF00FF00.toInt(), "blue" to 0xFF0000FF.toInt(), "yellow" to 0xFFFFFF00.toInt(),
    "cyan" to 0xFF00FFFF.toInt(), "magenta" to 0xFFFF00FF.toInt(), "aqua" to 0xFF00FFFF.toInt(),
    "fuchsia" to 0xFFFF00FF.toInt(), "darkgrey" to 0xFF444444.toInt(), "grey" to 0xFF888888.toInt(),
    "lightgrey" to 0xFFCCCCCC.toInt(), "lime" to 0xFF00FF00.toInt(), "maroon" to 0xFF800000.toInt(),
    "navy" to 0xFF000080.toInt(), "olive" to 0xFF808000.toInt(), "purple" to 0xFF800080.toInt(),
    "silver" to 0xFFC0C0C0.toInt(), "teal" to 0xFF008080.toInt(),
)
