package com.kgs.calendar.widget

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.TextView
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kgs.calendar.R
import com.kgs.calendar.data.LOCAL_COLLECTION
import com.kgs.calendar.data.RepositoryHarness
import com.kgs.calendar.data.TEST_ZONE
import com.kgs.calendar.data.eventPayload
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.data.settings.WidgetTaskDisplayMode
import com.kgs.calendar.data.taskPayload
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.reminder.TaskMutationCoordinator
import com.kgs.calendar.reminder.TaskNotificationReconciler
import com.kgs.calendar.widget.model.WidgetListRowType
import com.kgs.calendar.widget.render.KgsWidgetCollectionFactory
import com.kgs.calendar.widget.render.KgsWidgetDayCollectionFactory
import com.kgs.calendar.widget.update.WidgetTaskToggle
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Builds each widget kind from the in-memory repository harness, the same way the updater and
 * the collection service do, and inflates the resulting RemoteViews to catch wiring mistakes.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetKindRenderingTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var harness: RepositoryHarness
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var widgets: WidgetDependencies
    private val context: Context get() = harness.context
    private val today = LocalDate.now(TEST_ZONE)
    private val appWidgetId = 41
    private val defaultTimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        // Widget code resolves the system zone; keep it in line with the harness zone.
        TimeZone.setDefault(TimeZone.getTimeZone(TEST_ZONE))
        harness = RepositoryHarness()
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) {
                File(tempFolder.root, "settings.preferences_pb")
            },
        )
        widgets = WidgetDependencies(context, settingsStore, harness.components.queries)
        runBlocking {
            harness.repository.ensureLocalCalendar()
            harness.repository.createEvent(eventPayload("Standup", today, LocalTime.of(9, 0), LocalTime.of(9, 30)))
            harness.repository.createEvent(eventPayload("Offsite", today, start = null, end = null, allDay = true))
            harness.repository.createTask(taskPayload("Write report", dueDate = today))
            harness.repository.createTask(taskPayload("Buy milk"))
        }
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        harness.close()
        TimeZone.setDefault(defaultTimeZone)
    }

    @Test
    fun agendaWidgetListsTodaysEventsAndTasks() = runBlocking {
        val renderer = widgets.renderer()
        val snapshot = requireNotNull(renderer.collectionSnapshot(KgsWidgetKind.Agenda, appWidgetId))

        assertEquals(
            listOf("Offsite", "Write report", "Standup"),
            snapshot.rows.filter { it.type != WidgetListRowType.Section }.map { it.title },
        )
        val views = renderer.render(KgsWidgetKind.Agenda, appWidgetId, Bundle(), collectionSnapshot = snapshot)
        assertEquals(R.layout.widget_calendar, views.layoutId)
        assertTrue(texts(views).contains("Agenda"))

        val items = collectionItems(KgsWidgetCollectionFactory(widgets, KgsWidgetKind.Agenda, appWidgetId))
        assertEquals(snapshot.rows.size, items.size)
        assertEquals(R.layout.widget_agenda_event_item, items.last().layoutId)
        assertTrue(widgetImageFiles().isNotEmpty())
    }

    @Test
    fun tasksWidgetListsPlannedTasks() = runBlocking {
        val renderer = widgets.renderer()
        val snapshot = requireNotNull(renderer.collectionSnapshot(KgsWidgetKind.Tasks, appWidgetId))

        assertEquals(listOf("Write report"), snapshot.rows.map { it.title })
        assertEquals(WidgetListRowType.Task, snapshot.rows.single().type)
        val views = renderer.render(KgsWidgetKind.Tasks, appWidgetId, Bundle(), collectionSnapshot = snapshot)
        assertEquals(R.layout.widget_calendar_tasks, views.layoutId)
        assertTrue(texts(views).contains("Tasks"))

        val items = collectionItems(KgsWidgetCollectionFactory(widgets, KgsWidgetKind.Tasks, appWidgetId))
        assertTrue(texts(items.single()).contains("Write report"))
    }

    @Test
    fun monthWidgetPlacesItemsOnToday() = runBlocking {
        val renderer = widgets.renderer()
        val prepared = renderer.prepareMonthUpdate(appWidgetId, Bundle())

        assertEquals(YearMonth.from(today), prepared.page.month)
        assertTrue(prepared.page.rowCount in 4..6)
        assertEquals(3, prepared.itemCount)
        val todayCell = prepared.page.cells.single { it.date == today }
        assertEquals(setOf("Offsite", "Write report", "Standup"), todayCell.items.map { it.title }.toSet())
        assertNotNull(prepared.signature)
        assertNotNull(widgets.state.monthPages.get(YearMonth.from(today), prepared.settings, TEST_ZONE.id))

        val result = renderer.renderMonthUpdate(prepared)
        assertEquals(R.layout.widget_month_calendar, result.views.layoutId)
        val texts = texts(result.views)
        assertTrue(texts.contains(today.dayOfMonth.toString()))
        assertTrue(texts.contains("Offsite"))
    }

    @Test
    fun multiWidgetCombinesMonthAndAgenda() = runBlocking {
        val renderer = widgets.renderer()
        val prepared = renderer.prepareMultiUpdate(appWidgetId, Bundle())

        assertEquals(YearMonth.from(today), prepared.page.month)
        assertEquals(
            listOf("Offsite", "Write report", "Standup"),
            prepared.collectionSnapshot.rows.filter { it.type != WidgetListRowType.Section }.map { it.title },
        )
        val views = renderer.render(KgsWidgetKind.Multi, appWidgetId, Bundle(), preparedMulti = prepared)
        assertEquals(R.layout.widget_calendar_multi, views.layoutId)
        assertTrue(texts(views).contains(today.dayOfMonth.toString()))

        val items = collectionItems(KgsWidgetCollectionFactory(widgets, KgsWidgetKind.Multi, appWidgetId))
        assertEquals(prepared.collectionSnapshot.rows.size, items.size)
    }

    @Test
    fun dayWidgetShowsTimedAndAllDayItems() = runBlocking {
        val renderer = widgets.renderer()
        val grid = renderer.dayGridCollectionSnapshot(appWidgetId)

        val standupRow = grid.rows.single { row -> row.timedItems.any { it.item.title == "Standup" } }
        assertEquals(9, standupRow.hour)
        val frame = renderer.dayAllDaySectionFrameData(appWidgetId, Bundle())
        assertEquals(today, frame.timeline.day)
        assertEquals(listOf("Offsite", "Write report"), frame.timeline.allDayItems.map { it.title })

        val views = renderer.render(KgsWidgetKind.Day, appWidgetId, Bundle())
        assertEquals(R.layout.widget_day_calendar, views.layoutId)
        assertTrue(texts(views).contains("+"))
        assertTrue(widgetImageFiles().isNotEmpty())

        val items = collectionItems(KgsWidgetDayCollectionFactory(widgets, appWidgetId))
        assertEquals(grid.rows.size, items.size)
    }

    @Test
    fun tasksWidgetListsTheCurrentOccurrenceOfARecurringTaskAndItsToggleCompletesOnlyThatOne() = runBlocking {
        val repository = harness.repository
        repository.createTask(taskPayload("Water plants", dueDate = today.minusDays(3)).copy(recurrenceRule = "FREQ=DAILY"))
        val renderer = widgets.renderer()
        val row = requireNotNull(renderer.collectionSnapshot(KgsWidgetKind.Tasks, appWidgetId)).rows.single { it.title == "Water plants" }
        assertEquals(today, row.date)
        val resourceHref = requireNotNull(row.taskResourceHref)
        val occurrenceMillis = requireNotNull(row.taskOccurrenceMillis)

        val statusWrites = mutableListOf<CalendarOccurrenceId.Task?>()
        WidgetTaskToggle(
            taskByResource = repository::taskByResource,
            currentOccurrence = { null },
            coordinator = TaskMutationCoordinator(
                persistStatus = { href, status, occurrenceId ->
                    statusWrites += occurrenceId
                    if (occurrenceId == null) {
                        repository.setTaskStatus(href, status)
                    } else {
                        repository.setTaskOccurrenceStatus(href, occurrenceId.recurrenceIdMillis, status)
                    }
                },
                pushPendingChanges = {},
                notificationReconciler = object : TaskNotificationReconciler {
                    override suspend fun cancelOccurrence(occurrenceId: CalendarOccurrenceId.Task) = Unit
                    override suspend fun cancelResource(resourceHref: String) = Unit
                },
                rescheduleReminders = {},
                updateWidgets = {},
            ),
        ).toggle(resourceHref, occurrenceMillis)

        assertEquals(listOf(CalendarOccurrenceId.Task(resourceHref, occurrenceMillis)), statusWrites)
        assertFalse(requireNotNull(harness.task(resourceHref)).isCompleted)
        val next = requireNotNull(renderer.collectionSnapshot(KgsWidgetKind.Tasks, appWidgetId)).rows.single { it.title == "Water plants" }
        assertEquals(today.plusDays(1), next.date)
        assertFalse(next.completed)
    }

    @Test
    fun tasksWidgetForTodayListsEveryMissedOccurrenceUntilItIsCompleted() = runBlocking {
        val repository = harness.repository
        repository.createTask(taskPayload("Water plants", dueDate = today.minusDays(3)).copy(recurrenceRule = "FREQ=DAILY"))
        repository.createTask(taskPayload("Pay bill", dueDate = today.minusDays(1)))
        widgets.settingsStore.setTasksWidgetDisplayMode(WidgetTaskDisplayMode.Today)
        val renderer = widgets.renderer()

        val rows = requireNotNull(renderer.collectionSnapshot(KgsWidgetKind.Tasks, appWidgetId)).rows
        val waterRows = rows.filter { it.title == "Water plants" }
        assertEquals(listOf(3L, 2L, 1L, 0L).map { today.minusDays(it) }, waterRows.map { it.date })
        assertEquals(rows.size, rows.map { it.stableId }.toSet().size)
        assertEquals(today.minusDays(1), rows.single { it.title == "Pay bill" }.date)

        val missed = waterRows[1]
        repository.setTaskOccurrenceStatus(
            requireNotNull(missed.taskResourceHref),
            requireNotNull(missed.taskOccurrenceMillis),
            "COMPLETED",
        )
        val afterCompletion = requireNotNull(renderer.collectionSnapshot(KgsWidgetKind.Tasks, appWidgetId)).rows
        assertEquals(
            listOf(3L, 1L, 0L).map { today.minusDays(it) },
            afterCompletion.filter { it.title == "Water plants" }.map { it.date },
        )

        widgets.settingsStore.setTasksWidgetIncludeOverdue(false)
        val withoutOverdue = requireNotNull(renderer.collectionSnapshot(KgsWidgetKind.Tasks, appWidgetId)).rows
        assertEquals(listOf(today), withoutOverdue.filter { it.title == "Water plants" }.map { it.date })
        assertTrue(withoutOverdue.none { it.title == "Pay bill" })
    }

    @Test
    fun tasksWidgetLeavesOutCompletedSubtasksAndTheExpanderOfParentsWithOnlyCompletedOnes() = runBlocking {
        val repository = harness.repository
        repository.createTask(taskPayload("Trip", dueDate = today))
        repository.createTask(taskPayload("Party", dueDate = today))
        val tasks = harness.tasksIn(LOCAL_COLLECTION)
        val trip = tasks.single { it.title == "Trip" }
        val party = tasks.single { it.title == "Party" }
        repository.createTask(taskPayload("Pack", parentUid = trip.uid))
        repository.createTask(taskPayload("Book hotel", parentUid = trip.uid, isCompleted = true))
        repository.createTask(taskPayload("Invite", parentUid = party.uid, isCompleted = true))

        val rows = requireNotNull(widgets.renderer().collectionSnapshot(KgsWidgetKind.Tasks, appWidgetId)).rows

        assertEquals(listOf("Party", "Trip", "Pack", "Write report"), rows.map { it.title })
        assertEquals(1, rows.single { it.title == "Trip" }.childCount)
        assertEquals(0, rows.single { it.title == "Party" }.childCount)
    }

    private fun widgetImageFiles(): List<File> =
        File(context.filesDir, "widget_images/day_$appWidgetId").listFiles().orEmpty().filter { it.extension == "png" }

    private fun collectionItems(factory: RemoteViewsService.RemoteViewsFactory): List<RemoteViews> {
        factory.onCreate()
        factory.onDataSetChanged()
        return (0 until factory.count).map(factory::getViewAt)
    }

    private fun texts(views: RemoteViews): List<String> {
        val texts = mutableListOf<String>()
        fun collect(view: View) {
            if (view.visibility != View.VISIBLE) return
            if (view is TextView && view.text.isNotEmpty()) texts += view.text.toString()
            if (view is ViewGroup) (0 until view.childCount).forEach { collect(view.getChildAt(it)) }
        }
        collect(views.apply(context, FrameLayout(context)))
        return texts
    }
}
