package com.kgs.calendar.domain.source

import com.kgs.calendar.data.READ_ONLY_PREFIX
import com.kgs.calendar.data.local.entity.CollectionEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorySyncTest {
    private val own = collection("/calendars/me/personal/", readOnly = false)
    private val shared = collection("/calendars/me/shared-by-ann/", readOnly = true)
    private val feed = collection("${READ_ONLY_PREFIX}holidays", readOnly = false)

    @Test
    fun byDefaultOnlyWritableCalendarsShareTheirCategories() {
        val sync = CategorySync()

        assertTrue(sync.usesCategoriesOf(own))
        assertFalse(sync.usesCategoriesOf(shared))
        assertFalse(sync.usesCategoriesOf(feed))
    }

    @Test
    fun theUserCanTurnEitherDefaultAround() {
        val sync = CategorySync(enabledIn = setOf(shared.href), disabledIn = setOf(own.href))

        assertFalse(sync.usesCategoriesOf(own))
        assertTrue(sync.usesCategoriesOf(shared))
        assertFalse(sync.usesCategoriesOf(feed))
    }

    private fun collection(href: String, readOnly: Boolean) = CollectionEntity(
        href = href,
        accountId = "account",
        displayName = href,
        color = 0,
        supportsEvents = true,
        supportsTasks = true,
        syncToken = null,
        ctag = null,
        readOnly = readOnly,
    )
}
