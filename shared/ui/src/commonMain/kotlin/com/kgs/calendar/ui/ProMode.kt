package com.kgs.calendar.ui

import com.kgs.calendar.ui.res.StringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.kgs.calendar.ui.res.stringResource

/** Whether pro mode is on: the app leaves out its explanations and hints, for more compact pages. */
val LocalProMode = staticCompositionLocalOf { false }

/**
 * An explanation, such as a settings row's help line or a page's footnote: the text, or null in pro
 * mode. Texts that carry a value or a state are not explanations and stay.
 */
@Composable
@ReadOnlyComposable
fun explanation(id: StringResource, vararg formatArgs: Any): String? =
    if (LocalProMode.current) null else stringResource(id, *formatArgs)
