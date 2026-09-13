package com.linkedout.app.data.linkedin

import com.linkedout.app.core.common.AppError
import com.linkedout.app.core.common.Outcome
import com.linkedout.app.core.debug.RequestLog
import com.linkedout.app.core.link.LinkedInLink
import com.linkedout.app.core.model.Conversation
import com.linkedout.app.core.model.Feed
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.network.ErrorMapper
import com.linkedout.app.core.network.HostThrottle
import com.linkedout.app.core.web.ChallengeGateway

/**
 * Reads linkedin.com as a logged out visitor, and gets past the wall.
 *
 * This is the whole network layer of the app. MTGA had five sources behind a
 * pool because its content existed in several places, and choosing between
 * them was most of the work. Here there is one host and no alternative, so the
 * only questions left are how to ask and how to tell a page from a wall.
 *
 * The wall is not an HTTP failure. LinkedIn answers 200 with a body that is
 * nothing but a redirect script, so the status is useless and the body decides.
 * See [LinkedInHost.isAuthWall].
 *
 * What gets past it is the Referer, and only the Referer. LinkedIn serves the
 * full page to a reader arriving from a search engine and the wall to one
 * arriving from nowhere, which is why the same link opens on the second
 * attempt after a detour through the results. So the retry here is a short
 * ladder of referrers rather than a repeat of the same request, and each rung
 * is named in the log so a failure says which ones were refused.
 *
 * The offscreen browser is deliberately not on that ladder. It runs the wall's
 * own script, which navigates to /authwall, so it would turn a readable body
 * into a redirect. It stays available through [ChallengeGateway] for the case
 * LinkedIn puts a real bot check in front, which is a different problem with a
 * different answer.
 *
 * Paging does not exist for a guest. A profile carries a fixed slice of recent
 * activity with no cursor, and a post page stops at ten comments behind a sign
 * in, so both return what they have and say so.
 */
