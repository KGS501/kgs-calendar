package com.kgs.calendar.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/** A desktop window never folds. */
@Composable
actual fun rememberWindowFoldFeatures(): State<List<FoldFeatureSnapshot>> =
    remember { mutableStateOf(emptyList()) }
