package com.kgs.calendar.ui.calendar

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal const val DayStartHour = 0
internal const val DayEndHour = 23
internal const val DayPagerPageCount = 80_000
internal const val DefaultTaskDurationMillis = 30L * 60L * 1000L
internal const val MonthStripPageCount = 24_000
internal const val MonthStripCenterPage = MonthStripPageCount / 2
internal const val MonthViewPageCount = 4_800
internal const val YearStripBase = 1800
internal const val YearStripPageCount = 401

internal val DayPagerBaseDate: LocalDate = LocalDate.of(2000, 1, 1)
internal val MonthStripBaseMonth: YearMonth = YearMonth.of(2000, 1)
internal val MonthViewBase: YearMonth = YearMonth.of(YearStripBase, 1)

internal fun LocalDate.toDayPage(): Int =
    ChronoUnit.DAYS.between(DayPagerBaseDate, this).toInt().coerceIn(0, DayPagerPageCount - 1)

internal fun Int.toDayDate(): LocalDate =
    DayPagerBaseDate.plusDays(toLong())

internal fun YearMonth.toMonthPage(): Int =
    (MonthStripCenterPage + ChronoUnit.MONTHS.between(MonthStripBaseMonth, this))
        .toInt()
        .coerceIn(0, MonthStripPageCount - 1)

internal fun Int.toMonth(): YearMonth =
    MonthStripBaseMonth.plusMonths((this - MonthStripCenterPage).toLong())

internal fun YearMonth.toMonthViewPage(): Int =
    ChronoUnit.MONTHS.between(MonthViewBase, this)
        .toInt()
        .coerceIn(0, MonthViewPageCount - 1)

internal fun YearMonth.shortMonthLabel(formatter: DateTimeFormatter, locale: Locale): String {
    val short = format(formatter).replaceFirstChar { it.titlecase(locale) }
    val full = format(DateTimeFormatter.ofPattern("MMMM", locale)).replaceFirstChar { it.titlecase(locale) }
    return if (short.removeSuffix(".").equals(full, ignoreCase = true)) {
        full
    } else {
        short.removeSuffix(".") + "."
    }
}

internal fun YearMonth.monthGridRowCount(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): Int {
    val leadingEmptyDays = atDay(1).leadingDaysFrom(firstDayOfWeek)
    return maxOf(5, (leadingEmptyDays + lengthOfMonth() + 6) / 7)
}

internal fun YearMonth.monthGridHeight(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): Dp {
    val rows = monthGridRowCount(firstDayOfWeek)
    return (rows * 44 + (rows - 1) * 2).dp
}

internal fun YearMonth.overviewPanelHeight(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): Dp =
    96.dp + monthGridHeight(firstDayOfWeek)

internal fun LocalDate.leadingDaysFrom(firstDayOfWeek: DayOfWeek): Int =
    (dayOfWeek.value - firstDayOfWeek.value + 7) % 7

internal fun weekHeaderLabels(firstDayOfWeek: DayOfWeek): List<String> =
    (0 until 7).map { offset ->
        val day = DayOfWeek.of(((firstDayOfWeek.value - 1 + offset) % 7) + 1)
        when (day) {
            DayOfWeek.MONDAY -> "M"
            DayOfWeek.TUESDAY -> "D"
            DayOfWeek.WEDNESDAY -> "M"
            DayOfWeek.THURSDAY -> "D"
            DayOfWeek.FRIDAY -> "F"
            DayOfWeek.SATURDAY -> "S"
            DayOfWeek.SUNDAY -> "S"
        }
    }
