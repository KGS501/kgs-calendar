package com.kgs.calendar.data.ical

import java.util.Base64
import java.util.Locale

/**
 * Calendar-level properties of an iCalendar feed: its name (RFC 7986 NAME or the common
 * X-WR-CALNAME) and image (RFC 7986 IMAGE, as a URI or inline BASE64 data).
 */
internal data class IcsCalendarProperties(
    val name: String?,
    val imageUrl: String?,
    val imageBytes: ByteArray?,
) {
    companion object {
        fun parse(rawIcs: String): IcsCalendarProperties {
            var name: String? = null
            var fallbackName: String? = null
            var imageUrl: String? = null
            var imageBytes: ByteArray? = null
            var depth = 0
            for (line in unfold(rawIcs)) {
                val upper = line.uppercase(Locale.US)
                when {
                    upper.startsWith("BEGIN:") -> depth++
                    upper.startsWith("END:") -> depth--
                    // Only properties of the VCALENDAR itself, not of its events or time zones.
                    depth == 1 -> {
                        val colon = line.indexOf(':').takeIf { it > 0 } ?: continue
                        val nameAndParams = line.substring(0, colon).split(';')
                        val value = line.substring(colon + 1).trim()
                        val params = nameAndParams.drop(1).associate {
                            it.substringBefore('=').uppercase(Locale.US) to it.substringAfter('=', "").trim('"')
                        }
                        when (nameAndParams.first().uppercase(Locale.US)) {
                            "NAME" -> name = name ?: value.unescapedText()
                            "X-WR-CALNAME" -> fallbackName = fallbackName ?: value.unescapedText()
                            "IMAGE" -> if (imageUrl == null && imageBytes == null) {
                                if (params["ENCODING"].equals("BASE64", ignoreCase = true) || params["VALUE"].equals("BINARY", ignoreCase = true)) {
                                    imageBytes = runCatching { Base64.getMimeDecoder().decode(value) }.getOrNull()
                                } else {
                                    imageUrl = value.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
                                }
                            }
                        }
                    }
                }
            }
            return IcsCalendarProperties(
                name = (name ?: fallbackName)?.takeIf { it.isNotBlank() },
                imageUrl = imageUrl,
                imageBytes = imageBytes?.takeIf { it.isNotEmpty() },
            )
        }

        private fun unfold(rawIcs: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            for (line in rawIcs.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
                if ((line.startsWith(" ") || line.startsWith("\t")) && current.isNotEmpty()) {
                    current.append(line, 1, line.length)
                } else {
                    if (current.isNotEmpty()) result += current.toString()
                    current.setLength(0)
                    current.append(line)
                }
            }
            if (current.isNotEmpty()) result += current.toString()
            return result
        }

        private fun String.unescapedText(): String =
            replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim()
    }

    override fun equals(other: Any?): Boolean =
        other is IcsCalendarProperties && name == other.name && imageUrl == other.imageUrl &&
            imageBytes.contentEqualsNullable(other.imageBytes)

    override fun hashCode(): Int = (name?.hashCode() ?: 0) * 31 + (imageUrl?.hashCode() ?: 0)
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    if (this == null || other == null) this === other else contentEquals(other)
