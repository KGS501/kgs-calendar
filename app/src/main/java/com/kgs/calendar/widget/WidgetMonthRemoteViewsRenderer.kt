package com.kgs.calendar.widget

import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import java.time.ZoneId

internal data class MonthWidgetRenderResult(
    val views: RemoteViews,
    val hasCompleteData: Boolean,
    val signature: String?,
)

internal class WidgetMonthRemoteViewsRenderer(
    context: Context,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val delegate = KgsWidgetRenderer(context, zoneId)

    fun render(
        kind: KgsWidgetKind,
        snapshot: MonthNavSnapshot,
        options: Bundle,
        settings: WidgetRenderSettings,
        page: WidgetMonthPage,
        hasCompleteData: Boolean,
    ): MonthWidgetRenderResult =
        delegate.renderMonthNavigationPage(
            kind = kind,
            snapshot = snapshot,
            options = options,
            settings = settings,
            page = page,
            hasCompleteData = hasCompleteData,
        )
}
