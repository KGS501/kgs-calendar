package com.kgs.calendar.data.local

import androidx.room.TypeConverter
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.MutationAction
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.model.SyncState

/** Stores the typed domain values as the same TEXT values the schema has always used. */
class KgsTypeConverters {
    @TypeConverter
    fun sourceTypeToValue(type: SourceType): String = type.value

    @TypeConverter
    fun sourceTypeFromValue(value: String?): SourceType = SourceType.fromValue(value)

    @TypeConverter
    fun componentTypeToValue(type: ComponentType): String = type.value

    @TypeConverter
    fun componentTypeFromValue(value: String?): ComponentType = ComponentType.fromValue(value)

    @TypeConverter
    fun mutationActionToValue(action: MutationAction): String = action.value

    @TypeConverter
    fun mutationActionFromValue(value: String?): MutationAction = MutationAction.fromValue(value)

    @TypeConverter
    fun syncStateToValue(state: SyncState): String = state.value

    @TypeConverter
    fun syncStateFromValue(value: String?): SyncState = SyncState.fromValue(value)
}
