package com.linkedout.app.core.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.linkedout.app.core.common.ChallengeKind
import com.linkedout.app.core.network.ChallengeDetector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream

/**
 * One WebView, one task, then destroyed.
 *
 * This is the only place in LinkedOut that runs remote JavaScript, and it is a
 * deliberate loosening of the threat model, so the WebView is locked down:
 * https only, no file or content access, no popups, no downloads, no
 * navigation off the challenged host, analytics hosts answered with nothing,
 * DOM storage wiped on release. JavaScript exists only while the task runs,
 * because the WebView itself only exists while the task runs.
 *
 * A reading task, [ChallengeSolver.Task.read], changes what a wall means.
 * The first time the page turns out to be a wall, by navigating to one of its
 * addresses or by being one, the engine lets it stand, waits for it to
 * settle, and asks for the target once more. That is a phone browser's "go
 * back and click again", automated, and the second ask carries the cookies
 * the wall visit just set. Only a wall met again after that retry is refused,
 * which is where a loop would start. See [BrowserRead] for the evidence.
 */
@SuppressLint("SetJavaScriptEnabled")
class ChallengeDriver(
    context: Context,
    private val task: ChallengeSolver.Task,
    private val solver: ChallengeSolver
) {

    private val handler = Handler(Looper.getMainLooper())
    private var done = false
    private var lastMainFrameError: Pair<String, Int>? = null
    private var lastFinishedAt = 0L

    /** Navigations refused after the retry was already spent. */
    var refusedNavigations = 0
        private set

    /** True once the engine let the wall load, on purpose, to be given its cookies. */
    var wallVisited = false
        private set

    /** True once the target was asked for the second time. One retry, ever. */
    private var wallRetried = false

    val view: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setGeolocationEnabled(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = true
        // Images stay on. Some checks load their own. Post media is refused
        // in shouldInterceptRequest instead, which is where it can be told apart.
        settings.blockNetworkImage = false
        settings.loadsImagesAutomatically = true

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

        webViewClient = Client()
        setDownloadListener { _, _, _, _, _ -> }
        tag = this@ChallengeDriver
    }

    private val poll = object : Runnable {
        override fun run() {
            if (done) return
            inspect()
            handler.postDelayed(this, POLL_MS)
        }
    }

    init {
        // Before the load, since the string decides which page LinkedIn
        // builds, and after it the request is already out.
        task.read?.userAgent?.let { view.settings.userAgentString = it }
        solver.onUserAgent(view.settings.userAgentString.orEmpty())
        val headers = task.read?.headers.orEmpty()
        if (headers.isEmpty()) view.loadUrl(task.url) else view.loadUrl(task.url, headers)
        handler.postDelayed(poll, POLL_MS)
    }

    /** Must be called when the view leaves the screen. Idempotent. */
    fun release() {
        done = true
        handler.removeCallbacksAndMessages(null)
        runCatching {
            view.stopLoading()
            view.settings.javaScriptEnabled = false
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        // Keep the cookies, they are the whole point. Nothing else survives.
        runCatching { WebStorage.getInstance().deleteAllData() }
        runCatching { CookieManager.getInstance().flush() }
    }

    private fun finish(result: ChallengeSolver.Result) {
        if (done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        solver.complete(task, result)
    }

    /**
     * Reads the current document and asks the same detector the native path
     * uses whether it is still a check. Runs after every page load and on a
     * timer, because some checks swap the page without navigating.
     */
    private fun inspect() {
        if (done || lastFinishedAt == 0L || view.progress < 100) return
        val current = view.url ?: return
        if (!current.startsWith("https://")) return

        view.evaluateJavascript(READ_DOCUMENT_JS) { raw ->
            if (done) return@evaluateJavascript
            val html = decode(raw) ?: return@evaluateJavascript

            // The wall detour, for reading tasks only. Standing on the wall is
            // not a result: it is the visit that earns the cookies. Once it
            // has settled, ask for the target again, once. After that retry
            // the document is taken for what it is, and a wall that came back
            // anyway is reported upstream as one.
            val read = task.read
            if (read != null && !wallRetried &&
                read.isDetour(Uri.parse(current).path.orEmpty(), html)
            ) {
                wallVisited = true
                val settled = SystemClock.elapsedRealtime() - lastFinishedAt > WALL_SETTLE_MS
                if (settled) {
                    wallRetried = true
                    if (read.headers.isEmpty()) {
                        view.loadUrl(task.url)
                    } else {
                        view.loadUrl(task.url, read.headers)
                    }
                }
                return@evaluateJavascript
            }

            val recorded = lastMainFrameError?.takeIf { it.first == current }?.second ?: 200
            // A check answers 403 or 503 on the very URL it guards, then
            // reloads that URL with the real page. A success fires no error
            // callback, so the check's status stays recorded against the URL.
            // In 1.3.4 twstalker's real page came back labelled 403 and was
            // read as a refusal. Posts on the page settle it. A genuine 404
            // is left alone.
            val status = if (recorded in CHECK_STATUSES && ChallengeDetector.hasContent(html)) {
                200
            } else {
                recorded
            }

            when (ChallengeDetector.detect(status, html)) {
                null -> finish(
                    ChallengeSolver.Result.Cleared(
                        html = html,
                        finalUrl = current,
                        status = status,
                        refusedNavigations = refusedNavigations,
                        wallVisited = wallVisited
                    )
                )
                ChallengeKind.WAF_BLOCK -> {
                    // A plain refusal with no script to run. Give it a moment
                    // in case something redirects, then stop wasting time.
                    val settled = SystemClock.elapsedRealtime() - lastFinishedAt > BLOCK_SETTLE_MS
                    if (!task.interactive && settled) {
                        finish(ChallengeSolver.Result.Blocked(status))
                    }
                }
                else -> Unit // still working, the page will move on by itself
            }
        }
    }

    private fun decode(raw: String?): String? {
        if (raw.isNullOrEmpty() || raw == "null") return null
        return runCatching {
            (Json.parseToJsonElement(raw) as? JsonPrimitive)?.takeIf { it.isString }?.content
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun onTaskHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return host == task.host || host.endsWith("." + task.host)
    }

    private inner class Client : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Frames must be free to load. The Cloudflare check widget lives in
            // an iframe on its own domain, and blocking it was why 1.1.0 never
            // got past a single check. Only the top page is kept on the host.
            if (!request.isForMainFrame) return false
            val url: Uri = request.url
            // A read may roam the wider domain, because LinkedIn serves a
            // profile from the country subdomain of its owner and a redirect
            // to fr.linkedin.com is a formality, not an exit.
            val allowed = url.scheme == "https" &&
                (onTaskHost(url.host) || task.read?.allowsHost(url.host.orEmpty()) == true)
            if (!allowed) return true
            // The wall's own addresses. Before the retry they are allowed to
            // load: the visit is what sets the cookies the second ask rides
            // on. After it, a wall coming back is a refusal to report, not a
            // page to stand on, and following it again would loop.
            if (task.read?.refuses(url.path.orEmpty()) == true && wallRetried) {
                refusedNavigations++
                return true
            }
            return false
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val host = request.url.host.orEmpty()
            val path = request.url.path.orEmpty()
            val tracker = BLOCKED_HOSTS.any { host == it || host.endsWith(".$it") }
            // Post media is never part of a check. Refusing it keeps a page
            // read through the browser as light as the native read.
            val media = MEDIA_HOSTS.any { host == it } ||
                (onTaskHost(host) && MEDIA_PATHS.any { path.startsWith(it) })
            return if (tracker || media) {
                WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            } else {
                null
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            lastFinishedAt = 0L
        }

        override fun onPageFinished(view: WebView, url: String?) {
            lastFinishedAt = SystemClock.elapsedRealtime()
            inspect()
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            if (request.isForMainFrame) {
                lastMainFrameError = request.url.toString() to errorResponse.statusCode
            }
        }

        /** No exceptions to certificate validation, ever. */
        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, sslHandler: SslErrorHandler, error: SslError) {
            sslHandler.cancel()
            finish(ChallengeSolver.Result.Failed("TLS error ${error.primaryError}"))
        }

        /** Without this, a crashed renderer takes the whole app down. */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            finish(ChallengeSolver.Result.Failed("browser engine stopped"))
            return true
        }
    }

    private companion object {
        const val POLL_MS = 1_500L
        const val BLOCK_SETTLE_MS = 4_000L

        /**
         * How long the wall gets to finish its own work before the target is
         * asked for again. The cookies it sets are the point of the visit,
         * and some of them are set by its script rather than by its response.
         */
        const val WALL_SETTLE_MS = 2_000L

        /** What Cloudflare style and WAF checks answer on the page they guard. */
        val CHECK_STATUSES = setOf(403, 503)

        const val READ_DOCUMENT_JS =
            "document.documentElement ? document.documentElement.outerHTML : null"

        /**
         * Answered with nothing. The parser reads markup, so an avatar or a
         * video fetched here would be paid for twice and shown never. Only
         * the media hosts: static.licdn.com carries the page's own script and
         * styles and is left alone.
         */
        val MEDIA_HOSTS = listOf(
            "pbs.twimg.com",
            "video.twimg.com",
            "abs.twimg.com",
            "media.licdn.com",
            "dms.licdn.com"
        )
        val MEDIA_PATHS = listOf("/pic/", "/video/")

        /** Answered with an empty body. Never needed to pass a check. */
        val BLOCKED_HOSTS = listOf(
            "google-analytics.com",
            "googletagmanager.com",
            "googlesyndication.com",
            "doubleclick.net",
            "adservice.google.com",
            "connect.facebook.net",
            "facebook.net",
            "scorecardresearch.com",
            "amazon-adsystem.com",
            "adnxs.com",
            "taboola.com",
            "outbrain.com"
        )
    }
}
