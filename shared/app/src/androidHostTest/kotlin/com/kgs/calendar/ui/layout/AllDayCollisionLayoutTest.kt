package com.kgs.calendar.ui.layout

import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.ui.buildAllDayOverlayItems
import com.kgs.calendar.ui.calendar.toDayPage
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class AllDayCollisionLayoutTest {
    private val october1 = LocalDate.of(2026, 10, 1)
    private val collisionPage = october1.toDayPage()

    @Test fun collisionGeometryIsContinuousReversibleAndFullWidthAtRest() {
        val scene = scene(-1, 1)
        val group = scene.collapsedLayout.collisions.single()
        fun frame(offset: Float) = requireNotNull(allDayCollisionFrame(
            group, scene.collapsedLayout.segments, collisionPage - 2, offset, 100f, 100f, 300f,
        ))
        val widths = (0..8).map { step ->
            val frame = frame(-25f * step)
            frame.rightX - frame.leftX
        }
        assertEquals(listOf(0f, 25f, 50f, 75f, 100f, 75f, 50f, 25f, 0f), widths)
        assertEquals(100f, frame(-100f).leftX, 0.001f)
        assertEquals(200f, frame(-100f).rightX, 0.001f)
    }

    @Test fun overlappingHandoffDoesNotLeaveAnEventBarOnTheCollisionDay() {
        val scene = scene(-1, 1)
        assertFalse("Event 3 must retract into the overlap card on October 1", scene.collapsedLayout.segments.any {
            it.item.title == "3" && collisionPage in it.startPage..it.endPage
        })
        assertEquals(setOf("3", "4", "5"), scene.hiddenPages.getValue(collisionPage).map { it.title }.toSet())
        assertEquals(4, scene.metrics.collapsedRowCount)
        val overlap = scene.overflowGroups.single { it.page == collisionPage && it.lane == 2 }
        assertTrue(overlap.collision)
        assertEquals(setOf("3", "4", "5"), overlap.items.map { it.title }.toSet())
        assertEquals(3, overlap.restingLane)
        assertEquals(1, scene.overflowGroups.count { it.page == collisionPage })
    }

    @Test fun existingOverflowMovesUpAndBackWithoutSquashingOrDuplicating() {
        val scene = scene(-1, 1)
        val group = scene.overflowGroups.single { it.page == collisionPage }
        fun frame(offset: Float): AllDayOverflowFrame {
            val collision = allDayCollisionFrame(scene.collapsedLayout.collisions.single(),
                scene.collapsedLayout.segments, collisionPage - 2, offset, 100f, 100f, 300f)
            return allDayOverflowFrame(group, collision, 200f + offset, 100f)
        }
        val frames = (0..8).map { frame(-25f * it) }
        assertEquals(listOf(3f, 2.75f, 2.5f, 2.25f, 2f, 2.25f, 2.5f, 2.75f, 3f), frames.map { it.lane })
        frames.forEach { assertEquals(100f, it.rightX - it.leftX, 0.001f) }
    }

    @Test fun emptyRestingRowShrinksWithTheCardAndReturnsOnReverseSwipe() {
        val scene = scene(-1, 1)
        val rows = (0..8).map { step ->
            scene.collapsedRowsAtViewport(collisionPage - 2, -25f * step, 100f, 100f, 300f)
        }
        assertEquals(listOf(4f, 3.75f, 3.5f, 3.25f, 3f, 3.25f, 3.5f, 3.75f, 4f), rows)
    }

    @Test fun aDraftInTheRestingRowPreventsThatRowFromShrinking() {
        val scene = scene(-1, 1)
        assertEquals(4f, scene.collapsedRowsAtViewport(
            collisionPage - 2, -100f, 100f, 100f, 300f, minimumRows = 4), 0.001f)
    }

    @Test fun anotherDaysOverflowKeepsItsRowWhileTheCollisionCardMovesUp() {
        val scene = scene(-1, 1)
        val regular = AllDayOverflowGroup(collisionPage + 1, 3, scene.overflowGroups.single().items)
        val withAnotherOverflow = scene.copy(overflowGroups = scene.overflowGroups + regular)
        assertEquals(4f, withAnotherOverflow.collapsedRowsAtViewport(
            collisionPage - 2, -100f, 100f, 100f, 300f), 0.001f)
    }

    @Test fun collisionWithoutAnExistingOverflowStillSquashesInsideItsRow() {
        val scene = scene(-1, 1)
        val group = scene.collapsedLayout.collisions.single()
        val frames = (0..8).map { step ->
            val collision = allDayCollisionFrame(group, scene.collapsedLayout.segments,
                collisionPage - 2, -25f * step, 100f, 100f, 300f)
            allDayOverflowFrame(group, collision, 200f - 25f * step, 100f)
        }
        assertEquals(listOf(0f, 25f, 50f, 75f, 100f, 75f, 50f, 25f, 0f), frames.map { it.rightX - it.leftX })
        assertTrue(frames.all { it.lane == 2f })
    }

    @Test fun edgeWindowsKeepTheirExistingSelection() {
        val before = scene(-2, 0)
        val after = scene(0, 2)
        assertTrue(before.collapsedLayout.collisions.isEmpty())
        assertTrue(after.collapsedLayout.collisions.isEmpty())
        assertEquals(setOf("1", "2", "3"), before.visibleTitles(collisionPage))
        assertEquals(setOf("1", "2", "4"), after.visibleTitles(collisionPage))
        assertEquals(setOf("4", "5"), before.hiddenPages.getValue(collisionPage).map { it.title }.toSet())
        assertEquals(setOf("3", "5"), after.hiddenPages.getValue(collisionPage).map { it.title }.toSet())
    }

    @Test fun copiesInDifferentCalendarsHaveDistinctStableIdentity() {
        val original = event("copy", -2, 2)
        val copy = original.copy(collectionHref = "other", resourceHref = "other/copy.ics")
        val items = items(listOf(original, copy), -1, 1)
        assertEquals(2, items.map { it.id }.toSet().size)
        assertEquals(items.map { it.id to it.lane }, items(listOf(copy, original), -1, 1).map { it.id to it.lane })
    }

    @Test fun continuingItemsWithLaterEndsAreHigherThanLongerButEndingItems() {
        val items = items(listOf(event("older", -20, 0), event("future", -2, 8)), -1, 1)
        assertTrue(items.single { it.title == "future" }.lane < items.single { it.title == "older" }.lane)
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
                val scene = buildAllDayScene(items, collisionPage - 2, collisionPage + 2,
                    collisionPage - 2, collisionPage + 2, limit)
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

    @Test fun collisionCanSpanSeveralDaysButAdjacentNonOverlappingEventsDoNotCollide() {
        fun make(leftEnd: Int): AllDayScene {
            val items = items(listOf(event("1", -20, 20), event("2", -10, 10),
                event("3", -4, leftEnd), event("4", 0, 8), event("5", 0, 1)), -1, 2)
            return buildAllDayScene(items, collisionPage - 1, collisionPage + 2,
                collisionPage - 1, collisionPage + 2, 4)
        }
        val multiDay = make(1)
        assertEquals(setOf(collisionPage, collisionPage + 1), multiDay.collapsedLayout.collisions.map { it.page }.toSet())
        val frames = multiDay.collapsedLayout.collisions.map { group ->
            requireNotNull(allDayCollisionFrame(group, multiDay.collapsedLayout.segments,
                collisionPage - 1, -50f, 100f, 108f, 424f))
        }
        assertEquals(frames.first().intervalLeftX, frames.last().intervalLeftX, 0.001f)
        assertEquals(frames.first().intervalRightX, frames.last().intervalRightX, 0.001f)
        frames.forEach { assertTrue(it.rightX >= it.leftX) }
        assertTrue(make(-1).collapsedLayout.collisions.isEmpty())
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

    private fun scene(start: Int, end: Int): AllDayScene {
        val items = items(listOf(event("1", -20, 20), event("2", -10, 10), event("3", -4, 0),
            event("4", 0, 8), event("5", 0, 0)), start, end)
        return buildAllDayScene(items, collisionPage + start, collisionPage + end,
            collisionPage + start, collisionPage + end, 4)
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
