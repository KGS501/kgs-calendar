package com.kgs.calendar.domain.source

import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.domain.model.SourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSourceRulesTest {
    @Test
    fun hrefPrefixesIdentifyLocalAndReadOnlyCollections() {
        assertTrue("local://calendar".isLocalCollectionHref())
        assertTrue("readonly-abc".isReadOnlyCollectionHref())
        assertFalse("/remote.php/dav/calendars/u/local/".isLocalCollectionHref())
    }

    @Test
    fun readOnlyCollectionsAreFlaggedOrReadOnlyFeeds() {
        assertTrue(collection(href = "readonly-feed").isReadOnlyCollection())
        assertTrue(collection(readOnly = true).isReadOnlyCollection())
        assertFalse(collection().isReadOnlyCollection())
    }

    @Test
    fun androidProviderSourcesAreRecognisedByTypeOrId() {
        assertTrue(collection(sourceType = SourceType.AndroidProvider).isAndroidProviderCollection())
        assertTrue(collection(href = "android://calendar/7").isAndroidProviderCollection())
        assertFalse(collection().isAndroidProviderCollection())
        assertTrue(account(id = "android-provider").isAndroidProviderAccount())
        assertTrue(account(sourceType = SourceType.AndroidProvider).isAndroidProviderAccount())
        assertFalse(account().isAndroidProviderAccount())
    }

    private fun collection(
        href: String = "/cal/",
        readOnly: Boolean = false,
        sourceType: SourceType = SourceType.CalDav,
    ) = CollectionEntity(
        href = href,
        accountId = "a",
        displayName = "Calendar",
        color = 0,
        supportsEvents = true,
        supportsTasks = true,
        syncToken = null,
        ctag = null,
        readOnly = readOnly,
        sourceType = sourceType,
    )

    private fun account(id: String = "a", sourceType: SourceType = SourceType.CalDav) = AccountEntity(
        id = id,
        serverUrl = "https://example.com",
        username = "u",
        displayName = null,
        lastSyncAtMillis = null,
        sourceType = sourceType,
    )
}
