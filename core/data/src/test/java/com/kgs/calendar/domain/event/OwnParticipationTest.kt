package com.kgs.calendar.domain.event

import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.EventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OwnParticipationTest {
    private val event = EventEntity(
        uid = "invite",
        collectionHref = "/cal/",
        resourceHref = "/cal/invite.ics",
        title = "Invite",
        description = null,
        location = null,
        startsAtMillis = 0,
        endsAtMillis = 3_600_000,
        allDay = false,
        recurrenceRule = null,
        isRecurring = false,
        color = 0,
    )

    @Test
    fun ownAddressesComeFromTheServerAndAnEmailLogin() {
        val account = AccountEntity(
            serverUrl = "https://cloud.example.com",
            username = "Anna@Example.com",
            displayName = null,
            lastSyncAtMillis = null,
            capabilitiesJson = """{"calendarUserAddresses":["mailto:anna.work@example.com","/remote.php/dav/principals/users/anna/"]}""",
        )

        assertEquals(setOf("anna@example.com", "anna.work@example.com"), account.ownCalendarAddresses())
        assertEquals(emptySet<String>(), account.copy(username = "anna", capabilitiesJson = null).ownCalendarAddresses())
    }

    @Test
    fun declinedAndCancelledEventsDoNotRemind() {
        val attendees = """[{"email":"anna@example.com","partstat":"DECLINED"},{"email":"bob@example.com","partstat":"ACCEPTED"}]"""
        val invite = event.copy(attendeesJson = attendees)

        assertTrue(invite.isDeclinedBy(setOf("anna@example.com")))
        assertFalse(invite.remindsUser(setOf("anna@example.com")))
        assertTrue(invite.remindsUser(setOf("bob@example.com")))
        assertTrue(invite.remindsUser(emptySet()))
        assertFalse(event.copy(status = "CANCELLED").remindsUser(emptySet()))
    }

    @Test
    fun invitationsFromOthersAreRecognised() {
        val invited = event.copy(
            organizerJson = """{"email":"boss@example.com"}""",
            attendeesJson = """[{"email":"anna@example.com","partstat":"NEEDS-ACTION"}]""",
        )

        assertTrue(invited.isInvitationFromOthers(setOf("anna@example.com")))
        assertFalse(invited.isInvitationFromOthers(setOf("boss@example.com")))
        assertFalse(invited.isInvitationFromOthers(emptySet()))
        assertFalse(event.copy(organizerJson = """{"email":"boss@example.com"}""").isInvitationFromOthers(setOf("anna@example.com")))
    }
}
