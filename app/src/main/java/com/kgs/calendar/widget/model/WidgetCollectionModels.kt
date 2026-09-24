package com.kgs.calendar.widget.model

import android.os.SystemClock
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.WIDGET_TASK_ART_WIDTH_DP
import com.kgs.calendar.widget.theme.WidgetPalette

internal data class WidgetCollectionSnapshot(
    val kind: KgsWidgetKind,
    val appWidgetId: Int,
    val settings: WidgetRenderSettings,
    val palette: WidgetPalette,
    val renderSize: WidgetSize,
    val taskArtWidthDp: Float,
    val rows: List<WidgetListRow>,
    val signature: String,
    val createdAtMillis: Long = SystemClock.elapsedRealtime(),
)

internal data class WidgetCollectionRenderOptions(
    val taskRows: Map<Long, WidgetTaskRowRenderOptions> = emptyMap(),
    val suppressPriorityMotion: Boolean = false,
    val lightweightTaskTransition: Boolean = false,
    val taskArtWidthDp: Float = WIDGET_TASK_ART_WIDTH_DP.toFloat(),
) {
    fun withTaskArtWidth(widthDp: Float): WidgetCollectionRenderOptions =
        copy(taskArtWidthDp = widthDp.coerceAtLeast(1f))
}

internal data class WidgetTaskRowRenderOptions(
    val rowProgress: Float = 1f,
    val subtaskExpansionProgress: Float? = null,
)
