package com.kgs.calendar.ui

import android.net.Uri
import com.kgs.calendar.R
import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.account.SourceImageKeys
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.domain.source.CalendarSourceGroup
import com.kgs.calendar.domain.source.CalendarSourceGroups
import com.kgs.calendar.domain.source.ReadOnlyFeedStatus
import com.kgs.calendar.domain.source.readOnlyFeedStatus
import com.kgs.calendar.sync.CalendarStructuralMutation
import com.kgs.calendar.sync.PostMutationStage
import com.kgs.calendar.sync.SourceCalendarMutationCoordinator
import com.kgs.calendar.sync.StructuralMutationResult
import com.kgs.calendar.ui.sources.DefaultLocalSourceName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/** Accounts, calendar sources and collections, manual sync and Android provider diagnostics. */
class CalendarSourceActions internal constructor(
    private val scope: CoroutineScope,
    private val repository: CalendarRepository,
    private val settingsStore: SettingsStore,
    private val sourceCalendarMutationCoordinator: SourceCalendarMutationCoordinator,
    private val reminderRescheduler: ReminderRescheduler,
    private val appLifecycleSignals: AppLifecycleSignals,
    private val strings: UiStrings,
    private val transient: CalendarTransientState,
    private val pickedContentReader: PickedContentReader = PickedContentReader { null },
) {
    private val busy = transient.busy
    private val manualSyncing = transient.manualSyncing
    private val message = transient.message

    fun refreshAndroidProviderDiagnostics() {
        scope.launch {
            runCatching { refreshAndroidProviderDiagnosticsInternal() }
        }
    }

    fun setDisabledAndroidProviderCalendarsVisible(visible: Boolean) {
        runBusy(rescheduleReminders = true) {
            settingsStore.setShowDisabledAndroidProviderCalendars(visible)
            if (visible) {
                repository.enableAndroidCalendars(includeDisabledProviderCalendars = true)
            } else {
                repository.refreshAndroidCalendarsIfEnabled(
                    removeStale = true,
                    includeDisabledProviderCalendars = false,
                )
            }
            refreshAndroidProviderDiagnosticsInternal()
            message.value = if (visible) {
                "Disabled Android provider calendars are visible."
            } else {
                "Disabled Android provider calendars are hidden."
            }
        }
    }

    fun applyCollectionOrder(hrefs: List<String>) {
        scope.launch { repository.applyCollectionOrder(hrefs) }
    }

    fun manualLogin(
        serverUrl: String,
        username: String,
        appPassword: String,
        onResult: ((Boolean, String?) -> Unit)? = null,
    ) {
        scope.launch {
            busy.value = true
            val saved = runCatching {
                sourceCalendarMutationCoordinator.run(
                    kind = CalendarStructuralMutation.AddSource,
                    onMutationPersisted = {
                        val acceptedMessage = "CalDAV account added. Initial sync started."
                        message.value = acceptedMessage
                        busy.value = false
                        onResult?.invoke(true, acceptedMessage)
                    },
                ) { repository.saveManualAccount(serverUrl, username, appPassword) }
            }
            saved.onFailure {
                val errorMessage = it.message ?: "Could not verify this CalDAV login."
                message.value = errorMessage
                busy.value = false
                onResult?.invoke(false, errorMessage)
            }
            if (saved.isFailure) {
                return@launch
            }
            refreshAndroidProviderDiagnosticsInternal()
            val resultMessage = structuralMutationMessage(saved.getOrThrow())
            message.value = resultMessage
            busy.value = false
        }
    }

    fun startBrowserLogin(serverUrl: String) {
        runStructuralMutation(CalendarStructuralMutation.AddSource) {
            val login = repository.startLoginFlow(serverUrl)
            transient.externalLoginUrl.value = login.loginUrl
            repository.completeLoginFlow(login.pollEndpoint, login.token)
        }
    }

    fun addReadOnlyCalendar(url: String) {
        scope.launch {
            busy.value = true
            manualSyncing.value = true
            var addedId: String? = null
            runCatching {
                sourceCalendarMutationCoordinator.run(CalendarStructuralMutation.AddSource) {
                    addedId = repository.addReadOnlyCalendar(url).id
                }
            }.onSuccess {
                // The calendar stays even when its URL gives nothing yet; say so instead of failing.
                val account = repository.observeAccounts().first().firstOrNull { it.id == addedId }
                val collection = repository.observeCollections().first()
                    .firstOrNull { it.accountId == addedId }
                message.value = when (collection?.let { readOnlyFeedStatus(account, it) }) {
                    ReadOnlyFeedStatus.NoDataYet, ReadOnlyFeedStatus.Loading, ReadOnlyFeedStatus.UpdateFailed ->
                        strings.get(R.string.read_only_added_no_data)
                    ReadOnlyFeedStatus.Empty -> strings.get(R.string.read_only_added_empty)
                    null -> strings.get(R.string.read_only_added)
                }
            }.onFailure {
                message.value = "Adding the read-only calendar failed. ${it.message ?: it::class.java.simpleName}"
            }
            manualSyncing.value = false
            busy.value = false
        }
    }

    /** Moves a calendar into the sidebar group [groupKey]; null returns it to its automatic group. */
    fun moveCollectionToGroup(href: String, groupKey: String?) {
        scope.launch { settingsStore.setCalendarGroup(href, groupKey) }
    }

    /** Creates the custom sidebar group [name] and moves the calendar [href] into it. */
    fun moveCollectionToNewGroup(href: String, name: String) {
        val groupKey = CalendarSourceGroups.CUSTOM_PREFIX + UUID.randomUUID()
        scope.launch {
            settingsStore.setCalendarGroupName(groupKey, name)
            settingsStore.setCalendarGroup(href, groupKey)
        }
    }

    /** Creates an empty custom group named [name] and returns its key. */
    fun createGroup(name: String): String {
        val groupKey = CalendarSourceGroups.CUSTOM_PREFIX + UUID.randomUUID()
        scope.launch { settingsStore.setCalendarGroupName(groupKey, name) }
        return groupKey
    }

    /** Deletes the custom group [groupKey] and its picture; its calendars return to their sources' groups. */
    fun deleteGroup(groupKey: String) {
        if (!CalendarSourceGroups.isCustomKey(groupKey)) return
        scope.launch {
            settingsStore.deleteCalendarGroup(groupKey)
            repository.deleteSourceImage(SourceImageKeys.group(groupKey))
        }
    }

    /** A source's own group has the source's name, so renaming it renames the source. */
    fun renameGroup(groupKey: String, name: String?) {
        scope.launch {
            val owner = ownerOfGroup(groupKey)
            if (owner == null) {
                settingsStore.setCalendarGroupName(groupKey, name)
                return@launch
            }
            val trimmed = name?.trim().orEmpty()
            when {
                trimmed.isNotEmpty() -> repository.renameAccount(owner.id, trimmed)
                // The local calendar's default name follows the app's language again.
                owner.id == UiLocalAccountId -> repository.renameAccount(owner.id, DefaultLocalSourceName)
            }
        }
    }

    /** A source's own group shows the source's picture, so the picture is set on the source. */
    fun setGroupImage(groupKey: String, uri: Uri) {
        scope.launch {
            val owner = ownerOfGroup(groupKey)
            if (owner != null) return@launch setAccountImageNow(owner.id, uri)
            val bytes = pickedContentReader.read(uri)
            if (bytes == null || !repository.saveSourceImage(SourceImageKeys.group(groupKey), bytes)) {
                message.value = strings.get(R.string.calendar_group_image_failed)
            }
        }
    }

    fun removeGroupImage(groupKey: String) {
        scope.launch {
            val owner = ownerOfGroup(groupKey)
            if (owner != null) removeAccountImageNow(owner.id) else repository.deleteSourceImage(SourceImageKeys.group(groupKey))
        }
    }

    /** Uses a picked picture for the source [accountId] instead of the one it provides. */
    fun setAccountImage(accountId: String, uri: Uri) {
        scope.launch { setAccountImageNow(accountId, uri) }
    }

    /** Removes the source's picture: the one the user picked, and the one the source provides. */
    fun removeAccountImage(accountId: String) {
        scope.launch { removeAccountImageNow(accountId) }
    }

    /** Shows the picture the source [accountId] provides again after it was removed. */
    fun restoreAccountImage(accountId: String) {
        scope.launch { settingsStore.setSourceImageHidden(SourceImageKeys.account(accountId), hidden = false) }
    }

    private suspend fun setAccountImageNow(accountId: String, uri: Uri) {
        val bytes = pickedContentReader.read(uri)
        if (bytes == null || !repository.saveSourceImage(SourceImageKeys.accountCustom(accountId), bytes)) {
            message.value = strings.get(R.string.calendar_group_image_failed)
        }
    }

    private suspend fun removeAccountImageNow(accountId: String) {
        repository.deleteSourceImage(SourceImageKeys.accountCustom(accountId))
        val provided = SourceImageKeys.account(accountId)
        if (provided in repository.observeSourceImages().first()) settingsStore.setSourceImageHidden(provided, hidden = true)
    }

    /**
     * Earlier versions let a source's own group have a name, picture and color of its own, apart
     * from the source's. Moves them to the source, so each source has one name and one picture.
     */
    fun adoptOwnedGroups() {
        scope.launch {
            runCatching {
                val settings = settingsStore.calendarGroupSettings.first()
                val images = repository.observeSourceImages().first()
                currentGroups().forEach { group ->
                    val owner = group.owner ?: return@forEach
                    val groupImage = SourceImageKeys.group(group.key)
                    val name = settings.names[group.key]
                    if (name == null && groupImage !in images && groupImage !in settings.avatarColors) return@forEach
                    if (name != null) repository.renameAccount(owner.id, name)
                    repository.moveSourceImage(groupImage, SourceImageKeys.accountCustom(owner.id))
                    settingsStore.adoptGroupIntoSource(group.key, owner.id)
                }
            }
        }
    }

    private suspend fun currentGroups(): List<CalendarSourceGroup> = CalendarSourceGroups.group(
        accounts = repository.observeAccounts().first(),
        collections = repository.observeCollections().first(),
        settings = settingsStore.calendarGroupSettings.first(),
    )

    private suspend fun ownerOfGroup(groupKey: String): AccountEntity? =
        currentGroups().firstOrNull { it.key == groupKey }?.owner

    /** Sets the color of the letter picture of [imageKey]; null restores the automatic color. */
    fun setAvatarColor(imageKey: String, color: Int?) {
        scope.launch { settingsStore.setAvatarColor(imageKey, color) }
    }

    fun addAndroidDeviceCalendars() {
        runStructuralMutation(CalendarStructuralMutation.AddSource, showManualSync = true) {
            repository.enableAndroidCalendars(
                includeDisabledProviderCalendars = includeDisabledAndroidProviderCalendars(),
            )
            appLifecycleSignals.registerAndroidCalendarObserverIfPermitted()
        }
    }

    /** A source's name lives only on this device, so it is saved at once without a refresh. */
    fun renameAccount(accountId: String, displayName: String) {
        scope.launch {
            runCatching { repository.renameAccount(accountId, displayName) }
                .onSuccess { message.value = null }
                .onFailure { message.value = it.message ?: "Could not update calendar settings." }
        }
    }

    fun updateAccount(accountId: String, displayName: String, serverUrl: String, username: String, appPassword: String?) {
        runStructuralMutation(CalendarStructuralMutation.EditSource) {
            repository.updateAccount(accountId, displayName, serverUrl, username, appPassword)
        }
    }

    fun deleteAccount(accountId: String) {
        runStructuralMutation(CalendarStructuralMutation.RemoveSource) {
            val accounts = repository.observeAccounts().first()
            repository.deleteAccount(accountId)
            // The source's own group goes with it, unless other feeds of the same website remain.
            settingsStore.setAvatarColor(SourceImageKeys.account(accountId), null)
            val removed = accounts.firstOrNull { it.id == accountId } ?: return@runStructuralMutation
            val groupKey = CalendarSourceGroups.automaticKey(removed, accounts)
            if (accounts.none { it.id != accountId && CalendarSourceGroups.automaticKey(it, accounts) == groupKey }) {
                repository.deleteSourceImage(SourceImageKeys.group(groupKey))
                settingsStore.setCalendarGroupName(groupKey, null)
                settingsStore.setAvatarColor(SourceImageKeys.group(groupKey), null)
            }
        }
    }

    /** Deletes the local calendar with all its events and tasks; it is not created again on start. */
    fun deleteLocalCalendar() {
        runStructuralMutation(CalendarStructuralMutation.RemoveSource) {
            settingsStore.setLocalCalendarRemoved(true)
            repository.deleteAccount(UiLocalAccountId)
        }
    }

    /** Adds an empty local calendar again after it was deleted. */
    fun addLocalCalendar() {
        runStructuralMutation(CalendarStructuralMutation.AddSource) {
            settingsStore.setLocalCalendarRemoved(false)
            repository.ensureLocalCalendar()
        }
    }

    fun externalLoginUrlConsumed() {
        transient.externalLoginUrl.value = null
    }

    fun syncNow() {
        runBusy(rescheduleReminders = true, showManualSync = true) {
            repository.syncNow(
                includeDisabledProviderCalendars = includeDisabledAndroidProviderCalendars(),
                forceFullCalDavRefresh = true,
            )
            refreshAndroidProviderDiagnosticsInternal()
        }
    }

    fun setCollectionEnabled(href: String, enabled: Boolean) {
        val kind = if (enabled) {
            CalendarStructuralMutation.EnableCalendar
        } else {
            CalendarStructuralMutation.DisableCalendar
        }
        runStructuralMutation(kind) {
            repository.setCollectionEnabled(href, enabled)
        }
    }

    /**
     * Shows a calendar's new name and color at once, then sends them to its server. The server
     * step waits behind any running refresh; the app does not.
     */
    fun updateCollectionAppearance(href: String, displayName: String, customColor: Int?) {
        scope.launch {
            val saved = runCatching { repository.updateCollectionAppearanceLocally(href, displayName, customColor) }
                .onFailure { message.value = it.message ?: "Could not update calendar settings." }
                .isSuccess
            if (saved) {
                runStructuralMutation(
                    CalendarStructuralMutation.EditCalendar,
                    failureMessage = { strings.get(R.string.calendar_appearance_saved_on_device_only) },
                ) {
                    repository.pushCollectionAppearance(href)
                }
            }
        }
    }

    fun createCalDavCalendar(
        accountId: String,
        displayName: String,
        supportsEvents: Boolean,
        supportsTasks: Boolean,
    ) {
        runStructuralMutation(CalendarStructuralMutation.AddCalendar) {
            repository.createCalDavCalendar(
                accountId = accountId,
                displayName = displayName,
                color = null,
                supportsEvents = supportsEvents,
                supportsTasks = supportsTasks,
            )
        }
    }

    fun deleteCalDavCalendar(href: String) {
        runStructuralMutation(CalendarStructuralMutation.RemoveCalendar) {
            repository.deleteCalDavCalendar(href)
        }
    }

    internal suspend fun includeDisabledAndroidProviderCalendars(): Boolean =
        settingsStore.showDisabledAndroidProviderCalendars.first()

    internal suspend fun refreshAndroidProviderDiagnosticsInternal() {
        transient.hiddenAndroidProviderCalendarNames.value = repository.hiddenOrNotSyncedAndroidCalendars()
    }

    private fun runBusy(rescheduleReminders: Boolean = false, showManualSync: Boolean = false, block: suspend () -> Unit) {
        scope.launch {
            busy.value = true
            if (showManualSync) manualSyncing.value = true
            runCatching { block() }
                .onSuccess { message.value = null }
                .onFailure { message.value = it.message ?: "Something went wrong." }
            if (rescheduleReminders) {
                runCatching { reminderRescheduler.reschedule() }
            }
            if (showManualSync) manualSyncing.value = false
            busy.value = false
        }
    }

    private fun runStructuralMutation(
        kind: CalendarStructuralMutation,
        showManualSync: Boolean = false,
        failureMessage: (Throwable) -> String = { it.message ?: "Could not update calendar settings." },
        mutation: suspend () -> Unit,
    ) {
        scope.launch {
            busy.value = true
            if (showManualSync) manualSyncing.value = true
            runCatching {
                sourceCalendarMutationCoordinator.run(kind, mutation = mutation)
            }.onSuccess { result ->
                refreshAndroidProviderDiagnosticsInternal()
                message.value = structuralMutationMessage(result)
            }.onFailure {
                message.value = failureMessage(it)
            }
            if (showManualSync) manualSyncing.value = false
            busy.value = false
        }
    }

    private fun structuralMutationMessage(
        result: StructuralMutationResult,
    ): String? = when (result) {
        // Only problems are reported; a change that worked shows itself.
        StructuralMutationResult.Complete -> null
        is StructuralMutationResult.SavedWithFollowUpFailure -> when (result.stage) {
            PostMutationStage.Refresh -> strings.get(R.string.calendar_settings_saved_refresh_failed)
            PostMutationStage.Reconciliation -> strings.get(R.string.calendar_settings_saved_reconciliation_failed)
        }
    }
}
