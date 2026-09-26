package com.kgs.calendar.ui.layout

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kgs.calendar.domain.model.CalendarWindowLayout

/** Bottom sheets (editor, details) never grow wider than this; the Material guideline for large screens. */
internal val SheetMaxWidth = 640.dp

/** Settings pages keep readable line lengths on tablets. */
internal val SettingsContentMaxWidth = 720.dp

/** Agenda and search result lists; wide enough for the date column plus comfortable cards. */
internal val ListContentMaxWidth = 840.dp

/** The welcome screen's logo, question and two large buttons. */
internal val WelcomeContentMaxWidth = 560.dp

/** The current app window, from the (possibly split-screen or freeform) window configuration. */
@Composable
@ReadOnlyComposable
internal fun currentCalendarWindowLayout(): CalendarWindowLayout {
    val configuration = LocalConfiguration.current
    return CalendarWindowLayout(
        isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
    )
}

/**
 * Fills the available width up to [maxWidth] and centres the result. When the parent is not wider than
 * [maxWidth] (every phone) this measures and places exactly like `fillMaxWidth()`.
 */
internal fun Modifier.centeredMaxWidth(maxWidth: Dp): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = maxWidth)
    .fillMaxWidth()

/**
 * Horizontal padding that keeps content of at most [maxWidth] centred in [availableWidth] while the
 * scrollable container itself still spans the full width. Returns [basePadding] whenever the content fits.
 */
internal fun centeredContentPadding(availableWidth: Dp, maxWidth: Dp, basePadding: Dp): Dp =
    basePadding + ((availableWidth - maxWidth) / 2).coerceAtLeast(0.dp)
