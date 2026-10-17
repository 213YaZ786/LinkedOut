package com.linkedout.app.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import com.linkedout.app.core.web.GuestCookieJar
import com.linkedout.app.core.web.GuestCookies
import com.linkedout.app.core.web.WebSession
import com.linkedout.app.core.web.WebSessionInterceptor

/**
 * The single HTTP client for the app.
 *
 * expectSuccess stays false on purpose. A 429 or a 403 is information we want
 * to classify and report, not an exception thrown from deep inside a plugin.
 *
 * Cookies: one jar, two narrow cases. For hosts the challenge WebView has
 * cleared, it reads the WebView's cookie store and presents the WebView's
 * User-Agent. For [cookieDomain] it carries the guest session LinkedIn hands
 * any anonymous visitor, which is what a browser has and this app did not.
 * Every other host still sees a cookieless client.
 */
object HttpClientFactory {

    const val CONNECT_TIMEOUT_MS = 8_000L
    const val REQUEST_TIMEOUT_MS = 15_000L
    const val PROBE_TIMEOUT_MS = 6_000L

    /**
     * The biggest answer taken, for the network and for a page the web
     * engine hands back. A LinkedIn page is about 400 kB (a company page
     * measured 2026-10-17: 464 kB); 8 MB leaves seventeen times that and
     * stops a hostile or broken answer from being read whole into memory.
     */
    const val MAX_BODY_BYTES = 8L * 1024 * 1024

    /**
     * [userAgent] is the string every request carries unless it sets its own.
     * It used to be a feed reader identifier, which LinkedIn answers with a
     * stripped page, and it was wrong for every request this app makes. The
     * caller supplies the one string the app speaks with, so the default and
     * the per request header can no longer disagree.
     */
    fun create(
        session: WebSession,
        guest: GuestCookies,
        userAgent: String,
        cookieDomain: String
    ): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false

        engine {
            config { cookieJar(GuestCookieJar(session, guest, cookieDomain)) }
            addInterceptor(WebSessionInterceptor(session))
            // Ported from Signal-Android's OkHttpUtil size limit, see ResponseLimit.
            addInterceptor(ResponseLimit(MAX_BODY_BYTES))
        }
        followRedirects = true

        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }

        defaultRequest {
            header("User-Agent", userAgent)
            header("Accept-Language", "en;q=0.9")
        }
    }
}
