package com.kgs.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.domain.source.isReadOnlyCollection
import com.kgs.calendar.domain.source.isReadOnlyCollectionHref

/**
 * The calendars whose items can't be changed: subscriptions, and calendars the server marks
 * read-only. Their cards don't pick up a drag, so they can't be dropped somewhere they won't stay.
 */
val LocalReadOnlyCollectionHrefs = compositionLocalOf<Set<String>> { emptySet() }

fun List<CollectionEntity>.readOnlyHrefs(): Set<String> =
    filter { it.isReadOnlyCollection() }.mapTo(HashSet()) { it.href }

/** Whether an item of [collectionHref] may be moved by dragging it. */
@Composable
fun canMoveItemsOf(collectionHref: String): Boolean =
    !collectionHref.isReadOnlyCollectionHref() && collectionHref !in LocalReadOnlyCollectionHrefs.current
