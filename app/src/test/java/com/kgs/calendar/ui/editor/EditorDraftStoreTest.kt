package com.kgs.calendar.ui.editor

import androidx.compose.runtime.saveable.SaverScope
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.ConversionSource
import com.kgs.calendar.ui.CreationSheet
import com.kgs.calendar.ui.EditorTransferDraft
import com.kgs.calendar.ui.shell.CalendarShellUiState
import com.kgs.calendar.ui.shell.CalendarShellUiStateTest.Companion.DefaultColor
import com.kgs.calendar.ui.shell.CalendarShellUiStateTest.Companion.collection
import com.kgs.calendar.ui.shell.CalendarShellUiStateTest.Companion.event
import com.kgs.calendar.ui.shell.editorSchedule
import com.kgs.calendar.ui.shell.initialEditorSchedule
import com.kgs.calendar.ui.shell.newEventSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

// Robolectric provides android.util.Log for the logged write failures.
@RunWith(RobolectricTestRunner::class)
class EditorDraftStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val today = LocalDate.of(2026, 9, 23)
    private val loadedState = CalendarUiState(
        initialDataLoaded = true,
        collections = listOf(collection("work")),
        events = listOf(event("meeting")),
    )
    private val directory: File get() = File(folder.root, "editor-drafts")

    private fun TestScope.fileStore() = EditorDraftStore(EditorDraftFiles(directory), ioScope = this)

    private fun draftFiles(): List<String> = directory.list().orEmpty().sorted()

    @Test
    fun liveDraftKeepsItsValuesAndEndsWhenDiscarded() {
        val store = EditorDraftStore()
        val draftId = store.newDraft()

        store.put(draftId, "title", "Typed")
        store.put(draftId, "manualColor", null)

        assertEquals(mapOf("title" to "Typed", "manualColor" to null), store.values(draftId))
        store.discard(draftId)
        assertTrue(store.values(draftId).isEmpty())
        store.put(draftId, "title", "Late write")
        assertTrue(store.values(draftId).isEmpty())
    }

    @Test
    fun persistedDraftSurvivesProcessDeathIncludingAOneMegabyteDescription() = runTest {
        val description = "Lorem ipsum dolor sit amet, ".repeat(40_000).take(1_000_000)
        val store = fileStore()
        val draftId = store.newDraft()
        store.put(draftId, "description", description)
        store.put(draftId, "manualColor", null)
        store.put(draftId, "priority", 3)
        store.put(draftId, "isCompleted", true)
        store.put(draftId, "reminderMinutes", DraftCodec.MinuteSet.save(linkedSetOf(30, 0, 5)))
        store.put(draftId, "transferDraft", mapOf("title" to "x", "schedule" to mapOf("allDay" to false), "tags" to listOf("a")))
        advanceUntilIdle()

        val restored = fileStore().values(draftId)

        assertEquals(description, restored["description"])
        assertTrue(restored.containsKey("manualColor"))
        assertNull(restored["manualColor"])
        assertEquals(3, DraftCodec.Number.restore(restored["priority"]))
        assertEquals(true, restored["isCompleted"])
        assertEquals(listOf(30, 0, 5), DraftCodec.MinuteSet.restore(restored["reminderMinutes"]).toList())
        assertEquals(mapOf("title" to "x", "schedule" to mapOf("allDay" to false), "tags" to listOf("a")), restored["transferDraft"])
    }

    @Test
    fun writesAreBatchedAndFlushWritesAtOnce() = runTest {
        val store = fileStore()
        val draftId = store.newDraft()
        "Typing".forEachIndexed { index, _ -> store.put(draftId, "title", "Typing".take(index + 1)) }
        assertTrue(draftFiles().isEmpty())

        store.flush()

        assertEquals(listOf("$draftId.json"), draftFiles())
        assertEquals("Typing", fileStore().values(draftId)["title"])
    }

    @Test
    fun flushWaitsForAWriteInFlightAndReturnsOnlyOnceTheLatestRevisionIsWritten() {
        val blocked = BlockingFirstWrite()
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = EditorDraftStore(blocked.files, ioScope, writeDelayMillis = 0)
        try {
            val draftId = store.newDraft()
            store.put(draftId, "title", "Older")
            assertTrue(blocked.started.await(5, TimeUnit.SECONDS))
            store.put(draftId, "title", "Newer")

            val flushing = thread { store.flush() }
            flushing.join(300)
            assertTrue("flush returned while a write was still running", flushing.isAlive)
            blocked.release.countDown()
            flushing.join(5_000)

            assertFalse(flushing.isAlive)
            assertEquals("Newer", EditorDraftFiles(directory).read(draftId)!!["title"])
        } finally {
            blocked.release.countDown()
            ioScope.cancel()
        }
    }

    @Test
    fun aRevisionQueuedDuringAWriteInFlightIsWrittenAfterIt() = runBlocking {
        val blocked = BlockingFirstWrite()
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = EditorDraftStore(blocked.files, ioScope, writeDelayMillis = 0)
        try {
            val draftId = store.newDraft()
            store.put(draftId, "title", "Older")
            assertTrue(blocked.started.await(5, TimeUnit.SECONDS))
            store.put(draftId, "title", "Newer")

            blocked.release.countDown()
            withTimeout(5_000) { ioScope.coroutineContext.job.children.forEach { it.join() } }

            assertEquals("Newer", EditorDraftFiles(directory).read(draftId)!!["title"])
        } finally {
            blocked.release.countDown()
            ioScope.cancel()
        }
    }

    @Test
    fun theLiveDraftWinsOverTheFileAndTheFileOverNothing() = runTest {
        val store = fileStore()
        val draftId = store.newDraft()
        store.put(draftId, "title", "Written")
        advanceUntilIdle()
        store.put(draftId, "title", "Live")

        assertEquals("Live", store.values(draftId)["title"])
        assertEquals("Written", fileStore().values(draftId)["title"])
        assertTrue(fileStore().values("unknown-draft").isEmpty())
    }

    @Test
    fun fieldsPreferTheDraftOverTheItemAndResetOnlyWhenTheirInputsChange() {
        val store = EditorDraftStore()
        val draftId = store.newDraft()
        store.put(draftId, "title", "Restored")
        store.put(draftId, "manualColor", null)
        store.put(draftId, "priority", "not a number")
        val fields = EditorDraftFields(store, draftId)

        val title = fields.newState("title", DraftCodec.Text) { "From the item" }
        val color = fields.newState("manualColor", DraftCodec.OptionalNumber) { 7 }
        val priority = fields.newState("priority", DraftCodec.Number) { 9 }
        val notes = fields.newState("notes", DraftCodec.Text) { "Item notes" }

        assertEquals("Restored", title.value)
        assertNull(color.value)
        assertEquals(9, priority.value)
        assertEquals("Item notes", notes.value)
        title.value = "Edited"
        assertEquals("Edited", store.values(draftId)["title"])
        // A later change of the field's inputs, e.g. a new transfer draft, resets it as before.
        assertEquals("From the item", fields.newState("title", DraftCodec.Text) { "From the item" }.value)
        assertEquals("From the item", store.values(draftId)["title"])

        // A new composition after a rotation takes the draft again.
        val afterRotation = EditorDraftFields(store, draftId)
        assertEquals("From the item", afterRotation.newState("title", DraftCodec.Text) { "Other" }.value)
        assertEquals(9, afterRotation.newState("priority", DraftCodec.Number) { 1 }.value)
    }

    @Test
    fun afterProcessDeathTheDraftWinsOverTheItemThatLoadsLater() = runTest {
        val store = fileStore()
        val shell = CalendarShellUiState(initialEditorSchedule(today), DefaultColor, store)
        val meeting = event("meeting")
        shell.editEvent(meeting, meeting.editorSchedule())
        val draftId = shell.editorDraftId!!
        EditorDraftFields(store, draftId).newState("title", DraftCodec.Text) { meeting.title }.value = "Typed draft"
        advanceUntilIdle()

        val saved = with(CalendarShellUiState.saver(today, DefaultColor, store) { loadedState }) {
            SaverScope { true }.save(shell)
        }!!
        val newStore = fileStore()
        val restored = CalendarShellUiState.saver(today, DefaultColor, newStore) { CalendarUiState() }.restore(saved)!!
        assertNull(restored.creationSheet)
        restored.applyPendingRestore(loadedState)

        assertEquals(CreationSheet.EditEvent(meeting), restored.creationSheet)
        assertEquals(draftId, restored.editorDraftId)
        val fields = EditorDraftFields(newStore, draftId)
        assertEquals("Typed draft", fields.newState("title", DraftCodec.Text) { meeting.title }.value)
        assertEquals("From the item", fields.newState("location", DraftCodec.Text) { "From the item" }.value)
    }

    @Test
    fun savingOrClosingTheEditorDeletesItsDraftButRotationDoesNot() = runTest {
        val store = fileStore()
        val shell = CalendarShellUiState(initialEditorSchedule(today), DefaultColor, store)
        shell.openEventCreation(newEventSchedule(today, LocalTime.NOON, 60), DefaultColor)
        val draftId = shell.editorDraftId!!
        store.put(draftId, "title", "Typed")
        advanceUntilIdle()

        val rotated = saveAndRestore(shell, store)
        assertEquals(CreationSheet.EventFull, rotated.creationSheet)
        assertEquals(draftId, rotated.editorDraftId)
        assertEquals("Typed", store.values(draftId)["title"])
        assertEquals(listOf("$draftId.json"), draftFiles())

        rotated.finishCreation()
        advanceUntilIdle()
        assertNull(rotated.editorDraftId)
        assertTrue(store.values(draftId).isEmpty())
        assertTrue(draftFiles().isEmpty())

        rotated.openEventCreation(newEventSchedule(today, LocalTime.NOON, 60), DefaultColor)
        val discardedId = rotated.editorDraftId!!
        assertNotEquals(draftId, discardedId)
        store.put(discardedId, "title", "Thrown away")
        advanceUntilIdle()
        rotated.closeCreationSheet()
        advanceUntilIdle()
        assertTrue(store.values(discardedId).isEmpty())
        assertTrue(draftFiles().isEmpty())
    }

    @Test
    fun switchingEditorsStartsANewDraftThatHoldsTheTransferDraft() = runTest {
        val store = fileStore()
        val shell = CalendarShellUiState(initialEditorSchedule(today), DefaultColor, store)
        shell.openEventCreation(newEventSchedule(today, LocalTime.NOON, 60), DefaultColor)
        val eventDraftId = shell.editorDraftId!!
        val notes = "n".repeat(1_000_000)
        val transfer = EditorTransferDraft(title = "Typed", notes = notes, reminderMinutes = linkedSetOf(10, 0))

        shell.switchEditor(transfer, CreationSheet.Task, conversion = null, today = today)
        advanceUntilIdle()

        val taskDraftId = shell.editorDraftId!!
        assertNotEquals(eventDraftId, taskDraftId)
        assertTrue(store.values(eventDraftId).isEmpty())
        assertEquals(listOf("$taskDraftId.json"), draftFiles())

        // Process death: a new store reads the file; the Bundle only holds the draft ID.
        val saved = with(CalendarShellUiState.saver(today, DefaultColor, store) { loadedState }) {
            SaverScope { true }.save(shell)
        }!!
        assertFalse(saved.toString().contains(notes))
        val newStore = fileStore()
        val notLoaded = CalendarShellUiState.saver(today, DefaultColor, newStore) { CalendarUiState() }.restore(saved)!!
        assertTrue(notLoaded.hasPendingRestore)
        notLoaded.applyPendingRestore(loadedState)

        assertEquals(CreationSheet.Task, notLoaded.creationSheet)
        assertEquals(taskDraftId, notLoaded.editorDraftId)
        assertEquals(transfer, notLoaded.editorTransferDraft)
    }

    @Test
    fun aDraftWhoseEditorCannotComeBackIsDiscarded() = runTest {
        val store = fileStore()
        val shell = CalendarShellUiState(initialEditorSchedule(today), DefaultColor, store)
        shell.switchEditor(EditorTransferDraft(title = "x"), CreationSheet.Task, ConversionSource.Event(event("gone")), today)
        advanceUntilIdle()
        assertEquals(1, draftFiles().size)

        val restored = saveAndRestore(shell, fileStore())
        advanceUntilIdle()

        assertNull(restored.creationSheet)
        assertNull(restored.editorDraftId)
        assertTrue(draftFiles().isEmpty())
    }

    @Test
    fun staleDraftFilesAreDeleted() = runTest {
        val files = EditorDraftFiles(directory)
        files.write("old", mapOf("title" to "Old"))
        files.write("recent", mapOf("title" to "Recent"))
        val now = System.currentTimeMillis()
        File(directory, "old.json").setLastModified(now - EditorDraftStore.STALE_DRAFT_MAX_AGE_MILLIS - 60_000)

        fileStore().deleteStaleDrafts(nowMillis = now)
        advanceUntilIdle()

        assertEquals(listOf("recent.json"), draftFiles())
    }

    @Test
    fun aFailedWriteStaysPendingAndALaterFlushWritesIt() = runTest {
        var failWrites = true
        val files = EditorDraftFiles(directory, writeFile = { file, text ->
            if (failWrites) throw IOException("No space left on device")
            file.writeText(text)
        })
        val store = EditorDraftStore(files, ioScope = this)
        val draftId = store.newDraft()
        store.put(draftId, "title", "Typed")
        advanceUntilIdle()
        assertTrue(draftFiles().isEmpty())

        failWrites = false
        store.flush()
        advanceUntilIdle()

        assertEquals(listOf("$draftId.json"), draftFiles())
        assertEquals("Typed", fileStore().values(draftId)["title"])
    }

    @Test
    fun aNewerRevisionQueuedDuringAFailedWriteIsNotReplacedByTheRetry() = runTest {
        lateinit var store: EditorDraftStore
        lateinit var draftId: String
        var failWrites = true
        val files = EditorDraftFiles(directory, writeFile = { file, text ->
            if (failWrites) {
                failWrites = false
                store.put(draftId, "title", "Newer")
                throw IOException("No space left on device")
            }
            file.writeText(text)
        })
        store = EditorDraftStore(files, ioScope = this)
        draftId = store.newDraft()
        store.put(draftId, "title", "Older")
        advanceUntilIdle()

        store.flush()
        advanceUntilIdle()

        assertEquals("Newer", fileStore().values(draftId)["title"])
    }

    @Test
    fun aFailedRenameKeepsThePreviousFileIntact() {
        var failRename = false
        val files = EditorDraftFiles(directory, replaceFile = { source, target ->
            if (failRename || !source.renameTo(target)) throw IOException("rename failed")
        })
        assertTrue(files.write("draft", mapOf("title" to "Old")))

        failRename = true
        assertFalse(files.write("draft", mapOf("title" to "New")))

        assertEquals(mapOf("title" to "Old"), files.read("draft"))
        assertEquals(listOf("draft.json"), draftFiles())
    }

    @Test
    fun draftIdsCannotEscapeTheDraftDirectory() {
        val files = EditorDraftFiles(directory)
        files.write("../escape", mapOf("title" to "x"))

        assertFalse(File(folder.root, "escape.json").exists())
        assertNull(files.read("../escape"))
    }

    /** Draft files whose first write blocks until [release]. */
    private inner class BlockingFirstWrite {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        private val first = AtomicBoolean(true)
        val files = EditorDraftFiles(directory, writeFile = { file, text ->
            if (first.getAndSet(false)) {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
            file.writeText(text)
        })
    }

    private fun saveAndRestore(shell: CalendarShellUiState, store: EditorDraftStore): CalendarShellUiState {
        val saver = CalendarShellUiState.saver(today, DefaultColor, store) { loadedState }
        val saved = with(saver) { SaverScope { true }.save(shell) }!!
        return saver.restore(saved)!!
    }
}
