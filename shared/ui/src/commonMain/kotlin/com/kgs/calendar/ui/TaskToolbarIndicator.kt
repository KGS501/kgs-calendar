package com.kgs.calendar.ui

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.task.isInactive
import com.kgs.calendar.ui.model.isOverdueTask
import com.kgs.calendar.ui.model.taskDate
import kotlinx.datetime.LocalDate
import com.kgs.calendar.time.ZoneId

fun hasTaskToolbarAttention(
    tasks: Iterable<TaskEntity>,
    today: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean = tasks.any { task ->
    !task.isInactive() &&
        (
            task.taskDate() == today ||
                task.isOverdueTask(today = today, zoneId = zoneId)
        )
}
