package com.linkedout.app.data.repository

import com.linkedout.app.core.common.Outcome
import com.linkedout.app.core.model.Conversation
import com.linkedout.app.core.common.AppError
import com.linkedout.app.core.model.AccountKind
import com.linkedout.app.data.accounts.AccountStore
import com.linkedout.app.core.model.Feed
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
class FeedRepository(
    private val linkedin: LinkedInSource,
    private val accounts: AccountStore
) {

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
     * [kind] decides both the address and the parser. It defaults to a person
     * so every caller written before organisations existed keeps its old
     * behaviour instead of silently asking /company/ for someone's profile.
     *
     * [cursor] is accepted and ignored. A logged out profile page has no
     * paging at all. An organisation page does hand out a continuation token,
     * which [Feed.nextCursor] now carries, but following it is a request
     * against an internal route and is not wired up yet.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun loadFeed(
        handle: String,
        cursor: String? = null,
        kind: AccountKind = AccountKind.PERSON
    ): Outcome<Feed> {
        if (kind.isOrganisation) return linkedin.fetchOrganisation(handle, kind.segment)

        val asPerson = linkedin.fetchProfile(handle)
        if (asPerson !is Outcome.Failure || asPerson.error !is AppError.AccountNotFound) return asPerson

        // A company followed as a person. Their two addresses are different
        // pages and /in/ answers 404 for an organisation, which the reader
        // reads as "this account does not exist" about an account they can
        // see in a browser. The logs had three of them, all real.
        //
        // Only on a 404, so a refusal or a wall never sends a second request,
        // and the answer is filed so the mistake costs one extra read once
        // and never again.
        val asCompany = linkedin.fetchOrganisation(handle, AccountKind.COMPANY.segment)
        if (asCompany is Outcome.Success) accounts.updateKind(handle, AccountKind.COMPANY)
        return if (asCompany is Outcome.Success) asCompany else asPerson
    }
}
