package com.kgs.calendar.domain.sync

import com.kgs.calendar.data.local.entity.PendingMutationEntity

/**
 * The occurrences of a recurring resource that a queued upload changes, stored in
 * [PendingMutationEntity.occurrenceScope]. It only decides which occurrences show the "waiting
 * for sync" indicators; uploads, conflict handling and sync never read it.
 *
 * Stored text: null means the whole resource. Otherwise a comma-separated list of RECURRENCE-ID
 * epoch millis, where a trailing `+` means that occurrence and every later one.
 */
data class PendingOccurrenceScope(
    val occurrences: Set<Long> = emptySet(),
    val followingFrom: Long? = null,
) {
    fun covers(recurrenceIdMillis: Long): Boolean =
        recurrenceIdMillis in occurrences || (followingFrom != null && recurrenceIdMillis >= followingFrom)

    operator fun plus(other: PendingOccurrenceScope): PendingOccurrenceScope {
        val following = listOfNotNull(followingFrom, other.followingFrom).minOrNull()
        return PendingOccurrenceScope(
            occurrences = (occurrences + other.occurrences)
                .filterTo(sortedSetOf()) { following == null || it < following },
            followingFrom = following,
        )
    }

    fun encode(): String =
        (occurrences.sorted().map(Long::toString) + listOfNotNull(followingFrom?.let { "$it+" }))
            .joinToString(",")

    companion object {
        /** Stored scope of an edit to the single occurrence [recurrenceIdMillis]. */
        fun single(recurrenceIdMillis: Long): String = PendingOccurrenceScope(occurrences = setOf(recurrenceIdMillis)).encode()

        /** Stored scope of an edit to the occurrence [recurrenceIdMillis] and every later one. */
        fun following(recurrenceIdMillis: Long): String = PendingOccurrenceScope(followingFrom = recurrenceIdMillis).encode()

        /** Null for the whole resource, including stored text this version cannot read. */
        fun decode(stored: String?): PendingOccurrenceScope? {
            if (stored.isNullOrBlank()) return null
            val occurrences = sortedSetOf<Long>()
            var followingFrom: Long? = null
            stored.split(',').forEach { raw ->
                val token = raw.trim()
                if (token.endsWith('+')) {
                    val millis = token.dropLast(1).toLongOrNull() ?: return null
                    followingFrom = followingFrom?.let { minOf(it, millis) } ?: millis
                } else {
                    occurrences += token.toLongOrNull() ?: return null
                }
            }
            return PendingOccurrenceScope(occurrences, followingFrom)
        }

        /**
         * Stored scope of a queued upload that replaces queued uploads with the [previous] scopes:
         * the union of all of them, and the whole resource as soon as any of them is.
         */
        fun merge(previous: List<String?>, next: String?): String? {
            val scopes = (previous + next).map { decode(it) ?: return null }
            return scopes.reduce(PendingOccurrenceScope::plus).encode()
        }

        /**
         * Whether a pending change with the [stored] scope applies to the occurrence whose
         * RECURRENCE-ID [recurrenceIdMillis] returns. It is only asked for scoped changes; an
         * occurrence without an id counts as covered.
         */
        fun covers(stored: String?, recurrenceIdMillis: () -> Long?): Boolean {
            val scope = decode(stored) ?: return true
            val id = recurrenceIdMillis() ?: return true
            return scope.covers(id)
        }
    }
}

/**
 * The newest pending change of [resourceHref] that applies to the displayed occurrence whose
 * RECURRENCE-ID [recurrenceIdMillis] returns (only evaluated when a scoped change exists).
 */
fun List<PendingMutationEntity>.pendingMutationFor(
    resourceHref: String,
    recurrenceIdMillis: () -> Long?,
): PendingMutationEntity? {
    val id by lazy(LazyThreadSafetyMode.NONE, recurrenceIdMillis)
    return lastOrNull { mutation ->
        mutation.resourceHref == resourceHref && PendingOccurrenceScope.covers(mutation.occurrenceScope) { id }
    }
}
