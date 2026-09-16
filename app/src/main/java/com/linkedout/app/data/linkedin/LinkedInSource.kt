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
import com.linkedout.app.core.web.BrowserRead
import com.linkedout.app.core.web.ChallengeGateway
import com.linkedout.app.core.web.GuestCookies
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 * Before any of that, the visit itself. A browser gets past targets this app
 * was refused on, and the reason is not the Referer: it is that LinkedIn set
 * `bcookie` and `lidc` on its first visit and recognises it afterwards. A
 * client with no cookies at all is a stranger on every request, and a stranger
 * is what some profiles answer 999 to. So the first request of a run is a warm
 * up on the home page, and everything after it carries what that returned. See
 * [GuestCookies] for what is kept and what is refused.
 *
 * What gets past the wall is the Referer, and only the Referer. LinkedIn serves the
 * full page to a reader arriving from a search engine and the wall to one
 * arriving from nowhere, which is why the same link opens on the second
 * attempt after a detour through the results. So the retry here is a short
 * ladder of referrers rather than a repeat of the same request, and each rung
 * is named in the log so a failure says which ones were refused.
 *
 * The offscreen browser used to be deliberately off that ladder, because it
 * runs the wall's own script. 0.6.13 put it on as the last rung with that
 * script refused, and the phone browsers proved that wrong too: the first
 * click on a refused profile lands on the wall, and going back and clicking
 * again lands on the page, because the wall visit itself sets the cookies the
 * second ask carries, `fid` from LinkedIn and `__cf_bm` from the Cloudflare
 * tier on the country hosts. So the engine now takes the wall once, lets it
 * settle, and asks for the page a second time. It remains the one rung the
 * native client cannot imitate: what differs is under the headers, in the TLS
 * handshake and the HTTP/2 settings, and no header rewrites those.
 *
 * It is not free and it is not silent. It runs LinkedIn's own scripts, so
 * LinkedIn gets the measurements a browser gives it, and it keeps its cookies
 * in the engine's store rather than in the file Settings can count. Nothing
 * there can sign anyone in, there is no credential in this app, and "Clear
 * browsing data" erases both stores.
 *
 * Paging does not exist for a guest. A profile carries a fixed slice of recent
 * activity with no cursor, and a post page stops at ten comments behind a sign
 * in, so both return what they have and say so.
 */
