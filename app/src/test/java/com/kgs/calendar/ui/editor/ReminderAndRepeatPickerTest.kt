package com.kgs.calendar.ui.editor

import com.kgs.calendar.data.settings.ReminderPresets
import com.kgs.calendar.domain.model.REMINDER_AT_END
import com.kgs.calendar.ui.RecurrenceDraft
import com.kgs.calendar.ui.initialRecurrenceDraft
import com.kgs.calendar.ui.ReminderPick
import com.kgs.calendar.ui.initialReminderState
import com.kgs.calendar.ui.labels.RecurrenceOption
import com.kgs.calendar.ui.labels.ReminderUnit
import com.kgs.calendar.ui.steppedReminderAmount
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderAndRepeatPickerTest {
    @Test
    fun minutesStepByOneOnATapAndToTheNextFiveWhenHeld() {
        assertEquals(16, steppedReminderAmount(ReminderUnit.Minutes, 15, up = true, held = false))
        assertEquals(20, steppedReminderAmount(ReminderUnit.Minutes, 17, up = true, held = true))
        assertEquals(20, steppedReminderAmount(ReminderUnit.Minutes, 15, up = true, held = true))
        assertEquals(15, steppedReminderAmount(ReminderUnit.Minutes, 17, up = false, held = true))
        assertEquals(10, steppedReminderAmount(ReminderUnit.Minutes, 15, up = false, held = true))
        // Amounts stay within their unit's range.
        assertEquals(1, steppedReminderAmount(ReminderUnit.Minutes, 3, up = false, held = true))
        assertEquals(4, steppedReminderAmount(ReminderUnit.Weeks, 4, up = true, held = false))
        assertEquals(3, steppedReminderAmount(ReminderUnit.Hours, 2, up = true, held = true))
    }

    @Test
    fun anExistingReminderOpensOnTheCardOfItsLargestWholeUnit() {
        val presets = ReminderPresets()
        assertEquals(ReminderPick.Before(ReminderUnit.Hours) to presets.copy(hours = 3), initialReminderState(180, presets))
        assertEquals(ReminderPick.Before(ReminderUnit.Weeks) to presets.copy(weeks = 2), initialReminderState(2 * 10080, presets))
        assertEquals(ReminderPick.Before(ReminderUnit.Days) to presets.copy(days = 3), initialReminderState(3 * 1440, presets))
        assertEquals(ReminderPick.Before(ReminderUnit.Minutes) to presets.copy(minutes = 10), initialReminderState(10, presets))
        assertEquals(ReminderPick.Moment(REMINDER_AT_END) to presets, initialReminderState(REMINDER_AT_END, presets))
        // A new reminder starts on the minutes card with the presets.
        assertEquals(ReminderPick.Before(ReminderUnit.Minutes) to presets, initialReminderState(null, presets))
    }

    @Test
    fun theRuleKeepsPartsThePopupDoesNotEdit() {
        val draft = RecurrenceDraft.fromRule("FREQ=WEEKLY;BYDAY=MO,WE;COUNT=5")
        assertEquals(listOf("BYDAY=MO,WE"), draft.extraParts)
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;COUNT=5", draft.copy(interval = 2).toRule())
        // Another unit drops the weekdays, which would not fit it.
        assertEquals("FREQ=MONTHLY;COUNT=5", draft.withUnit(RecurrenceOption.Monthly).toRule())
    }

    @Test
    fun theRepeatPopupStartsWeeklyAndNeverEndingForAnItemWithoutARepeat() {
        assertEquals("FREQ=WEEKLY", initialRecurrenceDraft("").toRule())
        assertEquals("FREQ=MONTHLY;INTERVAL=3", initialRecurrenceDraft("FREQ=MONTHLY;INTERVAL=3").toRule())
    }
}
