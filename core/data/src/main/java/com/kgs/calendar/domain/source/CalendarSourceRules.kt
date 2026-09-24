package com.kgs.calendar.domain.source

import com.kgs.calendar.data.LOCAL_COLLECTION_PREFIX
import com.kgs.calendar.data.READ_ONLY_PREFIX
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.domain.model.SourceType

fun String.isLocalCollectionHref(): Boolean = startsWith(LOCAL_COLLECTION_PREFIX)

fun String.isReadOnlyCollectionHref(): Boolean = startsWith(READ_ONLY_PREFIX)

fun CollectionEntity.isReadOnlyCollection(): Boolean = readOnly || href.isReadOnlyCollectionHref()

fun CollectionEntity.isAndroidProviderCollection(): Boolean =
    sourceType == SourceType.AndroidProvider || href.startsWith(AndroidCalendarProviderClient.ANDROID_CALENDAR_PREFIX)

fun AccountEntity.isAndroidProviderAccount(): Boolean =
    sourceType == SourceType.AndroidProvider || id == AndroidCalendarProviderClient.ANDROID_ACCOUNT_ID
