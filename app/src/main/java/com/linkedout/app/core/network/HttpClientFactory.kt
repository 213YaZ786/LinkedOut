package com.linkedout.app.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import com.linkedout.app.core.web.WebSession
import com.linkedout.app.core.web.WebSessionInterceptor
import com.linkedout.app.core.web.WebViewCookieJar

/**
 * The single HTTP client for the app.
 *
 * expectSuccess stays false on purpose. A 429 or a 403 is information we want
 * to classify and report, not an exception thrown from deep inside a plugin.
 *
 * Cookies: the client has none of its own. For hosts the challenge WebView has
 * cleared, it reads the WebView's cookie store, and presents the WebView's
 * User-Agent. Every other host sees a cookieless client, as before.
 */
object HttpClientFactory {

    const val CONNECT_TIMEOUT_MS = 8_000L
    const val REQUEST_TIMEOUT_MS = 15_000L
    const val PROBE_TIMEOUT_MS = 6_000L

    /**
     * [userAgent] is the string every request carries unless it sets its own.
     * It used to be a feed reader identifier, which LinkedIn answers with a
     * stripped page, and it was wrong for every request this app makes. The
     * caller supplies the one string the app speaks with, so the default and
     * the per request header can no longer disagree.
     */
    fun create(session: WebSession, userAgent: String): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false

        engine {
            config { cookieJar(WebViewCookieJar(session)) }
            addInterceptor(WebSessionInterceptor(session))
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
