package com.kgs.calendar.ui.sources

import com.kgs.calendar.ui.dialogFieldColors
import com.kgs.calendar.ui.DialogFieldCaption
import com.kgs.calendar.ui.DialogDismissButton
import com.kgs.calendar.ui.DialogConfirmButton
import com.kgs.calendar.ui.DialogChoiceRow
import com.kgs.calendar.ui.DialogChoiceGroup
import com.kgs.calendar.ui.ColorChoiceRow
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.semantics.Role
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
import com.kgs.calendar.domain.source.CalendarGroupSettings
import com.kgs.calendar.domain.source.CalendarSourceGroup
import com.kgs.calendar.domain.source.CalendarSourceGroupKind
import com.kgs.calendar.domain.source.CalendarSourceGroups
import com.kgs.calendar.domain.source.ReadOnlyFeedStatus
import com.kgs.calendar.ui.KgsAlertDialog
import com.kgs.calendar.ui.WarmBrown
import com.kgs.calendar.ui.WarmInk
import com.kgs.calendar.ui.components.ClearableOutlinedTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The name the local calendar's source is created with; shown in the app's language until renamed. */
internal const val DefaultLocalSourceName = "Local calendar"

/** A source's name as shown; the untouched default name of the local calendar follows the app's language. */
@Composable
internal fun AccountEntity.shownName(): String {
    val stored = displayName?.takeIf { it.isNotBlank() } ?: username
    return if (id == "local" && stored == DefaultLocalSourceName) stringResource(R.string.local_calendar) else stored
}

/** The group's name as shown: the user's name, the source's name, or the localized kind. */
@Composable
internal fun CalendarSourceGroup.displayName(): String =
    customName ?: defaultName?.takeUnless { kind == CalendarSourceGroupKind.Local && it == DefaultLocalSourceName } ?: when (kind) {
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
    CalendarSourceGroupKind.Local -> stringResource(R.string.on_this_device)
    CalendarSourceGroupKind.AndroidDevice -> stringResource(R.string.android_device_calendars)
    CalendarSourceGroupKind.ReadOnlyUrl -> stringResource(R.string.read_only_url)
    CalendarSourceGroupKind.Custom -> stringResource(R.string.calendar_group_custom)
    CalendarSourceGroupKind.CalDav -> stringResource(R.string.nextcloud)
}

/**
 * The group's picture: a source's own group shows the source's picture; any other group only the
 * one the user picked for it, never that of a source whose calendar was moved in.
 */
internal fun CalendarSourceGroup.image(images: Map<String, SourceImage>): SourceImage? =
    owner?.image(images) ?: images[SourceImageKeys.group(key)]

/** The picture of a source: the one the user picked, else the one the source provides. */
internal fun AccountEntity.image(images: Map<String, SourceImage>): SourceImage? =
    images[SourceImageKeys.accountCustom(id)] ?: images[SourceImageKeys.account(id)]

/** Whether the user removed the picture this source provides, so it can be shown again. */
internal fun AccountEntity.providedImageHidden(settings: CalendarGroupSettings): Boolean =
    SourceImageKeys.account(id) in settings.hiddenImages

/**
 * Muted colors for the letter pictures of sources and groups. Each source gets one by its key,
 * so neighbors usually differ; the user can pick another.
 */
internal val AvatarPalette: List<Int> = listOf(
    0xFF4F6D9A.toInt(), // slate blue
    0xFF5E8C6A.toInt(), // sage
    0xFF8C6A9E.toInt(), // dusty violet
    0xFFB0785A.toInt(), // terracotta
    0xFF4E8A8C.toInt(), // teal
    0xFF9A6070.toInt(), // rosewood
    0xFF7C7A4F.toInt(), // olive
    0xFF6A6FA8.toInt(), // periwinkle
    0xFFA08A4E.toInt(), // ochre
    0xFF5F7682.toInt(), // steel
)

/** The automatic letter-picture color for [key]: stable, and spread over [AvatarPalette]. */
internal fun defaultAvatarColor(key: String): Int =
    AvatarPalette[Math.floorMod(key.hashCode() * 31 + key.length, AvatarPalette.size)]

/** The letter-picture color of a source. */
internal fun AccountEntity.avatarColor(settings: CalendarGroupSettings): Int =
    settings.avatarColors[SourceImageKeys.account(id)] ?: automaticAvatarColor()

/** The letter-picture color of a source when the user has not picked one. */
internal fun AccountEntity.automaticAvatarColor(): Int = defaultAvatarColor(SourceImageKeys.account(id))

/** The letter-picture color of a group; a source's own group has the source's color. */
internal fun CalendarSourceGroup.avatarColor(settings: CalendarGroupSettings): Int =
    settings.avatarColors[avatarColorKey()] ?: automaticAvatarColor()

/** The letter-picture color of a group when the user has not picked one for it. */
internal fun CalendarSourceGroup.automaticAvatarColor(): Int =
    owner?.automaticAvatarColor() ?: defaultAvatarColor(SourceImageKeys.group(key))

