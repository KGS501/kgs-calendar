package com.kgs.calendar.data.remote

import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.platform.canonicalHttpUrlOrNull
import com.kgs.calendar.platform.parseUri

/**
 * Where an account's credentials and calendar data may be sent. The origin of the server URL the
 * user entered is always allowed, and so is its upgrade from plain HTTP to HTTPS. Anything else a
 * server names (a principal, a calendar home, a member href or a redirect) needs the user's
 * approval, because a server's metadata must not be able to pick a new recipient for the password.
 */
data class DavOrigins(
    /** Origins the user allowed besides the one they entered, in [canonicalOrigin] form. */
    val approved: Set<String> = emptySet(),
    /**
     * The origin of the account's calendar home when it isn't the entered one. Hrefs are stored the
     * way the server wrote them, usually without a host, and resolve against this origin.
     */
    val davOrigin: String? = null,
) {
    companion object {
        val None = DavOrigins()
    }
}

/** Refused before anything was sent; [origin] is what the server asked for. */
class CalDavOriginNotApprovedException(
    val origin: String,
    /** The request would have left HTTPS for plain HTTP, which can't be approved. */
    val isDowngrade: Boolean,
) : IllegalStateException(
    if (isDowngrade) {
        "The server tried to move this account from HTTPS to the unencrypted address $origin. Nothing was sent there."
    } else {
        "The server asked this app to send your login to $origin, which this account hasn't approved. Reconnect the account to review it."
    },
)

/**
 * Adding an account stopped because its server pointed to [origin]. The user decides; approving
 * repeats the attempt with [approved] and [origin] allowed.
 */
class CalDavOriginApprovalRequired(
    val origin: String,
    val serverUrl: String,
    val username: String,
    val appPassword: String,
    val approved: Set<String>,
) : IllegalStateException("This server wants your login to go to $origin.")

/** The origin named by the sync error of an account stopped by [requireApprovedDavOrigin]. */
fun String?.unapprovedDavOrigin(): String? =
    this?.let { UNAPPROVED_ORIGIN.find(it)?.groupValues?.getOrNull(1) }

/** An account that stopped syncing until the user reviews [origin]. */
data class OriginApprovalNotice(val accountId: String, val accountName: String, val origin: String) {
    /** Identifies one request, so that it is announced once, however many syncs run into it. */
    val key: String get() = "$accountId|$origin"
}

fun List<AccountEntity>.originApprovalNotices(): List<OriginApprovalNotice> = mapNotNull { account ->
    account.syncError.unapprovedDavOrigin()?.let { origin ->
        OriginApprovalNotice(account.id, account.displayName?.takeIf { it.isNotBlank() } ?: account.username, origin)
    }
}

private val UNAPPROVED_ORIGIN = Regex("""send your login to (https?://\S+), which this account hasn't approved""")

/** `scheme://host:port` with the effective port, or null when [url] isn't an HTTP(S) URL. */
fun canonicalOrigin(url: String): String? {
    // The same parser the transport uses decides what a URL's host is.
    val parsed = parseUri(canonicalHttpUrlOrNull(url) ?: return null)
    val scheme = parsed.scheme?.lowercase() ?: return null
    val host = parsed.host?.lowercase() ?: return null
    val port = parsed.port.takeIf { it > 0 } ?: when (scheme) {
        "https" -> 443
        "http" -> 80
        else -> return null
    }
    return "$scheme://$host:$port"
}

/** Throws unless the account of [serverUrl] may send credentials and data to [targetUrl]. */
fun requireApprovedDavOrigin(targetUrl: String, serverUrl: String, origins: DavOrigins) {
    val target = canonicalOrigin(targetUrl) ?: throw CalDavOriginNotApprovedException(targetUrl, isDowngrade = false)
    val entered = canonicalOrigin(serverUrl)
    if (target == entered || target in origins.approved) return
    if (entered != null && isHttpsUpgrade(entered, target)) return
    val leavesHttps = entered?.startsWith("https:") == true && target.startsWith("http:")
    throw CalDavOriginNotApprovedException(target, isDowngrade = leavesHttps)
}

private fun isHttpsUpgrade(from: String, to: String): Boolean =
    from.startsWith("http:") && from.endsWith(":80") && to.startsWith("https:") && to.endsWith(":443") &&
        from.removePrefix("http://").removeSuffix(":80") == to.removePrefix("https://").removeSuffix(":443")
