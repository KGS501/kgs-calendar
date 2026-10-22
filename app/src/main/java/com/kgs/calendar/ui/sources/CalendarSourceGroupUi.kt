package com.kgs.calendar.ui.sources

import com.kgs.calendar.ui.dialogFieldColors
import com.kgs.calendar.ui.DialogFieldCaption
import com.kgs.calendar.ui.DialogDismissButton
import com.kgs.calendar.ui.DialogConfirmButton
import com.kgs.calendar.ui.DialogChoiceRow
import com.kgs.calendar.ui.DialogChoiceGroup
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.FlowRow
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

/** The user's picture for the group, else the first picture one of its sources has. */
internal fun CalendarSourceGroup.image(images: Map<String, SourceImage>): SourceImage? =
    images[SourceImageKeys.group(key)]
        ?: accounts.firstNotNullOfOrNull { it.image(images) }

internal fun CalendarSourceGroup.hasCustomImage(images: Map<String, SourceImage>): Boolean =
    SourceImageKeys.group(key) in images

/** The picture of a source: the one the user picked, else the one the source provides. */
internal fun AccountEntity.image(images: Map<String, SourceImage>): SourceImage? =
    images[SourceImageKeys.accountCustom(id)] ?: images[SourceImageKeys.account(id)]

internal fun AccountEntity.hasCustomImage(images: Map<String, SourceImage>): Boolean =
    SourceImageKeys.accountCustom(id) in images

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
    SourceImageKeys.account(id).let { key -> settings.avatarColors[key] ?: defaultAvatarColor(key) }

/** The letter-picture color of a group; a group of one source uses that source's color. */
internal fun CalendarSourceGroup.avatarColor(settings: CalendarGroupSettings): Int =
    settings.avatarColors[SourceImageKeys.group(key)]
        ?: accounts.singleOrNull()?.avatarColor(settings)
        ?: defaultAvatarColor(SourceImageKeys.group(key))

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
 * picture shown when there is none. Changes apply right away.
 */
@Composable
internal fun AvatarAppearanceEditor(
    name: String,
    image: SourceImage?,
    color: Int,
    customColor: Int?,
    hasCustomImage: Boolean,
    onPickImage: (Uri) -> Unit,
    onRemoveImage: () -> Unit,
    onColorSelected: (Int?) -> Unit,
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
                if (hasCustomImage) {
                    TextButton(onClick = onRemoveImage) { Text(stringResource(R.string.calendar_group_remove_image)) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogFieldCaption(stringResource(R.string.avatar_color))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarColorSwatch(color = null, selected = customColor == null, onClick = { onColorSelected(null) })
                AvatarPalette.forEach { option ->
                    AvatarColorSwatch(color = option, selected = customColor == option, onClick = { onColorSelected(option) })
                }
            }
        }
    }
}

/** One color choice; the null color is "automatic", drawn as a split dot. */
@Composable
private fun AvatarColorSwatch(color: Int?, selected: Boolean, onClick: () -> Unit) {
    val description = if (color == null) stringResource(R.string.automatic) else null
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.RadioButton, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Default.Check, contentDescription = description, tint = if (color == null) WarmInk else Color.White, modifier = Modifier.size(18.dp))
            color == null -> Text("A", color = WarmInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Changes the picture and letter-picture color of one source. */
@Composable
internal fun AccountAvatarDialog(
    account: AccountEntity,
    name: String,
    images: Map<String, SourceImage>,
    settings: CalendarGroupSettings,
    onPickImage: (Uri) -> Unit,
    onRemoveImage: () -> Unit,
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
                customColor = settings.avatarColors[SourceImageKeys.account(account.id)],
                hasCustomImage = account.hasCustomImage(images),
                onPickImage = onPickImage,
                onRemoveImage = onRemoveImage,
                onColorSelected = onColorSelected,
            )
        },
        confirmButton = { DialogConfirmButton(stringResource(R.string.done), onClick = onDismiss) },
    )
}

/** Renames a group and sets or removes its picture. */
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
) {
    val currentName = group.displayName()
    val automaticName = group.automaticName()
    var name by rememberSaveable(group.key) { mutableStateOf(currentName) }
    KgsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_group_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AvatarAppearanceEditor(
                    name = name.ifBlank { automaticName },
                    image = group.image(images),
                    color = group.avatarColor(settings),
                    customColor = settings.avatarColors[SourceImageKeys.group(group.key)],
                    hasCustomImage = group.hasCustomImage(images),
                    onPickImage = onPickImage,
                    onRemoveImage = onRemoveImage,
                    onColorSelected = onColorSelected,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DialogFieldCaption(stringResource(R.string.calendar_group_name))
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
                if (group.customName != null && group.kind != CalendarSourceGroupKind.Custom) {
                    TextButton(onClick = { name = automaticName }) {
                        Text(stringResource(R.string.calendar_group_reset_name))
                    }
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.save),
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
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onClick = onDismiss)
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
        listOfNotNull(automatic) + groups.filter { it.key != automatic?.key && it.collections.isNotEmpty() }
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
