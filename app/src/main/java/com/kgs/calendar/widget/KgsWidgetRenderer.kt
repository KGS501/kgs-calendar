package com.kgs.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.RequiresApi
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.MainActivity
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppLanguageMode
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.data.settings.WidgetColorMode
import com.kgs.calendar.data.settings.WidgetTaskCreateMode
import com.kgs.calendar.data.settings.WidgetTaskDisplayMode
import com.kgs.calendar.data.settings.WidgetTaskSortMode
import com.kgs.calendar.data.settings.WidgetTaskSubtaskDefaultMode
import com.kgs.calendar.data.settings.WidgetThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

internal class KgsWidgetRenderer(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val packageName = context.packageName
    private val dataSource = KgsWidgetDataSource(context, zoneId)
    private val monthPageSource = WidgetMonthPageSource(context, zoneId)

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
            else -> renderCollectionWidget(
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

    suspend fun renderDayAllDaySectionFrame(
        frameData: WidgetDayAllDaySectionFrameData,
        expansionProgress: Float,
    ): RemoteViews {
        val textContext = context.withWidgetLocale(frameData.settings.locale)
        val views = RemoteViews(packageName, R.layout.widget_day_calendar)
        views.bindCollectionBottomFade(frameData.palette, visible = true)
        if (frameData.timeline.allDayItems.isNotEmpty()) {
            views.setViewVisibility(R.id.widget_day_all_day_section, View.VISIBLE)
            val settled = expansionProgress <= 0.001f || expansionProgress >= 0.999f
            WidgetDayGridRow(
                day = frameData.timeline.day,
                hour = null,
                allDayItems = frameData.timeline.allDayItems,
                priorityAnimationsEnabled = frameData.settings.priorityAnimationsEnabled && settled,
                maxVisibleAllDayItems = frameData.settings.maxVisibleAllDayItems,
                allDayExpanded = frameData.allDayExpanded,
                allDayExpansionProgress = expansionProgress,
            ).bindInto(
                target = views,
                context = textContext,
                packageName = packageName,
                palette = frameData.palette,
                widthDp = frameData.contentWidthDp,
                rootId = R.id.widget_day_all_day_section,
                artId = R.id.widget_day_all_day_art,
                motionId = R.id.widget_day_priority_motion,
                overlayId = R.id.widget_day_all_day_overlay,
                appWidgetId = frameData.appWidgetId,
                useImageUris = true,
            )
        } else {
            views.setViewVisibility(R.id.widget_day_all_day_section, View.GONE)
            views.removeAllViews(R.id.widget_day_all_day_overlay)
        }
        return views
    }

    suspend fun dayAllDaySectionFrameData(
        appWidgetId: Int,
        options: Bundle,
    ): WidgetDayAllDaySectionFrameData {
        val settings = dataSource.loadSettings(KgsWidgetKind.Day)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val today = LocalDate.now(zoneId)
        val day = KgsWidgetDayState.day(context, appWidgetId, today)
        val size = WidgetSize.from(context, options, KgsWidgetKind.Day)
        val timeline = dataSource.dayTimeline(day, settings)
        return WidgetDayAllDaySectionFrameData(
            appWidgetId = appWidgetId,
            settings = settings,
            palette = palette,
            timeline = timeline,
            contentWidthDp = size.dayGridContentWidthDp(),
            allDayExpanded = KgsWidgetDayState.isAllDayExpanded(context, appWidgetId),
        )
    }

    suspend fun prepareMonthUpdate(appWidgetId: Int, options: Bundle): PreparedMonthWidgetRender =
        prepareMonthWidget(appWidgetId, options)

    suspend fun prepareMultiUpdate(appWidgetId: Int, options: Bundle): PreparedMultiWidgetRender =
        prepareMultiWidget(appWidgetId, options)

    fun renderMonthUpdate(prepared: PreparedMonthWidgetRender): MonthWidgetRenderResult =
        renderPreparedMonthPage(prepared)

    suspend fun renderMonthUpdate(appWidgetId: Int, options: Bundle): MonthWidgetRenderResult =
        renderPreparedMonthPage(prepareMonthWidget(appWidgetId, options))

    internal fun renderMonthNavigationPage(
        kind: KgsWidgetKind,
        snapshot: MonthNavSnapshot,
        options: Bundle,
        settings: WidgetRenderSettings,
        page: WidgetMonthPage,
        hasCompleteData: Boolean,
    ): MonthWidgetRenderResult {
        return when (kind) {
            KgsWidgetKind.Month -> renderMonthPageResult(
                appWidgetId = snapshot.widgetId,
                options = options,
                settings = settings,
                page = page,
                hasCompleteData = hasCompleteData,
            )
            KgsWidgetKind.Multi -> {
                val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
                val renderedPage = if (hasCompleteData) page else page.loadingSkeleton(palette.muted)
                MonthWidgetRenderResult(
                    views = renderMultiMonthNavigationPage(snapshot.widgetId, options, settings, renderedPage),
                    hasCompleteData = hasCompleteData,
                    signature = null,
                )
            }
            else -> error("Month navigation is unsupported for ${kind.name}")
        }
    }

    private suspend fun renderDayWidget(
        appWidgetId: Int,
        options: Bundle,
        applyInitialScroll: Boolean = true,
        forceServiceCollection: Boolean = false,
    ): RemoteViews {
        val settings = dataSource.loadSettings(KgsWidgetKind.Day)
        val textContext = context.withWidgetLocale(settings.locale)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val today = LocalDate.now(zoneId)
        val day = KgsWidgetDayState.day(context, appWidgetId, today)
        val size = WidgetSize.from(context, options, KgsWidgetKind.Day)
        val timeline = dataSource.dayTimeline(day, settings)
        val useCurrentHourStart = settings.dayWidgetStartAtCurrentHour && day == today
        val nextTimeline = if (useCurrentHourStart) {
            dataSource.dayTimeline(day.plusDays(1), settings)
        } else {
            null
        }
        val contentWidthDp = size.dayGridContentWidthDp()
        val gridRows = dayGridRows(timeline, settings, nextTimeline)
        val views = RemoteViews(packageName, R.layout.widget_day_calendar)
        val contentPadding = context.dpToPx(WIDGET_DAY_ROOT_PADDING_DP)
        views.setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        views.setInt(R.id.widget_header, "setBackgroundResource", palette.rootBackgroundRes)
        views.setInt(R.id.widget_day_header_compact, "setBackgroundResource", palette.rootBackgroundRes)
        views.setViewPadding(R.id.widget_content, contentPadding, contentPadding, contentPadding, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutMargin(R.id.widget_day_list_viewport, RemoteViews.MARGIN_BOTTOM, 0f, TypedValue.COMPLEX_UNIT_DIP)
        }
        val dayTitle = day.format(DateTimeFormatter.ofPattern("EEE, d. MMM", settings.locale))
        val hideDayTitle = shouldHideWidgetTitle(size.widthDp, dayTitle, reservedDp = 174f, textSp = 16f)
        views.setViewVisibility(R.id.widget_header, if (hideDayTitle) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_day_header_compact, if (hideDayTitle) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_title, dayTitle)
        views.setTextColor(R.id.widget_title, palette.text)
        views.setViewVisibility(R.id.widget_title, View.VISIBLE)
        views.setInt(R.id.widget_header, "setGravity", Gravity.CENTER_VERTICAL)
        views.setImageViewBitmap(R.id.widget_day_today, widgetTodayDateIconBitmap(context, palette.onAccent, today.dayOfMonth))
        views.setImageViewBitmap(R.id.widget_day_today_compact, widgetTodayDateIconBitmap(context, palette.onAccent, today.dayOfMonth))
        views.setInt(R.id.widget_day_today, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setInt(R.id.widget_day_today_compact, "setBackgroundResource", palette.badgeBackgroundRes)
        val todayVisibility = if (day == today) View.GONE else View.VISIBLE
        views.setViewVisibility(R.id.widget_day_today, todayVisibility)
        views.setViewVisibility(R.id.widget_day_today_compact, todayVisibility)
        views.setOnClickPendingIntent(R.id.widget_day_today, dayTodayPendingIntent(appWidgetId))
        views.setOnClickPendingIntent(R.id.widget_day_today_compact, dayTodayPendingIntent(appWidgetId))
        views.setTextViewText(R.id.widget_badge, "+")
        views.setTextViewText(R.id.widget_badge_compact, "+")
        views.setTextViewTextSize(R.id.widget_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
        views.setTextViewTextSize(R.id.widget_badge_compact, TypedValue.COMPLEX_UNIT_SP, 17f)
        views.setTextColor(R.id.widget_badge, palette.onAccent)
        views.setTextColor(R.id.widget_badge_compact, palette.onAccent)
        views.setInt(R.id.widget_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setInt(R.id.widget_badge_compact, "setBackgroundResource", palette.badgeBackgroundRes)
        val createTaskIntent = createTaskPendingIntent(appWidgetId, day, WidgetTaskCreateMode.Today)
        views.setOnClickPendingIntent(R.id.widget_badge, createTaskIntent)
        views.setOnClickPendingIntent(R.id.widget_badge_compact, createTaskIntent)
        views.setTextViewText(R.id.widget_month_prev, "\u2039")
        views.setTextViewText(R.id.widget_month_next, "\u203A")
        views.setTextViewText(R.id.widget_month_prev_compact, "\u2039")
        views.setTextViewText(R.id.widget_month_next_compact, "\u203A")
        views.setTextColor(R.id.widget_month_prev, palette.accent)
        views.setTextColor(R.id.widget_month_next, palette.accent)
        views.setTextColor(R.id.widget_month_prev_compact, palette.accent)
        views.setTextColor(R.id.widget_month_next_compact, palette.accent)
        val previousDayIntent = dayNavigationPendingIntent(appWidgetId, previous = true)
        val nextDayIntent = dayNavigationPendingIntent(appWidgetId, previous = false)
        views.setOnClickPendingIntent(R.id.widget_month_prev, previousDayIntent)
        views.setOnClickPendingIntent(R.id.widget_month_prev_compact, previousDayIntent)
        views.setOnClickPendingIntent(R.id.widget_month_next, nextDayIntent)
        views.setOnClickPendingIntent(R.id.widget_month_next_compact, nextDayIntent)
        val openApp = openMainAppPendingIntent(appWidgetId)
        views.setOnClickPendingIntent(R.id.widget_root, openApp)
        views.setOnClickPendingIntent(R.id.widget_content, openApp)
        views.setOnClickPendingIntent(R.id.widget_title, openApp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewVisibility(R.id.widget_day_timeline_art, View.GONE)
            views.setViewVisibility(R.id.widget_list, View.VISIBLE)
            if (timeline.allDayItems.isNotEmpty()) {
                views.setViewVisibility(R.id.widget_day_all_day_section, View.VISIBLE)
                WidgetDayGridRow(
                    day = timeline.day,
                    hour = null,
                    allDayItems = timeline.allDayItems,
                    priorityAnimationsEnabled = settings.priorityAnimationsEnabled,
                    maxVisibleAllDayItems = settings.maxVisibleAllDayItems,
                    allDayExpanded = KgsWidgetDayState.isAllDayExpanded(context, appWidgetId),
                ).bindInto(
                    target = views,
                    context = textContext,
                    packageName = packageName,
                    palette = palette,
                    widthDp = contentWidthDp,
                    rootId = R.id.widget_day_all_day_section,
                    artId = R.id.widget_day_all_day_art,
                    motionId = R.id.widget_day_priority_motion,
                    overlayId = R.id.widget_day_all_day_overlay,
                    appWidgetId = appWidgetId,
                )
            } else {
                views.setViewVisibility(R.id.widget_day_all_day_section, View.GONE)
                views.removeAllViews(R.id.widget_day_all_day_overlay)
            }
            if (usesDirectDayGridItems() && !forceServiceCollection) {
                views.setPendingIntentTemplate(R.id.widget_list, collectionClickPendingIntent(KgsWidgetKind.Day, appWidgetId))
                views.bindDirectDayGridItems(
                    context = textContext,
                    packageName = packageName,
                    palette = palette,
                    rows = gridRows,
                    widthDp = contentWidthDp + WIDGET_DAY_LIST_SIDE_BLEED_DP * 2f,
                    appWidgetId = appWidgetId,
                )
            } else if (applyInitialScroll) {
                views.setRemoteAdapter(R.id.widget_list, collectionAdapterIntent(KgsWidgetKind.Day, appWidgetId))
                views.setPendingIntentTemplate(R.id.widget_list, collectionClickPendingIntent(KgsWidgetKind.Day, appWidgetId))
            }
        } else {
            views.setOnClickPendingIntent(R.id.widget_day_timeline_art, openApp)
            views.setViewVisibility(R.id.widget_day_all_day_section, View.GONE)
            views.setViewVisibility(R.id.widget_day_timeline_art, View.VISIBLE)
            views.setViewVisibility(R.id.widget_list, View.GONE)
            views.setImageViewBitmap(R.id.widget_day_timeline_art, dayTimelineBitmap(size, palette, timeline))
        }
        views.bindCollectionBottomFade(palette, visible = true)
        views.setViewVisibility(R.id.widget_empty, View.GONE)
        views.setViewVisibility(R.id.widget_month_section, View.GONE)
        WidgetLog.d(context, "Day widget $appWidgetId date=$day timed=${timeline.timedItems.size} allDay=${timeline.allDayItems.size}")
        return views
    }

    suspend fun dayGridCollectionSnapshot(appWidgetId: Int): WidgetDayGridCollectionSnapshot {
        val settings = dataSource.loadSettings(KgsWidgetKind.Day)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val day = KgsWidgetDayState.day(context, appWidgetId, LocalDate.now(zoneId))
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val size = WidgetSize.from(context, options, KgsWidgetKind.Day)
        val timeline = dataSource.dayTimeline(day, settings)
        val nextTimeline = if (settings.dayWidgetStartAtCurrentHour && day == LocalDate.now(zoneId)) {
            dataSource.dayTimeline(day.plusDays(1), settings)
        } else {
            null
        }
        return WidgetDayGridCollectionSnapshot(
            rows = dayGridRows(timeline, settings, nextTimeline),
            settings = settings,
            palette = palette,
            widthDp = size.dayGridContentWidthDp() + WIDGET_DAY_LIST_SIDE_BLEED_DP * 2f,
        )
    }

    private fun dayGridRows(
        timeline: WidgetDayTimeline,
        settings: WidgetRenderSettings,
        nextTimeline: WidgetDayTimeline? = null,
    ): List<WidgetDayGridRow> {
        val now = LocalTime.now(zoneId)
        val today = LocalDate.now(zoneId)
        val hourRowHeightDp = settings.dayWidgetHourRowHeightDp()
        val availableAnimatedBlocks = WIDGET_DAY_MAX_PRIORITY_MOTION_ROWS - if (
            settings.priorityAnimationsEnabled &&
            timeline.allDayItems.any { it.hasDayPriorityMotion() }
        ) {
            1
        } else {
            0
        }
        val useCurrentHourStart = settings.dayWidgetStartAtCurrentHour && timeline.day == today
        val firstVisibleHour = if (useCurrentHourStart) {
            now.hour.coerceIn(WIDGET_DAY_START_HOUR, WIDGET_DAY_END_HOUR)
        } else if (settings.dayWidgetStartAtCurrentHour) {
            WIDGET_DAY_START_HOUR
        } else {
            settings.dayWidgetStartHour.coerceIn(WIDGET_DAY_START_HOUR, WIDGET_DAY_END_HOUR)
        }
        val primaryRows = dayGridRowsForTimeline(
            timeline = timeline,
            settings = settings,
            now = now,
            today = today,
            hourRowHeightDp = hourRowHeightDp,
            firstVisibleHour = firstVisibleHour,
            lastVisibleHour = WIDGET_DAY_END_HOUR,
            availableAnimatedBlocks = availableAnimatedBlocks,
            daySwitchLabel = null,
        )
        if (!useCurrentHourStart || firstVisibleHour == WIDGET_DAY_START_HOUR || nextTimeline == null) {
            return primaryRows
        }
        val nextRows = dayGridRowsForTimeline(
            timeline = nextTimeline,
            settings = settings,
            now = now,
            today = today,
            hourRowHeightDp = hourRowHeightDp,
            firstVisibleHour = WIDGET_DAY_START_HOUR,
            lastVisibleHour = firstVisibleHour - 1,
            availableAnimatedBlocks = availableAnimatedBlocks,
            daySwitchLabel = null,
        )
        val boundaryRow = WidgetDayGridRow(
            day = nextTimeline.day,
            hour = null,
            hourRowHeightDp = hourRowHeightDp,
            daySwitchLabel = nextTimeline.day.format(DateTimeFormatter.ofPattern("EEE d.", settings.locale)),
            priorityAnimationsEnabled = false,
        )
        return primaryRows + boundaryRow + nextRows
    }

    private fun dayGridRowsForTimeline(
        timeline: WidgetDayTimeline,
        settings: WidgetRenderSettings,
        now: LocalTime,
        today: LocalDate,
        hourRowHeightDp: Float,
        firstVisibleHour: Int,
        lastVisibleHour: Int,
        availableAnimatedBlocks: Int,
        daySwitchLabel: String?,
    ): List<WidgetDayGridRow> {
        if (firstVisibleHour > lastVisibleHour) return emptyList()
        val allTimedItems = timeline.timedItems
        val protectedRanges = if (settings.priorityAnimationsEnabled && availableAnimatedBlocks > 0) {
            allTimedItems.asSequence()
                .filter { it.item.hasDayPriorityMotion() }
                .distinctBy { it.item.stableKey }
                .sortedWith(
                    compareBy<WidgetDayTimedLayout> { it.item.priority ?: 9 }
                        .thenBy { it.startMinute }
                        .thenBy { it.endMinute }
                        .thenBy { it.item.title.lowercase(settings.locale) },
                )
                .take(availableAnimatedBlocks)
                .map { layout ->
                    val firstHour = (layout.startMinute / 60 - 1).coerceIn(0, 23)
                    val occupiedLastHour = ((layout.endMinute - 1).coerceAtLeast(layout.startMinute) / 60).coerceIn(0, 23)
                    firstHour..(occupiedLastHour + 1).coerceAtMost(23)
                }
                .toList()
                .sortedBy { it.first }
                .fold(mutableListOf<IntRange>()) { merged, range ->
                    val previous = merged.lastOrNull()
                    if (previous != null && range.first <= previous.last + 1) {
                        merged[merged.lastIndex] = previous.first..max(previous.last, range.last)
                    } else {
                        merged += range
                    }
                    merged
                }
        } else {
            emptyList()
        }
        val spanningRanges = allTimedItems.asSequence()
            .mapNotNull { layout ->
                val firstHour = (layout.startMinute / 60).coerceIn(WIDGET_DAY_START_HOUR, WIDGET_DAY_END_HOUR)
                val occupiedLastHour = ((layout.endMinute - 1).coerceAtLeast(layout.startMinute) / 60)
                    .coerceIn(WIDGET_DAY_START_HOUR, WIDGET_DAY_END_HOUR)
                val clippedFirst = max(firstHour, firstVisibleHour)
                val clippedLast = min(occupiedLastHour, lastVisibleHour)
                if (clippedLast > clippedFirst) clippedFirst..clippedLast else null
            }
            .toList()
        val groupedRanges = (protectedRanges + spanningRanges)
            .sortedBy { it.first }
            .fold(mutableListOf<IntRange>()) { merged, range ->
                val clipped = max(range.first, firstVisibleHour)..min(range.last, lastVisibleHour)
                if (clipped.first > clipped.last) return@fold merged
                val previous = merged.lastOrNull()
                if (previous != null && clipped.first <= previous.last + 1) {
                    merged[merged.lastIndex] = previous.first..max(previous.last, clipped.last)
                } else {
                    merged += clipped
                }
                merged
            }

        fun row(
            startHour: Int,
            hourCount: Int,
            animatePriority: Boolean,
        ): WidgetDayGridRow {
            val startMinute = startHour * 60
            val endMinute = (startHour + hourCount) * 60
            return WidgetDayGridRow(
                day = timeline.day,
                hour = startHour,
                hourCount = hourCount,
                hourRowHeightDp = hourRowHeightDp,
                timedItems = allTimedItems.filter { it.startMinute < endMinute && it.endMinute > startMinute },
                nowMinute = if (
                    timeline.day == today &&
                    now.hour in startHour until (startHour + hourCount)
                ) {
                    now.hour * 60 + now.minute
                } else {
                    null
                },
                daySwitchLabel = daySwitchLabel.takeIf { startHour == firstVisibleHour },
                priorityAnimationsEnabled = animatePriority,
            )
        }

        return buildList {
            var hour = firstVisibleHour
            var groupedIndex = 0
            while (hour <= lastVisibleHour) {
                val grouped = groupedRanges.getOrNull(groupedIndex)
                if (grouped != null && hour in grouped) {
                    val groupedStart = max(hour, grouped.first)
                    val groupedLast = min(grouped.last, lastVisibleHour)
                    val animatePriority = protectedRanges.any { range ->
                        range.first <= groupedLast && range.last >= groupedStart
                    }
                    add(
                        row(
                            startHour = groupedStart,
                            hourCount = groupedLast - groupedStart + 1,
                            animatePriority = animatePriority,
                        ),
                    )
                    repeat(groupedLast - groupedStart) { offset ->
                        add(
                            WidgetDayGridRow(
                                day = timeline.day,
                                hour = groupedStart + offset + 1,
                                hourCount = 0,
                                hourRowHeightDp = hourRowHeightDp,
                                priorityAnimationsEnabled = false,
                            ),
                        )
                    }
                    hour = groupedLast + 1
                    groupedIndex++
                } else {
                    add(row(startHour = hour, hourCount = 1, animatePriority = false))
                    hour++
                }
            }
        }
    }

    private fun dayTimelineRows(timeline: WidgetDayTimeline, settings: WidgetRenderSettings): List<WidgetListRow> {
        val rows = mutableListOf<WidgetListRow>()
        if (timeline.allDayItems.isNotEmpty()) {
            timeline.allDayItems.forEach { item ->
                rows += item.toDayRow(
                    date = timeline.day,
                    startMinute = 0,
                    endMinute = 24 * 60,
                    settings = settings,
                    allDay = true,
                )
            }
        }
        val byHour = timeline.timedItems.groupBy { (it.startMinute / 60).coerceIn(0, 23) }
        val now = LocalTime.now(zoneId)
        for (hour in settings.dayWidgetHourOrder()) {
            rows += WidgetListRow.section("%02d:00".format(hour), sortValue = hour.toLong())
            if (timeline.day == LocalDate.now(zoneId) && now.hour == hour) {
                rows += WidgetListRow.now(
                    date = timeline.day,
                    timeLabel = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    sortValue = hour.toLong(),
                )
            }
            byHour[hour]
                .orEmpty()
                .sortedWith(compareBy<WidgetDayTimedLayout> { it.startMinute }.thenBy { it.lane }.thenBy { it.item.title.lowercase(settings.locale) })
                .forEach { layout ->
                    rows += layout.item.toDayRow(
                        date = timeline.day,
                        startMinute = layout.startMinute,
                        endMinute = layout.endMinute,
                        settings = settings,
                        allDay = false,
                    )
                }
        }
        return rows
    }

    private fun WidgetRenderSettings.dayWidgetHourOrder(): List<Int> {
        return (WIDGET_DAY_START_HOUR..WIDGET_DAY_END_HOUR).toList()
    }

    private fun WidgetDayItem.toDayRow(
        date: LocalDate,
        startMinute: Int,
        endMinute: Int,
        settings: WidgetRenderSettings,
        allDay: Boolean,
    ): WidgetListRow {
        val labels = context.withWidgetLocale(settings.locale)
        val rowMeta = if (allDay) labels.getString(R.string.all_day) else (startMinute to endMinute).timeRangeText()
        return if (isTask && !taskResourceHref.isNullOrBlank()) {
            WidgetListRow.task(
                title = title,
                meta = rowMeta,
                color = color,
                sortMillis = if (allDay) Long.MIN_VALUE else startMinute.toLong(),
                date = date,
                completed = completed,
                taskResourceHref = taskResourceHref,
                statusGlyph = statusGlyph,
                location = location,
                depth = 0,
                childCount = 0,
                continuationLevels = emptySet(),
                lastSibling = true,
                subtasksExpanded = false,
                priority = priority,
                priorityMotionEnabled = settings.priorityAnimationsEnabled,
                launchKind = KgsWidgetKind.Day,
            )
        } else {
            WidgetListRow.item(
                title = title,
                meta = rowMeta,
                color = color,
                sortMillis = if (allDay) Long.MIN_VALUE else startMinute.toLong(),
                date = date,
                completed = completed,
                allDaySort = if (allDay) 0 else 1,
                launchKind = KgsWidgetKind.Day,
                stableKey = stableKey,
                eventResourceHref = eventResourceHref,
                location = location,
                eventStatus = eventStatus,
                endMillis = if (allDay) Long.MAX_VALUE else endMinute.toLong(),
            )
        }
    }

    private fun dayTimelineBitmap(size: WidgetSize, palette: WidgetPalette, timeline: WidgetDayTimeline): Bitmap {
        val density = context.resources.displayMetrics.density
        val widthDp = size.widthDp.coerceAtLeast(120)
        val heightDp = (
            size.heightDp -
                WIDGET_DAY_ROOT_PADDING_DP * 2 -
                WIDGET_DAY_HEADER_HEIGHT_DP -
                WIDGET_DAY_TIMELINE_TOP_MARGIN_DP
            ).coerceAtLeast(96)
        val bitmap = Bitmap.createBitmap(
            (widthDp * density).roundToInt().coerceAtLeast(1),
            (heightDp * density).roundToInt().coerceAtLeast(1),
            Bitmap.Config.RGB_565,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.rootBackgroundColor)
        canvas.scale(density, density)

        val dark = palette.rootBackgroundColor.isDarkColor()
        val slotColor = if (dark) 0xFF182534.toInt() else 0xFFFAFCFF.toInt()
        val slotLine = if (dark) 0xFF35475B.toInt() else 0xFFD2E0EF.toInt()
        val timeColumnWidth = if (widthDp < 230) 28f else 30f
        val gridLeft = timeColumnWidth
        val gridWidth = (widthDp - gridLeft).coerceAtLeast(1f)
        val allDayRows = when {
            timeline.allDayItems.isEmpty() -> 0
            heightDp < 170 -> 1
            else -> 2
        }
        val allDayRowHeight = if (heightDp < 170) 17f else 20f
        val allDayHeight = if (allDayRows > 0) allDayRows * allDayRowHeight + 3f else 0f
        val timelineTop = allDayHeight + if (allDayHeight > 0f) 5f else 0f
        val timelineHeight = (heightDp - timelineTop).coerceAtLeast(60f)
        val hourCount = WIDGET_DAY_END_HOUR - WIDGET_DAY_START_HOUR + 1
        val rowHeight = timelineHeight / hourCount.toFloat()
        val slotRadius = min(10f, rowHeight / 2.7f).coerceAtLeast(3f)

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted
            textSize = when {
                rowHeight < 10f -> 5.8f
                rowHeight < 14f -> 6.6f
                else -> 7.5f
            }
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slotColor
            style = Paint.Style.FILL
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slotLine
            strokeWidth = 0.65f
        }

        drawDayAllDayItems(
            canvas = canvas,
            palette = palette,
            items = timeline.allDayItems,
            rows = allDayRows,
            rowHeight = allDayRowHeight,
            timeColumnWidth = timeColumnWidth,
            gridLeft = gridLeft,
            gridWidth = gridWidth,
        )

        val labelEvery = when {
            rowHeight < 9f -> 4
            rowHeight < 13f -> 2
            else -> 1
        }
        repeat(hourCount) { index ->
            val top = timelineTop + index * rowHeight + 1f
            val bottom = timelineTop + (index + 1) * rowHeight - 1f
            canvas.drawRoundRect(
                RectF(gridLeft, top, widthDp.toFloat(), bottom.coerceAtLeast(top + 1f)),
                slotRadius,
                slotRadius,
                slotPaint,
            )
            if (index % labelEvery == 0) {
                val hour = WIDGET_DAY_START_HOUR + index
                canvas.drawText("%02d:00".format(hour), timeColumnWidth / 2f, top + timePaint.textSize + 1f, timePaint)
            }
            if (index > 0) {
                canvas.drawLine(gridLeft, top - 1f, widthDp.toFloat(), top - 1f, linePaint)
            }
        }

        timeline.timedItems.forEach { layout ->
            drawDayTimedItem(
                canvas = canvas,
                palette = palette,
                layout = layout,
                gridLeft = gridLeft,
                gridWidth = gridWidth,
                timelineTop = timelineTop,
                rowHeight = rowHeight,
            )
        }

        if (timeline.day == LocalDate.now(zoneId)) {
            val now = LocalTime.now(zoneId)
            if (now.hour in WIDGET_DAY_START_HOUR..WIDGET_DAY_END_HOUR) {
                val minute = now.hour * 60 + now.minute - WIDGET_DAY_START_HOUR * 60
                val y = timelineTop + minute / 60f * rowHeight
                val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.accent
                    strokeWidth = 1.4f
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(gridLeft, y, widthDp.toFloat(), y, nowPaint)
                canvas.drawCircle(gridLeft, y, 2.4f, nowPaint)
            }
        }
        return bitmap
    }

    private fun drawDayAllDayItems(
        canvas: Canvas,
        palette: WidgetPalette,
        items: List<WidgetDayItem>,
        rows: Int,
        rowHeight: Float,
        timeColumnWidth: Float,
        gridLeft: Float,
        gridWidth: Float,
    ) {
        if (rows <= 0 || items.isEmpty()) return
        val shown = items.take(rows)
        val hidden = (items.size - shown.size).coerceAtLeast(0)
        shown.forEachIndexed { index, item ->
            val top = index * rowHeight
            val rightInset = if (hidden > 0 && index == shown.lastIndex) 31f else 0f
            drawDayChip(
                canvas = canvas,
                palette = palette,
                item = item,
                rect = RectF(gridLeft, top, gridLeft + gridWidth - rightInset, top + rowHeight - 2f),
                titleSize = 9.5f,
                showMeta = false,
            )
            if (hidden > 0 && index == shown.lastIndex) {
                val overflowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.muted
                    textSize = 9f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val centerY = top + (rowHeight - 2f) / 2f - (overflowPaint.fontMetrics.ascent + overflowPaint.fontMetrics.descent) / 2f
                canvas.drawText("+$hidden", gridLeft + gridWidth - 15f, centerY, overflowPaint)
            }
        }
    }

    private fun drawDayTimedItem(
        canvas: Canvas,
        palette: WidgetPalette,
        layout: WidgetDayTimedLayout,
        gridLeft: Float,
        gridWidth: Float,
        timelineTop: Float,
        rowHeight: Float,
    ) {
        val laneGap = 2f
        val laneWidth = ((gridWidth - laneGap * (layout.laneCount - 1)) / layout.laneCount).coerceAtLeast(14f)
        val left = gridLeft + layout.lane * (laneWidth + laneGap)
        val top = timelineTop + layout.startMinute / 60f * rowHeight + 1f
        val rawHeight = (layout.endMinute - layout.startMinute).coerceAtLeast(1) / 60f * rowHeight
        val height = rawHeight.coerceAtLeast(if (rowHeight < 13f) 10f else 16f)
        val rect = RectF(left, top, (left + laneWidth).coerceAtMost(gridLeft + gridWidth), top + height - 1f)
        val titleSize = when {
            rowHeight < 10f -> 7.5f
            rowHeight < 15f -> 8.8f
            else -> 10.4f
        }
        drawDayChip(
            canvas = canvas,
            palette = palette,
            item = layout.item,
            rect = rect,
            titleSize = titleSize,
            showMeta = rect.height() >= 23f,
        )
    }

    private fun drawDayChip(
        canvas: Canvas,
        palette: WidgetPalette,
        item: WidgetDayItem,
        rect: RectF,
        titleSize: Float,
        showMeta: Boolean,
    ) {
        val color = if (item.completed) item.color.blendWith(palette.rootBackgroundColor, 0.45f) else item.color
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val radius = min(9f, rect.height() / 2f).coerceAtLeast(4f)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        val contentColor = if (color.isDarkColor()) 0xFFFFFFFF.toInt() else 0xFF1C1A18.toInt()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = contentColor.withAlpha(if (item.completed) 0.68f else 1f)
            textSize = titleSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = contentColor.withAlpha(if (item.completed) 0.55f else 0.84f)
            textSize = (titleSize - 1.5f).coerceAtLeast(6.5f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val statusSpace = if (item.isTask) titleSize + 8f else 0f
        if (item.isTask) {
            val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = titlePaint.color
                style = Paint.Style.STROKE
                strokeWidth = 1.25f
            }
            canvas.drawCircle(rect.left + 8f, rect.top + rect.height() / 2f, (titleSize * 0.42f).coerceAtLeast(3f), statusPaint)
        }
        val textStart = rect.left + 7f + statusSpace
        val textEnd = rect.right - 7f
        val textWidth = (textEnd - textStart).coerceAtLeast(0f)
        val titleBaseline = if (showMeta) {
            rect.top + 8f + titleSize
        } else {
            rect.centerY() - (titlePaint.fontMetrics.ascent + titlePaint.fontMetrics.descent) / 2f
        }
        canvas.drawText(ellipsizeForPaint(item.title, titlePaint, textWidth), textStart, titleBaseline, titlePaint)
        if (showMeta && item.meta.isNotBlank()) {
            val metaBaseline = titleBaseline + titleSize + 1f
            if (metaBaseline < rect.bottom - 3f) {
                canvas.drawText(ellipsizeForPaint(item.meta, metaPaint, textWidth), textStart, metaBaseline, metaPaint)
            }
        }
    }

    suspend fun collectionSignature(kind: KgsWidgetKind, appWidgetId: Int): String? {
        if (!kind.usesCollectionList) return null
        val settings = dataSource.loadSettings(kind)
        return dataSource.collectionSignature(kind, settings, appWidgetId)
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

    private fun collectionRenderSignature(
        dataSignature: String,
        renderSize: WidgetSize,
        taskArtWidthDp: Float,
        priorityFrameCount: Int,
    ): String =
        "$dataSignature\nrender|$WIDGET_COLLECTION_RENDER_SIGNATURE_VERSION|${renderSize.widthDp}x${renderSize.heightDp}|$taskArtWidthDp|$priorityFrameCount|$WIDGET_TASK_PRIORITY_OVERDRAW_DP|$WIDGET_TASK_PRIORITY_BITMAP_SCALE|$WIDGET_AGENDA_LIST_SIDE_BLEED_DP"

    fun error(kind: KgsWidgetKind, appWidgetId: Int, message: String): RemoteViews {
        val settings = WidgetRenderSettings(locale = Locale.getDefault())
        val palette = WidgetPalette.from(context, AppThemeMode.KgsBlue, AppColorMode.Auto)
        val views = RemoteViews(packageName, if (kind == KgsWidgetKind.Tasks) R.layout.widget_calendar_tasks else R.layout.widget_calendar)
        views.bindBaseShell(kind, appWidgetId, settings, palette, LocalDate.now(zoneId))
        views.setViewVisibility(R.id.widget_month_section, View.GONE)
        views.setViewVisibility(R.id.widget_list, View.GONE)
        views.showEmpty(message, palette)
        return views
    }

    private suspend fun prepareMonthWidget(
        appWidgetId: Int,
        options: Bundle,
    ): PreparedMonthWidgetRender {
        val settingsStarted = SystemClock.elapsedRealtime()
        val settings = dataSource.loadSettings(KgsWidgetKind.Month)
        val settingsMillis = SystemClock.elapsedRealtime() - settingsStarted
        val month = KgsWidgetMonthState.month(context, appWidgetId, YearMonth.now(zoneId))
        val generation = WidgetDataGeneration.current()
        val cachedPage = KgsWidgetMonthPageCache.get(month, settings, zoneId.id, generation)
        val pageStarted = SystemClock.elapsedRealtime()
        val page = preparedWidgetValue(cachedPage) {
            monthPageSource.load(month, settings)
        }
        WidgetLog.d(
            context,
            "MonthPrepare widget=$appWidgetId settingsMs=$settingsMillis pageMs=${SystemClock.elapsedRealtime() - pageStarted} " +
                "pageCacheHit=${cachedPage != null} generation=$generation currentGeneration=${WidgetDataGeneration.current()}",
        )
        if (WidgetDataGeneration.current() == generation) {
            KgsWidgetMonthPageCache.put(month, settings, zoneId.id, page, generation)
            warmWidgetMonthPageCache(context, zoneId, month, settings)
        }
        return prepareMonthPage(appWidgetId, options, settings, page, hasCompleteData = true)
    }

    private suspend fun prepareMultiWidget(
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
        val month = KgsWidgetMonthState.month(context, appWidgetId, YearMonth.from(today))
        val generation = WidgetDataGeneration.current()
        val cachedPage = KgsWidgetMonthPageCache.get(month, settings, zoneId.id, generation)
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
        if (WidgetDataGeneration.current() == generation) {
            KgsWidgetMonthPageCache.put(month, settings, zoneId.id, monthPage, generation)
            warmWidgetMonthPageCache(context, zoneId, month, settings)
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

    private fun renderMonthPageResult(
        appWidgetId: Int,
        options: Bundle,
        settings: WidgetRenderSettings,
        page: WidgetMonthPage,
        hasCompleteData: Boolean,
    ): MonthWidgetRenderResult = renderPreparedMonthPage(
        prepareMonthPage(appWidgetId, options, settings, page, hasCompleteData),
    )

    private fun prepareMonthPage(
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

    private fun renderPreparedMonthPage(prepared: PreparedMonthWidgetRender): MonthWidgetRenderResult {
        WidgetLog.d(
            context,
            "Month widget ${prepared.appWidgetId} bucket=${prepared.renderSpec.bucket.name} rows=${prepared.page.rowCount} " +
                "weekHeight=${prepared.renderSpec.weekCellHeightDp} items=${prepared.itemCount} complete=${prepared.hasCompleteData}",
        )

        return MonthWidgetRenderResult(
            views = renderMonthRoot(
                appWidgetId = prepared.appWidgetId,
                settings = prepared.settings,
                palette = prepared.palette,
                today = prepared.today,
                page = prepared.page,
                renderSpec = prepared.renderSpec,
            ),
            hasCompleteData = prepared.hasCompleteData,
            signature = prepared.signature,
        )
    }

    private fun monthRenderSignature(
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

    private fun renderMonthRoot(
        appWidgetId: Int,
        settings: WidgetRenderSettings,
        palette: WidgetPalette,
        today: LocalDate,
        page: WidgetMonthPage,
        renderSpec: WidgetMonthRenderSpec,
    ): RemoteViews {
        val views = RemoteViews(packageName, monthRenderedLayout())
        views.setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        val padding = context.dpToPx(renderSpec.rootPaddingDp)
        views.setViewPadding(R.id.widget_root, padding, padding, padding, padding)
        val monthTitle = page.title(settings.locale)
        val hideMonthTitle = shouldHideWidgetTitle(renderSpec.totalWidthDp, monthTitle, reservedDp = 178f, textSp = renderSpec.titleTextSp)
        val currentMonth = YearMonth.from(today)
        val showingCurrentMonth = page.month == currentMonth
        views.setTextViewText(R.id.widget_title, monthTitle)
        views.setTextColor(R.id.widget_title, palette.text)
        views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, renderSpec.titleTextSp)
        views.setViewVisibility(R.id.widget_title, if (hideMonthTitle) View.GONE else View.VISIBLE)
        views.setInt(R.id.widget_header, "setGravity", if (hideMonthTitle) Gravity.CENTER else Gravity.CENTER_VERTICAL)
        val monthOpenPendingIntent = openAppPendingIntent(
            kind = KgsWidgetKind.Month,
            requestCode = widgetRequestCode("month-open:$appWidgetId:${page.month}"),
            date = page.month.atDay(1),
            clearTask = true,
        )
        views.setOnClickPendingIntent(R.id.widget_header, monthOpenPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_title, monthOpenPendingIntent)
        views.setTextViewText(R.id.widget_badge, "+")
        views.setTextColor(R.id.widget_badge, palette.onAccent)
        views.setInt(R.id.widget_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setOnClickPendingIntent(R.id.widget_badge, createEventPendingIntent(appWidgetId, today))
        views.setImageViewBitmap(R.id.widget_day_today, widgetTodayDateIconBitmap(context, palette.onAccent, today.dayOfMonth))
        views.setInt(R.id.widget_day_today, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setViewVisibility(R.id.widget_day_today, if (showingCurrentMonth) View.GONE else View.VISIBLE)
        views.setOnClickPendingIntent(R.id.widget_day_today, monthTodayPendingIntent(appWidgetId))
        views.setTextViewText(R.id.widget_month_prev, "\u2039")
        views.setTextViewText(R.id.widget_month_next, "\u203A")
        views.setTextColor(R.id.widget_month_prev, palette.accent)
        views.setTextColor(R.id.widget_month_next, palette.accent)
        views.setOnClickPendingIntent(R.id.widget_month_prev, monthNavigationPendingIntent(appWidgetId, previous = true))
        views.setOnClickPendingIntent(R.id.widget_month_next, monthNavigationPendingIntent(appWidgetId, previous = false))

        views.bindMonthPage(R.id.widget_month_page_a, page, settings, palette, renderSpec, appWidgetId)
        views.removeAllViews(R.id.widget_month_page_b)
        views.setDisplayedChild(R.id.widget_month_flipper, 0)
        return views
    }

    private fun RemoteViews.bindMonthPage(
        pageContainerId: Int,
        page: WidgetMonthPage,
        settings: WidgetRenderSettings,
        palette: WidgetPalette,
        renderSpec: WidgetMonthRenderSpec,
        appWidgetId: Int,
    ) {
        removeAllViews(pageContainerId)
        val header = RemoteViews(packageName, R.layout.widget_month_grid_header_row)
        for (dayOfWeek in settings.weekDays()) {
            val weekday = RemoteViews(packageName, R.layout.widget_weekday_cell)
            weekday.setTextViewText(R.id.widget_weekday_text, dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, settings.locale))
            weekday.setTextColor(R.id.widget_weekday_text, palette.muted)
            weekday.setTextViewTextSize(R.id.widget_weekday_text, TypedValue.COMPLEX_UNIT_SP, renderSpec.weekdayTextSp)
            header.addView(R.id.widget_month_grid_row, weekday)
        }
        addView(pageContainerId, header)

        var index = 0
        repeat(page.rowCount) {
            val rowCells = page.cells.subList(index, index + 7)
            val row = RemoteViews(packageName, R.layout.widget_month_grid_week_row)
            val textItemCapacity = if (renderSpec.usesDotCells) 0 else renderSpec.textItemCapacityFor(rowCells)
            repeat(7) { column ->
                val cell = rowCells[column]
                val visibleCount = cell.items.count { it.lane < textItemCapacity }
                val textOverflow = if (!renderSpec.usesDotCells && cell.inCurrentMonth) {
                    (cell.totalItemCount - visibleCount).coerceAtLeast(0)
                } else {
                    0
                }
                row.addView(
                    R.id.widget_month_grid_row,
                    if (renderSpec.usesDotCells) {
                        renderMonthCell(cell, palette, renderSpec, appWidgetId)
                    } else {
                        renderMonthCellShell(cell, palette, renderSpec, appWidgetId, textOverflow)
                    },
                )
            }
            if (renderSpec.usesDotCells) {
                row.setViewVisibility(R.id.widget_month_week_chip_layers, View.GONE)
            } else {
                val connectedBottomFadeSegments = rowCells.monthBottomFadeSegments(textItemCapacity)
                row.bindMonthWeekChips(rowCells, palette, renderSpec, appWidgetId, textItemCapacity)
                if (rowCells.any { cell -> cell.items.any { it.lane < textItemCapacity } }) {
                    row.bindMonthWeekBottomFades(rowCells, palette, connectedBottomFadeSegments)
                    row.bindMonthWeekBottomSpanFades(palette, connectedBottomFadeSegments)
                }
            }
            row.bindMonthTodayBorder(rowCells, palette, appWidgetId)
            addView(pageContainerId, row)
            index += 7
        }
    }

    private fun monthRenderedLayout(): Int =
        R.layout.widget_month_calendar

    private fun RemoteViews.bindMonthWeekChips(
        rowCells: List<WidgetMonthCellContent>,
        palette: WidgetPalette,
        renderSpec: WidgetMonthRenderSpec,
        appWidgetId: Int,
        textItemCapacity: Int,
    ) {
        val segmentsByLane = (0 until textItemCapacity).map(rowCells::monthWeekSegments)
        val lastOccupiedLane = segmentsByLane.indexOfLast { it.isNotEmpty() }
        if (lastOccupiedLane < 0) return
        repeat(lastOccupiedLane + 1) { lane ->
            val laneRow = RemoteViews(packageName, R.layout.widget_month_chip_lane_row)
            var column = 0
            for (segment in segmentsByLane[lane]) {
                val leading = segment.startColumn - column
                if (leading > 0) {
                    laneRow.addDaySpacers(leading)
                }
                val span = segment.columnSpan.coerceIn(1, 7)
                val chip = RemoteViews(packageName, monthSpanChipLayout(span))
                val style = segment.item.color.monthChipStyle()
                chip.setImageViewResource(
                    R.id.widget_month_span_chip_background,
                    segment.item.monthChipMaskRes(),
                )
                chip.setInt(R.id.widget_month_span_chip_background, "setColorFilter", style.fillColor)
                chip.setTextViewText(R.id.widget_month_span_chip_text, segment.title)
                chip.setTextColor(R.id.widget_month_span_chip_text, style.textColor)
                chip.setTextViewTextSize(
                    R.id.widget_month_span_chip_text,
                    TypedValue.COMPLEX_UNIT_SP,
                    renderSpec.chipTextSp,
                )
                chip.setViewPadding(
                    R.id.widget_month_span_chip_text,
                    context.dpToPx(segment.item.monthSpanTextStartPaddingDp()).roundToInt(),
                    0,
                    context.dpToPx(
                        if (segment.item.fadesToNext) WIDGET_MONTH_SPAN_FADE_WIDTH_DP else 4f,
                    ).roundToInt(),
                    0,
                )
                chip.setViewVisibility(
                    R.id.widget_month_span_chip_text,
                    if (segment.title.isBlank()) View.GONE else View.VISIBLE,
                )
                val chipPendingIntent = openAppPendingIntent(
                    KgsWidgetKind.Day,
                    dayPendingIntentRequestCode(appWidgetId, rowCells[segment.startColumn].date),
                    rowCells[segment.startColumn].date,
                )
                chip.setOnClickPendingIntent(R.id.widget_month_span_chip_root, chipPendingIntent)
                chip.setOnClickPendingIntent(R.id.widget_month_span_chip_background, chipPendingIntent)
                chip.setOnClickPendingIntent(R.id.widget_month_span_chip_text, chipPendingIntent)
                laneRow.addView(R.id.widget_month_chip_lane_row, chip)
                column = segment.endColumn + 1
            }
            if (column < 7) {
                laneRow.addDaySpacers(7 - column)
            }
            addView(R.id.widget_month_week_chip_layers, laneRow)
        }
    }

    private fun RemoteViews.addDaySpacers(count: Int) {
        repeat(count.coerceAtLeast(0)) {
            addView(R.id.widget_month_chip_lane_row, RemoteViews(packageName, R.layout.widget_month_span_spacer))
        }
    }

    private fun RemoteViews.bindMonthWeekBottomFades(
        rowCells: List<WidgetMonthCellContent>,
        palette: WidgetPalette,
        connectedSegments: List<WidgetMonthWeekSegment>,
    ) {
        rowCells.forEachIndexed { column, cell ->
            val hasConnectedFade = connectedSegments.any { column in it.startColumn..it.endColumn }
            val fade = RemoteViews(
                packageName,
                if (cell.inCurrentMonth && !hasConnectedFade) {
                    R.layout.widget_month_bottom_fade_cell
                } else {
                    R.layout.widget_month_bottom_fade_spacer
                },
            )
            if (cell.inCurrentMonth && !hasConnectedFade) {
                fade.setInt(R.id.widget_month_bottom_fade_cell, "setBackgroundResource", cellBottomFadeRes(palette.itemBackgroundRes))
            }
            addView(R.id.widget_month_week_bottom_fade_row, fade)
        }
        setViewVisibility(R.id.widget_month_week_bottom_fade_row, View.VISIBLE)
    }

    private fun RemoteViews.bindMonthWeekBottomSpanFades(
        palette: WidgetPalette,
        connectedSegments: List<WidgetMonthWeekSegment>,
    ) {
        if (connectedSegments.isEmpty()) {
            return
        }
        val fadeRow = RemoteViews(packageName, R.layout.widget_month_bottom_span_fade_row)
        var column = 0
        for (segment in connectedSegments) {
            val leading = segment.startColumn - column
            if (leading > 0) {
                repeat(leading) {
                    fadeRow.addView(R.id.widget_month_bottom_span_fade_row, RemoteViews(packageName, R.layout.widget_month_bottom_fade_spacer))
                }
            }
            val fade = RemoteViews(packageName, monthBottomFadeSpanLayout(segment.columnSpan))
            fade.setInt(R.id.widget_month_bottom_fade_span, "setBackgroundResource", cellBottomFadeRes(palette.itemBackgroundRes))
            fadeRow.addView(R.id.widget_month_bottom_span_fade_row, fade)
            column = segment.endColumn + 1
        }
        if (column < 7) {
            repeat(7 - column) {
                fadeRow.addView(R.id.widget_month_bottom_span_fade_row, RemoteViews(packageName, R.layout.widget_month_bottom_fade_spacer))
            }
        }
        addView(R.id.widget_month_week_span_fade_layers, fadeRow)
        setViewVisibility(R.id.widget_month_week_span_fade_layers, View.VISIBLE)
    }

    private fun RemoteViews.bindMonthTodayBorder(
        rowCells: List<WidgetMonthCellContent>,
        palette: WidgetPalette,
        appWidgetId: Int,
    ) {
        val today = LocalDate.now(zoneId)
        if (rowCells.none { it.inCurrentMonth && it.date == today }) {
            setViewVisibility(R.id.widget_month_today_border_row, View.GONE)
            return
        }
        rowCells.forEach { cell ->
            val border = RemoteViews(
                packageName,
                if (cell.inCurrentMonth && cell.date == today) {
                    R.layout.widget_month_today_border_cell
                } else {
                    R.layout.widget_month_today_border_spacer
                },
            )
            if (cell.inCurrentMonth && cell.date == today) {
                border.setInt(R.id.widget_month_today_border, "setBackgroundResource", palette.daySelectedBackgroundRes)
                border.setOnClickPendingIntent(
                    R.id.widget_month_today_border,
                    openAppPendingIntent(KgsWidgetKind.Day, dayPendingIntentRequestCode(appWidgetId, cell.date), cell.date, clearTask = true),
                )
            }
            addView(R.id.widget_month_today_border_row, border)
        }
    }

    private fun renderMonthCellShell(
        cell: WidgetMonthCellContent,
        palette: WidgetPalette,
        renderSpec: WidgetMonthRenderSpec,
        appWidgetId: Int,
        textOverflowCount: Int = 0,
    ): RemoteViews {
        val views = RemoteViews(packageName, renderSpec.layoutRes)
        if (!cell.inCurrentMonth) {
            views.setViewVisibility(R.id.widget_month_day_root, View.INVISIBLE)
            return views
        }
        val isToday = cell.date == LocalDate.now(zoneId)
        val cellBackgroundRes = when {
                renderSpec.usesDotCells -> palette.compactItemBackgroundRes
                else -> palette.itemBackgroundRes
            }
        views.setInt(R.id.widget_month_day_card_background, "setBackgroundResource", cellBackgroundRes)
        views.setTextViewText(R.id.widget_month_day_text, monthTodayLabel(cell.date.dayOfMonth.toString(), isToday))
        views.setTextColor(R.id.widget_month_day_text, if (isToday) palette.monthTodayTextColor else palette.text)
        views.setTextViewTextSize(R.id.widget_month_day_text, TypedValue.COMPLEX_UNIT_SP, renderSpec.dayTextSp)
        if (!renderSpec.usesDotCells && textOverflowCount > 0) {
            views.setViewVisibility(R.id.widget_month_overflow_text, View.VISIBLE)
            views.setTextViewText(R.id.widget_month_overflow_text, monthTodayLabel("+$textOverflowCount", isToday))
            views.setTextColor(R.id.widget_month_overflow_text, if (isToday) palette.monthTodayTextColor else palette.muted)
        }
        val dayPendingIntent = openAppPendingIntent(KgsWidgetKind.Day, dayPendingIntentRequestCode(appWidgetId, cell.date), cell.date, clearTask = true)
        views.setOnClickPendingIntent(R.id.widget_month_day_root, dayPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_month_day_text, dayPendingIntent)
        if (!renderSpec.usesDotCells && textOverflowCount > 0) {
            views.setOnClickPendingIntent(R.id.widget_month_overflow_text, dayPendingIntent)
        }
        return views
    }

    private fun cellBottomFadeRes(cellBackgroundRes: Int): Int =
        when (cellBackgroundRes) {
            R.drawable.widget_month_cell_compact -> R.drawable.widget_month_cell_bottom_fade_compact
            R.drawable.widget_month_cell_compact_dark -> R.drawable.widget_month_cell_bottom_fade_compact_dark
            R.drawable.widget_item_background_dark -> R.drawable.widget_month_cell_bottom_fade_dark
            R.drawable.widget_month_day_selected -> R.drawable.widget_month_cell_bottom_fade_selected
            R.drawable.widget_month_day_selected_dark -> R.drawable.widget_month_cell_bottom_fade_selected_dark
            R.drawable.widget_month_day_selected_fresh -> R.drawable.widget_month_cell_bottom_fade_selected_fresh
            R.drawable.widget_month_day_selected_warm -> R.drawable.widget_month_cell_bottom_fade_selected_warm
            else -> R.drawable.widget_month_cell_bottom_fade
        }

    private fun renderMonthCell(
        cell: WidgetMonthCellContent,
        palette: WidgetPalette,
        renderSpec: WidgetMonthRenderSpec,
        appWidgetId: Int,
    ): RemoteViews {
        val views = renderMonthCellShell(cell, palette, renderSpec, appWidgetId)
        if (!cell.inCurrentMonth) return views
        val isToday = cell.date == LocalDate.now(zoneId)
        val dayPendingIntent = openAppPendingIntent(KgsWidgetKind.Day, dayPendingIntentRequestCode(appWidgetId, cell.date), cell.date, clearTask = true)

        if (renderSpec.usesDotCells) {
            views.bindMiniMonthCell(cell, palette, renderSpec, isToday)
        } else {
            views.bindTextMonthCell(cell, palette, renderSpec, isToday, dayPendingIntent)
        }
        return views
    }

    private fun RemoteViews.bindMiniMonthCell(
        cell: WidgetMonthCellContent,
        palette: WidgetPalette,
        renderSpec: WidgetMonthRenderSpec,
        isToday: Boolean,
    ) {
        val dotIds = listOf(
            R.id.widget_month_dot_1,
            R.id.widget_month_dot_2,
            R.id.widget_month_dot_3,
            R.id.widget_month_dot_4,
            R.id.widget_month_dot_5,
            R.id.widget_month_dot_6,
            R.id.widget_month_dot_7,
            R.id.widget_month_dot_8,
            R.id.widget_month_dot_9,
            R.id.widget_month_dot_10,
        )
        val sortedItems = cell.items.sortedBy { it.lane }
        val rowCounts = renderSpec.miniDotRowCountsFor(cell.totalItemCount)
        val visibleCount = minOf(sortedItems.size, rowCounts.first + rowCounts.second)
        val rowBreak = minOf(rowCounts.first, visibleCount)
        val secondRowVisibleCount = (visibleCount - rowBreak).coerceAtLeast(0)
        for (index in 0 until rowBreak) {
            val id = dotIds[index]
            val item = sortedItems[index]
            setViewVisibility(id, View.VISIBLE)
            setImageViewBitmap(id, monthDotBitmap(context, item.color))
        }
        for (index in 0 until secondRowVisibleCount) {
            val id = dotIds[WIDGET_MONTH_DOTS_PER_ROW + index]
            val itemIndex = rowBreak + index
            val item = sortedItems[itemIndex]
            setViewVisibility(id, View.VISIBLE)
            setImageViewBitmap(id, monthDotBitmap(context, item.color))
        }
        val hidden = (cell.totalItemCount - visibleCount).coerceAtLeast(0)
        if (rowBreak > 0) {
            setViewVisibility(R.id.widget_month_dot_row_1, View.VISIBLE)
        }
        if (secondRowVisibleCount > 0) {
            setViewVisibility(R.id.widget_month_dot_row_2, View.VISIBLE)
        }
        if (renderSpec.showMiniOverflow(hidden)) {
            setViewVisibility(R.id.widget_month_overflow_text, View.VISIBLE)
            setTextViewText(R.id.widget_month_overflow_text, monthTodayLabel("+$hidden", isToday))
            setTextColor(R.id.widget_month_overflow_text, if (isToday) palette.monthTodayTextColor else palette.muted)
        }
    }

    private fun RemoteViews.bindTextMonthCell(
        cell: WidgetMonthCellContent,
        palette: WidgetPalette,
        renderSpec: WidgetMonthRenderSpec,
        isToday: Boolean,
        clickPendingIntent: PendingIntent,
    ) {
        data class TextChipBinding(
            val containerId: Int,
            val textId: Int,
        )

        val chipBindings = listOf(
            TextChipBinding(R.id.widget_month_chip_1_container, R.id.widget_month_chip_1_text),
            TextChipBinding(R.id.widget_month_chip_2_container, R.id.widget_month_chip_2_text),
            TextChipBinding(R.id.widget_month_chip_3_container, R.id.widget_month_chip_3_text),
        ) + if (renderSpec.baseTextItemCapacity >= 4) {
            listOf(
                TextChipBinding(R.id.widget_month_chip_4_container, R.id.widget_month_chip_4_text),
                TextChipBinding(R.id.widget_month_chip_5_container, R.id.widget_month_chip_5_text),
                TextChipBinding(R.id.widget_month_chip_6_container, R.id.widget_month_chip_6_text),
                TextChipBinding(R.id.widget_month_chip_7_container, R.id.widget_month_chip_7_text),
                TextChipBinding(R.id.widget_month_chip_8_container, R.id.widget_month_chip_8_text),
            )
        } else emptyList()
        val textItemCapacity = renderSpec.textItemCapacityFor(listOf(cell))
        for (index in chipBindings.indices) {
            val binding = chipBindings[index]
            val item = cell.items.firstOrNull { it.lane == index }
            val visible = item != null && index < textItemCapacity
            if (visible) {
                setViewVisibility(binding.containerId, View.VISIBLE)
                val visibleItem = item
                val style = visibleItem.color.monthChipStyle()
                setInt(binding.containerId, "setBackgroundResource", style.cellBackgroundRes(visibleItem))
                setTextViewText(binding.textId, visibleItem.title)
                setTextColor(binding.textId, style.textColor)
                setTextViewTextSize(binding.textId, TypedValue.COMPLEX_UNIT_SP, renderSpec.chipTextSp)
                setViewPadding(
                    binding.textId,
                    context.dpToPx(if (visibleItem.fadesFromPrevious) 10 else 4),
                    0,
                    context.dpToPx(if (visibleItem.fadesToNext) 10 else 4),
                    0,
                )
                setOnClickPendingIntent(binding.containerId, clickPendingIntent)
                setOnClickPendingIntent(binding.textId, clickPendingIntent)
            }
        }
        val visibleCount = cell.items.count { it.lane < textItemCapacity }
        val hidden = (cell.totalItemCount - visibleCount).coerceAtLeast(0)
        if (hidden > 0 && renderSpec.showTextOverflow(textItemCapacity)) {
            setViewVisibility(R.id.widget_month_overflow_text, View.VISIBLE)
            setTextViewText(R.id.widget_month_overflow_text, monthTodayLabel("+$hidden", isToday))
            setTextColor(R.id.widget_month_overflow_text, if (isToday) palette.monthTodayTextColor else palette.muted)
        }
    }

    private fun renderMultiMonthNavigationPage(
        appWidgetId: Int,
        options: Bundle,
        settings: WidgetRenderSettings,
        page: WidgetMonthPage,
    ): RemoteViews {
        val today = LocalDate.now(zoneId)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val size = WidgetSize.from(context, options, KgsWidgetKind.Multi)
        val contentPaddingDp = 12
        val contentHeightDp = (size.heightDp - contentPaddingDp).coerceAtLeast(2)
        val monthPercent = SettingsStore.normalizeMultiWidgetMonthPercent(settings.multiWidgetMonthPercent)
        val monthPanelHeightDp = ((contentHeightDp * monthPercent) / 100f)
            .roundToInt()
            .coerceIn(1, contentHeightDp - 1)
        val agendaPanelHeightDp = (contentHeightDp - monthPanelHeightDp).coerceAtLeast(1)
        val monthSpec = WidgetMonthRenderSpec.from(
            WidgetSize(widthDp = size.widthDp, heightDp = monthPanelHeightDp),
            page.rowCount,
        )
        val views = RemoteViews(packageName, R.layout.widget_calendar_multi)
        val contentPadding = context.dpToPx(contentPaddingDp)
        views.setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        views.setViewPadding(R.id.widget_content, contentPadding, contentPadding, contentPadding, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutHeight(
                R.id.widget_multi_month_panel,
                monthPanelHeightDp.toFloat(),
                TypedValue.COMPLEX_UNIT_DIP,
            )
            views.setViewLayoutHeight(
                R.id.widget_multi_agenda_panel,
                agendaPanelHeightDp.toFloat(),
                TypedValue.COMPLEX_UNIT_DIP,
            )
        }
        views.setOnClickPendingIntent(R.id.widget_root, openMainAppPendingIntent(appWidgetId))

        val monthTitle = page.title(settings.locale)
        val hideTitle = shouldHideWidgetTitle(
            size.widthDp - contentPaddingDp * 2,
            monthTitle,
            reservedDp = 150f,
            textSp = monthSpec.titleTextSp,
        )
        val showingCurrentMonth = page.month == YearMonth.from(today)
        views.setTextViewText(R.id.widget_multi_month_title, monthTitle)
        views.setTextColor(R.id.widget_multi_month_title, palette.text)
        views.setTextViewTextSize(
            R.id.widget_multi_month_title,
            TypedValue.COMPLEX_UNIT_SP,
            monthSpec.titleTextSp,
        )
        views.setViewVisibility(R.id.widget_multi_month_title, if (hideTitle) View.GONE else View.VISIBLE)
        views.setInt(
            R.id.widget_multi_month_header,
            "setGravity",
            if (hideTitle) Gravity.CENTER else Gravity.CENTER_VERTICAL,
        )
        views.setTextViewText(R.id.widget_multi_month_badge, "+")
        views.setTextViewTextSize(R.id.widget_multi_month_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
        views.setTextColor(R.id.widget_multi_month_badge, palette.onAccent)
        views.setInt(R.id.widget_multi_month_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setOnClickPendingIntent(R.id.widget_multi_month_badge, createEventPendingIntent(appWidgetId, today))
        views.setImageViewBitmap(
            R.id.widget_day_today,
            widgetTodayDateIconBitmap(context, palette.onAccent, today.dayOfMonth),
        )
        views.setInt(R.id.widget_day_today, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setViewVisibility(R.id.widget_day_today, if (showingCurrentMonth) View.GONE else View.VISIBLE)
        views.setOnClickPendingIntent(
            R.id.widget_day_today,
            monthTodayPendingIntent(appWidgetId, targetKind = KgsWidgetKind.Multi),
        )
        views.setTextViewText(R.id.widget_month_prev, "\u2039")
        views.setTextViewText(R.id.widget_month_next, "\u203A")
        views.setTextColor(R.id.widget_month_prev, palette.accent)
        views.setTextColor(R.id.widget_month_next, palette.accent)
        views.setOnClickPendingIntent(
            R.id.widget_month_prev,
            monthNavigationPendingIntent(appWidgetId, previous = true, targetKind = KgsWidgetKind.Multi),
        )
        views.setOnClickPendingIntent(
            R.id.widget_month_next,
            monthNavigationPendingIntent(appWidgetId, previous = false, targetKind = KgsWidgetKind.Multi),
        )
        views.setOnClickPendingIntent(
            R.id.widget_multi_month_title,
            openAppPendingIntent(KgsWidgetKind.Month, 66_000 + appWidgetId, page.month.atDay(1)),
        )
        views.bindMonthPage(
            pageContainerId = R.id.widget_month_section,
            page = page,
            settings = settings,
            palette = palette,
            renderSpec = monthSpec,
            appWidgetId = appWidgetId,
        )
        views.setViewVisibility(R.id.widget_month_section, View.VISIBLE)
        return views
    }

    private suspend fun renderMultiWidget(
        appWidgetId: Int,
        options: Bundle,
        forceServiceCollection: Boolean = false,
        prepared: PreparedMultiWidgetRender? = null,
    ): RemoteViews {
        val renderData = preparedWidgetValue(prepared) {
            prepareMultiWidget(appWidgetId, options)
        }
        require(renderData.appWidgetId == appWidgetId)
        val today = renderData.today
        val settings = renderData.settings
        val textContext = context.withWidgetLocale(settings.locale)
        val palette = renderData.palette
        val size = renderData.size
        val contentPaddingDp = WIDGET_MULTI_CONTENT_PADDING_DP
        val monthPanelHeightDp = renderData.monthPanelHeightDp
        val agendaPanelHeightDp = renderData.agendaPanelHeightDp
        val page = renderData.page
        val monthSpec = renderData.monthSpec
        val views = RemoteViews(packageName, R.layout.widget_calendar_multi)
        val contentPadding = context.dpToPx(contentPaddingDp)
        val openApp = openMainAppPendingIntent(appWidgetId)

        views.setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        views.setViewPadding(R.id.widget_content, contentPadding, contentPadding, contentPadding, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutHeight(R.id.widget_multi_month_panel, monthPanelHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_multi_agenda_panel, agendaPanelHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp)

        val multiMonthTitle = page.title(settings.locale)
        val hideMultiMonthTitle = shouldHideWidgetTitle(size.widthDp - contentPaddingDp * 2, multiMonthTitle, reservedDp = 150f, textSp = monthSpec.titleTextSp)
        val showingCurrentMonth = page.month == YearMonth.from(today)
        views.setTextViewText(R.id.widget_multi_month_title, multiMonthTitle)
        views.setTextColor(R.id.widget_multi_month_title, palette.text)
        views.setTextViewTextSize(R.id.widget_multi_month_title, TypedValue.COMPLEX_UNIT_SP, monthSpec.titleTextSp)
        views.setViewVisibility(R.id.widget_multi_month_title, if (hideMultiMonthTitle) View.GONE else View.VISIBLE)
        views.setInt(R.id.widget_multi_month_header, "setGravity", if (hideMultiMonthTitle) Gravity.CENTER else Gravity.CENTER_VERTICAL)
        views.setTextViewText(R.id.widget_multi_month_badge, "+")
        views.setTextViewTextSize(R.id.widget_multi_month_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
        views.setTextColor(R.id.widget_multi_month_badge, palette.onAccent)
        views.setInt(R.id.widget_multi_month_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setOnClickPendingIntent(R.id.widget_multi_month_badge, createEventPendingIntent(appWidgetId, today))
        views.setImageViewBitmap(R.id.widget_day_today, widgetTodayDateIconBitmap(context, palette.onAccent, today.dayOfMonth))
        views.setInt(R.id.widget_day_today, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setViewVisibility(R.id.widget_day_today, if (showingCurrentMonth) View.GONE else View.VISIBLE)
        views.setOnClickPendingIntent(R.id.widget_day_today, monthTodayPendingIntent(appWidgetId, targetKind = KgsWidgetKind.Multi))
        views.setTextViewText(R.id.widget_month_prev, "\u2039")
        views.setTextViewText(R.id.widget_month_next, "\u203A")
        views.setTextColor(R.id.widget_month_prev, palette.accent)
        views.setTextColor(R.id.widget_month_next, palette.accent)
        views.setOnClickPendingIntent(R.id.widget_month_prev, monthNavigationPendingIntent(appWidgetId, previous = true, targetKind = KgsWidgetKind.Multi))
        views.setOnClickPendingIntent(R.id.widget_month_next, monthNavigationPendingIntent(appWidgetId, previous = false, targetKind = KgsWidgetKind.Multi))
        views.setOnClickPendingIntent(
            R.id.widget_multi_month_title,
            openAppPendingIntent(KgsWidgetKind.Month, 66_000 + appWidgetId, page.month.atDay(1)),
        )
        views.bindMonthPage(
            pageContainerId = R.id.widget_month_section,
            page = page,
            settings = settings,
            palette = palette,
            renderSpec = monthSpec,
            appWidgetId = appWidgetId,
        )
        views.setViewVisibility(R.id.widget_month_section, View.VISIBLE)

        views.bindCollectionBottomFade(palette, visible = true)
        views.setInt(R.id.widget_multi_agenda_top_fade, "setBackgroundResource", palette.topFadeRes)
        views.setTextViewText(R.id.widget_empty, KgsWidgetKind.Multi.emptyText(textContext))
        views.setTextColor(R.id.widget_empty, palette.muted)
        views.setViewVisibility(R.id.widget_empty, View.GONE)
        views.setViewVisibility(R.id.widget_list, View.VISIBLE)
        if (KgsWidgetKind.Multi.usesDirectCollectionItems() && !forceServiceCollection) {
            views.bindDirectCollectionItems(
                context = textContext,
                packageName = packageName,
                palette = palette,
                rows = renderData.collectionSnapshot.rows,
                sourceKind = KgsWidgetKind.Multi,
                appWidgetId = appWidgetId,
                renderOptions = WidgetCollectionRenderOptions(taskArtWidthDp = renderData.collectionSnapshot.taskArtWidthDp),
            )
        } else {
            views.setRemoteAdapter(R.id.widget_list, collectionAdapterIntent(KgsWidgetKind.Multi, appWidgetId))
        }
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)
        views.setPendingIntentTemplate(R.id.widget_list, collectionClickPendingIntent(KgsWidgetKind.Multi, appWidgetId))
        return views
    }

    private suspend fun renderCollectionWidget(
        kind: KgsWidgetKind,
        appWidgetId: Int,
        options: Bundle,
        forceServiceCollection: Boolean = false,
        collectionSnapshot: WidgetCollectionSnapshot? = null,
    ): RemoteViews {
        collectionSnapshot?.let { snapshot ->
            require(snapshot.kind == kind && snapshot.appWidgetId == appWidgetId)
        }
        val today = LocalDate.now(zoneId)
        val settings = preparedWidgetValue(collectionSnapshot?.settings) {
            dataSource.loadSettings(kind)
        }
        val textContext = context.withWidgetLocale(settings.locale)
        val palette = collectionSnapshot?.palette
            ?: WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val size = collectionSnapshot?.renderSize ?: WidgetSize.from(context, options, kind)
        val views = RemoteViews(packageName, if (kind == KgsWidgetKind.Tasks) R.layout.widget_calendar_tasks else R.layout.widget_calendar)
        views.bindBaseShell(kind, appWidgetId, settings, palette, today, size)
        if (kind == KgsWidgetKind.Multi) {
            val month = YearMonth.from(today)
            val page = monthPageSource.load(month, settings)
            val monthBucket = if (size.compact) WidgetSizeBucket.Mini else WidgetSizeBucket.Compact
            views.setViewVisibility(R.id.widget_month_section, View.VISIBLE)
            views.bindMonthPage(
                pageContainerId = R.id.widget_month_section,
                page = page,
                settings = settings,
                palette = palette,
                renderSpec = WidgetMonthRenderSpec(
                    bucket = monthBucket,
                    weekCellHeightDp = WidgetMonthRenderSpec.weekCellHeightDp(size, page.rowCount),
                    cellContentWidthDp = WidgetMonthRenderSpec.cellContentWidthDp(
                        size = size,
                        bucket = monthBucket,
                    ),
                ),
                appWidgetId = appWidgetId,
            )
        } else {
            views.setViewVisibility(R.id.widget_month_section, View.GONE)
        }
        views.setTextViewText(R.id.widget_empty, kind.emptyText(textContext))
        views.setTextColor(R.id.widget_empty, palette.muted)
        if (kind.usesDirectCollectionItems() && !forceServiceCollection) {
            val rows = preparedWidgetValue(collectionSnapshot?.rows) {
                dataSource.listRows(kind, settings, appWidgetId)
            }
            views.bindDirectCollectionItems(
                context = textContext,
                packageName = packageName,
                palette = palette,
                rows = rows,
                sourceKind = kind,
                appWidgetId = appWidgetId,
                renderOptions = WidgetCollectionRenderOptions(taskArtWidthDp = size.collectionArtWidthDp(kind)),
            )
        } else {
            views.setRemoteAdapter(R.id.widget_list, collectionAdapterIntent(kind, appWidgetId))
        }
        if (kind != KgsWidgetKind.Tasks) {
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
        }
        views.setPendingIntentTemplate(R.id.widget_list, collectionClickPendingIntent(kind, appWidgetId))
        return views
    }

    private fun RemoteViews.bindBaseShell(
        kind: KgsWidgetKind,
        appWidgetId: Int,
        settings: WidgetRenderSettings,
        palette: WidgetPalette,
        today: LocalDate,
        size: WidgetSize? = null,
        ) {
        setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        setInt(R.id.widget_header, "setBackgroundResource", palette.rootBackgroundRes)
        val contentPadding = context.dpToPx(12)
        val textContext = context.withWidgetLocale(settings.locale)
        val agendaEdgeFade = kind == KgsWidgetKind.Agenda
        setViewPadding(
            R.id.widget_content,
            if (kind == KgsWidgetKind.Tasks) 0 else contentPadding,
            contentPadding,
            if (kind == KgsWidgetKind.Tasks) 0 else contentPadding,
            if (kind == KgsWidgetKind.Tasks || agendaEdgeFade) 0 else contentPadding,
        )
        if (agendaEdgeFade) {
            setViewPadding(R.id.widget_header, 0, 0, 0, 0)
        }
        val titleText = kind.title(textContext)
        setTextViewText(R.id.widget_title, titleText)
        setTextColor(R.id.widget_title, palette.text)
        setTextViewText(R.id.widget_subtitle, headerSubtitle(kind, today, settings))
        setTextColor(R.id.widget_subtitle, palette.muted)
        val hideTitleText = size?.let { widgetSize ->
            val reservedDp = if (kind == KgsWidgetKind.Tasks) {
                56f + settings.tasksWidgetSortMode.widgetButtonWidthDp(textContext)
            } else {
                54f
            }
            shouldHideWidgetTitle(widgetSize.widthDp, titleText, reservedDp, textSp = 16f)
        } == true
        setViewVisibility(R.id.widget_title_group, if (hideTitleText) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.widget_title, if (hideTitleText) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.widget_subtitle, if (hideTitleText) View.GONE else View.VISIBLE)
        setInt(R.id.widget_header, "setGravity", if (hideTitleText) Gravity.CENTER else Gravity.CENTER_VERTICAL)
        if (kind == KgsWidgetKind.Tasks) {
            val openTasksIntent = openAppPendingIntent(KgsWidgetKind.Tasks, 45_000 + appWidgetId, today)
            setOnClickPendingIntent(R.id.widget_title, openTasksIntent)
            setOnClickPendingIntent(R.id.widget_subtitle, openTasksIntent)
        }
        setTextColor(R.id.widget_badge, palette.onAccent)
        setInt(R.id.widget_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        setInt(R.id.widget_sort, "setBackgroundResource", palette.sortBackgroundRes)
        setImageViewBitmap(R.id.widget_sort_icon, widgetSortIconBitmap(context, palette.onAccent))
        setTextColor(R.id.widget_sort_label_date, palette.onAccent)
        setTextColor(R.id.widget_sort_label_priority, palette.onAccent)
        setTextColor(R.id.widget_sort_label_status, palette.onAccent)
        setTextViewText(R.id.widget_sort_label_date, WidgetTaskSortMode.Date.widgetLabel(textContext))
        setTextViewText(R.id.widget_sort_label_priority, WidgetTaskSortMode.Priority.widgetLabel(textContext))
        setTextViewText(R.id.widget_sort_label_status, WidgetTaskSortMode.Status.widgetLabel(textContext))
        if (kind == KgsWidgetKind.Tasks) {
            bindTasksSortButtonState(
                context = textContext,
                appWidgetId = appWidgetId,
                mode = settings.tasksWidgetSortMode,
                widthDp = settings.tasksWidgetSortMode.widgetButtonWidthDp(textContext),
                createMode = settings.tasksWidgetCreateMode,
            )
        }
        setViewVisibility(R.id.widget_sort, if (kind == KgsWidgetKind.Tasks) View.VISIBLE else View.GONE)
        bindCollectionBottomFade(palette, visible = kind == KgsWidgetKind.Tasks || agendaEdgeFade)
        if (kind != KgsWidgetKind.Tasks) {
            setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(kind, appWidgetId, today))
        }
        when (kind) {
            KgsWidgetKind.Tasks -> {
                setTextViewText(R.id.widget_badge, "+")
                setTextViewTextSize(R.id.widget_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
                setOnClickPendingIntent(R.id.widget_badge, createTaskPendingIntent(appWidgetId, today, settings.tasksWidgetCreateMode))
            }
            KgsWidgetKind.Agenda -> {
                setTextViewText(R.id.widget_badge, "+")
                setTextViewTextSize(R.id.widget_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
                setOnClickPendingIntent(R.id.widget_badge, createEventPendingIntent(appWidgetId, today))
            }
            else -> {
                setTextViewText(R.id.widget_badge, "\u21BB")
                setTextViewTextSize(R.id.widget_badge, TypedValue.COMPLEX_UNIT_SP, 13f)
                setOnClickPendingIntent(R.id.widget_badge, refreshPendingIntent(kind, appWidgetId))
            }
        }
        setViewVisibility(R.id.widget_empty, View.GONE)
        setViewVisibility(R.id.widget_list, View.VISIBLE)
    }

    private fun RemoteViews.showEmpty(text: String, palette: WidgetPalette) {
        setViewVisibility(R.id.widget_empty, View.VISIBLE)
        setTextViewText(R.id.widget_empty, text)
        setTextColor(R.id.widget_empty, palette.muted)
    }

    private fun headerSubtitle(kind: KgsWidgetKind, today: LocalDate, settings: WidgetRenderSettings): String {
        val textContext = context.withWidgetLocale(settings.locale)
        val shortDate = today.format(DateTimeFormatter.ofPattern("EEE, d. MMM", settings.locale))
        return when (kind) {
            KgsWidgetKind.Month, KgsWidgetKind.Multi -> YearMonth.from(today).format(DateTimeFormatter.ofPattern("MMMM yyyy", settings.locale))
            KgsWidgetKind.Day -> shortDate
            KgsWidgetKind.Tasks -> settings.tasksWidgetDisplayMode.widgetLabel(textContext)
            KgsWidgetKind.Agenda -> shortDate
        }
    }

    private fun collectionAdapterIntent(kind: KgsWidgetKind, appWidgetId: Int): Intent =
        Intent(context, KgsWidgetCollectionService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(EXTRA_WIDGET_KIND, kind.name)
            data = Uri.parse("kgs-calendar://widget-collection/${kind.name}/$appWidgetId")
        }

    private fun collectionClickPendingIntent(kind: KgsWidgetKind, appWidgetId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            50_000 + appWidgetId * 10 + kind.ordinal,
            Intent(context, KgsWidgetActionReceiver::class.java).apply {
                action = KgsWidgetProvider.ACTION_COLLECTION_CLICK
                data = Uri.parse("kgs-calendar://widget-collection-click/${kind.name}/$appWidgetId")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    private fun openMainAppPendingIntent(appWidgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("kgs-calendar://widget-open/$appWidgetId/${SystemClock.elapsedRealtime()}")
        }
        return PendingIntent.getActivity(
            context,
            60_000 + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(kind: KgsWidgetKind, requestCode: Int, date: LocalDate, clearTask: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                (if (clearTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_WIDGET_KIND, kind.name)
            putExtra(EXTRA_WIDGET_DATE, date.toString())
            data = Uri.parse("kgs-calendar://widget/${kind.name}/$requestCode/$date")
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createEventPendingIntent(appWidgetId: Int, date: LocalDate): PendingIntent {
        val requestCode = 30_000 + appWidgetId
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WIDGET_KIND, KgsWidgetKind.Day.name)
            putExtra(EXTRA_WIDGET_DATE, date.toString())
            putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_CREATE_EVENT)
            data = Uri.parse("kgs-calendar://widget-create-event/$appWidgetId/$date")
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createTaskPendingIntent(appWidgetId: Int, date: LocalDate, mode: WidgetTaskCreateMode): PendingIntent {
        val requestCode = 35_000 + appWidgetId
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WIDGET_KIND, KgsWidgetKind.Tasks.name)
            putExtra(EXTRA_WIDGET_DATE, date.toString())
            putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_CREATE_TASK)
            putExtra(EXTRA_WIDGET_TASK_CREATE_MODE, mode.name)
            data = Uri.parse("kgs-calendar://widget-create-task/$appWidgetId/$date/${mode.name}")
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun monthNavigationPendingIntent(
        appWidgetId: Int,
        previous: Boolean,
        targetKind: KgsWidgetKind = KgsWidgetKind.Month,
    ): PendingIntent {
        val intent = Intent(context, targetKind.providerClass).apply {
            action = if (previous) KgsWidgetProvider.ACTION_MONTH_PREVIOUS else KgsWidgetProvider.ACTION_MONTH_NEXT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-month/${targetKind.name}/${if (previous) "previous" else "next"}/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            20_000 + appWidgetId * 10 + targetKind.ordinal * 2 + if (previous) 0 else 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun monthTodayPendingIntent(
        appWidgetId: Int,
        targetKind: KgsWidgetKind = KgsWidgetKind.Month,
    ): PendingIntent {
        val intent = Intent(context, targetKind.providerClass).apply {
            action = KgsWidgetProvider.ACTION_MONTH_TODAY
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-month/${targetKind.name}/today/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            23_000 + appWidgetId * 10 + targetKind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dayNavigationPendingIntent(appWidgetId: Int, previous: Boolean): PendingIntent {
        val intent = Intent(context, KgsWidgetKind.Day.providerClass).apply {
            action = if (previous) KgsWidgetProvider.ACTION_DAY_PREVIOUS else KgsWidgetProvider.ACTION_DAY_NEXT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-day/${if (previous) "previous" else "next"}/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            24_000 + appWidgetId * 2 + if (previous) 0 else 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dayTodayPendingIntent(appWidgetId: Int): PendingIntent {
        val intent = Intent(context, KgsWidgetKind.Day.providerClass).apply {
            action = KgsWidgetProvider.ACTION_DAY_TODAY
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-day/today/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            27_000 + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun refreshPendingIntent(kind: KgsWidgetKind, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, kind.providerClass).apply {
            action = KgsWidgetProvider.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-refresh/${kind.name}/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            10_000 + appWidgetId + kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
