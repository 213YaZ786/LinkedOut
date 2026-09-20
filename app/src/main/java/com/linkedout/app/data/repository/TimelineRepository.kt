package com.linkedout.app.data.repository

import com.linkedout.app.core.common.AppError
import com.linkedout.app.core.common.Outcome
import com.linkedout.app.core.model.AccountKind
import com.linkedout.app.core.model.FollowedAccount
import com.linkedout.app.core.model.Post
import com.linkedout.app.data.accounts.AccountStore
import com.linkedout.app.core.media.MediaPrefetch
import com.linkedout.app.data.cache.FeedCache
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Everything you follow, merged into one stream.
 *
 * Cache first, then network. The reader sees content immediately and watches it
 * update, rather than staring at a spinner while several instances are polled.
 * When a fetch fails the cached posts stay on screen and the failure is
 * reported per account, because losing one account is not losing the timeline.
 */
class TimelineRepository(
    private val accounts: AccountStore,
    private val feeds: FeedRepository,
    private val cache: FeedCache,
    private val media: MediaPrefetch
) {

    /** Null means every account, which is the stream with no folder chosen. */
    private fun List<FollowedAccount>.inFolder(folder: String?): List<FollowedAccount> =
        if (folder == null) this else filter { it.folder == folder }

    data class Merged(
        val posts: List<Post> = emptyList(),
        val errors: Map<String, AppError> = emptyMap(),
        val fromCache: Boolean = false,
        val oldestFetchedAtMillis: Long? = null,
        val canLoadMore: Boolean = false
    )

    /**
     * Instant, offline, no network touched.
     *
     * [folder] narrows it to the accounts filed there. Null is every account,
     * which is the stream Home opens on.
     */
    suspend fun cached(folder: String? = null): Merged {
        val handles = accounts.accounts.value.inFolder(folder).map { it.handle }
        if (handles.isEmpty()) return Merged()

        val loaded = handles.mapNotNull { cache.read(it) }
        return Merged(
            posts = merge(loaded.flatMap { it.posts }),
            fromCache = true,
            oldestFetchedAtMillis = loaded.minOfOrNull { it.fetchedAtMillis },
            canLoadMore = loaded.any { it.nextCursor != null }
        )
    }

    /**
     * Fetches followed accounts, a few at a time, and returns the whole merged
     * timeline.
     *
     * [only] narrows the fetch to those handles, lowercased, and leaves every
     * other account to its cache. Following one account then costs one read,
     * not a pass over the whole list against a fragile host. Null fetches all.
     *
     * The concurrency limit is the point. Firing twenty simultaneous requests at
     * a single surviving instance is the fastest way to get rate limited, and
     * the pool's backoff would then punish every later read.
     */
    suspend fun refresh(only: Set<String>? = null, folder: String? = null): Merged = coroutineScope {
        val followed = accounts.accounts.value.inFolder(folder)
        if (followed.isEmpty()) return@coroutineScope Merged()

        val targets = if (only == null) followed else followed.filter { it.handle.lowercase() in only }

        val gate = Semaphore(MAX_PARALLEL_FETCHES)

        // Each account is asked for by its own kind. A company read as a person
        // is a request to an address that does not exist, which LinkedIn
        // answers with a denial rather than a 404.
        val results = targets.map { account ->
            async {
                gate.withPermit {
                    account.handle to feeds.loadFeed(account.handle, kind = account.kind)
                }
            }
        }.map { it.await() }

        val errors = mutableMapOf<String, AppError>()
        val fresh = mutableListOf<Post>()
        var oldest: Long? = null
        var more = false

        // MTGA fetched a fresh head from x.com alongside the instances, and
        // an account whose head arrived was not reported as failing even when
        // the deeper fetch did. With one source there is no second opinion to
        // hold against the first, so a failure here is simply a failure.
        for ((handle, outcome) in results) {
            when (outcome) {
                is Outcome.Success -> {
                    // Merge rather than overwrite, so a refresh does not throw
                    // away every page the reader already scrolled through.
                    val merged = cache.append(outcome.value)
                    fresh += outcome.value.posts
                    accounts.updateDisplayName(handle, outcome.value.displayName)
                    if (merged.nextCursor != null) more = true
                    oldest = minOf(oldest ?: outcome.value.fetchedAtMillis, outcome.value.fetchedAtMillis)
                }
                is Outcome.Failure -> {
                    errors[handle] = outcome.error
                    cache.read(handle)?.let { if (it.nextCursor != null) more = true }
                }
            }
        }

        // Read back from the cache rather than from this run's results, so the
        // merged view includes everything ever collected, not only what today's
        // fetch happened to return. This is what makes background polling
        // accumulate history instead of replacing it. The list is read again
        // here, so an account unfollowed during the fetch does not come back.
        val stored = accounts.accounts.value.inFolder(folder).mapNotNull { cache.read(it.handle) }
        val posts = merge(stored.flatMap { it.posts })

        // Only what this pass actually read, not the whole merged archive: a
        // first run would otherwise queue every picture ever cached at once.
        // Here rather than in the screen, so a background check saves media
        // too. It returns at once, the work is handed to DownloadManager.
        media.queue(fresh)

        Merged(
            posts = posts,
            errors = errors,
            fromCache = false,
            oldestFetchedAtMillis = oldest,
            canLoadMore = more || stored.any { it.nextCursor != null }
        )
    }

    /**
     * Extends the merged timeline further back.
     *
     * The trick is choosing whom to ask. An account whose oldest loaded post is
     * recent is the one capping how far back the merged view can honestly go,
     * so those get paged first. Asking every account for another page instead
     * would waste requests on accounts that already reach back weeks, and with
     * one fragile instance in the pool, wasted requests are the scarce resource.
     */
    suspend fun loadMore(): Merged = coroutineScope {
        val handles = accounts.accounts.value.map { it.handle }
        if (handles.isEmpty()) return@coroutineScope Merged()

        val cached = handles.mapNotNull { cache.read(it) }
        val blocking = cached
            .filter { it.nextCursor != null && it.posts.isNotEmpty() }
            .sortedByDescending { feed -> feed.posts.minOf { it.publishedAtMillis } }
            .take(MAX_PARALLEL_FETCHES)

        if (blocking.isEmpty()) {
            return@coroutineScope Merged(
                posts = merge(cached.flatMap { it.posts }),
                canLoadMore = false
            )
        }

        val gate = Semaphore(MAX_PARALLEL_FETCHES)
        val errors = mutableMapOf<String, AppError>()

        // The kind comes from the followed list, not from the cached feed,
        // which does not record it.
        val kinds = accounts.accounts.value.associate { it.handle to it.kind }

        blocking.map { feed ->
            async {
                gate.withPermit {
                    feed.handle to feeds.loadFeed(
                        handle = feed.handle,
                        cursor = feed.nextCursor,
                        kind = kinds[feed.handle] ?: AccountKind.PERSON
                    )
                }
            }
        }.map { it.await() }.forEach { (handle, outcome) ->
            when (outcome) {
                is Outcome.Success -> cache.append(outcome.value, isPagedFetch = true)
                is Outcome.Failure -> errors[handle] = outcome.error
            }
        }

        val refreshed = handles.mapNotNull { cache.read(it) }
        Merged(
            posts = merge(refreshed.flatMap { it.posts }),
            errors = errors,
            fromCache = false,
            oldestFetchedAtMillis = refreshed.minOfOrNull { it.fetchedAtMillis },
            canLoadMore = refreshed.any { it.nextCursor != null }
        )
    }

    /**
     * Newest first, deduplicated. Pinned posts lose their pin in the merged
     * view: a pin is a statement about one profile, and honouring it here would
     * park an old post at the top of everything.
     */
    private fun merge(posts: List<Post>): List<Post> =
        posts.distinctBy { it.id }
            .sortedByDescending { it.publishedAtMillis }
            .map { if (it.isPinned) it.copy(isPinned = false) else it }
            .take(MAX_TIMELINE_POSTS)

    private companion object {
        /**
         * One at a time. Every request in this app goes to linkedin.com, so
         * "parallel" would only mean several simultaneous requests to the one
         * host that can refuse them all at once. The throttle paces them
         * anyway, so concurrency here would buy nothing and risk a 429.
         */
        const val MAX_PARALLEL_FETCHES = 1
        const val MAX_TIMELINE_POSTS = 2_000
    }
}
