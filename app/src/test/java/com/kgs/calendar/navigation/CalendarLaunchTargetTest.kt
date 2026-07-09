package com.kgs.calendar.navigation

import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.model.CalendarViewMode
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarLaunchTargetTest {
    @Test
    fun launchTargetRoundTripsExactTaskOccurrence() {
        val target = CalendarLaunchTarget(
            date = LocalDate.of(2026, 7, 9),
            viewMode = CalendarViewMode.Day,
            action = CalendarLaunchAction.OpenOccurrence,
            occurrence = CalendarOccurrenceId.Task("tasks/42.ics", 1_700_000_000_000),
        )

        assertEquals(target, CalendarLaunchTarget.decode(target.encode()))
    }

    @Test
    fun launchTargetPreservesNullableFields() {
        val target = CalendarLaunchTarget(action = CalendarLaunchAction.OpenDate)

        assertEquals(target, CalendarLaunchTarget.decode(target.encode()))
    }

    @Test
    fun unknownActionIsRejected() {
        val encoded = JSONObject()
            .put("version", 1)
            .put("action", "unsupported")
            .toString()

        assertNull(CalendarLaunchTarget.decode(encoded))
    }

    @Test
    fun unknownOccurrenceKindIsRejected() {
        val encoded = JSONObject()
            .put("version", 1)
            .put("action", "open_occurrence")
            .put(
                "occurrence",
                JSONObject()
                    .put("kind", "journal")
                    .put("resourceHref", "journals/42.ics")
                    .put("recurrenceIdMillis", 42L),
            )
            .toString()

        assertNull(CalendarLaunchTarget.decode(encoded))
    }
}
