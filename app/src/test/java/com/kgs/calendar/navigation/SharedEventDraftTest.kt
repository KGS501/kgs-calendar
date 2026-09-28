package com.kgs.calendar.navigation

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedEventDraftTest {
    @Test
    fun subjectBecomesTitleAndTextTheNotesWithLineBreaks() {
        val draft = SharedEventDraft.fromShare(
            subject = "  Team lunch ",
            text = "Hi all,\r\nlunch at noon.\r\n\r\nSee you  \n",
            htmlText = null,
        )

        assertEquals("Team lunch", draft.title)
        assertEquals("Hi all,\nlunch at noon.\n\nSee you", draft.notes)
    }

    @Test
    fun firstNonBlankLineIsTheTitleWithoutASubject() {
        val draft = SharedEventDraft.fromShare(subject = "  ", text = "\n\n  Dentist on Friday \nBring the card", htmlText = null)

        assertEquals("Dentist on Friday", draft.title)
        assertEquals("Dentist on Friday\nBring the card", draft.notes.trim())
    }

    @Test
    fun multiLineSubjectIsJoinedIntoOneLine() {
        val draft = SharedEventDraft.fromShare(subject = "Re: planning\n\tmeeting", text = "Body", htmlText = null)

        assertEquals("Re: planning meeting", draft.title)
    }

    @Test
    fun longTitleIsCutAtAWordWithAnEllipsis() {
        val subject = List(40) { "word$it" }.joinToString(" ")

        val title = SharedEventDraft.fromShare(subject = subject, text = null, htmlText = null).title

        assertTrue(title.length <= SharedEventDraft.MAX_TITLE_CHARS)
        assertTrue(title.endsWith("…"))
        assertTrue(subject.startsWith(title.removeSuffix("…")))
        assertFalse(title.removeSuffix("…").endsWith(" "))
        // The last kept word is complete.
        assertTrue(title.removeSuffix("…").split(" ").last() in subject.split(" "))
    }

    @Test
    fun htmlIsConvertedWhenThereIsNoPlainText() {
        val html = """
            <html><head><title>Mail</title><style>p { color: red; }</style></head>
            <body><!-- tracking -->
              <div>Hi all,</div><div><br></div>
              <div>lunch   at <b>noon</b> &amp; coffee&nbsp;after.</div>
              <p>First paragraph</p><p>Second&#8217;s line<br>next line</p>
              <ul><li>One</li><li>Two</li></ul>
              <script>alert('x')</script>
            </body></html>
        """.trimIndent()

        val draft = SharedEventDraft.fromShare(subject = "Lunch", text = null, htmlText = html)

        assertEquals(
            "Hi all,\n\nlunch at noon & coffee after.\n\nFirst paragraph\n\nSecond’s line\nnext line\n\n• One\n• Two",
            draft.notes,
        )
        assertEquals("Lunch", draft.title)
    }

    @Test
    fun plainTextIsPreferredOverHtml() {
        val draft = SharedEventDraft.fromShare(subject = null, text = "Plain <b>as typed</b>", htmlText = "<p>Rich</p>")

        assertEquals("Plain <b>as typed</b>", draft.notes)
        assertEquals("Plain <b>as typed</b>", draft.title)
    }

    @Test
    fun htmlDocumentSentAsPlainTextIsConverted() {
        val draft = SharedEventDraft.fromShare(
            subject = null,
            text = "<!DOCTYPE html><html><body><p>Board meeting</p><p>Room 4 &lt;north&gt;</p></body></html>",
            htmlText = null,
        )

        assertEquals("Board meeting\n\nRoom 4 <north>", draft.notes)
        assertEquals("Board meeting", draft.title)
    }

    @Test
    fun emptyShareGivesAnEmptyDraft() {
        assertEquals(SharedEventDraft("", ""), SharedEventDraft.fromShare(subject = null, text = null, htmlText = null))
        assertEquals(SharedEventDraft("", ""), SharedEventDraft.fromShare(subject = " ", text = "\n ", htmlText = "  "))
    }

    @Test
    fun oversizedTextIsCapped() {
        val text = "Line of an endless mail\n".repeat(200_000)

        val draft = SharedEventDraft.fromShare(subject = null, text = text, htmlText = null)

        assertEquals(SharedEventDraft.MAX_NOTES_CHARS, draft.notes.length)
        assertTrue(draft.notes.endsWith("…"))
        assertEquals("Line of an endless mail", draft.title)
    }

    @Test
    fun oversizedHtmlIsConvertedAndCapped() {
        val html = "<div>Paragraph with <i>markup</i></div>".repeat(100_000)

        val draft = SharedEventDraft.fromShare(subject = "Big", text = null, htmlText = html)

        assertTrue(draft.notes.length <= SharedEventDraft.MAX_NOTES_CHARS)
        assertTrue(draft.notes.startsWith("Paragraph with markup\nParagraph with markup\n"))
    }

    @Test
    fun shareIntentReadsOnlyTheTextExtras() {
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Team lunch")
            .putExtra(Intent.EXTRA_TEXT, "Hi all,\nlunch at noon")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://evil/secret"))

        assertEquals(SharedEventDraft("Team lunch", "Hi all,\nlunch at noon"), SharedEventDraft.fromShareIntent(share))
    }

    @Test
    fun shareIntentWithOnlyHtmlTextIsConverted() {
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_HTML_TEXT, "<p>Hello<br>World</p>")

        assertEquals(SharedEventDraft("Hello", "Hello\nWorld"), SharedEventDraft.fromShareIntent(share))
    }

    @Test
    fun otherIntentsAreNoShares() {
        val view = Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_TEXT, "text")

        assertNull(SharedEventDraft.fromShareIntent(view))
        assertNull(SharedEventDraft.readFrom(view))
        assertNull(SharedEventDraft.readFrom(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "text")))
    }

    @Test
    fun forwardedDraftRoundTripsAndIsLimitedAgain() {
        val draft = SharedEventDraft("Team lunch", "Hi all,\nlunch at noon")
        assertEquals(draft, SharedEventDraft.readFrom(draft.writeTo(Intent())))

        val forged = SharedEventDraft("x".repeat(500), "y".repeat(SharedEventDraft.MAX_NOTES_CHARS * 2)).writeTo(Intent())
        val limited = SharedEventDraft.readFrom(forged)!!
        assertEquals(SharedEventDraft.MAX_TITLE_CHARS, limited.title.length)
        assertEquals(SharedEventDraft.MAX_NOTES_CHARS, limited.notes.length)
    }
}
