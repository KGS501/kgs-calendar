@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import com.kgs.calendar.data.settings.TaskDefaultDates
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.kgs.calendar.R
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppLanguageMode
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.WidgetColorMode
import com.kgs.calendar.data.settings.WidgetTaskCreateMode
import com.kgs.calendar.data.settings.WidgetTaskDisplayMode
import com.kgs.calendar.data.settings.WidgetTaskSubtaskDefaultMode
import com.kgs.calendar.data.settings.WidgetThemeMode
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.source.isAndroidProviderCollection
import com.kgs.calendar.domain.source.isLocalCollectionHref
import com.kgs.calendar.domain.source.isReadOnlyCollection
import com.kgs.calendar.domain.source.isReadOnlyCollectionHref
import java.time.DayOfWeek
import java.util.Locale

@Composable
internal fun CalendarViewMode.localizedLabel(weekViewEnabled: Boolean = false): String =
    appString(labelRes(weekViewEnabled))

internal fun CalendarViewMode.settingsIcon(): ImageVector = when (this) {
    CalendarViewMode.Agenda -> Icons.Default.ViewAgenda
    CalendarViewMode.Day -> Icons.Default.ViewDay
    CalendarViewMode.ThreeDay -> Icons.Default.ViewWeek
    CalendarViewMode.Month -> Icons.Default.CalendarMonth
    CalendarViewMode.Tasks -> Icons.Default.TaskAlt
}


@Composable
internal fun DayOfWeek.localizedWeekdayLabel(): String =
    when (this) {
        DayOfWeek.MONDAY -> appString(R.string.week_monday)
        DayOfWeek.TUESDAY -> appString(R.string.week_tuesday)
        DayOfWeek.WEDNESDAY -> appString(R.string.week_wednesday)
        DayOfWeek.THURSDAY -> appString(R.string.week_thursday)
        DayOfWeek.FRIDAY -> appString(R.string.week_friday)
        DayOfWeek.SATURDAY -> appString(R.string.week_saturday)
        DayOfWeek.SUNDAY -> appString(R.string.week_sunday)
    }


private fun TaskEntity.statusLabel(): String = when (status?.uppercase()) {
    "IN-PROCESS" -> "In progress"
    "CANCELLED" -> "Cancelled"
    "COMPLETED" -> "Completed"
    "NEEDS-ACTION" -> "Open"
    null -> if (isCompleted) "Completed" else "Open"
    else -> status!!
}

@Composable
internal fun AppThemeMode.localizedLabel(): String = when (this) {
    AppThemeMode.KgsBlue -> appString(R.string.kgs_blue)
    AppThemeMode.KgsWarm -> appString(R.string.kgs_warm)
    AppThemeMode.KgsFresh -> appString(R.string.kgs_fresh)
    AppThemeMode.SystemDynamic -> appString(R.string.android_colors)
}

@Composable
internal fun AppColorMode.localizedLabel(): String = when (this) {
    AppColorMode.Auto -> appString(R.string.auto)
    AppColorMode.Light -> appString(R.string.light)
    AppColorMode.Dark -> appString(R.string.dark)
}

@Composable
internal fun WidgetThemeMode.localizedLabel(): String = when (this) {
    WidgetThemeMode.FollowApp -> appString(R.string.follow_app)
    WidgetThemeMode.KgsBlue -> appString(R.string.kgs_blue)
    WidgetThemeMode.KgsWarm -> appString(R.string.kgs_warm)
    WidgetThemeMode.KgsFresh -> appString(R.string.kgs_fresh)
    WidgetThemeMode.SystemDynamic -> appString(R.string.android_colors)
}

@Composable
internal fun WidgetColorMode.localizedLabel(): String = when (this) {
    WidgetColorMode.FollowApp -> appString(R.string.follow_app)
    WidgetColorMode.FollowOs -> appString(R.string.follow_os)
    WidgetColorMode.Light -> appString(R.string.light)
    WidgetColorMode.Dark -> appString(R.string.dark)
}

@Composable
internal fun WidgetTaskDisplayMode.localizedLabel(): String = when (this) {
    WidgetTaskDisplayMode.Planned -> appString(R.string.planned_tasks)
    WidgetTaskDisplayMode.Unplanned -> appString(R.string.unplanned_tasks)
    WidgetTaskDisplayMode.Today -> appString(R.string.tasks_for_today)
}

