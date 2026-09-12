package com.linkedout.app.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkedout.app.core.model.FollowedAccount
import com.linkedout.app.data.accounts.AccountStore
import com.linkedout.app.data.cache.FeedCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One followed account as the list shows it, enriched from the local cache. */
data class AccountRow(
    val handle: String,
    val name: String?,
    val avatarUrl: String?,
    val lastPostMillis: Long?
)

/**
 * Accounts and search in one place. The query filters the accounts you follow,
 * and when it is a valid handle you do not follow yet, the screen offers to
 * open that profile. Nothing is ever followed without an explicit tap.
 */
class AccountsViewModel(
    private val store: AccountStore,
    private val cache: FeedCache
) : ViewModel() {

    private data class Summary(val name: String?, val avatarUrl: String?, val lastPostMillis: Long?)

    private val summaries = MutableStateFlow<Map<String, Summary>>(emptyMap())

    /** Sorted by name, because this list is for finding an account, not for reading. */
    val rows: StateFlow<List<AccountRow>> = combine(store.accounts, summaries) { accounts, known ->
        accounts.map { account ->
            val summary = known[account.handle.lowercase()]
            AccountRow(
                handle = account.handle,
                name = summary?.name ?: account.displayName,
                avatarUrl = summary?.avatarUrl,
                lastPostMillis = summary?.lastPostMillis
            )
        }.sortedBy { (it.name ?: it.handle).lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        store.accounts.onEach { refresh() }.launchIn(viewModelScope)
    }

    /** Re-reads avatars and last post dates. Cheap: local files only, no network. */
    fun refresh() {
        viewModelScope.launch {
            summaries.value = store.accounts.value.associate { account ->
                val feed = cache.read(account.handle)
                account.handle.lowercase() to Summary(
                    name = feed?.displayName?.takeIf { it.isNotBlank() && it != account.handle },
                    avatarUrl = feed?.avatarUrl,
                    lastPostMillis = feed?.posts
                        ?.maxOfOrNull { it.publishedAtMillis }
                        ?.takeIf { it > 0L }
                )
            }
        }
    }

    fun isFollowed(handle: String): Boolean =
        store.accounts.value.any { it.handle.equals(handle, ignoreCase = true) }

    fun follow(handle: String) {
        store.add(handle)
    }

    companion object {
        /** The query as a handle, or null when it cannot be one. */
        fun asHandle(query: String): String? = FollowedAccount.normalise(query)
    }
}
