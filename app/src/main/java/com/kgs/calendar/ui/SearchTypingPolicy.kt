package com.kgs.calendar.ui

internal const val SEARCH_TYPING_DEBOUNCE_MILLIS = 250L

internal fun searchTypingDelayMillis(query: String): Long =
    if (query.isBlank()) 0L else SEARCH_TYPING_DEBOUNCE_MILLIS
