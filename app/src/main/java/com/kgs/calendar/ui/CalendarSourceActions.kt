package com.kgs.calendar.ui

import com.kgs.calendar.R
import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.sync.CalendarStructuralMutation
import com.kgs.calendar.sync.PostMutationStage
import com.kgs.calendar.sync.SourceCalendarMutationCoordinator
import com.kgs.calendar.sync.StructuralMutationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
            val resultMessage = structuralMutationMessage(saved.getOrThrow(), "CalDAV account synced.")
            message.value = resultMessage
            busy.value = false
        }
    }

    fun startBrowserLogin(serverUrl: String) {
        runStructuralMutation(CalendarStructuralMutation.AddSource, "Login complete.") {
            val login = repository.startLoginFlow(serverUrl)
            transient.externalLoginUrl.value = login.loginUrl
            repository.completeLoginFlow(login.pollEndpoint, login.token)
        }
    }

    fun addReadOnlyCalendar(url: String) {
        runStructuralMutation(
            CalendarStructuralMutation.AddSource,
            "Read-only calendar added.",
            showManualSync = true,
            // A failed subscription leaves no source behind, so this notice is the only place the error shows up.
            failureMessage = { "Adding the read-only calendar failed. ${it.message ?: it::class.java.simpleName}" },
        ) {
            repository.addReadOnlyCalendar(url)
        }
    }

    fun addAndroidDeviceCalendars() {
        runStructuralMutation(CalendarStructuralMutation.AddSource, "Android device calendars added.", showManualSync = true) {
            repository.enableAndroidCalendars(
                includeDisabledProviderCalendars = includeDisabledAndroidProviderCalendars(),
            )
            appLifecycleSignals.registerAndroidCalendarObserverIfPermitted()
        }
    }

    fun renameAccount(accountId: String, displayName: String) {
        runStructuralMutation(CalendarStructuralMutation.EditSource, "Source renamed.") {
            repository.renameAccount(accountId, displayName)
        }
    }

    fun updateAccount(accountId: String, displayName: String, serverUrl: String, username: String, appPassword: String?) {
        runStructuralMutation(CalendarStructuralMutation.EditSource, "Source updated.") {
            repository.updateAccount(accountId, displayName, serverUrl, username, appPassword)
        }
    }

    fun deleteAccount(accountId: String) {
        runStructuralMutation(CalendarStructuralMutation.RemoveSource, "Source removed.") {
            repository.deleteAccount(accountId)
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
            message.value = "Sync complete."
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

    fun updateCollectionAppearance(href: String, displayName: String, customColor: Int?) {
        runStructuralMutation(CalendarStructuralMutation.EditCalendar) {
            repository.updateCollectionAppearance(href, displayName, customColor)
        }
    }

    fun createCalDavCalendar(
        accountId: String,
        displayName: String,
        supportsEvents: Boolean,
        supportsTasks: Boolean,
    ) {
        runStructuralMutation(CalendarStructuralMutation.AddCalendar, "CalDAV calendar created.") {
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
        runStructuralMutation(CalendarStructuralMutation.RemoveCalendar, "CalDAV calendar deleted.") {
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
        successMessage: String? = null,
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
                message.value = structuralMutationMessage(result, successMessage)
            }.onFailure {
                message.value = failureMessage(it)
            }
            if (showManualSync) manualSyncing.value = false
            busy.value = false
        }
    }

    private fun structuralMutationMessage(
        result: StructuralMutationResult,
        successMessage: String?,
    ): String? = when (result) {
        StructuralMutationResult.Complete -> successMessage
        is StructuralMutationResult.SavedWithFollowUpFailure -> when (result.stage) {
            PostMutationStage.Refresh -> strings.get(R.string.calendar_settings_saved_refresh_failed)
            PostMutationStage.Reconciliation -> strings.get(R.string.calendar_settings_saved_reconciliation_failed)
        }
    }
}
