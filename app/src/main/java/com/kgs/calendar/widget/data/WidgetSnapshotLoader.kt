package com.kgs.calendar.widget.data

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.os.SystemClock
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.WidgetDependencies
import com.kgs.calendar.widget.WIDGET_DAY_LIST_SIDE_BLEED_DP
import com.kgs.calendar.widget.WIDGET_MULTI_CONTENT_PADDING_DP
import com.kgs.calendar.widget.WidgetLog
import com.kgs.calendar.widget.model.PreparedDayWidgetRender
import com.kgs.calendar.widget.model.PreparedMonthWidgetRender
import com.kgs.calendar.widget.model.PreparedMultiWidgetRender
import com.kgs.calendar.widget.model.WidgetCollectionSnapshot
import com.kgs.calendar.widget.model.WidgetDayAllDaySectionFrameData
import com.kgs.calendar.widget.model.WidgetDayGridCollectionSnapshot
import com.kgs.calendar.widget.model.WidgetMonthPage
import com.kgs.calendar.widget.model.WidgetMonthRenderSpec
import com.kgs.calendar.widget.model.WidgetRenderSettings
import com.kgs.calendar.widget.model.WidgetSize
import com.kgs.calendar.widget.model.collectionArtWidthDp
import com.kgs.calendar.widget.model.collectionRenderSignature
import com.kgs.calendar.widget.model.dayGridContentWidthDp
import com.kgs.calendar.widget.model.loadingSkeleton
import com.kgs.calendar.widget.model.monthRenderSignature
import com.kgs.calendar.widget.model.multiWidgetRenderSignature
import com.kgs.calendar.widget.model.preparedWidgetValue
import com.kgs.calendar.widget.model.priorityMotionFrameCount
import com.kgs.calendar.widget.model.usesCollectionList
import com.kgs.calendar.widget.model.widgetDayGridRows
import com.kgs.calendar.widget.theme.WidgetPalette
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class WidgetSnapshotLoader(
    private val widgets: WidgetDependencies,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val context = widgets.appContext
    private val state = widgets.state
    val dataSource = widgets.dataSource(zoneId)
    private val monthPageSource = widgets.monthPageSource(zoneId)

    suspend fun prepareDayWidget(appWidgetId: Int, options: Bundle): PreparedDayWidgetRender {
        val settings = dataSource.loadSettings(KgsWidgetKind.Day)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val today = LocalDate.now(zoneId)
        val day = state.day.day(appWidgetId, today)
        val size = WidgetSize.from(context, options, KgsWidgetKind.Day)
        val timeline = dataSource.dayTimeline(day, settings)
        val useCurrentHourStart = settings.dayWidgetStartAtCurrentHour && day == today
        val nextTimeline = if (useCurrentHourStart) {
            dataSource.dayTimeline(day.plusDays(1), settings)
        } else {
            null
        }
        return PreparedDayWidgetRender(
            appWidgetId = appWidgetId,
            settings = settings,
            palette = palette,
            today = today,
            day = day,
            size = size,
            timeline = timeline,
            gridRows = widgetDayGridRows(timeline, settings, nextTimeline, zoneId),
            allDayExpanded = state.day.isAllDayExpanded(appWidgetId),
        )
    }


    suspend fun dayAllDaySectionFrameData(
        appWidgetId: Int,
        options: Bundle,
    ): WidgetDayAllDaySectionFrameData {
        val settings = dataSource.loadSettings(KgsWidgetKind.Day)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val today = LocalDate.now(zoneId)
        val day = state.day.day(appWidgetId, today)
        val size = WidgetSize.from(context, options, KgsWidgetKind.Day)
        val timeline = dataSource.dayTimeline(day, settings)
        return WidgetDayAllDaySectionFrameData(
            appWidgetId = appWidgetId,
            settings = settings,
            palette = palette,
            timeline = timeline,
            contentWidthDp = size.dayGridContentWidthDp(),
            allDayExpanded = state.day.isAllDayExpanded(appWidgetId),
        )
    }

    suspend fun dayGridCollectionSnapshot(appWidgetId: Int): WidgetDayGridCollectionSnapshot {
        val settings = dataSource.loadSettings(KgsWidgetKind.Day)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val day = state.day.day(appWidgetId, LocalDate.now(zoneId))
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val size = WidgetSize.from(context, options, KgsWidgetKind.Day)
        val timeline = dataSource.dayTimeline(day, settings)
        val nextTimeline = if (settings.dayWidgetStartAtCurrentHour && day == LocalDate.now(zoneId)) {
            dataSource.dayTimeline(day.plusDays(1), settings)
        } else {
            null
        }
        return WidgetDayGridCollectionSnapshot(
            rows = widgetDayGridRows(timeline, settings, nextTimeline, zoneId),
            settings = settings,
            palette = palette,
            widthDp = size.dayGridContentWidthDp() + WIDGET_DAY_LIST_SIDE_BLEED_DP * 2f,
        )
    }

    suspend fun collectionSnapshot(kind: KgsWidgetKind, appWidgetId: Int): WidgetCollectionSnapshot? {
        if (!kind.usesCollectionList) return null
        val settings = dataSource.loadSettings(kind)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val renderSize = WidgetSize.from(
            context,
            AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId),
            kind,
        )
        val taskArtWidthDp = renderSize.collectionArtWidthDp(kind)
        val priorityFrameCount = kind.priorityMotionFrameCount()
        val rows = dataSource.listRows(kind, settings, appWidgetId)
        val dataSignature = dataSource.collectionSignature(kind, settings, appWidgetId, rows)
        return WidgetCollectionSnapshot(
            kind = kind,
            appWidgetId = appWidgetId,
            settings = settings,
            palette = palette,
            renderSize = renderSize,
            taskArtWidthDp = taskArtWidthDp,
            rows = rows,
            signature = collectionRenderSignature(
                dataSignature = dataSignature,
                renderSize = renderSize,
                taskArtWidthDp = taskArtWidthDp,
                priorityFrameCount = priorityFrameCount,
            ),
        )
    }

    suspend fun prepareMonthWidget(
        appWidgetId: Int,
        options: Bundle,
    ): PreparedMonthWidgetRender {
        val settingsStarted = SystemClock.elapsedRealtime()
        val settings = dataSource.loadSettings(KgsWidgetKind.Month)
        val settingsMillis = SystemClock.elapsedRealtime() - settingsStarted
        val month = state.month.month(appWidgetId, YearMonth.now(zoneId))
        val generation = state.dataGeneration.current()
        val cachedPage = state.monthPages.get(month, settings, zoneId.id, generation)
        val pageStarted = SystemClock.elapsedRealtime()
        val page = preparedWidgetValue(cachedPage) {
            monthPageSource.load(month, settings)
        }
        WidgetLog.d(
            context,
            "MonthPrepare widget=$appWidgetId settingsMs=$settingsMillis pageMs=${SystemClock.elapsedRealtime() - pageStarted} " +
                "pageCacheHit=${cachedPage != null} generation=$generation currentGeneration=${state.dataGeneration.current()}",
        )
        if (state.dataGeneration.current() == generation) {
            state.monthPages.put(month, settings, zoneId.id, page, generation)
            warmWidgetMonthPageCache(widgets, zoneId, month, settings)
        }
        return prepareMonthPage(appWidgetId, options, settings, page, hasCompleteData = true)
    }

    suspend fun prepareMultiWidget(
        appWidgetId: Int,
        options: Bundle,
    ): PreparedMultiWidgetRender {
        val settings = dataSource.loadSettings(KgsWidgetKind.Multi)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val today = LocalDate.now(zoneId)
        val size = WidgetSize.from(context, options, KgsWidgetKind.Multi)
        val contentHeightDp = (size.heightDp - WIDGET_MULTI_CONTENT_PADDING_DP).coerceAtLeast(2)
        val monthPercent = SettingsStore.normalizeMultiWidgetMonthPercent(settings.multiWidgetMonthPercent)
        val monthPanelHeightDp = ((contentHeightDp * monthPercent) / 100f)
            .roundToInt()
            .coerceIn(1, contentHeightDp - 1)
        val agendaPanelHeightDp = (contentHeightDp - monthPanelHeightDp).coerceAtLeast(1)
        val month = state.month.month(appWidgetId, YearMonth.from(today))
        val generation = state.dataGeneration.current()
        val cachedPage = state.monthPages.get(month, settings, zoneId.id, generation)
        val page = coroutineScope {
            val pageDeferred = async {
                preparedWidgetValue(cachedPage) { monthPageSource.load(month, settings) }
            }
            val rowsDeferred = async {
                dataSource.listRows(KgsWidgetKind.Multi, settings, appWidgetId)
            }
            pageDeferred.await() to rowsDeferred.await()
        }
        val monthPage = page.first
        val rows = page.second
        if (state.dataGeneration.current() == generation) {
            state.monthPages.put(month, settings, zoneId.id, monthPage, generation)
            warmWidgetMonthPageCache(widgets, zoneId, month, settings)
        }
        val monthSpec = WidgetMonthRenderSpec.from(
            WidgetSize(widthDp = size.widthDp, heightDp = monthPanelHeightDp),
            monthPage.rowCount,
        )
        val taskArtWidthDp = size.collectionArtWidthDp(KgsWidgetKind.Multi)
        val dataSignature = dataSource.collectionSignature(KgsWidgetKind.Multi, settings, appWidgetId, rows)
        val collectionSnapshot = WidgetCollectionSnapshot(
            kind = KgsWidgetKind.Multi,
            appWidgetId = appWidgetId,
            settings = settings,
            palette = palette,
            renderSize = size,
            taskArtWidthDp = taskArtWidthDp,
            rows = rows,
            signature = collectionRenderSignature(
                dataSignature = dataSignature,
                renderSize = size,
                taskArtWidthDp = taskArtWidthDp,
                priorityFrameCount = KgsWidgetKind.Multi.priorityMotionFrameCount(),
            ),
        )
        val monthSignature = monthRenderSignature(
            today = today,
            settings = settings,
            palette = palette,
            currentSize = size,
            renderSpec = monthSpec,
            page = monthPage,
        )
        return PreparedMultiWidgetRender(
            appWidgetId = appWidgetId,
            settings = settings,
            palette = palette,
            today = today,
            page = monthPage,
            size = size,
            monthSpec = monthSpec,
            monthPanelHeightDp = monthPanelHeightDp,
            agendaPanelHeightDp = agendaPanelHeightDp,
            collectionSnapshot = collectionSnapshot,
            signature = multiWidgetRenderSignature(
                collectionSignature = collectionSnapshot.signature,
                monthSignature = monthSignature,
                monthPanelHeightDp = monthPanelHeightDp,
                agendaPanelHeightDp = agendaPanelHeightDp,
            ),
        )
    }

    fun prepareMonthPage(
        appWidgetId: Int,
        options: Bundle,
        settings: WidgetRenderSettings,
        page: WidgetMonthPage,
        hasCompleteData: Boolean,
    ): PreparedMonthWidgetRender {
        val today = LocalDate.now(zoneId)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val renderedPage = if (hasCompleteData) page else page.loadingSkeleton(palette.muted)
        val currentSize = WidgetSize.from(context, options, KgsWidgetKind.Month)
        val renderSpec = WidgetMonthRenderSpec.from(currentSize, renderedPage.rowCount)

        return PreparedMonthWidgetRender(
            appWidgetId = appWidgetId,
            settings = settings,
            palette = palette,
            today = today,
            page = renderedPage,
            currentSize = currentSize,
            renderSpec = renderSpec,
            hasCompleteData = hasCompleteData,
            signature = if (hasCompleteData) {
                monthRenderSignature(
                    today = today,
                    settings = settings,
                    palette = palette,
                    currentSize = currentSize,
                    renderSpec = renderSpec,
                    page = page,
                )
            } else {
                null
            },
        )
    }
}
