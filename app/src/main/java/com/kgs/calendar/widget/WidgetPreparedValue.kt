package com.kgs.calendar.widget

internal suspend fun <T> preparedWidgetValue(
    prepared: T?,
    load: suspend () -> T,
): T = prepared ?: load()

internal data class WidgetTransitionSnapshots<T>(
    val before: T?,
    val target: T,
)

internal suspend fun <T> loadSubtaskTransitionSnapshots(
    cachedBefore: T?,
    loadTarget: suspend () -> T,
): WidgetTransitionSnapshots<T> = WidgetTransitionSnapshots(
    before = cachedBefore,
    target = loadTarget(),
)
