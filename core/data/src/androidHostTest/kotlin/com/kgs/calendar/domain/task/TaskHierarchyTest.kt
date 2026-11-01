package com.kgs.calendar.domain.task

import com.kgs.calendar.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskHierarchyTest {
    @Test
    fun parentInTheSameCollectionWinsOverAGloballyUniqueUid() {
        val localParent = task("parent", collection = "a")
        val child = task("child", collection = "a", parent = "parent")
        val otherCollectionParent = task("parent", collection = "b")

        assertEquals(localParent, TaskParentLookup(listOf(child, otherCollectionParent, localParent)).parentOf(child))
    }

    @Test
    fun parentInAnotherCollectionIsOnlyUsedWhenItsUidIsUnique() {
        val child = task("child", collection = "a", parent = "parent")
        val remoteParent = task("parent", collection = "b")

        assertEquals(remoteParent, TaskParentLookup(listOf(child, remoteParent)).parentOf(child))
        assertNull(TaskParentLookup(listOf(child, remoteParent, task("parent", collection = "c"))).parentOf(child))
        assertNull(TaskParentLookup(listOf(task("x", parent = " "))).parentOf(task("x", parent = " ")))
    }

    @Test
    fun ancestorsStopAtCycles() {
        val a = task("a", parent = "c")
        val b = task("b", parent = "a")
        val c = task("c", parent = "b")
        val lookup = TaskParentLookup(listOf(a, b, c))

        assertEquals(listOf("b", "a", "c"), lookup.selfAndAncestors(b).map { it.uid })
        assertEquals("c", lookup.rootOf(b).uid)
    }

    @Test
    fun rootOfWalksToTheTopmostAncestor() {
        val root = task("root")
        val middle = task("middle", parent = "root")
        val leaf = task("leaf", parent = "middle")
        val lookup = TaskParentLookup(listOf(leaf, middle, root))

        assertEquals(listOf("leaf", "middle", "root"), lookup.selfAndAncestors(leaf).map { it.uid })
        assertEquals(root, lookup.rootOf(leaf))
        assertEquals(root, lookup.rootOf(root))
    }

    @Test
    fun treeParentsDropParentsForTasksInACycle() {
        val root = task("root")
        val child = task("child", parent = "root")
        val orphan = task("orphan", parent = "missing")
        val loopA = task("loopA", parent = "loopB")
        val loopB = task("loopB", parent = "loopA")

        val parents = listOf(root, child, orphan, loopA, loopB).treeParents { it.resourceHref }

        assertNull(parents.getValue(root.resourceHref))
        assertEquals(root, parents.getValue(child.resourceHref))
        assertNull(parents.getValue(orphan.resourceHref))
        assertNull(parents.getValue(loopA.resourceHref))
        assertNull(parents.getValue(loopB.resourceHref))
    }

    @Test
    fun hiddenCompletedTasksDropClosedSubtasksButKeepOpenOnes() {
        val parent = task("parent")
        val open = task("open", parent = "parent")
        val done = task("done", parent = "parent").copy(isCompleted = true, status = "COMPLETED")
        val cancelled = task("cancelled", parent = "parent").copy(status = "CANCELLED")
        val tasks = listOf(parent, open, done, cancelled)

        assertEquals(listOf("parent", "open"), tasks.withoutHiddenClosedSubtasks(showCompleted = false).map { it.uid })
        assertEquals(tasks, tasks.withoutHiddenClosedSubtasks(showCompleted = true))
    }

    @Test
    fun parentWhoseSubtasksAreAllDoneIsLeftWithoutChildren() {
        val parent = task("parent")
        val first = task("first", parent = "parent").copy(isCompleted = true)
        val second = task("second", parent = "parent").copy(isCompleted = true)

        val shown = listOf(parent, first, second).withoutHiddenClosedSubtasks(showCompleted = false)

        assertEquals(listOf(parent), shown)
        assertEquals(emptyList<TaskEntity>(), shown.filter { TaskParentLookup(shown).parentOf(it) != null })
    }

    @Test
    fun closedSubtaskStaysWhileItHasAnOpenDescendantAndClosedRootsAreKept() {
        val closedRoot = task("root").copy(isCompleted = true)
        val closedMiddle = task("middle", parent = "root").copy(isCompleted = true)
        val openLeaf = task("leaf", parent = "middle")
        val closedSibling = task("sibling", parent = "middle").copy(isCompleted = true)

        val shown = listOf(closedRoot, closedMiddle, openLeaf, closedSibling).withoutHiddenClosedSubtasks(showCompleted = false)

        assertEquals(listOf("root", "middle", "leaf"), shown.map { it.uid })
    }

    private fun task(uid: String, collection: String = "a", parent: String? = null) = TaskEntity(
        uid = uid,
        collectionHref = collection,
        resourceHref = "$collection/$uid.ics",
        title = uid,
        notes = null,
        dueAtMillis = null,
        startAtMillis = null,
        completedAtMillis = null,
        isCompleted = false,
        priority = null,
        parentUid = parent,
        color = 0,
    )
}
