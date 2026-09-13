package com.linkedout.app.data.repository

import com.linkedout.app.core.common.Outcome
import com.linkedout.app.core.model.Conversation
import com.linkedout.app.core.model.Feed
import com.linkedout.app.core.model.ProfileTab
import com.linkedout.app.data.linkedin.LinkedInSource

/**
 * The seam between the screens and the network.
 *
 * In MTGA this class chose a source, and the choice was the hard part: HTML or
 * RSS, this instance or that one, fall back to twstalker or report the failure.
 * None of that survives the port. LinkedIn is one host with one page per
 * profile, so there is nothing to choose and this is a thin pass through. It is
 * kept rather than dissolved because the screens call it, and because the post
 * page, when it lands, is a second call that belongs beside the first.
 */
class FeedRepository(private val linkedin: LinkedInSource) {

    /**
     * MTGA fetched the head of a feed from x.com in parallel with the
     * instances, because the two sources disagreed about freshness. One source
     * cannot disagree with itself, so there is no second request to make.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun loadHead(handle: String): Outcome<Feed>? = null

    /**
     * A post and the comments a guest is shown.
     *
     * The author is not a parameter and must not become one. Knowing it is not
     * enough to rebuild the long /posts/ address, which needs the post's own
     * word slug, and a handmade one does not resolve. The short /feed/update/
     * form does, and it needs only the id.
     */
    suspend fun loadConversation(id: String): Outcome<Conversation> =
        linkedin.fetchPost(id, permalink = null)

    /**
     * The guest profile page carries Posts, Comments and Reactions in one
     * document, and the parser already splits them apart, so a tab is not a
     * second request.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun loadTab(handle: String, tab: ProfileTab, cursor: String? = null): Outcome<Feed> =
        loadFeed(handle, cursor)

    /**
     * [cursor] is accepted and ignored. A logged out profile page has no
     * paging: LinkedIn serves a fixed slice of recent activity and offers no
     * continuation, so every fetch returns the same window and
     * [Feed.nextCursor] stays null.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun loadFeed(handle: String, cursor: String? = null): Outcome<Feed> =
        linkedin.fetchProfile(handle)
}
