package com.kgs.calendar.ui

import com.kgs.calendar.ui.res.StringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.kgs.calendar.time.AppLocale
import com.kgs.calendar.time.defaultAppLocale
import androidx.compose.runtime.ProvidableCompositionLocal
import com.kgs.calendar.ui.res.stringResource

/** The app's language: the user's choice in Settings, or the system's. */
val LocalAppLocale: ProvidableCompositionLocal<AppLocale> = compositionLocalOf { defaultAppLocale() }

@Composable
@ReadOnlyComposable
fun appString(id: StringResource, vararg args: Any?): String = stringResource(id, *args)

val SecurePasswordKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    keyboardType = KeyboardType.Password,
    imeAction = ImeAction.Done,
    autoCorrectEnabled = false,
)
val UrlKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    keyboardType = KeyboardType.Uri,
    imeAction = ImeAction.Next,
    autoCorrectEnabled = false,
)
val UsernameKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    keyboardType = KeyboardType.Email,
    imeAction = ImeAction.Next,
    autoCorrectEnabled = false,
)
