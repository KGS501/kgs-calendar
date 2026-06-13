package com.kgs.calendar.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class TaskEditPayload(
    val title: String,
    val collectionHref: String?,
    val notes: String?,
    val location: String?,
    val locationMapVerified: Boolean?,
    val manualColor: Int?,
    val url: String?,
    val categories: String?,
    val startDate: LocalDate?,
    val startTime: LocalTime?,
    val startHasTime: Boolean,
    val dueDate: LocalDate?,
    val dueTime: LocalTime?,
    val dueHasTime: Boolean,
    val priority: Int?,
    val percentComplete: Int?,
    val isCompleted: Boolean,
    val recurrenceRule: String?,
    val parentUid: String? = null,
    /**
     * One of NEEDS-ACTION / IN-PROCESS / COMPLETED / CANCELLED, or null when the
     * editor wants the repository to derive it from [isCompleted].
     */
    val status: String? = null,
    val reminderMinutes: List<Int> = emptyList(),
)
