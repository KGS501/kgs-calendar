package com.kgs.calendar.ui

import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.ui.model.isOverdueTask
import com.kgs.calendar.ui.model.taskDate
import java.time.LocalDate
import java.time.ZoneId

internal fun hasTaskToolbarAttention(
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
