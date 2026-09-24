package com.kgs.calendar.widget.render

import android.app.PendingIntent
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.kgs.calendar.R
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.WIDGET_MONTH_SPAN_FADE_WIDTH_DP
import com.kgs.calendar.widget.bitmap.monthDotBitmap
import com.kgs.calendar.widget.dpToPx
import com.kgs.calendar.widget.model.WIDGET_MONTH_DOTS_PER_ROW
import com.kgs.calendar.widget.model.WidgetMonthCellContent
import com.kgs.calendar.widget.model.WidgetMonthPage
import com.kgs.calendar.widget.model.WidgetMonthRenderSpec
import com.kgs.calendar.widget.model.WidgetMonthWeekSegment
import com.kgs.calendar.widget.model.WidgetRenderSettings
import com.kgs.calendar.widget.model.monthBottomFadeSegments
import com.kgs.calendar.widget.model.monthWeekSegments
import com.kgs.calendar.widget.theme.WidgetPalette
import com.kgs.calendar.widget.theme.cellBottomFadeRes
import com.kgs.calendar.widget.theme.monthBottomFadeSpanLayout
import com.kgs.calendar.widget.theme.monthChipMaskRes
import com.kgs.calendar.widget.theme.monthChipStyle
import com.kgs.calendar.widget.theme.monthSpanChipLayout
import com.kgs.calendar.widget.theme.monthSpanTextStartPaddingDp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import kotlin.math.roundToInt

internal class WidgetMonthPageBinder(
    private val context: Context,
    private val zoneId: ZoneId,
) {
    private val packageName = context.packageName
    private val intents = WidgetPendingIntents(context)

    fun bindMonthPage(
        views: RemoteViews,
        pageContainerId: Int,
        page: WidgetMonthPage,
        settings: WidgetRenderSettings,
        palette: WidgetPalette,
        renderSpec: WidgetMonthRenderSpec,
        appWidgetId: Int,
    ) = views.bindPage(pageContainerId, page, settings, palette, renderSpec, appWidgetId)

    private fun RemoteViews.bindPage(
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
                val chipPendingIntent = intents.openAppPendingIntent(
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
                    intents.openAppPendingIntent(KgsWidgetKind.Day, dayPendingIntentRequestCode(appWidgetId, cell.date), cell.date, clearTask = true),
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
        val dayPendingIntent = intents.openAppPendingIntent(KgsWidgetKind.Day, dayPendingIntentRequestCode(appWidgetId, cell.date), cell.date, clearTask = true)
        views.setOnClickPendingIntent(R.id.widget_month_day_root, dayPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_month_day_text, dayPendingIntent)
        if (!renderSpec.usesDotCells && textOverflowCount > 0) {
            views.setOnClickPendingIntent(R.id.widget_month_overflow_text, dayPendingIntent)
        }
        return views
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
        val dayPendingIntent = intents.openAppPendingIntent(KgsWidgetKind.Day, dayPendingIntentRequestCode(appWidgetId, cell.date), cell.date, clearTask = true)

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
}

internal fun monthTodayLabel(text: String, isToday: Boolean): CharSequence =
    if (isToday && text.isNotEmpty()) {
        SpannableString(text).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    } else {
        text
    }
