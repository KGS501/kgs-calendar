package com.kgs.calendar.ui.labels

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.domain.model.REMINDER_AT_END
import com.kgs.calendar.domain.model.REMINDER_AT_START
import com.kgs.calendar.domain.model.normalizedReminderOffsets
import kotlinx.datetime.LocalDate
import com.kgs.calendar.time.DateTimeFormatter
import com.kgs.calendar.time.AppLocale as Locale

enum class ReminderUnit(val label: String, val minutes: Int) {
    Minutes("Minutes", 1),
    Hours("Hours", 60),
    Days("Days", 1440),
    Weeks("Weeks", 10080),
}

enum class ReminderChoice(val label: String, val minutes: Int?) {
    None("No reminder", null),
    AtStart("At start", REMINDER_AT_START),
    AtEnd("At end", REMINDER_AT_END),
    FifteenMinutes("15 min before", 15),
    TwoHours("2 hours before", 120),
    OneDay("1 day before", 1440),
    OneWeek("1 week before", 10080),
    Custom("Custom reminder", Int.MIN_VALUE),
}

enum class RecurrenceOption(val label: String, val rule: String?) {
    Once("One-time", null),
    Daily("Daily", "FREQ=DAILY"),
    Weekly("Weekly", "FREQ=WEEKLY"),
    Monthly("Monthly", "FREQ=MONTHLY"),
    Yearly("Yearly", "FREQ=YEARLY"),
    Custom("Custom", "CUSTOM"),
}

fun String?.parseReminderMinutes(): Set<Int> =
    this?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.normalizedReminderOffsets()?.toSet().orEmpty()

fun Int.toReminderAmountUnit(): Pair<Int, ReminderUnit> {
    if (this <= 0) return 0 to ReminderUnit.Minutes
    return when {
        this % ReminderUnit.Weeks.minutes == 0 -> this / ReminderUnit.Weeks.minutes to ReminderUnit.Weeks
        this % ReminderUnit.Days.minutes == 0 -> this / ReminderUnit.Days.minutes to ReminderUnit.Days
        this % ReminderUnit.Hours.minutes == 0 -> this / ReminderUnit.Hours.minutes to ReminderUnit.Hours
        else -> this to ReminderUnit.Minutes
    }
}

fun String?.reminderSummary(): String? {
    val minutes = this?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.distinct()?.sorted().orEmpty()
    if (minutes.isEmpty()) return null
    return minutes.joinToString(", ") {
        when {
            it == REMINDER_AT_END -> "At end"
            it == REMINDER_AT_START -> "At start"
            it % 1440 == 0 -> "${it / 1440} day(s) before"
            it % 60 == 0 -> "${it / 60} hr before"
            else -> "$it min before"
        }
    }
}

fun String.toRecurrenceOption(): RecurrenceOption {
    val freq = recurrenceFrequency() ?: return RecurrenceOption.Once
    return when (freq) {
        "DAILY" -> RecurrenceOption.Daily
        "WEEKLY" -> if (hasOnlyFrequency()) RecurrenceOption.Weekly else RecurrenceOption.Custom
        "MONTHLY" -> if (hasOnlyFrequency()) RecurrenceOption.Monthly else RecurrenceOption.Custom
        "YEARLY" -> if (hasOnlyFrequency()) RecurrenceOption.Yearly else RecurrenceOption.Custom
        else -> RecurrenceOption.Custom
    }
}

fun String.toRecurrenceLabel(): String {
    val rule = trim()
    if (rule.isBlank()) return "One-time"
    val freqLabel = when (rule.recurrenceFrequency()) {
        "DAILY" -> "Daily"
        "WEEKLY" -> "Weekly"
        "MONTHLY" -> "Monthly"
        "YEARLY" -> "Yearly"
        else -> "Custom"
    }
    val dayPart = rule.recurrencePart("BYDAY") ?: rule.recurrencePart("DAY")
    val dayLabel = dayPart?.split(',')?.joinToString(", ") { it.toWeekdayLabel() }
    val interval = rule.recurrencePart("INTERVAL")?.toIntOrNull()?.takeIf { it > 1 }?.let { "every $it" }
    val count = rule.recurrencePart("COUNT")?.let { "$it times" }
    val until = rule.recurrencePart("UNTIL")?.toIsoUntilDate()
    return listOfNotNull(freqLabel, interval, dayLabel?.let { "on $it" }, count, until?.let { "until $it" }).joinToString(" ")
}

fun String.recurrenceFrequency(): String? =
    recurrencePart("FREQ")?.uppercase()

fun String.recurrencePart(key: String): String? =
    split(';')
        .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] } }
        .firstOrNull { it.first == key.uppercase() }
        ?.second

fun String.hasOnlyFrequency(): Boolean =
    trim().split(';').filter { it.isNotBlank() }.size == 1

fun String.toIsoUntilDate(): String? {
    val raw = take(8)
    return runCatching { LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE).toString() }.getOrNull()
}

fun String.toRecurrenceUntilValue(): String? =
    runCatching { LocalDate.parse(this).format(DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()

fun RecurrenceOption.intervalUnitLabel(): String = when (this) {
    RecurrenceOption.Daily -> "Days"
    RecurrenceOption.Weekly -> "Weeks"
    RecurrenceOption.Monthly -> "Months"
    RecurrenceOption.Yearly -> "Years"
    else -> "Intervals"
}

fun String.toWeekdayLabel(): String = when (uppercase()) {
    "MO" -> "Mon"
    "TU" -> "Tue"
    "WE" -> "Wed"
    "TH" -> "Thu"
    "FR" -> "Fri"
    "SA" -> "Sat"
    "SU" -> "Sun"
    else -> this
}
