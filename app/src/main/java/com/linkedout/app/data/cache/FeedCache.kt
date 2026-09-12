package com.linkedout.app.data.cache

import android.content.Context
import com.linkedout.app.core.model.Feed
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * On disk cache of the last fetched feed per account.
 *
 * One JSON file per handle rather than a database. For a few hundred posts this
 * is faster to read than opening a database, adds no code generation to the
 * build, and keeps everything the app stores inspectable. Room earns its place
 * when full text search across the archive arrives, not before.
 *
 * Lives in filesDir, not cacheDir, because the system is free to delete cacheDir
 * under storage pressure and an offline reader that loses its content when the
 * phone gets full is not much of an offline reader.
 */
class FeedCache(
    context: Context,
    /** Days to keep saved posts, 0 for no limit. Read at each write. */
    private val retentionDays: () -> Int = { 0 }
) {

    private val directory = File(context.filesDir, "feeds").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Posts the reader scrolled back to in this session that the disk does
     * not keep: older than "Keep posts", or past the per account cap. Memory
     * only, per lowercased handle, gone when the app closes.
     *
     * Before 2.4.2 they were dropped at the write that fetched them, so a page
     * of older posts arrived and vanished at once. Home and profiles saw no
     * new posts, took it as a failure and stopped loading older ones. Kept
     * here, they show until the app closes, which is what the setting
     * promises: older posts can be read, they are simply not kept.
     */
    private val scrolledBack = ConcurrentHashMap<String, List<Post>>()

    suspend fun read(handle: String): Feed? = withContext(Dispatchers.IO) {
        val file = fileFor(handle)
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<Feed>(file.readText()) }.getOrNull()
            ?.let(::canonical)
            ?.let(::withScrolledBack)
    }

    suspend fun write(feed: Feed) = withContext(Dispatchers.IO) {
        val kept = trim(feed)
        if (kept !== feed) {
            val keptIds = kept.posts.mapTo(HashSet()) { it.id }
            remember(feed.handle, feed.posts.filterNot { it.id in keptIds })
        }
        runCatching {
            fileFor(feed.handle).writeText(json.encodeToString(kept))
        }
        Unit
    }

    /** Adds what the disk dropped to this session's memory, newest version first. */
    private fun remember(handle: String, dropped: List<Post>) {
        if (dropped.isEmpty()) return
        scrolledBack.merge(handle.lowercase(), dropped) { old, new ->
            (new + old).distinctBy { it.id }
                .sortedByDescending { it.publishedAtMillis }
                .take(MAX_SCROLLED_BACK_PER_ACCOUNT)
        }
    }

    /** The stored feed plus the posts of this session the disk does not keep. */
    private fun withScrolledBack(feed: Feed): Feed {
        val extra = scrolledBack[feed.handle.lowercase()].orEmpty()
        if (extra.isEmpty()) return feed
        val known = feed.posts.mapTo(HashSet()) { it.id }
        val missing = extra.filterNot { it.id in known }
        if (missing.isEmpty()) return feed
        return feed.copy(posts = (feed.posts + missing).sortedByDescending { it.publishedAtMillis })
    }

    /**
     * Appends a newly fetched page to what is already stored, keeping the newer
     * cursor. Deduplicated by post id, because Nitter pages overlap at their
     * boundary and a repeated post breaks LazyColumn's key contract.
     */
    suspend fun append(page: Feed, isPagedFetch: Boolean = false): Feed = withContext(Dispatchers.IO) {
        val incoming = canonical(page)
        val existing = read(incoming.handle)
        if (existing == null) {
            write(incoming)
            return@withContext incoming
        }

        val known = existing.posts.map { it.id }.toSet()
        val newPosts = incoming.posts.filterNot { it.id in known }

        // A post already stored may come back richer: x.com serves the head
        // without cards or polls, Nitter serves them for the same ids. Keep
        // the stored post and fill in what it lacked, plus fresher counts.
        val seenAgain = incoming.posts.filter { it.id in known }.associateBy { it.id }
        val refreshed = if (seenAgain.isEmpty()) existing.posts else existing.posts.map { post ->
            seenAgain[post.id]?.let(post::mergedWith) ?: post
        }

        // A paged fetch that brings back nothing new means the cursor did not
        // advance. Continuing would loop forever on the same page, so stop
        // offering to load more rather than spinning against the instance.
        val exhausted = isPagedFetch && newPosts.isEmpty()

        // A refresh reads the first page again. When that page meets posts
        // already stored, what is stored runs on from the head without a
        // hole, so the deeper cursor kept from earlier paging is still where
        // to continue. Before 2.4.2 the page's own cursor replaced it, the
        // next scroll asked for page two, found only known posts, and paging
        // ended for that account. In MTGA this also had to check that both
        // cursors came from the same source, because they were not
        // interchangeable. One source, so that question no longer arises.
        val keepDeeperCursor = !isPagedFetch &&
            seenAgain.isNotEmpty() &&
            existing.nextCursor != null

        val combined = incoming.copy(
            posts = (refreshed + newPosts).sortedByDescending { it.publishedAtMillis },
            displayName = incoming.displayName.ifBlank { existing.displayName },
            avatarUrl = incoming.avatarUrl ?: existing.avatarUrl,
            bio = incoming.bio ?: existing.bio,
            bannerUrl = incoming.bannerUrl ?: existing.bannerUrl,
            location = incoming.location ?: existing.location,
            currentCompany = incoming.currentCompany ?: existing.currentCompany,
            school = incoming.school ?: existing.school,
            stats = incoming.stats ?: existing.stats,
            nextCursor = when {
                exhausted -> null
                keepDeeperCursor -> existing.nextCursor
                else -> incoming.nextCursor ?: existing.nextCursor
            }
        )
        write(combined)
        combined
    }

    /**
     * Finds one stored post. [hint] is the account whose file most likely holds
     * it. Otherwise every file is scanned, which is a few local reads and only
     * happens when a post is opened from a place that could not tell.
     */
    suspend fun find(id: String, hint: String?): Post? = withContext(Dispatchers.IO) {
        val wanted = PostId.normalize(id)
        hint?.let { read(it) }?.posts?.firstOrNull { it.id == wanted }?.let { return@withContext it }
        directory.listFiles().orEmpty().asSequence()
            .mapNotNull { file -> runCatching { json.decodeFromString<Feed>(file.readText()) }.getOrNull() }
            .map(::canonical)
            .firstNotNullOfOrNull { feed -> feed.posts.firstOrNull { it.id == wanted } }
    }

    /** Every saved post across all accounts, each once, newest first. */
    suspend fun allPosts(): List<Post> = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().asSequence()
            .mapNotNull { file -> runCatching { json.decodeFromString<Feed>(file.readText()) }.getOrNull() }
            .flatMap { canonical(it).posts.asSequence() }
            .distinctBy { it.id }
            .sortedByDescending { it.publishedAtMillis }
            .toList()
    }

    suspend fun forget(handle: String) = withContext(Dispatchers.IO) {
        scrolledBack.remove(handle.lowercase())
        runCatching { fileFor(handle).delete() }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        scrolledBack.clear()
        runCatching { directory.listFiles()?.forEach { it.delete() } }
        Unit
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Rewrites ids to their canonical form and drops duplicates. Also repairs
     * caches written before 1.1.3, where the same post could be stored twice
     * under two id formats. The cleaned version is persisted on the next write.
     */
    private fun canonical(feed: Feed): Feed {
        val posts = feed.posts
            .map { post -> PostId.normalize(post.id).let { if (it == post.id) post else post.copy(id = it) } }
            .distinctBy { it.id }
        return if (posts == feed.posts) feed else feed.copy(posts = posts)
    }

    /**
     * Caps what a single account can occupy, so the cache cannot grow without
     * bound, and drops posts older than the reader's retention. Older posts
     * can still be read by scrolling back, they are simply not kept.
     */
    private fun trim(feed: Feed): Feed {
        val days = retentionDays()
        val cutoff = if (days > 0) System.currentTimeMillis() - days * DAY_MS else Long.MIN_VALUE
        val kept = feed.posts
            .filter { it.publishedAtMillis <= 0L || it.publishedAtMillis >= cutoff }
            .take(MAX_POSTS_PER_ACCOUNT)
        return if (kept.size == feed.posts.size) feed else feed.copy(posts = kept)
    }

    /**
     * Applies the retention to every saved account now, rather than at each
     * account's next fetch. Run at launch and when the setting changes.
     */
    suspend fun applyRetention() = withContext(Dispatchers.IO) {
        if (retentionDays() <= 0) return@withContext
        directory.listFiles().orEmpty().forEach { file ->
            runCatching {
                val feed = json.decodeFromString<Feed>(file.readText())
                val trimmed = trim(feed)
                if (trimmed !== feed) file.writeText(json.encodeToString(trimmed))
            }
        }
    }

    private fun fileFor(handle: String) = File(directory, "${handle.lowercase()}.json")

    private companion object {
        const val MAX_POSTS_PER_ACCOUNT = 300

        /** Scrolling back further than this in one session drops the oldest again. */
        const val MAX_SCROLLED_BACK_PER_ACCOUNT = 1_000
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
