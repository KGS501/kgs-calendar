package com.kgs.calendar.ui

import androidx.compose.runtime.Composable
import com.kgs.calendar.res.stringResource
import com.kgs.calendar.res.SharedR as R

@Composable
internal fun CreateFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCreateTask: () -> Unit,
    onCreateEvent: () -> Unit,
) = SharedCreateFabMenu(
    labels = CreateMenuLabels(stringResource(R.string.task), stringResource(R.string.event),
        stringResource(R.string.close), stringResource(R.string.create)),
    expanded = expanded,
    onExpandedChange = onExpandedChange,
    onCreateTask = onCreateTask,
    onCreateEvent = onCreateEvent,
)
