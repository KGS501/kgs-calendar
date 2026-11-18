package com.kgs.calendar.ui.layout

import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.ui.buildAllDayOverlayItems
import com.kgs.calendar.ui.calendar.toDayPage
import com.kgs.calendar.time.*
import kotlinx.datetime.*
import kotlinx.datetime.LocalDate
import com.kgs.calendar.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class AllDayCollisionLayoutTest {
    private val october1 = LocalDate.of(2026, 10, 1)
    private val collisionPage = october1.toDayPage()

    @Test fun theOverflowCardStaysInTheBottomRowBelowTheCardsOfItsDay() {
        val scene = collisionScene()
        val bars = scene.collapsedLayout.segments.filter { collisionPage in it.startPage..it.endPage }
        assertEquals(mapOf("1" to 0, "2" to 1, "3" to 2), bars.associate { it.item.title to it.lane })
        val overflow = scene.overflowGroups.single { it.page == collisionPage }
        assertEquals(3, overflow.lane)
        assertEquals(setOf("4", "5", "6"), overflow.items.map { it.title }.toSet())
        assertEquals(4, scene.metrics.collapsedRowCount)
    }

    @Test fun cardsComingOutOfTheOverflowFadeInAndOnlyOneTakesItsRow() {
        val scene = collisionScene()
        val returning = scene.collapsedLayout.segments.filter { it.startPage == collisionPage + 1 && it.item.title in setOf("4", "6") }
        assertEquals(2, returning.size)
        assertTrue(returning.all { it.fromOverflow })
        // The longer one continues in the overflow's row; the other takes the row that became free.
        assertEquals(3, returning.single { it.item.title == "6" }.lane)
        assertEquals(2, returning.single { it.item.title == "4" }.lane)
        assertFalse(scene.collapsedLayout.segments.single { it.item.title == "3" }.intoOverflow)
    }

    @Test fun anOverloadedDayKeepsTheCardsThatStartedFirstWithoutAGap() {
        // November 2026 as in the demo profile: Friday the 6th holds five items in three rows.
        val events = listOf(
            november("Conference", 4, 6), november("test", 4, 6), november("Grandma visiting", 5, 8),
            november("Public holiday", 6, 6), november("Write blog post", 6, 10), november("Laundry day", 7, 7),
            november("Release week", 9, 13),
        )
        val friday = LocalDate.of(2026, 11, 6).toDayPage()
        val scene = novemberScene(events, 3, 9)
        val bars = scene.collapsedLayout.segments.filter { friday in it.startPage..it.endPage }
        assertEquals(mapOf("Conference" to 0, "test" to 1), bars.associate { it.item.title to it.lane })
        val overflow = scene.overflowGroups.single { it.page == friday }
        assertEquals(2, overflow.lane)
        assertEquals(setOf("Grandma visiting", "Public holiday", "Write blog post"), overflow.items.map { it.title }.toSet())
        val grandma = scene.collapsedLayout.segments.filter { it.item.title == "Grandma visiting" }.sortedBy { it.startPage }
        assertEquals(listOf(2, 2), grandma.map { it.lane })
        assertTrue(grandma.first().intoOverflow)
        assertTrue(grandma.last().fromOverflow)
    }

    @Test fun aCardRunningIntoAnOverloadedDayStaysVisibleThere() {
        val events = listOf(
            november("Write blog post", 6, 10), november("Release week", 9, 13), november("Hackathon", 12, 13),
            november("Office closed", 13, 13), november("test 2", 13, 16), november("Laundry day", 14, 14),
        )
        val friday = LocalDate.of(2026, 11, 13).toDayPage()
        val scene = novemberScene(events, 10, 16)
        val release = scene.collapsedLayout.segments.single { it.item.title == "Release week" }
        assertEquals(friday, release.endPage)
        assertFalse(release.intoOverflow)
        assertEquals(2, scene.overflowGroups.single { it.page == friday }.lane)
        val test2 = scene.collapsedLayout.segments.single { it.item.title == "test 2" }
        assertEquals(friday + 1, test2.startPage)
        assertEquals(2, test2.lane)
        assertTrue(test2.fromOverflow)
    }

    @Test fun aDayShowsTheSameItemsWhereverTheWindowStarts() {
        val before = scene(-2, 0)
        val after = scene(0, 2)
        // The items that started first keep the day; the later ones go into its overflow.
        assertEquals(setOf("1", "2", "3"), before.visibleTitles(collisionPage))
        assertEquals(setOf("1", "2", "3"), after.visibleTitles(collisionPage))
        assertEquals(setOf("4", "5"), before.hiddenPages.getValue(collisionPage).map { it.title }.toSet())
        assertEquals(setOf("4", "5"), after.hiddenPages.getValue(collisionPage).map { it.title }.toSet())
    }

    @Test fun copiesInDifferentCalendarsHaveDistinctStableIdentity() {
        val original = event("copy", -2, 2)
        val copy = original.copy(collectionHref = "other", resourceHref = "other/copy.ics")
        val items = items(listOf(original, copy), -1, 1)
        assertEquals(2, items.map { it.id }.toSet().size)
        assertEquals(items.map { it.id to it.lane }, items(listOf(copy, original), -1, 1).map { it.id to it.lane })
    }

    @Test fun earlierItemsKeepTheHigherRowWhenTheyEndFirst() {
        val items = items(listOf(event("older", -20, 0), event("future", -2, 8)), -1, 1)
        assertTrue(items.single { it.title == "older" }.lane < items.single { it.title == "future" }.lane)
    }

    @Test fun scrollingNeverSwapsTheRowsOfTwoVisibleItems() {
        val random = java.util.Random(7)
        repeat(120) { iteration ->
            val events = (0 until 14).map { index ->
                val start = random.nextInt(24) - 12
                event("$index", start, start + random.nextInt(9))
            }
            for (limit in listOf(0, 2, 3, 4)) {
                var previous: AllDayScene? = null
                for (start in -6..6) {
                    val scene = buildAllDayScene(items(events, start, start + 4), collisionPage + start, collisionPage + start + 4, limit)
                    previous?.let { earlier ->
                        assertSameRowOrder("expanded $iteration/$limit/$start", earlier.overlayItems.associate { it.id to it.lane },
                            scene.overlayItems.associate { it.id to it.lane })
                        val sharedPages = (collisionPage + start)..(collisionPage + start + 3)
                        fun rows(of: AllDayScene) = of.collapsedLayout.segments
                            .filter { segment -> sharedPages.any { it in segment.startPage..segment.endPage } }
                            .associate { it.item.id to it.lane }
                        assertSameRowOrder("collapsed $iteration/$limit/$start", rows(earlier), rows(scene))
                    }
                    previous = scene
                }
            }
        }
    }

    private fun assertSameRowOrder(message: String, before: Map<String, Int>, after: Map<String, Int>) {
        val shared = before.keys.intersect(after.keys).toList()
        for (a in shared) for (b in shared) {
            if (before.getValue(a) < before.getValue(b)) {
                assertTrue("$message: $a and $b traded rows", after.getValue(a) < after.getValue(b))
            }
        }
    }

    @Test fun arbitraryWindowsAccountForEveryItemExactlyOnceWithoutExceedingTheRowLimit() {
        val random = java.util.Random(42)
        repeat(150) { iteration ->
            val events = (0 until 12).map { index ->
                val start = random.nextInt(12) - 6
                event("$index", start, start + random.nextInt(8))
            }
            for (limit in 0..6) {
                val items = items(events, -2, 2)
                val scene = buildAllDayScene(items, collisionPage - 2, collisionPage + 2, limit)
                assertTrue("Row budget iteration=$iteration limit=$limit actual=${scene.metrics.collapsedRowCount}",
                    scene.metrics.collapsedRowCount <= limit.coerceAtLeast(1))
                for (page in collisionPage - 2..collisionPage + 2) {
                    val bars = scene.collapsedLayout.segments.filter { page in it.startPage..it.endPage }
                    val groups = scene.overflowGroups.filter { it.page == page }
                    assertTrue("At most one abstraction per day", groups.size <= 1)
                    val represented = bars.map { it.item.id } + groups.flatMap { it.items.map { item -> item.id } }
                    val expected = items.filter { page in it.startPage..it.endPage }.map { it.id }
                    assertEquals("Every item once on $page", expected.sorted(), represented.sorted())
                    val lanes = bars.map { it.lane } + groups.map { it.lane }
                    assertEquals("No overlapping surfaces on $page", lanes.size, lanes.toSet().size)
                }
            }
        }
    }

    @Test fun longAllDayTasksKeepTheirActualEndAndCopiesAndOccurrencesRemainDistinct() {
        val task = TaskEntity(uid = "task", collectionHref = "test", resourceHref = "test/task.ics", title = "Long task",
            notes = null, startAtMillis = october1.minusDays(500).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            dueAtMillis = october1.plusDays(5).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            dueHasTime = false, startHasTime = false, completedAtMillis = null, isCompleted = false, priority = null, color = 0)
        val nextOccurrence = task.copy(startAtMillis = task.startAtMillis!! + 86_400_000L, dueAtMillis = task.dueAtMillis!! + 86_400_000L)
        val items = buildAllDayOverlayItems(emptyList(), listOf(task, task.copy(resourceHref = "other/task.ics"), nextOccurrence),
            TaskColorMode.Collection, collisionPage - 1, collisionPage + 1)
        assertEquals(3, items.size)
        assertEquals(3, items.map { it.id }.toSet().size)
        assertEquals(listOf(collisionPage + 5, collisionPage + 5, collisionPage + 6), items.map { it.endPage }.sorted())
    }

    private fun AllDayScene.visibleTitles(page: Int) = collapsedLayout.segments
        .filter { page in it.startPage..it.endPage }.map { it.item.title }.toSet()

    /**
     * October 1 holds six items in four rows: 1, 2 and 3 started first and keep the day, 4, 5 and 6
     * go into its overflow. 4 and 6 come out of it on October 2.
     */
    private fun collisionScene(): AllDayScene {
        val items = items(listOf(event("1", -20, 20), event("2", -10, 10), event("3", -4, 0),
            event("4", 0, 8), event("5", 0, 0), event("6", 0, 9)), -1, 1)
        return buildAllDayScene(items, collisionPage - 1, collisionPage + 1, 4)
    }

    private fun scene(start: Int, end: Int): AllDayScene {
        val items = items(listOf(event("1", -20, 20), event("2", -10, 10), event("3", -4, 0),
            event("4", 0, 8), event("5", 0, 0)), start, end)
        return buildAllDayScene(items, collisionPage + start, collisionPage + end, 4)
    }

    private fun november(title: String, startDay: Int, endDay: Int): EventEntity =
        event(title, 0, 0).let { base ->
            val zone = ZoneId.systemDefault()
            base.copy(
                startsAtMillis = LocalDate.of(2026, 11, startDay).atStartOfDay(zone).toInstant().toEpochMilli(),
                endsAtMillis = LocalDate.of(2026, 11, endDay + 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }

    private fun novemberScene(events: List<EventEntity>, startDay: Int, endDay: Int): AllDayScene {
        val start = LocalDate.of(2026, 11, startDay).toDayPage()
        val end = LocalDate.of(2026, 11, endDay).toDayPage()
        val items = buildAllDayOverlayItems(events, emptyList(), TaskColorMode.Collection, start, end)
        return buildAllDayScene(items, start, end, 3)
    }

    private fun items(events: List<EventEntity>, start: Int, end: Int) = buildAllDayOverlayItems(
        events, emptyList(), TaskColorMode.Collection, collisionPage + start, collisionPage + end)

    private fun event(title: String, start: Int, end: Int): EventEntity {
        val zone = ZoneId.systemDefault()
        return EventEntity(uid = title, collectionHref = "test", resourceHref = "test/$title.ics",
            title = title, description = null, location = null,
            startsAtMillis = october1.plusDays(start.toLong()).atStartOfDay(zone).toInstant().toEpochMilli(),
            endsAtMillis = october1.plusDays(end.toLong() + 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            allDay = true, recurrenceRule = null, isRecurring = false, color = 0)
    }
}
