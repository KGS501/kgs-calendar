package com.kgs.calendar.ui.haptics

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.kgs.calendar.ui.timeline.TimelineDropTarget
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KgsHapticsTest {
    private class FakePerformer : KgsHapticPerformer {
        val cues = mutableListOf<KgsHapticCue>()
        override fun perform(cue: KgsHapticCue) {
            cues += cue
        }
    }

    @Test
    fun settingOffPerformsNothing() {
        val performer = FakePerformer()
        val haptics = KgsHaptics(isEnabled = { false }, performer = performer)

        haptics.dragStart()
        haptics.dragSnap()
        haptics.drop()
        haptics.taskToggled(completed = true)
        haptics.taskToggled(completed = false)
        KgsHapticCue.entries.forEach(haptics::perform)

        assertEquals(emptyList<KgsHapticCue>(), performer.cues)
        assertFalse(haptics.enabled)
    }

    @Test
    fun settingOnPerformsEachCue() {
        val performer = FakePerformer()
        val haptics = KgsHaptics(isEnabled = { true }, performer = performer)

        haptics.dragStart()
        haptics.dragSnap()
        haptics.drop()
        haptics.taskToggled(completed = true)
        haptics.taskToggled(completed = false)

        assertEquals(
            listOf(
                KgsHapticCue.DragStart,
                KgsHapticCue.DragSnap,
                KgsHapticCue.Drop,
                KgsHapticCue.TaskCompleted,
                KgsHapticCue.TaskReopened,
            ),
            performer.cues,
        )
        assertTrue(haptics.enabled)
    }

    @Test
    fun theSettingIsReadOnEveryCue() {
        val performer = FakePerformer()
        var enabled = true
        val haptics = KgsHaptics(isEnabled = { enabled }, performer = performer)

        haptics.drop()
        enabled = false
        haptics.drop()
        haptics.dragSnap()
        enabled = true
        haptics.dragSnap()

        assertEquals(listOf(KgsHapticCue.Drop, KgsHapticCue.DragSnap), performer.cues)
    }

    @Test
    fun defaultHapticsAreOff() {
        assertFalse(KgsHaptics.Off.enabled)
        KgsHaptics.Off.drop()
    }

    @Test
    fun cuesUseSubtleSystemFeedbackTypes() {
        assertEquals(HapticFeedbackType.LongPress, KgsHapticCue.DragStart.feedbackType)
        assertEquals(HapticFeedbackType.SegmentTick, KgsHapticCue.DragSnap.feedbackType)
        assertEquals(HapticFeedbackType.Confirm, KgsHapticCue.Drop.feedbackType)
        assertEquals(HapticFeedbackType.ToggleOn, KgsHapticCue.TaskCompleted.feedbackType)
        assertEquals(HapticFeedbackType.ToggleOff, KgsHapticCue.TaskReopened.feedbackType)
    }

    @Test
    fun snapTrackerTicksOnlyWhenTheSlotChanges() {
        val date = LocalDate.of(2026, 9, 26)
        val tracker = DragSnapTracker()

        assertFalse(tracker.onSlot(TimelineDropTarget.Timed(date, 9 * 60, 10 * 60)))
        assertFalse(tracker.onSlot(TimelineDropTarget.Timed(date, 9 * 60, 10 * 60)))
        assertTrue(tracker.onSlot(TimelineDropTarget.Timed(date, 9 * 60 + 15, 10 * 60 + 15)))
        assertTrue(tracker.onSlot(TimelineDropTarget.Timed(date.plusDays(1), 9 * 60 + 15, 10 * 60 + 15)))
        assertTrue(tracker.onSlot(TimelineDropTarget.AllDay(date.plusDays(1), lane = 0)))
        assertFalse(tracker.onSlot(TimelineDropTarget.AllDay(date.plusDays(1), lane = 0)))
    }

    @Test
    fun aFreshTrackerNeverTicksOnTheDragStartSlot() {
        assertFalse(DragSnapTracker().onSlot(0 to 540))
    }
}
