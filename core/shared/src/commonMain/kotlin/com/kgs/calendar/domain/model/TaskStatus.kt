package com.kgs.calendar.domain.model

/**
 * Typed view of an iCalendar VTODO STATUS. Known values match case-insensitively; anything else
 * is kept verbatim in [Other] so server values round-trip unchanged.
 */
sealed interface TaskStatus {
    val value: String

    data object NeedsAction : TaskStatus {
        override val value = "NEEDS-ACTION"
    }

    data object InProcess : TaskStatus {
        override val value = "IN-PROCESS"
    }

    data object Completed : TaskStatus {
        override val value = "COMPLETED"
    }

    data object Cancelled : TaskStatus {
        override val value = "CANCELLED"
    }

    data class Other(override val value: String) : TaskStatus

    /** COMPLETED and CANCELLED both close a task. */
    val closesTask: Boolean
        get() = this == Completed || this == Cancelled

    companion object {
        // A getter, not a stored list: referencing the objects from the interface's static initializer
        // can capture null for an object whose own initialization triggered it.
        private val known get() = listOf(NeedsAction, InProcess, Completed, Cancelled)

        fun from(raw: String?): TaskStatus? =
            raw?.let { value -> known.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: Other(value) }
    }
}
