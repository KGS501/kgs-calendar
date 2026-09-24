package com.kgs.calendar.ui.editor

import android.os.Parcel
import androidx.compose.runtime.saveable.SaverScope
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.ConversionSource
import com.kgs.calendar.ui.CreationSheet
import com.kgs.calendar.ui.EditorTransferDraft
import com.kgs.calendar.ui.shell.CalendarShellUiState
import com.kgs.calendar.ui.shell.CalendarShellUiStateTest.Companion.DefaultColor
import com.kgs.calendar.ui.shell.CalendarShellUiStateTest.Companion.collection
import com.kgs.calendar.ui.shell.CalendarShellUiStateTest.Companion.event
import com.kgs.calendar.ui.shell.SavedShellState
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
import kotlinx.coroutines.test.runCurrent
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
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

    private fun fileDraft(draftId: String): DraftRevision? = EditorDraftFiles(directory).read(draftId)

    /** A store after process death that has read the files of [draftIds]. */
    private fun TestScope.restoredStore(vararg draftIds: String): EditorDraftStore =
        fileStore().also {
            it.onShellRestored(draftIds.toSet())
            advanceUntilIdle()
        }

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

        val restored = restoredStore(draftId).values(draftId)

        assertEquals(description, restored["description"])
        assertTrue(restored.containsKey("manualColor"))
        assertNull(restored["manualColor"])
        assertEquals(3, DraftCodec.Number.restore(restored["priority"]))
        assertEquals(true, restored["isCompleted"])
        assertEquals(listOf(30, 0, 5), DraftCodec.MinuteSet.restore(restored["reminderMinutes"]).toList())
        assertEquals(mapOf("title" to "x", "schedule" to mapOf("allDay" to false), "tags" to listOf("a")), restored["transferDraft"])
    }

    @Test
    fun writesAreBatchedAndFlushSchedulesTheWriteOfTheLatestRevisionAtOnce() = runTest {
        val store = fileStore()
        val draftId = store.newDraft()
        "Typing".forEachIndexed { index, _ -> store.put(draftId, "title", "Typing".take(index + 1)) }

        store.flush()
        // flush() only schedules the write; nothing touches the file before the IO dispatcher runs.
        assertTrue(draftFiles().isEmpty())
        runCurrent()

        assertEquals(listOf("$draftId.json"), draftFiles())
        assertEquals("Typing", fileDraft(draftId)!!.values["title"])
        assertEquals(6L, fileDraft(draftId)!!.revision)
    }

    @Test
    fun flushNeverWritesOnTheCallingThreadNorWaitsForAWriteInFlight() = runBlocking {
        val blocked = BlockingFirstWrite()
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = EditorDraftStore(blocked.files, ioScope, writeDelayMillis = 0)
        try {
            val draftId = store.newDraft()
            store.put(draftId, "title", "Older")
            assertTrue(blocked.started.await(5, TimeUnit.SECONDS))
            store.put(draftId, "title", "Newer")

            // The write in flight holds the file lock for up to five seconds; flush() still returns at once.
            val flushStarted = System.nanoTime()
            store.flush()
            assertTrue(System.nanoTime() - flushStarted < TimeUnit.SECONDS.toNanos(1))
            blocked.release.countDown()
            withTimeout(5_000) { ioScope.coroutineContext.job.children.forEach { it.join() } }

            assertEquals("Newer", fileDraft(draftId)!!.values["title"])
            assertTrue(blocked.writeThreads.isNotEmpty())
            assertFalse(Thread.currentThread() in blocked.writeThreads)
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

            assertEquals("Newer", fileDraft(draftId)!!.values["title"])
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
        assertEquals("Written", restoredStore(draftId).values(draftId)["title"])
        assertTrue(restoredStore("unknown-draft").values("unknown-draft").isEmpty())
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
        newStore.onShellRestored(restored.referencedDraftIds)
        advanceUntilIdle()
        restored.applyPendingRestore(loadedState)

        assertEquals(CreationSheet.EditEvent(meeting), restored.creationSheet)
        assertEquals(draftId, restored.editorDraftId)
        val fields = EditorDraftFields(newStore, draftId)
        assertEquals("Typed draft", fields.newState("title", DraftCodec.Text) { meeting.title }.value)
        assertEquals("From the item", fields.newState("location", DraftCodec.Text) { "From the item" }.value)
    }

    @Test
    fun aSmallDraftRoundTripsThroughTheBundleAloneWithoutAFile() = runTest {
        // Without files, e.g. when the process died before any write completed.
        val store = EditorDraftStore()
        val shell = CalendarShellUiState(initialEditorSchedule(today), DefaultColor, store)
        val meeting = event("meeting")
        shell.editEvent(meeting, meeting.editorSchedule())
        val draftId = shell.editorDraftId!!
        store.put(draftId, "title", "Typed draft")
        store.put(draftId, "manualColor", null)
        store.put(draftId, "reminderMinutes", DraftCodec.MinuteSet.save(linkedSetOf(30, 0)))

        val saved = parcelRoundTrip(saveShell(shell, store))
        val newStore = fileStore()
        val restored = CalendarShellUiState.saver(today, DefaultColor, newStore) { CalendarUiState() }.restore(saved)!!
        newStore.onShellRestored(restored.referencedDraftIds)
        advanceUntilIdle()
        restored.applyPendingRestore(loadedState)

        assertTrue(draftFiles().isEmpty())
        assertEquals(draftId, restored.editorDraftId)
        val values = newStore.values(draftId)
        assertEquals("Typed draft", values["title"])
        assertTrue(values.containsKey("manualColor"))
        assertEquals(listOf(30, 0), DraftCodec.MinuteSet.restore(values["reminderMinutes"]).toList())
        // Later changes are numbered above the restored revision.
        newStore.put(draftId, "title", "Edited")
        advanceUntilIdle()
        assertEquals(4L, fileDraft(draftId)!!.revision)
    }

    @Test
    fun aLargeDraftKeepsOnlyItsIdAndRevisionInTheBundleAndRestoresFromTheFile() = runTest {
        val description = "d".repeat(EditorDraftStore.BUNDLE_DRAFT_MAX_CHARS)
        val store = fileStore()
        val shell = CalendarShellUiState(initialEditorSchedule(today), DefaultColor, store)
        val meeting = event("meeting")
        shell.editEvent(meeting, meeting.editorSchedule())
        val draftId = shell.editorDraftId!!
        store.put(draftId, "title", "Typed draft")
        store.put(draftId, "description", description)
        advanceUntilIdle()

        val saved = parcelRoundTrip(saveShell(shell, store))
        assertFalse(saved.toString().contains(description))
        assertEquals(SavedDraft(2, null), SavedShellState.fromSaveable(saved)!!.editorDraft)
        val restored = restoreAfterProcessDeath(saved)

        assertEquals(draftId, restored.shell.editorDraftId)
        assertEquals("Typed draft", restored.store.values(draftId)["title"])
        assertEquals(description, restored.store.values(draftId)["description"])
    }

    @Test
    fun aNewerBundleWinsOverAnOlderFile() = runTest {
        val store = fileStore()
        val shell = CalendarShellUiState(initialEditorSchedule(today), DefaultColor, store)
        shell.openEventCreation(newEventSchedule(today, LocalTime.NOON, 60), DefaultColor)
        val draftId = shell.editorDraftId!!
        store.put(draftId, "title", "Written")
        advanceUntilIdle()
        // The process dies before this revision reaches the file.
        store.put(draftId, "title", "Only in the Bundle")
        val saved = parcelRoundTrip(saveShell(shell, store))
        assertEquals(1L, fileDraft(draftId)!!.revision)

        val restored = restoreAfterProcessDeath(saved)

        assertEquals("Only in the Bundle", restored.store.values(draftId)["title"])
    }

    @Test
    fun aNewerFileWinsOverAnOlderBundle() = runTest {
        val store = fileStore()
        val shell = CalendarShellUiState(initialEditorSchedule(today), DefaultColor, store)
        shell.openEventCreation(newEventSchedule(today, LocalTime.NOON, 60), DefaultColor)
        val draftId = shell.editorDraftId!!
        store.put(draftId, "title", "In the Bundle")
        val saved = parcelRoundTrip(saveShell(shell, store))
        // The user keeps typing after the state was saved, e.g. in multi-window mode.
        store.put(draftId, "title", "Written later")
        advanceUntilIdle()
        assertEquals(2L, fileDraft(draftId)!!.revision)

        val restored = restoreAfterProcessDeath(saved)

        assertEquals("Written later", restored.store.values(draftId)["title"])
    }

    @Test
    fun aDraftFileWithoutRevisionIsReadAsRevisionZero() = runTest {
        directory.mkdirs()
        File(directory, "legacy.json").writeText("""{"values":{"title":"Legacy","manualColor":null}}""")

        val draft = fileDraft("legacy")!!
        assertEquals(0L, draft.revision)
        assertEquals(mapOf("title" to "Legacy", "manualColor" to null), draft.values)

        val store = restoredStore("legacy")
        assertEquals("Legacy", store.values("legacy")["title"])
        store.put("legacy", "title", "Edited")
        advanceUntilIdle()
        assertEquals(1L, fileDraft("legacy")!!.revision)
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

        // Process death: a new store reads the file; the Bundle only holds the draft ID and revision.
        val saved = with(CalendarShellUiState.saver(today, DefaultColor, store) { loadedState }) {
            SaverScope { true }.save(shell)
        }!!
        assertFalse(saved.toString().contains(notes))
        val newStore = fileStore()
        val notLoaded = CalendarShellUiState.saver(today, DefaultColor, newStore) { CalendarUiState() }.restore(saved)!!
        assertTrue(notLoaded.hasPendingRestore)
        newStore.onShellRestored(notLoaded.referencedDraftIds)
        advanceUntilIdle()
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
    fun unreferencedStaleDraftFilesAreDeletedOnceTheShellRestored() = runTest {
        val files = EditorDraftFiles(directory)
        files.write("old", DraftRevision(1, mapOf("title" to "Old")))
        files.write("recent", DraftRevision(1, mapOf("title" to "Recent")))
        File(directory, "leftover.json.tmp").writeText("{")
        val now = System.currentTimeMillis()
        File(directory, "old.json").setLastModified(now - TEN_DAYS_MILLIS)
        File(directory, "leftover.json.tmp").setLastModified(now - TEN_DAYS_MILLIS)
        val store = fileStore()
        advanceUntilIdle()
        assertEquals(listOf("leftover.json.tmp", "old.json", "recent.json"), draftFiles())

        store.onShellRestored(referencedDraftIds = emptySet(), nowMillis = now)
        advanceUntilIdle()

        assertEquals(listOf("recent.json"), draftFiles())
    }

    @Test
    fun aTenDayOldDraftReferencedByTheRestoredStateSurvivesCleanUpAndRestores() = runTest {
        val store = fileStore()
        val shell = CalendarShellUiState(initialEditorSchedule(today), DefaultColor, store)
        val meeting = event("meeting")
        shell.editEvent(meeting, meeting.editorSchedule())
        val draftId = shell.editorDraftId!!
        store.put(draftId, "title", "Typed ten days ago")
        store.flush()
        runCurrent()
        val saved = with(CalendarShellUiState.saver(today, DefaultColor, store) { loadedState }) {
            SaverScope { true }.save(shell)
        }!!
        val now = System.currentTimeMillis()
        File(directory, "$draftId.json").setLastModified(now - TEN_DAYS_MILLIS)

        // Process death: the data has not loaded yet when the shell comes back.
        val newStore = fileStore()
        val restored = CalendarShellUiState.saver(today, DefaultColor, newStore) { CalendarUiState() }.restore(saved)!!
        assertTrue(restored.hasPendingRestore)
        newStore.onShellRestored(restored.referencedDraftIds, nowMillis = now)
        advanceUntilIdle()
        restored.applyPendingRestore(loadedState)

        assertEquals(listOf("$draftId.json"), draftFiles())
        assertEquals(draftId, restored.editorDraftId)
        assertEquals("Typed ten days ago", newStore.values(draftId)["title"])
    }

    @Test
    fun referencedDraftsAreReadOnIoBeforeTheRestoredEditorAsksForThem() = runTest {
        EditorDraftFiles(directory).write("restored", DraftRevision(1, mapOf("title" to "From the file")))
        val store = fileStore()

        store.onShellRestored(referencedDraftIds = setOf("restored"))
        advanceUntilIdle()
        File(directory, "restored.json").delete()

        assertEquals("From the file", store.values("restored")["title"])
    }

    @Test
    fun cleanUpRacingAWriteCannotDeleteTheFreshRevision() = runBlocking {
        EditorDraftFiles(directory).write("reopened", DraftRevision(1, mapOf("title" to "Old")))
        val now = System.currentTimeMillis()
        File(directory, "reopened.json").setLastModified(now - TEN_DAYS_MILLIS)
        val blocked = BlockingFirstWrite()
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = EditorDraftStore(blocked.files, ioScope, writeDelayMillis = 0)
        try {
            // Another draft's write is running, so the clean-up has to wait for it.
            val other = store.newDraft()
            store.put(other, "title", "Other")
            assertTrue(blocked.started.await(5, TimeUnit.SECONDS))
            store.onShellRestored(referencedDraftIds = emptySet(), nowMillis = now)
            // The old file gets a fresh revision while the clean-up waits.
            store.put("reopened", "title", "Fresh")

            blocked.release.countDown()
            withTimeout(5_000) { ioScope.coroutineContext.job.children.forEach { it.join() } }

            assertEquals(listOf("$other.json", "reopened.json"), draftFiles().sorted())
            assertEquals("Fresh", fileDraft("reopened")!!.values["title"])
        } finally {
            blocked.release.countDown()
            ioScope.cancel()
        }
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
        assertEquals("Typed", fileDraft(draftId)!!.values["title"])
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

        assertEquals("Newer", fileDraft(draftId)!!.values["title"])
        assertEquals(2L, fileDraft(draftId)!!.revision)
    }

    @Test
    fun aFailedRenameKeepsThePreviousFileIntact() {
        var failRename = false
        val files = EditorDraftFiles(directory, replaceFile = { source, target ->
            if (failRename || !source.renameTo(target)) throw IOException("rename failed")
        })
        assertTrue(files.write("draft", DraftRevision(1, mapOf("title" to "Old"))))

        failRename = true
        assertFalse(files.write("draft", DraftRevision(2, mapOf("title" to "New"))))

        assertEquals(mapOf("title" to "Old"), files.read("draft")!!.values)
        assertEquals(1L, files.read("draft")!!.revision)
        assertEquals(listOf("draft.json"), draftFiles())
    }

    @Test
    fun draftIdsCannotEscapeTheDraftDirectory() {
        val files = EditorDraftFiles(directory)
        files.write("../escape", DraftRevision(1, mapOf("title" to "x")))

        assertFalse(File(folder.root, "escape.json").exists())
        assertNull(files.read("../escape"))
    }

    private companion object {
        const val TEN_DAYS_MILLIS = 10L * 24 * 60 * 60 * 1000
    }

    /** Draft files whose first write blocks until [release]; they record the threads that wrote. */
    private inner class BlockingFirstWrite {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writeThreads: MutableSet<Thread> = Collections.synchronizedSet(HashSet())
        private val first = AtomicBoolean(true)
        val files = EditorDraftFiles(directory, writeFile = { file, text ->
            writeThreads += Thread.currentThread()
            if (first.getAndSet(false)) {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
            file.writeText(text)
        })
    }

    private class Restored(val shell: CalendarShellUiState, val store: EditorDraftStore)

    private fun saveShell(shell: CalendarShellUiState, store: EditorDraftStore): Any =
        with(CalendarShellUiState.saver(today, DefaultColor, store) { loadedState }) { SaverScope { true }.save(shell) }!!

    /** Restores [saved] into a new store the way the app does after process death. */
    private fun TestScope.restoreAfterProcessDeath(saved: Any): Restored {
        val store = fileStore()
        val shell = CalendarShellUiState.saver(today, DefaultColor, store) { CalendarUiState() }.restore(saved)!!
        assertTrue(shell.hasPendingRestore)
        store.onShellRestored(shell.referencedDraftIds)
        advanceUntilIdle()
        shell.applyPendingRestore(loadedState)
        return Restored(shell, store)
    }

    private fun parcelRoundTrip(value: Any): Any {
        val parcel = Parcel.obtain()
        try {
            parcel.writeValue(value)
            parcel.setDataPosition(0)
            return parcel.readValue(javaClass.classLoader)!!
        } finally {
            parcel.recycle()
        }
    }

    private fun saveAndRestore(shell: CalendarShellUiState, store: EditorDraftStore): CalendarShellUiState {
        val saver = CalendarShellUiState.saver(today, DefaultColor, store) { loadedState }
        val saved = with(saver) { SaverScope { true }.save(shell) }!!
        return saver.restore(saved)!!
    }
}
