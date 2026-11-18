package com.kgs.calendar.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.kgs.calendar.data.remote.CalDavOriginApprovalRequired
import com.kgs.calendar.ui.res.Res
import com.kgs.calendar.ui.res.stringResource

/** Asks whether a new account may send its login to the other server its server named. */
@Composable
fun OriginApprovalDialog(
    request: CalDavOriginApprovalRequired,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
) {
    KgsAlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(Res.string.origin_approval_title)) },
        text = { Text(stringResource(Res.string.origin_approval_body, request.serverUrl, request.origin)) },
        confirmButton = { DialogConfirmButton(text = stringResource(Res.string.origin_approval_allow), onClick = onApprove) },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(Res.string.cancel)) } },
    )
}
