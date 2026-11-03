package com.kgs.calendar.navigation


/**
 * A new event prefilled from text shared to the app, e.g. an email: the subject becomes the title
 * and the text the description. Only the text extras of a share are read; streams, URIs and
 * everything else in the share are ignored.
 */
data class SharedEventDraft(
    val title: String,
    val notes: String,
    val location: String = "",
    val beginTimeMillis: Long? = null,
    val endTimeMillis: Long? = null,
    val allDay: Boolean = false,
) {
    companion object {
        const val ACTION_CREATE_SHARED_EVENT = "com.kgs.calendar.action.CREATE_SHARED_EVENT"
        const val EXTRA_TITLE = "com.kgs.calendar.extra.SHARED_TITLE"
        const val EXTRA_NOTES = "com.kgs.calendar.extra.SHARED_NOTES"

        const val MAX_TITLE_CHARS = 120
        const val MAX_NOTES_CHARS = 50_000

        /**
         * Title = the subject, or the first line of the text without one, on one line and at most
         * [MAX_TITLE_CHARS] long. Notes = the plain text with its line breaks; HTML is only
         * converted when there is no plain text (or the "plain" text is an HTML document).
         */
        fun fromShare(subject: CharSequence?, text: CharSequence?, htmlText: String?): SharedEventDraft {
            val plain = text?.toString()?.takeIf { it.isNotBlank() }
            val body = when {
                plain != null && !plain.looksLikeHtmlDocument() -> plain
                plain != null -> htmlToPlainText(plain)
                !htmlText.isNullOrBlank() -> htmlToPlainText(htmlText)
                else -> ""
            }
            val notes = limitNotes(body)
            val titleSource = subject?.toString()?.takeIf { it.isNotBlank() }
                ?: notes.lineSequence().firstOrNull { it.isNotBlank() }
                .orEmpty()
            return SharedEventDraft(title = sharedTitle(titleSource), notes = notes)
        }

        fun sharedTitle(value: String): String =
            value.replace(Whitespace, " ").trim().truncated(MAX_TITLE_CHARS, preferWordBreak = true)

        fun limitNotes(value: String): String {
            val lines = value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .joinToString("\n") { it.trimEnd() }
                .trim('\n')
            return lines.truncated(MAX_NOTES_CHARS, preferWordBreak = false)
        }

        private fun String.truncated(maxChars: Int, preferWordBreak: Boolean): String {
            if (length <= maxChars) return this
            var end = maxChars - 1
            if (preferWordBreak) {
                val space = lastIndexOf(' ', end)
                if (space >= maxChars / 2) end = space
            }
            if (end > 0 && this[end - 1].isHighSurrogate()) end--
            return substring(0, end).trimEnd() + Ellipsis
        }

        private fun String.looksLikeHtmlDocument(): Boolean {
            val start = trimStart().take(256).lowercase()
            return start.startsWith("<!doctype html") || start.startsWith("<html") ||
                contains(BodyTag)
        }

        private val Whitespace = Regex("\\s+")
        private val BodyTag = Regex("<body[\\s>]", RegexOption.IGNORE_CASE)
        private const val Ellipsis = "…"
    }
}

/**
 * A readable plain text of an HTML mail body: invisible parts (head, styles, scripts, comments)
 * are dropped, blocks and list items start new lines, paragraphs are separated by a blank line,
 * `<br>` breaks a line and entities are decoded.
 */
fun htmlToPlainText(html: String): String {
    val source = html.replace(HiddenBlocks, " ").replace(HtmlWhitespace, " ")
    val out = StringBuilder(source.length)
    var textStart = 0
    for (tag in AnyTag.findAll(source)) {
        out.append(source.substring(textStart, tag.range.first).decodeHtmlEntities())
        textStart = tag.range.last + 1
        val closing = tag.groupValues[1].isNotEmpty()
        val name = tag.groupValues[2].lowercase()
        when {
            name == "br" -> out.trimTrailingSpaces().append('\n')
            name == "p" -> out.endLineWith(blankLine = true)
            name == "li" && !closing -> out.endLineWith(blankLine = false).append("• ")
            name in BlockTags -> out.endLineWith(blankLine = false)
            (name == "td" || name == "th") && closing -> out.append(' ')
        }
    }
    out.append(source.substring(textStart).decodeHtmlEntities())
    return out.toString()
        .replace('\u00A0', ' ')
        .lines()
        .joinToString("\n") { it.trim() }
        .replace(ExtraBlankLines, "\n\n")
        .trim('\n')
}

private fun StringBuilder.trimTrailingSpaces(): StringBuilder {
    while (isNotEmpty() && (last() == ' ' || last() == '\t')) setLength(length - 1)
    return this
}

/** Makes the text so far end with a line break (and a blank line); nothing at the very start. */
private fun StringBuilder.endLineWith(blankLine: Boolean): StringBuilder {
    trimTrailingSpaces()
    if (isEmpty()) return this
    val wanted = if (blankLine) 2 else 1
    var trailing = 0
    while (trailing < length && this[length - 1 - trailing] == '\n') trailing++
    repeat(wanted - trailing) { append('\n') }
    return this
}

private fun String.decodeHtmlEntities(): String = EntityPattern.replace(this) { match ->
    val name = match.groupValues[1]
    val decoded = when {
        name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.toCodePointString()
        name.startsWith("#") -> name.drop(1).toIntOrNull()?.toCodePointString()
        else -> NamedEntities[name]
    }
    decoded ?: match.value
}

private fun Int.toCodePointString(): String? =
    takeIf { it in 1..0x10FFFF }?.let { codePoint ->
        if (codePoint < 0x10000) codePoint.toChar().toString()
        else {
            val v = codePoint - 0x10000
            charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
        }
    }

private val HiddenBlocks = Regex(
    "(?s)<!--.*?-->|<(head|style|script|title)\\b[^>]*>.*?</\\1\\s*>",
    RegexOption.IGNORE_CASE,
)
private val HtmlWhitespace = Regex("[ \\t\\r\\n\\f]+")
/** Elements (group 1: closing slash, group 2: name), plus doctype and processing instructions. */
private val AnyTag = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*>|<[!?][^>]*>")
private val BlockTags = setOf(
    "div", "h1", "h2", "h3", "h4", "h5", "h6", "tr", "table", "ul", "ol", "li", "blockquote", "pre",
    "section", "article", "header", "footer", "hr", "address", "dl", "dt", "dd",
)
private val ExtraBlankLines = Regex("\n{3,}")
private val EntityPattern = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});")
private val NamedEntities = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
    "ndash" to "–",
    "mdash" to "—",
    "hellip" to "…",
    "lsquo" to "‘",
    "rsquo" to "’",
    "sbquo" to "‚",
    "ldquo" to "“",
    "rdquo" to "”",
    "bdquo" to "„",
    "laquo" to "«",
    "raquo" to "»",
    "bull" to "•",
    "middot" to "·",
    "copy" to "©",
    "reg" to "®",
    "euro" to "€",
    "auml" to "ä",
    "ouml" to "ö",
    "uuml" to "ü",
    "Auml" to "Ä",
    "Ouml" to "Ö",
    "Uuml" to "Ü",
    "szlig" to "ß",
    "eacute" to "é",
    "egrave" to "è",
    "agrave" to "à",
    "ccedil" to "ç",
)
