package com.kgs.calendar.navigation

import android.content.Intent
import android.provider.CalendarContract

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
    /** Hands the draft from [ShareToCalendarActivity][com.kgs.calendar.ShareToCalendarActivity] to the main activity. */
    fun writeTo(intent: Intent): Intent = intent
        .setAction(ACTION_CREATE_SHARED_EVENT)
        .putExtra(EXTRA_TITLE, title)
        .putExtra(EXTRA_NOTES, notes)

    companion object {
        const val ACTION_CREATE_SHARED_EVENT = "com.kgs.calendar.action.CREATE_SHARED_EVENT"
        private const val EXTRA_TITLE = "com.kgs.calendar.extra.SHARED_TITLE"
        private const val EXTRA_NOTES = "com.kgs.calendar.extra.SHARED_NOTES"

        const val MAX_TITLE_CHARS = 120
        const val MAX_NOTES_CHARS = 50_000

        /** The draft of an `ACTION_SEND` share, or null for any other intent. */
        fun fromShareIntent(intent: Intent): SharedEventDraft? {
            if (intent.action != Intent.ACTION_SEND) return null
            return fromShare(
                subject = runCatching { intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT) }.getOrNull(),
                text = runCatching { intent.getCharSequenceExtra(Intent.EXTRA_TEXT) }.getOrNull(),
                htmlText = runCatching { intent.getStringExtra(Intent.EXTRA_HTML_TEXT) }.getOrNull(),
            )
        }

        /** Android's calendar insert contract opens a draft for review, never saves directly. */
        fun fromCalendarInsertIntent(intent: Intent): SharedEventDraft? {
            val eventType = intent.type in setOf("vnd.android.cursor.dir/event", "vnd.android.cursor.item/event")
            val eventsUri = intent.data?.let {
                it.scheme == "content" && it.authority in setOf("com.android.calendar", "calendar") &&
                    it.path?.trimEnd('/') == "/events"
            } == true
            val insert = intent.action == Intent.ACTION_INSERT || intent.action == Intent.ACTION_INSERT_OR_EDIT
            if (!insert || !(eventsUri || eventType)) return null
            return SharedEventDraft(
                title = sharedTitle(intent.getStringExtra(CalendarContract.Events.TITLE).orEmpty()),
                notes = limitNotes(intent.getStringExtra(CalendarContract.Events.DESCRIPTION).orEmpty()),
                location = intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION).orEmpty().take(2_000),
                beginTimeMillis = intent.optionalTime(CalendarContract.EXTRA_EVENT_BEGIN_TIME),
                endTimeMillis = intent.optionalTime(CalendarContract.EXTRA_EVENT_END_TIME),
                allDay = intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false),
            )
        }

        private fun Intent.optionalTime(key: String): Long? =
            getLongExtra(key, Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE }

        /** The draft handed over by [writeTo]; the limits apply again because the main activity is exported. */
        fun readFrom(intent: Intent): SharedEventDraft? {
            if (intent.action != ACTION_CREATE_SHARED_EVENT) return null
            return SharedEventDraft(
                title = sharedTitle(runCatching { intent.getStringExtra(EXTRA_TITLE) }.getOrNull().orEmpty()),
                notes = limitNotes(runCatching { intent.getStringExtra(EXTRA_NOTES) }.getOrNull().orEmpty()),
            )
        }

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

        private fun sharedTitle(value: String): String =
            value.replace(Whitespace, " ").trim().truncated(MAX_TITLE_CHARS, preferWordBreak = true)

        private fun limitNotes(value: String): String {
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
internal fun htmlToPlainText(html: String): String {
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
    takeIf { Character.isValidCodePoint(it) && it != 0 }?.let { String(Character.toChars(it)) }

private val HiddenBlocks = Regex(
    "<!--.*?-->|<(head|style|script|title)\\b[^>]*>.*?</\\1\\s*>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
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
