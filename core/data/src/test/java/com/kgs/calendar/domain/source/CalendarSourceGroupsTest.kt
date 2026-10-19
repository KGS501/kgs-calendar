package com.kgs.calendar.domain.source

import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarSourceGroupsTest {
    private val local = account("local", "local://device", SourceType.Local, "Local calendar")
    private val nextcloud = account("primary", "https://cloud.example.com", SourceType.CalDav, "anna")
    private val schoolA = feed("readonly-a", "https://a.school.org/term.ics", "a.school.org")
    private val schoolB = feed("readonly-b", "webcals://b.school.org/exams.ics", "b.school.org")
    private val holidays = feed("readonly-h", "https://feeds.holidays.co.uk/uk.ics", "My holidays")
    private val shared = feed("readonly-s", "https://files.example.com/s/public.ics", "files.example.com")
    private val accounts = listOf(local, nextcloud, schoolA, schoolB, holidays, shared)
    private val collections = listOf(
        collection("local://cal", local),
        collection("/dav/personal/", nextcloud),
        collection("/dav/work/", nextcloud),
        collection("readonly-readonly-a", schoolA),
        collection("readonly-readonly-b", schoolB),
        collection("readonly-readonly-h", holidays),
        collection("readonly-readonly-s", shared),
    )

    @Test
    fun feedsOfOneDomainShareAGroupAndJoinTheCalDavAccountOfTheirDomain() {
        val groups = CalendarSourceGroups.group(accounts, collections)

        assertEquals(listOf("local", "account:primary", "domain:school.org", "domain:holidays.co.uk"), groups.map { it.key })
        val school = groups.single { it.key == "domain:school.org" }
        assertEquals(CalendarSourceGroupKind.ReadOnlyUrl, school.kind)
        assertEquals("school.org", school.name)
        assertEquals(listOf(schoolA.id, schoolB.id), school.accounts.map { it.id })
        val cloud = groups.single { it.key == "account:primary" }
        assertEquals(CalendarSourceGroupKind.CalDav, cloud.kind)
        assertEquals("anna", cloud.name)
        assertEquals(listOf("/dav/personal/", "/dav/work/", "readonly-readonly-s"), cloud.collections.map { it.href })
    }

    @Test
    fun aSingleFeedKeepsItsSourceName() {
        val holidayGroup = CalendarSourceGroups.group(accounts, collections).single { it.key == "domain:holidays.co.uk" }

        assertEquals("My holidays", holidayGroup.name)
        assertNull(CalendarSourceGroups.group(listOf(local), collections.take(1)).single().customName)
    }

    @Test
    fun userAssignmentsMoveCalendarsAndNamesRenameGroups() {
        val settings = CalendarGroupSettings(
            assignments = mapOf(
                "/dav/work/" to "custom:job",
                "readonly-readonly-a" to "custom:job",
                "readonly-readonly-h" to "account:primary",
                // Stale keys fall back to the automatic group.
                "readonly-readonly-b" to "account:gone",
            ),
            names = mapOf("custom:job" to "Job", "domain:school.org" to "School"),
        )

        val groups = CalendarSourceGroups.group(accounts, collections, settings)

        val job = groups.single { it.key == "custom:job" }
        assertEquals(CalendarSourceGroupKind.Custom, job.kind)
        assertEquals("Job", job.name)
        assertEquals(listOf("/dav/work/", "readonly-readonly-a"), job.collections.map { it.href })
        assertEquals(listOf(nextcloud.id, schoolA.id), job.accounts.map { it.id })
        assertEquals(listOf("readonly-readonly-b"), groups.single { it.key == "domain:school.org" }.collections.map { it.href })
        assertEquals("School", groups.single { it.key == "domain:school.org" }.name)
        assertEquals(
            listOf("/dav/personal/", "readonly-readonly-h", "readonly-readonly-s"),
            groups.single { it.key == "account:primary" }.collections.map { it.href },
        )
        // The holidays feed's own group is empty now but still lists its source.
        assertEquals(listOf(holidays.id), groups.single { it.key == "domain:holidays.co.uk" }.accounts.map { it.id })
    }

    @Test
    fun settingsSurviveEncoding() {
        val settings = CalendarGroupSettings(mapOf("a" to "custom:x"), mapOf("custom:x" to "Ä \"quoted\""))

        assertEquals(settings, CalendarGroupSettings.decode(settings.encode()))
        assertEquals(CalendarGroupSettings(), CalendarGroupSettings.decode("not json"))
    }

    @Test
    fun registrableDomainUsesThePublicSuffixList() {
        assertEquals("example.co.uk", CalendarSourceGroups.registrableDomain("https://a.b.example.co.uk/x.ics"))
        assertEquals("school.org", CalendarSourceGroups.registrableDomain("webcal://cal.school.org/feed"))
        assertEquals("192.168.1.5", CalendarSourceGroups.registrableDomain("http://192.168.1.5:8080/c.ics"))
    }

    private fun account(id: String, url: String, type: SourceType, name: String) = AccountEntity(
        id = id,
        serverUrl = url,
        username = if (type == SourceType.ReadOnlyUrl) "Read-only URL" else name,
        displayName = name,
        lastSyncAtMillis = null,
        sourceType = type,
    )

    private fun feed(id: String, url: String, name: String) = account(id, url, SourceType.ReadOnlyUrl, name)

    private fun collection(href: String, account: AccountEntity) = CollectionEntity(
        href = href,
        accountId = account.id,
        displayName = href,
        color = 0,
        supportsEvents = true,
        supportsTasks = false,
        syncToken = null,
        ctag = null,
        sourceType = account.sourceType,
    )
}
