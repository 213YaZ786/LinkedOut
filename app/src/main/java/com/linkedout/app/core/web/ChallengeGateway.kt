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
        requestHeaders: Map<String, String>
    ): Page {
        if (session.prefersWebView(host)) {
            viaWebView(url, host, kind, alreadyPaced = true)?.let { return it }
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
        return viaWebView(url, host, kind, alreadyPaced = false) ?: native
    }

    private suspend fun viaWebView(
        url: String,
        host: String,
        kind: RequestLog.Kind,
        alreadyPaced: Boolean
    ): Page? {
        session.autoSolveSkipReason(host)?.let { reason ->
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
        val result = solver.solve(url, host)
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
                    detail = if (session.prefersWebView(host)) {
                        "$host checks every request, staying on the browser path"
                    } else {
                        "cookie now shared with the native client for $host"
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
