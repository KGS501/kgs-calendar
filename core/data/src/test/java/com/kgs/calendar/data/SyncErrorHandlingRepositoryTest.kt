package com.kgs.calendar.data

import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.remote.HttpStatusException
import com.kgs.calendar.domain.model.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncErrorHandlingRepositoryTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val server = harness.server

    @After
    fun tearDown() = harness.close()

    @Test
    fun unauthorizedAccountIsMarkedAsErrorAndSyncThrowsWhenNothingSucceeded() = runTest {
        repository.ensureLocalCalendar()
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        val lastSync = harness.account(AccountEntity.PRIMARY_ID)!!.lastSyncAtMillis
        server.rejectCredentials = true

        // The local calendar never counts as a successful account, so the CalDAV failure is rethrown.
        val error = expectFailure<IllegalStateException> { repository.syncNow() }

        assertTrue(error.message!!.startsWith("Source \"alice\": "))
        assertTrue(error.message!!.endsWith("HTTP 401"))
        assertEquals(401, (error.cause as HttpStatusException).statusCode)
        val account = harness.account(AccountEntity.PRIMARY_ID)!!
        assertEquals(SyncState.Error, account.syncState)
        assertEquals(error.message, account.syncError)
        assertEquals(lastSync, account.lastSyncAtMillis)
        assertEquals("Kickoff", harness.event(eventHref)!!.title)
        assertEquals(SyncState.Idle, harness.account("local")!!.syncState)
    }

    @Test
    fun syncNowDoesNotThrowWhenAnotherAccountSucceeds() = runTest {
        harness.addSyncedCalDavAccount()
        server.setFeed("/feeds/public.ics", SampleIcs.event("public-1", "Public event"))
        val readOnly = repository.addReadOnlyCalendar(server.url("/feeds/public.ics"), "Public")
        server.rejectCredentials = true

        repository.syncNow()

        val calDav = harness.account(AccountEntity.PRIMARY_ID)!!
        assertEquals(SyncState.Error, calDav.syncState)
        assertTrue(calDav.syncError!!.endsWith("HTTP 401"))
        val feed = harness.account(readOnly.id)!!
        assertEquals(SyncState.Idle, feed.syncState)
        assertNull(feed.syncError)
    }

    @Test
    fun recoveredAccountClearsErrorOnNextSuccessfulSync() = runTest {
        harness.addSyncedCalDavAccount()
        server.rejectCredentials = true
        runCatching { repository.syncNow() }
        server.rejectCredentials = false

        repository.syncNow()

        val account = harness.account(AccountEntity.PRIMARY_ID)!!
        assertEquals(SyncState.Idle, account.syncState)
        assertNull(account.syncError)
    }
}
