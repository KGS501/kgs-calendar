package com.kgs.calendar.ui.editor

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class EditorSchedulePreview(
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val allDay: Boolean = false,
)

data class EditorScheduleState(
    val startDateText: String,
    val endDateText: String,
    val startTimeText: String,
    val endTimeText: String,
    val hasStartDate: Boolean,
    val hasEndDate: Boolean,
    val hasStartTime: Boolean,
    val hasEndTime: Boolean,
    val allDay: Boolean,
    val lastValidPreview: EditorSchedulePreview?,
) {
    fun applyTimelineChange(preview: EditorSchedulePreview): EditorScheduleState = copy(
        startDateText = preview.date.toString(),
        endDateText = preview.date.toString(),
        startTimeText = preview.start.format(TIME_FORMATTER),
        endTimeText = preview.end.format(TIME_FORMATTER),
        hasStartDate = true,
        hasEndDate = true,
        hasStartTime = !preview.allDay,
        hasEndTime = !preview.allDay,
        allDay = preview.allDay,
        lastValidPreview = preview,
    )

    fun recalculatePreview(): EditorScheduleState {
        val startDate = parseDateIfEnabled(hasStartDate, startDateText) ?: if (hasStartDate) return this else null
        val endDate = parseDateIfEnabled(hasEndDate, endDateText) ?: if (hasEndDate) return this else null
        val startTime = parseTimeIfEnabled(hasStartTime && !allDay, startTimeText)
            ?: if (hasStartTime && !allDay) return this else null
        val endTime = parseTimeIfEnabled(hasEndTime && !allDay, endTimeText)
            ?: if (hasEndTime && !allDay) return this else null

        if (startDate != null && endDate != null) {
            val invalidRange = if (allDay || (startTime == null && endTime == null)) {
                endDate.isBefore(startDate)
            } else {
                val effectiveStart = startTime ?: LocalTime.MIDNIGHT
                val effectiveEnd = endTime ?: LocalTime.MAX
                !endDate.atTime(effectiveEnd).isAfter(startDate.atTime(effectiveStart))
            }
            if (invalidRange) return this
        }

        val preview = when {
            allDay && (startDate != null || endDate != null) -> EditorSchedulePreview(
                date = startDate ?: endDate!!,
                start = LocalTime.MIDNIGHT,
                end = LocalTime.of(23, 59),
                allDay = true,
            )
            startDate != null && startTime != null -> EditorSchedulePreview(
                date = startDate,
                start = startTime,
                end = if (endDate == startDate && endTime != null) {
                    endTime
                } else {
                    startTime.plusMinutes(30).takeIf { it.isAfter(startTime) } ?: LocalTime.MAX
                },
            )
            endDate != null && endTime != null -> EditorSchedulePreview(
                date = endDate,
                start = endTime.minusMinutes(30).takeIf { it.isBefore(endTime) } ?: LocalTime.MIDNIGHT,
                end = endTime,
            )
            else -> null
        }
        return copy(lastValidPreview = preview)
    }

    companion object {
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

        fun fromPreview(preview: EditorSchedulePreview): EditorScheduleState = EditorScheduleState(
            startDateText = preview.date.toString(),
            endDateText = preview.date.toString(),
            startTimeText = preview.start.format(TIME_FORMATTER),
            endTimeText = preview.end.format(TIME_FORMATTER),
            hasStartDate = true,
            hasEndDate = true,
            hasStartTime = !preview.allDay,
            hasEndTime = !preview.allDay,
            allDay = preview.allDay,
            lastValidPreview = preview,
        )
    }
}

private fun parseDateIfEnabled(enabled: Boolean, value: String): LocalDate? =
    if (!enabled) null else runCatching { LocalDate.parse(value) }.getOrNull()

private fun parseTimeIfEnabled(enabled: Boolean, value: String): LocalTime? =
    if (!enabled) null else runCatching { LocalTime.parse(value) }.getOrNull()
