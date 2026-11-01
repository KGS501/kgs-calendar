package com.kgs.calendar.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.ReadOnlyComposable
import com.kgs.calendar.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale

/** A string in the app's language, which also applies inside dialogs and popups. */
@Composable
@ReadOnlyComposable
internal fun appString(@StringRes id: Int, vararg args: Any): String = stringResource(id, *args)

internal val SecurePasswordKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    keyboardType = KeyboardType.Password,
    imeAction = ImeAction.Done,
    autoCorrectEnabled = false,
)
internal val UrlKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    keyboardType = KeyboardType.Uri,
    imeAction = ImeAction.Next,
    autoCorrectEnabled = false,
)
internal val UsernameKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    keyboardType = KeyboardType.Email,
    imeAction = ImeAction.Next,
    autoCorrectEnabled = false,
)
