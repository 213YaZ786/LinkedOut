package com.linkedout.app.sync

import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostKind

/**
 * Decides which posts are worth a notification. Pure, so it can be run
 * against real data outside Android.
 *
 * A post is new for an account when its id was not stored before the check,
 * it is newer than the newest post already stored for that account, and it is
 * newer than the moment notifications were turned on. The second rule keeps
 * a Nitter page that reaches further back than the cache from being read as
 * twenty new posts. An account with nothing stored yet announces nothing:
 * its first fetch is a backlog, not news. Pinned posts are old by nature.
 */
object NewPosts {

    /** What the cache held for one account before the check. */
    data class Before(val ids: Set<String>, val newestMillis: Long?)

    /** Home's own filters, so a notification never announces what Home hides. */
    data class Filters(
        val hideReplies: Boolean = false,
        val hideReposts: Boolean = false,
        val mediaOnly: Boolean = false
    )

    fun snapshot(posts: List<Post>): Before =
        Before(posts.map { it.id }.toSet(), posts.maxOfOrNull { it.publishedAtMillis })

    /**
     * [before] and [after] are keyed by followed handle, lower case.
     * Returns the new posts, newest first, each post once.
     */
    fun detect(
        before: Map<String, Before>,
        after: Map<String, List<Post>>,
        sinceMillis: Long,
        filters: Filters = Filters()
    ): List<Post> = after.flatMap { (handle, posts) ->
        val known = before[handle] ?: return@flatMap emptyList()
        val watermark = known.newestMillis ?: return@flatMap emptyList()
        val floor = maxOf(watermark, sinceMillis)
        posts.filter { post ->
            post.id !in known.ids &&
                post.publishedAtMillis > floor &&
                !post.isPinned &&
                !(filters.hideReplies && post.kind == PostKind.REPLY) &&
                !(filters.hideReposts && post.kind == PostKind.REPOST) &&
                !(filters.mediaOnly && post.media.isEmpty())
        }
    }
        .distinctBy { it.id }
        .sortedByDescending { it.publishedAtMillis }
}
