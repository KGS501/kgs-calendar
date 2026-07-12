package com.kgs.calendar.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCalendarMutationCoordinatorTest {
    @Test
    fun onlyStructuralChangesRequireFullRefresh() {
        val structural = listOf(
            CalendarStructuralMutation.AddSource,
            CalendarStructuralMutation.EditSource,
            CalendarStructuralMutation.RemoveSource,
            CalendarStructuralMutation.EnableSource,
            CalendarStructuralMutation.DisableSource,
            CalendarStructuralMutation.AddCalendar,
            CalendarStructuralMutation.EditCalendar,
            CalendarStructuralMutation.RemoveCalendar,
            CalendarStructuralMutation.EnableCalendar,
            CalendarStructuralMutation.DisableCalendar,
        )

        structural.forEach { assertTrue("$it should refresh", it.requiresFullRefresh) }
        assertFalse(CalendarStructuralMutation.ReorderCalendars.requiresFullRefresh)
        assertFalse(CalendarStructuralMutation.SetVisibleInViews.requiresFullRefresh)
    }

    @Test
    fun savedMutationIsReportedWhenRefreshFailsAndReconciliationStillRuns() = runTest {
        var mutationCount = 0
        var reconciliationCount = 0
        val coordinator = SourceCalendarMutationCoordinator(
            includeDisabledProviderCalendars = { true },
            fullRefresh = { includeDisabled ->
                assertTrue(includeDisabled)
                error("offline")
            },
            reconcileLocalState = { reconciliationCount++ },
        )

        val result = coordinator.run(CalendarStructuralMutation.EditSource) { mutationCount++ }

        assertEquals(1, mutationCount)
        assertEquals(1, reconciliationCount)
        assertEquals(PostMutationStage.Refresh, (result as StructuralMutationResult.SavedWithFollowUpFailure).stage)
    }

    @Test
    fun structuralMutationsAreSerializedThroughReconciliation() = runTest {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val coordinator = SourceCalendarMutationCoordinator(
            includeDisabledProviderCalendars = { false },
            fullRefresh = {},
            reconcileLocalState = { calls += "reconcile" },
        )

        val first = async {
            coordinator.run(CalendarStructuralMutation.EditCalendar) {
                calls += "first-start"
                firstEntered.complete(Unit)
                releaseFirst.await()
                calls += "first-end"
            }
        }
        firstEntered.await()
        val second = async {
            coordinator.run(CalendarStructuralMutation.RemoveCalendar) { calls += "second" }
        }
        assertEquals(listOf("first-start"), calls)

        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("first-start", "first-end", "reconcile", "second", "reconcile"), calls)
    }
}
