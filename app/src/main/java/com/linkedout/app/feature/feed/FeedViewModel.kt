package com.linkedout.app.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkedout.app.core.common.AppError
import com.linkedout.app.core.common.Outcome
import com.linkedout.app.core.common.valueOrNull
import com.linkedout.app.core.model.Feed
import com.linkedout.app.core.model.FollowedAccount
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.ProfileTab
import com.linkedout.app.core.web.ChallengeSolver
import com.linkedout.app.data.accounts.AccountStore
import com.linkedout.app.data.cache.FeedCache
import com.linkedout.app.data.repository.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A Replies or Media tab: read fresh from Nitter, kept in memory only. */
data class TabFeed(
    val posts: List<Post> = emptyList(),
    val cursor: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** A first read finished, successfully or not. */
    val loaded: Boolean = false,
    val error: AppError? = null,
    val pagingFailed: Boolean = false
)

data class FeedUiState(
    val handle: String = "",
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val feed: Feed? = null,
    val error: AppError? = null,
    val pagingFailed: Boolean = false,
    val tab: ProfileTab = ProfileTab.POSTS,
    val tabs: Map<ProfileTab, TabFeed> = emptyMap()
) {
    val canLoadMore: Boolean get() = feed?.nextCursor != null

    fun tabFeed(tab: ProfileTab): TabFeed = tabs[tab] ?: TabFeed()
}

class FeedViewModel(
    private val repository: FeedRepository,
    private val accounts: AccountStore,
    private val cache: FeedCache,
    private val solver: ChallengeSolver
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    /** Followed accounts, so the screen can show Follow or Following. */
    val followed: StateFlow<List<FollowedAccount>> = accounts.accounts

    /**
     * Follows or unfollows the account on screen. A feed can now be opened
     * from the Accounts search without following it, so this is where following happens.
     */
    fun toggleFollow() {
        val handle = _state.value.handle
        if (handle.isBlank()) return
        if (accounts.accounts.value.any { it.handle.equals(handle, ignoreCase = true) }) {
            accounts.remove(handle)
        } else if (accounts.add(handle)) {
            _state.value.feed?.displayName
                ?.takeIf { it.isNotBlank() && it != handle }
                ?.let { accounts.updateDisplayName(handle, it) }
        }
    }

    fun load(handle: String) {
        if (_state.value.handle == handle && _state.value.feed != null) return
        _state.value = FeedUiState(handle = handle, loading = true)

        viewModelScope.launch {
            // Show what is on disk first. Opening an account you have read
            // before should be instant, even with no network.
            cache.read(handle)?.let { cached ->
                _state.value = _state.value.copy(feed = cached)
            }
            refresh()
        }
    }

    /**
     * Switches tab. Replies and Media are read the first time they are shown,
     * not before, so opening a profile still costs one request.
     */
    fun selectTab(tab: ProfileTab) {
        _state.value = _state.value.copy(tab = tab)
        val current = _state.value.tabFeed(tab)
        if (tab != ProfileTab.POSTS && !current.loaded && !current.loading) loadTab(tab)
    }

    private fun updateTab(tab: ProfileTab, change: (TabFeed) -> TabFeed) {
        _state.value = _state.value.copy(tabs = _state.value.tabs + (tab to change(_state.value.tabFeed(tab))))
    }

    private fun loadTab(tab: ProfileTab) {
        val handle = _state.value.handle
        if (handle.isBlank()) return
        viewModelScope.launch {
            updateTab(tab) { it.copy(loading = true, error = null) }
            when (val outcome = repository.loadTab(handle, tab)) {
                is Outcome.Success -> updateTab(tab) {
                    TabFeed(posts = outcome.value.posts, cursor = outcome.value.nextCursor, loaded = true)
                }
                is Outcome.Failure -> updateTab(tab) {
                    it.copy(loading = false, loaded = true, error = outcome.error)
                }
            }
        }
    }

    private fun loadMoreTab(tab: ProfileTab, manual: Boolean) {
        val current = _state.value.tabFeed(tab)
        val cursor = current.cursor ?: return
        if (current.loading || current.loadingMore) return
        if (current.pagingFailed && !manual) return
        val handle = _state.value.handle

        viewModelScope.launch {
            updateTab(tab) { it.copy(loadingMore = true, pagingFailed = false) }
            when (val outcome = repository.loadTab(handle, tab, cursor)) {
                is Outcome.Success -> updateTab(tab) { now ->
                    val known = now.posts.map { it.id }.toSet()
                    val fresh = outcome.value.posts.filterNot { it.id in known }
                    now.copy(
                        posts = now.posts + fresh,
                        // A page with nothing new means the cursor went nowhere.
                        cursor = if (fresh.isEmpty()) null else outcome.value.nextCursor,
                        loadingMore = false
                    )
                }
                is Outcome.Failure -> updateTab(tab) {
                    it.copy(loadingMore = false, pagingFailed = true, error = outcome.error)
                }
            }
        }
    }

    fun refresh() {
        val handle = _state.value.handle
        if (handle.isBlank()) return
        val tab = _state.value.tab
        if (tab != ProfileTab.POSTS) {
            loadTab(tab)
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)

            // x.com first. It is the quickest and most accurate head of the
            // feed, so it goes on screen while the instances work on depth.
            repository.loadHead(handle)?.valueOrNull()?.let { head ->
                val merged = cache.append(head)
                accounts.updateDisplayName(handle, head.displayName)
                _state.value = _state.value.copy(feed = merged)
            }

            when (val outcome = repository.loadFeed(handle)) {
                is Outcome.Success -> {
                    val merged = cache.append(outcome.value)
                    accounts.updateDisplayName(handle, outcome.value.displayName)
                    // Keep the tabs, only the Posts part is new.
                    _state.value = _state.value.copy(
                        loading = false,
                        loadingMore = false,
                        feed = merged,
                        error = null,
                        pagingFailed = false
                    )
                }
                is Outcome.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    error = outcome.error
                )
            }
        }
    }

    fun loadMore(manual: Boolean = false) {
        if (_state.value.tab != ProfileTab.POSTS) {
            loadMoreTab(_state.value.tab, manual)
            return
        }
        val current = _state.value
        val cursor = current.feed?.nextCursor ?: return
        if (current.loadingMore || current.loading) return
        if (current.pagingFailed && !manual) return

        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true, pagingFailed = false)
            val before = current.feed?.posts?.size ?: 0
            when (val outcome = repository.loadFeed(current.handle, cursor)) {
                is Outcome.Success -> {
                    val merged = cache.append(outcome.value, isPagedFetch = true)
                    _state.value = _state.value.copy(
                        feed = merged,
                        loadingMore = false,
                        pagingFailed = merged.posts.size <= before
                    )
                }
                is Outcome.Failure -> _state.value = _state.value.copy(
                    loadingMore = false,
                    pagingFailed = true,
                    error = outcome.error
                )
            }
        }
    }

    /**
     * The user asked to complete a bot check by hand. On success the host is
     * cleared for the session and the feed is read again straight away.
     */
    fun verify(error: AppError.ChallengeRequired) {
        viewModelScope.launch {
            val result = solver.solve(error.url, error.host, interactive = true)
            if (result is ChallengeSolver.Result.Cleared) refresh()
        }
    }
}
