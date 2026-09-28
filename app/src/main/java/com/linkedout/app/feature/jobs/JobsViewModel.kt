package com.linkedout.app.feature.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkedout.app.core.common.AppError
import com.linkedout.app.core.common.Outcome
import com.linkedout.app.core.model.JobCard
import com.linkedout.app.core.model.JobDetail
import com.linkedout.app.data.linkedin.JobsSource
import com.linkedout.app.data.settings.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class JobsUiState(
    val keywords: String = "",
    val location: String = "",
    /** The search the results belong to, which the fields may have moved on from. */
    val searched: Pair<String, String>? = null,
    val cards: List<JobCard> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: AppError? = null
)

/**
 * The job search. Nothing runs until the reader searches: opening the tab
 * costs no request, and a search is one request per page of ten, against a
 * host every other read of the app shares.
 */
class JobsViewModel(
    private val source: JobsSource,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(
        JobsUiState(keywords = settings.current.jobKeywords, location = settings.current.jobLocation)
    )
    val state: StateFlow<JobsUiState> = _state.asStateFlow()

    private var running: Job? = null

    fun setKeywords(value: String) {
        _state.value = _state.value.copy(keywords = value)
    }

    fun setLocation(value: String) {
        _state.value = _state.value.copy(location = value)
    }

    fun search() {
        val keywords = _state.value.keywords.trim()
        val location = _state.value.location.trim()
        if (keywords.isEmpty()) return
        settings.update { it.copy(jobKeywords = keywords, jobLocation = location) }
        running?.cancel()
        _state.value = _state.value.copy(
            searched = keywords to location,
            cards = emptyList(),
            loading = true,
            loadingMore = false,
            endReached = false,
            error = null
        )
        running = viewModelScope.launch {
            when (val outcome = source.search(keywords, location, start = 0)) {
                is Outcome.Success -> _state.value = _state.value.copy(
                    cards = outcome.value,
                    loading = false,
                    endReached = outcome.value.isEmpty()
                )
                is Outcome.Failure -> _state.value = _state.value.copy(loading = false, error = outcome.error)
            }
        }
    }

    /** The next page. Asked for by the reader, never by a scroll, see the class comment. */
    fun loadMore() {
        val current = _state.value
        val (keywords, location) = current.searched ?: return
        if (current.loading || current.loadingMore || current.endReached) return
        _state.value = current.copy(loadingMore = true, error = null)
        running = viewModelScope.launch {
            when (val outcome = source.search(keywords, location, start = current.cards.size)) {
                is Outcome.Success -> {
                    val known = current.cards.map { it.id }.toSet()
                    val fresh = outcome.value.filterNot { it.id in known }
                    _state.value = _state.value.copy(
                        cards = current.cards + fresh,
                        loadingMore = false,
                        // A page of nothing new is the end, whatever LinkedIn claims.
                        endReached = fresh.isEmpty()
                    )
                }
                is Outcome.Failure -> _state.value = _state.value.copy(loadingMore = false, error = outcome.error)
            }
        }
    }
}

data class JobDetailUiState(
    val loading: Boolean = true,
    val detail: JobDetail? = null,
    val error: AppError? = null
)

class JobDetailViewModel(private val source: JobsSource) : ViewModel() {

    private val _state = MutableStateFlow(JobDetailUiState())
    val state: StateFlow<JobDetailUiState> = _state.asStateFlow()

    private var id: String? = null

    fun load(jobId: String, force: Boolean = false) {
        if (id == jobId && !force && _state.value.detail != null) return
        id = jobId
        _state.value = JobDetailUiState(loading = true)
        viewModelScope.launch {
            _state.value = when (val outcome = source.detail(jobId)) {
                is Outcome.Success -> JobDetailUiState(loading = false, detail = outcome.value)
                is Outcome.Failure -> JobDetailUiState(loading = false, error = outcome.error)
            }
        }
    }
}
