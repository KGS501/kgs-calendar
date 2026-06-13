package com.kgs.calendar.data.ical

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity

data class ParsedCalendarComponent(
    val uid: String,
    val componentType: String,
    val event: EventEntity?,
    val task: TaskEntity?,
)
