package com.kgs.calendar.domain.model

internal fun isMonthSurfaceTaskVisible(
    isCompleted: Boolean,
    status: String?,
): Boolean =
    !isCompleted &&
        !status.equals("COMPLETED", ignoreCase = true) &&
        !status.equals("CANCELLED", ignoreCase = true)
