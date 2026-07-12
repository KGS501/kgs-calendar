package com.kgs.calendar.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class CalendarStructuralMutation(val requiresFullRefresh: Boolean) {
    AddSource(true),
    EditSource(true),
    RemoveSource(true),
    EnableSource(true),
    DisableSource(true),
    AddCalendar(true),
    EditCalendar(true),
    RemoveCalendar(true),
    EnableCalendar(true),
    DisableCalendar(true),
    ReorderCalendars(false),
    SetVisibleInViews(false),
}

enum class PostMutationStage {
    Refresh,
    Reconciliation,
}

sealed interface StructuralMutationResult {
    data object Complete : StructuralMutationResult

    data class SavedWithFollowUpFailure(
        val stage: PostMutationStage,
        val cause: Throwable,
    ) : StructuralMutationResult
}

class SourceCalendarMutationCoordinator(
    private val includeDisabledProviderCalendars: suspend () -> Boolean,
    private val fullRefresh: suspend (includeDisabledProviderCalendars: Boolean) -> Unit,
    private val reconcileLocalState: suspend () -> Unit,
    private val mutex: Mutex = Mutex(),
) {
    suspend fun run(
        kind: CalendarStructuralMutation,
        mutation: suspend () -> Unit,
    ): StructuralMutationResult = mutex.withLock {
        mutation()
        if (!kind.requiresFullRefresh) return@withLock StructuralMutationResult.Complete

        val refreshFailure = runCatching {
            fullRefresh(includeDisabledProviderCalendars())
        }.exceptionOrNull()
        val reconciliationFailure = runCatching {
            reconcileLocalState()
        }.exceptionOrNull()

        when {
            reconciliationFailure != null -> StructuralMutationResult.SavedWithFollowUpFailure(
                PostMutationStage.Reconciliation,
                reconciliationFailure,
            )
            refreshFailure != null -> StructuralMutationResult.SavedWithFollowUpFailure(
                PostMutationStage.Refresh,
                refreshFailure,
            )
            else -> StructuralMutationResult.Complete
        }
    }
}
