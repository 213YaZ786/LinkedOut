package com.linkedout.app.core.web

import android.os.LocaleList
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * What LinkedOut knows, for this run only, about hosts that sit behind a bot check.
 *
 * Deliberately not persisted, like instance health. The cookies themselves live
 * in the WebView cookie store and survive restarts. After a restart the first
 * read of a host is challenged once more, the WebView presents its stored
 * cookie, passes instantly, and the host is marked cleared again. That costs
 * one request and never acts on a stale assumption.
 */
class WebSession {

    /** Hosts where the WebView passed a check. Only these receive cookies. */
    private val cleared: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Hosts that still challenged the native client while carrying the cookie. */
    private val nativeRejected: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val failedAt = ConcurrentHashMap<String, Long>()

    @Volatile
    private var anyFailureAt = 0L

    /**
     * The WebView's own User-Agent, captured the first time one is created.
     * Clearance cookies are commonly bound to the User-Agent that earned them,
     * so the native client must present exactly this one to the same host.
     */
    @Volatile
    var userAgent: String? = null
        private set

    /**
     * Built the way Chromium builds it from the device locales, for example
     * "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7". Best effort, and said so: some
     * checks hash this header, and the request log will show if it misses.
     */
    val acceptLanguage: String by lazy { chromiumAcceptLanguage() }

    fun onUserAgent(value: String) {
        if (value.isNotBlank()) userAgent = value
    }

    /**
     * What became of the last client hint override. Kept so the request log
     * can say it: a read that fails with hints aligned and one that fails
     * with an engine too old to align them are two different problems.
     */
    @Volatile
    var clientHints: String? = null
        private set

    fun onClientHints(outcome: String) {
        clientHints = outcome
    }

    fun isCleared(host: String): Boolean = host in cleared

    fun markCleared(host: String) {
        cleared += host
        failedAt.remove(host)
        // A check was passed, so the wall is passable. Other hosts deserve
        // their own attempt again.
        anyFailureAt = 0L
    }

    fun markNativeRejected(host: String) {
        nativeRejected += host
    }

    /** True once the cookie alone proved not enough for the native client. */
    fun prefersWebView(host: String): Boolean = host in nativeRejected

    fun recordFailure(host: String) {
        val now = System.currentTimeMillis()
        failedAt[host] = now
        anyFailureAt = now
    }

    /**
     * Returns why a background attempt should be skipped, or null to go ahead.
     *
     * A host that failed is left alone for ten minutes. And after any failure,
     * the other hosts are left alone for two, because the pool sits behind one
     * shared WAF product. Without this, 1.1.0 spent twenty seconds per server
     * on the same wall, one after the other. The user can still complete a
     * check by hand at any time.
     */
    fun autoSolveSkipReason(host: String): String? {
        val now = System.currentTimeMillis()
        failedAt[host]?.let { last ->
            if (now - last < HOST_RETRY_AFTER_MS) return "failed recently on $host"
        }
        // A host that passed this session has its own way through, another
        // server failing says nothing about it. Without this, one failed
        // check on a newly listed server would stall xcancel for two minutes.
        if (host !in cleared && now - anyFailureAt < POOL_RETRY_AFTER_MS) {
            return "a check just failed on another server behind the same wall"
        }
        return null
    }

    private val handedOver: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Records that what the engine earned on [host] has been handed to the
     * native client. The next native refusal on that host is then worth
     * something: it says the cookies were not what was missing, and the
     * engine should lead from then on instead of being reached through three
     * doomed requests every time.
     */
    fun handCookiesOver(host: String) {
        handedOver += host
    }

    fun cookiesWereHandedOver(host: String): Boolean = host in handedOver

    /**
     * Why a browser read should be skipped, or null to go ahead.
     *
     * Shorter than [autoSolveSkipReason] and without its pool rule. That rule
     * exists to stop one wall from being met on ten servers in a row, and
     * there is one host here.
     *
     * The wait is short on purpose. There is no check for a person to pass on
     * LinkedIn, so standing down for a minute meant a reader who tapped again
     * was refused by us rather than by the wall, and the log said we were
     * waiting for something that was never going to happen. Asking again is
     * the only move there is, and in a browser it is the move that works, so
     * the cost of one failed attempt is all this holds back.
     */
    fun readSkipReason(host: String): String? {
        val last = failedAt[host] ?: return null
        val since = System.currentTimeMillis() - last
        return if (since < READ_RETRY_AFTER_MS) {
            "the browser failed on $host ${since / 1000}s ago"
        } else {
            null
        }
    }

    /**
     * Forgets every judgement made about every host. Paired with erasing the
     * engine's cookies, since a host marked cleared would otherwise keep being
     * read with a jar that no longer holds anything.
     */
    fun forget() {
        cleared.clear()
        nativeRejected.clear()
        handedOver.clear()
        failedAt.clear()
        anyFailureAt = 0L
    }

    private fun chromiumAcceptLanguage(): String {
        val tags = mutableListOf<String>()
        val locales = LocaleList.getAdjustedDefault()
        for (i in 0 until locales.size()) {
            val locale = locales[i] ?: continue
            val full = locale.toLanguageTag()
            if (full.isNotBlank() && full != "und" && full !in tags) tags += full
            val base = locale.language
            if (base.isNotBlank() && base !in tags) tags += base
        }
        if (tags.isEmpty()) return "en-US,en;q=0.9"
        return tags.mapIndexed { index, tag ->
            if (index == 0) {
                tag
            } else {
                val q = (10 - index).coerceAtLeast(1) / 10.0
                tag + ";q=" + String.format(Locale.US, "%.1f", q)
            }
        }.joinToString(",")
    }

    private companion object {
        const val HOST_RETRY_AFTER_MS = 10 * 60_000L
        const val POOL_RETRY_AFTER_MS = 2 * 60_000L
        const val READ_RETRY_AFTER_MS = 15_000L
    }
}
