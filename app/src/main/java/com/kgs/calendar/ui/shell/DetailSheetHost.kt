@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.kgs.calendar.ui.model.occurrenceStartForEdit
import com.kgs.calendar.ui.model.taskDate
import com.kgs.calendar.ui.shell.CalendarShellUiState
import com.kgs.calendar.ui.shell.editorSchedule
import com.kgs.calendar.ui.shell.newSubtaskSchedule
import java.time.LocalDate
import java.time.LocalTime

/** The event/task detail sheet, morphing between a task and its subtasks or parent. */
@Composable
internal fun DetailSheetHost(
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    renderState: CalendarUiState,
    shell: CalendarShellUiState,
    today: LocalDate,
) {
    shell.detailSheet?.let { detail ->
        val currentDetail = when (detail) {
            is DetailSheet.Event -> {
                val sameResource = renderState.events.filter { it.resourceHref == detail.event.resourceHref }
                val occurrenceStart = detail.event.occurrenceStartForEdit()
                val refreshed = sameResource.firstOrNull { it.occurrenceStartForEdit() == occurrenceStart }
                    ?: sameResource.firstOrNull()
                refreshed?.let { DetailSheet.Event(it) } ?: detail
            }
            is DetailSheet.Task -> {
                val occurrenceStart = detail.task.occurrenceStartForEdit()
                val refreshed = renderState.datedTasks.firstOrNull {
                    it.resourceHref == detail.task.resourceHref && it.occurrenceStartForEdit() == occurrenceStart
                } ?: renderState.allTasks.firstOrNull {
                    it.resourceHref == detail.task.resourceHref &&
                        (detail.task.recurrenceRule.isNullOrBlank() || it.occurrenceStartForEdit() == occurrenceStart)
                }
                refreshed?.let { DetailSheet.Task(it) } ?: detail
            }
        }
        KgsModalBottomSheet(
            onDismissRequest = shell::closeDetail,
            initialSnap = currentDetail.preferredInitialSnap(),
            initialContentHeight = currentDetail.estimatedPopoverHeight(),
            onBackRequest = shell::navigateDetailBack,
        ) {
            SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    AnimatedContent(
                        targetState = currentDetail,
                        contentKey = { it.transitionKey() },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .graphicsLayer { clip = false },
                        transitionSpec = {
                            (EnterTransition.None togetherWith ExitTransition.None)
                                .using(
                                    SizeTransform(clip = false) { _, _ ->
                                        tween(TaskDetailMorphDurationMs, easing = MotionEmphasized)
                                    },
                                )
                        },
                        label = "taskDetailMorph",
                    ) { animatedDetail ->
                        val detailMorphScope = this
                        CompositionLocalProvider(LocalMorphAnimatedVisibilityScope provides detailMorphScope) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface),
                            ) {
                                DetailSheetContent(
                                    detail = animatedDetail,
                                    collections = state.collections,
                                    hiddenCollectionHrefs = state.hiddenCollectionHrefs,
                                    accounts = state.accounts,
                                    problemResources = state.problemResources,
                                    taskColorMode = state.taskColorMode,
                                    eventFieldOrder = state.eventFieldOrder,
                                    taskFieldOrder = state.taskFieldOrder,
                                    autoLoadMapPreviews = state.autoLoadMapPreviews,
                                    accountEmails = (state.accounts.map { it.username } + listOfNotNull(state.account?.username)).distinct(),
                                    allTasks = renderState.allTasks,
                                    taskMorphGeneration = shell.detailTaskMorphGeneration,
                                    taskMorphSourceHref = shell.detailTaskMorphSourceHref,
                                    onTaskStatusChanged = viewModel.edits::setTaskStatus,
                                    onTaskPriorityChanged = viewModel.edits::setTaskPriority,
                                    onTaskProgressChanged = viewModel.edits::setTaskProgress,
                                    onEventParticipationChanged = viewModel.edits::setEventParticipation,
                                    onEditEvent = { shell.editEvent(it, it.editorSchedule()) },
                                    onDuplicateEvent = { shell.duplicateEvent(it, it.editorSchedule()) },
                                    onCopyEventTo = { event, collectionHref ->
                                        viewModel.edits.copyEventTo(event.resourceHref, collectionHref)
                                        shell.closeDetail()
                                    },
                                    onDeleteEvent = { uid, scope, occurrenceStartMillis ->
                                        when (scope) {
                                            EventDeleteScope.This -> viewModel.edits.deleteEventOccurrence(uid, occurrenceStartMillis)
                                            EventDeleteScope.ThisAndFollowing -> viewModel.edits.deleteEventFollowing(uid, occurrenceStartMillis)
                                            EventDeleteScope.All -> viewModel.edits.deleteEvent(uid)
                                        }
                                        shell.closeDetail()
                                    },
                                    onEditTask = { shell.editTask(it, it.editorSchedule(today)) },
                                    onDuplicateTask = { shell.duplicateTask(it, it.editorSchedule(today)) },
                                    onCopyTaskTo = { task, collectionHref ->
                                        viewModel.edits.copyTaskTo(task.resourceHref, collectionHref)
                                        shell.closeDetail()
                                    },
                                    onDeleteTask = {
                                        viewModel.edits.deleteTask(it)
                                        shell.closeDetail()
                                    },
                                    onOpenSubtask = shell::openSubtask,
                                    onOpenParentTask = shell::openParentTask,
                                    onAddSubtask = { parent ->
                                        shell.addSubtask(parent, newSubtaskSchedule(parent.taskDate() ?: state.selectedDate, LocalTime.now()))
                                    },
                                    onClose = shell::closeDetail,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
