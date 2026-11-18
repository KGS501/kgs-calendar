package com.kgs.calendar.domain.model

const val MIN_MULTI_DAY_COUNT = 2
const val DEFAULT_MULTI_DAY_COUNT = 3
const val MAX_MULTI_DAY_COUNT = 7

fun Int.coerceMultiDayCount(): Int = coerceIn(MIN_MULTI_DAY_COUNT, MAX_MULTI_DAY_COUNT)
