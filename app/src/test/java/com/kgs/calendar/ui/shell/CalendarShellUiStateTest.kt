package com.kgs.calendar.ui.shell

import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.TaskEditPayload
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.ConversionSource
import com.kgs.calendar.ui.CreationSheet
import com.kgs.calendar.ui.DetailSheet
import com.kgs.calendar.ui.EditorTransferDraft
import com.kgs.calendar.ui.HiddenSaveKind
import com.kgs.calendar.ui.HiddenSaveNotice
import com.kgs.calendar.ui.RecurringSaveRequest
import com.kgs.calendar.ui.SettingsDestination
import com.kgs.calendar.ui.SheetSnap
import com.kgs.calendar.ui.editor.EditorSchedulePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CalendarShellUiStateTest {
    private val today = LocalDate.of(2026, 9, 23)
    private val shell = CalendarShellUiState(initialEditorSchedule(today), DefaultColor)

    @Test
    fun openingTheEventEditorClosesEveryOtherOverlay() {
        shell.openSearch()
        shell.openProblems()
        shell.openSettings(SettingsDestination.Design)
        shell.editCollection(collection("work"))
        shell.openTaskDetail(task("parent"))
        shell.openSubtask(task("parent"), task("child"))
        val schedule = newEventSchedule(today, LocalTime.of(10, 5), 45)

        shell.openEventCreation(schedule, wireframeColor = 7)

        assertEquals(CreationSheet.EventFull, shell.creationSheet)
        assertEquals(schedule, shell.editorSchedule)
        assertEquals(7, shell.draftWireframeColor)
        assertFalse(shell.searchOpen)
        assertFalse(shell.problemsOpen)
        assertFalse(shell.settingsOpen)
        assertNull(shell.editingCollection)
        assertNull(shell.detailSheet)
        assertTrue(shell.detailTaskBackStack.isEmpty())
        assertNull(shell.editorTransferDraft)
        assertNull(shell.conversionSource)
    }

    @Test
    fun backClosesTheTopmostOverlayBeforeWalkingTheViewHistory() {
        val selectedViews = mutableListOf<CalendarViewMode>()
        var searchClosed = 0
        fun back() = shell.navigateBack(closeSearch = { searchClosed++; shell.closeSearch() }, selectView = { selectedViews += it })

        shell.recordViewChange(CalendarViewMode.ThreeDay)
        shell.recordViewChange(CalendarViewMode.ThreeDay)
        shell.recordViewChange(CalendarViewMode.Month)
        shell.openSettings(SettingsDestination.Main)
        shell.openSearch()
        shell.setCreateMenuExpanded(true)
        assertTrue(shell.canNavigateBack)

        back()
        assertFalse(shell.createMenuOpen)
        assertTrue(shell.searchOpen)
        back()
        assertEquals(1, searchClosed)
        assertTrue(shell.settingsOpen)
        back()
        assertFalse(shell.settingsOpen)
        assertFalse(shell.anyOverlayOpen)
        back()
        back()
        assertEquals(listOf(CalendarViewMode.Month, CalendarViewMode.ThreeDay), selectedViews)
        assertFalse(shell.canNavigateBack)
    }

    @Test
    fun subtaskNavigationKeepsABackStackAndMorphsOnEveryStep() {
        val root = task("root")
        val child = task("child")
        val grandchild = task("grandchild")
        shell.openTaskDetail(root)

        shell.openSubtask(root, child)
        shell.openSubtask(child, grandchild)
        assertEquals(DetailSheet.Task(grandchild), shell.detailSheet)
        assertEquals(listOf(root, child), shell.detailTaskBackStack)
        assertEquals(2, shell.detailTaskMorphGeneration)

        shell.navigateDetailBack()
        assertEquals(DetailSheet.Task(child), shell.detailSheet)
        assertEquals("grandchild.ics", shell.detailTaskMorphSourceHref)
        assertEquals(3, shell.detailTaskMorphGeneration)

        shell.openSubtask(child, grandchild)
        shell.openParentTask(root)
        assertEquals(DetailSheet.Task(root), shell.detailSheet)
        assertTrue(shell.detailTaskBackStack.isEmpty())

        shell.navigateDetailBack()
        assertNull(shell.detailSheet)
        shell.onDetailSheetClosed()
        assertEquals(0, shell.detailTaskMorphGeneration)
    }

    @Test
    fun aLaunchedDetailOpensOnACleanShell() {
        shell.openTaskDrawer()
        shell.openTaskCreation(newSubtaskSchedule(today, LocalTime.NOON), wireframeColor = 1)
        shell.openTaskDetail(task("parent"))
        shell.openSubtask(task("parent"), task("child"))

        shell.openLaunchedDetail(DetailSheet.Event(event("launched")))

        assertEquals(DetailSheet.Event(event("launched")), shell.detailSheet)
        assertNull(shell.creationSheet)
        assertFalse(shell.taskDrawerOpen)
        assertTrue(shell.detailTaskBackStack.isEmpty())
        assertEquals(0, shell.detailTaskMorphGeneration)
    }

    @Test
    fun timelineSlotsStartTheLowDraftAndCollapseAnAlreadyOpenSheet() {
        val first = EditorSchedulePreview(today, LocalTime.of(9, 0), LocalTime.of(10, 0))
        shell.selectDraftSlot(first, wireframeColor = 3)
        assertEquals(CreationSheet.EventLow, shell.creationSheet)
        assertEquals(0, shell.creationCollapseRequest)
        assertTrue(shell.editorWireframeMode)
        assertEquals(first, shell.draftEventSelection()?.let { EditorSchedulePreview(it.date, it.start, it.end, it.allDay) })
        assertEquals(3, shell.draftEventSelection()?.color)

        shell.selectDraftSlot(allDaySlotDraftPreview(today.plusDays(1)), wireframeColor = 3)
        assertEquals(1, shell.creationCollapseRequest)
        assertTrue(shell.draftEventSelection()!!.allDay)

        shell.onEditorSnapChanged(SheetSnap.Half)
        assertFalse(shell.editorWireframeMode)
        shell.closeCreationSheet()
        assertNull(shell.draftEventSelection())
    }

    @Test
    fun switchingEditorsCarriesTheDraftScheduleAndConversionSource() {
        val source = event("source")
        shell.editEvent(source, source.editorSchedule())
        val transfer = EditorTransferDraft(
            title = "Typed",
            date = today,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(9, 0),
        )

        shell.switchEditor(transfer, CreationSheet.Task, ConversionSource.Event(source), today)

        assertEquals(CreationSheet.Task, shell.creationSheet)
        assertEquals(transfer, shell.editorTransferDraft)
        assertEquals(ConversionSource.Event(source), shell.conversionSource)
        assertEquals(LocalTime.of(8, 0), shell.editorSchedule.lastValidPreview?.start)
        assertTrue(shell.draftEventSelection() != null)

        shell.closeCreationSheet()
        shell.onCreationSheetClosed()
        assertNull(shell.conversionSource)
    }

    @Test
    fun theCreateButtonFirstFoldsTheOverdueTasks() {
        shell.overdueTasksExpanded = true
        shell.setCreateMenuExpanded(true)
        assertFalse(shell.overdueTasksExpanded)
        assertFalse(shell.createMenuOpen)
        shell.setCreateMenuExpanded(true)
        assertTrue(shell.createMenuOpen)
    }

    @Test
    fun hiddenSaveNoticeOnlyForHiddenCalendars() {
        val state = CalendarUiState(
            defaultTaskCollectionHref = "hidden",
            hiddenCollectionHrefs = setOf("hidden"),
        )
        shell.showHiddenSaveNotice("visible", HiddenSaveKind.Event, state)
        assertNull(shell.hiddenSaveNotice)
        shell.showHiddenSaveNotice(null, HiddenSaveKind.Task, state)
        assertEquals(HiddenSaveNotice("hidden", HiddenSaveKind.Task), shell.hiddenSaveNotice)
    }

    @Test
    fun recurringSaveKeepsTheEditorOpenUntilAScopeIsChosen() {
        val recurring = task("recurring")
        shell.editTask(recurring, recurring.editorSchedule(today))
        shell.requestRecurringSave(
            RecurringSaveRequest.Task(recurring, taskPayload()),
        )
        assertEquals(CreationSheet.EditTask(recurring), shell.creationSheet)
        shell.finishRecurringSave()
        assertNull(shell.recurringSaveRequest)
        assertNull(shell.creationSheet)
    }

    @Test
    fun addCalendarSourcesOpensSettingsOnTheAddPage() {
        shell.openTaskCreation(newSubtaskSchedule(today, LocalTime.NOON), wireframeColor = 1)
        shell.openAddCalendarSources()
        assertNull(shell.creationSheet)
        assertTrue(shell.settingsOpen)
        assertEquals(SettingsDestination.AddSource, shell.settingsStartDestination)
    }

    private fun taskPayload() = TaskEditPayload(
        title = "Task",
        collectionHref = null,
        notes = null,
        location = null,
        locationMapVerified = null,
        manualColor = null,
        url = null,
        categories = null,
        startDate = null,
        startTime = null,
        startHasTime = false,
        dueDate = null,
        dueTime = null,
        dueHasTime = false,
        priority = null,
        percentComplete = null,
        isCompleted = false,
        recurrenceRule = "FREQ=DAILY",
    )

    companion object {
        const val DefaultColor = 0xFF8A5A44.toInt()

        fun collection(href: String) = CollectionEntity(
            href = href,
            accountId = "account",
            displayName = href,
            color = 1,
            supportsEvents = true,
            supportsTasks = true,
            syncToken = null,
            ctag = null,
        )

        fun event(name: String, startsAt: Long = 1_790_000_000_000L) = EventEntity(
            uid = name,
            collectionHref = "work",
            resourceHref = "$name.ics",
            title = name,
            description = null,
            location = null,
            startsAtMillis = startsAt,
            endsAtMillis = startsAt + 3_600_000L,
            allDay = false,
            recurrenceRule = null,
            isRecurring = false,
            color = 1,
        )

        fun task(name: String, startAt: Long? = null, recurrenceRule: String? = null) = TaskEntity(
            uid = name,
            collectionHref = "work",
            resourceHref = "$name.ics",
            title = name,
            notes = null,
            dueAtMillis = null,
            startAtMillis = startAt,
            completedAtMillis = null,
            isCompleted = false,
            priority = null,
            recurrenceRule = recurrenceRule,
            color = 1,
        )
    }
}
