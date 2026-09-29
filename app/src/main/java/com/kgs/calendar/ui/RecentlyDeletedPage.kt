package com.kgs.calendar.ui

import androidx.annotation.PluralsRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.TrashedItemEntity
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.data.trash.TrashedItemPreview
import com.kgs.calendar.domain.trash.TrashOrigin
import com.kgs.calendar.domain.trash.TrashRetention
import com.kgs.calendar.ui.layout.ListContentMaxWidth
import com.kgs.calendar.ui.layout.centeredContentPadding
import com.kgs.calendar.ui.layout.centeredMaxWidth
import com.kgs.calendar.ui.layout.currentCalendarWindowLayout
import com.kgs.calendar.ui.layout.largeScreenMaxWidth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * "Recently deleted", opened from the sidebar: the deleted events and tasks as their usual cards
 * (the ones search and the agenda use), ordered by event/task date. A tap
 * opens the item's detail sheet, which offers restore and permanent delete.
 */
@Composable
internal fun RecentlyDeletedPage(
    trash: TrashUiState,
    taskColorMode: TaskColorMode,
    onItemClick: (TrashedItemPreview) -> Unit,
    onEmptyTrash: () -> Unit,
    onDismissNotice: () -> Unit,
    onOpened: () -> Unit,
    onClose: () -> Unit,
) {
    LaunchedEffect(Unit) { onOpened() }
    var emptyTrashConfirmOpen by rememberSaveable { mutableStateOf(false) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val entries = remember(trash.entries) {
        trash.entries.map { it to it.searchResult() }
            .sortedWith(compareBy<Pair<TrashedItemPreview, CalendarSearchResult>> { it.second.sortMillis }
                .thenBy { it.first.item.id })
    }
    val background = MaterialTheme.colorScheme.background
    // The cards show the snapshot as it was deleted; nothing of the live sync state applies to them.
    CompositionLocalProvider(
        LocalPendingMutations provides emptyList(),
        LocalExitingResourceHrefs provides emptySet(),
    ) {
        Surface(
            // No z-index: the detail sheet, composed after this page, has to open over it.
            modifier = Modifier
                .fillMaxSize()
                .testTag("recently_deleted_page"),
            color = background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusTop + 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .centeredMaxWidth(largeScreenMaxWidth(ListContentMaxWidth))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = WarmInk, modifier = Modifier.size(25.dp))
                    }
                    Text(
                        stringResource(R.string.recently_deleted),
                        color = WarmInk,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (trash.entries.isNotEmpty()) {
                        IconButton(
                            onClick = { emptyTrashConfirmOpen = true },
                            modifier = Modifier.size(40.dp).testTag("empty_trash"),
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.empty_trash),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
                if (trash.entries.isEmpty()) {
                    RecentlyDeletedEmptyState(Modifier.fillMaxSize().padding(bottom = navBottom))
                } else {
                    val listSidePadding = centeredContentPadding(
                        availableWidth = currentCalendarWindowLayout().widthDp.dp,
                        maxWidth = largeScreenMaxWidth(ListContentMaxWidth),
                        basePadding = 18.dp,
                    )
                    val taskHierarchy = rememberTaskHierarchyPresentation(emptyList(), expandedByDefault = false)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("recently_deleted_list"),
                        contentPadding = PaddingValues(start = listSidePadding, top = 8.dp, end = listSidePadding, bottom = navBottom + 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        entries.forEachIndexed { index, (entry, result) ->
                            val showDate = index == 0 || result.date != entries[index - 1].second.date
                            item(key = "trashed-${entry.item.id}") {
                                Box(Modifier.fillMaxWidth().testTag("trashed_item_${entry.item.id}")) {
                                    CalendarSearchResultRow(
                                        item = result,
                                        showDate = showDate,
                                        taskColorMode = taskColorMode,
                                        onTaskStatusChanged = { _, _ -> },
                                        agendaDateHierarchy = false,
                                        showCalendarWeeks = false,
                                        firstDayOfWeek = java.time.DayOfWeek.MONDAY,
                                        taskHierarchy = taskHierarchy,
                                        onEventClick = { onItemClick(entry) },
                                        onTaskClick = { onItemClick(entry) },
                                        taskStatusToggleEnabled = false,
                                        // Being in the past means nothing here: show the cards in their normal colours.
                                        mutePastEvents = false,
                                    )
                                }
                            }
                        }
                        item(key = "recently-deleted-help") {
                            Text(
                                stringResource(R.string.recently_deleted_help, TrashRetention.DAYS),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(start = 62.dp, top = 10.dp, end = 8.dp),
                            )
                        }
                        item { Spacer(Modifier.height(40.dp)) }
                    }
                }
            }
        }
    }
    if (emptyTrashConfirmOpen) {
        val count = trash.entries.size
        TrashConfirmDialog(
            title = appString(R.string.empty_trash_question),
            body = appPluralString(R.plurals.empty_trash_body, count, count),
            confirmLabel = appString(R.string.empty_trash),
            onConfirm = {
                emptyTrashConfirmOpen = false
                onEmptyTrash()
            },
            onDismiss = { emptyTrashConfirmOpen = false },
        )
    }
    trash.notice?.let { notice -> TrashNoticeDialog(notice, onDismissNotice) }
}

@Composable
private fun RecentlyDeletedEmptyState(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.recently_deleted_empty_title),
                color = WarmInk,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.recently_deleted_empty_body, TrashRetention.DAYS),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun TrashedItemPreview.searchResult(): CalendarSearchResult =
    task?.let(CalendarSearchResult::TaskItem) ?: CalendarSearchResult.Event(requireNotNull(event))

