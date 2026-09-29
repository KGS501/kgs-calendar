package com.kgs.calendar.sync

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.MainActivity
import com.kgs.calendar.data.FakeCalDavServer
import com.kgs.calendar.data.SampleIcs
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the real Application outbox observer, WorkManager and foreground lifecycle against local HTTP. */
@RunWith(AndroidJUnit4::class)
class AutomaticSyncInstrumentedTest {
    @Test fun intervalPreferenceAutomaticallyUpdatesTheScheduledJob() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<KgsCalendarApplication>()
        val store = app.appGraph.settingsStore
        val original = store.syncIntervalMinutes.first()
        try {
            store.setSyncIntervalMinutes(60)
            withTimeout(30_000) {
                while (WorkManager.getInstance(app).getWorkInfosForUniqueWork("kgs_periodic_sync").get()
                        .singleOrNull()?.periodicityInfo?.repeatIntervalMillis != 3_600_000L) delay(250)
            }
        } finally {
            store.setSyncIntervalMinutes(original)
        }
    }

    @Test fun uploadsRetryAndForegroundDownloadsRunWithoutManualSync() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<KgsCalendarApplication>()
        val graph = app.appGraph
        val server = FakeCalDavServer(username = "sync-regression")
        server.start()
        var accountId: String? = null
        try {
            val href = server.putRemote(server.tasksHref, "task.ics", SampleIcs.task("auto-sync-regression", "Before"))
            val account = graph.repository.saveManualAccount(server.serverUrl, server.username, server.password)
            accountId = account.id
            graph.repository.syncNow()
            server.clearRequests()
            server.respondNext("PUT", href) { MockResponse().setResponseCode(503) }
            // Deliberately bypass all UI upload helpers: the committed outbox must schedule recovery itself.
            graph.repository.setTaskStatus(href, "COMPLETED")
            withTimeout(120_000) {
                while (server.requests("PUT").size < 2 || graph.database.pendingMutationDao().allForAccount(account.id).isNotEmpty()) delay(250)
            }
            assertTrue(server.stored(href)!!.ics.contains("STATUS:COMPLETED"))
            assertTrue(server.requests("PUT").size >= 2)

            ActivityScenario.launch(MainActivity::class.java).use {
                // Let the initial foreground refresh finish, then change the server while the app remains open.
                withTimeout(120_000) {
                    while (app.getSharedPreferences("kgs_sync_worker", 0).getLong("last_sync_activity_at", 0) == 0L) delay(250)
                }
                server.putRemote(server.tasksHref, "task.ics", SampleIcs.task("auto-sync-regression", "Changed on server"))
                withTimeout(150_000) {
                    while (graph.database.taskDao().byResource(href)?.title != "Changed on server") delay(250)
                }
                assertEquals("Changed on server", graph.database.taskDao().byResource(href)!!.title)
            }
        } finally {
            accountId?.let { graph.repository.deleteAccount(it) }
            server.close()
        }
    }
}
