package com.kgs.calendar.data

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.TrashedItemEntity
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.data.trash.TrashRestoreResult
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.MutationAction
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.trash.TrashRetention
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class TrashRepositoryTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val day = LocalDate.of(2026, 10, 12)

    @After
    fun tearDown() = harness.close()

    private suspend fun trash(): List<TrashedItemEntity> = harness.database.trashDao().all()

    // --- Local calendar -------------------------------------------------------------------------

    @Test
    fun deletingLocalEventMovesSnapshotToTrashAndRestoreRecreatesIt() = runTest {
        repository.ensureLocalCalendar()
        repository.createEvent(eventPayload("Dentist", day, LocalTime.of(10, 0), LocalTime.of(11, 0), description = "Bring card"))
        val event = harness.eventsIn(LOCAL_COLLECTION).single()
        repository.updateEventManualColor(event.uid, 0xFF336699.toInt())
        val rawIcs = harness.resource(event.resourceHref)!!.rawIcs
        val before = System.currentTimeMillis()

        repository.deleteEvent(event.uid)

        assertNull(harness.event(event.resourceHref))
        assertNull(harness.resource(event.resourceHref))
        val item = trash().single()
        assertEquals(ComponentType.Event, item.componentType)
        assertEquals(event.uid, item.uid)
        assertEquals(LOCAL_COLLECTION, item.collectionHref)
        assertEquals("local", item.accountId)
        assertEquals(SourceType.Local, item.sourceType)
        assertEquals(event.resourceHref, item.resourceHref)
        assertNull(item.providerEventId)
        assertEquals(rawIcs, item.rawIcs)
        assertEquals("Dentist", item.title)
        assertEquals(harness.millis(day, LocalTime.of(10, 0)), item.startMillis)
        assertTrue(item.hasTime)
        assertEquals("Lokal", item.collectionName)
        assertEquals(harness.collection(LOCAL_COLLECTION)!!.color, item.collectionColor)
        assertEquals(0xFF336699.toInt(), item.manualColor)
        assertTrue(item.deletedAtMillis >= before)

        val result = repository.restoreTrashedItem(item.id)

        assertEquals(TrashRestoreResult.Restored(LOCAL_COLLECTION, "Lokal", inOriginalCalendar = true), result)
        val restored = harness.event(event.resourceHref)!!
        assertEquals(event.uid, restored.uid)
        assertEquals("Dentist", restored.title)
        assertEquals("Bring card", restored.description)
        assertEquals(event.startsAtMillis, restored.startsAtMillis)
        assertEquals(event.endsAtMillis, restored.endsAtMillis)
        assertEquals(0xFF336699.toInt(), restored.manualColor)
        assertEquals(rawIcs, harness.resource(event.resourceHref)!!.rawIcs)
        assertTrue(harness.pendingMutations().isEmpty())
        assertTrue(trash().isEmpty())
        assertEquals(TrashRestoreResult.NotFound, repository.restoreTrashedItem(item.id))
    }

    @Test
    fun deletingLocalTaskTrashesOnlyThatTaskAndRestoreKeepsChildrenReparented() = runTest {
        repository.ensureLocalCalendar()
        repository.createTask(taskPayload("Parent", dueDate = day))
        val parent = harness.tasksIn(LOCAL_COLLECTION).single()
        repository.createTask(taskPayload("Child", parentUid = parent.uid))
        val child = harness.tasksIn(LOCAL_COLLECTION).single { it.title == "Child" }

        repository.deleteTask(parent.uid)

        val item = trash().single()
        assertEquals(ComponentType.Task, item.componentType)
        assertEquals(parent.uid, item.uid)
        assertEquals("Parent", item.title)
        assertEquals(harness.startOfDay(day), item.startMillis)
        assertFalse(item.hasTime)
        assertNull(harness.task(child.resourceHref)!!.parentUid)

        assertTrue(repository.restoreTrashedItem(item.id) is TrashRestoreResult.Restored)

        val restored = harness.task(parent.resourceHref)!!
        assertEquals(parent.uid, restored.uid)
        assertEquals("Parent", restored.title)
        assertEquals(parent.dueAtMillis, restored.dueAtMillis)
        // Restoring does not re-parent the subtasks that moved up when the task was deleted.
        assertNull(harness.task(child.resourceHref)!!.parentUid)
        assertTrue(trash().isEmpty())
    }

    @Test
    fun convertingDeletesDoNotTrash() = runTest {
        repository.ensureLocalCalendar()
        repository.createEvent(eventPayload("Converted", day))
        repository.createTask(taskPayload("Converted task"))
        val event = harness.eventsIn(LOCAL_COLLECTION).single()
        val task = harness.tasksIn(LOCAL_COLLECTION).single()

        repository.deleteEvent(event.uid, moveToTrash = false)
        repository.deleteTask(task.uid, moveToTrash = false)

        assertNull(harness.event(event.resourceHref))
        assertNull(harness.task(task.resourceHref))
        assertTrue(trash().isEmpty())
    }

    @Test
    fun deletingOneOccurrenceDoesNotTrash() = runTest {
        repository.ensureLocalCalendar()
        repository.createEvent(eventPayload("Standup", day, recurrenceRule = "FREQ=DAILY;COUNT=5"))
        val event = harness.eventsIn(LOCAL_COLLECTION).single()

        repository.deleteEventOccurrence(event.uid, event.startsAtMillis + TimeUnit.DAYS.toMillis(1))

        assertNotNull(harness.event(event.resourceHref))
        assertTrue(trash().isEmpty())
    }

    @Test
    fun deletingPermanentlyAndEmptyingTheTrash() = runTest {
        repository.ensureLocalCalendar()
        listOf("One", "Two", "Three").forEach { repository.createEvent(eventPayload(it, day)) }
        harness.eventsIn(LOCAL_COLLECTION).forEach { repository.deleteEvent(it.uid) }
        val items = trash()
        assertEquals(3, items.size)
        assertEquals(items, repository.observeTrashedItems().first())

        repository.deleteTrashedItemPermanently(items.first().id)

        assertEquals(items.drop(1).map { it.id }, trash().map { it.id })

        repository.emptyTrash()

        assertTrue(trash().isEmpty())
        assertTrue(harness.eventsIn(LOCAL_COLLECTION).isEmpty())
    }

    @Test
    fun purgeRemovesOnlyItemsOlderThanThirtyDays() = runTest {
        val now = System.currentTimeMillis()
        val dao = harness.database.trashDao()
        val old = dao.insert(trashedItem("Old", deletedAtMillis = now - TimeUnit.DAYS.toMillis(31)))
        val borderline = dao.insert(trashedItem("Borderline", deletedAtMillis = now - TimeUnit.DAYS.toMillis(29)))
        val fresh = dao.insert(trashedItem("Fresh", deletedAtMillis = now))

        assertEquals(1, harness.components.trash.purgeExpired(now))

        assertEquals(setOf(borderline, fresh), trash().map { it.id }.toSet())
        assertNull(dao.get(old))
        assertEquals(TrashRetention.MILLIS, TimeUnit.DAYS.toMillis(30))

        // Two more days later, the borderline item has expired as well.
        harness.components.trash.purgeExpired(now + TimeUnit.DAYS.toMillis(2))

        assertEquals(listOf(fresh), trash().map { it.id })
    }

    @Test
    fun syncPurgesExpiredItems() = runTest {
        val dao = harness.database.trashDao()
        dao.insert(trashedItem("Old", deletedAtMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(40)))
        val fresh = dao.insert(trashedItem("Fresh", deletedAtMillis = System.currentTimeMillis()))

        repository.syncNow()

        assertEquals(listOf(fresh), trash().map { it.id })
    }

    // --- CalDAV ---------------------------------------------------------------------------------

    @Test
    fun deletingCalDavEventTrashesItTogetherWithTheQueuedDelete() = runTest {
        val server = harness.server
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        val rawIcs = harness.resource(eventHref)!!.rawIcs

        repository.deleteEvent("remote-event")

        val item = trash().single()
        assertEquals(SourceType.CalDav, item.sourceType)
        assertEquals(AccountEntity.PRIMARY_ID, item.accountId)
        assertEquals(server.eventsHref, item.collectionHref)
        assertEquals(eventHref, item.resourceHref)
        assertEquals(rawIcs, item.rawIcs)
        assertEquals("Events", item.collectionName)
        assertEquals(MutationAction.Delete, harness.pendingMutations().single().action)
    }

    @Test
    fun restoringUploadedCalDavDeleteQueuesCreateWithSameUidAndHref() = runTest {
        val server = harness.server
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        repository.deleteEvent("remote-event")
        repository.syncNow()
        assertNull(server.stored(eventHref))
        assertNull(harness.event(eventHref))
        val item = trash().single()
        server.clearRequests()

        val result = repository.restoreTrashedItem(item.id)

        assertEquals(TrashRestoreResult.Restored(server.eventsHref, "Events", inOriginalCalendar = true), result)
        val mutation = harness.pendingMutations().single()
        assertEquals(MutationAction.Put, mutation.action)
        assertEquals(eventHref, mutation.resourceHref)
        assertEquals(server.eventsHref, mutation.collectionHref)
        assertNull(mutation.baseEtag)
        assertEquals(item.rawIcs, mutation.payloadIcs)
        assertEquals("Kickoff", harness.event(eventHref)!!.title)
        assertEquals("remote-event", harness.event(eventHref)!!.uid)
        assertTrue(trash().isEmpty())

        repository.pushPendingChangesCreatedSince(0)

        val put = server.requests("PUT").single()
        assertEquals(eventHref, put.path)
        assertEquals("*", put.header("If-None-Match"))
        assertNull(put.header("If-Match"))
        assertTrue(put.body.unfoldedIcs().contains("UID:remote-event"))
        assertTrue(put.body.unfoldedIcs().contains("SUMMARY:Kickoff"))
        val stored = server.stored(eventHref)!!
        assertEquals(stored.etag, harness.resource(eventHref)!!.etag)
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun restoringBeforeTheDeleteWasUploadedWithdrawsTheQueuedDelete() = runTest {
        val server = harness.server
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        val etag = harness.resource(eventHref)!!.etag!!
        repository.updateEvent("remote-event", eventPayload("Kickoff (edited)", day))
        repository.deleteEvent("remote-event")
        assertEquals(MutationAction.Delete, harness.pendingMutations().single().action)
        server.clearRequests()

        repository.restoreTrashedItem(trash().single().id)

        val mutation = harness.pendingMutations().single()
        assertEquals(MutationAction.Put, mutation.action)
        assertEquals(eventHref, mutation.resourceHref)
        assertEquals(etag, mutation.baseEtag)
        assertEquals("Kickoff (edited)", harness.event(eventHref)!!.title)

        repository.syncNow()

        assertTrue(server.requests("DELETE").isEmpty())
        val put = server.requests("PUT").single()
        assertEquals(eventHref, put.path)
        assertEquals(etag, put.header("If-Match"))
        assertTrue(server.stored(eventHref)!!.ics.unfoldedIcs().contains("SUMMARY:Kickoff (edited)"))
        assertEquals("Kickoff (edited)", harness.event(eventHref)!!.title)
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun restoringCalDavTaskQueuesCreate() = runTest {
        val server = harness.server
        val taskHref = server.putRemote(server.tasksHref, "todo.ics", SampleIcs.task("remote-task", "Write agenda"))
        harness.addSyncedCalDavAccount()
        repository.deleteTask("remote-task")
        repository.syncNow()
        assertNull(server.stored(taskHref))
        server.clearRequests()

        repository.restoreTrashedItem(trash().single().id)
        repository.pushPendingChangesCreatedSince(0)

        val put = server.requests("PUT").single()
        assertEquals(taskHref, put.path)
        assertEquals("*", put.header("If-None-Match"))
        assertTrue(put.body.unfoldedIcs().contains("UID:remote-task"))
        assertEquals("Write agenda", harness.task(taskHref)!!.title)
    }

    @Test
    fun restoreUsesANewHrefWhenTheOldOneIsTaken() = runTest {
        val server = harness.server
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        repository.deleteEvent("remote-event")
        repository.syncNow()
        // Another client reuses the file name for a different event.
        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("other-event", "Other"))
        repository.syncNow()
        assertEquals("other-event", harness.event(eventHref)!!.uid)

        repository.restoreTrashedItem(trash().single().id)

        val mutation = harness.pendingMutations().single()
        assertNotEquals(eventHref, mutation.resourceHref)
        assertTrue(mutation.resourceHref.startsWith(server.eventsHref))
        assertEquals("remote-event", harness.event(mutation.resourceHref)!!.uid)
        assertEquals("other-event", harness.event(eventHref)!!.uid)
        repository.pushPendingChangesCreatedSince(0)
        assertEquals("*", server.requests("PUT").single().header("If-None-Match"))
    }

    @Test
    fun restoreRefusesWhenTheCalendarAlreadyHasTheUid() = runTest {
        val server = harness.server
        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        repository.deleteEvent("remote-event")
        repository.syncNow()
        server.putRemote(server.eventsHref, "copy.ics", SampleIcs.event("remote-event", "Kickoff again"))
        repository.syncNow()
        val item = trash().single()

        assertEquals(TrashRestoreResult.AlreadyExists, repository.restoreTrashedItem(item.id))

        assertEquals(listOf(item.id), trash().map { it.id })
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun remoteDeletesFoundBySyncAreNotTrashed() = runTest {
        val server = harness.server
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        val taskHref = server.putRemote(server.tasksHref, "todo.ics", SampleIcs.task("remote-task", "Write agenda"))
        harness.addSyncedCalDavAccount()

        server.deleteRemote(eventHref)
        server.deleteRemote(taskHref)
        repository.syncNow()

        assertNull(harness.event(eventHref))
        assertNull(harness.task(taskHref))
        assertTrue(trash().isEmpty())
    }

    // --- Missing or read-only calendars ---------------------------------------------------------

    @Test
    fun restoreFallsBackToAnotherWritableCalendarWhenTheOriginalIsGone() = runTest {
        repository.ensureLocalCalendar()
        val server = harness.server
        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        repository.deleteEvent("remote-event")
        repository.syncNow()
        harness.database.collectionDao().delete(server.eventsHref)

        val result = repository.restoreTrashedItem(trash().single().id)

        // The CalDAV account has no other event calendar, so the item goes to the local calendar.
        assertEquals(TrashRestoreResult.Restored(LOCAL_COLLECTION, "Lokal", inOriginalCalendar = false), result)
        val restored = harness.eventsIn(LOCAL_COLLECTION).single()
        assertEquals("remote-event", restored.uid)
        assertEquals("Kickoff", restored.title)
        assertTrue(harness.pendingMutations().isEmpty())
        assertTrue(trash().isEmpty())
    }

    @Test
    fun restorePrefersAnotherCalendarOfTheSameAccount() = runTest {
        repository.ensureLocalCalendar()
        val server = harness.server
        server.addCollection("${server.homeHref}work/", "Work", setOf("VEVENT"))
        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        repository.deleteEvent("remote-event")
        repository.syncNow()
        harness.database.collectionDao().delete(server.eventsHref)

        val result = repository.restoreTrashedItem(trash().single().id)

        assertEquals(TrashRestoreResult.Restored("${server.homeHref}work/", "Work", inOriginalCalendar = false), result)
        val mutation = harness.pendingMutations().single()
        assertEquals("${server.homeHref}work/", mutation.collectionHref)
        assertNull(mutation.baseEtag)
    }

    @Test
    fun restoreRefusesWhenNoWritableCalendarIsLeft() = runTest {
        repository.ensureLocalCalendar()
        repository.createTask(taskPayload("Lonely"))
        repository.deleteTask(harness.tasksIn(LOCAL_COLLECTION).single().uid)
        harness.database.collectionDao().delete(LOCAL_COLLECTION)
        val item = trash().single()

        assertEquals(TrashRestoreResult.NoWritableCalendar, repository.restoreTrashedItem(item.id))

        assertEquals(listOf(item.id), trash().map { it.id })
    }

    // --- Android device calendars ---------------------------------------------------------------

    @Test
    fun deviceCalendarEventIsSnapshottedBeforeTheProviderDeleteAndRestoredAsANewProviderEvent() = runTest {
        val provider = FakeCalendarProvider.install()
        val collection = addDeviceCalendar()
        repository.createEvent(eventPayload("Seed meeting", day, LocalTime.of(14, 0), LocalTime.of(15, 0), collectionHref = collection.href))
        val event = harness.eventsIn(collection.href).single()
        val eventId = event.resourceHref.substringAfterLast('/').toLong()
        assertTrue(eventId in provider.events)

        repository.deleteEvent(event.uid)

        assertFalse(eventId in provider.events)
        assertNull(harness.event(event.resourceHref))
        val item = trash().single()
        assertEquals(SourceType.AndroidProvider, item.sourceType)
        assertEquals(eventId, item.providerEventId)
        assertEquals("Seed", item.collectionName)
        assertTrue(item.rawIcs.unfoldedIcs().contains("SUMMARY:Seed meeting"))

        val result = repository.restoreTrashedItem(item.id)

        assertEquals(TrashRestoreResult.Restored(collection.href, "Seed", inOriginalCalendar = true), result)
        val restored = harness.eventsIn(collection.href).single()
        val newId = restored.resourceHref.substringAfterLast('/').toLong()
        assertNotEquals(eventId, newId)
        assertEquals("android-event-$newId", restored.uid)
        assertEquals("Seed meeting", restored.title)
        assertEquals(event.startsAtMillis, restored.startsAtMillis)
        val values = provider.events.getValue(newId)
        assertEquals("Seed meeting", values.getAsString(CalendarContract.Events.TITLE))
        assertEquals(7L, values.getAsLong(CalendarContract.Events.CALENDAR_ID))
        assertEquals(event.startsAtMillis, values.getAsLong(CalendarContract.Events.DTSTART))
        assertTrue(harness.pendingMutations().isEmpty())
        assertTrue(trash().isEmpty())
    }

    @Test
    fun failedProviderDeleteLeavesNothingInTheTrash() = runTest {
        val provider = FakeCalendarProvider.install()
        val collection = addDeviceCalendar()
        repository.createEvent(eventPayload("Stays", day, collectionHref = collection.href))
        val event = harness.eventsIn(collection.href).single()
        provider.failDeletes = true

        expectFailure<IllegalStateException> { repository.deleteEvent(event.uid) }

        assertNotNull(harness.event(event.resourceHref))
        assertTrue(trash().isEmpty())
    }

    private suspend fun addDeviceCalendar(): CollectionEntity {
        shadowOf(harness.context as Application).grantPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        harness.database.accountDao().upsert(
            AccountEntity(
                id = AndroidCalendarProviderClient.ANDROID_ACCOUNT_ID,
                serverUrl = AndroidCalendarProviderClient.ANDROID_ACCOUNT_SERVER_URL,
                username = AndroidCalendarProviderClient.ANDROID_ACCOUNT_USERNAME,
                displayName = "Device",
                lastSyncAtMillis = null,
                sourceType = SourceType.AndroidProvider,
            ),
        )
        val collection = CollectionEntity(
            href = "${AndroidCalendarProviderClient.ANDROID_CALENDAR_PREFIX}7",
            accountId = AndroidCalendarProviderClient.ANDROID_ACCOUNT_ID,
            displayName = "Seed",
            color = 0xFF228844.toInt(),
            supportsEvents = true,
            supportsTasks = false,
            syncToken = null,
            ctag = null,
            sourceType = SourceType.AndroidProvider,
            externalId = "7",
        )
        harness.database.collectionDao().upsertAll(listOf(collection))
        return collection
    }

    private fun trashedItem(title: String, deletedAtMillis: Long) = TrashedItemEntity(
        componentType = ComponentType.Event,
        uid = "$title@test",
        collectionHref = LOCAL_COLLECTION,
        accountId = "local",
        sourceType = SourceType.Local,
        resourceHref = "$LOCAL_COLLECTION/$title.ics",
        rawIcs = SampleIcs.event("$title@test", title),
        title = title,
        startMillis = null,
        hasTime = true,
        collectionName = "Lokal",
        collectionColor = 0,
        deletedAtMillis = deletedAtMillis,
    )
}

/** In-memory stand-in for the calendar provider: events and reminders by id, nothing to query. */
class FakeCalendarProvider : ContentProvider() {
    val events = LinkedHashMap<Long, ContentValues>()
    private val reminders = mutableListOf<ContentValues>()
    private var nextId = 100L

    @Volatile var failDeletes = false

    override fun onCreate(): Boolean = true

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val id = nextId++
        when {
            uri.path.orEmpty().startsWith(CalendarContract.Events.CONTENT_URI.path!!) -> events[id] = ContentValues(values)
            else -> reminders += ContentValues(values)
        }
        return ContentUris.withAppendedId(uri, id)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        if (!uri.path.orEmpty().startsWith(CalendarContract.Events.CONTENT_URI.path!!)) return 0
        if (failDeletes) error("Provider refused the delete.")
        return if (events.remove(ContentUris.parseId(uri)) != null) 1 else 0
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val id = ContentUris.parseId(uri)
        val existing = events[id] ?: return 0
        values?.let { existing.putAll(it) }
        return 1
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor = MatrixCursor(projection ?: emptyArray())

    override fun getType(uri: Uri): String? = null

    companion object {
        fun install(): FakeCalendarProvider =
            Robolectric.setupContentProvider(FakeCalendarProvider::class.java, CalendarContract.AUTHORITY)
    }
}
