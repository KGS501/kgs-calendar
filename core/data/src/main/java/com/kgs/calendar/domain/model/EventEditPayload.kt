package com.kgs.calendar.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class EventEditPayload(
    val title: String,
    val collectionHref: String?,
    val date: LocalDate,
    val endDate: LocalDate? = null,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val allDay: Boolean,
    val description: String?,
    val location: String?,
    val locationMapVerified: Boolean?,
    val manualColor: Int?,
    val recurrenceRule: String?,
    val reminderMinutes: List<Int> = emptyList(),
    val status: String? = null,
    val classification: String? = null,
    val transparency: String? = null,
    val categories: String? = null,
    val organizerJson: String? = null,
    val attendeesJson: String? = null,
)
