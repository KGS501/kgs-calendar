package com.kgs.calendar.data.platform

import com.kgs.calendar.data.local.KgsDatabase

/** Whether [this] is the database's own failure, which must abort a write instead of being recorded. */
internal expect fun Throwable.isDatabaseFailure(): Boolean

/** Runs [block] in one write transaction of the database. */
internal expect suspend fun <T> KgsDatabase.inWriteTransaction(block: suspend () -> T): T

/**
 * Decodes an image, crops it to the centered square and scales it down to at most [maxSize]
 * pixels per side. Returns the result as PNG, or null when [bytes] are not a decodable image.
 */
internal expect fun squarePng(bytes: ByteArray, maxSize: Int): ByteArray?