@Composable
internal fun WidgetTaskCreateMode.localizedLabel(): String = when (this) {
    WidgetTaskCreateMode.Today -> appString(R.string.create_task_for_today)
    WidgetTaskCreateMode.Unplanned -> appString(R.string.create_unplanned_task)
}

@Composable
internal fun WidgetTaskSubtaskDefaultMode.localizedLabel(): String = when (this) {
    WidgetTaskSubtaskDefaultMode.FollowApp -> appString(R.string.follow_app)
    WidgetTaskSubtaskDefaultMode.Open -> appString(R.string.subtasks_default_open)
    WidgetTaskSubtaskDefaultMode.Closed -> appString(R.string.subtasks_default_closed)
}

@Composable
internal fun AppLanguageMode.localizedLabel(): String = when (this) {
    AppLanguageMode.System -> appString(R.string.follow_system)
    AppLanguageMode.English -> appString(R.string.english)
    AppLanguageMode.German -> appString(R.string.german)
}

@Composable
internal fun TaskEntity.localizedStatusLabel(): String = when (status?.uppercase()) {
    "IN-PROCESS" -> appString(R.string.in_progress)
    "CANCELLED" -> appString(R.string.aborted)
    "COMPLETED" -> appString(R.string.status_completed)
    "NEEDS-ACTION" -> appString(R.string.status_open)
    null -> if (isCompleted) appString(R.string.status_completed) else appString(R.string.status_open)
    else -> status!!
}


private fun CollectionEntity.typeLabel(): String = when {
    supportsEvents && supportsTasks -> "Events and tasks"
    supportsTasks -> "Tasks"
    else -> "Events"
}


private fun String.participantRoleLabel(): String =
    ParticipantRoleOption.entries.firstOrNull { it.value.equals(this, ignoreCase = true) }?.label ?: this

private fun String.eventStatusLabel(): String = when (uppercase(Locale.US)) {
    "TENTATIVE" -> "Tentative"
    "CANCELLED" -> "Cancelled"
    else -> "Confirmed"
}

@Composable
internal fun EventEntity.statusLabelText(): String = when ((status ?: EventStatusOption.Confirmed.value).uppercase(Locale.US)) {
    "TENTATIVE" -> appString(R.string.tentative)
    "CANCELLED" -> appString(R.string.cancelled)
    else -> appString(R.string.confirmed)
}

private fun String.eventClassLabel(): String = when (uppercase(Locale.US)) {
    "CONFIDENTIAL" -> "Show busy status when shared"
    "PRIVATE" -> "Hide this event when shared"
    else -> "Show full event when shared"
}

@Composable
internal fun EventEntity.classLabelText(): String = when ((classification ?: EventClassOption.Public.value).uppercase(Locale.US)) {
    "CONFIDENTIAL" -> appString(R.string.busy_only)
    "PRIVATE" -> appString(R.string.hidden)
    else -> appString(R.string.full_details)
}

private fun String.eventTransparencyLabel(): String = when (uppercase(Locale.US)) {
    "TRANSPARENT" -> "Free"
    else -> "Busy"
}

@Composable
internal fun EventEntity.transparencyLabelText(): String = when ((transparency ?: EventTransparencyOption.Busy.value).uppercase(Locale.US)) {
    "TRANSPARENT" -> appString(R.string.free)
    else -> appString(R.string.busy)
}

private fun String.participationLabel(): String = when (uppercase(Locale.US)) {
    "ACCEPTED" -> "Accepted"
    "DECLINED" -> "Declined"
    "TENTATIVE" -> "Tentative"
    "DELEGATED" -> "Delegated"
    else -> "No response"
}

@Composable
private fun String.participationColor(): Color = when (uppercase(Locale.US)) {
    "ACCEPTED" -> Color(0xFF00A86B)
    "DECLINED" -> Color(0xFFE53935)
    "TENTATIVE" -> Color(0xFFFFA000)
    "DELEGATED" -> Color(0xFF5B6CFF)
    else -> Color(0xFF6B7280)
}

private fun CalendarParticipant.displayParticipationLabel(): String =
    deliveryStatusLabel() ?: partstat.participationLabel()

