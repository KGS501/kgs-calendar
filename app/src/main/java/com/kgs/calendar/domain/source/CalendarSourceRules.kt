package com.kgs.calendar.domain.source

import com.kgs.calendar.data.LOCAL_COLLECTION_PREFIX
import com.kgs.calendar.data.READ_ONLY_PREFIX
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.domain.model.SourceType

internal fun String.isLocalCollectionHref(): Boolean = startsWith(LOCAL_COLLECTION_PREFIX)

internal fun String.isReadOnlyCollectionHref(): Boolean = startsWith(READ_ONLY_PREFIX)

internal fun CollectionEntity.isReadOnlyCollection(): Boolean = readOnly || href.isReadOnlyCollectionHref()

internal fun CollectionEntity.isAndroidProviderCollection(): Boolean =
    sourceType == SourceType.AndroidProvider || href.startsWith(AndroidCalendarProviderClient.ANDROID_CALENDAR_PREFIX)

internal fun AccountEntity.isAndroidProviderAccount(): Boolean =
    sourceType == SourceType.AndroidProvider || id == AndroidCalendarProviderClient.ANDROID_ACCOUNT_ID
