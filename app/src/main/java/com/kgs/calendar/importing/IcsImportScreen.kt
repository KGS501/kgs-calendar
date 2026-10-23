package com.kgs.calendar.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kgs.calendar.R
import com.kgs.calendar.domain.source.isLocalCollectionHref
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun IcsImportScreen(
    state: IcsImportState,
    onSelectCalendar: (String) -> Unit,
    onImport: () -> Unit,
    onClose: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    Scaffold(
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose, enabled = !state.saving) { Text(stringResource(R.string.close)) }
                if (state.result != null) {
                    Button(onClick = onOpenCalendar) { Text(stringResource(R.string.ics_open_calendar)) }
                } else {
                    Button(
                        onClick = onImport,
                        enabled = !state.loading && !state.saving && state.events.isNotEmpty() && state.selectedCalendar != null,
                    ) { Text(stringResource(if (state.saving) R.string.ics_importing else R.string.ics_import_action)) }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text(stringResource(R.string.ics_import_title), style = MaterialTheme.typography.headlineSmall) }
            if (state.loading) {
                item { CircularProgressIndicator() }
            }
            state.error?.let { error ->
                item {
                    Text(stringResource(when (error) {
                        IcsImportError.Read -> R.string.ics_import_read_error
                        IcsImportError.Invalid -> R.string.ics_import_invalid_error
                        IcsImportError.Save -> R.string.ics_import_save_error
                    }), color = MaterialTheme.colorScheme.error)
                }
            }
            state.result?.let { result ->
                item { Text(stringResource(R.string.ics_import_result, result.imported, result.skipped)) }
            }
            if (state.events.isNotEmpty() && state.result == null) {
                item { Text(pluralStringResource(R.plurals.ics_import_help, state.events.size, state.events.size)) }
                item { Text(stringResource(R.string.ics_import_destination), style = MaterialTheme.typography.titleMedium) }
                items(state.calendars, key = { it.href }) { calendar ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = state.selectedCalendar == calendar.href,
                            enabled = !state.saving,
                            role = Role.RadioButton,
                            onClick = { onSelectCalendar(calendar.href) },
                        ).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.selectedCalendar == calendar.href, onClick = null, enabled = !state.saving)
                        val label = if (calendar.href.isLocalCollectionHref() && calendar.customDisplayName == null) {
                            stringResource(R.string.local_calendar)
                        } else calendar.displayName
                        Text(label, Modifier.padding(start = 12.dp))
                    }
                }
                if (state.calendars.isEmpty()) {
                    item { Text(stringResource(R.string.ics_import_no_calendar)) }
                }
                item { HorizontalDivider() }
            }
            items(state.events, key = { it.event.uid }) { item ->
                val event = item.event
                val zone = ZoneId.systemDefault()
                val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                val start = Instant.ofEpochMilli(event.startsAtMillis).atZone(zone)
                val end = Instant.ofEpochMilli(event.endsAtMillis).atZone(zone)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(event.title, style = MaterialTheme.typography.titleMedium)
                        Text(if (event.allDay) {
                            "${date.format(start)} – ${date.format(end.toLocalDate().minusDays(1))} · ${stringResource(R.string.all_day)}"
                        } else {
                            "${dateTime.format(start)} – ${dateTime.format(end)}"
                        })
                        event.location?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        if (event.isRecurring) Text(stringResource(R.string.ics_import_recurring))
                        event.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    }
                }
            }
        }
    }
}
