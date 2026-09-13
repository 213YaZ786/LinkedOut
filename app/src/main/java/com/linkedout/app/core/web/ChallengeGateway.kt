package com.linkedout.app.core.web

import com.linkedout.app.core.debug.RequestLog
import com.linkedout.app.core.network.ChallengeDetector
import com.linkedout.app.core.network.HostThrottle
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

/**
 * Fetches a page, and when a bot check stands in the way, gets through it.
 *
 * Order of preference, cheapest first:
 * 1. Native request. If the host was cleared earlier, it carries the cookie.
 * 2. On a check, one offscreen WebView load of the same URL. Its HTML is the
 *    page itself, so it is used directly and no second request is spent.
 * 3. If a host keeps challenging the native client even with the cookie, the
 *    fingerprint is being checked per request. That host is then read through
 *    the WebView for the rest of the session, still offscreen, still parsed
 *    natively. The WebView is a transport here, never a screen.
 *
 * [readInBrowser] adds a fourth case, and it is not a check at all. LinkedIn
 * refuses a plain client a page it hands any browser, and no header will fix
 * that: what differs is below the headers, in the TLS handshake and the HTTP/2
 * settings, which a native client cannot rewrite. The engine on the device is
 * a real browser and has neither problem. So when the native ladder ends at
 * the wall, the same page is asked for again by the engine. See [BrowserRead]
 * for what that costs and what is done to keep it cheap.
 *
 * Only GET pages go through here. Callers keep their own parsing and error
 * mapping. The returned body may still be a check, and [ChallengeDetector]
 * inside the error mapper will name it.
 */
class ChallengeGateway(
    private val client: HttpClient,
    private val solver: ChallengeSolver,
    private val session: WebSession,
    private val throttle: HostThrottle,
    private val log: RequestLog
) {

    enum class Via { NATIVE, WEBVIEW }

    class Page(
        val status: Int,
        val body: String,
        val retryAfterSeconds: Long?,
        val via: Via
    )

    /**
     * Assumes the caller already waited on [HostThrottle] for this request.
     * Throws on transport failure, exactly like the Ktor call it wraps, so
     * callers keep mapping exceptions through ErrorMapper.
     */
    suspend fun getPage(
        url: String,
        host: String,
        kind: RequestLog.Kind,
        requestHeaders: Map<String, String>,
        read: BrowserRead? = null
    ): Page {
        if (session.prefersWebView(host)) {
            viaWebView(url, host, kind, alreadyPaced = true, read = read)?.let { return it }
        }

        val response = client.get(url) { requestHeaders.forEach { (k, v) -> header(k, v) } }
        val body = response.bodyAsText()
        val status = response.status.value
        val retryAfter = response.headers["Retry-After"]?.toLongOrNull()
        val native = Page(status, body, retryAfter, Via.NATIVE)

        val challenge = ChallengeDetector.inspect(status, body) ?: return native

        val hadCookie = session.isCleared(host)
        if (hadCookie) session.markNativeRejected(host)
        log.record(
            kind = kind,
            url = url,
            outcome = "bot check detected",
            httpStatus = status,
            bodyBytes = body.length,
            detail = "${challenge.kind.name} matched ${challenge.reason}" +
                " | title: ${challenge.title ?: "none"}" +
                (if (hadCookie) " | cookie was sent and refused" else "") +
                " | trying the offscreen browser"
        )
        return viaWebView(url, host, kind, alreadyPaced = false, read = read) ?: native
    }

    /**
     * Reads a page through the engine because the native client was refused,
     * not because a check appeared. Null when the engine could not be used or
     * did not get the page either, and the caller keeps the native answer.
     *
     * A page that arrives this way marks the host as one the native client is
     * refused on, so the next read starts with the engine instead of spending
     * three requests learning the same thing again.
     */
    suspend fun readInBrowser(
        url: String,
        host: String,
        kind: RequestLog.Kind,
        read: BrowserRead
    ): Page? {
        val page = viaWebView(url, host, kind, alreadyPaced = false, read = read) ?: return null
        session.markNativeRejected(host)
        return page
    }

    private suspend fun viaWebView(
        url: String,
        host: String,
        kind: RequestLog.Kind,
        alreadyPaced: Boolean,
        read: BrowserRead? = null
    ): Page? {
        // A read is a page the reader is waiting for, on the one host this
        // app talks to, so the pool rule that protects a list of servers does
        // not apply to it. A failure still costs up to twenty seconds, which
        // is why one is enough to stand down for a minute.
        val skip = if (read != null) session.readSkipReason(host) else session.autoSolveSkipReason(host)
        skip?.let { reason ->
            log.record(
                kind = kind,
                url = url,
                outcome = "browser check skipped",
                detail = "$reason, waiting for a manual check"
            )
            return null
        }
        // The WebView load is a request like any other, the host gets the
        // same one second spacing.
        if (!alreadyPaced && !throttle.acquire(host)) return null

        val startedAt = System.nanoTime()
        val result = solver.solve(url, host, read = read)
        val elapsed = (System.nanoTime() - startedAt) / 1_000_000

        return when (result) {
            is ChallengeSolver.Result.Cleared -> {
                log.record(
                    kind = kind,
                    url = url,
                    outcome = "read through offscreen browser",
                    httpStatus = result.status,
                    bodyBytes = result.html.length,
                    durationMillis = elapsed,
                    detail = when {
                        read != null -> buildString {
                            append("the engine's own read")
                            if (result.wallVisited) {
                                append(", the wall was visited once and the page asked again")
                            }
                            if (result.refusedNavigations > 0) {
                                append(", ${result.refusedNavigations} wall after the retry refused")
                            }
                            // Names only, never values. The presence of fid or
                            // __cf_bm is the whole proof the detour earned its
                            // keep, and this line is where a failed account
                            // gets read against what was actually held.
                            val held = BrowserData.cookieNames(url)
                            append(
                                if (held.isEmpty()) ", engine holds no cookies"
                                else ", engine cookies " + held.joinToString(",")
                            )
                        }
                        session.prefersWebView(host) ->
                            "$host checks every request, staying on the browser path"
                        else -> "cookie now shared with the native client for $host"
                    }
                )
                Page(result.status, result.html, null, Via.WEBVIEW)
            }
            else -> {
                val remember = result !is ChallengeSolver.Result.NoHost &&
                    result !is ChallengeSolver.Result.Cancelled
                if (remember) session.recordFailure(host)
                log.record(
                    kind = kind,
                    url = url,
                    outcome = "browser check not passed",
                    durationMillis = elapsed,
                    detail = describe(result)
                )
                null
            }
        }
    }

    private fun describe(result: ChallengeSolver.Result): String = when (result) {
        is ChallengeSolver.Result.Cleared -> "cleared"
        ChallengeSolver.Result.NeedsInteraction -> "still a check after 20s, needs a manual check"
        is ChallengeSolver.Result.Blocked -> "the browser was refused too (${result.status ?: "no status"})"
        ChallengeSolver.Result.NoHost -> "app not on screen, no browser available"
        ChallengeSolver.Result.Cancelled -> "closed by the user"
        is ChallengeSolver.Result.Failed -> result.detail
    }
}
