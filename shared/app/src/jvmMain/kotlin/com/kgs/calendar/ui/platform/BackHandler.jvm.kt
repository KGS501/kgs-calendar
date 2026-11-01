package com.kgs.calendar.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The window's back handlers, most recent last. The window sends Escape and the mouse's back
 * button here; the most recently registered enabled handler consumes it.
 */
class BackDispatcher {
    private class Entry(var enabled: Boolean, var onBack: () -> Unit)

    private val entries = mutableListOf<Entry>()

    /** Goes back once. Returns false when nothing handled it. */
    fun back(): Boolean {
        val entry = entries.lastOrNull { it.enabled } ?: return false
        entry.onBack()
        return true
    }

    @Composable
    internal fun Register(enabled: Boolean, onBack: () -> Unit) {
        val currentOnBack = rememberUpdatedState(onBack)
        val entry = remember { Entry(enabled) { currentOnBack.value() } }
        SideEffect { entry.enabled = enabled }
        DisposableEffect(entry) {
            entries += entry
            onDispose { entries -= entry }
        }
    }
}

val LocalBackDispatcher = staticCompositionLocalOf { BackDispatcher() }

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) =
    LocalBackDispatcher.current.Register(enabled, onBack)
