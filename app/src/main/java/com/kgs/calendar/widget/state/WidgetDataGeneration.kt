package com.kgs.calendar.widget.state

import java.util.concurrent.atomic.AtomicLong

internal object WidgetDataGeneration {
    private val generation = AtomicLong()

    fun current(): Long = generation.get()

    fun increment(): Long = generation.incrementAndGet()
}
