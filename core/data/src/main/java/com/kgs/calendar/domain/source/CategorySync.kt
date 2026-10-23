package com.kgs.calendar.domain.source

import com.kgs.calendar.data.local.entity.CollectionEntity

/**
 * Which calendars' categories join the user's own categories, the suggestions offered when
 * tagging an event or task. By default only calendars the user can write to do: a read-only
 * calendar, such as one shared by someone else, keeps its owner's categories to itself. The user
 * can turn either default around per calendar; only those choices are stored.
 */
data class CategorySync(
    val enabledIn: Set<String> = emptySet(),
    val disabledIn: Set<String> = emptySet(),
) {
    fun usesCategoriesOf(collection: CollectionEntity): Boolean = when (collection.href) {
        in enabledIn -> true
        in disabledIn -> false
        else -> usesCategoriesByDefault(collection)
    }

    companion object {
        fun usesCategoriesByDefault(collection: CollectionEntity): Boolean = !collection.isReadOnlyCollection()
    }
}