/** Where the letter-picture color of the group is kept: on its source, if it is a source's own group. */
internal fun CalendarSourceGroup.avatarColorKey(): String =
    owner?.let { SourceImageKeys.account(it.id) } ?: SourceImageKeys.group(key)

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
internal fun SourceAvatar(name: String, image: SourceImage?, size: Dp, color: Int, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, image?.path, image?.version) {
        value = image?.let { stored ->
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(stored.path)?.asImageBitmap() }.getOrNull() }
        }
    }
    Box(modifier.size(size).clip(CircleShape).background(Color(color)), contentAlignment = Alignment.Center) {
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

/**
 * A source's or group's picture with buttons to pick or remove one, and the color of the letter
 * picture shown when there is none. [onRestoreImage] shows a removed picture of the source again.
 * Changes apply right away.
 */
@Composable
internal fun AvatarAppearanceEditor(
    name: String,
    image: SourceImage?,
    color: Int,
    automaticColor: Int,
    customColor: Int?,
    onPickImage: (Uri) -> Unit,
    onRemoveImage: () -> Unit,
    onColorSelected: (Int?) -> Unit,
    onRestoreImage: (() -> Unit)? = null,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPickImage)
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SourceAvatar(name, image, 64.dp, color)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(stringResource(R.string.calendar_group_choose_image))
                }
                if (image != null) {
                    TextButton(onClick = onRemoveImage) { Text(stringResource(R.string.calendar_group_remove_image)) }
                } else if (onRestoreImage != null) {
                    TextButton(onClick = onRestoreImage) { Text(stringResource(R.string.source_image_restore)) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogFieldCaption(stringResource(R.string.avatar_color))
            ColorChoiceRow(
                selectedColor = customColor,
                automaticColor = automaticColor,
                onColorSelected = onColorSelected,
                contentPadding = PaddingValues(0.dp),
                viewportBleed = 0.dp,
            )
        }
    }
}

/** The removed picture of this source can be shown again: the user removed it and picked none. */
internal fun AccountEntity.canRestoreImage(images: Map<String, SourceImage>, settings: CalendarGroupSettings): Boolean =
    providedImageHidden(settings) && SourceImageKeys.accountCustom(id) !in images

/** Changes the picture and letter-picture color of one source. */
@Composable
internal fun AccountAvatarDialog(
    account: AccountEntity,
    name: String,
    images: Map<String, SourceImage>,
    settings: CalendarGroupSettings,
    onPickImage: (Uri) -> Unit,
    onRemoveImage: () -> Unit,
    onRestoreImage: () -> Unit,
    onColorSelected: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    KgsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.avatar_picture_and_color)) },
        text = {
            AvatarAppearanceEditor(
                name = name,
                image = account.image(images),
                color = account.avatarColor(settings),
                automaticColor = account.automaticAvatarColor(),
                customColor = settings.avatarColors[SourceImageKeys.account(account.id)],
                onPickImage = onPickImage,
                onRemoveImage = onRemoveImage,
                onColorSelected = onColorSelected,
                onRestoreImage = onRestoreImage.takeIf { account.canRestoreImage(images, settings) },
            )
        },
        confirmButton = { DialogConfirmButton(stringResource(R.string.done), onClick = onDismiss) },
    )
}

/**
 * Edits a group: its name and picture, and for a custom group the calendars in it and deleting
 * it. A source's own group has the source's name and picture, so changing them changes the source.
 * [calendars] are the calendars a custom group can hold, [groups] tell where each one is now.
 * [onCalendarsChanged] gets the calendars added and removed when the user saves.
 */
