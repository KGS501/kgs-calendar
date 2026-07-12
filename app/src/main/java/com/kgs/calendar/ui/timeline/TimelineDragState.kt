package com.kgs.calendar.ui.timeline

import java.time.LocalDate

internal sealed interface TimelineDropTarget {
    val date: LocalDate

    data class Timed(
        override val date: LocalDate,
        val startMinute: Int,
        val endMinute: Int,
    ) : TimelineDropTarget {
        init {
            require(endMinute > startMinute) { "Timed target must have a positive duration" }
        }
    }

    data class AllDay(
        override val date: LocalDate,
        val lane: Int,
    ) : TimelineDropTarget {
        init {
            require(lane >= 0) { "All-day lane must not be negative" }
        }
    }
}

internal data class AllDayReservation(
    val date: LocalDate,
    val lane: Int,
) {
    init {
        require(lane >= 0) { "All-day lane must not be negative" }
    }
}

internal data class TimelineDragSession(
    val target: TimelineDropTarget,
    val reservation: AllDayReservation? = target.toReservation(),
)

internal enum class TimelineDraggedItemKind { Event, Task }

internal data class TimelineDraggedItem(
    val kind: TimelineDraggedItemKind,
    val resourceHref: String,
    val occurrenceMillis: Long,
    val title: String,
    val colorArgb: Int,
    val priority: Int?,
    val completed: Boolean,
    val sourceDate: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
) {
    init {
        require(endMinute > startMinute) { "Dragged item must have a positive duration" }
    }
}

internal data class TimelineDragPoint(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && y.isFinite()) { "Drag point must be finite" }
    }
}

internal data class TimelineDragBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    init {
        require(listOf(left, top, width, height).all(Float::isFinite)) { "Drag bounds must be finite" }
        require(width > 0f && height > 0f) { "Drag bounds must be positive" }
    }
}

internal data class TimelineTimedDragStart(
    val item: TimelineDraggedItem,
    val pointerInRoot: TimelineDragPoint,
    val cardBoundsInRoot: TimelineDragBounds,
)

internal interface TimelineTimedDragReporter {
    val usesRootOverlay: Boolean
    fun start(start: TimelineTimedDragStart)
    fun update(pointerInRoot: TimelineDragPoint)
    fun end()
    fun cancel()
}

internal object NoOpTimelineTimedDragReporter : TimelineTimedDragReporter {
    override val usesRootOverlay: Boolean = false
    override fun start(start: TimelineTimedDragStart) = Unit
    override fun update(pointerInRoot: TimelineDragPoint) = Unit
    override fun end() = Unit
    override fun cancel() = Unit
}

internal class TimelineDragReducer(
    private val entryMarginPx: Float,
    private val exitMarginPx: Float,
) {
    init {
        require(entryMarginPx.isFinite() && entryMarginPx >= 0f) {
            "Entry margin must be finite and non-negative"
        }
        require(exitMarginPx.isFinite() && exitMarginPx >= 0f) {
            "Exit margin must be finite and non-negative"
        }
    }

    fun update(
        pointerY: Float,
        boundaryY: Float,
        timedTarget: TimelineDropTarget.Timed,
        allDayTarget: TimelineDropTarget.AllDay,
        previous: TimelineDragSession,
    ): TimelineDragSession {
        require(pointerY.isFinite() && boundaryY.isFinite()) {
            "Drag coordinates must be finite"
        }
        val allDay = if (previous.target is TimelineDropTarget.AllDay) {
            pointerY <= boundaryY + exitMarginPx
        } else {
            pointerY <= boundaryY - entryMarginPx
        }
        return if (allDay) {
            TimelineDragSession(
                target = allDayTarget,
                reservation = allDayTarget.toReservation(),
            )
        } else {
            TimelineDragSession(target = timedTarget, reservation = null)
        }
    }
}

private fun TimelineDropTarget.toReservation(): AllDayReservation? =
    (this as? TimelineDropTarget.AllDay)?.let { AllDayReservation(it.date, it.lane) }
