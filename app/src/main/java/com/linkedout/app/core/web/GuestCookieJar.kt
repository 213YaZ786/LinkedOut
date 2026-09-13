package com.linkedout.app.core.web

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.File

/**
 * The single cookie jar of the app, and the only place a cookie is ever kept.
 *
 * Two behaviours, one per reason a cookie exists here.
 *
 * A host the challenge WebView cleared keeps working exactly as before,
 * through [WebViewCookieJar], because a clearance cookie belongs to the
 * browser that earned it.
 *
 * Every other host gets nothing at all, except the one domain named in
 * [cookieDomain]. That one gets the guest session described in [GuestCookies],
 * which is what LinkedIn hands any first time visitor and what it recognises
 * a returning one by.
 */
class GuestCookieJar(
    private val session: WebSession,
    private val guest: GuestCookies,
    private val cookieDomain: String
) : CookieJar {

    private val webView = WebViewCookieJar(session)

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (session.isCleared(url.host)) return webView.loadForRequest(url)
        if (!allows(url.host)) return emptyList()
        return guest.matching(url.host, url.encodedPath).mapNotNull { cookie ->
            runCatching {
                Cookie.Builder()
                    .name(cookie.name)
                    .value(cookie.value)
                    .hostOnlyDomain(url.host)
                    .path("/")
                    .secure()
                    .build()
            }.getOrNull()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (session.isCleared(url.host)) {
            webView.saveFromResponse(url, cookies)
            return
        }
        if (!allows(url.host) || cookies.isEmpty()) return
        guest.put(
            cookies.map { cookie ->
                GuestCookie(
                    name = cookie.name,
                    value = cookie.value,
                    domain = cookie.domain,
                    path = cookie.path,
                    // okhttp gives a session cookie the maximum date rather
                    // than a flag, so persistent decides, not the number.
                    expiresAtMillis = if (cookie.persistent) cookie.expiresAt else 0L,
                    secure = cookie.secure,
                    hostOnly = cookie.hostOnly
                )
            }
        )
    }

    private fun allows(host: String): Boolean {
        val target = host.lowercase()
        return target == cookieDomain || target.endsWith(".$cookieDomain")
    }
}

/**
 * The persistent half, a plain file in the app's own storage.
 *
 * Same approach as the accounts list: readable, tiny, and deletable. Written
 * on every change rather than on a timer, because the file is a few hundred
 * bytes and a crash between a cookie and its write is exactly how a session
 * ends up half remembered.
 */
class FileCookieStorage(context: Context) : GuestCookies.Storage {

    private val file = File(context.filesDir, "guest-cookies.txt")

    override fun read(): String =
        runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")

    override fun write(text: String) {
        runCatching {
            if (text.isBlank()) file.delete() else file.writeText(text)
        }
    }
}
