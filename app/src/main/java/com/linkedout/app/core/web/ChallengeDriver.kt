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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /** Navigations off the domain, refused. */
    var refusedNavigations = 0
        private set

    /** True once the engine let the wall load, on purpose, to be given its cookies. */
    var wallVisited = false
        private set

    /** True once the target was asked for the second time. One retry, ever. */
    private var wallRetried = false

    /** Which leg of a reading task is in flight. See [advance]. */
    private enum class Leg { PRELUDE, TARGET, WALL, BACK, RETRY }

    private var leg: Leg = if (task.read?.preludeUrl != null) Leg.PRELUDE else Leg.TARGET

    private var wallSince = 0L

    /**
     * What the last probe saw. A page that has not changed since the last
     * full read has nothing new to say, and reading it again is the expensive
     * part of this class.
     */
    private var lastSeen: String? = null

    /**
     * Which document the engine is on. Every load moves it forward, and a
     * read that comes back carrying an older number is describing a page that
     * has already been left.
     *
     * Two reads of one document can be in flight at once, one from the load
     * that finished and one from the timer. The first makes the engine move
     * on, and the second used to arrive afterwards holding the page it had
     * just left, on a leg where that page counts as the answer. That is how
     * the home page came back labelled as a profile.
     */
    private var document = 0

    /** Off the main thread, where a 400 kB document belongs. */
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Default)


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
        val read = task.read
        val first = read?.preludeUrl?.takeIf { leg == Leg.PRELUDE } ?: task.url
        if (read == null) view.loadUrl(task.url) else load(first, read)
        handler.postDelayed(poll, POLL_MS)
    }

    /** Must be called when the view leaves the screen. Idempotent. */
    fun release() {
        done = true
        work.cancel()
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
     * Asks the page for a few bytes about itself, and only pulls the whole
     * document when those bytes have changed.
     *
     * This is where the app's own animations were being eaten. A LinkedIn page
     * is around 400 kB, `outerHTML` copies it, the bridge escapes it into a
     * JSON string, and the callback lands on the main thread, where it was
     * then unescaped character by character. On a timer, every 1.5 seconds,
     * for the life of the task. The probe below costs a few dozen bytes and
     * answers the only question the timer exists for: has this document been
     * swapped without a navigation.
     */
    private fun inspect() {
        if (done || lastFinishedAt == 0L || view.progress < 100) return
        val current = view.url ?: return
        if (!current.startsWith("https://")) return

        view.evaluateJavascript(PROBE_JS) { raw ->
            if (done) return@evaluateJavascript
            val seen = raw?.trim('"').orEmpty()
            if (seen.isEmpty() || seen == "null") return@evaluateJavascript
            if (!seen.startsWith("complete") && !seen.startsWith("interactive")) return@evaluateJavascript
            if (seen == lastSeen) return@evaluateJavascript
            lastSeen = seen
            readDocument(current)
        }
    }

    /** The expensive read, made once per version of a document. */
    private fun readDocument(current: String) {
        val asked = document
        view.evaluateJavascript(READ_DOCUMENT_JS) { raw ->
            if (done || raw == null || document != asked) return@evaluateJavascript
            // Decoding and matching happen off the main thread. Only the
            // decisions come back to it, because only they touch the view.
            work.launch {
                val html = decode(raw) ?: return@launch
                withContext(Dispatchers.Main) { judge(current, html, asked) }
            }
        }
    }

    private fun judge(current: String, html: String, asked: Int) {
        if (done || document != asked) return

        // The page the engine was greeted on is never an answer to a request
        // for something else, whatever leg thinks it has finished.
        val read = task.read
        if (leg != Leg.PRELUDE && read?.isGreeting(current, task.url) == true) return

        if (read != null && !advance(read, current, html)) return

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

    /**
     * Walks a reading task through its legs and says whether the document in
     * hand is the answer. False means the engine is still working and this
     * document is scaffolding: the page it was greeted on, or the wall.
     *
     * The legs are one person's gesture, written down. Arrive somewhere
     * first. Ask for the post. If the wall comes up, let it finish its work,
     * because that work is what hands over `fid`. Go back, which is what a
     * thumb does, and ask again from the page behind it. Whatever comes back
     * then is the answer, page or wall.
     */
    private fun advance(read: BrowserRead, current: String, html: String): Boolean {
        val onWall = read.isDetour(Uri.parse(current).path.orEmpty(), html)
        when (leg) {
            Leg.PRELUDE -> {
                // Greeted. Now ask for what was wanted.
                leg = Leg.TARGET
                load(task.url, read)
                return false
            }
            Leg.TARGET -> {
                if (!onWall) return true
                wallVisited = true
                leg = Leg.WALL
                wallSince = SystemClock.elapsedRealtime()
                watchWall(read)
                return false
            }
            // The chain started when the wall came up keeps its own timer.
            // Nothing here has to push it along.
            Leg.WALL -> return false
            // Going back lands on the page behind, which is not an answer to
            // anything. The second ask has not even been made yet.
            Leg.BACK -> return false
            Leg.RETRY -> return true
        }
    }

    /**
     * Sits on the wall until it has handed over what it is visited for, or
     * until it has had long enough. Then goes back and asks again.
     *
     * Going back rather than reloading matters. A reload from the wall carries
     * the wall as its referrer, which is the arrival LinkedIn just refused. A
     * browser that goes back and clicks again arrives from the page behind,
     * and that is the one that works.
     */
    private fun watchWall(read: BrowserRead) {
        if (done || leg != Leg.WALL) return
        val held = BrowserData.cookieNames(task.url).toSet()
        val waited = SystemClock.elapsedRealtime() - wallSince
        val ready = read.hasWhatWasWanted(held) || waited > WALL_MAX_MS
        if (!ready) {
            handler.postDelayed({ watchWall(read) }, WALL_CHECK_MS)
            return
        }
        wallRetried = true
        if (view.canGoBack()) {
            leg = Leg.BACK
            document++
            lastSeen = null
            view.goBack()
            // The back has to land before the second ask, or the ask is the
            // navigation that gets replaced.
            handler.postDelayed({
                if (done) return@postDelayed
                leg = Leg.RETRY
                load(task.url, read)
            }, BACK_SETTLE_MS)
        } else {
            leg = Leg.RETRY
            load(task.url, read)
        }
    }

    private fun load(url: String, read: BrowserRead) {
        document++
        lastSeen = null
        if (read.headers.isEmpty()) view.loadUrl(url) else view.loadUrl(url, read.headers)
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
            if (!allowed) {
                refusedNavigations++
                return true
            }
            // The wall's own addresses. Before the retry they are allowed to
            // load: the visit is what sets the cookies the second ask rides
            // on. After it, a wall coming back is a refusal to report, not a
            // page to stand on, and following it again would loop.
            // The wall's own addresses are followed, on purpose. The visit is
            // what sets the cookies the second ask rides on, and refusing it
            // in 0.6.13 was the mistake that kept the page out of reach. There
            // is no loop to guard against any more: the legs in [advance] end
            // at the second ask whatever comes back.
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
            // A new document, so the last probe describes something that is
            // no longer on screen, and any read still in flight describes it
            // too.
            lastSeen = null
            document++
        }

        override fun onPageFinished(view: WebView, url: String?) {
            lastFinishedAt = SystemClock.elapsedRealtime()
            // A finished load is a new document by definition, so this one
            // skips the probe and reads it.
            val current = view.url
            lastSeen = null
            if (current != null && current.startsWith("https://")) readDocument(current)
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


        /** How often the wall is asked whether it has handed anything over. */
        const val WALL_CHECK_MS = 300L

        /**
         * The longest the wall is given to run its own scripts. `fid` is set
         * by the abuse-features module the wall loads, not by its response,
         * so this is a wait for someone else's work to finish.
         */
        const val WALL_MAX_MS = 9_000L

        /** A back gesture is a navigation and needs its moment to land. */
        const val BACK_SETTLE_MS = 600L

        /** What Cloudflare style and WAF checks answer on the page they guard. */
        val CHECK_STATUSES = setOf(403, 503)

        const val READ_DOCUMENT_JS =
            "document.documentElement ? document.documentElement.outerHTML : null"

        /**
         * A few bytes that change when the document does: its state, the two
         * meta fields that name a LinkedIn page, and how many elements it
         * holds. Cheap enough to run on a timer without the reader feeling it.
         */
        const val PROBE_JS =
            "(function(){try{var m=document.querySelector('meta[name=pageKey]');" +
                "var c=document.querySelector('link[rel=canonical]');" +
                "return document.readyState+'|'+(m?m.content:'')+'|'+(c?c.getAttribute('href'):'')" +
                "+'|'+document.getElementsByTagName('*').length;}catch(e){return 'err'}})()"

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
