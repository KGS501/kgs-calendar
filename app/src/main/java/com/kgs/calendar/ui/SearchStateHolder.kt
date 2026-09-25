package com.kgs.calendar.ui

import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.search.CalendarSearchMode
import com.kgs.calendar.domain.model.CalendarRange
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

internal data class SearchUiState(
    val query: String,
    val mode: CalendarSearchMode,
    val occurrenceRange: CalendarRange,
    val results: List<EventEntity>,
    val taskResults: List<TaskEntity>,
)

/** Search query, mode and occurrence window plus the results filtered by hidden collections. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchStateHolder internal constructor(
    private val repository: CalendarRepository,
    private val hiddenCollectionHrefs: Flow<Set<String>>,
    private val zoneId: ZoneId,
) {
    private val searchQuery = MutableStateFlow("")
    private val searchMode = MutableStateFlow(CalendarSearchMode.TextAndLabels)
    private val searchOccurrenceRange = MutableStateFlow(initialSearchOccurrenceRange(LocalDate.now(zoneId)))

    internal val occurrenceRange: CalendarRange
        get() = searchOccurrenceRange.value

    private val searchRequest = combine(searchQuery, searchMode, searchOccurrenceRange, ::SearchRequest)
        .distinctUntilChanged()
    private val searchResults = searchRequest
        .flatMapLatest { request ->
            val trimmed = request.query.trim()
            if (trimmed.isBlank()) {
                flowOf(emptyList())
            } else {
                combine(
                    repository.searchEvents(
                        query = trimmed,
                        mode = request.mode,
                        rangeStartMillis = request.range.startMillis(zoneId),
                        rangeEndMillis = request.range.endMillis(zoneId),
                    ),
                    hiddenCollectionHrefs,
                ) { events, hidden ->
                    events.filterNot { it.collectionHref in hidden }
                }
            }
        }
    private val searchTaskResults = searchRequest
        .flatMapLatest { request ->
            val trimmed = request.query.trim()
            if (trimmed.isBlank()) {
                flowOf(emptyList())
            } else {
                combine(
                    repository.searchTasks(
                        query = trimmed,
                        mode = request.mode,
                        rangeStartMillis = request.range.startMillis(zoneId),
                        rangeEndMillis = request.range.endMillis(zoneId),
                    ),
                    hiddenCollectionHrefs,
                ) { tasks, hidden ->
                    tasks.filterNot { it.collectionHref in hidden }
                }
            }
        }

    internal val state: Flow<SearchUiState> = combine(
        searchQuery,
        searchMode,
        searchOccurrenceRange,
        searchResults,
        searchTaskResults,
        ::SearchUiState,
    )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
        if (query.isBlank()) {
            searchOccurrenceRange.value = initialSearchOccurrenceRange(LocalDate.now(zoneId))
        }
    }

    fun setSearchMode(mode: CalendarSearchMode) {
        searchMode.value = mode
    }

    fun loadEarlierSearchOccurrences() {
        searchOccurrenceRange.update { SearchOccurrenceLoadingPolicy.extend(it, SearchOccurrenceEdge.Earlier) }
    }

    fun loadLaterSearchOccurrences() {
        searchOccurrenceRange.update { SearchOccurrenceLoadingPolicy.extend(it, SearchOccurrenceEdge.Later) }
    }
}

private data class SearchRequest(
    val query: String,
    val mode: CalendarSearchMode,
    val range: CalendarRange,
)

private fun initialSearchOccurrenceRange(today: LocalDate): CalendarRange =
    SearchOccurrenceLoadingPolicy.initialRange(today)
