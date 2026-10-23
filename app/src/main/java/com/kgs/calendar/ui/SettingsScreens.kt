package com.kgs.calendar.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.kgs.calendar.R
import com.kgs.calendar.data.isSupportedReadOnlyCalendarUrl
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppLanguageMode
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.data.settings.TaskDefaultDates
import com.kgs.calendar.data.settings.WidgetColorMode
import com.kgs.calendar.data.settings.WidgetTaskCreateMode
import com.kgs.calendar.data.settings.WidgetTaskDisplayMode
import com.kgs.calendar.data.settings.WidgetTaskSubtaskDefaultMode
import com.kgs.calendar.data.settings.WidgetThemeMode
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.MAX_MULTI_DAY_COUNT
import com.kgs.calendar.domain.model.MIN_MULTI_DAY_COUNT
import com.kgs.calendar.domain.model.MultiDayCountBucket
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.model.coerceMultiDayCount
import com.kgs.calendar.domain.source.CalendarSourceGroupKind
import com.kgs.calendar.domain.source.CalendarSourceGroups
import com.kgs.calendar.domain.source.ReadOnlyFeedStatus
import com.kgs.calendar.domain.source.isAndroidProviderAccount
import com.kgs.calendar.domain.source.isAndroidProviderCollection
import com.kgs.calendar.domain.source.isLocalCollectionHref
import com.kgs.calendar.domain.source.isReadOnlyCollection
import com.kgs.calendar.domain.source.readOnlyFeedStatus
import com.kgs.calendar.ui.layout.SettingsContentMaxWidth
import com.kgs.calendar.ui.layout.centeredMaxWidth
import com.kgs.calendar.ui.layout.currentCalendarWindowLayout
import com.kgs.calendar.ui.layout.largeScreenMaxWidth
import com.kgs.calendar.ui.sources.SourceAvatar
import com.kgs.calendar.ui.sources.displayName
import com.kgs.calendar.data.account.SourceImageKeys
import com.kgs.calendar.ui.sources.AccountAvatarDialog
import com.kgs.calendar.ui.sources.avatarColor
import com.kgs.calendar.ui.sources.shownName
import com.kgs.calendar.ui.sources.image
import com.kgs.calendar.ui.sources.label
import com.kgs.calendar.ui.sources.subtitle
import org.json.JSONObject
import java.time.DayOfWeek
import com.kgs.calendar.ui.components.ClearableOutlinedTextField as OutlinedTextField

