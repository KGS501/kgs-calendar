package com.kgs.calendar.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTaskCardRendererTest {
    private val renderer = WidgetTaskCardRenderer()

    @Test
    fun everyPriorityUsesIdenticalBaseMetrics() {
        listOf(
            KgsWidgetKind.Tasks,
            KgsWidgetKind.Agenda,
            KgsWidgetKind.Day,
            KgsWidgetKind.Multi,
        ).forEach { kind ->
            val baseline = renderer.baseSpec(kind = kind, priority = null)

            (1..9).forEach { priority ->
                assertEquals(baseline, renderer.baseSpec(kind = kind, priority = priority))
            }
        }
    }

    @Test
    fun baseMetricsKeepTypographyCheckboxAndCardGeometryCanonical() {
        val tasks = renderer.baseSpec(KgsWidgetKind.Tasks, priority = 1)
        val agenda = renderer.baseSpec(KgsWidgetKind.Agenda, priority = 9)

        assertEquals(46f, tasks.cardHeightDp)
        assertEquals(14f, tasks.cornerRadiusDp)
        assertEquals(6.9f, tasks.statusRadiusDp)
        assertEquals(1.45f, tasks.statusStrokeDp)
        assertEquals(13f, tasks.titleTextSizeSp)
        assertEquals(11f, tasks.metaTextSizeSp)
        assertEquals(12.3f, agenda.titleTextSizeSp)
        assertEquals(10.2f, agenda.metaTextSizeSp)
    }

    @Test
    fun completionAndHierarchyDoNotRedefineSharedCardMetrics() {
        val active = renderer.baseSpec(
            kind = KgsWidgetKind.Tasks,
            priority = 1,
            depth = 2,
            childCount = 1,
        )
        val completed = renderer.baseSpec(
            kind = KgsWidgetKind.Tasks,
            priority = 1,
            depth = 2,
            childCount = 1,
            completed = true,
        )

        assertEquals(active, completed)
        assertEquals(2, active.hierarchyDepth)
        assertEquals(12f, active.hierarchySideInsetDp)
        assertEquals(18f, active.hierarchyIndentDp)
        assertEquals(1.65f, active.hierarchyLineStrokeDp)
        assertEquals(4.25f, active.chevronHalfWidthDp)
        assertEquals(1.55f, active.chevronStrokeDp)
    }

    @Test
    fun priorityStillChangesEffectStateIncludingScale() {
        val highest = renderer.effect(priority = 1, frame = 5, frameCount = 20, bitmapScale = 1.15f)
        val none = renderer.effect(priority = 9, frame = 5, frameCount = 20, bitmapScale = 1.15f)

        assertNotEquals(highest, none)
        assertTrue(highest.scale > 1f)
        assertTrue(highest.glowSpread > 0f)
        assertEquals(TaskPriorityEffect.None, none)
    }
}
