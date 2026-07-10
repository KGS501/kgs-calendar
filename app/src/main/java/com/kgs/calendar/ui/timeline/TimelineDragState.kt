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
