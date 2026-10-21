package com.kgs.calendar.importing

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgs.calendar.data.account.CalendarSourceManager
import com.kgs.calendar.data.ical.IcsEvent
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.mutation.EventImportResult
import com.kgs.calendar.data.mutation.EventImports
import com.kgs.calendar.data.query.CalendarQueries
import com.kgs.calendar.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class IcsImportError { Read, Invalid, Save }

internal data class IcsImportState(
    val loading: Boolean = true,
    val events: List<IcsEvent> = emptyList(),
    val calendars: List<CollectionEntity> = emptyList(),
    val selectedCalendar: String? = null,
    val saving: Boolean = false,
    val error: IcsImportError? = null,
    val result: EventImportResult? = null,
)

internal class IcsImportViewModel(
    private val imports: EventImports,
    queries: CalendarQueries,
    private val sources: CalendarSourceManager,
    private val settings: SettingsStore,
    private val readAttachment: suspend (Uri) -> String,
    private val onImported: suspend () -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(IcsImportState())
    val state = mutableState.asStateFlow()
    private var loaded = false
    private var rawIcs: String? = null

    init {
        viewModelScope.launch {
            combine(queries.observeCollections(), settings.defaultEventCollectionHref) { collections, preferred ->
                collections.filter(imports::canImportInto).sortedBy { it.href != preferred }
            }.collect { calendars ->
                mutableState.update { state ->
                    state.copy(
                        calendars = calendars,
                        selectedCalendar = state.selectedCalendar?.takeIf { href -> calendars.any { it.href == href } }
                            ?: calendars.firstOrNull()?.href,
                    )
                }
            }
        }
    }

    fun load(uri: Uri?) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val text = try {
                withContext(Dispatchers.IO) { readAttachment(requireNotNull(uri)) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(loading = false, error = IcsImportError.Read) }
                return@launch
            }
            try {
                val events = withContext(Dispatchers.IO) {
                    val parsed = imports.reader.read(text)
                    // Imports can go to the local calendar, unless the user deleted it.
                    if (!settings.localCalendarRemoved.first()) sources.ensureLocalCalendar()
                    parsed
                }
                rawIcs = text
                mutableState.update { it.copy(loading = false, events = events) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(loading = false, error = IcsImportError.Invalid) }
            }
        }
    }

    fun selectCalendar(href: String) {
        if (!state.value.saving && state.value.calendars.any { it.href == href }) {
            mutableState.update { it.copy(selectedCalendar = href) }
        }
    }

    fun importEvents() {
        val current = state.value
        val href = current.selectedCalendar ?: return
        val raw = rawIcs ?: return
        if (current.saving || current.result != null) return
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { imports.importEvents(raw, href) }
                mutableState.update { it.copy(saving = false, result = result) }
                // The import is already committed. A reminder refresh must not turn it into a failed import.
                try { onImported() } catch (error: Exception) {
                    if (error is CancellationException) throw error
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(saving = false, error = IcsImportError.Save) }
            }
        }
    }
}
