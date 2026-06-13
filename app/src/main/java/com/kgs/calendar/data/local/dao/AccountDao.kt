package com.kgs.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kgs.calendar.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY displayName COLLATE NOCASE, username COLLATE NOCASE")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observe(id: String = AccountEntity.PRIMARY_ID): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts ORDER BY displayName COLLATE NOCASE, username COLLATE NOCASE")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: String = AccountEntity.PRIMARY_ID): AccountEntity?

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("UPDATE accounts SET displayName = :displayName WHERE id = :id")
    suspend fun updateDisplayName(id: String, displayName: String)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE accounts SET syncState = :state, syncError = :error, lastSyncAtMillis = :lastSyncAtMillis WHERE id = :id")
    suspend fun updateSyncState(
        state: String,
        error: String?,
        lastSyncAtMillis: Long?,
        id: String = AccountEntity.PRIMARY_ID,
    )

    @Query(
        """
        UPDATE accounts
        SET principalUrl = :principalUrl,
            calendarHomeUrl = :calendarHomeUrl,
            capabilitiesJson = :capabilitiesJson
        WHERE id = :id
        """,
    )
    suspend fun updateCalDavDiscovery(
        id: String,
        principalUrl: String?,
        calendarHomeUrl: String,
        capabilitiesJson: String,
    )
}
