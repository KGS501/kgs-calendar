package com.kgs.calendar.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KgsDatabaseInstrumentedTest {
    private lateinit var database: KgsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KgsDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun storesTaskWithCollection() = runBlocking {
        database.accountDao().upsert(
            AccountEntity(serverUrl = "https://nextcloud.test", username = "fromb", displayName = "fromb", lastSyncAtMillis = null),
        )
        database.collectionDao().upsertAll(
            listOf(
                CollectionEntity(
                    href = "/remote.php/dav/calendars/fromb/tasks/",
                    accountId = AccountEntity.PRIMARY_ID,
                    displayName = "Tasks",
                    color = 0xff176b5d.toInt(),
                    supportsEvents = false,
                    supportsTasks = true,
                    syncToken = null,
                    ctag = null,
                ),
            ),
        )
        database.taskDao().upsert(
            TaskEntity(
                uid = "task-1",
                collectionHref = "/remote.php/dav/calendars/fromb/tasks/",
                resourceHref = "/remote.php/dav/calendars/fromb/tasks/task-1.ics",
                title = "Test task",
                notes = null,
                dueAtMillis = null,
                startAtMillis = null,
                completedAtMillis = null,
                isCompleted = false,
                priority = null,
                color = 0xff176b5d.toInt(),
            ),
        )

        assertEquals("Test task", database.taskDao().get("task-1")!!.title)
    }

    @Test
    fun resourceLookupKeepsDuplicateUidsInSeparateCollections() = runBlocking {
        database.accountDao().upsert(
            AccountEntity(serverUrl = "https://nextcloud.test", username = "fromb", displayName = "fromb", lastSyncAtMillis = null),
        )
        val firstCollection = "/remote.php/dav/calendars/fromb/work/"
        val secondCollection = "/remote.php/dav/calendars/fromb/home/"
        database.collectionDao().upsertAll(
            listOf(
                CollectionEntity(firstCollection, AccountEntity.PRIMARY_ID, "Work", 0xff176b5d.toInt(), false, true, null, null),
                CollectionEntity(secondCollection, AccountEntity.PRIMARY_ID, "Home", 0xff3267a8.toInt(), false, true, null, null),
            ),
        )
        val firstHref = "${firstCollection}same-uid.ics"
        val secondHref = "${secondCollection}same-uid.ics"
        val first = TaskEntity("same-uid", firstCollection, firstHref, "Work task", null, dueAtMillis = null, startAtMillis = null, completedAtMillis = null, isCompleted = false, priority = null, color = 0xff176b5d.toInt())
        val second = TaskEntity("same-uid", secondCollection, secondHref, "Home task", null, dueAtMillis = null, startAtMillis = null, completedAtMillis = null, isCompleted = false, priority = null, color = 0xff3267a8.toInt())
        database.taskDao().upsert(first)
        database.taskDao().upsert(second)

        database.taskDao().upsert(database.taskDao().byResource(secondHref)!!.copy(status = "COMPLETED", isCompleted = true))

        assertEquals(null, database.taskDao().byResource(firstHref)!!.status)
        assertEquals("COMPLETED", database.taskDao().byResource(secondHref)!!.status)
    }

    @Test
    fun reminderQueryExcludesCancelledTasks() = runBlocking {
        database.accountDao().upsert(
            AccountEntity(serverUrl = "https://nextcloud.test", username = "fromb", displayName = "fromb", lastSyncAtMillis = null),
        )
        val collectionHref = "/remote.php/dav/calendars/fromb/tasks/"
        database.collectionDao().upsertAll(
            listOf(CollectionEntity(collectionHref, AccountEntity.PRIMARY_ID, "Tasks", 0xff176b5d.toInt(), false, true, null, null)),
        )
        database.taskDao().upsert(
            TaskEntity(
                uid = "active",
                collectionHref = collectionHref,
                resourceHref = "${collectionHref}active.ics",
                title = "Active",
                notes = null,
                dueAtMillis = 2_000_000L,
                startAtMillis = null,
                completedAtMillis = null,
                isCompleted = false,
                status = "NEEDS-ACTION",
                priority = null,
                remindersCsv = "10",
                color = 0xff176b5d.toInt(),
            ),
        )
        database.taskDao().upsert(
            TaskEntity(
                uid = "cancelled",
                collectionHref = collectionHref,
                resourceHref = "${collectionHref}cancelled.ics",
                title = "Cancelled",
                notes = null,
                dueAtMillis = 2_000_000L,
                startAtMillis = null,
                completedAtMillis = null,
                isCompleted = false,
                status = "CANCELLED",
                priority = null,
                remindersCsv = "10",
                color = 0xff176b5d.toInt(),
            ),
        )

        assertEquals(listOf("active"), database.taskDao().withReminders().map { it.uid })
    }
}
