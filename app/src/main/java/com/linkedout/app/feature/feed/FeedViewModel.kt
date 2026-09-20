package com.linkedout.app.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkedout.app.core.common.AppError
import com.linkedout.app.core.common.Outcome
import com.linkedout.app.core.model.AccountKind
import com.linkedout.app.core.model.Feed
import com.linkedout.app.core.model.FollowedAccount
import com.linkedout.app.core.model.Post
import com.linkedout.app.data.accounts.AccountStore
import com.linkedout.app.data.cache.FeedCache
import com.linkedout.app.data.repository.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedUiState(
    val handle: String = "",
    /** Which page this is, which decides the address and the parser. */
    val kind: AccountKind = AccountKind.PERSON,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val feed: Feed? = null,
    val error: AppError? = null,
    val pagingFailed: Boolean = false
) {
    val canLoadMore: Boolean get() = feed?.nextCursor != null
}

class FeedViewModel(
    private val repository: FeedRepository,
    private val accounts: AccountStore,
    private val cache: FeedCache
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
        } else if (accounts.add(handle, _state.value.kind)) {
            _state.value.feed?.displayName
                ?.takeIf { it.isNotBlank() && it != handle }
                ?.let { accounts.updateDisplayName(handle, it) }
        }
    }

    fun load(handle: String, kind: AccountKind = AccountKind.PERSON) {
        if (_state.value.handle == handle && _state.value.kind == kind && _state.value.feed != null) return
        _state.value = FeedUiState(handle = handle, kind = kind, loading = true)

        viewModelScope.launch {
            // Show what is on disk first. Opening an account you have read
            // before should be instant, even with no network.
            cache.read(handle)?.let { cached ->
                _state.value = _state.value.copy(feed = cached)
            }
            refresh()
        }
    }

    fun refresh() {
        val handle = _state.value.handle
        if (handle.isBlank()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)

            when (val outcome = repository.loadFeed(handle, kind = _state.value.kind)) {
                is Outcome.Success -> {
                    val merged = cache.append(outcome.value)
                    accounts.updateDisplayName(handle, outcome.value.displayName)
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
        val current = _state.value
        val cursor = current.feed?.nextCursor ?: return
        if (current.loadingMore || current.loading) return
        if (current.pagingFailed && !manual) return

        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true, pagingFailed = false)
            val before = current.feed?.posts?.size ?: 0
            when (val outcome = repository.loadFeed(current.handle, cursor, current.kind)) {
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
}