@Composable
internal fun CalendarParticipant.localizedDisplayParticipationLabel(): String =
    localizedDeliveryStatusLabel() ?: when (partstat.uppercase(Locale.US)) {
        "ACCEPTED" -> appString(R.string.accept)
        "DECLINED" -> appString(R.string.decline)
        "TENTATIVE" -> appString(R.string.tentative)
        "DELEGATED" -> appString(R.string.delegated)
        else -> appString(R.string.no_response)
    }

@Composable
internal fun CalendarParticipant.displayParticipationColor(): Color =
    if (deliveryStatusLabel() != null) deliveryStatusColor() else partstat.participationColor()

private fun CalendarParticipant.deliveryStatusLabel(): String? {
    val code = scheduleStatus
        ?.substringBefore(',')
        ?.substringBefore(';')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return when {
        code == "1.0" -> "Preparing invitation"
        code == "1.1" -> "Invitation sent"
        code == "1.2" || code == "2.0" -> null
        code == "3.7" -> "Invitation could not be delivered"
        code.startsWith("3.") -> "Invitation could not be delivered"
        code.startsWith("5.") -> "Invitation could not be delivered"
        else -> null
    }
}

@Composable
internal fun CalendarParticipant.deliveryStatusColor(): Color =
    when {
        scheduleStatus.orEmpty().startsWith("1.") -> Color(0xFF4DA3FF)
        else -> Color(0xFFE53935)
    }



@Composable
internal fun PlannedTaskSort.localizedLabel(): String = when (this) {
    PlannedTaskSort.Date -> appString(R.string.date)
    PlannedTaskSort.Priority -> appString(R.string.priority)
    PlannedTaskSort.Status -> appString(R.string.status)
}


@Composable
internal fun TaskDefaultDates.localizedLabel(): String = when (this) {
    TaskDefaultDates.None -> appString(R.string.no_date)
    TaskDefaultDates.Start -> appString(R.string.task_dates_start)
    TaskDefaultDates.Due -> appString(R.string.task_dates_end)
    TaskDefaultDates.StartAndDue -> appString(R.string.task_dates_both)
}


@Composable
internal fun DurationUnit.localizedLabel(): String = when (this) {
    DurationUnit.Minutes -> appString(R.string.minutes)
    DurationUnit.Hours -> appString(R.string.hours)
}


@Composable
internal fun Int.localizedDurationLabel(): String =
    if (this < 60) {
        stringResource(R.string.duration_minutes, this)
    } else {
        val hoursText = stringResource(R.string.duration_hours, this / 60)
        if (this % 60 == 0) hoursText else "$hoursText ${stringResource(R.string.duration_minutes, this % 60)}"
    }


