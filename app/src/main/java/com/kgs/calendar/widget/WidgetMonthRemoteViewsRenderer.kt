package com.kgs.calendar.widget

import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId

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
