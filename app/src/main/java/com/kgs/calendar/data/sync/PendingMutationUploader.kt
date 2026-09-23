package com.kgs.calendar.data.sync

import com.kgs.calendar.data.LocalWriteSupport
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.remote.CalDavConflictException
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.PutResult
import com.kgs.calendar.data.secure.CredentialsStore
import com.kgs.calendar.data.secure.StoredCredentials
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.MutationAction

/** Drains the pending-mutation queue to CalDAV: conditional PUT/DELETE, the 412 retry and the local bookkeeping. */
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
        mutations
            .groupBy { it.accountId }
            .forEach { (accountId, accountMutations) ->
                val credentials = credentialsStore.get(accountId) ?: return@forEach
                pushPendingMutations(credentials, accountMutations)
            }
    }

    suspend fun pushPendingMutations(credentials: StoredCredentials, mutations: List<PendingMutationEntity>) {
        var firstFailure: Throwable? = null
        mutations.forEach { mutation ->
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
                        val putAttempt = putCalDavResourceWithConflictRetry(
                            initialBaseEtag = effectiveBaseEtag,
                            put = { baseEtag ->
                                calDavClient.putResource(
                                    serverUrl = credentials.serverUrl,
                                    href = mutation.resourceHref,
                                    username = credentials.username,
                                    appPassword = credentials.appPassword,
                                    rawIcs = raw,
                                    baseEtag = baseEtag,
                                )
                            },
                            resolveCurrentEtag = {
                                calDavClient.getResourceEtag(
                                    serverUrl = credentials.serverUrl,
                                    href = mutation.resourceHref,
                                    username = credentials.username,
                                    appPassword = credentials.appPassword,
                                )
                            },
                        )
                        val result = putAttempt.result
                        val uploadedEtag = result.etag
                            ?: runCatching {
                                calDavClient.getResourceEtag(
                                    serverUrl = credentials.serverUrl,
                                    href = result.href,
                                    username = credentials.username,
                                    appPassword = credentials.appPassword,
                                )
                            }.getOrNull()
                            ?: putAttempt.submittedBaseEtag
                        // The stored ETag is what later syncs use to recognise this upload as our own write.
                        localWrites.writeTransaction {
                            database.resourceDao().markSynced(mutation.resourceHref, uploadedEtag)
                            if (result.href != mutation.resourceHref) {
                                database.resourceDao().markSynced(result.href, uploadedEtag)
                            }
                            database.pendingMutationDao()
                                .latestForResourceAndAction(mutation.resourceHref, MutationAction.Put)
                                ?.takeIf { it.id != mutation.id }
                                ?.let { newerMutation ->
                                    database.pendingMutationDao().updateBaseEtag(newerMutation.id, uploadedEtag)
                                }
                            database.pendingMutationDao().delete(mutation)
                        }
                    }
                    MutationAction.Delete -> {
                        calDavClient.deleteResource(
                            serverUrl = credentials.serverUrl,
                            href = mutation.resourceHref,
                            username = credentials.username,
                            appPassword = credentials.appPassword,
                            baseEtag = mutation.baseEtag,
                        )
                        localWrites.writeTransaction {
                            when (mutation.componentType) {
                                ComponentType.Event -> database.eventDao().deleteByResource(mutation.resourceHref)
                                ComponentType.Task -> database.taskDao().deleteByResource(mutation.resourceHref)
                            }
                            database.resourceDao().delete(mutation.resourceHref)
                            database.pendingMutationDao().delete(mutation)
                        }
                    }
                }
            } catch (error: Throwable) {
                database.resourceDao().setSyncError(mutation.resourceHref, error.message ?: "Upload failed")
                if (firstFailure == null) firstFailure = error
            }
        }
        firstFailure?.let { throw it }
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

internal data class CalDavPutAttemptResult(
    val result: PutResult,
    val submittedBaseEtag: String?,
)

internal suspend fun putCalDavResourceWithConflictRetry(
    initialBaseEtag: String?,
    put: suspend (baseEtag: String?) -> PutResult,
    resolveCurrentEtag: suspend () -> String?,
): CalDavPutAttemptResult = try {
    CalDavPutAttemptResult(
        result = put(initialBaseEtag),
        submittedBaseEtag = initialBaseEtag,
    )
} catch (conflict: CalDavConflictException) {
    val currentEtag = resolveCurrentEtag() ?: throw conflict
    if (currentEtag == initialBaseEtag) throw conflict
    CalDavPutAttemptResult(
        result = put(currentEtag),
        submittedBaseEtag = currentEtag,
    )
}

private fun String.normalizedIcsForUploadComparison(): String =
    replace("\r\n", "\n").replace('\r', '\n').trim()