@Composable
internal fun CollectionEntity.localizedPermissionMetadataRows(): List<Pair<String, String>> {
    val capabilities = capabilitiesJson.toJsonObjectOrNull()
    val androidAccessLevel = capabilities?.optIntOrNull("androidAccessLevel")
    val androidVisible = capabilities?.optBooleanOrNull("androidVisible")
    val androidSyncEvents = capabilities?.optBooleanOrNull("androidSyncEvents")
    val reminders = capabilities?.optBooleanOrNull("reminders")
    val availability = capabilities?.optStringOrNull("androidAvailability")?.takeIf { it.isNotBlank() }
    val androidAccessLabel = androidAccessLevel?.localizedAndroidAccessLevel()
    val calDavCanWriteContent = capabilities?.optBooleanOrNull("canWriteContent")
    val calDavCanWriteProperties = capabilities?.optBooleanOrNull("canWriteProperties")
    val calDavCanCreate = capabilities?.optBooleanOrNull("canCreateResources")
    val calDavCanDelete = capabilities?.optBooleanOrNull("canDeleteResources")
    val calDavCanReadFreeBusy = capabilities?.optBooleanOrNull("canReadFreeBusy")
    val calDavIncrementalSync = capabilities?.optBooleanOrNull("supportsSyncCollection")
    val accessLabel = when {
        isAndroidProviderCollection() && readOnly -> listOfNotNull(
            stringResource(R.string.calendar_access_read_only),
            androidAccessLabel,
        ).joinToString(" · ")
        isAndroidProviderCollection() && androidAccessLabel != null -> stringResource(R.string.calendar_access_writable_level, androidAccessLabel)
        readOnly -> stringResource(R.string.calendar_access_read_only)
        else -> stringResource(R.string.calendar_access_full)
    }
    val identifier = externalId?.takeIf { it.isNotBlank() } ?: href
    return buildList {
        add(stringResource(R.string.source) to localizedSourceTypeLabel())
        add(stringResource(R.string.calendar_access) to accessLabel)
        add(stringResource(R.string.calendar_writes) to if (isReadOnlyCollection()) stringResource(R.string.calendar_writes_not_allowed) else stringResource(R.string.calendar_writes_allowed))
        add(stringResource(R.string.calendar_sync_state) to if (isEnabled) stringResource(R.string.calendar_active) else stringResource(R.string.calendar_inactive))
        if (isAndroidProviderCollection()) {
            add(stringResource(R.string.calendar_device_visibility) to androidVisible.localizedVisibleHidden())
            add(stringResource(R.string.calendar_provider_sync) to androidSyncEvents.localizedSupportedUnsupported())
        } else if (sourceType == SourceType.CalDav) {
            add(stringResource(R.string.caldav_edit_events) to calDavCanWriteContent.localizedSupportedUnsupported(defaultSupported = !readOnly))
            add(stringResource(R.string.caldav_edit_calendar) to calDavCanWriteProperties.localizedSupportedUnsupported(defaultSupported = !readOnly))
            add(stringResource(R.string.caldav_create_items) to calDavCanCreate.localizedSupportedUnsupported(defaultSupported = !readOnly))
            add(stringResource(R.string.caldav_delete_items) to calDavCanDelete.localizedSupportedUnsupported(defaultSupported = !readOnly))
            add(stringResource(R.string.caldav_free_busy) to calDavCanReadFreeBusy.localizedSupportedUnsupported(defaultSupported = true))
            add(stringResource(R.string.caldav_incremental_sync) to calDavIncrementalSync.localizedSupportedUnsupported())
        }
        add(stringResource(R.string.calendar_reminder_permission) to reminders.localizedSupportedUnsupported(defaultSupported = true))
        availability?.let { add(stringResource(R.string.calendar_availability_values) to it) }
        add(stringResource(R.string.calendar_identifier) to identifier)
        add(stringResource(R.string.calendar_sync_token) to (syncToken ?: stringResource(R.string.calendar_unknown)))
    }
}

@Composable
private fun CollectionEntity.localizedSourceTypeLabel(): String = when {
    href.isLocalCollectionHref() || sourceType == SourceType.Local -> stringResource(R.string.local_calendar)
    isAndroidProviderCollection() -> stringResource(R.string.android_device_calendars)
    href.isReadOnlyCollectionHref() || sourceType == SourceType.ReadOnlyUrl -> stringResource(R.string.read_only_url)
    else -> stringResource(R.string.caldav)
}

@Composable
private fun Int.localizedAndroidAccessLevel(): String = when (this) {
    0 -> stringResource(R.string.calendar_android_access_none)
    100 -> stringResource(R.string.calendar_android_access_freebusy)
    200 -> stringResource(R.string.calendar_android_access_read)
    300 -> stringResource(R.string.calendar_android_access_respond)
    400 -> stringResource(R.string.calendar_android_access_override)
    500 -> stringResource(R.string.calendar_android_access_contributor)
    600 -> stringResource(R.string.calendar_android_access_editor)
    700 -> stringResource(R.string.calendar_android_access_owner)
    800 -> stringResource(R.string.calendar_android_access_root)
    else -> this.toString()
}

@Composable
internal fun Boolean?.localizedSupportedUnsupported(defaultSupported: Boolean? = null): String =
    when (this ?: defaultSupported) {
        true -> stringResource(R.string.calendar_supported)
        false -> stringResource(R.string.calendar_unsupported)
        null -> stringResource(R.string.calendar_unknown)
    }

@Composable
private fun Boolean?.localizedVisibleHidden(): String =
    when (this) {
        true -> stringResource(R.string.calendar_visible)
        false -> stringResource(R.string.calendar_hidden)
        null -> stringResource(R.string.calendar_unknown)
    }


private fun CollectionEntity.kindLabel(): String = buildList {
    if (supportsEvents) add("Events")
    if (supportsTasks) add("Tasks")
}.joinToString(" & ").ifBlank { "Calendar" }

@Composable
internal fun CollectionEntity.localizedKindLabel(): String = buildList {
    if (supportsEvents) add(stringResource(R.string.events))
    if (supportsTasks) add(stringResource(R.string.tasks))
}.joinToString(" & ").ifBlank { stringResource(R.string.calendar) }
