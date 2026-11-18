package com.kgs.calendar.data.settings

/**
 * The four reminders the reminder popup opens with: one amount each of minutes, hours, days and
 * weeks before. The user picks exactly one per unit in the settings.
 */
data class ReminderPresets(
    val minutes: Int = DEFAULT_MINUTES,
    val hours: Int = DEFAULT_HOURS,
    val days: Int = DEFAULT_DAYS,
    val weeks: Int = DEFAULT_WEEKS,
) {
    /** Keeps every amount within the range its unit allows. */
    fun coerced(): ReminderPresets = ReminderPresets(
        minutes = minutes.coerceIn(MINUTES_RANGE),
        hours = hours.coerceIn(HOURS_RANGE),
        days = days.coerceIn(DAYS_RANGE),
        weeks = weeks.coerceIn(WEEKS_RANGE),
    )

    fun encode(): String = with(coerced()) { "$minutes,$hours,$days,$weeks" }

    companion object {
        const val DEFAULT_MINUTES = 15
        const val DEFAULT_HOURS = 2
        const val DEFAULT_DAYS = 1
        const val DEFAULT_WEEKS = 1
        val MINUTES_RANGE = 1..120
        val HOURS_RANGE = 1..48
        val DAYS_RANGE = 1..30
        val WEEKS_RANGE = 1..4

        /** Unreadable or missing values fall back to the defaults, each on its own. */
        fun decode(value: String?): ReminderPresets {
            val parts = value?.split(',')?.map { it.trim().toIntOrNull() }.orEmpty()
            return ReminderPresets(
                minutes = parts.getOrNull(0) ?: DEFAULT_MINUTES,
                hours = parts.getOrNull(1) ?: DEFAULT_HOURS,
                days = parts.getOrNull(2) ?: DEFAULT_DAYS,
                weeks = parts.getOrNull(3) ?: DEFAULT_WEEKS,
            ).coerced()
        }
    }
}
