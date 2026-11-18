package com.kgs.calendar.importing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class IcsAttachmentTest {
    private val uri = Uri.parse("content://mail/attachments/invitation.ics")

    @Test fun viewAndSharedStreamsAreAcceptedWhilePlainTextAndRemoteUrlsAreIgnored() {
        assertEquals(uri, Intent(Intent.ACTION_VIEW, uri).icsAttachmentUri())
        assertEquals(uri, Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri).icsAttachmentUri())
        assertEquals(uri, Intent(Intent.ACTION_SEND).apply { clipData = ClipData.newRawUri("invitation", uri) }.icsAttachmentUri())
        assertNull(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "email").icsAttachmentUri())
        assertNull(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/private.ics")).icsAttachmentUri())
    }

    @Test fun attachmentReaderClosesTheStreamAndRejectsOversizedInput() {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        var closed = false
        shadowOf(resolver).registerInputStream(uri, object : ByteArrayInputStream("BEGIN:VCALENDAR".toByteArray()) {
            override fun close() { closed = true; super.close() }
        })
        assertEquals("BEGIN:VCALENDAR", resolver.readIcsAttachment(uri))
        assertTrue(closed)
        shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(ByteArray(2 * 1024 * 1024 + 1)))
        try { resolver.readIcsAttachment(uri); fail("Expected size limit") } catch (_: IllegalArgumentException) { }
    }
}
