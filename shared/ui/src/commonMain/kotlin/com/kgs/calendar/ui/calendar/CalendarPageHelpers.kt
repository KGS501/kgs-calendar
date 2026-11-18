package com.kgs.calendar.ui.calendar

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import com.kgs.calendar.time.DateTimeFormatter
import com.kgs.calendar.time.TextStyle
import com.kgs.calendar.time.ChronoUnit
import com.kgs.calendar.time.AppLocale as Locale

const val DayStartHour = 0
const val DayEndHour = 23
const val DayPagerPageCount = 80_000
const val DefaultTaskDurationMillis = 30L * 60L * 1000L
const val MonthStripPageCount = 24_000
const val MonthStripCenterPage = MonthStripPageCount / 2
const val MonthViewPageCount = 4_800
const val YearStripBase = 1800
const val YearStripPageCount = 401

val DayPagerBaseDate: LocalDate = LocalDate.of(2000, 1, 1)
val MonthStripBaseMonth: YearMonth = YearMonth.of(2000, 1)
val MonthViewBase: YearMonth = YearMonth.of(YearStripBase, 1)

fun LocalDate.toDayPage(): Int =
    ChronoUnit.DAYS.between(DayPagerBaseDate, this).toInt().coerceIn(0, DayPagerPageCount - 1)

fun Int.toDayDate(): LocalDate =
    DayPagerBaseDate.plusDays(toLong())

fun YearMonth.toMonthPage(): Int =
    (MonthStripCenterPage + ChronoUnit.MONTHS.between(MonthStripBaseMonth, this))
        .toInt()
        .coerceIn(0, MonthStripPageCount - 1)

fun Int.toMonth(): YearMonth =
    MonthStripBaseMonth.plusMonths((this - MonthStripCenterPage).toLong())

fun YearMonth.toMonthViewPage(): Int =
    ChronoUnit.MONTHS.between(MonthViewBase, this)
        .toInt()
        .coerceIn(0, MonthViewPageCount - 1)

fun YearMonth.shortMonthLabel(formatter: DateTimeFormatter, locale: Locale): String {
    val short = format(formatter).replaceFirstChar { it.titlecase() }
    val full = format(DateTimeFormatter.ofPattern("MMMM", locale)).replaceFirstChar { it.titlecase() }
    return if (short.removeSuffix(".").equals(full, ignoreCase = true)) {
        full
    } else {
        short.removeSuffix(".") + "."
    }
}

fun YearMonth.monthGridRowCount(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): Int {
    val leadingEmptyDays = atDay(1).leadingDaysFrom(firstDayOfWeek)
    return maxOf(5, (leadingEmptyDays + lengthOfMonth() + 6) / 7)
}

fun YearMonth.monthGridHeight(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): Dp {
    val rows = monthGridRowCount(firstDayOfWeek)
    return (rows * 44 + (rows - 1) * 2).dp
}

fun YearMonth.overviewPanelHeight(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): Dp =
    96.dp + monthGridHeight(firstDayOfWeek)

fun LocalDate.leadingDaysFrom(firstDayOfWeek: DayOfWeek): Int =
    (dayOfWeek.value - firstDayOfWeek.value + 7) % 7

fun weekHeaderLabels(firstDayOfWeek: DayOfWeek, locale: Locale): List<String> =
    (0 until 7).map { offset ->
        firstDayOfWeek.plus(offset.toLong()).getDisplayName(TextStyle.NARROW, locale)
    }
