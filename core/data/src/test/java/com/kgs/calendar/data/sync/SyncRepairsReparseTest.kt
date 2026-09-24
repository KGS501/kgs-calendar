package com.kgs.calendar.data.sync

import com.kgs.calendar.data.LOCAL_COLLECTION
import com.kgs.calendar.data.RepositoryHarness
import com.kgs.calendar.data.SampleIcs
import com.kgs.calendar.data.taskPayload
import com.kgs.calendar.domain.model.ComponentType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncRepairsReparseTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val repairs = harness.components.repairs

    @After
    fun tearDown() = harness.close()

    private suspend fun taskResources() = harness.database.resourceDao().forComponentType(ComponentType.Task)

    @Test
    fun reparseRefreshesAStaleRowAndKeepsItsManualColor() = runTest {
        repository.ensureLocalCalendar()
        repository.createTask(taskPayload("Write report"))
        val task = harness.tasksIn(LOCAL_COLLECTION).single()
        repository.updateTaskManualColor(task.uid, 0xFF445566.toInt())
        harness.database.taskDao().upsert(harness.task(task.uid)!!.copy(title = "Stale"))

        repository.reparseLocalTaskResources()

        val reparsed = harness.task(task.uid)!!
        assertEquals("Write report", reparsed.title)
        assertEquals(0xFF445566.toInt(), reparsed.manualColor)
    }

    @Test
    fun localEditCommittedAfterTheSnapshotIsNotOverwritten() = runTest {
        repository.ensureLocalCalendar()
        repository.createTask(taskPayload("Draft"))
        val uid = harness.tasksIn(LOCAL_COLLECTION).single().uid
        val snapshot = taskResources()

        repository.updateTask(uid, taskPayload("Final", isCompleted = true))
        repairs.reparseResources(snapshot)

        val task = harness.task(uid)!!
        assertEquals("Final", task.title)
        assertTrue(task.isCompleted)
    }

    @Test
    fun queuedCalDavEditCommittedAfterTheSnapshotIsNotOverwritten() = runTest {
        val server = harness.server
        val href = server.putRemote(server.tasksHref, "todo.ics", SampleIcs.task("remote-task", "Write agenda"))
        harness.addSyncedCalDavAccount()
        val snapshot = taskResources()

        repository.updateTask("remote-task", taskPayload("Agenda v2", collectionHref = server.tasksHref))
        repairs.reparseResources(snapshot)

        assertEquals("Agenda v2", harness.task(href)!!.title)
        assertTrue(harness.pendingMutations().any { it.resourceHref == href && "Agenda v2" in it.payloadIcs.orEmpty() })
    }

    @Test
    fun reparseUsesTheCurrentCollectionColor() = runTest {
        repository.ensureLocalCalendar()
        repository.createTask(taskPayload("Water plants"))
        val uid = harness.tasksIn(LOCAL_COLLECTION).single().uid
        val snapshot = taskResources()

        repository.updateCollectionAppearance(LOCAL_COLLECTION, "Private", 0xFF112233.toInt())
        repairs.reparseResources(snapshot)

        assertEquals(0xFF112233.toInt(), harness.task(uid)!!.color)
    }
}
