package com.kgs.calendar.ui.haptics

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The few moments that get haptic feedback. Plain taps and navigation never do.
 *
 * Each cue maps to a Compose [HapticFeedbackType], which goes through
 * `View.performHapticFeedback`: the system touch-feedback setting applies, and the newer
 * constants fall back to older ones on devices before API 30/34.
 */
enum class KgsHapticCue {
    /** A long-press picked up a timeline event or task for dragging. */
    DragStart,

    /** A dragged item moved to another time slot, day or all-day lane. */
    DragSnap,

    /** A dragged item was dropped. */
    Drop,

    /** A task was ticked complete. */
    TaskCompleted,

    /** A completed task was ticked open again. */
    TaskReopened,
}

fun interface KgsHapticPerformer {
    fun perform(cue: KgsHapticCue)
}

/**
 * The single entry point for haptics in the app. Every cue checks the app's "Haptic feedback"
 * setting first, so nothing buzzes when it is off.
 */
@Stable
class KgsHaptics(
    private val isEnabled: () -> Boolean,
    private val performer: KgsHapticPerformer,
) {
    val enabled: Boolean
        get() = isEnabled()

    fun dragStart() = perform(KgsHapticCue.DragStart)

    fun dragSnap() = perform(KgsHapticCue.DragSnap)

    fun drop() = perform(KgsHapticCue.Drop)

    fun taskToggled(completed: Boolean) =
        perform(if (completed) KgsHapticCue.TaskCompleted else KgsHapticCue.TaskReopened)

    fun perform(cue: KgsHapticCue) {
        if (!isEnabled()) return
        // Enable with `adb shell setprop log.tag.KgsHaptics DEBUG` to verify cues on an emulator.
        if (runCatching { Log.isLoggable(LOG_TAG, Log.DEBUG) }.getOrDefault(false)) {
            Log.d(LOG_TAG, "perform $cue")
        }
        performer.perform(cue)
    }

    companion object {
        const val LOG_TAG = "KgsHaptics"

        /** Used where no app provides haptics, e.g. isolated previews and tests. */
        val Off = KgsHaptics(isEnabled = { false }, performer = {})
    }
}

internal val KgsHapticCue.feedbackType: HapticFeedbackType
    get() = when (this) {
        KgsHapticCue.DragStart -> HapticFeedbackType.LongPress
        KgsHapticCue.DragSnap -> HapticFeedbackType.SegmentTick
        KgsHapticCue.Drop -> HapticFeedbackType.Confirm
        KgsHapticCue.TaskCompleted -> HapticFeedbackType.ToggleOn
        KgsHapticCue.TaskReopened -> HapticFeedbackType.ToggleOff
    }

val LocalKgsHaptics = staticCompositionLocalOf { KgsHaptics.Off }

/** App haptics backed by the platform's [LocalHapticFeedback]; [enabled] is read on every cue. */
@Composable
fun rememberKgsHaptics(enabled: Boolean): KgsHaptics {
    val platform = LocalHapticFeedback.current
    val currentEnabled = rememberUpdatedState(enabled)
    return remember(platform) {
        KgsHaptics(
            isEnabled = { currentEnabled.value },
            performer = { cue -> platform.performHapticFeedback(cue.feedbackType) },
        )
    }
}

/**
 * Tells when a drag lands in a new snap slot. The first slot is where the drag started, so it
 * never ticks. Use one tracker per drag.
 */
internal class DragSnapTracker {
    private var lastSlot: Any? = null

    fun onSlot(slot: Any): Boolean {
        val previous = lastSlot
        lastSlot = slot
        return previous != null && previous != slot
    }
}

/**
 * Ticks once per snap step of an active drag. Compose it only while the drag is active, so every
 * drag starts with a fresh tracker and idle items cost nothing.
 */
@Composable
internal fun DragSnapHaptics(slot: Any) {
    val haptics = LocalKgsHaptics.current
    val tracker = remember { DragSnapTracker() }
    LaunchedEffect(slot) {
        if (tracker.onSlot(slot)) haptics.dragSnap()
    }
}
