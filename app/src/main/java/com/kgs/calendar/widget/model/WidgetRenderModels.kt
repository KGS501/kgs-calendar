package com.kgs.calendar.widget.model

import android.widget.RemoteViews
import com.kgs.calendar.widget.WIDGET_AGENDA_LIST_SIDE_BLEED_DP
import com.kgs.calendar.widget.WIDGET_COLLECTION_RENDER_SIGNATURE_VERSION
import com.kgs.calendar.widget.WIDGET_MONTH_RENDER_SIGNATURE_VERSION
import com.kgs.calendar.widget.WIDGET_TASK_PRIORITY_BITMAP_SCALE
import com.kgs.calendar.widget.WIDGET_TASK_PRIORITY_OVERDRAW_DP
import com.kgs.calendar.widget.theme.WidgetPalette
import java.time.LocalDate

internal data class MonthWidgetRenderResult(
    val views: RemoteViews,
    val hasCompleteData: Boolean,
    val signature: String?,
)

internal data class PreparedMonthWidgetRender(
    val appWidgetId: Int,
    val settings: WidgetRenderSettings,
    val palette: WidgetPalette,
    val today: LocalDate,
    val page: WidgetMonthPage,
    val currentSize: WidgetSize,
    val renderSpec: WidgetMonthRenderSpec,
    val hasCompleteData: Boolean,
    val signature: String?,
) {
    val itemCount: Int
        get() = page.cells.sumOf { cell -> cell.items.size }
}

internal data class PreparedMultiWidgetRender(
    val appWidgetId: Int,
    val settings: WidgetRenderSettings,
    val palette: WidgetPalette,
    val today: LocalDate,
    val page: WidgetMonthPage,
    val size: WidgetSize,
    val monthSpec: WidgetMonthRenderSpec,
    val monthPanelHeightDp: Int,
    val agendaPanelHeightDp: Int,
    val collectionSnapshot: WidgetCollectionSnapshot,
    val signature: String,
) {
    val itemCount: Int
        get() = page.cells.sumOf { cell -> cell.items.size } + collectionSnapshot.rows.size
}

internal fun shouldBuildMonthRemoteViews(
    signature: String?,
    isAlreadyApplied: (String) -> Boolean,
): Boolean = signature == null || !isAlreadyApplied(signature)

internal fun multiWidgetRenderSignature(
    collectionSignature: String,
    monthSignature: String,
    monthPanelHeightDp: Int,
    agendaPanelHeightDp: Int,
): String = buildString {
    append("multi|").append(WIDGET_MONTH_RENDER_SIGNATURE_VERSION)
    append('|').append(monthPanelHeightDp).append('x').append(agendaPanelHeightDp)
    append("|collection:").append(collectionSignature)
    append("|month:").append(monthSignature)
}

internal enum class WidgetMonthChipEdge {
    Rounded,
    Square,
    Fade,
}

internal data class WidgetMonthChipEdges(
    val start: WidgetMonthChipEdge,
    val end: WidgetMonthChipEdge,
)

internal enum class WidgetMonthChipMask {
    RoundRound,
    RoundSquare,
    RoundFade,
    SquareRound,
    SquareSquare,
    SquareFade,
    FadeRound,
    FadeSquare,
    FadeFade,
}

internal fun monthChipEdges(
    continuesFromPrevious: Boolean,
    continuesToNext: Boolean,
    fadesFromPrevious: Boolean,
    fadesToNext: Boolean,
): WidgetMonthChipEdges = WidgetMonthChipEdges(
    start = when {
        fadesFromPrevious -> WidgetMonthChipEdge.Fade
        continuesFromPrevious -> WidgetMonthChipEdge.Square
        else -> WidgetMonthChipEdge.Rounded
    },
    end = when {
        fadesToNext -> WidgetMonthChipEdge.Fade
        continuesToNext -> WidgetMonthChipEdge.Square
        else -> WidgetMonthChipEdge.Rounded
    },
)

