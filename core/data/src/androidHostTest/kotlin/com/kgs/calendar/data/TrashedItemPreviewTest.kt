package com.kgs.calendar.data

import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.TrashedItemEntity
import com.kgs.calendar.data.remote.CalDavTrashBinSupport
import com.kgs.calendar.data.remote.RemoteTrashBin
import com.kgs.calendar.data.trash.TrashedItemPreview
import com.kgs.calendar.data.trash.previewOf
import com.kgs.calendar.data.trash.trashRetentionDays
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.trash.TrashRetention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Reading "Recently deleted" items back into events and tasks for display, and the retention the delete dialogs quote. */
class TrashedItemPreviewTest {
    private val codec = IcalCodec()
    private val color = 0xFF3366CC.toInt()

    @Test
    fun eventsAreReadBackFromTheirIcsWithTheItemsOwnColour() {
        val item = trashed(ComponentType.Event, SampleIcs.event("e1", "Kickoff"), manualColor = 0xFFAA0000.toInt())

        val preview = codec.previewOf(item)

        val event = preview.event!!
        assertNull(preview.task)
        assertEquals("Kickoff", event.title)
        assertEquals(Instant.parse("2026-10-05T08:00:00Z").toEpochMilli(), event.startsAtMillis)
        assertEquals("work", event.collectionHref)
        assertEquals(color, event.color)
        assertEquals(0xFFAA0000.toInt(), event.manualColor)
        assertEquals(TrashedItemPreview.displayHref(item), event.resourceHref)
    }

    @Test
    fun tasksAreReadBackFromTheirIcs() {
        val item = trashed(ComponentType.Task, SampleIcs.task("t1", "Write agenda"))

        val task = codec.previewOf(item).task!!

        assertEquals("Write agenda", task.title)
        assertEquals(Instant.parse("2026-10-06T15:00:00Z").toEpochMilli(), task.dueAtMillis)
        assertFalse(task.isCompleted)
        assertEquals("trashed-item:${item.id}", task.resourceHref)
    }

    @Test
    fun unreadableDataFallsBackToTheStoredTitleAndDate() {
        val start = Instant.parse("2026-10-05T08:00:00Z").toEpochMilli()
        val event = codec.previewOf(trashed(ComponentType.Event, "garbage", startMillis = start)).event!!
        assertEquals("Stored title", event.title)
        assertEquals(start, event.startsAtMillis)
        assertFalse(event.allDay)

        val task = codec.previewOf(trashed(ComponentType.Task, "garbage", startMillis = null)).task!!
        assertEquals("Stored title", task.title)
        assertNull(task.dueAtMillis)
    }

    @Test
    fun retentionIsTheLocalOneExceptForNextcloudTrashBins() {
        val local = collection(SourceType.Local)
        val calDav = collection(SourceType.CalDav)
        fun account(support: CalDavTrashBinSupport?) = AccountEntity(
            serverUrl = "https://cloud.example",
            username = "me",
            displayName = null,
            lastSyncAtMillis = null,
            capabilitiesJson = support?.writeInto(null),
        )
        val home = "https://cloud.example/remote.php/dav/calendars/me/"

        assertEquals(TrashRetention.DAYS, trashRetentionDays(local, null))
        assertEquals(TrashRetention.DAYS, trashRetentionDays(collection(SourceType.AndroidProvider), null))
        // A server without a trash bin keeps local snapshots.
        assertEquals(TrashRetention.DAYS, trashRetentionDays(calDav, account(CalDavTrashBinSupport(home, null, 0))))
        // Nextcloud's retention, rounded up to whole days.
        val sevenDays = RemoteTrashBin("${home}trashbin/", 7L * 24 * 60 * 60)
        assertEquals(7, trashRetentionDays(calDav, account(CalDavTrashBinSupport(home, sevenDays, 0))))
        val halfADay = RemoteTrashBin("${home}trashbin/", 12L * 60 * 60)
        assertEquals(1, trashRetentionDays(calDav, account(CalDavTrashBinSupport(home, halfADay, 0))))
        // Unknown: not checked yet, no reported retention, or no calendar.
        assertNull(trashRetentionDays(calDav, account(null)))
        assertNull(trashRetentionDays(calDav, account(CalDavTrashBinSupport(home, RemoteTrashBin("${home}trashbin/", null), 0))))
        assertNull(trashRetentionDays(null, null))
    }

    private fun trashed(
        type: ComponentType,
        ics: String,
        manualColor: Int? = null,
        startMillis: Long? = null,
    ) = TrashedItemEntity(
        id = 42,
        componentType = type,
        uid = "uid",
        collectionHref = "work",
        accountId = "primary",
        sourceType = SourceType.CalDav,
        resourceHref = "/cal/work/uid.ics",
        rawIcs = ics,
        title = "Stored title",
        startMillis = startMillis,
        hasTime = true,
        collectionName = "Work",
        collectionColor = color,
        manualColor = manualColor,
        deletedAtMillis = 1_790_000_000_000L,
    )

    private fun collection(sourceType: SourceType) = CollectionEntity(
        href = "work",
        accountId = "primary",
        displayName = "Work",
        color = color,
        supportsEvents = true,
        supportsTasks = true,
        syncToken = null,
        ctag = null,
        sourceType = sourceType,
    )
}
