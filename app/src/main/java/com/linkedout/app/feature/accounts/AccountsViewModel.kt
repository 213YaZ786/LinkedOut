package com.linkedout.app.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkedout.app.core.link.LinkedInLink
import com.linkedout.app.core.model.AccountKind
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

/** What the search field currently holds, read once and reused by the screen. */
sealed interface QueryKind {

    /** Nothing typed yet. */
    data object Blank : QueryKind

    /** A vanity name, typed bare or pulled out of a pasted address. */
    data class Profile(val handle: String, val kind: AccountKind = AccountKind.PERSON) : QueryKind

    /** A link to one post. Real, but this screen opens people, not posts. */
    data object PostLink : QueryKind

    /** A lnkd.in short link, which hides the address it points at. */
    data object ShortLink : QueryKind

    /** A name with spaces, a job link, anything with no profile in it. */
    data object Unusable : QueryKind
}

/** One followed account as the list shows it, enriched from the local cache. */
data class AccountRow(
    val handle: String,
    val kind: AccountKind,
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
                kind = account.kind,
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

    fun follow(handle: String, kind: AccountKind) {
        store.add(handle, kind)
    }

    companion object {

        /**
         * What the reader typed, once. LinkedIn has no @handle, so the only
         * thing that identifies a person is the vanity name in
         * linkedin.com/in/<vanity>, and the field has to accept both that and
         * a whole address pasted from the browser or the LinkedIn app.
         *
         * Everything that is not a profile gets its own answer instead of a
         * single "no match", because the reasons are different and only the
         * reader can fix them.
         */
        fun classify(raw: String): QueryKind {
            val text = raw.trim()
            if (text.isEmpty()) return QueryKind.Blank

            if (looksLikeAddress(text)) {
                val url = if (text.contains("://")) text else "https://$text"
                val host = url.substringAfter("://").substringBefore('/').lowercase()
                if (host == "lnkd.in") return QueryKind.ShortLink
                return when (val link = LinkedInLink.parse(url)) {
                    is LinkedInLink.Profile -> QueryKind.Profile(link.handle)
                    // Company, school and showcase are read by selector set 3,
                    // so they are candidates like anyone else. The segment is
                    // kept: the three are different addresses.
                    is LinkedInLink.Company ->
                        QueryKind.Profile(link.slug, AccountKind.ofSegment(link.segment))
                    is LinkedInLink.Post -> QueryKind.PostLink
                    null -> QueryKind.Unusable
                }
            }

            return FollowedAccount.normalise(text)
                ?.let { QueryKind.Profile(it) }
                ?: QueryKind.Unusable
        }

        /**
         * True when the text is meant to be an address rather than a vanity
         * name. Matched on linkedin.com and lnkd.in rather than on "linkedin",
         * because a vanity name like "linkedin-expert" is a real one.
         */
        private fun looksLikeAddress(text: String): Boolean {
            val lower = text.lowercase()
            return lower.contains("://") || lower.contains("linkedin.com") ||
                lower.contains("lnkd.in")
        }
    }
}