@Composable
internal fun SettingsPage(
    state: CalendarUiState,
    initialDestination: SettingsDestination = SettingsDestination.Main,
    onViewSelected: (CalendarViewMode) -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
    onColorModeSelected: (AppColorMode) -> Unit,
    onMonthWidgetThemeSelected: (WidgetThemeMode) -> Unit,
    onMonthWidgetColorModeSelected: (WidgetColorMode) -> Unit,
    onAgendaWidgetThemeSelected: (WidgetThemeMode) -> Unit,
    onAgendaWidgetColorModeSelected: (WidgetColorMode) -> Unit,
    onTasksWidgetThemeSelected: (WidgetThemeMode) -> Unit,
    onTasksWidgetColorModeSelected: (WidgetColorMode) -> Unit,
    onDayWidgetThemeSelected: (WidgetThemeMode) -> Unit,
    onDayWidgetColorModeSelected: (WidgetColorMode) -> Unit,
    onMultiWidgetThemeSelected: (WidgetThemeMode) -> Unit,
    onMultiWidgetColorModeSelected: (WidgetColorMode) -> Unit,
    onMultiWidgetMonthPercentChanged: (Int) -> Unit,
    onTasksWidgetDisplayModeSelected: (WidgetTaskDisplayMode) -> Unit,
    onTasksWidgetIncludeOverdueChanged: (Boolean) -> Unit,
    onTasksWidgetCreateModeSelected: (WidgetTaskCreateMode) -> Unit,
    onTasksWidgetSubtaskDefaultModeSelected: (WidgetTaskSubtaskDefaultMode) -> Unit,
    onDayWidgetScaleChanged: (Int) -> Unit,
    onDayWidgetStartHourChanged: (Int) -> Unit,
    onDayWidgetStartAtCurrentHourChanged: (Boolean) -> Unit,
    onLanguageSelected: (AppLanguageMode) -> Unit,
    onTaskColorModeSelected: (TaskColorMode) -> Unit,
    onPriorityAnimationsChanged: (Boolean) -> Unit,
    onOverdueSummaryPriorityAnimationChanged: (Boolean) -> Unit,
    onSubtasksExpandedByDefaultChanged: (Boolean) -> Unit,
    onAutoLoadMapPreviewsChanged: (Boolean) -> Unit,
    onHapticFeedbackChanged: (Boolean) -> Unit,
    onMaxVisibleAllDayItemsChanged: (Int) -> Unit,
    onMultiDaySidebarControlsChanged: (Boolean) -> Unit,
    onMultiDayCountChanged: (MultiDayCountBucket, Int) -> Unit,
    onWeekViewEnabledChanged: (Boolean) -> Unit,
    onFullWeekSwipeEnabledChanged: (Boolean) -> Unit,
    onFocusTitleOnCreateChanged: (Boolean) -> Unit,
    onFirstDayOfWeekSelected: (DayOfWeek) -> Unit,
    onShowCompletedTasksChanged: (Boolean) -> Unit,
    onShowCalendarWeeksChanged: (Boolean) -> Unit,
    onDefaultEventDurationChanged: (Int) -> Unit,
    onDefaultTaskDatesSelected: (TaskDefaultDates) -> Unit,
    onDefaultTaskHasTimeChanged: (Boolean) -> Unit,
    onDefaultEventRemindersChanged: (Set<Int>) -> Unit,
    onDefaultTaskRemindersChanged: (Set<Int>) -> Unit,
    onTaskStartNotificationsChanged: (Boolean) -> Unit,
    onTaskEndNotificationsChanged: (Boolean) -> Unit,
    onEventStartNotificationsChanged: (Boolean) -> Unit,
    onEventEndNotificationsChanged: (Boolean) -> Unit,
    onDefaultEventCollectionSelected: (String?) -> Unit,
    onDefaultTaskCollectionSelected: (String?) -> Unit,
    onEventFieldOrderChanged: (List<String>) -> Unit,
    onTaskFieldOrderChanged: (List<String>) -> Unit,
    onCollectionsReordered: (List<String>) -> Unit,
    onManualLogin: (String, String, String, (Boolean, String?) -> Unit) -> Unit,
    onBrowserLogin: (String) -> Unit,
    onAddReadOnlyCalendar: (String) -> Unit,
    onAddAndroidCalendars: () -> Unit,
    onDisabledAndroidProviderCalendarsVisibleChanged: (Boolean) -> Unit,
    onUpdateAccount: (String, String, String, String, String?) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onCreateCalDavCalendar: (String, String, Boolean, Boolean) -> Unit,
    onSync: () -> Unit,
    onSyncIntervalChanged: (Int) -> Unit = {},
    onCollectionSettings: (CollectionEntity) -> Unit,
    onDeleteLocalCalendar: () -> Unit = {},
    onPickAccountImage: (String, Uri) -> Unit = { _, _ -> },
    onRemoveAccountImage: (String) -> Unit = {},
    onAvatarColorSelected: (String, Int?) -> Unit = { _, _ -> },
    onAddLocalCalendar: () -> Unit = {},
    onGroupSettings: (String) -> Unit = {},
    trashCount: Int = 0,
    onRecentlyDeleted: () -> Unit = {},
    onClose: () -> Unit,
) {
    val timelineVisibility = timelineSettingsVisibility(
        weekViewEnabled = state.weekViewEnabled,
        sidebarControlsEnabled = state.multiDaySidebarControlsEnabled,
    )
    val initialNavigationStack = remember(initialDestination) {
        if (initialDestination == SettingsDestination.Main) {
            listOf(SettingsDestination.Main.name)
        } else {
            listOf(SettingsDestination.Main.name, initialDestination.name)
        }
    }
    var navigationStack by rememberSaveable(initialDestination.name) { mutableStateOf(initialNavigationStack) }
    var selectedAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var eventDurationDialogOpen by remember { mutableStateOf(false) }
    var defaultEventCollectionDialogOpen by remember { mutableStateOf(false) }
    var defaultTaskCollectionDialogOpen by remember { mutableStateOf(false) }
    var deleteAccountCandidateId by remember { mutableStateOf<String?>(null) }
    var readOnlyUrl by rememberSaveable { mutableStateOf("") }
    var addServerUrl by rememberSaveable { mutableStateOf("") }
    var addUsername by rememberSaveable { mutableStateOf("") }
    var addPassword by rememberSaveable { mutableStateOf("") }
    var addAccountError by rememberSaveable { mutableStateOf<String?>(null) }
    var addAccountSubmitting by remember { mutableStateOf(false) }
    var serverDetailsOpen by rememberSaveable { mutableStateOf(false) }
    var accountFieldEdit by remember { mutableStateOf<AccountFieldEdit?>(null) }
    var createServerCalendarOpen by remember { mutableStateOf(false) }
    var accountAvatarOpen by remember { mutableStateOf(false) }
    var navigatingForward by remember { mutableStateOf(true) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val currentDestination = remember(navigationStack) {
        runCatching { SettingsDestination.valueOf(navigationStack.last()) }.getOrDefault(SettingsDestination.Main)
    }
    // Each page keeps its scroll position while it is in the back stack, so going back returns
    // to where the user was; a page opened anew starts at the top.
    val scrollStates = remember { mutableMapOf<SettingsDestination, ScrollState>() }
    fun navigateTo(destination: SettingsDestination) {
        navigatingForward = true
        scrollStates.remove(destination)
        navigationStack = navigationStack + destination.name
    }
    fun navigateToRoot(destination: SettingsDestination) {
        navigatingForward = true
        scrollStates.keys.retainAll { it == SettingsDestination.Main }
        navigationStack = listOf(SettingsDestination.Main.name, destination.name)
    }
    fun resetAddAccountForm() {
        addServerUrl = ""
        addUsername = ""
        addPassword = ""
        addAccountError = null
        addAccountSubmitting = false
    }
    fun openAccount(account: AccountEntity) {
        selectedAccountId = account.id
        serverDetailsOpen = false
        navigateTo(SettingsDestination.AccountDetail)
    }
    val context = LocalContext.current
    val calDavValidationFailedText = stringResource(R.string.caldav_validation_failed)
    val calendarPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val readGranted = grants[Manifest.permission.READ_CALENDAR] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val writeGranted = grants[Manifest.permission.WRITE_CALENDAR] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (readGranted && writeGranted) {
            onAddAndroidCalendars()
            navigateToRoot(SettingsDestination.Accounts)
        }
    }
    fun addAndroidCalendarsWithPermission() {
        val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (readGranted && writeGranted) {
            onAddAndroidCalendars()
            navigateToRoot(SettingsDestination.Accounts)
        } else {
            calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
    }
    fun navigateBack() {
        navigatingForward = false
        if (navigationStack.size > 1) navigationStack = navigationStack.dropLast(1) else onClose()
    }
    BackHandler(enabled = true) {
        navigateBack()
    }
    val accounts = state.accounts.ifEmpty { state.account?.let(::listOf).orEmpty() }
    // A source needs attention when it hides device calendars or its subscription delivers nothing.
    fun AccountEntity.needsAttention(): Boolean =
        (isAndroidProviderAccount() && state.hiddenAndroidProviderCalendarNames.isNotEmpty()) ||
            state.collections.any { collection ->
                collection.accountId == id && readOnlyFeedStatus(this, collection).let {
                    it != null && it != ReadOnlyFeedStatus.Empty && it != ReadOnlyFeedStatus.Loading
                }
            }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(40f),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .centeredMaxWidth(largeScreenMaxWidth(SettingsContentMaxWidth))
                .padding(top = statusTop + 8.dp, bottom = navBottom),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = ::navigateBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = WarmInk,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    currentDestination.localizedTitle(),
                    color = WarmInk,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                    if (navigatingForward) {
                        (slideInHorizontally(tween(MotionMedium, easing = MotionStandard)) { it / 5 } + fadeIn(tween(MotionMedium))) togetherWith
                            (slideOutHorizontally(tween(MotionShort, easing = MotionStandardAccelerate)) { -it / 5 } + fadeOut(tween(MotionShort)))
                    } else {
                        (slideInHorizontally(tween(MotionMedium, easing = MotionStandard)) { -it / 5 } + fadeIn(tween(MotionMedium))) togetherWith
                            (slideOutHorizontally(tween(MotionShort, easing = MotionStandardAccelerate)) { it / 5 } + fadeOut(tween(MotionShort)))
                    }
                },
                label = "settingsSubpage",
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollStates.getOrPut(page) { ScrollState(0) })
                        .padding(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    when (page) {
                        SettingsDestination.Main -> {
                            SettingsGroup {
                                SettingsNavRow(
                                    title = stringResource(R.string.settings_calendars),
                                    subtitle = listOf(
                                        pluralStringResource(R.plurals.settings_source_count, accounts.size, accounts.size),
                                        state.collections.count { it.isEnabled }.let { pluralStringResource(R.plurals.settings_calendar_count, it, it) },
                                    ).joinToString(" · "),
                                    icon = Icons.Default.CalendarMonth,
                                    iconColor = SettingsColors.Calendars,
                                    warning = accounts.any { it.needsAttention() },
                                ) { navigateTo(SettingsDestination.Accounts) }
                            }
                            SettingsGroup {
                                SettingsNavRow(
                                    title = stringResource(R.string.settings_views),
                                    subtitle = stringResource(R.string.settings_views_summary),
                                    icon = Icons.Default.ViewWeek,
                                    iconColor = SettingsColors.Views,
                                ) { navigateTo(SettingsDestination.Views) }
                                SettingsNavRow(
                                    title = stringResource(R.string.settings_new_items),
                                    subtitle = stringResource(R.string.settings_new_items_summary),
                                    icon = Icons.Default.EditCalendar,
                                    iconColor = SettingsColors.NewItems,
                                ) { navigateTo(SettingsDestination.NewItems) }
                                SettingsNavRow(
                                    title = stringResource(R.string.appearance),
                                    subtitle = stringResource(R.string.settings_appearance_summary),
                                    icon = Icons.Default.Palette,
                                    iconColor = SettingsColors.Appearance,
                                ) { navigateTo(SettingsDestination.Appearance) }
                                SettingsNavRow(
                                    title = stringResource(R.string.settings_general),
                                    subtitle = stringResource(R.string.settings_general_summary),
                                    icon = Icons.Default.Tune,
                                    iconColor = SettingsColors.General,
                                ) { navigateTo(SettingsDestination.General) }
                                SettingsNavRow(
                                    title = stringResource(R.string.widgets),
                                    subtitle = stringResource(R.string.widget_settings_summary),
                                    icon = Icons.Default.Widgets,
                                    iconColor = SettingsColors.Widgets,
                                ) { navigateTo(SettingsDestination.Widgets) }
                            }
                            SettingsGroup {
                                SettingsNavRow(
                                    title = stringResource(R.string.recently_deleted),
                                    subtitle = if (trashCount > 0) {
                                        pluralStringResource(R.plurals.recently_deleted_items, trashCount, trashCount)
                                    } else {
                                        stringResource(R.string.recently_deleted_summary_empty)
                                    },
                                    icon = Icons.Default.Delete,
                                    iconColor = SettingsColors.Trash,
                                    onClick = onRecentlyDeleted,
                                )
                            }
                            SettingsGroup(title = stringResource(R.string.settings_project)) {
                                SettingsNavRow(
                                    title = stringResource(R.string.about_kgs_calendar),
                                    subtitle = stringResource(R.string.version_value, appVersionName()),
                                    icon = Icons.Default.Info,
                                    iconColor = SettingsColors.About,
                                ) { navigateTo(SettingsDestination.About) }
                                SettingsLinkRow(
                                    title = stringResource(R.string.sponsor_project),
                                    subtitle = stringResource(R.string.sponsor_project_summary),
                                    icon = Icons.Default.Favorite,
                                    iconColor = SettingsColors.Sponsor,
                                    url = SponsorProjectUrl,
                                )
                                SettingsLinkRow(
                                    title = stringResource(R.string.report_bug),
                                    subtitle = stringResource(R.string.report_bug_summary),
                                    icon = Icons.Default.BugReport,
                                    iconColor = SettingsColors.Bug,
                                    url = BugReportIssuesUrl,
                                )
                            }
                        }
                        SettingsDestination.About -> {
                            AboutIdentityCard()
                            SettingsGroup {
                                SettingsLinkRow(
                                    title = stringResource(R.string.source_code),
                                    subtitle = "github.com/KGS501/kgs-calendar",
                                    icon = Icons.Default.Code,
                                    iconColor = SettingsColors.Views,
                                    url = SourceCodeUrl,
                                )
                                SettingsNavRow(
                                    title = stringResource(R.string.privacy),
                                    subtitle = stringResource(R.string.privacy_summary),
                                    icon = Icons.Default.Lock,
                                    iconColor = SettingsColors.Privacy,
                                ) { navigateTo(SettingsDestination.Privacy) }
                                SettingsLinkRow(
                                    title = stringResource(R.string.release_notes),
                                    subtitle = stringResource(R.string.release_notes_summary),
                                    icon = Icons.Default.NewReleases,
                                    iconColor = SettingsColors.NewItems,
                                    url = ReleasesUrl,
                                )
                            }
                        }
                        SettingsDestination.Accounts -> {
                            val externalAccounts = accounts.filterNot { it.id == UiLocalAccountId }
                            val localCollection = state.collections.firstOrNull { it.href.isLocalCollectionHref() }
                            SettingsGroup(title = stringResource(R.string.sources)) {
                                // Sources in their automatic groups (feeds of one website together), each once.
                                // Custom groups only rearrange the sidebar.
                                state.calendarGroups.filter { it.kind != CalendarSourceGroupKind.Custom }.forEach { group ->
                                    val groupAccounts = accounts.filter { CalendarSourceGroups.automaticKey(it, accounts) == group.key }
                                    if (groupAccounts.isEmpty()) return@forEach
                                    key(group.key) {
                                        run {
                                            val groupName = group.displayName()
                                            val groupImage = group.image(state.sourceImages)
                                            val groupColor = group.avatarColor(state.calendarGroupSettings)
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                val single = groupAccounts.size == 1
                                                if (!single) {
                                                    SettingsRow(
                                                        title = groupName,
                                                        subtitle = group.subtitle(),
                                                        leading = { SourceAvatar(groupName, groupImage, 40.dp, groupColor) },
                                                        onClick = { onGroupSettings(group.key) },
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Edit,
                                                            contentDescription = stringResource(R.string.calendar_group_edit),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(20.dp),
                                                        )
                                                    }
                                                }
                                                groupAccounts.forEach { account ->
                                                    // Feeds of one website share their host name, so they are told apart by calendar name.
                                                    val feedCalendarName = state.collections
                                                        .takeIf { account.sourceType == SourceType.ReadOnlyUrl }
                                                        ?.firstOrNull { it.accountId == account.id }
                                                        ?.displayName
                                                    val rowTitle = when {
                                                        single -> groupName
                                                        feedCalendarName != null -> feedCalendarName
                                                        else -> account.shownName()
                                                    }
                                                    SettingsNavRow(
                                                        title = rowTitle,
                                                        subtitle = if (account.id == UiLocalAccountId && localCollection?.isEnabled == false) {
                                                            stringResource(R.string.calendar_inactive)
                                                        } else {
                                                            account.sourceDescription(state.collections, single)
                                                        },
                                                        // Every source has its picture; those of a group are indented under it.
                                                        leading = if (single) {
                                                            { SourceAvatar(groupName, groupImage, 40.dp, groupColor) }
                                                        } else {
                                                            {
                                                                SourceAvatar(
                                                                    rowTitle,
                                                                    account.image(state.sourceImages),
                                                                    32.dp,
                                                                    account.avatarColor(state.calendarGroupSettings),
                                                                    modifier = Modifier.padding(start = 20.dp),
                                                                )
                                                            }
                                                        },
                                                        warning = account.needsAttention(),
                                                    ) { openAccount(account) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            SettingsPrimaryAction(
                                label = stringResource(R.string.add_calendar),
                                icon = Icons.Default.Add,
                            ) { navigateTo(SettingsDestination.AddSource) }
                            // Only calendars from elsewhere sync; the local calendar alone has nothing to sync.
                            if (externalAccounts.isNotEmpty()) SettingsGroup(
                                title = stringResource(R.string.sync),
                                footer = stringResource(R.string.sync_interval_help),
                            ) {
                                SettingsRow(
                                    title = stringResource(R.string.sync_now),
                                    icon = Icons.Default.Refresh,
                                    enabled = !state.isBusy,
                                    onClick = onSync,
                                )
                                SettingsStepSliderRow(
                                    title = stringResource(R.string.sync_interval),
                                    options = SettingsStore.SYNC_INTERVAL_MINUTES,
                                    selected = state.syncIntervalMinutes,
                                    label = { syncIntervalLabel(it) },
                                    onSelected = onSyncIntervalChanged,
                                )
                            }
                            SettingsListSection(
                                title = stringResource(R.string.calendar_order),
                                footer = stringResource(R.string.calendar_order_help),
                            ) {
                                CollectionReorderList(
                                    collections = state.collections,
                                    accounts = accounts,
                                    hiddenCollectionHrefs = state.hiddenCollectionHrefs,
                                    onReordered = onCollectionsReordered,
                                    onCollectionClick = onCollectionSettings,
                                )
                            }
                        }
                        SettingsDestination.AddSource -> {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                SourceOptionCard(
                                    title = stringResource(R.string.caldav),
                                    leadingIcon = Icons.Default.Cloud,
                                    color = SettingsColors.Calendars,
                                    chips = listOf(stringResource(R.string.events) to SourceChipColors.Events, stringResource(R.string.tasks) to SourceChipColors.Tasks, stringResource(R.string.source_full_sync) to SourceChipColors.FullSync),
                                    examples = listOf(
                                        SourceExampleBrand("Nextcloud", Color(0xFF0082C9), iconRes = R.drawable.brand_nextcloud),
                                        SourceExampleBrand("mailbox.org", Color(0xFF76B900), iconRes = R.drawable.brand_mailbox),
                                        SourceExampleBrand("DAVx5", Color(0xFF7CB342), iconRes = R.drawable.brand_davx5),
                                        SourceExampleBrand("iCloud", Color(0xFF6E6E73), iconRes = R.drawable.brand_icloud),
                                    ),
                                ) {
                                    resetAddAccountForm()
                                    navigateTo(SettingsDestination.AddAccount)
                                }
                                SourceOptionCard(
                                    title = stringResource(R.string.android_device_calendars),
                                    leadingIcon = Icons.Default.PhoneAndroid,
                                    color = SettingsColors.NewItems,
                                    chips = listOf(stringResource(R.string.events) to SourceChipColors.Events, stringResource(R.string.source_full_sync) to SourceChipColors.FullSync),
                                    examples = listOf(
                                        SourceExampleBrand("Google", Color(0xFF4285F4), iconRes = R.drawable.brand_google_calendar),
                                        SourceExampleBrand("Microsoft", Color(0xFF00A4EF), iconRes = R.drawable.brand_microsoft),
                                        SourceExampleBrand("Samsung", Color(0xFF1428A0), iconRes = R.drawable.brand_samsung),
                                    ),
                                ) {
                                    addAndroidCalendarsWithPermission()
                                }
                                SourceOptionCard(
                                    title = stringResource(R.string.subscribe_url),
                                    leadingIcon = Icons.Default.Public,
                                    color = SettingsColors.Appearance,
                                    chips = listOf(stringResource(R.string.events) to SourceChipColors.Events, stringResource(R.string.source_read_only) to SourceChipColors.ReadOnly),
                                    examples = listOf(
                                        SourceExampleBrand("ICS", Color(0xFF7C3AED), imageVector = Icons.Default.Event),
                                        SourceExampleBrand("webcal", Color(0xFF0891B2), imageVector = Icons.Default.Link),
                                        SourceExampleBrand("public URL", Color(0xFF64748B), imageVector = Icons.Default.Public),
                                    ),
                                ) {
                                    readOnlyUrl = ""
                                    navigateTo(SettingsDestination.AddReadOnly)
                                }
                                // Only one local calendar exists; it can be added again after it was deleted.
                                if (accounts.none { it.id == UiLocalAccountId }) {
                                    SourceOptionCard(
                                        title = stringResource(R.string.local_calendar),
                                        leadingIcon = Icons.Default.Smartphone,
                                        color = SettingsColors.General,
                                        chips = listOf(stringResource(R.string.events) to SourceChipColors.Events, stringResource(R.string.tasks) to SourceChipColors.Tasks, stringResource(R.string.source_device_only) to SourceChipColors.DeviceOnly),
                                        examples = emptyList(),
                                    ) {
                                        onAddLocalCalendar()
                                        navigateToRoot(SettingsDestination.Accounts)
                                    }
                                }
                            }
                        }
                        SettingsDestination.AddAccount -> {
                            SettingsForm {
                                OutlinedTextField(
                                    value = addServerUrl,
                                    onValueChange = {
                                        addServerUrl = it
                                        addAccountError = null
                                    },
                                    label = { Text(stringResource(R.string.caldav_server_url)) },
                                    singleLine = true,
                                    keyboardOptions = UrlKeyboardOptions,
                                    shape = SettingsFieldShape,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = addUsername,
                                    onValueChange = {
                                        addUsername = it
                                        addAccountError = null
                                    },
                                    label = { Text(stringResource(R.string.username)) },
                                    singleLine = true,
                                    keyboardOptions = UsernameKeyboardOptions,
                                    shape = SettingsFieldShape,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                SecurePasswordField(
                                    value = addPassword,
                                    onValueChange = {
                                        addPassword = it
                                        addAccountError = null
                                    },
                                    label = stringResource(R.string.password_or_app_password),
                                    shape = SettingsFieldShape,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                AnimatedVisibility(visible = addAccountError != null) {
                                    Text(
                                        addAccountError.orEmpty(),
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 13.sp,
                                        lineHeight = 17.sp,
                                    )
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SettingsPrimaryAction(
                                    label = if (addAccountSubmitting) stringResource(R.string.checking_connection) else stringResource(R.string.add),
                                    icon = Icons.Default.Check,
                                    enabled = addServerUrl.isNotBlank() &&
                                        addUsername.isNotBlank() &&
                                        addPassword.isNotBlank() &&
                                        !addAccountSubmitting &&
                                        !state.isBusy,
                                ) {
                                    addAccountSubmitting = true
                                    addAccountError = null
                                    onManualLogin(addServerUrl, addUsername, addPassword) { success, _ ->
                                        addAccountSubmitting = false
                                        if (success) {
                                            resetAddAccountForm()
                                            navigateToRoot(SettingsDestination.Accounts)
                                        } else {
                                            addAccountError = calDavValidationFailedText
                                        }
                                    }
                                }
                                SettingsSecondaryAction(
                                    label = stringResource(R.string.nextcloud_browser_login),
                                    enabled = addServerUrl.isNotBlank() && !state.isBusy,
                                ) {
                                    onBrowserLogin(addServerUrl)
                                    navigateToRoot(SettingsDestination.Accounts)
                                }
                                SettingsFootnote(stringResource(R.string.nextcloud_browser_login_help), Modifier.padding(horizontal = 16.dp))
                            }
                        }
                        SettingsDestination.AddReadOnly -> {
                            SettingsForm {
                                OutlinedTextField(
                                    value = readOnlyUrl,
                                    onValueChange = { readOnlyUrl = it },
                                    label = { Text(stringResource(R.string.ics_calendar_url)) },
                                    singleLine = true,
                                    keyboardOptions = UrlKeyboardOptions,
                                    shape = SettingsFieldShape,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SettingsPrimaryAction(
                                    label = stringResource(R.string.add_url),
                                    icon = Icons.Default.Add,
                                    enabled = isSupportedReadOnlyCalendarUrl(readOnlyUrl),
                                ) {
                                    onAddReadOnlyCalendar(readOnlyUrl)
                                    readOnlyUrl = ""
                                    navigateToRoot(SettingsDestination.Accounts)
                                }
                                SettingsFootnote(stringResource(R.string.subscribed_readonly_help), Modifier.padding(horizontal = 16.dp))
                            }
                        }
                        SettingsDestination.AccountDetail -> {
                            val account = state.accounts.firstOrNull { it.id == selectedAccountId }
                            if (account != null) {
                                val isLocalSource = account.id == UiLocalAccountId || account.serverUrl.startsWith(UiLocalCollectionPrefix)
                                val isAndroidSource = account.isAndroidProviderAccount()
                                val isReadOnlySource = account.sourceType == SourceType.ReadOnlyUrl || account.username == "Read-only URL"
                                val isCalDavSource = !isLocalSource && !isAndroidSource && !isReadOnlySource
                                val accountGroup = state.calendarGroups.firstOrNull { it.key == CalendarSourceGroups.automaticKey(account, state.accounts) }
                                val accountName = account.shownName()
                                val accountGroupName = accountGroup?.displayName() ?: accountName
                                val accountGroupImage = accountGroup?.image(state.sourceImages)
                                fun update(
                                    name: String = accountName,
                                    serverUrl: String = account.serverUrl,
                                    username: String = account.username,
                                    password: String? = null,
                                ) = onUpdateAccount(account.id, name, serverUrl, username, password)
                                // A subscription is known by its calendar's name, as in the source list.
                                val headerName = state.collections
                                    .takeIf { isReadOnlySource }
                                    ?.firstOrNull { it.accountId == account.id }
                                    ?.displayName
                                    ?: accountName
                                SourceIdentityHeader(
                                    name = headerName,
                                    description = account.sourceDescription(state.collections, single = true),
                                    avatarName = headerName,
                                    image = account.image(state.sourceImages),
                                    color = account.avatarColor(state.calendarGroupSettings),
                                    onAvatarClick = { accountAvatarOpen = true },
                                )
                                if (accountAvatarOpen) {
                                    AccountAvatarDialog(
                                        account = account,
                                        name = headerName,
                                        images = state.sourceImages,
                                        settings = state.calendarGroupSettings,
                                        onPickImage = { uri -> onPickAccountImage(account.id, uri) },
                                        onRemoveImage = { onRemoveAccountImage(account.id) },
                                        onColorSelected = { color -> onAvatarColorSelected(SourceImageKeys.account(account.id), color) },
                                        onDismiss = { accountAvatarOpen = false },
                                    )
                                }
                                SettingsGroup(title = stringResource(R.string.account_section)) {
                                    SettingsEditRow(stringResource(R.string.name), accountName) {
                                        accountFieldEdit = AccountFieldEdit(AccountField.Name, accountName)
                                    }
                                    if (!isLocalSource && !isAndroidSource) {
                                        SettingsEditRow(
                                            title = stringResource(if (isReadOnlySource) R.string.ics_calendar_url else R.string.server_url),
                                            value = account.serverUrl,
                                        ) { accountFieldEdit = AccountFieldEdit(AccountField.ServerUrl, account.serverUrl) }
                                    }
                                    if (isCalDavSource) {
                                        SettingsEditRow(stringResource(R.string.username), account.username) {
                                            accountFieldEdit = AccountFieldEdit(AccountField.Username, account.username)
                                        }
                                        SettingsEditRow(stringResource(R.string.password), "••••••••") {
                                            accountFieldEdit = AccountFieldEdit(AccountField.Password, "")
                                        }
                                    }
                                }
                                if (accountGroup != null) {
                                    SettingsGroup(title = stringResource(R.string.calendar_group_choose)) {
                                        SettingsNavRow(
                                            title = accountGroupName,
                                            subtitle = stringResource(R.string.calendar_group_edit_summary),
                                            leading = { SourceAvatar(accountGroupName, accountGroupImage, 40.dp, accountGroup.avatarColor(state.calendarGroupSettings)) },
                                            onClick = { onGroupSettings(accountGroup.key) },
                                        )
                                    }
                                }
                                if (isAndroidSource && state.hiddenAndroidProviderCalendarNames.isNotEmpty()) {
                                    Box(Modifier.padding(horizontal = 16.dp)) {
                                        AndroidProviderDiagnosticsCard(
                                            hiddenCalendarNames = state.hiddenAndroidProviderCalendarNames,
                                            showDisabledProviderCalendars = state.showDisabledAndroidProviderCalendars,
                                            onDisabledProviderCalendarsVisibleChanged = onDisabledAndroidProviderCalendarsVisibleChanged,
                                        )
                                    }
                                }
                                if (isCalDavSource) {
                                    SettingsGroup(title = stringResource(R.string.server_section)) {
                                        SettingsRow(
                                            title = stringResource(R.string.create_server_calendar),
                                            titleColor = WarmBrown,
                                            icon = Icons.Default.Add,
                                            enabled = !state.isBusy,
                                            onClick = { createServerCalendarOpen = true },
                                        )
                                        SettingsRow(
                                            title = stringResource(R.string.caldav_discovery),
                                            onClick = { serverDetailsOpen = !serverDetailsOpen },
                                        ) {
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp).rotateWhen(serverDetailsOpen),
                                            )
                                        }
                                        AnimatedVisibility(
                                            visible = serverDetailsOpen,
                                            enter = expandVertically(animationSpec = tween(MotionMedium, easing = MotionStandard)) + fadeIn(tween(MotionShort)),
                                            exit = shrinkVertically(animationSpec = tween(MotionMedium, easing = MotionStandardAccelerate)) + fadeOut(tween(MotionShort)),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(settingsControlColor())
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                CalendarMetadataRow(stringResource(R.string.caldav_principal), account.principalUrl ?: stringResource(R.string.calendar_unknown))
                                                CalendarMetadataRow(stringResource(R.string.caldav_calendar_home), account.calendarHomeUrl ?: stringResource(R.string.calendar_unknown))
                                                CalendarMetadataRow(
                                                    stringResource(R.string.caldav_scheduling),
                                                    account.capabilitiesJson.toJsonObjectOrNull()?.optBooleanOrNull("supportsScheduling").localizedSupportedUnsupported(),
                                                )
                                            }
                                        }
                                    }
                                }
                                SettingsGroup(footer = if (isLocalSource) stringResource(R.string.delete_local_calendar_footer) else null) {
                                    SettingsRow(
                                        title = stringResource(if (isLocalSource) R.string.delete_local_calendar else R.string.remove_source),
                                        titleColor = MaterialTheme.colorScheme.error,
                                        leading = {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                                        },
                                        onClick = { deleteAccountCandidateId = account.id },
                                    )
                                }
                                accountFieldEdit?.let { edit ->
                                    AccountFieldDialog(
                                        edit = edit,
                                        onDismiss = { accountFieldEdit = null },
                                        onSave = { value ->
                                            when (edit.field) {
                                                AccountField.Name -> update(name = value)
                                                AccountField.ServerUrl -> update(serverUrl = value)
                                                AccountField.Username -> update(username = value)
                                                AccountField.Password -> update(password = value)
                                            }
                                            accountFieldEdit = null
                                        },
                                    )
                                }
                                if (createServerCalendarOpen) {
                                    CreateServerCalendarDialog(
                                        onDismiss = { createServerCalendarOpen = false },
                                        onCreate = { name, events, tasks ->
                                            onCreateCalDavCalendar(account.id, name, events, tasks)
                                            createServerCalendarOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        SettingsDestination.Views -> {
                            SettingsGroup {
                                SettingsDropdownRow(
                                    title = stringResource(R.string.default_view),
                                    options = listOf(CalendarViewMode.Agenda, CalendarViewMode.Day, CalendarViewMode.ThreeDay, CalendarViewMode.Month),
                                    selected = state.selectedView,
                                    label = { it.localizedLabel(state.weekViewEnabled) },
                                    optionIcon = { it.settingsIcon() },
                                    onSelected = onViewSelected,
                                )
                                SettingsSegmentedRow(
                                    title = stringResource(R.string.first_day_of_week),
                                    options = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY),
                                    selected = state.firstDayOfWeek,
                                    label = { it.localizedWeekdayLabel() },
                                    onSelected = onFirstDayOfWeekSelected,
                                )
                            }
                            SettingsGroup(title = stringResource(R.string.settings_display)) {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.show_calendar_weeks),
                                    checked = state.showCalendarWeeks,
                                    onCheckedChange = onShowCalendarWeeksChanged,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.show_completed_tasks),
                                    checked = state.showCompletedTasksInCalendar,
                                    onCheckedChange = onShowCompletedTasksChanged,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.subtasks_expanded),
                                    checked = state.subtasksExpandedByDefault,
                                    onCheckedChange = onSubtasksExpandedByDefaultChanged,
                                )
                                SettingsSliderRow(
                                    title = stringResource(R.string.all_day_items),
                                    subtitle = stringResource(R.string.all_day_items_help),
                                    value = state.maxVisibleAllDayItems,
                                    range = 0..10,
                                    onValueChanged = onMaxVisibleAllDayItemsChanged,
                                )
                            }
                            SettingsGroup(title = stringResource(R.string.settings_multi_day)) {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.use_week_view),
                                    subtitle = stringResource(R.string.use_week_view_help),
                                    checked = state.weekViewEnabled,
                                    onCheckedChange = onWeekViewEnabledChanged,
                                )
                                SettingsReveal(visible = timelineVisibility.showFullWeekSwipe) {
                                    SettingsSwitchRow(
                                        title = stringResource(R.string.swipe_full_weeks),
                                        subtitle = stringResource(R.string.swipe_full_weeks_help),
                                        checked = state.fullWeekSwipeEnabled,
                                        onCheckedChange = onFullWeekSwipeEnabledChanged,
                                    )
                                }
                                SettingsReveal(visible = timelineVisibility.showMultiDayControls) {
                                    SettingsSwitchRow(
                                        title = stringResource(R.string.multi_day_sidebar_controls),
                                        subtitle = stringResource(R.string.multi_day_sidebar_controls_help),
                                        checked = state.multiDaySidebarControlsEnabled,
                                        onCheckedChange = onMultiDaySidebarControlsChanged,
                                    )
                                }
                                SettingsReveal(visible = timelineVisibility.showMultiDayCount) {
                                    // A tablet or unfolded foldable edits its own counts, so the
                                    // folded phone screen keeps the values that suit it.
                                    val largeScreen = currentCalendarWindowLayout().isLargeScreen
                                    val largeScreenNote = if (largeScreen) stringResource(R.string.multi_day_count_large_screen) else null
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        SettingsSliderRow(
                                            title = stringResource(R.string.multi_day_count_portrait),
                                            subtitle = largeScreenNote,
                                            value = if (largeScreen) {
                                                state.largePortraitMultiDayCount
                                            } else {
                                                state.portraitMultiDayCount
                                            }.coerceMultiDayCount(),
                                            range = MIN_MULTI_DAY_COUNT..MAX_MULTI_DAY_COUNT,
                                            onValueChanged = { count ->
                                                onMultiDayCountChanged(
                                                    if (largeScreen) MultiDayCountBucket.LargePortrait else MultiDayCountBucket.Portrait,
                                                    count,
                                                )
                                            },
                                        )
                                        SettingsSliderRow(
                                            title = stringResource(R.string.multi_day_count_landscape),
                                            subtitle = largeScreenNote,
                                            value = if (largeScreen) {
                                                state.largeLandscapeMultiDayCount
                                            } else {
                                                state.landscapeMultiDayCount
                                            }.coerceMultiDayCount(),
                                            range = MIN_MULTI_DAY_COUNT..MAX_MULTI_DAY_COUNT,
                                            onValueChanged = { count ->
                                                onMultiDayCountChanged(
                                                    if (largeScreen) MultiDayCountBucket.LargeLandscape else MultiDayCountBucket.Landscape,
                                                    count,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        SettingsDestination.NewItems -> {
                            val eventTargets = remember(state.collections) {
                                state.collections.filter { it.supportsEvents && it.isEnabled && !it.isReadOnlyCollection() }
                            }
                            val taskTargets = remember(state.collections) {
                                state.collections.filter { it.supportsTasks && it.isEnabled && !it.isReadOnlyCollection() }
                            }
                            SettingsGroup(title = stringResource(R.string.events)) {
                                // Common durations in a menu; any other length goes through the dialog.
                                val currentDuration = state.defaultEventDurationMinutes
                                SettingsDropdownRow(
                                    title = stringResource(R.string.duration),
                                    options = EventDurationPresets + CustomDurationOption,
                                    selected = currentDuration.takeIf { it in EventDurationPresets } ?: CustomDurationOption,
                                    label = { minutes ->
                                        when {
                                            minutes != CustomDurationOption -> minutes.localizedDurationLabel()
                                            currentDuration in EventDurationPresets -> stringResource(R.string.custom_duration_short)
                                            else -> currentDuration.localizedDurationLabel()
                                        }
                                    },
                                    onSelected = { minutes ->
                                        if (minutes == CustomDurationOption) eventDurationDialogOpen = true else onDefaultEventDurationChanged(minutes)
                                    },
                                )
                                if (eventTargets.isNotEmpty()) {
                                    SettingsValueRow(
                                        title = stringResource(R.string.default_calendar),
                                        value = eventTargets.firstOrNull { it.href == state.defaultEventCollectionHref }?.displayName ?: stringResource(R.string.automatic),
                                        onClick = { defaultEventCollectionDialogOpen = true },
                                    )
                                }
                                SettingsReminderEditor(
                                    title = stringResource(R.string.reminders),
                                    selected = state.defaultEventReminderMinutes,
                                    onSelectedChange = onDefaultEventRemindersChanged,
                                )
                            }
                            SettingsGroup(title = stringResource(R.string.tasks)) {
                                // Which dates a new task gets: a switch each for start and end.
                                val dates = state.defaultTaskDates
                                val hasStart = dates == TaskDefaultDates.Start || dates == TaskDefaultDates.StartAndDue
                                val hasEnd = dates == TaskDefaultDates.Due || dates == TaskDefaultDates.StartAndDue
                                fun datesFor(start: Boolean, end: Boolean) = when {
                                    start && end -> TaskDefaultDates.StartAndDue
                                    start -> TaskDefaultDates.Start
                                    end -> TaskDefaultDates.Due
                                    else -> TaskDefaultDates.None
                                }
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_task_start_date),
                                    checked = hasStart,
                                    onCheckedChange = { onDefaultTaskDatesSelected(datesFor(it, hasEnd)) },
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_task_end_date),
                                    checked = hasEnd,
                                    onCheckedChange = { onDefaultTaskDatesSelected(datesFor(hasStart, it)) },
                                )
                                SettingsReveal(visible = state.defaultTaskDates != TaskDefaultDates.None) {
                                    SettingsSwitchRow(
                                        title = stringResource(R.string.settings_task_with_time),
                                        subtitle = stringResource(R.string.settings_task_with_time_help),
                                        checked = state.defaultTaskHasTime,
                                        onCheckedChange = onDefaultTaskHasTimeChanged,
                                    )
                                }
                                if (taskTargets.isNotEmpty()) {
                                    SettingsValueRow(
                                        title = stringResource(R.string.default_list),
                                        value = taskTargets.firstOrNull { it.href == state.defaultTaskCollectionHref }?.displayName ?: stringResource(R.string.automatic),
                                        onClick = { defaultTaskCollectionDialogOpen = true },
                                    )
                                }
                                SettingsReminderEditor(
                                    title = stringResource(R.string.reminders),
                                    selected = state.defaultTaskReminderMinutes,
                                    onSelectedChange = onDefaultTaskRemindersChanged,
                                )
                            }
                            SettingsGroup(title = stringResource(R.string.settings_editor)) {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.focus_title_directly),
                                    checked = state.focusTitleOnCreate,
                                    onCheckedChange = onFocusTitleOnCreateChanged,
                                )
                                SettingsNavRow(
                                    title = stringResource(R.string.event_fields),
                                    subtitle = stringResource(R.string.field_order_summary),
                                ) { navigateTo(SettingsDestination.EventFieldOrder) }
                                SettingsNavRow(
                                    title = stringResource(R.string.task_fields),
                                    subtitle = stringResource(R.string.field_order_summary),
                                ) { navigateTo(SettingsDestination.TaskFieldOrder) }
                            }
                        }
                        SettingsDestination.Appearance -> {
                            SettingsGroup(title = stringResource(R.string.color_scheme)) {
                                ThemeSwatchPicker(selected = state.themeMode, onSelected = onThemeSelected)
                                SettingsSegmentedRow(
                                    title = stringResource(R.string.color_mode),
                                    options = listOf(AppColorMode.Auto, AppColorMode.Light, AppColorMode.Dark),
                                    selected = state.colorMode,
                                    label = { it.localizedLabel() },
                                    onSelected = onColorModeSelected,
                                )
                            }
                            SettingsGroup(title = stringResource(R.string.tasks)) {
                                SettingsSegmentedRow(
                                    title = stringResource(R.string.color_source),
                                    subtitle = stringResource(R.string.color_source_help),
                                    options = listOf(TaskColorMode.Collection, TaskColorMode.Priority),
                                    selected = state.taskColorMode,
                                    label = {
                                        when (it) {
                                            TaskColorMode.Collection -> stringResource(R.string.calendar)
                                            TaskColorMode.Priority -> stringResource(R.string.priority)
                                        }
                                    },
                                    onSelected = onTaskColorModeSelected,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.priority_animations),
                                    subtitle = stringResource(R.string.priority_animations_help),
                                    checked = state.priorityAnimationsEnabled,
                                    onCheckedChange = onPriorityAnimationsChanged,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.overdue_summary_priority_animation),
                                    subtitle = stringResource(R.string.overdue_summary_priority_animation_help),
                                    checked = state.overdueSummaryPriorityAnimationEnabled,
                                    onCheckedChange = onOverdueSummaryPriorityAnimationChanged,
                                )
                            }
                        }
                        SettingsDestination.General -> {
                            SettingsGroup {
                                SettingsDropdownRow(
                                    title = stringResource(R.string.app_language),
                                    options = listOf(AppLanguageMode.System, AppLanguageMode.English, AppLanguageMode.German),
                                    selected = state.languageMode,
                                    label = { it.localizedLabel() },
                                    onSelected = onLanguageSelected,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.haptic_feedback),
                                    checked = state.hapticFeedbackEnabled,
                                    onCheckedChange = onHapticFeedbackChanged,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.auto_map_previews),
                                    subtitle = stringResource(R.string.auto_map_previews_help),
                                    checked = state.autoLoadMapPreviews,
                                    onCheckedChange = onAutoLoadMapPreviewsChanged,
                                )
                            }
                        }
                        SettingsDestination.Widgets -> {
                            SettingsGroup {
                                SettingsNavRow(
                                    title = stringResource(R.string.widget_agenda_name),
                                    icon = Icons.Default.ViewAgenda,
                                ) { navigateTo(SettingsDestination.WidgetAgenda) }
                                SettingsNavRow(
                                    title = stringResource(R.string.widget_month_name),
                                    icon = Icons.Default.CalendarMonth,
                                ) { navigateTo(SettingsDestination.WidgetMonth) }
                                SettingsNavRow(
                                    title = stringResource(R.string.widget_tasks_name),
                                    icon = Icons.Default.TaskAlt,
                                ) { navigateTo(SettingsDestination.WidgetTasks) }
                                SettingsNavRow(
                                    title = stringResource(R.string.widget_day_name),
                                    icon = Icons.Default.ViewDay,
                                ) { navigateTo(SettingsDestination.WidgetDay) }
                                SettingsNavRow(
                                    title = stringResource(R.string.widget_multi_name),
                                    icon = Icons.Default.Widgets,
                                ) { navigateTo(SettingsDestination.WidgetMulti) }
                            }
                        }
                        SettingsDestination.WidgetAgenda -> {
                            WidgetLookGroup(
                                themeMode = state.widgetSettings.agendaWidgetThemeMode,
                                colorMode = state.widgetSettings.agendaWidgetColorMode,
                                onThemeSelected = onAgendaWidgetThemeSelected,
                                onColorModeSelected = onAgendaWidgetColorModeSelected,
                            )
                        }
                        SettingsDestination.WidgetMonth -> {
                            WidgetLookGroup(
                                themeMode = state.widgetSettings.monthWidgetThemeMode,
                                colorMode = state.widgetSettings.monthWidgetColorMode,
                                onThemeSelected = onMonthWidgetThemeSelected,
                                onColorModeSelected = onMonthWidgetColorModeSelected,
                            )
                        }
                        SettingsDestination.WidgetTasks -> {
                            WidgetLookGroup(
                                themeMode = state.widgetSettings.tasksWidgetThemeMode,
                                colorMode = state.widgetSettings.tasksWidgetColorMode,
                                onThemeSelected = onTasksWidgetThemeSelected,
                                onColorModeSelected = onTasksWidgetColorModeSelected,
                            )
                            SettingsGroup(title = stringResource(R.string.widget_content)) {
                                SettingsDropdownRow(
                                    title = stringResource(R.string.tasks_widget_display),
                                    options = listOf(WidgetTaskDisplayMode.Planned, WidgetTaskDisplayMode.Unplanned, WidgetTaskDisplayMode.Today),
                                    selected = state.widgetSettings.tasksWidgetDisplayMode,
                                    label = { it.localizedLabel() },
                                    onSelected = onTasksWidgetDisplayModeSelected,
                                )
                                SettingsReveal(visible = state.widgetSettings.tasksWidgetDisplayMode == WidgetTaskDisplayMode.Today) {
                                    SettingsSwitchRow(
                                        title = stringResource(R.string.include_overdue_tasks),
                                        checked = state.widgetSettings.tasksWidgetIncludeOverdue,
                                        onCheckedChange = onTasksWidgetIncludeOverdueChanged,
                                    )
                                }
                                SettingsDropdownRow(
                                    title = stringResource(R.string.tasks_widget_plus_action),
                                    options = listOf(WidgetTaskCreateMode.Today, WidgetTaskCreateMode.Unplanned),
                                    selected = state.widgetSettings.tasksWidgetCreateMode,
                                    label = { it.localizedLabel() },
                                    onSelected = onTasksWidgetCreateModeSelected,
                                )
                                SettingsDropdownRow(
                                    title = stringResource(R.string.tasks_widget_subtasks_default),
                                    options = listOf(
                                        WidgetTaskSubtaskDefaultMode.FollowApp,
                                        WidgetTaskSubtaskDefaultMode.Open,
                                        WidgetTaskSubtaskDefaultMode.Closed,
                                    ),
                                    selected = state.widgetSettings.tasksWidgetSubtaskDefaultMode,
                                    label = { it.localizedLabel() },
                                    onSelected = onTasksWidgetSubtaskDefaultModeSelected,
                                )
                            }
                        }
                        SettingsDestination.WidgetDay -> {
                            WidgetLookGroup(
                                themeMode = state.widgetSettings.dayWidgetThemeMode,
                                colorMode = state.widgetSettings.dayWidgetColorMode,
                                onThemeSelected = onDayWidgetThemeSelected,
                                onColorModeSelected = onDayWidgetColorModeSelected,
                            )
                            SettingsGroup(title = stringResource(R.string.widget_content)) {
                                SettingsSliderRow(
                                    title = stringResource(R.string.day_widget_scale),
                                    value = state.widgetSettings.dayWidgetScalePercent,
                                    valueLabel = { "$it%" },
                                    range = SettingsStore.MIN_DAY_WIDGET_SCALE_PERCENT..SettingsStore.MAX_DAY_WIDGET_SCALE_PERCENT,
                                    step = 5,
                                    onValueChanged = onDayWidgetScaleChanged,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.day_widget_start_current_hour),
                                    subtitle = stringResource(R.string.day_widget_start_current_hour_help),
                                    checked = state.widgetSettings.dayWidgetStartAtCurrentHour,
                                    onCheckedChange = onDayWidgetStartAtCurrentHourChanged,
                                )
                                SettingsReveal(visible = !state.widgetSettings.dayWidgetStartAtCurrentHour) {
                                    SettingsSliderRow(
                                        title = stringResource(R.string.day_widget_start_hour),
                                        value = state.widgetSettings.dayWidgetStartHour,
                                        valueLabel = { "%02d:00".format(it) },
                                        range = 0..23,
                                        onValueChanged = onDayWidgetStartHourChanged,
                                    )
                                }
                            }
                        }
                        SettingsDestination.WidgetMulti -> {
                            WidgetLookGroup(
                                themeMode = state.widgetSettings.multiWidgetThemeMode,
                                colorMode = state.widgetSettings.multiWidgetColorMode,
                                onThemeSelected = onMultiWidgetThemeSelected,
                                onColorModeSelected = onMultiWidgetColorModeSelected,
                            )
                            SettingsGroup(title = stringResource(R.string.widget_content)) {
                                SettingsSliderRow(
                                    title = stringResource(R.string.multi_widget_split),
                                    subtitle = stringResource(R.string.multi_widget_split_help),
                                    value = state.widgetSettings.multiWidgetMonthPercent,
                                    valueLabel = { "$it% / ${100 - it}%" },
                                    range = SettingsStore.MIN_MULTI_WIDGET_MONTH_PERCENT..SettingsStore.MAX_MULTI_WIDGET_MONTH_PERCENT,
                                    step = 5,
                                    onValueChanged = onMultiWidgetMonthPercentChanged,
                                )
                            }
                        }
                        SettingsDestination.Privacy -> PrivacyPolicyPage()
                        SettingsDestination.EventFieldOrder -> {
                            SettingsListSection(footer = stringResource(R.string.field_order_help)) {
                                FieldOrderList(
                                    fields = state.eventFieldOrder,
                                    onChanged = onEventFieldOrderChanged,
                                )
                            }
                        }
                        SettingsDestination.TaskFieldOrder -> {
                            SettingsListSection(footer = stringResource(R.string.field_order_help)) {
                                FieldOrderList(
                                    fields = state.taskFieldOrder,
                                    onChanged = onTaskFieldOrderChanged,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(navBottom + 12.dp))
                }
            }
        }
    }

    if (eventDurationDialogOpen) {
        EventDurationDialog(
            currentMinutes = state.defaultEventDurationMinutes,
            onSelected = {
                onDefaultEventDurationChanged(it)
                eventDurationDialogOpen = false
            },
            onDismiss = { eventDurationDialogOpen = false },
        )
    }
    if (defaultEventCollectionDialogOpen) {
        CollectionSelectionDialog(
            title = appString(R.string.default_calendar),
            selectedHref = state.defaultEventCollectionHref,
            collections = state.collections.filter { it.supportsEvents && it.isEnabled && !it.isReadOnlyCollection() },
            hiddenCollectionHrefs = state.collectionVisibility.hrefsHidingEvents,
            onSelected = {
                onDefaultEventCollectionSelected(it)
                defaultEventCollectionDialogOpen = false
            },
            onDismiss = { defaultEventCollectionDialogOpen = false },
        )
    }
    if (defaultTaskCollectionDialogOpen) {
        CollectionSelectionDialog(
            title = appString(R.string.default_list),
            selectedHref = state.defaultTaskCollectionHref,
            collections = state.collections.filter { it.supportsTasks && it.isEnabled && !it.isReadOnlyCollection() },
            hiddenCollectionHrefs = state.collectionVisibility.hrefsHidingTasks,
            onSelected = {
                onDefaultTaskCollectionSelected(it)
                defaultTaskCollectionDialogOpen = false
            },
            onDismiss = { defaultTaskCollectionDialogOpen = false },
        )
    }
    deleteAccountCandidateId?.let { accountId ->
        val local = accountId == UiLocalAccountId
        KgsAlertDialog(
            onDismissRequest = { deleteAccountCandidateId = null },
            title = { Text(stringResource(if (local) R.string.delete_local_calendar_question else R.string.remove_source_question)) },
            text = { Text(stringResource(if (local) R.string.delete_local_calendar_body else R.string.remove_source_body)) },
            confirmButton = {
                DialogConfirmButton(
                    text = stringResource(if (local) R.string.delete else R.string.remove),
                    destructive = true,
                    onClick = {
                        if (local) onDeleteLocalCalendar() else onDeleteAccount(accountId)
                        deleteAccountCandidateId = null
                        navigateBack()
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { deleteAccountCandidateId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private val SettingsFieldShape = RoundedCornerShape(16.dp)
private val EventDurationPresets = listOf(15, 30, 45, 60, 90, 120)
private const val CustomDurationOption = -1

/** What a source is, shown under its name: its address, its kind, or why a subscription is empty. */
@Composable
private fun AccountEntity.sourceDescription(collections: List<CollectionEntity>, single: Boolean): String = when {
    id == UiLocalAccountId -> stringResource(R.string.local_source)
    isAndroidProviderAccount() -> stringResource(R.string.android_device_calendars)
    sourceType == SourceType.ReadOnlyUrl || username == "Read-only URL" ->
        collections.firstOrNull { it.accountId == id }
            ?.let { readOnlyFeedStatus(this, it) }
            ?.label()
            ?: if (single) stringResource(R.string.read_only_url) else serverUrl.withoutScheme()
    else -> serverUrl.withoutScheme()
}

/** `https://www.example.com/feed/` → `example.com/feed`, short enough for one line. */
private fun String.withoutScheme(): String =
    substringAfter("://").removePrefix("www.").trimEnd('/')

@Composable
private fun syncIntervalLabel(minutes: Int): String = when {
    minutes >= 24 * 60 -> stringResource(R.string.sync_daily)
    minutes == 60 -> stringResource(R.string.sync_hourly)
    minutes > 60 -> stringResource(R.string.sync_every_hours, minutes / 60)
    else -> stringResource(R.string.sync_every_minutes, minutes)
}

/** A row that appears and disappears with the setting it depends on. */
@Composable
private fun ColumnScope.SettingsReveal(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(MotionMedium, easing = MotionStandard)) +
            expandVertically(animationSpec = tween(MotionMedium, easing = MotionStandard)),
        exit = fadeOut(animationSpec = tween(120, easing = MotionStandardAccelerate)) +
            shrinkVertically(animationSpec = tween(160, easing = MotionStandardAccelerate)),
    ) {
        content()
    }
}

/** A titled block of form fields on the page background. */
@Composable
private fun SettingsForm(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Text(
                title,
                color = WarmBrown,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        content()
    }
}

/** A titled list that draws its own rounded rows, such as the reorderable calendar list. */
@Composable
private fun SettingsListSection(title: String? = null, footer: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                title,
                color = WarmBrown,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        content()
        if (footer != null) {
            SettingsFootnote(footer, Modifier.padding(top = 8.dp))
        }
    }
}

/** An outlined, full-width button for a page's second action. */
@Composable
private fun SettingsSecondaryAction(
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        border = null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = settingsControlColor(),
            contentColor = WarmBrown,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(52.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** A row that opens a web page. */
@Composable
private fun SettingsLinkRow(
    title: String,
    icon: ImageVector,
    url: String,
    subtitle: String? = null,
    iconColor: Color? = null,
) {
    val context = LocalContext.current
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconColor = iconColor,
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
    ) {
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A widget's color scheme and light or dark mode. */
@Composable
private fun WidgetLookGroup(
    themeMode: WidgetThemeMode,
    colorMode: WidgetColorMode,
    onThemeSelected: (WidgetThemeMode) -> Unit,
    onColorModeSelected: (WidgetColorMode) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.widget_look)) {
        SettingsDropdownRow(
            title = stringResource(R.string.color_scheme),
            options = listOf(
                WidgetThemeMode.FollowApp,
                WidgetThemeMode.KgsBlue,
                WidgetThemeMode.KgsWarm,
                WidgetThemeMode.KgsFresh,
                WidgetThemeMode.SystemDynamic,
            ),
            selected = themeMode,
            label = { it.localizedLabel() },
            onSelected = onThemeSelected,
        )
        SettingsDropdownRow(
            title = stringResource(R.string.color_mode),
            options = listOf(WidgetColorMode.FollowApp, WidgetColorMode.FollowOs, WidgetColorMode.Light, WidgetColorMode.Dark),
            selected = colorMode,
            label = { it.localizedLabel() },
            onSelected = onColorModeSelected,
        )
    }
}

private fun Modifier.rotateWhen(rotated: Boolean): Modifier = rotate(if (rotated) 90f else 0f)

@Composable
internal fun ProblemsPage(
    problems: List<ProblemItem>,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onClose: () -> Unit,
    onResolveConflict: (resourceHref: String, keepLocal: Boolean) -> Unit = { _, _ -> },
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(90f),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .centeredMaxWidth(largeScreenMaxWidth(SettingsContentMaxWidth))
                .fillMaxSize()
                .padding(top = statusTop + 8.dp, bottom = navBottom),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = WarmInk, modifier = Modifier.size(25.dp))
                }
                Text(
                    stringResource(R.string.problems),
                    color = WarmInk,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (problems.isEmpty()) {
                    Box(Modifier.clip(SettingsGroupShape)) {
                        SettingsInfoRow(stringResource(R.string.no_problems), stringResource(R.string.all_synced))
                    }
                } else {
                    problems.forEach { problem ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SettingsControlShape)
                                .clickable(
                                    enabled = problem.target != null,
                                    onClick = {
                                        when (val target = problem.target) {
                                            is ProblemTarget.Event -> onEventClick(target.event)
                                            is ProblemTarget.Task -> onTaskClick(target.task)
                                            null -> Unit
                                        }
                                    },
                                ),
                            shape = SettingsControlShape,
                            color = settingsControlColor(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = SyncPendingOrange,
                                    modifier = Modifier.size(22.dp),
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        problem.title,
                                        color = WarmInk,
                                        fontSize = 14.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        problem.body,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                    )
                                    problem.conflictResourceHref?.let { href ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = { onResolveConflict(href, true) }) {
                                                Text(stringResource(R.string.sync_conflict_keep_mine))
                                            }
                                            TextButton(onClick = { onResolveConflict(href, false) }) {
                                                Text(stringResource(R.string.sync_conflict_use_server))
                                            }
                                        }
                                    }
                                }
                                if (problem.target != null) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsDestination.localizedTitle(): String = when (this) {
    SettingsDestination.Main -> stringResource(R.string.settings)
    SettingsDestination.Accounts -> stringResource(R.string.settings_calendars)
    SettingsDestination.AddSource -> stringResource(R.string.add_calendar)
    SettingsDestination.AddAccount -> stringResource(R.string.caldav_account)
    SettingsDestination.AddReadOnly -> stringResource(R.string.read_only_calendar)
    SettingsDestination.AccountDetail -> stringResource(R.string.edit_source)
    SettingsDestination.Views -> stringResource(R.string.settings_views)
    SettingsDestination.NewItems -> stringResource(R.string.settings_new_items)
    SettingsDestination.Appearance -> stringResource(R.string.appearance)
    SettingsDestination.General -> stringResource(R.string.settings_general)
    SettingsDestination.Widgets -> stringResource(R.string.widgets)
    SettingsDestination.WidgetAgenda -> stringResource(R.string.widget_agenda_name)
    SettingsDestination.WidgetMonth -> stringResource(R.string.widget_month_name)
    SettingsDestination.WidgetTasks -> stringResource(R.string.widget_tasks_name)
    SettingsDestination.WidgetMulti -> stringResource(R.string.widget_multi_name)
    SettingsDestination.WidgetDay -> stringResource(R.string.widget_day_name)
    SettingsDestination.Privacy -> stringResource(R.string.privacy)
    SettingsDestination.About -> stringResource(R.string.about_kgs_calendar)
    SettingsDestination.EventFieldOrder -> stringResource(R.string.event_fields)
    SettingsDestination.TaskFieldOrder -> stringResource(R.string.task_fields)
}

@Composable
private fun PrivacyPolicyPage() {
    SettingsGroup {
        SettingsLinkRow(
            title = stringResource(R.string.open_privacy_policy),
            icon = Icons.Default.Lock,
            url = PrivacyPolicyUrl,
        )
    }
    SettingsGroup {
        PrivacyTextRow(
            title = stringResource(R.string.privacy_storage_title),
            body = stringResource(R.string.privacy_storage_body),
        )
        PrivacyTextRow(
            title = stringResource(R.string.privacy_sync_title),
            body = stringResource(R.string.privacy_sync_body),
        )
        PrivacyTextRow(
            title = stringResource(R.string.privacy_location_title),
            body = stringResource(R.string.privacy_location_body),
        )
        PrivacyTextRow(
            title = stringResource(R.string.privacy_delete_title),
            body = stringResource(R.string.privacy_delete_body),
        )
    }
}

private const val PrivacyPolicyUrl = "https://kgs501.github.io/kgs-calendar/"
private const val BugReportIssuesUrl = "https://github.com/KGS501/kgs-calendar/issues"
private const val SourceCodeUrl = "https://github.com/KGS501/kgs-calendar"
private const val ReleasesUrl = "https://github.com/KGS501/kgs-calendar/releases"

/**
 * The muted colors of the settings areas' icons: each area is recognizable at a glance, while the
 * soft tint keeps the page calm.
 */
private object SettingsColors {
    val Calendars = Color(0xFF4F6D9A)
    val Views = Color(0xFF6A6FA8)
    val NewItems = Color(0xFF5E8C6A)
    val Appearance = Color(0xFF8C6A9E)
    val General = Color(0xFF5F7682)
    val Widgets = Color(0xFFB0785A)
    val Trash = Color(0xFFA8605E)
    val About = Color(0xFF4E8A8C)
    val Sponsor = Color(0xFF9A6070)
    val Bug = Color(0xFFA08A4E)
    val Privacy = Color(0xFF4E8A8C)
}

/** The installed version name, e.g. "V.1.4.5-2". */
@Composable
private fun appVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}

/** The app's mark, name and version at the top of the about page. */
@Composable
private fun AboutIdentityCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(SettingsGroupShape)
            .background(settingsControlColor())
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(
            painterResource(R.drawable.kgs_logo_vector),
            contentDescription = null,
            // The mark fills about 62 % of this drawable; at 92 dp it matches the 56 dp mark in KGS Notes.
            modifier = Modifier.size(92.dp),
        )
        Text("KGS Calendar", color = WarmInk, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.version_value, appVersionName()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, lineHeight = 20.sp)
        Text(
            stringResource(R.string.about_tagline),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp),
        )
    }
}
internal const val SponsorProjectUrl = "https://github.com/sponsors/KGS501"
private const val GoogleCalendarSyncSelectUrl = "https://calendar.google.com/calendar/syncselect"

