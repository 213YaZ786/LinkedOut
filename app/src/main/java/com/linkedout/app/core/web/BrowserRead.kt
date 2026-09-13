package com.linkedout.app.core.web

/**
 * How the offscreen browser should load a page when it is used as a reader
 * rather than as a way past a bot check.
 *
 * The distinction matters in three places.
 *
 * [blockedPaths] are main frame addresses the engine must not be allowed to
 * follow. LinkedIn's sign in wall is not a status code and not a check: it is
 * a script on the page that navigates to /authwall. A browser obeys it, which
 * is why the engine was kept off the reading path until now. Refusing exactly
 * that navigation leaves the document that was already served in place, and
 * that document is the page.
 *
 * [userAgent] overrides the engine's own string. The WebView says it is Chrome
 * on Android, and LinkedIn answers a phone with a stripped page that has no
 * activity section at all, which is the one thing this app exists to read.
 * Stated plainly: overriding the string does not change the client hints the
 * engine sends beside it, so a server that compares the two can still tell.
 *
 * [headers] ride on the main request. The Referer is the one that matters,
 * since it is what decides between the page and the wall in the first place.
 */
class BrowserRead(
    val blockedPaths: List<String> = emptyList(),
    val userAgent: String? = null,
    val headers: Map<String, String> = emptyMap()
) {

    /** True when this main frame path must not be allowed to load. */
    fun refuses(path: String): Boolean {
        if (blockedPaths.isEmpty()) return false
        val lower = path.lowercase()
        return blockedPaths.any { lower == it || lower.startsWith("$it/") }
    }
}
