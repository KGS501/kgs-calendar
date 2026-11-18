package com.kgs.calendar.ui.platform

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable

/** The space the app keeps free at the top for the system's status bar. */
expect val WindowInsets.Companion.appStatusBars: WindowInsets
    @Composable get
