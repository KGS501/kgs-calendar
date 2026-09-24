package com.kgs.calendar.widget.render

import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.WidgetDependencies
import com.kgs.calendar.widget.WidgetLog
import com.kgs.calendar.widget.data.WidgetSnapshotLoader
import com.kgs.calendar.widget.model.MonthNavSnapshot
import com.kgs.calendar.widget.model.MonthWidgetRenderResult
import com.kgs.calendar.widget.model.PreparedMonthWidgetRender
import com.kgs.calendar.widget.model.PreparedMultiWidgetRender
import com.kgs.calendar.widget.model.WidgetCollectionSnapshot
import com.kgs.calendar.widget.model.WidgetDayAllDaySectionFrameData
import com.kgs.calendar.widget.model.WidgetDayGridCollectionSnapshot
import com.kgs.calendar.widget.model.WidgetMonthPage
import com.kgs.calendar.widget.model.WidgetRenderSettings
import com.kgs.calendar.widget.model.loadingSkeleton
import com.kgs.calendar.widget.model.preparedWidgetValue
import com.kgs.calendar.widget.theme.WidgetPalette
import java.time.ZoneId

internal class KgsWidgetRenderer(
    widgets: WidgetDependencies,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val context = widgets.appContext
    private val snapshots = WidgetSnapshotLoader(widgets, zoneId)
    private val monthPages = WidgetMonthPageBinder(context, zoneId)
    private val dayRenderer = DayWidgetRenderer(context, zoneId, widgets.state.bitmapUris)
    private val monthRenderer = MonthWidgetRenderer(context, monthPages)
    private val multiRenderer = MultiWidgetRenderer(context, zoneId, monthPages, widgets.state.bitmapUris)
    private val collectionRenderer = CollectionWidgetRenderer(context, zoneId, snapshots.dataSource, widgets.state.bitmapUris)

    suspend fun render(
        kind: KgsWidgetKind,
        appWidgetId: Int,
        options: Bundle,
        forceServiceCollection: Boolean = false,
        collectionSnapshot: WidgetCollectionSnapshot? = null,
        preparedMulti: PreparedMultiWidgetRender? = null,
    ): RemoteViews {
        val start = SystemClock.elapsedRealtime()
        val result = when (kind) {
            KgsWidgetKind.Month -> renderMonthUpdate(appWidgetId, options).views
            KgsWidgetKind.Day -> renderDayWidget(appWidgetId, options, forceServiceCollection = forceServiceCollection)
            KgsWidgetKind.Multi -> renderMultiWidget(
                appWidgetId = appWidgetId,
                options = options,
                forceServiceCollection = forceServiceCollection,
                prepared = preparedMulti,
            )
            else -> collectionRenderer.renderCollectionWidget(
                kind = kind,
                appWidgetId = appWidgetId,
                options = options,
                forceServiceCollection = forceServiceCollection,
                collectionSnapshot = collectionSnapshot,
            )
        }
        WidgetLog.d(context, "Rendered ${kind.name} widget $appWidgetId in ${SystemClock.elapsedRealtime() - start}ms")
        return result
    }

    suspend fun renderDayNavigationUpdate(
        appWidgetId: Int,
        options: Bundle,
    ): RemoteViews =
        renderDayWidget(
            appWidgetId = appWidgetId,
            options = options,
            applyInitialScroll = false,
        )


    fun renderDayAllDaySectionFrame(
        frameData: WidgetDayAllDaySectionFrameData,
        expansionProgress: Float,
    ): RemoteViews = dayRenderer.renderDayAllDaySectionFrame(frameData, expansionProgress)

    suspend fun dayAllDaySectionFrameData(
        appWidgetId: Int,
        options: Bundle,
    ): WidgetDayAllDaySectionFrameData = snapshots.dayAllDaySectionFrameData(appWidgetId, options)

    suspend fun prepareMonthUpdate(appWidgetId: Int, options: Bundle): PreparedMonthWidgetRender =
        snapshots.prepareMonthWidget(appWidgetId, options)

    suspend fun prepareMultiUpdate(appWidgetId: Int, options: Bundle): PreparedMultiWidgetRender =
        snapshots.prepareMultiWidget(appWidgetId, options)

    fun renderMonthUpdate(prepared: PreparedMonthWidgetRender): MonthWidgetRenderResult =
        monthRenderer.renderPreparedMonthPage(prepared)

    suspend fun renderMonthUpdate(appWidgetId: Int, options: Bundle): MonthWidgetRenderResult =
        monthRenderer.renderPreparedMonthPage(snapshots.prepareMonthWidget(appWidgetId, options))

    fun renderMonthNavigationPage(
        kind: KgsWidgetKind,
        snapshot: MonthNavSnapshot,
        options: Bundle,
        settings: WidgetRenderSettings,
        page: WidgetMonthPage,
        hasCompleteData: Boolean,
    ): MonthWidgetRenderResult {
        return when (kind) {
            KgsWidgetKind.Month -> monthRenderer.renderPreparedMonthPage(
                snapshots.prepareMonthPage(
                    appWidgetId = snapshot.widgetId,
                    options = options,
                    settings = settings,
                    page = page,
                    hasCompleteData = hasCompleteData,
                ),
            )
            KgsWidgetKind.Multi -> {
                val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
                val renderedPage = if (hasCompleteData) page else page.loadingSkeleton(palette.muted)
                MonthWidgetRenderResult(
                    views = multiRenderer.renderMultiMonthNavigationPage(snapshot.widgetId, options, settings, renderedPage),
                    hasCompleteData = hasCompleteData,
                    signature = null,
                )
            }
            else -> error("Month navigation is unsupported for ${kind.name}")
        }
    }


    suspend fun dayGridCollectionSnapshot(appWidgetId: Int): WidgetDayGridCollectionSnapshot =
        snapshots.dayGridCollectionSnapshot(appWidgetId)

    suspend fun collectionSnapshot(kind: KgsWidgetKind, appWidgetId: Int): WidgetCollectionSnapshot? =
        snapshots.collectionSnapshot(kind, appWidgetId)

    fun error(kind: KgsWidgetKind, appWidgetId: Int, message: String): RemoteViews =
        collectionRenderer.error(kind, appWidgetId, message)

    private suspend fun renderDayWidget(
        appWidgetId: Int,
        options: Bundle,
        applyInitialScroll: Boolean = true,
        forceServiceCollection: Boolean = false,
    ): RemoteViews = dayRenderer.renderDayWidget(
        prepared = snapshots.prepareDayWidget(appWidgetId, options),
        applyInitialScroll = applyInitialScroll,
        forceServiceCollection = forceServiceCollection,
    )

    private suspend fun renderMultiWidget(
        appWidgetId: Int,
        options: Bundle,
        forceServiceCollection: Boolean = false,
        prepared: PreparedMultiWidgetRender? = null,
    ): RemoteViews {
        val renderData = preparedWidgetValue(prepared) {
            snapshots.prepareMultiWidget(appWidgetId, options)
        }
        return multiRenderer.renderMultiWidget(appWidgetId, renderData, forceServiceCollection)
    }
}
