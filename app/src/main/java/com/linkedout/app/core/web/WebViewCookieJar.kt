package com.linkedout.app.core.web

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Lets the native client use the cookies the WebView earned, by reading the
 * WebView's own store instead of copying it. One store, so nothing drifts and
 * expiry is handled by the engine that set the cookie.
 *
 * Scoped hard. Only hosts the WebView has cleared get any cookie at all, so
 * every other request LinkedOut makes stays as cookieless as it always was.
 * Analytics cookies set by page scripts are dropped even for cleared hosts.
 */
class WebViewCookieJar(private val session: WebSession) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!session.isCleared(url.host)) return emptyList()

        // Safe to touch here: a host is only ever cleared after a WebView ran,
        // so the cookie manager is already initialised.
        val raw = runCatching { CookieManager.getInstance().getCookie(url.toString()) }
            .getOrNull()
            ?: return emptyList()

        return raw.split(';').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val name = part.substring(0, eq).trim()
            val value = part.substring(eq + 1).trim()
            if (name.isEmpty() || isTracker(name)) return@mapNotNull null
            runCatching {
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .hostOnlyDomain(url.host)
                    .path("/")
                    .secure()
                    .build()
            }.getOrNull()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!session.isCleared(url.host)) return
        val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        // A host that refreshes its clearance on a native response keeps the
        // WebView store current, so the next check starts from the new value.
        cookies.forEach { cookie ->
            runCatching { manager.setCookie(url.toString(), cookie.toString()) }
        }
    }

    private fun isTracker(name: String): Boolean =
        TRACKER_PREFIXES.any { name.startsWith(it) }

    private companion object {
        val TRACKER_PREFIXES = listOf("_ga", "_gid", "_gat", "__gads", "__gpi", "_fbp", "__eoi")
    }
}
