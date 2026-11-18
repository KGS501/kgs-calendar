package com.kgs.calendar.ui.platform

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.useContents
import platform.UIKit.UIApplication

/** Status bars this low (iPad, iPhones without a notch) leave no room above the app's toolbar. */
private const val SHORT_STATUS_BAR_POINTS = 40.0
private val ShortStatusBarGap = 8.dp

/**
 * The window's own safe area, in every orientation: iPads keep their status bar in landscape.
 * It is read again when the window changes size, as on a rotation or in Split View.
 */
actual val WindowInsets.Companion.appStatusBars: WindowInsets
    @Composable get() {
        val windowSize = LocalWindowInfo.current.containerSize
        val density = LocalDensity.current
        val composeTop = with(density) { statusBars.getTop(density).toDp() }
        return remember(windowSize, composeTop) {
            val windowTop = UIApplication.sharedApplication.keyWindow?.safeAreaInsets?.useContents { top } ?: 0.0
            val top = maxOf(windowTop, composeTop.value.toDouble())
            val gap = if (top > 0.0 && top < SHORT_STATUS_BAR_POINTS) ShortStatusBarGap else 0.dp
            WindowInsets(top = top.dp + gap)
        }
    }
