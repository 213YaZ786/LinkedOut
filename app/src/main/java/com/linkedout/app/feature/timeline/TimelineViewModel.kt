package com.linkedout.app.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkedout.app.core.common.AppError
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostKind
import com.linkedout.app.data.accounts.AccountStore
import com.linkedout.app.data.repository.TimelineRepository
import com.linkedout.app.data.settings.Settings
import com.linkedout.app.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The Home filters. Each is a narrowing, so none selected means everything. */
data class HomeFilters(
    val hideReplies: Boolean = false,
    val hideReposts: Boolean = false,
    val mediaOnly: Boolean = false
) {
    val active: Boolean get() = hideReplies || hideReposts || mediaOnly

    fun keeps(post: Post): Boolean =
        !(hideReplies && post.kind == PostKind.REPLY) &&
            !(hideReposts && post.kind == PostKind.REPOST) &&
            !(mediaOnly && post.media.isEmpty())
}

data class TimelineUiState(
    /** Everything stored, unfiltered. */
    val allPosts: List<Post> = emptyList(),
    val filters: HomeFilters = HomeFilters(),
    val loading: Boolean = false,
    val errors: Map<String, AppError> = emptyMap(),
    val followedCount: Int = 0,
    val lastUpdatedMillis: Long? = null,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    /**
     * Set when a page request fails. Automatic prefetch stops, and the reader
     * gets a button instead. Retrying a rate limited host on a scroll gesture
     * is how a 429 turns into a fifteen minute ban.
     */
    val pagingFailed: Boolean = false,
    /** Posts that arrived above the reader with the last refresh, for the pill. */
    val newPostCount: Int = 0
) {
    val posts: List<Post> = if (filters.active) allPosts.filter(filters::keeps) else allPosts
    val isEmpty: Boolean get() = allPosts.isEmpty() && !loading
}

class TimelineViewModel(
    private val repository: TimelineRepository,
    private val accounts: AccountStore,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(TimelineUiState(filters = settings.current.toFilters()))
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    /**
     * One timeline operation at a time: launch, refresh, follow, unfollow,
     * paging. Each reads the cache and replaces the post list, so two running
     * together would overwrite each other, and two fetches together would
     * double the requests against a fragile host.
     */
    private val work = Mutex()

    /** Followed handles, lowercased, that the state reflects. Touched only under [work]. */
    private var known: Set<String> = emptySet()

    /** Set from a pull until it runs, so a second pull cannot queue a second pass. */
    private var fullRefreshQueued = false

    init {
        settings.settings
            .onEach { _state.value = _state.value.copy(filters = it.toFilters()) }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            // Paint from disk first so the timeline is readable before any
            // request goes out, then refresh over the top.
            work.withLock {
                known = followedKeys()
                val cached = repository.cached()
                _state.value = _state.value.copy(
                    allPosts = cached.posts,
                    followedCount = known.size,
                    lastUpdatedMillis = cached.oldestFetchedAtMillis,
                    canLoadMore = cached.canLoadMore
                )
            }
            refresh()
        }

        // Home follows the list live. Before 1.3.1 it read the list once at
        // launch, so an account followed later stayed off Home until a restart.
        // The flow is conflated, so a burst of changes becomes one pass.
        viewModelScope.launch {
            accounts.accounts.collect { reconcile() }
        }
    }

    fun refresh() {
        if (_state.value.loading || fullRefreshQueued) return
        fullRefreshQueued = true
        viewModelScope.launch {
            work.withLock {
                fullRefreshQueued = false
                known = followedKeys()
                fetch(only = null)
            }
        }
    }

    /**
     * Brings the state in line with the followed list. An unfollow costs no
     * request, its posts simply leave. A follow fetches that account only,
     * after showing whatever its cache already holds, for example from having
     * just opened its feed.
     */
    private suspend fun reconcile() = work.withLock {
        val current = followedKeys()
        val added = current - known
        val removed = known - current
        if (added.isEmpty() && removed.isEmpty()) return@withLock
        known = current

        val cached = repository.cached()
        _state.value = _state.value.copy(
            allPosts = cached.posts,
            followedCount = current.size,
            canLoadMore = cached.canLoadMore,
            errors = _state.value.errors.filterKeys { it.lowercase() in current },
            lastUpdatedMillis = if (current.isEmpty()) null else _state.value.lastUpdatedMillis
        )

        if (added.isNotEmpty()) fetch(only = added)
    }

    /**
     * Runs under [work]. [only] limits the network to those handles, and the
     * errors of every other account are kept, since they were not retried.
     */
    private suspend fun fetch(only: Set<String>?) {
        val before = _state.value
        _state.value = before.copy(loading = true, followedCount = known.size)
        val newestBefore = before.allPosts.maxOfOrNull { it.publishedAtMillis }

        val merged = repository.refresh(only)

        val arrived = if (newestBefore == null) {
            0
        } else {
            merged.posts.count { it.publishedAtMillis > newestBefore && _state.value.filters.keeps(it) }
        }
        val errors = if (only == null) {
            merged.errors
        } else {
            _state.value.errors.filterKeys { it.lowercase() !in only } + merged.errors
        }
        val lastUpdated = if (only == null) {
            merged.oldestFetchedAtMillis ?: _state.value.lastUpdatedMillis
        } else {
            // One account fetched does not make the whole timeline fresh.
            _state.value.lastUpdatedMillis ?: merged.oldestFetchedAtMillis
        }
        _state.value = _state.value.copy(
            allPosts = merged.posts,
            loading = false,
            errors = errors,
            followedCount = known.size,
            lastUpdatedMillis = lastUpdated,
            canLoadMore = merged.canLoadMore,
            loadingMore = false,
            pagingFailed = false,
            newPostCount = _state.value.newPostCount + arrived
        )
    }

    private fun followedKeys(): Set<String> =
        accounts.accounts.value.map { it.handle.lowercase() }.toSet()

    /** The reader has seen the top of the list, or tapped the pill. */
    fun clearNewPosts() {
        if (_state.value.newPostCount != 0) _state.value = _state.value.copy(newPostCount = 0)
    }

    fun setFilters(filters: HomeFilters) = settings.update {
        it.copy(
            homeHideReplies = filters.hideReplies,
            homeHideReposts = filters.hideReposts,
            homeMediaOnly = filters.mediaOnly
        )
    }

    /**
     * Called when the reader nears the bottom, and by the retry button.
     * [manual] bypasses the failure latch, so a person can insist, but a scroll
     * gesture cannot. Skipped while another operation holds the timeline, the
     * next scroll asks again.
     */
    fun loadMore(manual: Boolean = false) {
        val current = _state.value
        if (current.loadingMore || current.loading || !current.canLoadMore) return
        if (current.pagingFailed && !manual) return
        if (!work.tryLock()) return

        _state.value = current.copy(loadingMore = true, pagingFailed = false)
        viewModelScope.launch {
            try {
                val before = current.allPosts.size
                val merged = repository.loadMore()
                _state.value = _state.value.copy(
                    allPosts = merged.posts,
                    loadingMore = false,
                    canLoadMore = merged.canLoadMore,
                    errors = merged.errors.ifEmpty { _state.value.errors },
                    pagingFailed = merged.errors.isNotEmpty() || merged.posts.size <= before
                )
            } finally {
                work.unlock()
            }
        }
    }

    private fun Settings.toFilters() = HomeFilters(
        hideReplies = homeHideReplies,
        hideReposts = homeHideReposts,
        mediaOnly = homeMediaOnly
    )
}
