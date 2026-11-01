package com.kgs.calendar.domain.sync

import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.MutationAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingOccurrenceScopeTest {
    @Test
    fun singleAndFollowingScopesRoundTrip() {
        assertEquals("100", PendingOccurrenceScope.single(100))
        assertEquals("100+", PendingOccurrenceScope.following(100))
        assertEquals(PendingOccurrenceScope(occurrences = setOf(100)), PendingOccurrenceScope.decode("100"))
        assertEquals(PendingOccurrenceScope(setOf(100, 200), followingFrom = 300), PendingOccurrenceScope.decode("200,100,300+"))
    }

    @Test
    fun missingOrUnreadableScopeMeansWholeResource() {
        assertNull(PendingOccurrenceScope.decode(null))
        assertNull(PendingOccurrenceScope.decode(""))
        assertNull(PendingOccurrenceScope.decode("100,abc"))
        assertTrue(PendingOccurrenceScope.covers(null) { 42 })
        assertTrue(PendingOccurrenceScope.covers("garbage") { 42 })
    }

    @Test
    fun coversOnlyTheListedOccurrencesAndTheFollowingRange() {
        assertTrue(PendingOccurrenceScope.covers("100") { 100 })
        assertFalse(PendingOccurrenceScope.covers("100") { 200 })
        assertFalse(PendingOccurrenceScope.covers("300+") { 200 })
        assertTrue(PendingOccurrenceScope.covers("300+") { 300 })
        assertTrue(PendingOccurrenceScope.covers("300+") { 400 })
        // An occurrence whose id can't be told is marked rather than silently skipped.
        assertTrue(PendingOccurrenceScope.covers("100") { null })
    }

    @Test
    fun mergeUnitesOccurrenceScopes() {
        assertEquals("100", PendingOccurrenceScope.merge(emptyList(), "100"))
        assertEquals("100,200", PendingOccurrenceScope.merge(listOf("200"), "100"))
        assertEquals("100", PendingOccurrenceScope.merge(listOf("100"), "100"))
        assertEquals("100,300+", PendingOccurrenceScope.merge(listOf("100", "400"), "300+"))
        assertEquals("200+", PendingOccurrenceScope.merge(listOf("300+"), "200+"))
    }

    @Test
    fun mergeWithAnyWholeResourceChangeIsWholeResource() {
        assertNull(PendingOccurrenceScope.merge(emptyList(), null))
        assertNull(PendingOccurrenceScope.merge(listOf("100"), null))
        assertNull(PendingOccurrenceScope.merge(listOf(null), "100"))
        assertNull(PendingOccurrenceScope.merge(listOf("100", null), "200"))
    }

    @Test
    fun pendingMutationForMatchesOnlyCoveredOccurrences() {
        val scoped = mutation(id = 1, href = "/cal/series.ics", scope = "100")
        val other = mutation(id = 2, href = "/cal/other.ics", scope = null)
        val pending = listOf(scoped, other)

        assertSame(scoped, pending.pendingMutationFor("/cal/series.ics") { 100 })
        assertNull(pending.pendingMutationFor("/cal/series.ics") { 200 })
        assertSame(other, pending.pendingMutationFor("/cal/other.ics") { error("whole-resource changes need no id") })
        assertNull(pending.pendingMutationFor("/cal/unknown.ics") { 100 })
    }

    @Test
    fun pendingMutationForPrefersTheNewestCoveringMutation() {
        val put = mutation(id = 1, href = "/cal/series.ics", scope = "100")
        val delete = mutation(id = 2, href = "/cal/series.ics", scope = null, action = MutationAction.Delete)

        assertSame(delete, listOf(put, delete).pendingMutationFor("/cal/series.ics") { 100 })
        assertSame(delete, listOf(put, delete).pendingMutationFor("/cal/series.ics") { 200 })
    }

    private fun mutation(
        id: Long,
        href: String,
        scope: String?,
        action: MutationAction = MutationAction.Put,
    ) = PendingMutationEntity(
        id = id,
        accountId = "primary",
        collectionHref = "/cal/",
        resourceHref = href,
        componentType = ComponentType.Event,
        action = action,
        payloadIcs = null,
        baseEtag = null,
        createdAtMillis = id,
        occurrenceScope = scope,
    )
}
