package com.kgs.calendar.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgs.calendar.AppGraph

@Composable
fun rememberCalendarViewModel(graph: AppGraph): CalendarViewModel =
    viewModel(factory = CalendarViewModelFactory(graph))
