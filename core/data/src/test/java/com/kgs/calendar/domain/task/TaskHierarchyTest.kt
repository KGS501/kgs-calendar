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
