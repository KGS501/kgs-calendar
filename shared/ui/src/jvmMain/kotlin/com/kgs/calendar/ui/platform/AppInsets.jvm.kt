package com.kgs.calendar.ui.platform

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable

actual val WindowInsets.Companion.appStatusBars: WindowInsets
    @Composable get() = statusBars
