@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import com.kgs.calendar.ui.platform.deviceCalendarsName
import com.kgs.calendar.time.*
import kotlinx.datetime.*
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
import com.kgs.calendar.ui.res.stringResource
import com.kgs.calendar.ui.res.Res
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
import kotlinx.datetime.DayOfWeek
import com.kgs.calendar.time.AppLocale as Locale

@Composable
fun CalendarViewMode.localizedLabel(weekViewEnabled: Boolean = false): String =
    appString(labelRes(weekViewEnabled))

fun CalendarViewMode.settingsIcon(): ImageVector = when (this) {
    CalendarViewMode.Agenda -> Icons.Default.ViewAgenda
    CalendarViewMode.Day -> Icons.Default.ViewDay
    CalendarViewMode.ThreeDay -> Icons.Default.ViewWeek
    CalendarViewMode.Month -> Icons.Default.CalendarMonth
    CalendarViewMode.Tasks -> Icons.Default.TaskAlt
}


@Composable
fun DayOfWeek.localizedWeekdayLabel(): String =
    when (this) {
        DayOfWeek.MONDAY -> appString(Res.string.week_monday)
        DayOfWeek.TUESDAY -> appString(Res.string.week_tuesday)
        DayOfWeek.WEDNESDAY -> appString(Res.string.week_wednesday)
        DayOfWeek.THURSDAY -> appString(Res.string.week_thursday)
        DayOfWeek.FRIDAY -> appString(Res.string.week_friday)
        DayOfWeek.SATURDAY -> appString(Res.string.week_saturday)
        DayOfWeek.SUNDAY -> appString(Res.string.week_sunday)
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
fun AppThemeMode.localizedLabel(): String = when (this) {
    AppThemeMode.KgsBlue -> appString(Res.string.kgs_blue)
    AppThemeMode.KgsWarm -> appString(Res.string.kgs_warm)
    AppThemeMode.KgsFresh -> appString(Res.string.kgs_fresh)
    AppThemeMode.SystemDynamic -> appString(Res.string.android_colors)
}

@Composable
fun AppColorMode.localizedLabel(): String = when (this) {
    AppColorMode.Auto -> appString(Res.string.auto)
    AppColorMode.Light -> appString(Res.string.light)
    AppColorMode.Dark -> appString(Res.string.dark)
}

@Composable
fun WidgetThemeMode.localizedLabel(): String = when (this) {
    WidgetThemeMode.FollowApp -> appString(Res.string.follow_app)
    WidgetThemeMode.KgsBlue -> appString(Res.string.kgs_blue)
    WidgetThemeMode.KgsWarm -> appString(Res.string.kgs_warm)
    WidgetThemeMode.KgsFresh -> appString(Res.string.kgs_fresh)
    WidgetThemeMode.SystemDynamic -> appString(Res.string.android_colors)
}

@Composable
fun WidgetColorMode.localizedLabel(): String = when (this) {
    WidgetColorMode.FollowApp -> appString(Res.string.follow_app)
    WidgetColorMode.FollowOs -> appString(Res.string.follow_os)
    WidgetColorMode.Light -> appString(Res.string.light)
    WidgetColorMode.Dark -> appString(Res.string.dark)
}

@Composable
fun WidgetTaskDisplayMode.localizedLabel(): String = when (this) {
    WidgetTaskDisplayMode.Planned -> appString(Res.string.planned_tasks)
    WidgetTaskDisplayMode.Unplanned -> appString(Res.string.unplanned_tasks)
    WidgetTaskDisplayMode.Today -> appString(Res.string.tasks_for_today)
}

@Composable
fun WidgetTaskCreateMode.localizedLabel(): String = when (this) {
    WidgetTaskCreateMode.Today -> appString(Res.string.create_task_for_today)
    WidgetTaskCreateMode.Unplanned -> appString(Res.string.create_unplanned_task)
}

@Composable
fun WidgetTaskSubtaskDefaultMode.localizedLabel(): String = when (this) {
    WidgetTaskSubtaskDefaultMode.FollowApp -> appString(Res.string.follow_app)
    WidgetTaskSubtaskDefaultMode.Open -> appString(Res.string.subtasks_default_open)
    WidgetTaskSubtaskDefaultMode.Closed -> appString(Res.string.subtasks_default_closed)
}

@Composable
fun AppLanguageMode.localizedLabel(): String = when (this) {
    AppLanguageMode.System -> appString(Res.string.follow_system)
    AppLanguageMode.English -> appString(Res.string.english)
    AppLanguageMode.German -> appString(Res.string.german)
}

@Composable
fun TaskEntity.localizedStatusLabel(): String = when (status?.uppercase()) {
    "IN-PROCESS" -> appString(Res.string.in_progress)
    "CANCELLED" -> appString(Res.string.aborted)
    "COMPLETED" -> appString(Res.string.status_completed)
    "NEEDS-ACTION" -> appString(Res.string.status_open)
    null -> if (isCompleted) appString(Res.string.status_completed) else appString(Res.string.status_open)
    else -> status!!
}


private fun CollectionEntity.typeLabel(): String = when {
    supportsEvents && supportsTasks -> "Events and tasks"
    supportsTasks -> "Tasks"
    else -> "Events"
}


private fun String.participantRoleLabel(): String =
    ParticipantRoleOption.entries.firstOrNull { it.value.equals(this, ignoreCase = true) }?.label ?: this

private fun String.eventStatusLabel(): String = when (uppercase()) {
    "TENTATIVE" -> "Tentative"
    "CANCELLED" -> "Cancelled"
    else -> "Confirmed"
}

@Composable
fun EventEntity.statusLabelText(): String = when ((status ?: EventStatusOption.Confirmed.value).uppercase()) {
    "TENTATIVE" -> appString(Res.string.tentative)
    "CANCELLED" -> appString(Res.string.cancelled)
    else -> appString(Res.string.confirmed)
}

private fun String.eventClassLabel(): String = when (uppercase()) {
    "CONFIDENTIAL" -> "Show busy status when shared"
    "PRIVATE" -> "Hide this event when shared"
    else -> "Show full event when shared"
}

@Composable
fun EventEntity.classLabelText(): String = when ((classification ?: EventClassOption.Public.value).uppercase()) {
    "CONFIDENTIAL" -> appString(Res.string.busy_only)
    "PRIVATE" -> appString(Res.string.hidden)
    else -> appString(Res.string.full_details)
}

private fun String.eventTransparencyLabel(): String = when (uppercase()) {
    "TRANSPARENT" -> "Free"
    else -> "Busy"
}

@Composable
fun EventEntity.transparencyLabelText(): String = when ((transparency ?: EventTransparencyOption.Busy.value).uppercase()) {
    "TRANSPARENT" -> appString(Res.string.free)
    else -> appString(Res.string.busy)
}

private fun String.participationLabel(): String = when (uppercase()) {
    "ACCEPTED" -> "Accepted"
    "DECLINED" -> "Declined"
    "TENTATIVE" -> "Tentative"
    "DELEGATED" -> "Delegated"
    else -> "No response"
}

@Composable
private fun String.participationColor(): Color = when (uppercase()) {
    "ACCEPTED" -> Color(0xFF00A86B)
    "DECLINED" -> Color(0xFFE53935)
    "TENTATIVE" -> Color(0xFFFFA000)
    "DELEGATED" -> Color(0xFF5B6CFF)
    else -> Color(0xFF6B7280)
}

private fun CalendarParticipant.displayParticipationLabel(): String =
    deliveryStatusLabel() ?: partstat.participationLabel()

@Composable
fun CalendarParticipant.localizedDisplayParticipationLabel(): String =
    localizedDeliveryStatusLabel() ?: when (partstat.uppercase()) {
        "ACCEPTED" -> appString(Res.string.accept)
        "DECLINED" -> appString(Res.string.decline)
        "TENTATIVE" -> appString(Res.string.tentative)
        "DELEGATED" -> appString(Res.string.delegated)
        else -> appString(Res.string.no_response)
    }

@Composable
fun CalendarParticipant.displayParticipationColor(): Color =
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
fun CalendarParticipant.deliveryStatusColor(): Color =
    when {
        scheduleStatus.orEmpty().startsWith("1.") -> Color(0xFF4DA3FF)
        else -> Color(0xFFE53935)
    }



@Composable
fun PlannedTaskSort.localizedLabel(): String = when (this) {
    PlannedTaskSort.Date -> appString(Res.string.date)
    PlannedTaskSort.Priority -> appString(Res.string.priority)
    PlannedTaskSort.Status -> appString(Res.string.status)
}


@Composable
fun TaskDefaultDates.localizedLabel(): String = when (this) {
    TaskDefaultDates.None -> appString(Res.string.no_date)
    TaskDefaultDates.Start -> appString(Res.string.task_dates_start)
    TaskDefaultDates.Due -> appString(Res.string.task_dates_end)
    TaskDefaultDates.StartAndDue -> appString(Res.string.task_dates_both)
}


@Composable
fun DurationUnit.localizedLabel(): String = when (this) {
    DurationUnit.Minutes -> appString(Res.string.minutes)
    DurationUnit.Hours -> appString(Res.string.hours)
}


@Composable
fun Int.localizedDurationLabel(): String =
    if (this < 60) {
        stringResource(Res.string.duration_minutes, this)
    } else {
        val hoursText = stringResource(Res.string.duration_hours, this / 60)
        if (this % 60 == 0) hoursText else "$hoursText ${stringResource(Res.string.duration_minutes, this % 60)}"
    }


@Composable
fun CollectionEntity.localizedPermissionMetadataRows(): List<Pair<String, String>> {
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
            stringResource(Res.string.calendar_access_read_only),
            androidAccessLabel,
        ).joinToString(" · ")
        isAndroidProviderCollection() && androidAccessLabel != null -> stringResource(Res.string.calendar_access_writable_level, androidAccessLabel)
        readOnly -> stringResource(Res.string.calendar_access_read_only)
        else -> stringResource(Res.string.calendar_access_full)
    }
    val identifier = externalId?.takeIf { it.isNotBlank() } ?: href
    return buildList {
        add(stringResource(Res.string.source) to localizedSourceTypeLabel())
        add(stringResource(Res.string.calendar_access) to accessLabel)
        add(stringResource(Res.string.calendar_writes) to if (isReadOnlyCollection()) stringResource(Res.string.calendar_writes_not_allowed) else stringResource(Res.string.calendar_writes_allowed))
        add(stringResource(Res.string.calendar_sync_state) to if (isEnabled) stringResource(Res.string.calendar_active) else stringResource(Res.string.calendar_inactive))
        if (isAndroidProviderCollection()) {
            add(stringResource(Res.string.calendar_device_visibility) to androidVisible.localizedVisibleHidden())
            add(stringResource(Res.string.calendar_provider_sync) to androidSyncEvents.localizedSupportedUnsupported())
        } else if (sourceType == SourceType.CalDav) {
            add(stringResource(Res.string.caldav_edit_events) to calDavCanWriteContent.localizedSupportedUnsupported(defaultSupported = !readOnly))
            add(stringResource(Res.string.caldav_edit_calendar) to calDavCanWriteProperties.localizedSupportedUnsupported(defaultSupported = !readOnly))
            add(stringResource(Res.string.caldav_create_items) to calDavCanCreate.localizedSupportedUnsupported(defaultSupported = !readOnly))
            add(stringResource(Res.string.caldav_delete_items) to calDavCanDelete.localizedSupportedUnsupported(defaultSupported = !readOnly))
            add(stringResource(Res.string.caldav_free_busy) to calDavCanReadFreeBusy.localizedSupportedUnsupported(defaultSupported = true))
            add(stringResource(Res.string.caldav_incremental_sync) to calDavIncrementalSync.localizedSupportedUnsupported())
        }
        add(stringResource(Res.string.calendar_reminder_permission) to reminders.localizedSupportedUnsupported(defaultSupported = true))
        availability?.let { add(stringResource(Res.string.calendar_availability_values) to it) }
        add(stringResource(Res.string.calendar_identifier) to identifier)
        add(stringResource(Res.string.calendar_sync_token) to (syncToken ?: stringResource(Res.string.calendar_unknown)))
    }
}

