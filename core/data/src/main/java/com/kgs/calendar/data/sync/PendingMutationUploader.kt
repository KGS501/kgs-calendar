package com.kgs.calendar.data.sync

import com.kgs.calendar.data.LocalWriteSupport
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.CalDavConflictException
import com.kgs.calendar.data.remote.PutResult
import com.kgs.calendar.data.remote.HttpStatusException
import com.kgs.calendar.data.secure.CredentialsStore
import com.kgs.calendar.data.secure.StoredCredentials
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.MutationAction
import kotlin.coroutines.cancellation.CancellationException

/** Drains the pending-mutation queue to CalDAV: conditional PUT/DELETE and local bookkeeping; conflicts remain pending. */
class PendingMutationUploader internal constructor(
    private val database: KgsDatabase,
    private val credentialsStore: CredentialsStore,
    private val calDavClient: CalDavHttpClient,
    private val localWrites: LocalWriteSupport,
) {
    suspend fun pushPending(credentials: StoredCredentials, accountId: String) {
        val activeCollectionHrefs = database.collectionDao().forAccount(accountId)
            .filter { it.isEnabled }
            .map { it.href }
            .toSet()
        pushPendingMutations(
            credentials,
            database.pendingMutationDao().allForAccount(accountId)
                .filter { it.collectionHref in activeCollectionHrefs },
        )
    }

    suspend fun pushPendingMutations(mutations: List<PendingMutationEntity>) {
        var failure: Throwable? = null
        mutations
            .groupBy { it.accountId }
            .forEach { (accountId, accountMutations) ->
                val enabled = database.collectionDao().forAccount(accountId).filter { it.isEnabled }.map { it.href }.toSet()
                try {
                    val credentials = credentialsStore.get(accountId) ?: error("Missing account credentials. Reconnect this calendar account.")
                    pushPendingMutations(credentials, accountMutations.filter { it.collectionHref in enabled })
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    if (failure == null) failure = error else failure!!.addSuppressed(error)
                }
            }
        failure?.let { throw it }
    }

    suspend fun pushPendingMutations(credentials: StoredCredentials, mutations: List<PendingMutationEntity>) {
        var firstFailure: Throwable? = null
        mutations.forEach { snapshot ->
            // Re-read both existence and preconditions: earlier acknowledgements may have
            // advanced the base ETag, or a new edit may have replaced this queued mutation.
            val mutation = database.pendingMutationDao().forResource(snapshot.resourceHref)
                .firstOrNull { it.id == snapshot.id } ?: return@forEach
            try {
                when (mutation.action) {
                    MutationAction.Put -> {
                        val raw = if (mutation.componentType == ComponentType.Task) {
                            localWrites.normalizedPendingTaskPayload(mutation)
                        } else {
                            mutation.payloadIcs ?: error("Missing payload")
                        }
                        val resource = database.resourceDao().get(mutation.resourceHref)
                        val effectiveBaseEtag = resolveCalDavUploadBaseEtag(
                            queuedBaseEtag = mutation.baseEtag,
                            currentResourceEtag = resource?.etag,
                            queuedPayload = raw,
                            currentRawIcs = resource?.rawIcs,
                        )
                        // Never refresh a rejected precondition: that would overwrite another client's edit.
                        val result = try {
                            calDavClient.putResource(
                                serverUrl = credentials.serverUrl,
                                href = mutation.resourceHref,
                                username = credentials.username,
                                appPassword = credentials.appPassword,
                                rawIcs = raw,
                                baseEtag = effectiveBaseEtag,
                            )
                        } catch (conflict: CalDavConflictException) {
                            // The previous request may have reached the server but lost its reply.
                            // Only exact content equality acknowledges it; a different body stays a conflict.
                            val confirmedEtag = matchingRemoteEtag(credentials, mutation.resourceHref, raw, propagateFailure = true)
                                ?: throw conflict
                            PutResult(mutation.resourceHref, confirmedEtag)
                        }
                        val uploadedEtag = result.etag
                            ?: matchingRemoteEtag(credentials, result.href, raw)
                        // Never associate a later PROPFIND's ETag with an unverified older body.
                        // The stored ETag is what later syncs use to recognise this upload as our own write.
                        localWrites.writeTransaction {
                            database.resourceDao().markSynced(mutation.resourceHref, uploadedEtag)
                            if (result.href != mutation.resourceHref) {
                                database.resourceDao().markSynced(result.href, uploadedEtag)
                            }
                            database.pendingMutationDao().forResource(mutation.resourceHref)
                                .filter { it.id != mutation.id }
                                .forEach { newerMutation ->
                                    database.pendingMutationDao().updateBaseEtag(newerMutation.id, uploadedEtag)
                                }
                            database.pendingMutationDao().delete(mutation)
                        }
                    }
                    MutationAction.Delete -> {
                        deleteConditionally(credentials, mutation)
                        localWrites.writeTransaction {
                            val pendingPuts = database.pendingMutationDao().forResource(mutation.resourceHref)
                                .filter { it.action == MutationAction.Put }
                            if (pendingPuts.isEmpty()) {
                                when (mutation.componentType) {
                                    ComponentType.Event -> database.eventDao().deleteByResource(mutation.resourceHref)
                                    ComponentType.Task -> database.taskDao().deleteByResource(mutation.resourceHref)
                                    ComponentType.Unknown -> Unit
                                }
                                database.resourceDao().delete(mutation.resourceHref)
                            } else {
                                // A restore/edit during DELETE is a new create, not stale local data to erase.
                                database.resourceDao().markSynced(mutation.resourceHref, null)
                                pendingPuts.forEach { database.pendingMutationDao().updateBaseEtag(it.id, null) }
                            }
                            database.pendingMutationDao().delete(mutation)
                        }
                    }
                    MutationAction.Unknown -> Unit
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                database.resourceDao().setSyncError(mutation.resourceHref, error.message ?: "Upload failed")
                val first = firstFailure
                if (first == null) firstFailure = error else if (first !== error) first.addSuppressed(error)
            }
        }
        firstFailure?.let { throw it }
    }

    private suspend fun deleteConditionally(credentials: StoredCredentials, mutation: PendingMutationEntity) {
        val etag = mutation.baseEtag ?: run {
            val remote = try {
                calDavClient.getResourceWithEtag(credentials.serverUrl, mutation.resourceHref, credentials.username, credentials.appPassword)
            } catch (error: HttpStatusException) {
                if (error.statusCode == 404 || error.statusCode == 410) return
                throw error
            }
            val localRaw = database.resourceDao().get(mutation.resourceHref)?.rawIcs
            if (localRaw == null || remote.calendarData.normalizedIcsForUploadComparison() != localRaw.normalizedIcsForUploadComparison()) {
                throw CalDavConflictException("DELETE", mutation.resourceHref)
            }
            remote.etag ?: throw IllegalStateException("Cannot safely delete this server item without an ETag.")
        }
        calDavClient.deleteResource(credentials.serverUrl, mutation.resourceHref, credentials.username, credentials.appPassword, etag)
    }

    private suspend fun matchingRemoteEtag(
        credentials: StoredCredentials,
        href: String,
        raw: String,
        propagateFailure: Boolean = false,
    ): String? =
        try {
            val remote = calDavClient.getResourceWithEtag(
                credentials.serverUrl, href, credentials.username, credentials.appPassword,
            )
            remote.etag.takeIf { remote.calendarData.normalizedIcsForUploadComparison() == raw.normalizedIcsForUploadComparison() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (propagateFailure) throw error
            null
        }

}

internal fun resolveCalDavUploadBaseEtag(
    queuedBaseEtag: String?,
    currentResourceEtag: String?,
    queuedPayload: String,
    currentRawIcs: String?,
): String? {
    val payloadStillMatchesLocalState = currentRawIcs != null &&
        queuedPayload.normalizedIcsForUploadComparison() == currentRawIcs.normalizedIcsForUploadComparison()
    return when {
        payloadStillMatchesLocalState && currentResourceEtag != null -> currentResourceEtag
        queuedBaseEtag != null -> queuedBaseEtag
        else -> currentResourceEtag
    }
}

private fun String.normalizedIcsForUploadComparison(): String =
    replace("\r\n", "\n").replace('\r', '\n').trim()