@Composable
internal fun CalendarGroupSettingsDialog(
    group: CalendarSourceGroup,
    images: Map<String, SourceImage>,
    settings: CalendarGroupSettings,
    onRename: (String?) -> Unit,
    onPickImage: (Uri) -> Unit,
    onRemoveImage: () -> Unit,
    onColorSelected: (Int?) -> Unit,
    onDismiss: () -> Unit,
    onRestoreImage: (() -> Unit)? = null,
    calendars: List<CollectionEntity> = emptyList(),
    groups: List<CalendarSourceGroup> = emptyList(),
    onCalendarsChanged: (added: Set<String>, removed: Set<String>) -> Unit = { _, _ -> },
    onDelete: (() -> Unit)? = null,
) {
    val currentName = group.displayName()
    val automaticName = group.automaticName()
    val isCustom = group.kind == CalendarSourceGroupKind.Custom
    var name by rememberSaveable(group.key) { mutableStateOf(currentName) }
    val initialMembers = remember(group.key) { group.collections.map { it.href }.toSet() }
    var members by rememberSaveable(group.key) { mutableStateOf(initialMembers.toList()) }
    var confirmDelete by rememberSaveable(group.key) { mutableStateOf(false) }
    KgsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_group_edit)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AvatarAppearanceEditor(
                    name = name.ifBlank { automaticName },
                    image = group.image(images),
                    color = group.avatarColor(settings),
                    automaticColor = group.automaticAvatarColor(),
                    customColor = settings.avatarColors[group.avatarColorKey()],
                    onPickImage = onPickImage,
                    onRemoveImage = onRemoveImage,
                    onColorSelected = onColorSelected,
                    onRestoreImage = onRestoreImage,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DialogFieldCaption(stringResource(if (group.owner != null) R.string.name else R.string.calendar_group_name))
                    ClearableOutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text(automaticName) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = dialogFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Only a group of several subscriptions has a name apart from its sources.
                if (group.customName != null && group.owner == null && !isCustom) {
                    TextButton(onClick = { name = automaticName }) {
                        Text(stringResource(R.string.calendar_group_reset_name))
                    }
                }
                if (isCustom && calendars.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DialogFieldCaption(stringResource(R.string.calendar_group_calendars))
                        DialogChoiceGroup {
                            calendars.forEach { calendar ->
                                val inGroup = calendar.href in members
                                val elsewhere = groups.groupOf(calendar)?.takeIf { it.key != group.key }
                                DialogChoiceRow(
                                    label = calendar.displayName,
                                    selected = inGroup,
                                    role = Role.Checkbox,
                                    subtitle = elsewhere?.takeUnless { inGroup }?.displayName(),
                                    leading = {
                                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(calendar.color)))
                                    },
                                    onClick = { members = if (inGroup) members - calendar.href else members + calendar.href },
                                )
                            }
                        }
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.calendar_group_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.save),
                // A source's or custom group's name can be changed but not emptied.
                enabled = name.isNotBlank() || (group.owner == null && !isCustom),
                onClick = {
                    val trimmed = name.trim()
                    // The automatic name needs no override; a custom group always keeps a name.
                    onRename(
                        when {
                            trimmed.isEmpty() || (trimmed == automaticName && group.owner == null && !isCustom) -> null
                            else -> trimmed
                        },
                    )
                    val chosen = members.toSet()
                    if (isCustom && chosen != initialMembers) onCalendarsChanged(chosen - initialMembers, initialMembers - chosen)
                    onDismiss()
                },
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onClick = onDismiss)
        },
    )
    if (confirmDelete) {
        KgsAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.calendar_group_delete)) },
            text = { Text(stringResource(R.string.calendar_group_delete_message, currentName)) },
            confirmButton = {
                DialogConfirmButton(
                    text = stringResource(R.string.delete),
                    destructive = true,
                    onClick = {
                        confirmDelete = false
                        onDelete?.invoke()
                        onDismiss()
                    },
                )
            },
            dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onClick = { confirmDelete = false }) },
        )
    }
}

/** Asks for the name of a new custom group. */
@Composable
internal fun NewCalendarGroupDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    KgsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_group_new_title)) },
        text = {
            ClearableOutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.calendar_group_new_name)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = dialogFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.create),
                enabled = name.isNotBlank(),
                onClick = {
                    onCreate(name.trim())
                    onDismiss()
                },
            )
        },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onClick = onDismiss) },
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
    settings: CalendarGroupSettings,
    onSelect: (String?) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = groups.groupOf(collection)
    val automatic = groups.automaticGroupOf(collection, accounts)
    var creating by rememberSaveable(collection.href) { mutableStateOf(false) }
    var newName by rememberSaveable(collection.href) { mutableStateOf("") }
    val choices = remember(groups, automatic) {
        listOfNotNull(automatic) + groups.filter {
            it.key != automatic?.key && (it.collections.isNotEmpty() || it.kind == CalendarSourceGroupKind.Custom)
        }
    }
    KgsAlertDialog(
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
                DialogChoiceGroup {
                    choices.forEach { group ->
                        val label = if (group.key == automatic?.key) {
                            stringResource(R.string.calendar_group_automatic, group.displayName())
                        } else {
                            group.displayName()
                        }
                        val groupImage = group.image(images)
                        val avatarName = group.displayName()
                        DialogChoiceRow(
                            label = label,
                            selected = group.key == current?.key,
                            leading = { SourceAvatar(avatarName, groupImage, 30.dp, group.avatarColor(settings)) },
                            onClick = {
                                onSelect(group.key.takeUnless { it == automatic?.key })
                                onDismiss()
                            },
                        )
                    }
                }
                if (creating) {
                    ClearableOutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text(stringResource(R.string.calendar_group_new_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = dialogFieldColors(),
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
                        Icon(Icons.Default.Add, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(24.dp))
                        Text(stringResource(R.string.calendar_group_new), color = WarmBrown, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                DialogConfirmButton(
                    text = stringResource(R.string.save),
                    enabled = newName.isNotBlank(),
                    onClick = {
                        onCreate(newName.trim())
                        onDismiss()
                    },
                )
            }
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onClick = onDismiss)
        },
    )
}
