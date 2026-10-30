package com.kgs.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.R
import com.kgs.calendar.data.account.SourceImage
import com.kgs.calendar.ui.components.ClearableOutlinedTextField
import com.kgs.calendar.ui.sources.SourceAvatar

/** The field of a calendar source that is being edited in its own popup. */
internal enum class AccountField { Name, ServerUrl, Username, Password }

internal data class AccountFieldEdit(
    val field: AccountField,
    val initialValue: String,
    /** Whether the address receives the account's password, so plain HTTP is worth a warning. */
    val sendsCredentials: Boolean = true,
)

/** Warns that a plain-HTTP server address sends the password and calendar data unencrypted. */
@Composable
internal fun CleartextServerWarning(serverUrl: String) {
    if (!serverUrl.trim().startsWith("http://", ignoreCase = true)) return
    Text(
        stringResource(R.string.cleartext_server_warning),
        color = MaterialTheme.colorScheme.error,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    )
}

/** A source's picture, name and kind, centered at the top of its page. */
@Composable
internal fun SourceIdentityHeader(
    name: String,
    description: String,
    avatarName: String,
    image: SourceImage?,
    color: Int,
    onAvatarClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The picture opens the picture and color choice; a small pencil says so.
        Box(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.change_picture), onClick = onAvatarClick),
        ) {
            SourceAvatar(avatarName, image, 80.dp, color)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(settingsControlColor()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(14.dp))
            }
        }
        Text(name, color = WarmInk, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A setting's current value with an edit pencil; a tap opens a popup to change it. */
@Composable
internal fun SettingsEditRow(title: String, value: String, onClick: () -> Unit) {
    SettingsRow(title = title, subtitle = value, onClick = onClick) {
        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** Edits one field of a calendar source; the change is saved right away. */
@Composable
internal fun AccountFieldDialog(
    edit: AccountFieldEdit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by rememberSaveable(edit) { mutableStateOf(edit.initialValue) }
    val title = stringResource(
        when (edit.field) {
            AccountField.Name -> R.string.name
            AccountField.ServerUrl -> R.string.server_url
            AccountField.Username -> R.string.username
            AccountField.Password -> R.string.change_password
        },
    )
    KgsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (edit.field == AccountField.Password) {
                    Text(stringResource(R.string.change_password_help), fontSize = 14.sp, lineHeight = 20.sp)
                    SecurePasswordField(
                        value = value,
                        onValueChange = { value = it },
                        label = stringResource(R.string.password_or_app_password),
                        colors = dialogFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    ClearableOutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        keyboardOptions = when (edit.field) {
                            AccountField.ServerUrl -> UrlKeyboardOptions
                            AccountField.Username -> UsernameKeyboardOptions
                            else -> androidx.compose.foundation.text.KeyboardOptions.Default
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = dialogFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (edit.field == AccountField.ServerUrl && edit.sendsCredentials) CleartextServerWarning(value)
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.save),
                enabled = value.isNotBlank() && (edit.field == AccountField.Password || value != edit.initialValue),
                onClick = { onSave(value.trim()) },
            )
        },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onClick = onDismiss) },
    )
}

/** Creates a new calendar on a CalDAV server, for events, tasks or both. */
@Composable
internal fun CreateServerCalendarDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, events: Boolean, tasks: Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var events by rememberSaveable { mutableStateOf(true) }
    var tasks by rememberSaveable { mutableStateOf(false) }
    KgsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_server_calendar)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ClearableOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = dialogFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                CardGroup {
                    DialogSwitchRow(stringResource(R.string.events), events) { events = it }
                    DialogSwitchRow(stringResource(R.string.tasks), tasks) { tasks = it }
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.create),
                enabled = name.isNotBlank() && (events || tasks),
                onClick = { onCreate(name.trim(), events, tasks) },
            )
        },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onClick = onDismiss) },
    )
}
