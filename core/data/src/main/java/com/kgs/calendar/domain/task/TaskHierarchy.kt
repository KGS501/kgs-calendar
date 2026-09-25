package com.kgs.calendar.domain.task

import com.kgs.calendar.data.local.entity.TaskEntity

/**
 * Resolves RELATED-TO parents within [tasks]: a parent in the same collection wins, otherwise a
 * task whose UID is unique across [tasks].
 */
class TaskParentLookup(tasks: List<TaskEntity>) {
    private val byCollectionUid = tasks.associateBy { it.collectionHref to it.uid }
    private val globallyUniqueByUid = tasks.groupBy { it.uid }
        .filterValues { it.size == 1 }
        .mapValues { it.value.single() }

    fun parentOf(task: TaskEntity): TaskEntity? {
        val parentUid = task.parentUid?.takeIf { it.isNotBlank() } ?: return null
        return byCollectionUid[task.collectionHref to parentUid] ?: globallyUniqueByUid[parentUid]
    }

    /** [task] followed by its ancestors, stopping before the first resource seen twice. */
    fun selfAndAncestors(task: TaskEntity): List<TaskEntity> {
        val chain = mutableListOf<TaskEntity>()
        val seen = mutableSetOf<String>()
        var cursor: TaskEntity? = task
        while (cursor != null && seen.add(cursor.resourceHref)) {
            chain += cursor
            cursor = parentOf(cursor)
        }
        return chain
    }

    /** The topmost ancestor of [task], or the last task before a parent cycle. */
    fun rootOf(task: TaskEntity): TaskEntity = selfAndAncestors(task).last()
}

/**
 * Parent of every task for a tree display, keyed by [identity]. Tasks whose ancestry loops back
 * on itself get no parent, so a cycle is shown as separate roots instead of disappearing.
 * [this] must already be distinct by [identity].
 */
fun <K> List<TaskEntity>.treeParents(identity: (TaskEntity) -> K): Map<K, TaskEntity?> {
    val lookup = TaskParentLookup(this)
    return associate { task ->
        var parent = lookup.parentOf(task)
        val seen = mutableSetOf(identity(task))
        while (parent != null && seen.add(identity(parent))) {
            parent = lookup.parentOf(parent)
        }
        identity(task) to if (parent == null) lookup.parentOf(task) else null
    }
}

/**
 * Applies "Show completed tasks" to subtasks: when [showCompleted] is off, subtasks that are done
 * or cancelled are left out, unless one of their own descendants is still shown, so open work keeps
 * its place in the tree. A parent whose subtasks are all left out then has no children at all.
 * Top-level tasks (no parent in this list) are never removed here; each surface filters those itself.
 */
fun List<TaskEntity>.withoutHiddenClosedSubtasks(showCompleted: Boolean): List<TaskEntity> {
    if (showCompleted || none { it.isInactive() }) return this
    val lookup = TaskParentLookup(this)
    val shownResourceHrefs = mutableSetOf<String>()
    forEach { task ->
        if (!task.isInactive() || lookup.parentOf(task) == null) {
            lookup.selfAndAncestors(task).mapTo(shownResourceHrefs) { it.resourceHref }
        }
    }
    return filter { it.resourceHref in shownResourceHrefs }
}
