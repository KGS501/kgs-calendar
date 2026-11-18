package com.kgs.calendar.ui

import androidx.compose.runtime.Composable
import com.kgs.calendar.ui.res.stringResource
import com.kgs.calendar.ui.res.Res

@Composable
fun CreateFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCreateTask: () -> Unit,
    onCreateEvent: () -> Unit,
) = SharedCreateFabMenu(
    labels = CreateMenuLabels(stringResource(Res.string.task), stringResource(Res.string.event),
        stringResource(Res.string.close), stringResource(Res.string.create)),
    expanded = expanded,
    onExpandedChange = onExpandedChange,
    onCreateTask = onCreateTask,
    onCreateEvent = onCreateEvent,
)
