package com.kgs.calendar.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParserReparseTest {
    private val calls = mutableListOf<String>()
    private var recorded: Int? = null

    private suspend fun run(storedVersion: Int, failure: Throwable? = null) = reparseCachedIcalIfNeeded(
        storedVersion = storedVersion,
        reparseTaskResources = {
            calls += "tasks"
            failure?.let { throw it }
        },
        reparseAllResources = {
            calls += "all"
            failure?.let { throw it }
        },
        recordVersion = { recorded = it },
    )

    @Test
    fun oldVersionsReparseEverythingAndRecordTheCurrentVersion() = runTest {
        run(storedVersion = 0)

        assertEquals(listOf("all"), calls)
        assertEquals(PARSER_REPARSE_VERSION, recorded)
    }

    @Test
    fun versionsFromTheFullReparseOnOnlyRedoTasks() = runTest {
        run(storedVersion = FULL_REPARSE_VERSION)

        assertEquals(listOf("tasks"), calls)
        assertEquals(PARSER_REPARSE_VERSION, recorded)
    }

    @Test
    fun currentVersionSkipsTheReparse() = runTest {
        run(storedVersion = PARSER_REPARSE_VERSION)

        assertEquals(emptyList<String>(), calls)
        assertNull(recorded)
    }

    @Test
    fun failedReparseDoesNotRecordTheVersionSoItRunsAgain() = runTest {
        run(storedVersion = 0, failure = IllegalStateException("database is locked"))

        assertEquals(listOf("all"), calls)
        assertNull(recorded)

        run(storedVersion = 0)

        assertEquals(listOf("all", "all"), calls)
        assertEquals(PARSER_REPARSE_VERSION, recorded)
    }
}
