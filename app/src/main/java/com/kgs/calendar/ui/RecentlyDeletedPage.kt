package com.kgs.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.TrashedItemEntity
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.time.toDate
import com.kgs.calendar.domain.time.toTimeText
import com.kgs.calendar.domain.trash.TrashRetention
import java.time.format.DateTimeFormatter

/** Summary line of the "Recently deleted" row in the calendar settings. */
@Composable
internal fun recentlyDeletedSummary(itemCount: Int): String =
    if (itemCount == 0) {
        stringResource(R.string.recently_deleted_empty_summary)
    } else {
        pluralStringResource(R.plurals.recently_deleted_count, itemCount, itemCount, TrashRetention.DAYS)
    }

/** Settings subpage listing the trashed events and tasks, with restore and permanent delete. */
@Composable
internal fun RecentlyDeletedSettings(
    trash: TrashUiState,
    onRestore: (TrashedItemEntity) -> Unit,
    onDeletePermanently: (TrashedItemEntity) -> Unit,
    onEmptyTrash: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    var deleteCandidate by remember { mutableStateOf<TrashedItemEntity?>(null) }
    var emptyTrashConfirmOpen by remember { mutableStateOf(false) }
    SettingsSection(title = stringResource(R.string.recently_deleted), icon = Icons.Default.Delete) {
        if (trash.items.isEmpty()) {
            SettingsInfoRow(
                stringResource(R.string.recently_deleted_empty_title),
                stringResource(R.string.recently_deleted_empty_body, TrashRetention.DAYS),
            )
        } else {
            trash.items.forEach { item ->
                key(item.id) {
                    TrashedItemRow(
                        item = item,
                        onRestore = { onRestore(item) },
                        onDeletePermanently = { deleteCandidate = item },
                    )
                }
            }
            Button(
                onClick = { emptyTrashConfirmOpen = true },
                shape = SettingsControlShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(SettingsControlHeight).testTag("empty_trash"),
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.empty_trash))
            }
        }
        SettingsHelpText(stringResource(R.string.recently_deleted_help, TrashRetention.DAYS))
    }
    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.delete_permanently_question, item.displayTitle())) },
            text = { Text(stringResource(R.string.delete_irreversible)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePermanently(item)
                        deleteCandidate = null
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (emptyTrashConfirmOpen) {
        AlertDialog(
            onDismissRequest = { emptyTrashConfirmOpen = false },
            title = { Text(stringResource(R.string.empty_trash_question)) },
            text = { Text(pluralStringResource(R.plurals.empty_trash_body, trash.items.size, trash.items.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEmptyTrash()
                        emptyTrashConfirmOpen = false
                    },
                ) {
                    Text(stringResource(R.string.empty_trash), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { emptyTrashConfirmOpen = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    trash.notice?.let { notice ->
        val title = notice.title.ifBlank { stringResource(R.string.no_title) }
        AlertDialog(
            modifier = Modifier.padding(horizontal = 20.dp),
            onDismissRequest = onDismissNotice,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    stringResource(
                        if (notice is TrashNotice.RestoredElsewhere) R.string.restored_elsewhere_title else R.string.restore_failed_title,
                    ),
                    color = WarmInk,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    when (notice) {
                        is TrashNotice.RestoredElsewhere -> stringResource(R.string.restored_elsewhere_body, title, notice.collectionName)
                        is TrashNotice.NoWritableCalendar -> stringResource(R.string.restore_failed_no_calendar, title)
                        is TrashNotice.AlreadyExists -> stringResource(R.string.restore_failed_exists, title)
                        is TrashNotice.Unreadable -> stringResource(R.string.restore_failed_unreadable, title)
                        is TrashNotice.Failed -> stringResource(R.string.restore_failed_generic, title, notice.reason)
                    },
                    color = WarmInk,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissNotice) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun TrashedItemRow(
    item: TrashedItemEntity,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
) {
    val itemColor = Color(item.manualColor ?: item.collectionColor)
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("trashed_item_${item.id}"),
        shape = SettingsControlShape,
        color = settingsControlColor(),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.padding(end = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (item.componentType == ComponentType.Task) Icons.Default.TaskAlt else Icons.Default.Event,
                    contentDescription = stringResource(if (item.componentType == ComponentType.Task) R.string.task else R.string.event),
                    tint = itemColor,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        item.displayTitle(),
                        color = WarmInk,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(item.collectionColor)),
                        )
                        Text(
                            "${item.collectionName} · ${item.originalDateLabel()}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        stringResource(R.string.trash_deleted_on, item.deletedAtMillis.toDate().format(DateTimeFormatter.ofPattern("d. MMM yyyy", LocalAppLocale.current))),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDeletePermanently) {
                    Text(stringResource(R.string.delete_permanently), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onRestore) {
                    Icon(Icons.Default.RestoreFromTrash, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.restore), color = WarmBrown)
                }
            }
        }
    }
}

@Composable
private fun TrashedItemEntity.displayTitle(): String = title.ifBlank { stringResource(R.string.no_title) }

@Composable
private fun TrashedItemEntity.originalDateLabel(): String {
    val start = startMillis ?: return stringResource(R.string.no_date)
    val date = start.toDate().format(DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", LocalAppLocale.current))
    return if (hasTime) "$date, ${start.toTimeText()}" else date
}
