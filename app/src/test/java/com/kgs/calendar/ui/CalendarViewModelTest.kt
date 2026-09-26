package com.kgs.calendar.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.kgs.calendar.data.LOCAL_COLLECTION
import com.kgs.calendar.data.RepositoryHarness
import com.kgs.calendar.data.TEST_ZONE
import com.kgs.calendar.data.eventPayload
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.recurrence.occurrenceAt
import com.kgs.calendar.data.taskPayload
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.data.settings.WidgetTaskSortMode
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.model.CalendarRange
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.CalendarWindowLayout
import com.kgs.calendar.domain.model.visibleRangeFor
import com.kgs.calendar.navigation.CalendarLaunchAction
import com.kgs.calendar.navigation.CalendarLaunchResolver
import com.kgs.calendar.navigation.CalendarLaunchTarget
import com.kgs.calendar.navigation.SharedEventDraft
import com.kgs.calendar.reminder.TaskMutationCoordinator
import com.kgs.calendar.reminder.TaskNotificationReconciler
import com.kgs.calendar.sync.SourceCalendarMutationCoordinator
import com.kgs.calendar.ui.model.orderedOverdueTasks
import com.kgs.calendar.ui.timeline.TimelineOrientationViewportMemory
import com.kgs.calendar.widget.KgsWidgetKind
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CalendarViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var harness: RepositoryHarness
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var settingsStore: SettingsStore
    private val widgetRefresher = RecordingWidgetRefresher()
    private val lifecycleSignals = FakeAppLifecycleSignals()
    private val reminderReschedules = MutableStateFlow(0)
    private val viewModels = mutableListOf<CalendarViewModel>()
    private val today = LocalDate.now(TEST_ZONE)

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        harness = RepositoryHarness()
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) {
                File(tempFolder.root, "settings.preferences_pb")
            },
        )
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        dataStoreScope.cancel()
        harness.close()
        Dispatchers.resetMain()
    }

    @Test
    fun widgetLaunchTargetDefinesInitialState() = runTest {
        val date = LocalDate.of(2026, 3, 18)

        val viewModel = viewModel(
            widgetTarget = CalendarWidgetLaunchTarget(date = date, viewMode = CalendarViewMode.Month),
        )

        val initial = viewModel.uiState.value
        assertEquals(date, initial.selectedDate)
        assertEquals(CalendarViewMode.Month, initial.selectedView)
        assertEquals(1, initial.dateNavigationSerial)
        assertEquals(visibleRangeFor(date, CalendarViewMode.Month), initial.visibleRange)
        assertEquals(false, initial.initialDataLoaded)

        val loaded = viewModel.awaitState { it.initialDataLoaded }
        assertEquals(date, loaded.selectedDate)
        assertEquals(CalendarViewMode.Month, loaded.selectedView)
    }

    @Test
    fun initialWidgetCreateEventIsDeliveredExactlyOnce() = runTest {
        val date = LocalDate.of(2026, 3, 18)
        val viewModel = viewModel(
            widgetTarget = CalendarWidgetLaunchTarget(date = date, viewMode = CalendarViewMode.Day, createEvent = true),
        )

        viewModel.uiEvents.test {
            assertEquals(CalendarUiEvent.CreateEvent(date), awaitItem())
            expectNoEvents()
        }
        advanceUntilIdle()
        // A second collector, e.g. after the activity was recreated, must not see it again.
        viewModel.uiEvents.test { expectNoEvents() }
    }

    @Test
    fun restoredActivityReappliesLaunchSelectionWithoutEvents() = runTest {
        val date = LocalDate.of(2026, 3, 18)
        val viewModel = viewModel(
            widgetTarget = CalendarWidgetLaunchTarget(date = date, viewMode = CalendarViewMode.Day, createEvent = true),
            deliverInitialLaunchEvents = false,
        )

        assertEquals(date, viewModel.uiState.value.selectedDate)
        advanceUntilIdle()
        viewModel.uiEvents.test { expectNoEvents() }
    }

    @Test
    fun initialShareOpensThePrefilledEditorExactlyOnce() = runTest {
        settingsStore.setWelcomeCompleted(true)
        val draft = SharedEventDraft("Team lunch", "Hi all,\nlunch at noon")
        val viewModel = viewModel(sharedEvent = draft)

        viewModel.uiEvents.test {
            assertEquals(CalendarUiEvent.CreateSharedEvent(draft), awaitItem())
            expectNoEvents()
        }
        // It only waits for the loaded data, which the editor defaults come from.
        assertTrue(viewModel.uiState.value.initialDataLoaded)
        advanceUntilIdle()
        // A second collector, e.g. after the activity was recreated, must not see it again.
        viewModel.uiEvents.test { expectNoEvents() }
    }

    @Test
    fun restoredActivityDoesNotReplayTheShare() = runTest {
        settingsStore.setWelcomeCompleted(true)
        val viewModel = viewModel(
            sharedEvent = SharedEventDraft("Team lunch", "Hi all"),
            deliverInitialLaunchEvents = false,
        )

        viewModel.awaitState { it.initialDataLoaded && it.welcomeCompleted }
        advanceUntilIdle()
        viewModel.uiEvents.test { expectNoEvents() }
    }

    @Test
    fun warmShareReplacesAnOlderShareThatStillWaits() = runTest {
        settingsStore.setWelcomeCompleted(true)
        val viewModel = viewModel()
        val newer = SharedEventDraft("Newer", "second")
        viewModel.awaitState { it.initialDataLoaded && it.welcomeCompleted }

        viewModel.uiEvents.test {
            viewModel.openFromShare(SharedEventDraft("Older", "first"))
            viewModel.openFromShare(newer)
            assertEquals(CalendarUiEvent.CreateSharedEvent(newer), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun shareWaitsForTheWelcomeScreenAndTheCalendarConnection() = runTest {
        val draft = SharedEventDraft("Team lunch", "Hi all")
        val viewModel = viewModel(sharedEvent = draft)

        viewModel.uiEvents.test {
            viewModel.awaitState { it.initialDataLoaded && !it.welcomeCompleted }
            advanceUntilIdle()
            expectNoEvents()

            // "Connect existing calendars": the share waits until settings close again.
            viewModel.holdSharedEvent(true)
            settingsStore.setWelcomeCompleted(true)
            viewModel.awaitState { it.welcomeCompleted }
            advanceUntilIdle()
            expectNoEvents()

            viewModel.holdSharedEvent(false)
            assertEquals(CalendarUiEvent.CreateSharedEvent(draft), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun widgetCreateTaskAndOpenEventAreDeliveredOnce() = runTest {
        harness.repository.ensureLocalCalendar()
        harness.repository.createEvent(eventPayload("Dentist", today))
        val event = harness.eventsIn(LOCAL_COLLECTION).single()
        val viewModel = viewModel()

        viewModel.uiEvents.test {
            viewModel.openFromWidget(date = today, viewMode = CalendarViewMode.Tasks, createTaskScheduled = false)
            assertEquals(CalendarUiEvent.CreateTask(today, scheduledForDay = false), awaitItem())

            viewModel.openFromWidget(date = today, viewMode = CalendarViewMode.Day, openEventUid = event.uid)
            val opened = awaitItem()
            assertTrue(opened is CalendarUiEvent.OpenEvent)
            assertEquals(event.resourceHref, (opened as CalendarUiEvent.OpenEvent).event.resourceHref)
            expectNoEvents()
        }
        advanceUntilIdle()
        viewModel.uiEvents.test { expectNoEvents() }
        assertEquals(2, viewModel.uiState.value.dateNavigationSerial)
    }

    @Test
    fun notificationLaunchNavigatesAndOpensTheOccurrence() = runTest {
        val day = LocalDate.of(2026, 5, 4)
        harness.repository.ensureLocalCalendar()
        harness.repository.createEvent(eventPayload("Standup", day))
        val event = harness.eventsIn(LOCAL_COLLECTION).single()
        val viewModel = viewModel()

        viewModel.uiEvents.test {
            viewModel.openFromCalendarLaunch(
                CalendarLaunchTarget(
                    action = CalendarLaunchAction.OpenOccurrence,
                    occurrence = CalendarOccurrenceId.Event(event.resourceHref, harness.millis(day, LocalTime.of(10, 0))),
                ),
            )
            val opened = awaitItem()
            assertEquals(event.resourceHref, (opened as CalendarUiEvent.OpenEvent).event.resourceHref)
            expectNoEvents()
        }
        // The view reaches uiState one emission after the date, so wait for both.
        viewModel.awaitState { it.selectedDate == day && it.selectedView == CalendarViewMode.Day }
    }

    @Test
    fun foregroundAfterLongBackgroundRecentersOnToday() = runTest {
        val viewModel = viewModel()
        viewModel.selectView(CalendarViewMode.Day)
        viewModel.selectDate(today.minusDays(20))
        viewModel.awaitState { it.selectedDate == today.minusDays(20) }
        val now = System.currentTimeMillis()
        settingsStore.setLastBackgroundedAtMillis(now - 10 * 60_000L)

        viewModel.uiEvents.test {
            lifecycleSignals.foregrounded.emit(now)
            assertEquals(CalendarUiEvent.ForegroundRecentered, awaitItem())
        }
        viewModel.awaitState { it.selectedDate == today }
    }

    @Test
    fun selectDateAndViewUpdateVisibleRange() = runTest {
        val viewModel = viewModel()
        val date = LocalDate.of(2026, 7, 15)

        viewModel.selectView(CalendarViewMode.Day)
        // selectDate anchors the date to the current view, so let the view settle first.
        viewModel.awaitState { it.selectedView == CalendarViewMode.Day }
        viewModel.selectDate(date)
        // Range and view are separate inputs, so wait for the state in which both have settled.
        val dayRange = CalendarRange(LocalDate.of(2026, 6, 14), LocalDate.of(2026, 8, 15))
        viewModel.awaitState {
            it.selectedView == CalendarViewMode.Day && it.selectedDate == date && it.visibleRange == dayRange
        }

        viewModel.selectView(CalendarViewMode.Month)
        val monthRange = CalendarRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 10, 1))
        val monthState = viewModel.awaitState {
            it.selectedView == CalendarViewMode.Month && it.visibleRange == monthRange
        }
        assertEquals(date, monthState.selectedDate)
    }

    @Test
    fun windowSizeClassPicksItsOwnMultiDayCount() = runTest {
        val viewModel = viewModel()
        viewModel.awaitState { it.initialDataLoaded && it.multiDayCount == 3 }

        val tabletLandscape = CalendarWindowLayout(isLandscape = true, widthDp = 1280, heightDp = 800)
        viewModel.setWindowLayout(tabletLandscape, applyOrientationEntryView = true)
        viewModel.awaitState { it.multiDayCount == 5 }

        // The +/- controls on the unfolded screen change only the large-screen value ...
        viewModel.settings.setMultiDayCount(6)
        viewModel.awaitState { it.multiDayCount == 6 && it.largeLandscapeMultiDayCount == 6 }

        // ... so the folded phone screen keeps its own count.
        viewModel.setWindowLayout(CalendarWindowLayout.PhoneLandscape, applyOrientationEntryView = false)
        val folded = viewModel.awaitState { it.multiDayCount == 3 }
        assertEquals(3, folded.landscapeMultiDayCount)
        assertEquals(6, folded.largeLandscapeMultiDayCount)

        viewModel.setWindowLayout(
            CalendarWindowLayout(isLandscape = false, widthDp = 673, heightDp = 841),
            applyOrientationEntryView = true,
        )
        viewModel.awaitState { it.multiDayCount == 4 }
    }

    @Test
    fun enteringLandscapeSwapsDayForMultipleDaysOnlyWhenTheOrientationChanges() = runTest {
        val viewModel = viewModel()
        viewModel.selectView(CalendarViewMode.Day)
        viewModel.awaitState { it.selectedView == CalendarViewMode.Day }

        val tabletLandscape = CalendarWindowLayout(isLandscape = true, widthDp = 1280, heightDp = 800)
        viewModel.setWindowLayout(tabletLandscape, applyOrientationEntryView = true)
        viewModel.awaitState { it.selectedView == CalendarViewMode.ThreeDay }

        // Choosing Day in landscape sticks while the window is only resized (split screen, freeform).
        viewModel.selectView(CalendarViewMode.Day)
        viewModel.awaitState { it.selectedView == CalendarViewMode.Day }
        viewModel.setWindowLayout(tabletLandscape.copy(widthDp = 1000), applyOrientationEntryView = false)
        viewModel.settings.setShowCalendarWeeks(true)
        val resized = viewModel.awaitState { it.showCalendarWeeks }
        assertEquals(CalendarViewMode.Day, resized.selectedView)
    }

    @Test
    fun settingsChangesReachUiStateAndRefreshWidgets() = runTest {
        val viewModel = viewModel()
        viewModel.awaitState { it.initialDataLoaded }

        viewModel.settings.setShowCalendarWeeks(true)
        viewModel.settings.setTasksWidgetSortMode(WidgetTaskSortMode.Priority)

        val state = viewModel.awaitState {
            it.showCalendarWeeks && it.widgetSettings.tasksWidgetSortMode == WidgetTaskSortMode.Priority
        }
        assertTrue(state.showCalendarWeeks)
        assertEquals(
            listOf(KgsWidgetKind.Tasks to false),
            widgetRefresher.awaitUpdates { it.isNotEmpty() },
        )
    }

    @Test
    fun searchResultsExcludeHiddenCollections() = runTest {
        harness.repository.ensureLocalCalendar()
        harness.repository.createEvent(eventPayload("Dentist", today))
        val viewModel = viewModel()

        viewModel.search.setSearchQuery("Dentist")
        val found = viewModel.awaitState { it.searchResults.isNotEmpty() }
        assertEquals(listOf("Dentist"), found.searchResults.map { it.title })

        viewModel.settings.setCollectionVisibleInViews(LOCAL_COLLECTION, visible = false)
        val hidden = viewModel.awaitState { LOCAL_COLLECTION in it.hiddenCollectionHrefs && it.searchResults.isEmpty() }
        assertEquals("Dentist", hidden.searchQuery)
    }

    @Test
    fun hidingOnlyTheTasksOfACalendarKeepsItsEventsAndReplansReminders() = runTest {
        harness.repository.ensureLocalCalendar()
        harness.repository.createEvent(eventPayload("Team standup", today))
        harness.repository.createTask(taskPayload("Team report", dueDate = today))
        val viewModel = viewModel()
        viewModel.search.setSearchQuery("Team")
        viewModel.awaitState { state ->
            state.events.any { it.title == "Team standup" } &&
                state.datedTasks.any { it.title == "Team report" } &&
                state.searchResults.isNotEmpty() &&
                state.searchTaskResults.isNotEmpty()
        }
        val reschedulesBefore = reminderReschedules.value

        viewModel.settings.setCollectionTasksVisible(LOCAL_COLLECTION, visible = false)

        val hidden = viewModel.awaitState { state ->
            LOCAL_COLLECTION in state.collectionVisibility.tasksHiddenIn &&
                state.datedTasks.isEmpty() &&
                state.scheduledOpenTasks.isEmpty() &&
                state.searchTaskResults.isEmpty()
        }
        assertEquals(listOf("Team standup"), hidden.events.map { it.title })
        assertEquals(listOf("Team standup"), hidden.searchResults.map { it.title })
        assertTrue(hidden.hiddenCollectionHrefs.isEmpty())
        awaitReminderReschedules { it > reschedulesBefore }
    }

    @Test
    fun hidingACalendarReplansReminders() = runTest {
        harness.repository.ensureLocalCalendar()
        val viewModel = viewModel()
        val reschedulesBefore = reminderReschedules.value

        viewModel.settings.setCollectionVisibleInViews(LOCAL_COLLECTION, visible = false)

        viewModel.awaitState { LOCAL_COLLECTION in it.hiddenCollectionHrefs }
        awaitReminderReschedules { it > reschedulesBefore }
    }

    @Test
    fun completingARecurringTaskInTheTaskListCompletesOnlyTheListedOccurrence() = runTest {
        harness.repository.ensureLocalCalendar()
        harness.repository.createTask(taskPayload("Water plants", dueDate = today.minusDays(3)).copy(recurrenceRule = "FREQ=DAILY"))
        val viewModel = viewModel()
        val listed = viewModel.awaitState { state -> state.scheduledOpenTasks.any { it.title == "Water plants" } }
            .scheduledOpenTasks.single { it.title == "Water plants" }
        assertEquals(today, listed.dueDate())

        viewModel.edits.setTaskStatus(listed, "COMPLETED")

        val next = viewModel.awaitState { state ->
            state.scheduledOpenTasks.singleOrNull { it.title == "Water plants" }?.dueDate() == today.plusDays(1)
        }.scheduledOpenTasks.single { it.title == "Water plants" }
        assertFalse(next.isCompleted)
        val master = harness.repository.taskByResource(listed.resourceHref)!!
        assertFalse(master.isCompleted)
        assertTrue(master.occurrenceAt(listed.startAtMillis ?: listed.dueAtMillis!!).isCompleted)
    }

    @Test
    fun tickingAMissedOccurrenceInTheOverdueListCompletesOnlyThatOccurrence() = runTest {
        harness.repository.ensureLocalCalendar()
        harness.repository.createTask(taskPayload("Water plants", dueDate = today.minusDays(3)).copy(recurrenceRule = "FREQ=DAILY"))
        harness.repository.createTask(taskPayload("Single", dueDate = today.minusDays(1)))
        val viewModel = viewModel()
        val initial = viewModel.awaitState { state ->
            state.missedTaskOccurrences.size == 3 && state.scheduledOpenTasks.size == 2
        }
        val overdue = orderedOverdueTasks(initial.scheduledOpenTasks + initial.missedTaskOccurrences, today, TEST_ZONE)
        assertEquals(
            listOf("Water plants", "Water plants", "Single", "Water plants"),
            overdue.map { it.title },
        )
        assertEquals(
            listOf(today.minusDays(3), today.minusDays(2), today.minusDays(1), today.minusDays(1)),
            overdue.map { it.dueDate() },
        )
        assertEquals(today, initial.scheduledOpenTasks.single { it.title == "Water plants" }.dueDate())

        val ticked = initial.missedTaskOccurrences.single { it.dueDate() == today.minusDays(2) }
        viewModel.edits.setTaskStatus(ticked, "COMPLETED")

        val after = viewModel.awaitState { state ->
            state.missedTaskOccurrences.map { it.dueDate() } == listOf(today.minusDays(3), today.minusDays(1))
        }
        assertEquals(today, after.scheduledOpenTasks.single { it.title == "Water plants" }.dueDate())
        assertEquals(today.minusDays(1), after.scheduledOpenTasks.single { it.title == "Single" }.dueDate())
        val master = harness.repository.taskByResource(ticked.resourceHref)!!
        assertFalse(master.isCompleted)
        assertTrue(master.occurrenceAt(ticked.dueAtMillis!!).isCompleted)
    }

    @Test
    fun taskListFollowsTheCurrentDay() = runTest {
        harness.repository.ensureLocalCalendar()
        harness.repository.createTask(taskPayload("Water plants", dueDate = today.minusDays(3)).copy(recurrenceRule = "FREQ=DAILY"))
        val viewModel = viewModel()
        viewModel.awaitState { state -> state.scheduledOpenTasks.singleOrNull()?.dueDate() == today }

        viewModel.setCurrentDay(today.plusDays(1))

        viewModel.awaitState { state -> state.scheduledOpenTasks.singleOrNull()?.dueDate() == today.plusDays(1) }
    }

    private fun TaskEntity.dueDate(): LocalDate = Instant.ofEpochMilli(dueAtMillis!!).atZone(TEST_ZONE).toLocalDate()

    private suspend fun awaitReminderReschedules(predicate: (Int) -> Boolean) =
        withContext(Dispatchers.Default) {
            withTimeout(30_000) { reminderReschedules.first(predicate) }
        }

    private fun viewModel(
        widgetTarget: CalendarWidgetLaunchTarget? = null,
        sharedEvent: SharedEventDraft? = null,
        deliverInitialLaunchEvents: Boolean = true,
    ): CalendarViewModel {
        val repository = harness.repository
        return CalendarViewModel(
            repository = repository,
            settingsStore = settingsStore,
            sourceCalendarMutationCoordinator = SourceCalendarMutationCoordinator(
                includeDisabledProviderCalendars = { false },
                fullRefresh = {},
                reconcileLocalState = {},
            ),
            taskMutationCoordinator = TaskMutationCoordinator(
                persistStatus = { resourceHref, status, occurrenceId ->
                    if (occurrenceId == null) {
                        repository.setTaskStatus(resourceHref, status)
                    } else {
                        repository.setTaskOccurrenceStatus(resourceHref, occurrenceId.recurrenceIdMillis, status)
                    }
                },
                pushPendingChanges = repository::pushPendingChangesCreatedSince,
                notificationReconciler = NoOpNotificationReconciler,
                rescheduleReminders = {},
                updateWidgets = {},
            ),
            calendarLaunchResolver = CalendarLaunchResolver(
                eventByResource = repository::eventByResource,
                taskByResource = repository::taskByResource,
                expandEvents = repository::expandEventReminderOccurrences,
                expandTasks = repository::expandTaskReminderOccurrences,
                zoneId = TEST_ZONE,
            ),
            timelineViewportMemory = TimelineOrientationViewportMemory(),
            widgetRefresher = widgetRefresher,
            reminderRescheduler = ReminderRescheduler { reminderReschedules.update { it + 1 } },
            appLifecycleSignals = lifecycleSignals,
            uiStrings = UiStrings { "string-$it" },
            zoneId = TEST_ZONE,
            initialWidgetLaunchTarget = widgetTarget,
            initialSharedEvent = sharedEvent,
            deliverInitialLaunchEvents = deliverInitialLaunchEvents,
        ).also(viewModels::add)
    }
}

/** Waits in wall-clock time: Room and DataStore deliver on their own threads. */
private suspend fun CalendarViewModel.awaitState(predicate: (CalendarUiState) -> Boolean): CalendarUiState =
    withContext(Dispatchers.Default) {
        withTimeout(30_000) { uiState.first(predicate) }
    }

private class RecordingWidgetRefresher : WidgetRefresher {
    private val updates = MutableStateFlow<List<Pair<KgsWidgetKind?, Boolean>>>(emptyList())

    override fun updateAll() {
        updates.update { it + (null to false) }
    }

    override fun update(kind: KgsWidgetKind, forceFullDayUpdate: Boolean) {
        updates.update { it + (kind to forceFullDayUpdate) }
    }

    suspend fun awaitUpdates(predicate: (List<Pair<KgsWidgetKind?, Boolean>>) -> Boolean) =
        withContext(Dispatchers.Default) {
            withTimeout(30_000) { updates.first(predicate) }
        }
}

private class FakeAppLifecycleSignals : AppLifecycleSignals {
    val foregrounded = MutableSharedFlow<Long>()
    override val processForegroundedAt: Flow<Long> = foregrounded

    override fun registerAndroidCalendarObserverIfPermitted() = Unit
}

private object NoOpNotificationReconciler : TaskNotificationReconciler {
    override suspend fun cancelOccurrence(occurrenceId: CalendarOccurrenceId.Task) = Unit

    override suspend fun cancelResource(resourceHref: String) = Unit
}
