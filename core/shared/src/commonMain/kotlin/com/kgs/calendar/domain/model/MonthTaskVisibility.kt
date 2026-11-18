package com.kgs.calendar.domain.model

import com.kgs.calendar.domain.task.isOpenTask

fun isMonthSurfaceTaskVisible(
    isCompleted: Boolean,
    status: String?,
): Boolean = isOpenTask(isCompleted, status)
