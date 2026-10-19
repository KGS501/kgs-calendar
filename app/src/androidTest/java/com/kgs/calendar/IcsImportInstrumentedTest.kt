package com.kgs.calendar

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

@SdkSuppress(minSdkVersion = 29)
class IcsImportInstrumentedTest {
    @get:Rule val composeRule = createEmptyComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val graph get() = KgsCalendarApplication.graph(context)
    private val prefix = "issue44-${UUID.randomUUID()}"
    private val files = mutableListOf<Uri>()

    @Before fun prepare() = runBlocking {
        graph.settingsStore.setWelcomeCompleted(true)
        graph.settingsStore.markExactAlarmPromptShown()
        graph.calendarSources.ensureLocalCalendar()
    }

    @After fun cleanup() = runBlocking {
        files.forEach { context.contentResolver.delete(it, null, null) }
        graph.database.resourceDao().all().filter { it.uid.startsWith(prefix) }.forEach {
            graph.eventMutations.deleteEvent(it.href, moveToTrash = false)
        }
    }

    @Test fun calendarAttachmentsResolveWithoutCapturingUnrelatedFilesOrTextSharing() {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://mail/attachment/17"), "text/calendar")
        val share = Intent(Intent.ACTION_SEND).setType("text/calendar")
        val generic = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://downloads/public/invitation.ics"), "application/octet-stream")
        val noType = Intent(Intent.ACTION_VIEW, Uri.parse("file:///sdcard/Download/invitation.ics"))
        for (intent in listOf(view, share, generic, noType)) {
            assertTrue("No ICS handler for $intent", resolvesToImporter(intent))
        }
        assertFalse(resolvesToImporter(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://downloads/public/photo.jpg"), "image/jpeg")))
        assertFalse(resolvesToImporter(Intent(Intent.ACTION_SEND).setType("text/plain")))
    }

    @Test fun contentAttachmentPreviewSurvivesRotationThenImportsAllEventsAndSkipsDuplicates() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//KGS Import Test//EN
            METHOD:REQUEST
            BEGIN:VEVENT
            UID:$prefix-timed
            DTSTART;TZID=Europe/Berlin:20261019T100000
            DTEND;TZID=Europe/Berlin:20261019T110000
            SUMMARY:ICS emulator meeting
            DESCRIPTION:Attachment details
            LOCATION:Room 44
            RRULE:FREQ=DAILY;COUNT=2
            END:VEVENT
            BEGIN:VEVENT
            UID:$prefix-day
            DTSTART;VALUE=DATE:20261020
            DTEND;VALUE=DATE:20261022
            SUMMARY:ICS emulator holiday
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val uri = attachment(raw)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/calendar")
            .setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityScenario.launch<IcsImportActivity>(view).use { scenario ->
            waitFor("ICS emulator meeting")
            assertEquals(0, importedEvents().size)
            scenario.recreate()
            waitFor("ICS emulator meeting")
            composeRule.onAllNodesWithText(context.getString(R.string.ics_import_action)).onFirst().performClick()
            waitFor(context.getString(R.string.ics_import_result, 2, 0))
            val events = importedEvents()
            assertEquals(2, events.size)
            val meeting = events.single { it.uid.endsWith("-timed") }
            assertEquals(Instant.parse("2026-10-19T08:00:00Z").toEpochMilli(), meeting.startsAtMillis)
            assertEquals("Room 44", meeting.location)
            assertEquals("Attachment details", meeting.description)
            assertEquals("FREQ=DAILY;COUNT=2", meeting.recurrenceRule)
            assertTrue(events.single { it.uid.endsWith("-day") }.allDay)
            scenario.recreate()
            waitFor(context.getString(R.string.ics_import_result, 2, 0))
        }
        val share = Intent(Intent.ACTION_SEND).setType("text/calendar").putExtra(Intent.EXTRA_STREAM, uri)
            .setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityScenario.launch<IcsImportActivity>(share).use {
            waitFor("ICS emulator meeting")
            composeRule.onAllNodesWithText(context.getString(R.string.ics_import_action)).onFirst().performClick()
            waitFor(context.getString(R.string.ics_import_result, 0, 2))
            assertEquals(2, importedEvents().size)
        }
    }

    @Test fun malformedAttachmentShowsAnErrorAndSavesNothing() {
        val uri = attachment("This is an email, not an iCalendar file.")
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/calendar")
            .setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityScenario.launch<IcsImportActivity>(intent).use {
            waitFor(context.getString(R.string.ics_import_invalid_error))
            assertTrue(importedEvents().isEmpty())
        }
    }

    @Test fun closingThePreviewDoesNotImportAndUnreadableAttachmentsShowAnError() {
        val raw = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:$prefix-cancel\nDTSTART:20261019T080000Z\nDTEND:20261019T090000Z\nSUMMARY:Cancel this import\nEND:VEVENT\nEND:VCALENDAR"
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(attachment(raw), "text/calendar")
            .setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityScenario.launch<IcsImportActivity>(intent).use {
            waitFor("Cancel this import")
            composeRule.onAllNodesWithText(context.getString(R.string.close)).onFirst().performClick()
        }
        assertTrue(importedEvents().isEmpty())
        val missing = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://missing.ics.provider/invitation"), "text/calendar")
            .setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<IcsImportActivity>(missing).use {
            waitFor(context.getString(R.string.ics_import_read_error))
            assertTrue(importedEvents().isEmpty())
        }
    }

    private fun resolvesToImporter(intent: Intent): Boolean = context.packageManager.queryIntentActivities(
        intent.setPackage(context.packageName), PackageManager.MATCH_DEFAULT_ONLY,
    ).any { it.activityInfo.name == IcsImportActivity::class.java.name }

    private fun attachment(raw: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$prefix-${files.size}.ics")
            put(MediaStore.Downloads.MIME_TYPE, "text/calendar")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        files += uri
        requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(raw) }
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return uri
    }

    private fun importedEvents() = runBlocking {
        graph.database.resourceDao().all().filter { it.uid.startsWith(prefix) }.mapNotNull { graph.database.eventDao().byResource(it.href) }
    }

    private fun waitFor(text: String) {
        composeRule.waitUntil(30_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