@Composable
private fun AndroidProviderDiagnosticsCard(
    hiddenCalendarNames: List<String>,
    showDisabledProviderCalendars: Boolean,
    onDisabledProviderCalendarsVisibleChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val collapsedCount = 3
    val shownNames = if (expanded) hiddenCalendarNames else hiddenCalendarNames.take(collapsedCount)
    val remaining = (hiddenCalendarNames.size - collapsedCount).coerceAtLeast(0)
    val cardColor = SyncPendingOrange.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.26f else 0.14f)
    val textColor = WarmInk
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsControlShape,
        color = cardColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = SyncPendingOrange,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    stringResource(R.string.android_hidden_calendars_title),
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    shownNames.forEach { name ->
                        Text(
                            "- $name",
                            color = textColor,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                        )
                    }
                    if (!expanded && remaining > 0) {
                        Text(
                            stringResource(R.string.more_hidden_calendars, remaining),
                            color = WarmBrown,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { expanded = true }
                                .padding(vertical = 2.dp),
                        )
                    } else if (expanded && remaining > 0) {
                        TextButton(onClick = { expanded = false }) {
                            Text(stringResource(R.string.show_less))
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.android_hidden_calendars_help),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GoogleCalendarSyncSelectUrl))) },
                    shape = SettingsControlShape,
                    modifier = Modifier.fillMaxWidth().height(SettingsControlHeight),
                ) {
                    Text(stringResource(R.string.open_google_syncselect))
                }
                Button(
                    onClick = { onDisabledProviderCalendarsVisibleChanged(!showDisabledProviderCalendars) },
                    shape = SettingsControlShape,
                    colors = ButtonDefaults.buttonColors(containerColor = WarmBrown),
                    modifier = Modifier.fillMaxWidth().height(SettingsControlHeight),
                ) {
                    Text(
                        if (showDisabledProviderCalendars) {
                            stringResource(R.string.hide_disabled_provider_calendars)
                        } else {
                            stringResource(R.string.show_disabled_provider_calendars)
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyTextRow(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(settingsControlColor())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = WarmInk, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
        SelectionContainer {
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
internal fun CollectionSettingsSheet(
    collection: CollectionEntity,
    visibleInViews: Boolean,
    onSave: (String, Int?) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onVisibleInViewsChanged: (Boolean) -> Unit,
    eventsVisible: Boolean = true,
    tasksVisible: Boolean = true,
    onEventsVisibleChanged: (Boolean) -> Unit = {},
    onTasksVisibleChanged: (Boolean) -> Unit = {},
    categoriesSynced: Boolean = true,
    onCategoriesSyncedChanged: (Boolean) -> Unit = {},
    onDelete: (() -> Unit)? = null,
    groupName: String? = null,
    onChooseGroup: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    var name by remember(collection.href) { mutableStateOf(collection.displayName) }
    var customColor by remember(collection.href) { mutableStateOf(collection.customColor) }
    var enabled by remember(collection.href) { mutableStateOf(collection.isEnabled) }
    var confirmDelete by remember(collection.href) { mutableStateOf(false) }
    var metadataExpanded by remember(collection.href) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val metadataRows = collection.localizedPermissionMetadataRows()
    val automaticColor = collection.automaticColor ?: collection.sourceColor ?: collection.color
    val automaticDescription = if (collection.sourceColor != null) {
        stringResource(R.string.source_color_help)
    } else {
        stringResource(R.string.auto_color_help)
    }

    // The sheet is as light as the cards, so its groups take the page color to stand out.
    CompositionLocalProvider(LocalSettingsRowColor provides MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EditorTopBar(
                title = stringResource(R.string.calendar),
                onClose = onClose,
                onSave = { onSave(name, customColor) },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (collection.supportsEvents) CalendarCapabilityChip(stringResource(R.string.events), Color(0xFF2F5AEA))
                if (collection.supportsTasks) CalendarCapabilityChip(stringResource(R.string.tasks), Color(0xFF00A86B))
            }
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            SettingsGroup(horizontalPadding = 0.dp) {
                SettingsColorRow(
                    title = stringResource(R.string.color),
                    subtitle = if (customColor == null) automaticDescription else stringResource(R.string.custom_color_active),
                    selectedColor = customColor,
                    automaticColor = automaticColor,
                    onColorSelected = { customColor = it },
                )
                if (groupName != null && onChooseGroup != null) {
                    SettingsNavRow(
                        title = stringResource(R.string.calendar_group_choose),
                        subtitle = groupName,
                        icon = Icons.Default.Folder,
                        onClick = onChooseGroup,
                    )
                }
            }
            SettingsGroup(horizontalPadding = 0.dp) {
                SettingsSwitchRow(
                    title = appString(R.string.calendar_show_in_views),
                    subtitle = appString(R.string.calendar_hide_mutes_reminders),
                    checked = visibleInViews,
                    onCheckedChange = onVisibleInViewsChanged,
                    warningUnchecked = !visibleInViews,
                )
                // Calendars with both events and tasks can hide one of the two. Hiding both hides the calendar.
                if (collection.supportsEvents && collection.supportsTasks) {
                    SettingsReveal(visible = visibleInViews) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            SettingsSwitchRow(
                                title = appString(R.string.calendar_show_events),
                                checked = eventsVisible,
                                onCheckedChange = onEventsVisibleChanged,
                                warningUnchecked = !eventsVisible,
                            )
                            SettingsSwitchRow(
                                title = appString(R.string.calendar_show_tasks),
                                checked = tasksVisible,
                                onCheckedChange = onTasksVisibleChanged,
                                warningUnchecked = !tasksVisible,
                            )
                        }
                    }
                }
                // Device calendars have no categories.
                if (!collection.isAndroidProviderCollection()) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.calendar_use_categories),
                        subtitle = stringResource(R.string.calendar_use_categories_help),
                        checked = categoriesSynced,
                        onCheckedChange = onCategoriesSyncedChanged,
                    )
                }
                SettingsSwitchRow(
                    title = stringResource(R.string.active),
                    subtitle = stringResource(R.string.calendar_active_help),
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        onEnabledChanged(it)
                    },
                )
            }
            SettingsGroup(horizontalPadding = 0.dp) {
                SettingsRow(
                    title = stringResource(R.string.metadata),
                    onClick = { metadataExpanded = !metadataExpanded },
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp).rotateWhen(metadataExpanded),
                    )
                }
                SettingsReveal(visible = metadataExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(settingsControlColor())
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        metadataRows.forEach { row ->
                            CalendarMetadataRow(label = row.first, value = row.second)
                        }
                    }
                }
            }
            if (onDelete != null) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_server_calendar), color = MaterialTheme.colorScheme.error, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
    if (confirmDelete) {
        KgsAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_server_calendar)) },
            text = { Text(stringResource(R.string.delete_server_calendar_warning, collection.displayName)) },
            confirmButton = {
                DialogConfirmButton(
                    text = stringResource(R.string.delete),
                    destructive = true,
                    onClick = {
                        confirmDelete = false
                        onDelete?.invoke()
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

internal fun String?.toJsonObjectOrNull(): JSONObject? =
    takeIf { !it.isNullOrBlank() }?.let { json ->
        runCatching { JSONObject(json) }.getOrNull()
    }

internal fun JSONObject.optBooleanOrNull(name: String): Boolean? =
    if (has(name) && !isNull(name)) optBoolean(name) else null

internal fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

internal fun JSONObject.optStringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) optString(name) else null

internal fun CollectionEntity.eventEditorCapabilities(): EventEditorCapabilities =
    if (!isAndroidProviderCollection()) {
        EventEditorCapabilities.Full
    } else {
        EventEditorCapabilities(
            recurrence = true,
            reminders = capabilitiesJson.toJsonObjectOrNull()?.optBooleanOrNull("reminders") != false,
            location = true,
            notes = true,
            status = false,
            categories = false,
            color = true,
            participants = false,
        )
    }

internal fun CollectionEntity.isVisibleInSettingsCalendarList(): Boolean =
    !(href.isLocalCollectionHref() && !isEnabled)
