package com.kgs.calendar.platform

/** The text charsets calendar data names in practice; anything else reads as UTF-8 (as before). */
enum class TextCharset {
    UTF_8, ISO_8859_1, US_ASCII, WINDOWS_1252;

    fun encode(text: String): ByteArray = when (this) {
        UTF_8 -> text.encodeToByteArray()
        ISO_8859_1, US_ASCII -> ByteArray(text.length) { i -> text[i].code.let { if (it <= (if (this == US_ASCII) 0x7F else 0xFF)) it.toByte() else '?'.code.toByte() } }
        WINDOWS_1252 -> ByteArray(text.length) { i ->
            val c = text[i]
            val index = CP1252_HIGH.indexOf(c)
            when {
                index >= 0 -> (0x80 + index).toByte()
                c.code <= 0xFF -> c.code.toByte()
                else -> '?'.code.toByte()
            }
        }
    }

    fun decode(bytes: ByteArray): String = when (this) {
        UTF_8 -> bytes.decodeToString()
        ISO_8859_1 -> CharArray(bytes.size) { (bytes[it].toInt() and 0xFF).toChar() }.concatToString()
        US_ASCII -> CharArray(bytes.size) { (bytes[it].toInt() and 0xFF).let { b -> if (b <= 0x7F) b.toChar() else '�' } }.concatToString()
        WINDOWS_1252 -> CharArray(bytes.size) {
            val b = bytes[it].toInt() and 0xFF
            if (b in 0x80..0x9F) CP1252_HIGH[b - 0x80] else b.toChar()
        }.concatToString()
    }

    companion object {
        /** As `Charset.forName(name)` for the supported names; UTF-8 for null or unknown names. */
        fun forNameOrUtf8(name: String?): TextCharset = when (name?.trim()?.uppercase()?.replace('_', '-')) {
            "ISO-8859-1", "ISO8859-1", "LATIN1", "L1", "ISO-LATIN-1", "CP819" -> ISO_8859_1
            "US-ASCII", "ASCII" -> US_ASCII
            "WINDOWS-1252", "CP1252" -> WINDOWS_1252
            else -> UTF_8
        }

        private const val CP1252_HIGH =
            "€�‚ƒ„…†‡ˆ‰Š‹Œ�Ž�" +
                "�‘’“”•–—˜™š›œ�žŸ"
    }
}

/** As `URLDecoder.decode(value, charset)`. */
fun urlDecode(value: String, charset: TextCharset = TextCharset.UTF_8): String {
    val out = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            c == '+' -> { out += ' '.code.toByte(); i++ }
            c == '%' -> {
                require(i + 2 < value.length) { "Incomplete trailing escape (%) pattern" }
                val hex = value.substring(i + 1, minOf(i + 3, value.length)).takeIf { it.length == 2 }?.toIntOrNull(16)
                    ?: throw IllegalArgumentException("Illegal hex characters in escape (%) pattern")
                out += hex.toByte()
                i += 3
            }
            else -> { charset.encode(c.toString()).forEach { out += it }; i++ }
        }
    }
    return charset.decode(out.toByteArray())
}
