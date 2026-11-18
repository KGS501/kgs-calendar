package com.kgs.calendar.data.secure

import com.kgs.calendar.data.remote.DavOrigins
import com.kgs.calendar.data.remote.canonicalOrigin

data class StoredCredentials(
    val serverUrl: String,
    val username: String,
    val appPassword: String,
    /** Where these credentials may go besides [serverUrl]; see [DavOrigins]. */
    val origins: DavOrigins = DavOrigins.None,
) {
    /** These credentials with [DavOrigins.davOrigin] following a calendar home found at [calendarHomeUrl]. */
    fun withDiscoveredDavOrigin(calendarHomeUrl: String): StoredCredentials {
        val homeOrigin = canonicalOrigin(calendarHomeUrl)?.takeUnless { it == canonicalOrigin(serverUrl) }
        return if (homeOrigin == origins.davOrigin) this else copy(origins = origins.copy(davOrigin = homeOrigin))
    }
}