class LinkedInSource(
    private val gateway: ChallengeGateway,
    private val throttle: HostThrottle,
    private val profiles: ProfilePageParser,
    private val organisations: CompanyPageParser,
    private val posts: PostPageParser,
    private val log: RequestLog
) {

    suspend fun fetchProfile(handle: String): Outcome<Feed> {
        val url = LinkedInLink.profileUrl(handle)
        return when (val attempt = fetch(url, RequestLog.Kind.PROFILE)) {
            is Attempt.Failed -> Outcome.Failure(attempt.error)
            is Attempt.Body -> {
                val feed = profiles.parse(attempt.text, handle)
                if (feed == null) {
                    Outcome.Failure(
                        refusal(attempt.text, handle, ProfilePageParser.SELECTOR_SET_VERSION)
                    )
                } else {
                    log.record(
                        kind = RequestLog.Kind.PARSE,
                        url = url,
                        outcome = "ok",
                        bodyBytes = attempt.text.length,
                        detail = feed.census("profile selectors ${ProfilePageParser.SELECTOR_SET_VERSION}")
                    )
                    Outcome.Success(feed)
                }
            }
        }
    }

    /**
     * A company, school or showcase page. Separate from [fetchProfile] because
     * the two pages share no markup, so the parser is chosen here and not
     * inside one parser that tries both. [segment] decides the address.
     */
    suspend fun fetchOrganisation(slug: String, segment: String): Outcome<Feed> {
        val url = LinkedInLink.companyUrl(slug, segment)
        return when (val attempt = fetch(url, RequestLog.Kind.PROFILE)) {
            is Attempt.Failed -> Outcome.Failure(attempt.error)
            is Attempt.Body -> {
                val feed = organisations.parse(attempt.text, slug)
                if (feed == null) {
                    Outcome.Failure(refusal(attempt.text, slug, CompanyPageParser.SELECTOR_SET))
                } else {
                    log.record(
                        kind = RequestLog.Kind.PARSE,
                        url = url,
                        outcome = "ok",
                        bodyBytes = attempt.text.length,
                        detail = feed.census("org selectors ${CompanyPageParser.SELECTOR_SET}")
                    )
                    Outcome.Success(feed)
                }
            }
        }
    }

    /**
     * One post with the comments a guest is shown.
     *
     * [permalink] is used when the caller has it, because the long /posts/ form
     * answers faster than /feed/update/ and is the address LinkedIn itself
     * hands out. The short form is built from [id] when it does not.
     */
    suspend fun fetchPost(id: String, permalink: String?): Outcome<Conversation> {
        val url = permalink?.takeIf { LinkedInLink.parse(it) != null }?.substringBefore('?')
            ?: LinkedInLink.canonical("feed/update/urn:li:activity:$id")

        return when (val attempt = fetch(url, RequestLog.Kind.THREAD)) {
            is Attempt.Failed -> Outcome.Failure(attempt.error)
            is Attempt.Body -> {
                val conversation = posts.parse(attempt.text, id)
                if (conversation?.main == null) {
                    Outcome.Failure(
                        refusal(attempt.text, null, PostPageParser.SELECTOR_SET_VERSION)
                    )
                } else {
                    log.record(
                        kind = RequestLog.Kind.PARSE,
                        url = url,
                        outcome = "ok",
                        bodyBytes = attempt.text.length,
                        detail = "${conversation.replies.size} comments shown of " +
                            "${conversation.main.stats?.replies ?: 0}, " +
                            listOfNotNull(conversation.main).census(
                                "post selectors ${PostPageParser.SELECTOR_SET_VERSION}"
                            )
                    )
                    Outcome.Success(conversation)
                }
            }
        }
    }

    /**
     * What the parser found, not just how much. Counting the fields that go
     * missing one at a time, avatars and media, turns "the icon is not
     * showing" into either "the reader found none" or "the reader found it and
     * the screen did not draw it". Those are different bugs in different
     * files, and the log could not tell them apart before.
     */
    private fun Feed.census(suffix: String): String = posts.census(suffix)

    private fun List<Post>.census(suffix: String): String {
        // Counted outside any builder on purpose. Inside buildString the
        // receiver is a StringBuilder, so count { } resolves against its
        // characters rather than against these posts, and the compiler is
        // right to refuse it.
        val media = flatMap { it.media }
        val avatars = count { !it.avatarUrl.isNullOrBlank() }
        // Own words and quoted words counted apart. A card the reader only
        // reacted to has no words of its own, and counting it as "no text"
        // reads like a parser failure when it is the truth about the post.
        val texts = count { it.text.isNotBlank() }
        val quotes = count { !it.quoted?.text.isNullOrBlank() }
        val counts = count { it.stats != null }
        val videos = media.count { it.type == MediaType.VIDEO }
        // The first video's address, shortened. Whether it is an mp4 on
        // dms.licdn.com or a cover image on media.licdn.com is the whole
        // difference between a reader that found the player and one that fell
        // back to the poster, and no count can show that.
        val firstVideo = media.firstOrNull { it.type == MediaType.VIDEO }
            ?.downloadUrl
            ?.let { url ->
                val bare = url.substringAfter("://").substringBefore('?')
                bare.split('/').take(4).joinToString("/")
            }
        return "$size posts, $avatars avatars, $texts texts, $quotes quoted, " +
            "${media.size} media ($videos video), $counts counts, $suffix" +
            (firstVideo?.let { ", first video $it" } ?: "")
    }

    // ---- the guest gateway -------------------------------------------------

    private sealed interface Attempt {
        class Body(val text: String) : Attempt
        class Failed(val error: AppError) : Attempt
    }

    /**
     * A referrer to arrive from, and the name that goes in the log when it is
     * the one that worked. Ordered by how well each is known to do.
     */
    private enum class Arrival(val label: String, val referer: String?) {
        SEARCH("from a search engine", LinkedInHost.REFERER),
        LINKEDIN("from linkedin itself", LinkedInHost.BASE + "/"),
        DIRECT("with no referrer", null)
    }

    /**
     * Walks the ladder until a page comes back. A rung is only worth spending
     * when the previous one hit the wall: a timeout, a 429 or a 404 will say
     * the same thing three times over, and each retry is a request against a
     * host that rate limits.
     */
    private suspend fun fetch(url: String, kind: RequestLog.Kind): Attempt {
        var wall: Attempt.Failed? = null
        for (arrival in Arrival.entries) {
            val attempt = request(url, kind, arrival) ?: return Attempt.Failed(
                AppError.RateLimited(
                    LinkedInHost.HOST,
                    throttle.cooldownRemainingMs(LinkedInHost.HOST) / 1000
                )
            )
            when {
                attempt is Attempt.Body -> {
                    // Kept so the exact bytes the parser was given can be read
                    // back from the Activity log. A browser's own save is not
                    // the same document: it runs the page's script, this does
                    // not.
                    log.keepBody(url, attempt.text)
                    return attempt
                }
                attempt is Attempt.Failed && attempt.error is AppError.AccountUnavailable -> wall = attempt
                else -> return attempt
            }
        }
        return wall ?: Attempt.Failed(AppError.Unknown("No arrival got past the sign in wall"))
    }

    /** Null when the throttle refused to let the request out at all. */
    private suspend fun request(url: String, kind: RequestLog.Kind, arrival: Arrival): Attempt? {
        if (!throttle.acquire(LinkedInHost.HOST)) return null
        val started = System.currentTimeMillis()

        val page = try {
            gateway.getPage(url, LinkedInHost.HOST, kind, headers(arrival))
        } catch (failure: Throwable) {
            log.record(kind, url, "transport failure", detail = "${arrival.label} | ${failure.message}")
            return Attempt.Failed(ErrorMapper.fromThrowable(LinkedInHost.HOST, failure))
        }

        // A 429 and a 999 are the same message in two shapes: stop asking. The
        // cooldown is per host and therefore stops every read, which is right,
        // since LinkedIn refuses the address rather than the page.
        if (page.status == 429 || page.status == ErrorMapper.LINKEDIN_DENIED) {
            throttle.penalise(LinkedInHost.HOST, page.retryAfterSeconds)
        }

        val walled = page.status == 200 && LinkedInHost.isAuthWall(page.body)
        log.record(
            kind = kind,
            url = url,
            outcome = when {
                walled -> "sign in wall"
                page.status == 200 -> "ok"
                else -> "http ${page.status}"
            },
            httpStatus = page.status,
            bodyBytes = page.body.length,
            durationMillis = System.currentTimeMillis() - started,
            detail = "${arrival.label} | via ${page.via.name.lowercase()}"
        )

        if (walled) {
            return Attempt.Failed(
                AppError.AccountUnavailable(
                    handle = url.substringAfterLast('/'),
                    reason = "LinkedIn asked for a sign in ${arrival.label}"
                )
            )
        }

        val error = ErrorMapper.fromStatus(
            host = LinkedInHost.HOST,
            url = url,
            code = page.status,
            retryAfterSeconds = page.retryAfterSeconds,
            bodyHint = page.body
        )
        return if (error != null) Attempt.Failed(error) else Attempt.Body(page.body)
    }

    /**
     * A desktop browser, and an arrival. Both matter. LinkedIn serves a
     * stripped page with no activity at all to anything it reads as a phone,
     * and the wall to a visitor it cannot see arriving from anywhere.
     */
    private fun headers(arrival: Arrival): Map<String, String> = buildMap {
        put("User-Agent", LinkedInHost.USER_AGENT)
        put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        put("Accept-Language", "en-US,en;q=0.9")
        put("Upgrade-Insecure-Requests", "1")
        arrival.referer?.let { put("Referer", it) }
    }

    /**
     * Classifies a body the parser refused. A wall that survived every rung, a
     * removed account and a real markup change all arrive as a 200, so the
     * difference has to be read out of the page. Calling all three a parse
     * failure would tell the reader to report a bug when the answer is to open
     * the link in a browser.
     */
    private fun refusal(body: String, handle: String?, selectors: Int): AppError = when {
        LinkedInHost.isAuthWall(body) ->
            AppError.AccountUnavailable(handle.orEmpty(), "LinkedIn asked for a sign in")
        body.length < MIN_PAGE_BYTES && handle != null ->
            AppError.AccountNotFound(handle)
        body.length < MIN_PAGE_BYTES ->
            AppError.PostUnavailable(LinkedInHost.HOST, "The page came back almost empty")
        else -> AppError.ParseFailure(
            host = LinkedInHost.HOST,
            selectorSetVersion = selectors,
            snippet = body.take(SNIPPET_BYTES)
        )
    }

    private companion object {
        /** Below this a 200 is the wall stub or an error page, not a page. */
        const val MIN_PAGE_BYTES = 20_000

        /** Enough of the page for a report, not enough to be a copy of it. */
        const val SNIPPET_BYTES = 400
    }
}
