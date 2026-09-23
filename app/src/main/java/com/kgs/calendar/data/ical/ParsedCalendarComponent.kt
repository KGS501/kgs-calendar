package com.kgs.calendar.data.ical

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.ComponentType

data class ParsedCalendarComponent(
    val uid: String,
    val componentType: ComponentType,
    val event: EventEntity?,
    val task: TaskEntity?,
)
