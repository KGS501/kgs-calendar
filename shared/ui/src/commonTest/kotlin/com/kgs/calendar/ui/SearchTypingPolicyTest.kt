package com.kgs.calendar.ui

import kotlin.test.assertEquals
import kotlin.test.Test

class SearchTypingPolicyTest {
    @Test
    fun nonBlankQueriesWaitForTypingToSettle() {
        assertEquals(SEARCH_TYPING_DEBOUNCE_MILLIS, searchTypingDelayMillis("cal"))
    }

    @Test
    fun clearingSearchIsImmediate() {
        assertEquals(0L, searchTypingDelayMillis(""))
        assertEquals(0L, searchTypingDelayMillis("   "))
    }
}
