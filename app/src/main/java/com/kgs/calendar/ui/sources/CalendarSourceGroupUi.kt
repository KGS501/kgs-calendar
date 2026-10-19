package com.kgs.calendar.ui.sources

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.R
import com.kgs.calendar.data.account.SourceImage
import com.kgs.calendar.data.account.SourceImageKeys
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.domain.source.CalendarSourceGroup
import com.kgs.calendar.domain.source.CalendarSourceGroupKind
import com.kgs.calendar.domain.source.CalendarSourceGroups
import com.kgs.calendar.domain.source.ReadOnlyFeedStatus
import com.kgs.calendar.ui.WarmBrown
import com.kgs.calendar.ui.WarmInk
import com.kgs.calendar.ui.components.ClearableOutlinedTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The group's name as shown: the user's name, the source's name, or the localized kind. */
@Composable
internal fun CalendarSourceGroup.displayName(): String =
    name ?: when (kind) {
        CalendarSourceGroupKind.Local -> stringResource(R.string.local_calendar)
        CalendarSourceGroupKind.AndroidDevice -> stringResource(R.string.android_device_calendars)
        else -> stringResource(R.string.calendar_group_custom)
    }

/** The name without the user's rename, for "automatic" labels and the reset hint. */
@Composable
internal fun CalendarSourceGroup.automaticName(): String =
    defaultName ?: copy(customName = null).displayName()

@Composable
internal fun CalendarSourceGroup.subtitle(): String = when (kind) {
    CalendarSourceGroupKind.Local -> stringResource(R.string.local_calendar)
    CalendarSourceGroupKind.AndroidDevice -> stringResource(R.string.android_device_calendars)
    CalendarSourceGroupKind.ReadOnlyUrl -> stringResource(R.string.read_only_url)
    CalendarSourceGroupKind.Custom -> stringResource(R.string.calendar_group_custom)
    CalendarSourceGroupKind.CalDav -> stringResource(R.string.nextcloud)
}

/** The user's picture for the group, else the first picture one of its sources provides. */
internal fun CalendarSourceGroup.image(images: Map<String, SourceImage>): SourceImage? =
    images[SourceImageKeys.group(key)]
        ?: accounts.firstNotNullOfOrNull { images[SourceImageKeys.account(it.id)] }

internal fun CalendarSourceGroup.hasCustomImage(images: Map<String, SourceImage>): Boolean =
    SourceImageKeys.group(key) in images

/** The group that holds [collection], and its automatic group when the user moved it elsewhere. */
internal fun List<CalendarSourceGroup>.groupOf(collection: CollectionEntity): CalendarSourceGroup? =
    firstOrNull { group -> group.collections.any { it.href == collection.href } }

internal fun List<CalendarSourceGroup>.automaticGroupOf(
    collection: CollectionEntity,
    accounts: List<AccountEntity>,
): CalendarSourceGroup? {
    val account = accounts.firstOrNull { it.id == collection.accountId } ?: return null
    val key = CalendarSourceGroups.automaticKey(account, accounts)
    return firstOrNull { it.key == key }
}

@Composable
internal fun ReadOnlyFeedStatus.label(): String = when (this) {
    ReadOnlyFeedStatus.Loading -> stringResource(R.string.feed_status_loading)
    ReadOnlyFeedStatus.NoDataYet -> stringResource(R.string.feed_status_no_data)
    ReadOnlyFeedStatus.Empty -> stringResource(R.string.feed_status_empty)
    ReadOnlyFeedStatus.UpdateFailed -> stringResource(R.string.feed_status_update_failed)
}

/** A round source picture, or the initial of [name] when there is none. */
@Composable
internal fun SourceAvatar(name: String, image: SourceImage?, size: Dp, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, image?.path, image?.version) {
        value = image?.let { stored ->
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(stored.path)?.asImageBitmap() }.getOrNull() }
        }
    }
    Box(modifier.size(size).clip(CircleShape).background(WarmBrown), contentAlignment = Alignment.Center) {
        val loaded = bitmap
        if (loaded != null) {
            Image(loaded, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text(
                name.trim().take(1).uppercase().ifBlank { "?" },
                color = Color.White,
                fontSize = (size.value * 0.43f).sp,
                lineHeight = (size.value * 0.5f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Renames a group and sets or removes its picture. */
@Composable
internal fun CalendarGroupSettingsDialog(
    group: CalendarSourceGroup,
    images: Map<String, SourceImage>,
    onRename: (String?) -> Unit,
    onPickImage: (Uri) -> Unit,
    onRemoveImage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentName = group.displayName()
    val automaticName = group.automaticName()
    var name by rememberSaveable(group.key) { mutableStateOf(currentName) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPickImage)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_group_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SourceAvatar(name.ifBlank { automaticName }, group.image(images), 56.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Text(stringResource(R.string.calendar_group_choose_image))
                        }
                        if (group.hasCustomImage(images)) {
                            TextButton(onClick = onRemoveImage) { Text(stringResource(R.string.calendar_group_remove_image)) }
                        }
                    }
                }
                Text(
                    stringResource(R.string.calendar_group_image_help),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                )
                ClearableOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.calendar_group_name)) },
                    placeholder = { Text(automaticName) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (group.customName != null && group.kind != CalendarSourceGroupKind.Custom) {
                    TextButton(onClick = { name = automaticName }) {
                        Text(stringResource(R.string.calendar_group_reset_name))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    // The automatic name needs no override; a custom group always keeps a name.
                    onRename(
                        when {
                            trimmed.isEmpty() || (trimmed == automaticName && group.kind != CalendarSourceGroupKind.Custom) -> null
                            else -> trimmed
                        },
                    )
                    onDismiss()
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * Picks the sidebar group of [collection]: its automatic group, any other group, or a new one.
 * [onSelect] gets null for the automatic group.
 */
@Composable
internal fun CalendarGroupPickerDialog(
    collection: CollectionEntity,
    groups: List<CalendarSourceGroup>,
    accounts: List<AccountEntity>,
    images: Map<String, SourceImage>,
    onSelect: (String?) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = groups.groupOf(collection)
    val automatic = groups.automaticGroupOf(collection, accounts)
    var creating by rememberSaveable(collection.href) { mutableStateOf(false) }
    var newName by rememberSaveable(collection.href) { mutableStateOf("") }
    val choices = remember(groups, automatic) {
        listOfNotNull(automatic) + groups.filter { it.key != automatic?.key && it.collections.isNotEmpty() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_group_choose)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.calendar_group_help),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                choices.forEach { group ->
                    val label = if (group.key == automatic?.key) {
                        stringResource(R.string.calendar_group_automatic, group.displayName())
                    } else {
                        group.displayName()
                    }
                    GroupChoiceRow(
                        label = label,
                        avatarName = group.displayName(),
                        image = group.image(images),
                        selected = group.key == current?.key,
                    ) {
                        onSelect(group.key.takeUnless { it == automatic?.key })
                        onDismiss()
                    }
                }
                if (creating) {
                    ClearableOutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.calendar_group_new_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { creating = true }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = WarmInk, modifier = Modifier.size(28.dp))
                        Text(stringResource(R.string.calendar_group_new), color = WarmInk, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        onCreate(newName.trim())
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.save)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun GroupChoiceRow(
    label: String,
    avatarName: String,
    image: SourceImage?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SourceAvatar(avatarName, image, 28.dp)
        Text(
            label,
            color = WarmInk,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = WarmInk, modifier = Modifier.size(20.dp))
    }
}
