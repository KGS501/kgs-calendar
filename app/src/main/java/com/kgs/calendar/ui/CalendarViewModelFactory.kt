package com.kgs.calendar.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgs.calendar.AppGraph
import com.kgs.calendar.navigation.CalendarLaunchTarget
import com.kgs.calendar.navigation.SharedEventDraft
import com.kgs.calendar.ui.editor.EditorDraftFiles
import com.kgs.calendar.ui.editor.EditorDraftStore
import java.io.File

@Composable
fun rememberCalendarViewModel(graph: AppGraph): CalendarViewModel =
    viewModel(factory = CalendarViewModelFactory(graph))

@Suppress("UNCHECKED_CAST")
class CalendarViewModelFactory(
    private val graph: AppGraph,
    private val initialWidgetLaunchTarget: CalendarWidgetLaunchTarget? = null,
    private val initialCalendarLaunchTarget: CalendarLaunchTarget? = null,
    private val initialSharedEvent: SharedEventDraft? = null,
    private val deliverInitialLaunchEvents: Boolean = true,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CalendarViewModel(
            repository = graph.repository,
            settingsStore = graph.settingsStore,
            sourceCalendarMutationCoordinator = graph.sourceCalendarMutationCoordinator,
            taskMutationCoordinator = graph.taskMutationCoordinator,
            calendarLaunchResolver = graph.calendarLaunchResolver,
            timelineViewportMemory = graph.timelineViewportMemory,
            widgetRefresher = graph.widgetRefresher,
            reminderRescheduler = graph.reminderRescheduler,
            appLifecycleSignals = graph.appLifecycleSignals,
            uiStrings = graph.uiStrings,
            pickedContentReader = graph.pickedContentReader,
            calendarFileExporter = graph.calendarFileExporter,
            initialWidgetLaunchTarget = initialWidgetLaunchTarget,
            initialCalendarLaunchTarget = initialCalendarLaunchTarget,
            initialSharedEvent = initialSharedEvent,
            deliverInitialLaunchEvents = deliverInitialLaunchEvents,
            editorDrafts = EditorDraftStore(
                EditorDraftFiles(File(graph.appContext.noBackupFilesDir, EDITOR_DRAFT_DIRECTORY)),
            ),
        ) as T
    }
}

private const val EDITOR_DRAFT_DIRECTORY = "editor-drafts"
