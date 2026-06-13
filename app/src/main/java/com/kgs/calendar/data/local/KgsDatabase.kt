package com.kgs.calendar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kgs.calendar.data.local.dao.AccountDao
import com.kgs.calendar.data.local.dao.CollectionDao
import com.kgs.calendar.data.local.dao.EventDao
import com.kgs.calendar.data.local.dao.PendingMutationDao
import com.kgs.calendar.data.local.dao.ResourceDao
import com.kgs.calendar.data.local.dao.TaskDao
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.local.entity.TaskEntity

@Database(
    entities = [
        AccountEntity::class,
        CollectionEntity::class,
        CalendarResourceEntity::class,
        EventEntity::class,
        TaskEntity::class,
        PendingMutationEntity::class,
    ],
    version = 19,
    exportSchema = false,
)
abstract class KgsDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun collectionDao(): CollectionDao
    abstract fun resourceDao(): ResourceDao
    abstract fun eventDao(): EventDao
    abstract fun taskDao(): TaskDao
    abstract fun pendingMutationDao(): PendingMutationDao
}
