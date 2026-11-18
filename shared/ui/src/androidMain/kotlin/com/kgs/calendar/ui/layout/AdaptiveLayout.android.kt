package com.kgs.calendar.ui.layout

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.kgs.calendar.domain.model.CalendarWindowLayout

@Composable
actual fun currentCalendarWindowLayout(): CalendarWindowLayout {
    val configuration = LocalConfiguration.current
    return CalendarWindowLayout(
        isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
    )
}
