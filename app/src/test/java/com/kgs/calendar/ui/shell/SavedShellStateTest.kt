package com.kgs.calendar.ui.shell

import android.os.Parcel
import androidx.compose.runtime.saveable.SaverScope
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.ConversionSource
import com.kgs.calendar.ui.CreationSheet
import com.kgs.calendar.ui.DetailSheet
import com.kgs.calendar.ui.EditorTransferDraft
import com.kgs.calendar.ui.HiddenSaveKind
import com.kgs.calendar.ui.HiddenSaveNotice
import com.kgs.calendar.ui.ReminderMinutesSaver
import com.kgs.calendar.ui.SettingsDestination
import com.kgs.calendar.ui.shell.CalendarShellUiStateTest.Companion.DefaultColor
import com.kgs.calendar.ui.shell.CalendarShellUiStateTest.Companion.collection
import com.kgs.calendar.ui.shell.CalendarShellUiStateTest.Companion.event
import com.kgs.calendar.ui.shell.CalendarShellUiStateTest.Companion.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class SavedShellStateTest {
    private val today = LocalDate.of(2026, 9, 23)
    private val saverScope = SaverScope { true }

    private val occurrenceStart = 1_790_000_000_000L
    private val recurringOccurrence = task("daily", startAt = occurrenceStart, recurrenceRule = "FREQ=DAILY")
    private val laterOccurrence = recurringOccurrence.copy(startAtMillis = occurrenceStart + 86_400_000L)
    private val loadedState = CalendarUiState(
        initialDataLoaded = true,
        collections = listOf(collection("work")),
        events = listOf(event("meeting"), event("lunch")),
        datedTasks = listOf(laterOccurrence, recurringOccurrence),
        inboxTasks = listOf(task("inbox"), task("parent")),
    )

    @Test
    fun fullStateSurvivesAParcelRoundTrip() {
        val saved = SavedShellState(
            createMenuOpen = true,
            overdueTasksExpanded = true,
            searchOpen = true,
            drawerOpen = true,
            taskDrawerOpen = true,
            completedTasksOpen = true,
            settingsOpen = true,
            settingsStartDestination = SettingsDestination.AddSource,
            problemsOpen = true,
            editingCollectionHref = "work",
            creationSheet = SavedCreationSheet(SavedCreationSheet.Kind.EditTask, SavedItemRef.Task("daily.ics", occurrenceStart)),
            detailSheet = SavedItemRef.Event("meeting.ics", occurrenceStart),
            detailTaskBackStack = listOf(SavedItemRef.Task("inbox.ics", null), SavedItemRef.Task("daily.ics", occurrenceStart)),
            editorSchedule = newTaskSchedule(today, LocalTime.of(9, 10), false, true, true, true, 30),
            draftWireframeColor = DefaultColor,
            editorWireframeMode = true,
            editorTransferDraft = EditorTransferDraft(
                title = "Typed title",
                notes = "Notes",
                location = "Somewhere",
                locationMapVerified = true,
                manualColor = 0x112233,
                categories = "a,b",
                recurrenceRule = "FREQ=WEEKLY",
                reminderMinutes = linkedSetOf(15, 0, 60),
                sourceDefaultReminderMinutes = setOf(10),
                date = today,
                endDate = today.plusDays(1),
                startTime = LocalTime.of(8, 30),
                endTime = null,
                allDay = false,
                schedule = allDayScheduleFor(today),
            ),
            conversionSource = SavedItemRef.Event("lunch.ics", occurrenceStart),
            hiddenSaveNotice = HiddenSaveNotice("work", HiddenSaveKind.Task),
            viewHistory = listOf(CalendarViewMode.Month, CalendarViewMode.Day),
        )

        val saveable = saved.toSaveable()
        assertBundleSafe(saveable)

        assertEquals(saved, SavedShellState.fromSaveable(parcelRoundTrip(saveable)))
    }

    @Test
    fun rotationReopensTheEditorWithItsDraft() {
        val original = CalendarShellUiState(initialEditorSchedule(today), DefaultColor)
        original.editTask(recurringOccurrence, recurringOccurrence.editorSchedule(today))
        original.editorSchedule = original.editorSchedule.copy(startTimeText = "07:4").recalculatePreview()
        original.draftWireframeColor = 42
        original.recordViewChange(CalendarViewMode.Month)

        val restored = saveAndRestore(original, loadedState)

        assertEquals(CreationSheet.EditTask(recurringOccurrence), restored.creationSheet)
        assertEquals(original.editorSchedule, restored.editorSchedule)
        assertEquals(42, restored.draftWireframeColor)
        assertFalse(restored.hasPendingRestore)
        assertTrue(restored.canNavigateBack)
    }

    @Test
    fun detailSheetAndTaskBackStackResolveAgainstTheLoadedData() {
        val original = CalendarShellUiState(initialEditorSchedule(today), DefaultColor)
        val parent = task("parent")
        original.openTaskDetail(parent)
        original.openSubtask(parent, recurringOccurrence)
        original.openSettings(SettingsDestination.Widgets)
        original.editCollection(collection("work"))

        val restored = saveAndRestore(original, loadedState)

        assertEquals(DetailSheet.Task(recurringOccurrence), restored.detailSheet)
        assertEquals(listOf(parent), restored.detailTaskBackStack)
        assertEquals(0, restored.detailTaskMorphGeneration)
        assertTrue(restored.settingsOpen)
        assertEquals(SettingsDestination.Widgets, restored.settingsStartDestination)
        assertEquals(collection("work"), restored.editingCollection)
    }

    @Test
    fun anEditorWhoseItemIsGoneStaysClosed() {
        val original = CalendarShellUiState(initialEditorSchedule(today), DefaultColor)
        original.editEvent(event("deleted"), event("deleted").editorSchedule())
        original.openSearch()

        val restored = saveAndRestore(original, loadedState)

        assertNull(restored.creationSheet)
        assertTrue(restored.searchOpen)
    }

    @Test
    fun aConversionWhoseSourceIsGoneDropsTheEditor() {
        val original = CalendarShellUiState(initialEditorSchedule(today), DefaultColor)
        val gone = event("gone")
        original.switchEditor(EditorTransferDraft(title = "x"), CreationSheet.Task, ConversionSource.Event(gone), today)

        assertNull(saveAndRestore(original, loadedState).creationSheet)

        original.switchEditor(EditorTransferDraft(title = "x"), CreationSheet.Task, ConversionSource.Event(event("meeting")), today)
        val restored = saveAndRestore(original, loadedState)
        assertEquals(CreationSheet.Task, restored.creationSheet)
        assertEquals(ConversionSource.Event(event("meeting")), restored.conversionSource)
        assertEquals(EditorTransferDraft(title = "x"), restored.editorTransferDraft)
    }

    @Test
    fun beforeTheFirstDataLoadTheRestoreWaits() {
        val original = CalendarShellUiState(initialEditorSchedule(today), DefaultColor)
        original.openTaskDetail(task("inbox"))
        original.openTaskDrawer()
        val notLoaded = CalendarUiState()

        val restored = saveAndRestore(original, notLoaded)
        assertTrue(restored.hasPendingRestore)
        assertNull(restored.detailSheet)
        assertFalse(restored.canResolvePendingRestore(notLoaded))
        assertFalse(restored.canResolvePendingRestore(loadedState.copy(inboxTasks = emptyList())))
        // Saving again while waiting keeps what was restored.
        assertEquals(original.snapshot(), restored.snapshot())

        assertTrue(restored.canResolvePendingRestore(loadedState))
        restored.applyPendingRestore(loadedState)
        assertFalse(restored.hasPendingRestore)
        assertEquals(DetailSheet.Task(task("inbox")), restored.detailSheet)
        assertTrue(restored.taskDrawerOpen)
    }

    @Test
    fun taskRefsOnlyPinTheOccurrenceOfRecurringTasks() {
        assertNull(task("inbox").savedRef().occurrenceStart)
        assertEquals(occurrenceStart, recurringOccurrence.savedRef().occurrenceStart)
        assertEquals(laterOccurrence, loadedState.findTask(laterOccurrence.savedRef()))
        assertEquals(event("lunch"), loadedState.findEvent(event("lunch").savedRef()))
        assertNull(loadedState.findEvent(SavedItemRef.Event("lunch.ics", 0L)))
    }

    @Test
    fun reminderMinutesKeepTheirOrder() {
        val saved = with(ReminderMinutesSaver) { saverScope.save(linkedSetOf(30, 0, 5)) }!!
        assertEquals(listOf(30, 0, 5), ReminderMinutesSaver.restore(parcelRoundTrip(saved))!!.toList())
    }

    private fun saveAndRestore(shell: CalendarShellUiState, state: CalendarUiState): CalendarShellUiState {
        val saver = CalendarShellUiState.saver(today, DefaultColor) { state }
        val saved = with(saver) { saverScope.save(shell) }!!
        return saver.restore(parcelRoundTrip(saved))!!
    }

    private fun allDayScheduleFor(date: LocalDate) =
        editorScheduleState(date = date, start = LocalTime.MIDNIGHT, end = LocalTime.of(23, 59), allDay = true)

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

    private fun assertBundleSafe(value: Any?) {
        when (value) {
            null, is String, is Boolean, is Int, is Long -> Unit
            is Map<*, *> -> value.forEach { (key, item) ->
                assertTrue("$key", key is String)
                assertBundleSafe(item)
            }
            is List<*> -> value.forEach(::assertBundleSafe)
            else -> throw AssertionError("${value::class} is not a plain saved-state value")
        }
    }
}