@Composable
private fun CollectionEntity.localizedSourceTypeLabel(): String = when {
    href.isLocalCollectionHref() || sourceType == SourceType.Local -> stringResource(Res.string.local_calendar)
    isAndroidProviderCollection() -> stringResource(deviceCalendarsName)
    href.isReadOnlyCollectionHref() || sourceType == SourceType.ReadOnlyUrl -> stringResource(Res.string.read_only_url)
    else -> stringResource(Res.string.caldav)
}

@Composable
private fun Int.localizedAndroidAccessLevel(): String = when (this) {
    0 -> stringResource(Res.string.calendar_android_access_none)
    100 -> stringResource(Res.string.calendar_android_access_freebusy)
    200 -> stringResource(Res.string.calendar_android_access_read)
    300 -> stringResource(Res.string.calendar_android_access_respond)
    400 -> stringResource(Res.string.calendar_android_access_override)
    500 -> stringResource(Res.string.calendar_android_access_contributor)
    600 -> stringResource(Res.string.calendar_android_access_editor)
    700 -> stringResource(Res.string.calendar_android_access_owner)
    800 -> stringResource(Res.string.calendar_android_access_root)
    else -> this.toString()
}

@Composable
fun Boolean?.localizedSupportedUnsupported(defaultSupported: Boolean? = null): String =
    when (this ?: defaultSupported) {
        true -> stringResource(Res.string.calendar_supported)
        false -> stringResource(Res.string.calendar_unsupported)
        null -> stringResource(Res.string.calendar_unknown)
    }

@Composable
private fun Boolean?.localizedVisibleHidden(): String =
    when (this) {
        true -> stringResource(Res.string.calendar_visible)
        false -> stringResource(Res.string.calendar_hidden)
        null -> stringResource(Res.string.calendar_unknown)
    }


private fun CollectionEntity.kindLabel(): String = buildList {
    if (supportsEvents) add("Events")
    if (supportsTasks) add("Tasks")
}.joinToString(" & ").ifBlank { "Calendar" }

@Composable
fun CollectionEntity.localizedKindLabel(): String = buildList {
    if (supportsEvents) add(stringResource(Res.string.events))
    if (supportsTasks) add(stringResource(Res.string.tasks))
}.joinToString(" & ").ifBlank { stringResource(Res.string.calendar) }
