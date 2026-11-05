package com.kgs.calendar.ui.desktop

import com.kgs.calendar.ui.platform.DesktopLoginStart
import com.kgs.calendar.ui.platform.DesktopWindowBehaviour
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowBehaviourTest {
    private class FakeLoginStart(var on: Boolean = false, val refuses: Boolean = false) : DesktopLoginStart {
        override fun isEnabled() = on
        override fun setEnabled(value: Boolean) {
            if (refuses) error("refused")
            on = value
        }
    }

    @Test fun startsFromWhatTheSystemHasRecorded() {
        assertTrue(DesktopWindowBehaviour(loginStart = FakeLoginStart(on = true)).openAtLogin)
        assertFalse(DesktopWindowBehaviour(loginStart = FakeLoginStart(on = false)).openAtLogin)
    }

    @Test fun togglingChangesTheSystemSetting() {
        val system = FakeLoginStart()
        val behaviour = DesktopWindowBehaviour(loginStart = system)
        behaviour.updateOpenAtLogin(true)
        assertTrue(system.on)
        assertTrue(behaviour.openAtLogin)
        behaviour.updateOpenAtLogin(false)
        assertFalse(system.on)
        assertFalse(behaviour.openAtLogin)
    }

    @Test fun aRefusedChangeLeavesTheSwitchShowingTheTruth() {
        val behaviour = DesktopWindowBehaviour(loginStart = FakeLoginStart(refuses = true))
        behaviour.updateOpenAtLogin(true)
        assertFalse(behaviour.openAtLogin)
    }

    @Test fun aChangeMadeInTheSystemShowsAfterARefresh() {
        val system = FakeLoginStart(on = true)
        val behaviour = DesktopWindowBehaviour(loginStart = system)
        system.on = false
        assertTrue(behaviour.openAtLogin)
        behaviour.refreshOpenAtLogin()
        assertFalse(behaviour.openAtLogin)
    }

    @Test fun withoutASystemSettingThereIsNothingToOffer() {
        val behaviour = DesktopWindowBehaviour()
        assertFalse(behaviour.canOpenAtLogin)
        behaviour.updateOpenAtLogin(true)
        assertFalse(behaviour.openAtLogin)
    }
}
