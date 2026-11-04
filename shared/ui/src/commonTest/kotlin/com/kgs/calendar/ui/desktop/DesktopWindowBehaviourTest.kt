package com.kgs.calendar.ui.desktop

import com.kgs.calendar.ui.platform.DesktopLoginStart
import com.kgs.calendar.ui.platform.DesktopWindowBehaviour
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowBehaviourTest {
    private class FakeLoginStart(var enabled: Boolean = false, val refuses: Boolean = false) : DesktopLoginStart {
        override fun isEnabled() = enabled
        override fun setEnabled(value: Boolean) {
            if (refuses) error("refused")
            enabled = value
        }
    }

    @Test fun startsFromWhatTheSystemHasRecorded() {
        assertTrue(DesktopWindowBehaviour(loginStart = FakeLoginStart(enabled = true)).openAtLogin)
        assertFalse(DesktopWindowBehaviour(loginStart = FakeLoginStart(enabled = false)).openAtLogin)
    }

    @Test fun togglingChangesTheSystemSetting() {
        val system = FakeLoginStart()
        val behaviour = DesktopWindowBehaviour(loginStart = system)
        behaviour.updateOpenAtLogin(true)
        assertTrue(system.enabled)
        assertTrue(behaviour.openAtLogin)
        behaviour.updateOpenAtLogin(false)
        assertFalse(system.enabled)
        assertFalse(behaviour.openAtLogin)
    }

    @Test fun aRefusedChangeLeavesTheSwitchShowingTheTruth() {
        val behaviour = DesktopWindowBehaviour(loginStart = FakeLoginStart(refuses = true))
        behaviour.updateOpenAtLogin(true)
        assertFalse(behaviour.openAtLogin)
    }

    @Test fun aChangeMadeInTheSystemShowsAfterARefresh() {
        val system = FakeLoginStart(enabled = true)
        val behaviour = DesktopWindowBehaviour(loginStart = system)
        system.enabled = false
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
