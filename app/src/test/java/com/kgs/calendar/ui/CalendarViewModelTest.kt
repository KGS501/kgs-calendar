package com.kgs.calendar.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.kgs.calendar.data.LOCAL_COLLECTION
import com.kgs.calendar.data.RepositoryHarness
import com.kgs.calendar.data.TEST_ZONE
import com.kgs.calendar.data.eventPayload
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.data.settings.WidgetTaskSortMode
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.model.CalendarRange
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.visibleRangeFor
import com.kgs.calendar.navigation.CalendarLaunchAction
import com.kgs.calendar.navigation.CalendarLaunchResolver
import com.kgs.calendar.navigation.CalendarLaunchTarget
import com.kgs.calendar.reminder.TaskMutationCoordinator
import com.kgs.calendar.reminder.TaskNotificationReconciler
import com.kgs.calendar.sync.SourceCalendarMutationCoordinator
import com.kgs.calendar.ui.timeline.TimelineOrientationViewportMemory
import com.kgs.calendar.widget.KgsWidgetKind
import java.io.File
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
        val state = viewModel.awaitState { it.selectedDate == day }
        assertEquals(CalendarViewMode.Day, state.selectedView)
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

    private fun viewModel(
        widgetTarget: CalendarWidgetLaunchTarget? = null,
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
                persistStatus = { resourceHref, status, _ -> repository.setTaskStatus(resourceHref, status) },
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
            reminderRescheduler = ReminderRescheduler {},
            appLifecycleSignals = lifecycleSignals,
            uiStrings = UiStrings { "string-$it" },
            zoneId = TEST_ZONE,
            initialWidgetLaunchTarget = widgetTarget,
            deliverInitialLaunchEvents = deliverInitialLaunchEvents,
        ).also(viewModels::add)
    }
}

/** Waits in wall-clock time: Room and DataStore deliver on their own threads. */
private suspend fun CalendarViewModel.awaitState(predicate: (CalendarUiState) -> Boolean): CalendarUiState =
    withContext(Dispatchers.Default) {
        withTimeout(10_000) { uiState.first(predicate) }
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
            withTimeout(10_000) { updates.first(predicate) }
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
