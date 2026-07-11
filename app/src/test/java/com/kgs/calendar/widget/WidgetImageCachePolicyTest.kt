package com.kgs.calendar.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetImageCachePolicyTest {
    @Test
    fun cacheHitDoesNotCreateValue() {
        var creates = 0

        val value = cachedWidgetValue("cached") {
            creates += 1
            "created"
        }

        assertEquals("cached", value)
        assertEquals(0, creates)
    }

    @Test
    fun cacheMissCreatesValueOnce() {
        var creates = 0

        val value = cachedWidgetValue<String>(null) {
            creates += 1
            "created"
        }

        assertEquals("created", value)
        assertEquals(1, creates)
    }

    @Test
    fun evictionNeverRemovesCurrentOrPreviousGeneration() {
        val entries = listOf(
            WidgetImageCacheEntry("old-a.png", lastModifiedMillis = 1L, bytes = 10L),
            WidgetImageCacheEntry("old-b.png", lastModifiedMillis = 2L, bytes = 10L),
            WidgetImageCacheEntry("previous.png", lastModifiedMillis = 3L, bytes = 10L),
            WidgetImageCacheEntry("current.png", lastModifiedMillis = 4L, bytes = 10L),
        )

        val evictions = selectWidgetImageCacheEvictions(
            entries = entries,
            protectedNames = setOf("previous.png", "current.png"),
            maxFiles = 2,
            maxBytes = 20L,
        )

        assertEquals(listOf("old-a.png", "old-b.png"), evictions)
    }

    @Test
    fun evictionUsesOldestUnprotectedFilesUntilBothCapsAreMet() {
        val entries = listOf(
            WidgetImageCacheEntry("a.png", lastModifiedMillis = 1L, bytes = 40L),
            WidgetImageCacheEntry("b.png", lastModifiedMillis = 2L, bytes = 40L),
            WidgetImageCacheEntry("c.png", lastModifiedMillis = 3L, bytes = 40L),
        )

        val evictions = selectWidgetImageCacheEvictions(
            entries = entries,
            protectedNames = emptySet(),
            maxFiles = 3,
            maxBytes = 70L,
        )

        assertEquals(listOf("a.png", "b.png"), evictions)
    }
}
