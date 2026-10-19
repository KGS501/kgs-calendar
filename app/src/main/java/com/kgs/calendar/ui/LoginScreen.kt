@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.kgs.calendar.ui.components.ClearableOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kgs.calendar.R
import com.kgs.calendar.ui.layout.WelcomeContentMaxWidth
import com.kgs.calendar.ui.layout.centeredMaxWidth
import com.kgs.calendar.ui.layout.largeScreenMaxWidth

@Composable
internal fun SecurePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = SecurePasswordKeyboardOptions,
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(if (visible) R.string.hide_password else R.string.show_password),
                )
            }
        },
        shape = shape,
        modifier = modifier,
    )
}


@Composable
private fun LoginScreen(
    busy: Boolean,
    onManualLogin: (String, String, String, (Boolean, String?) -> Unit) -> Unit,
    onBrowserLogin: (String) -> Unit,
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var appPassword by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.connect_nextcloud_summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            serverUrl,
            {
                serverUrl = it
                loginError = null
            },
            label = { Text(stringResource(R.string.nextcloud_url)) },
            singleLine = true,
            keyboardOptions = UrlKeyboardOptions,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onBrowserLogin(serverUrl) }, enabled = serverUrl.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Login, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.login_in_browser))
        }
        HorizontalDivider()
        OutlinedTextField(
            username,
            {
                username = it
                loginError = null
            },
            label = { Text(stringResource(R.string.username)) },
            singleLine = true,
            keyboardOptions = UsernameKeyboardOptions,
            modifier = Modifier.fillMaxWidth(),
        )
        SecurePasswordField(
            value = appPassword,
            onValueChange = {
                appPassword = it
                loginError = null
            },
            label = stringResource(R.string.app_password),
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(visible = loginError != null) {
            Text(
                loginError.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
        }
        OutlinedButton(
            onClick = {
                onManualLogin(serverUrl, username, appPassword) { success, error ->
                    loginError = if (success) null else error
                    if (success) appPassword = ""
                }
            },
            enabled = serverUrl.isNotBlank() && username.isNotBlank() && appPassword.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.use_app_password))
        }
    }
}


@Composable
internal fun WelcomeScreen(
    onStartFresh: () -> Unit,
    onConnectCalendars: () -> Unit,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(120f),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .centeredMaxWidth(largeScreenMaxWidth(WelcomeContentMaxWidth))
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(top = statusTop + 30.dp, bottom = navBottom + 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.35f))
            Image(
                painter = painterResource(R.drawable.kgs_logo_vector),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(104.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                stringResource(R.string.app_name),
                color = WarmInk,
                fontSize = 31.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.welcome_question),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(0.85f))
            WelcomeActionButton(
                title = stringResource(R.string.welcome_start_fresh),
                icon = Icons.Default.Add,
                primary = false,
                onClick = onStartFresh,
            )
            Spacer(Modifier.height(12.dp))
            WelcomeActionButton(
                title = stringResource(R.string.welcome_connect_calendars),
                icon = Icons.Default.CalendarMonth,
                primary = true,
                onClick = onConnectCalendars,
            )
        }
    }
}


@Composable
private fun WelcomeActionButton(
    title: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val container = if (primary) WarmBrown else settingsControlColor()
    val content = if (primary) MaterialTheme.colorScheme.onPrimary else WarmInk
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(SettingsControlShape)
            .clickable(onClick = onClick),
        shape = SettingsControlShape,
        color = container,
        border = if (primary) null else BorderStroke(1.dp, WarmLine),
        shadowElevation = if (primary) 8.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (primary) Color.White.copy(alpha = 0.18f) else WarmPeach),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(25.dp))
            }
            Text(
                title,
                color = content,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = content.copy(alpha = 0.82f), modifier = Modifier.size(24.dp))
        }
    }
}