class LinkedInSource(
    private val gateway: ChallengeGateway,
    private val cookies: GuestCookies,
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
                    noParse(url, attempt.text, ProfilePageParser.SELECTOR_SET_VERSION)
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
                    noParse(url, attempt.text, CompanyPageParser.SELECTOR_SET)
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
                    noParse(url, attempt.text, PostPageParser.SELECTOR_SET_VERSION)
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
     * A parse that found nothing still leaves a line.
     *
     * It did not before, and that silence cost a whole feature: your showcase
     * page answered 200 with 272 kB and the log showed a PROFILE entry with no
     * PARSE behind it, which reads like a screen problem and was a parser
     * refusing the document on its first line. The page's own key is the thing
     * that decides, so it is what gets written down.
     */
    private fun noParse(url: String, body: String, selectors: Int) {
        log.record(
            kind = RequestLog.Kind.PARSE,
            url = url,
            outcome = "nothing read",
            bodyBytes = body.length,
            detail = "selectors $selectors, pageKey ${LinkedInHost.pageKey(body) ?: "none"}" +
                (if (LinkedInHost.isAuthWall(body)) ", this is the sign in wall" else "")
        )
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

        /**
         * [denial] marks a 999, LinkedIn's own refusal. It is carried rather
         * than acted on here, because the cooldown it earns would otherwise
         * come down before the browser rung and refuse the one read that can
         * still recover the page.
         */
        class Failed(
            val error: AppError,
            val denial: Boolean = false,
            val retryAfterSeconds: Long? = null
        ) : Attempt
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
        warmUp()
        var wall: Attempt.Failed? = null
        var denied: Attempt.Failed? = null

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
                attempt is Attempt.Failed && attempt.error is AppError.AccountUnavailable -> {
                    // The other arrivals have never answered a wall
                    // differently, and each one is a request and a second
                    // against a host that rate limits. The engine is what
                    // answers differently, so go there now.
                    wall = attempt
                    break
                }
                attempt is Attempt.Failed && attempt.denial -> {
                    // A denial repeated on another rung has never answered
                    // differently. Stop walking and spend the browser instead.
                    denied = attempt
                    break
                }
                else -> return attempt
            }
        }

        if (wall == null && denied == null) {
            return Attempt.Failed(AppError.Unknown("No arrival got past the sign in wall"))
        }

        readInBrowser(url, kind)?.let { return it }

        // Only now, and only if the browser did not get the page either.
        denied?.let { throttle.penalise(LinkedInHost.HOST, it.retryAfterSeconds) }
        return denied ?: wall ?: Attempt.Failed(AppError.Unknown("Refused by LinkedIn"))
    }

    /**
     * The last rung. The same address, asked for by the browser engine on the
     * device, which is a real browser down to its handshake.
     *
     * A wall that reaches the engine too is not treated as a page: the parser
     * would find no profile in it and report a markup change that never
     * happened.
     */
    private suspend fun readInBrowser(url: String, kind: RequestLog.Kind): Attempt.Body? {
        val page = gateway.readInBrowser(
            url = url,
            host = LinkedInHost.HOST,
            kind = kind,
            read = browserRead()
        ) ?: return null

        if (page.status != 200 || LinkedInHost.isAuthWall(page.body)) {
            log.record(
                kind = kind,
                url = url,
                outcome = "the browser reached the wall too",
                httpStatus = page.status,
                bodyBytes = page.body.length,
                detail = "pageKey ${LinkedInHost.pageKey(page.body) ?: "none"}"
            )
            return null
        }
        log.keepBody(url, page.body)
        return Attempt.Body(page.body)
    }

    /**
     * How the engine reads a LinkedIn page.
     *
     * The wall paths are a loop guard now, not a fence: the engine visits the
     * wall once, because that visit is what sets `fid` and the Cloudflare
     * `__cf_bm` a second ask rides on, and only refuses a wall that comes
     * back after the retry. The host suffix lets a read follow LinkedIn's
     * redirect onto the country subdomain a profile actually lives on. The
     * arrival is the one the native ladder puts first, since it is the one
     * LinkedIn answers pages to.
     */
    private fun browserRead(): BrowserRead = BrowserRead(
        blockedPaths = LinkedInHost.WALL_PATHS,
        hostSuffix = LinkedInHost.COOKIE_DOMAIN,
        userAgent = LinkedInHost.USER_AGENT,
        headers = mapOf("Referer" to LinkedInHost.REFERER),
        // The engine is greeted before it asks for anything, because a
        // browser always is. Opening cold on a post is the one arrival
        // LinkedIn treats worst, and it leaves no history entry to go back
        // to, which is the gesture that gets the page on a desk.
        preludeUrl = LinkedInHost.BASE + "/",
        // What the wall is visited for. `fid` comes from the abuse-features
        // module the wall loads, `__cf_bm` from the Cloudflare tier in front
        // of the country hosts. Neither is set by the response, so leaving
        // early throws away the reason for going.
        awaitCookies = listOf("fid", "__cf_bm"),
        looksBlocked = LinkedInHost::isAuthWall
    )

    /**
     * One request on the home page, before the first read of a run, kept for
     * its cookies.
     *
     * Always rather than after a refusal, and the reasoning is in the cost. A
     * refusal is a 999, a 999 puts the host on cooldown, and the retry that
     * would use the new cookies is then delayed by the very failure it was
     * meant to answer. One home page request per launch is cheaper than that,
     * and it is the order a browser does things in anyway.
     *
     * Never fatal. If the home page fails or sets nothing, the ladder runs as
     * it did before and the log says the warm up was skipped, so a later 999
     * can be read against what was actually carried.
     */
    private suspend fun warmUp() {
        if (cookies.isWarm(LinkedInHost.HOST)) return
        // The body is its own function so every exit below is a plain return.
        // Returning out of the lock's lambda works, since withLock is inline,
        // but it reads as a trap and one edit away from being one.
        warmUpLock.withLock { warmUpOnce() }
    }

    private suspend fun warmUpOnce() {
        if (cookies.isWarm(LinkedInHost.HOST)) return
        // A home page that sets nothing leaves the jar cold, and without
        // this every read of the run would spend a request learning that
        // again. Clearing browsing data resets the stamp, so that stays
        // immediate.
        val sinceLastTry = cookies.warmedMillisAgo()
        if (sinceLastTry != null && sinceLastTry < WARM_UP_RETRY_AFTER_MS) return
        cookies.markWarmed()
        val url = LinkedInHost.BASE + "/"
        if (!throttle.acquire(LinkedInHost.HOST)) {
            log.record(
                kind = RequestLog.Kind.SESSION,
                url = url,
                outcome = "warm up skipped",
                detail = "the host is on cooldown, reading without a guest session"
            )
            return
        }
        val started = System.currentTimeMillis()
        val page = try {
            // No Referer on purpose: a first visit to the home page is a
            // typed address, and this request exists to look like one.
            gateway.getPage(
                url = url,
                host = LinkedInHost.HOST,
                kind = RequestLog.Kind.SESSION,
                requestHeaders = headers(Arrival.DIRECT),
                read = browserRead()
            )
        } catch (failure: Throwable) {
            log.record(
                kind = RequestLog.Kind.SESSION,
                url = url,
                outcome = "warm up failed",
                detail = failure.message ?: failure::class.simpleName ?: "transport failure"
            )
            return
        }
        if (page.status == 429 || page.status == ErrorMapper.LINKEDIN_DENIED) {
            throttle.penalise(LinkedInHost.HOST, page.retryAfterSeconds)
        }
        val held = cookies.names(LinkedInHost.HOST)
        log.record(
            kind = RequestLog.Kind.SESSION,
            url = url,
            outcome = if (page.status == 200) "warm up ok" else "warm up http ${page.status}",
            httpStatus = page.status,
            bodyBytes = page.body.length,
            durationMillis = System.currentTimeMillis() - started,
            detail = if (held.isEmpty()) {
                "LinkedIn set no cookie, still reading as a stranger"
            } else {
                "guest session: " + held.joinToString(", ")
            }
        )
    }

    /*
     * A 999 used to drop the guest session, on the theory that LinkedIn had
     * turned those cookies down. The log disproved it: the same cookies read
     * one profile a minute after another was refused, and the refused one
     * loaded later without anything changing. So the refusal is not about the
     * session, and dropping it only bought a cold read, sometimes with the
     * warm up then skipped for cooldown. Removed rather than tuned.
     */

    /** Null when the throttle refused to let the request out at all. */
    private suspend fun request(url: String, kind: RequestLog.Kind, arrival: Arrival): Attempt? {
        if (!throttle.acquire(LinkedInHost.HOST)) return null
        val started = System.currentTimeMillis()

        val page = try {
            gateway.getPage(
                url = url,
                host = LinkedInHost.HOST,
                kind = kind,
                requestHeaders = headers(arrival),
                read = browserRead()
            )
        } catch (failure: Throwable) {
            log.record(kind, url, "transport failure", detail = "${arrival.label} | ${failure.message}")
            return Attempt.Failed(ErrorMapper.fromThrowable(LinkedInHost.HOST, failure))
        }

        // A 429 is an explicit rate limit and takes effect at once. A 999 is
        // the same message in a different shape, but its cooldown is deferred
        // to [fetch]: applied here it came down before the browser rung and
        // refused the one request that could still recover the page.
        if (page.status == 429) {
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
            detail = "${arrival.label} | via ${page.via.name.lowercase()}" +
                cookies.names(LinkedInHost.HOST).let { held ->
                    // Which cookies rode along, next to what came back. This
                    // is the one reading that says whether a 999 survives a
                    // guest session or was never about cookies at all.
                    if (held.isEmpty()) " | no cookies" else " | cookies " + held.joinToString(",")
                }
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
        return if (error != null) {
            Attempt.Failed(
                error = error,
                denial = page.status == ErrorMapper.LINKEDIN_DENIED,
                retryAfterSeconds = page.retryAfterSeconds
            )
        } else {
            Attempt.Body(page.body)
        }
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

    private val warmUpLock = Mutex()

    private companion object {
        /** How long before a warm up that set no cookie is worth trying again. */
        const val WARM_UP_RETRY_AFTER_MS = 5 * 60_000L

        /** Below this a 200 is the wall stub or an error page, not a page. */
        const val MIN_PAGE_BYTES = 20_000

        /** Enough of the page for a report, not enough to be a copy of it. */
        const val SNIPPET_BYTES = 400
    }
}