/**
 * The prominent top of the detail sheet of a "Recently deleted" item: where it is, when it was
 * deleted and how long it stays, with Restore and Delete permanently (after a confirmation).
 */
@Composable
internal fun TrashedItemDetailBanner(
    item: TrashedItemEntity,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
) {
    var deleteConfirmOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.background.isDark()
    val accent = WarmBrown
    val daysLeft = TrashRetention.daysLeft(item.expiresAtMillis, System.currentTimeMillis())
    val deletedLabel = stringResource(
        R.string.trash_deleted_on,
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(LocalAppLocale.current)
            .format(Instant.ofEpochMilli(item.deletedAtMillis).atZone(ZoneId.systemDefault())),
    )
    val remainingLabel = if (daysLeft > 0) {
        appPluralString(R.plurals.trash_days_left, daysLeft, daysLeft)
    } else {
        stringResource(R.string.trash_expires_today)
    }
    val sourceLabel = buildList {
        add(stringResource(R.string.trash_from_calendar, item.collectionName))
        if (item.origin == TrashOrigin.ServerTrashBin) add(stringResource(R.string.trash_in_nextcloud_trash_bin))
    }.joinToString(" · ")
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = if (dark) 0.26f else 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trashed_detail_banner"),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.trash_in_recently_deleted),
                        color = WarmInk,
                        fontSize = 17.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "$deletedLabel · $remainingLabel",
                        color = WarmInk,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    )
                    Text(
                        sourceLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
            // Stacked full width, so both labels fit in every language.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRestore,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = accentContainerContentColor()),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("trashed_detail_restore"),
                ) {
                    Icon(Icons.Default.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.restore),
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = { deleteConfirmOpen = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.46f)),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("trashed_detail_delete"),
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.delete_permanently),
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    if (deleteConfirmOpen) {
        TrashConfirmDialog(
            title = appString(R.string.delete_permanently_question, item.title.ifBlank { appString(R.string.no_title) }),
            body = appString(R.string.delete_irreversible),
            confirmLabel = appString(R.string.delete_permanently),
            onConfirm = {
                deleteConfirmOpen = false
                onDeletePermanently()
            },
            onDismiss = { deleteConfirmOpen = false },
        )
    }
}

/** A destructive confirmation in the style of the detail sheet's delete dialog. */
@Composable
private fun TrashConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.padding(horizontal = 20.dp),
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = popupSurfaceColor(),
        titleContentColor = WarmInk,
        textContentColor = WarmInk,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(appString(R.string.cancel)) } },
    )
}

@Composable
private fun TrashNoticeDialog(notice: TrashNotice, onDismiss: () -> Unit) {
    val noTitle = appString(R.string.no_title)
    fun String.orNoTitle() = ifBlank { noTitle }
    val title = when (notice) {
        is TrashNotice.RestoredElsewhere -> appString(R.string.restored_elsewhere_title)
        is TrashNotice.DeleteFailed -> appString(R.string.delete_permanently_failed_title)
        is TrashNotice.EmptyTrashFailed -> appString(R.string.empty_trash_failed_title)
        else -> appString(R.string.restore_failed_title)
    }
    val body = when (notice) {
        is TrashNotice.RestoredElsewhere -> appString(R.string.restored_elsewhere_body, notice.title.orNoTitle(), notice.collectionName)
        is TrashNotice.NoWritableCalendar -> appString(R.string.restore_failed_no_calendar, notice.title.orNoTitle())
        is TrashNotice.AlreadyExists -> appString(R.string.restore_failed_exists, notice.title.orNoTitle())
        is TrashNotice.Unreadable -> appString(R.string.restore_failed_unreadable, notice.title.orNoTitle())
        is TrashNotice.GoneFromServer -> appString(R.string.restore_failed_gone_from_server, notice.title.orNoTitle())
        is TrashNotice.ServerRefused -> appString(R.string.restore_failed_server_refused, notice.title.orNoTitle(), notice.statusCode)
        is TrashNotice.Failed -> appString(R.string.restore_failed_generic, notice.title.orNoTitle(), notice.reason)
        is TrashNotice.DeleteFailed -> notice.statusCode
            ?.let { appString(R.string.delete_permanently_failed_server_refused, notice.title.orNoTitle(), it) }
            ?: appString(R.string.delete_permanently_failed_generic, notice.title.orNoTitle(), notice.reason)
        is TrashNotice.EmptyTrashFailed -> notice.statusCode
            ?.let { appString(R.string.empty_trash_failed_server_refused, it) }
            ?: appString(R.string.empty_trash_failed_generic, notice.reason)
    }
    AlertDialog(
        modifier = Modifier.padding(horizontal = 20.dp),
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = popupSurfaceColor(),
        title = { Text(title, color = WarmInk, fontWeight = FontWeight.SemiBold) },
        text = { Text(body, color = WarmInk, lineHeight = 20.sp) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(appString(R.string.close)) }
        },
    )
}

/** Like [appString], for plurals: the app's language also inside dialogs. */
@Composable
internal fun appPluralString(@PluralsRes id: Int, count: Int, vararg args: Any): String {
    val context = LocalContext.current
    val locale = LocalAppLocale.current
    return remember(id, count, args.toList(), context, locale) {
        context.withAppLocale(locale).resources.getQuantityString(id, count, *args)
    }
}
