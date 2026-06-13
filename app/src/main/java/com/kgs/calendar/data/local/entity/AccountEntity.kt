package com.kgs.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kgs.calendar.data.SourceType

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String = PRIMARY_ID,
    val serverUrl: String,
    val username: String,
    val displayName: String?,
    val lastSyncAtMillis: Long?,
    val syncState: String = "idle",
    val syncError: String? = null,
    val sourceType: String = SourceType.CalDav,
    val principalUrl: String? = null,
    val calendarHomeUrl: String? = null,
    val capabilitiesJson: String? = null,
) {
    companion object {
        const val PRIMARY_ID = "primary"
    }
}
