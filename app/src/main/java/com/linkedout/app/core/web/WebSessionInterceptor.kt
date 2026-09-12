package com.linkedout.app.core.web

import okhttp3.Interceptor
import okhttp3.Response

/**
 * For a cleared host, presents the same User-Agent and Accept-Language the
 * WebView used when it earned the cookie. Other hosts are left untouched.
 *
 * This does not and cannot fake the TLS fingerprint. If a host checks that on
 * every request, the cookie is refused anyway and the gateway switches that
 * host to reading through the WebView.
 */
class WebSessionInterceptor(private val session: WebSession) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val agent = session.userAgent
        if (agent == null || !session.isCleared(request.url.host)) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder()
                .header("User-Agent", agent)
                .header("Accept-Language", session.acceptLanguage)
                .build()
        )
    }
}