internal fun monthChipMask(edges: WidgetMonthChipEdges): WidgetMonthChipMask = when (edges.start) {
    WidgetMonthChipEdge.Rounded -> when (edges.end) {
        WidgetMonthChipEdge.Rounded -> WidgetMonthChipMask.RoundRound
        WidgetMonthChipEdge.Square -> WidgetMonthChipMask.RoundSquare
        WidgetMonthChipEdge.Fade -> WidgetMonthChipMask.RoundFade
    }
    WidgetMonthChipEdge.Square -> when (edges.end) {
        WidgetMonthChipEdge.Rounded -> WidgetMonthChipMask.SquareRound
        WidgetMonthChipEdge.Square -> WidgetMonthChipMask.SquareSquare
        WidgetMonthChipEdge.Fade -> WidgetMonthChipMask.SquareFade
    }
    WidgetMonthChipEdge.Fade -> when (edges.end) {
        WidgetMonthChipEdge.Rounded -> WidgetMonthChipMask.FadeRound
        WidgetMonthChipEdge.Square -> WidgetMonthChipMask.FadeSquare
        WidgetMonthChipEdge.Fade -> WidgetMonthChipMask.FadeFade
    }
}

internal fun collectionRenderSignature(
    dataSignature: String,
    renderSize: WidgetSize,
    taskArtWidthDp: Float,
    priorityFrameCount: Int,
): String =
    "$dataSignature\nrender|$WIDGET_COLLECTION_RENDER_SIGNATURE_VERSION|${renderSize.widthDp}x${renderSize.heightDp}|$taskArtWidthDp|$priorityFrameCount|$WIDGET_TASK_PRIORITY_OVERDRAW_DP|$WIDGET_TASK_PRIORITY_BITMAP_SCALE|$WIDGET_AGENDA_LIST_SIDE_BLEED_DP"

internal fun monthRenderSignature(
    today: LocalDate,
    settings: WidgetRenderSettings,
    palette: WidgetPalette,
    currentSize: WidgetSize,
    renderSpec: WidgetMonthRenderSpec,
    page: WidgetMonthPage,
): String = buildString {
    append(WIDGET_MONTH_RENDER_SIGNATURE_VERSION)
    append('|').append(today.toEpochDay())
    append('|').append(settings.locale.toLanguageTag())
    append('|').append(settings.firstDayOfWeek.name)
    append('|').append(settings.hiddenCollectionHrefs.sorted().joinToString(","))
    append('|').append(settings.showCompletedTasks)
    append('|').append(settings.themeMode.name)
    append('|').append(settings.colorMode.name)
    append('|').append(settings.systemNightMode)
    append('|').append(settings.taskColorMode.name)
    append('|').append(palette.rootBackgroundRes)
    append('|').append(palette.itemBackgroundRes)
    append('|').append(palette.compactItemBackgroundRes)
    append('|').append(palette.daySelectedBackgroundRes)
    append('|').append(palette.text)
    append('|').append(palette.muted)
    append('|').append(palette.onAccent)
    append('|').append(palette.monthTodayTextColor)
    append('|').append(currentSize.widthDp).append('x').append(currentSize.heightDp)
    append('|').append(renderSpec.bucket.name)
    append('|').append(renderSpec.weekCellHeightDp)
    append('|').append(renderSpec.cellContentWidthDp)
    append('|').append(renderSpec.usesDotCells)
    append('|').append(page.month)
    append('|').append(page.rowCount)
    page.cells.forEach { cell ->
        append('|').append(cell.date.toEpochDay())
        append(',').append(cell.inCurrentMonth)
        append(',').append(cell.totalItemCount)
        cell.items.forEach { item ->
            append(';').append(item.id)
            append(',').append(item.title)
            append(',').append(item.color)
            append(',').append(item.sortMillis)
            append(',').append(item.lane)
            append(',').append(item.continuesFromPrevious)
            append(',').append(item.continuesToNext)
            append(',').append(item.fadesFromPrevious)
            append(',').append(item.fadesToNext)
            append(',').append(item.completed)
        }
    }
}
