package com.kgs.calendar.domain.model

/**
 * Typed views of iCalendar VEVENT STATUS, CLASS and TRANSP. Known values match case-insensitively;
 * anything else is kept verbatim in `Other` so server values round-trip unchanged.
 */
sealed interface EventStatus {
    val value: String

    data object Confirmed : EventStatus {
        override val value = "CONFIRMED"
    }

    data object Tentative : EventStatus {
        override val value = "TENTATIVE"
    }

    data object Cancelled : EventStatus {
        override val value = "CANCELLED"
    }

    data class Other(override val value: String) : EventStatus

    companion object {
        private val known = listOf(Confirmed, Tentative, Cancelled)

        fun from(raw: String?): EventStatus? =
            raw?.let { value -> known.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: Other(value) }
    }
}

sealed interface EventClassification {
    val value: String

    data object Public : EventClassification {
        override val value = "PUBLIC"
    }

    data object Private : EventClassification {
        override val value = "PRIVATE"
    }

    data object Confidential : EventClassification {
        override val value = "CONFIDENTIAL"
    }

    data class Other(override val value: String) : EventClassification

    companion object {
        private val known = listOf(Public, Private, Confidential)

        fun from(raw: String?): EventClassification? =
            raw?.let { value -> known.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: Other(value) }
    }
}

sealed interface EventTransparency {
    val value: String

    data object Opaque : EventTransparency {
        override val value = "OPAQUE"
    }

    data object Transparent : EventTransparency {
        override val value = "TRANSPARENT"
    }

    data class Other(override val value: String) : EventTransparency

    companion object {
        private val known = listOf(Opaque, Transparent)

        fun from(raw: String?): EventTransparency? =
            raw?.let { value -> known.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: Other(value) }
    }
}
