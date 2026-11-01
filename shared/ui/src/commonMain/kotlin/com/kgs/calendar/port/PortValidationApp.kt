package com.kgs.calendar.port

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.ui.*
import com.kgs.calendar.ui.theme.SharedCalendarTheme

/** A validation host for the actual shared controls, not a substitute calendar implementation. */
@Composable
fun PortValidationApp() {
    var dark by rememberSaveable { mutableStateOf(false) }
    var themeName by rememberSaveable { mutableStateOf(AppThemeMode.KgsBlue.name) }
    var menuOpen by remember { mutableStateOf(false) }
    var createOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val theme = AppThemeMode.valueOf(themeName)
    val themes = listOf(AppThemeMode.KgsBlue, AppThemeMode.KgsWarm, AppThemeMode.KgsFresh)
    SharedCalendarTheme(themeMode = theme, darkTheme = dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp).padding(top = 24.dp, bottom = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text("KGS Calendar · Port validation", style = MaterialTheme.typography.headlineSmall)
                    Text("These are the shared Android controls. Calendar views, data, sync and reminders are not connected in this host.")
                    DialogChoiceGroup {
                        DialogSwitchRow("Dark appearance", dark) { dark = it }
                    }
                    MorphingDropdown(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        buttonShape = RoundedCornerShape(22.dp),
                        buttonColor = WarmPeach,
                        onClick = { menuOpen = true },
                        button = { Text(theme.label, Modifier.padding(18.dp)) },
                        menu = {
                            themes.forEach { option ->
                                DialogChoiceRow(option.label, option == theme, {
                                    themeName = option.name
                                    menuOpen = false
                                })
                            }
                        },
                    )
                    DialogSegmentedControl(
                        options = themes, selected = theme, label = { it.label.removePrefix("KGS ") },
                        onSelected = { themeName = it.name },
                    )
                    DialogConfirmButton("Open dialog", { dialog = "Shared dialog" })
                    Text("Use the create button to check the original expanding menu, icon rotation, colors and animation timing.")
                }
                SharedCreateFabMenu(
                    labels = CreateMenuLabels("Task", "Event", "Close", "Create"),
                    expanded = createOpen, onExpandedChange = { createOpen = it },
                    onCreateTask = { dialog = "Task control" },
                    onCreateEvent = { dialog = "Event control" },
                )
                dialog?.let { title ->
                    KgsAlertDialog(
                        onDismissRequest = { dialog = null }, title = { Text(title) },
                        text = { Text("This validates the original dialog styling. No calendar item is created.") },
                        confirmButton = { DialogConfirmButton("Close", { dialog = null }) },
                    )
                }
            }
        }
    }
}
