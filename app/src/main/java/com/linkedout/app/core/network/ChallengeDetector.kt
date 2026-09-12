package com.linkedout.app.core.network

import com.linkedout.app.core.common.ChallengeKind

/**
 * Recognises a bot check by what the host served, as its own condition rather
 * than as a parse failure.
 *
 * Pure function, no Android, so it can run on a native response and on the
 * HTML a WebView ends up showing, which is how the solver knows it is through.
 *
 * Kept deliberately narrow. A false positive here sends a healthy page through
 * the slow path, so real content always wins over any marker.
 */
object ChallengeDetector {

    /** What was found and why, for the request log. */
    data class Verdict(val kind: ChallengeKind, val reason: String, val title: String?)

    fun detect(status: Int, body: String): ChallengeKind? = inspect(status, body)?.kind

    /** True when the page carries posts, whatever its status claims. */
    fun hasContent(body: String): Boolean = CONTENT_MARKERS.any { it in body }

    fun inspect(status: Int, body: String): Verdict? {
        // A page carrying posts is content, whatever else it says. A tweet that
        // happens to quote "not a bot" must not trip the detector.
        if (CONTENT_MARKERS.any { it in body }) return null

        val small = body.length < SMALL_PAGE_CHARS
        // Challenge pages are small and name themselves early. On a large page
        // only the head is inspected, where a title or a script tag would sit.
        val scope = (if (small) body else body.take(HEAD_CHARS)).lowercase()

        val title = titleOf(body)
        BLOCK_MARKERS.firstOrNull { it in scope }?.let {
            return Verdict(ChallengeKind.WAF_BLOCK, "\"$it\"", title)
        }
        POW_MARKERS.firstOrNull { it in scope }?.let {
            return Verdict(ChallengeKind.PROOF_OF_WORK, "\"$it\"", title)
        }
        JS_MARKERS.firstOrNull { it in scope }?.let {
            return Verdict(ChallengeKind.JS_INTERSTITIAL, "\"$it\"", title)
        }
        // The four 403 bodies seen in the field were within 200 bytes of each
        // other, one WAF product. A small 403 with no marker is that wall.
        if (status == 403 && small) {
            return Verdict(ChallengeKind.WAF_BLOCK, "small 403, no marker", title)
        }
        return null
    }

    private fun titleOf(body: String): String? = runCatching { findTitle(body) }.getOrNull()

    private fun findTitle(body: String): String? {
        val lower = body.take(HEAD_CHARS * 2).lowercase()
        val open = lower.indexOf("<title")
        if (open < 0) return null
        val start = lower.indexOf('>', open)
        val end = lower.indexOf("</title>", start)
        if (start < 0 || end < 0) return null
        return body.substring(start + 1, end).replace(Regex("\\s+"), " ").trim().take(80)
            .ifBlank { null }
    }

    private const val SMALL_PAGE_CHARS = 40_000
    private const val HEAD_CHARS = 4_000

    private val CONTENT_MARKERS = listOf(
        "class=\"timeline-item",
        "class=\"tweet-content",
        "activity-posts",
        "<rss"
    )

    private val BLOCK_MARKERS = listOf(
        "sorry, you have been blocked"
    )

    /** Anubis. Its page title, its asset path, and its challenge payload id. */
    private val POW_MARKERS = listOf(
        "not a bot",
        ".within.website",
        "anubis_challenge"
    )

    private val JS_MARKERS = listOf(
        "verifying your browser",
        "antibot-ref-id",
        "just a moment",
        "checking your browser",
        "cf-browser-verification",
        "challenge-platform",
        "enable javascript and cookies to continue",
        "attention required"
    )
}
