package com.kgs.calendar.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsExternalLinksTest {
    @Test
    fun sponsorProjectUrlTargetsKgs501GitHubSponsorsPage() {
        assertEquals("https://github.com/sponsors/KGS501", SponsorProjectUrl)
    }
}
