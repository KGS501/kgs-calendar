package com.kgs.calendar.ui.platform

import androidx.compose.runtime.Composable

/**
 * Calls [onBack] when the user goes back while [enabled]: the system back gesture on Android,
 * Escape or the mouse's back button on the desktop. The most recently composed enabled handler wins.
 */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
