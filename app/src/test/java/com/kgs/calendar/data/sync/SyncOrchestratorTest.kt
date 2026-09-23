package com.kgs.calendar.data.sync

import com.kgs.calendar.data.RepositoryHarness
import com.kgs.calendar.data.expectFailure
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.domain.model.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncOrchestratorTest {
    private val harness = RepositoryHarness()
    private val database = harness.database

    @After
    fun tearDown() = harness.close()

    /** Claims the accounts whose id is in [ids]; [failures] maps an account id to the error its sync throws. */
    private class FakeEngine(
        private val ids: Set<String>,
        private val failures: Map<String, Throwable> = emptyMap(),
        private val skipped: Set<String> = emptySet(),
    ) : CalendarSourceSyncEngine {
        val synced = mutableListOf<Pair<String, SourceSyncOptions>>()

        override fun handles(account: AccountEntity): Boolean = account.id in ids

        override suspend fun sync(account: AccountEntity, options: SourceSyncOptions): Boolean {
            synced += account.id to options
            failures[account.id]?.let { throw it }
            return account.id !in skipped
        }
    }

    private fun orchestrator(vararg engines: CalendarSourceSyncEngine) = SyncOrchestrator(
        database = database,
        repairs = harness.components.repairs,
        uploader = harness.components.uploader,
        engines = engines.toList(),
    )

    private suspend fun addAccount(id: String, displayName: String, lastSyncAtMillis: Long? = null) {
        database.accountDao().upsert(
            AccountEntity(
                id = id,
                serverUrl = "https://$id.example.test",
                username = id,
                displayName = displayName,
                lastSyncAtMillis = lastSyncAtMillis,
            ),
        )
    }

    @Test
    fun failingAccountIsMarkedButSyncSucceedsWhenAnotherAccountSucceeds() = runTest {
        addAccount("a", "Alpha", lastSyncAtMillis = 42L)
        addAccount("b", "Beta")
        val engine = FakeEngine(setOf("a", "b"), failures = mapOf("a" to IllegalStateException("boom")))

        orchestrator(engine).syncNow()

        assertEquals(listOf("a", "b"), engine.synced.map { it.first })
        val failed = harness.account("a")!!
        assertEquals(SyncState.Error, failed.syncState)
        assertEquals("Source \"Alpha\": boom", failed.syncError)
        assertEquals(42L, failed.lastSyncAtMillis)
        assertEquals(SyncState.Idle, harness.account("b")!!.syncState)
        assertNull(harness.account("b")!!.syncError)
    }

    @Test
    fun whenNoAccountSucceedsTheFirstFailureIsThrown() = runTest {
        addAccount("a", "Alpha")
        addAccount("b", "Beta")
        val firstCause = IllegalStateException("first")
        val engine = FakeEngine(setOf("a", "b"), failures = mapOf("a" to firstCause, "b" to IllegalStateException("second")))

        val error = expectFailure<IllegalStateException> { orchestrator(engine).syncNow() }

        assertEquals("Source \"Alpha\": first", error.message)
        assertSame(firstCause, error.cause)
        assertEquals("Source \"Beta\": second", harness.account("b")!!.syncError)
    }

    @Test
    fun alreadyDescribedErrorsAreNotPrefixedTwice() = runTest {
        addAccount("a", "Alpha")
        val engine = FakeEngine(setOf("a"), failures = mapOf("a" to IllegalStateException("Source \"Alpha\": offline")))

        val error = expectFailure<IllegalStateException> { orchestrator(engine).syncNow() }

        assertEquals("Source \"Alpha\": offline", error.message)
        assertEquals("Source \"Alpha\": offline", harness.account("a")!!.syncError)
    }

    @Test
    fun localSkippedAndUnclaimedAccountsDoNotCountAsSuccess() = runTest {
        harness.repository.ensureLocalCalendar()
        addAccount("skipped", "Skipped")
        addAccount("unclaimed", "Unclaimed")
        addAccount("failing", "Failing")
        val engine = FakeEngine(
            ids = setOf("local", "skipped", "failing"),
            failures = mapOf("failing" to IllegalStateException("down")),
            skipped = setOf("skipped"),
        )

        val error = expectFailure<IllegalStateException> { orchestrator(engine).syncNow() }

        assertEquals("Source \"Failing\": down", error.message)
        assertEquals(setOf("skipped", "failing"), engine.synced.map { it.first }.toSet())
        assertEquals(SyncState.Idle, harness.account("skipped")!!.syncState)
        assertEquals(SyncState.Idle, harness.account("unclaimed")!!.syncState)
    }

    @Test
    fun withoutAccountsSyncSucceeds() = runTest {
        orchestrator(FakeEngine(emptySet())).syncNow()
    }

    @Test
    fun firstClaimingEngineSyncsTheAccountWithTheRequestedOptions() = runTest {
        addAccount("a", "Alpha")
        val first = FakeEngine(setOf("a"))
        val second = FakeEngine(setOf("a"))

        orchestrator(first, second).syncNow(includeDisabledProviderCalendars = true, forceFullCalDavRefresh = true)

        assertEquals(listOf("a" to SourceSyncOptions(includeDisabledProviderCalendars = true, forceFullCalDavRefresh = true)), first.synced)
        assertTrue(second.synced.isEmpty())
    }
}
