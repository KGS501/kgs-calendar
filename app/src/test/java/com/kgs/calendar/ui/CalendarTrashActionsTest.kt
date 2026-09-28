package com.kgs.calendar.ui

import com.kgs.calendar.data.LOCAL_COLLECTION
import com.kgs.calendar.data.SampleIcs
import com.kgs.calendar.data.local.entity.TrashedItemEntity
import com.kgs.calendar.data.trash.TrashedItemPreview
import okhttp3.mockwebserver.MockResponse
import com.kgs.calendar.data.RepositoryHarness
import com.kgs.calendar.data.eventPayload
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.taskPayload
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.widget.KgsWidgetKind
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarTrashActionsTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val widgetUpdates = AtomicInteger(0)
    private val reminderReschedules = AtomicInteger(0)
    private val message = MutableStateFlow<String?>(null)
    private val day = LocalDate.of(2026, 10, 12)

    private val actions = CalendarTrashActions(
        scope = scope,
        repository = repository,
        widgetRefresher = object : WidgetRefresher {
            override fun updateAll() {
                widgetUpdates.incrementAndGet()
            }

            override fun update(kind: KgsWidgetKind, forceFullDayUpdate: Boolean) = Unit
        },
        reminderRescheduler = ReminderRescheduler { reminderReschedules.incrementAndGet() },
        message = message,
    )

    @After
    fun tearDown() {
        // Wait for the state collector to stop before the database closes under it.
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        harness.close()
    }

    /** Waits in wall-clock time for the exact state an assertion depends on. */
    private fun awaitState(predicate: (TrashUiState) -> Boolean): TrashUiState = runBlocking {
        withTimeout(30_000) { actions.state.first(predicate) }
    }

    private fun awaitCondition(condition: () -> Boolean) = runBlocking {
        withTimeout(30_000) {
            while (!condition()) kotlinx.coroutines.delay(20)
        }
    }

    @Test
    fun stateListsDeletedItemsNewestFirstAndRestoreBringsTheEventBack() = runBlocking {
        repository.ensureLocalCalendar()
        repository.createEvent(eventPayload("Older", day))
        repository.createTask(taskPayload("Newer"))
        repository.deleteEvent(harness.eventsIn(LOCAL_COLLECTION).single().uid)
        Thread.sleep(5)
        repository.deleteTask(harness.tasksIn(LOCAL_COLLECTION).single().uid)

        val listed = awaitState { it.items.size == 2 }
        assertEquals(listOf("Newer", "Older"), listed.items.map { it.title })
        assertNull(listed.notice)

        actions.restore(listed.items.single { it.title == "Older" })

        // The item leaves the list as soon as the restore starts.
        val remaining = awaitState { it.items.map { item -> item.title } == listOf("Newer") }
        assertNull(remaining.notice)
        awaitCondition { widgetUpdates.get() == 1 && reminderReschedules.get() == 1 }
        assertEquals("Older", harness.eventsIn(LOCAL_COLLECTION).single().title)
        assertEquals(listOf("Newer"), repository.observeTrashedItems().first().map { it.title })
        assertNull(message.value)
    }

    @Test
    fun restoreIntoAnotherCalendarShowsANotice() = runBlocking {
        repository.ensureLocalCalendar()
        harness.database.collectionDao().upsertAll(
            listOf(
                CollectionEntity(
                    href = "local://kgs-calendar/family",
                    accountId = "local",
                    displayName = "Family",
                    color = 0xFF884422.toInt(),
                    supportsEvents = true,
                    supportsTasks = true,
                    syncToken = null,
                    ctag = null,
                    sourceType = SourceType.Local,
                ),
            ),
        )
        repository.createEvent(eventPayload("Picnic", day, collectionHref = LOCAL_COLLECTION))
        repository.deleteEvent(harness.eventsIn(LOCAL_COLLECTION).single().uid)
        harness.database.collectionDao().delete(LOCAL_COLLECTION)
        val item = awaitState { it.items.size == 1 }.items.single()

        actions.restore(item)

        val state = awaitState { it.notice != null && it.items.isEmpty() }
        assertEquals(TrashNotice.RestoredElsewhere("Picnic", "Family"), state.notice)
        assertTrue(state.items.isEmpty())
        assertEquals("Picnic", harness.eventsIn("local://kgs-calendar/family").single().title)

        actions.dismissNotice()

        assertNull(awaitState { it.notice == null }.notice)
    }

    @Test
    fun failedRestoreKeepsTheItemAndExplainsWhy() = runBlocking {
        repository.ensureLocalCalendar()
        repository.createTask(taskPayload("Orphan"))
        repository.deleteTask(harness.tasksIn(LOCAL_COLLECTION).single().uid)
        harness.database.collectionDao().delete(LOCAL_COLLECTION)
        val item = awaitState { it.items.size == 1 }.items.single()

        actions.restore(item)

        // Hidden while the restore ran, back once it failed.
        val state = awaitState { it.notice != null && it.items.isNotEmpty() }
        assertEquals(TrashNotice.NoWritableCalendar("Orphan"), state.notice)
        assertEquals(listOf(item.id), state.items.map { it.id })
        assertEquals(0, widgetUpdates.get())
    }

    @Test
    fun deletePermanentlyAndEmptyTrash() = runBlocking {
        repository.ensureLocalCalendar()
        listOf("One", "Two", "Three").forEach { repository.createEvent(eventPayload(it, day)) }
        harness.eventsIn(LOCAL_COLLECTION).forEach { repository.deleteEvent(it.uid) }
        val items = awaitState { it.items.size == 3 }.items

        actions.deletePermanently(items.first())

        assertEquals(items.drop(1).map { it.id }, awaitState { it.items.size == 2 }.items.map { it.id })

        actions.emptyTrash()

        awaitState { it.items.isEmpty() }
        assertTrue(harness.eventsIn(LOCAL_COLLECTION).isEmpty())
    }

    @Test
    fun entriesCarryTheItemsAsEventsAndTasksForTheUsualCards() = runBlocking {
        repository.ensureLocalCalendar()
        repository.createEvent(eventPayload("Picnic", day))
        repository.createTask(taskPayload("Pack basket"))
        val event = harness.eventsIn(LOCAL_COLLECTION).single()
        harness.database.eventDao().upsert(event.copy(manualColor = 0xFF123456.toInt()))
        repository.deleteEvent(event.uid)
        repository.deleteTask(harness.tasksIn(LOCAL_COLLECTION).single().uid)

        val entries = awaitState { it.entries.size == 2 }.entries
        val picnic = entries.single { it.event != null }
        assertEquals("Picnic", picnic.event!!.title)
        assertEquals(event.startsAtMillis, picnic.event!!.startsAtMillis)
        assertEquals(LOCAL_COLLECTION, picnic.event!!.collectionHref)
        assertEquals(0xFF123456.toInt(), picnic.event!!.manualColor)
        assertNull(picnic.task)
        val basket = entries.single { it.task != null }
        assertEquals("Pack basket", basket.task!!.title)
        // Display copies never share an href with a live item or with each other.
        assertEquals(TrashedItemPreview.displayHref(basket.item), basket.task!!.resourceHref)
        assertTrue(harness.tasksIn(LOCAL_COLLECTION).isEmpty())
    }

    @Test
    fun aServerRefusingAPermanentDeleteKeepsTheItemWithANotice() = runBlocking {
        val item = serverTrashItem()
        // Nextcloud refuses read-only sharees.
        harness.server.respondNext("DELETE", "/trashbin/objects/") { MockResponse().setResponseCode(403) }

        actions.deletePermanently(item)

        val state = awaitState { it.notice != null && it.items.isNotEmpty() }
        val notice = state.notice as TrashNotice.DeleteFailed
        assertEquals("Kickoff", notice.title)
        assertEquals(403, notice.statusCode)
        // Hidden while the delete ran, back once it failed.
        assertEquals(listOf(item.id), state.items.map { it.id })
        assertEquals(1, harness.server.trashedObjects().size)
    }

    @Test
    fun aServerRefusingToEmptyTheTrashKeepsItsItemsWithANotice() = runBlocking {
        val item = serverTrashItem()
        repository.ensureLocalCalendar()
        repository.createEvent(eventPayload("Dentist", day))
        repository.deleteEvent(harness.eventsIn(LOCAL_COLLECTION).single().uid)
        awaitState { it.items.size == 2 }
        harness.server.respondNext("DELETE", "/trashbin/objects/") { MockResponse().setResponseCode(403) }

        actions.emptyTrash()

        val state = awaitState { it.notice != null && it.items.size == 1 }
        assertEquals(TrashNotice.EmptyTrashFailed(403, (state.notice as TrashNotice.EmptyTrashFailed).reason), state.notice)
        // The local snapshot went; the refused server item stays.
        assertEquals(listOf(item.id), state.items.map { it.id })
    }

    @Test
    fun aRestoreTheServerRefusesKeepsTheItemWithANotice() = runBlocking {
        val item = serverTrashItem()
        harness.server.respondNext("MOVE", "/trashbin/objects/") { MockResponse().setResponseCode(409) }

        actions.restore(item)

        val state = awaitState { it.notice != null && it.items.isNotEmpty() }
        assertEquals(TrashNotice.ServerRefused("Kickoff", 409), state.notice)
        assertEquals(listOf(item.id), state.items.map { it.id })
    }

    /** A Nextcloud account with one event deleted in its web UI, listed in the trash. */
    private fun serverTrashItem(): TrashedItemEntity = runBlocking {
        val server = harness.server
        server.trashBinEnabled = true
        val href = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        server.trashRemote(href)
        assertTrue(repository.refreshTrash())
        awaitState { it.items.size == 1 }.items.single()
    }
}
