package com.kgs.calendar.reminder

/**
 * The tag a Windows notification carries for the reminder key [notificationTag], so that a newer
 * reminder of the same item replaces the one before it in the notification centre, as on Android and
 * iOS. Windows takes at most 64 characters, and a key is longer, so this is a short stable hash
 * (64-bit FNV-1a, as 16 hex digits): the same key always gives the same tag, on every run.
 */
fun windowsToastTag(notificationTag: String): String {
    var hash = FNV_OFFSET_BASIS
    for (byte in notificationTag.encodeToByteArray()) {
        hash = (hash xor (byte.toLong() and 0xFF)) * FNV_PRIME
    }
    return hash.toULong().toString(16).padStart(16, '0')
}

/** The group every reminder notification belongs to. */
const val WindowsToastGroup = "kgs-reminders"

private const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
private const val FNV_PRIME = 0x100000001b3L
