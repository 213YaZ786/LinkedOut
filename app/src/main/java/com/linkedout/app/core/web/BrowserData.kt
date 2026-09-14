package com.linkedout.app.core.web

import android.webkit.CookieManager
import android.webkit.WebStorage

/**
 * Erases everything the offscreen browser engine kept on this phone.
 *
 * The engine has its own cookie store, separate from [GuestCookies], and the
 * app never reads it. That is the price of using a real browser to get past a
 * wall a plain client cannot, and it is only acceptable while the reader can
 * erase it. So "Clear browsing data" wipes this as well as the guest jar, and
 * neither one survives it.
 *
 * Nothing here can log anyone in. The app has no credential to give and no
 * screen to type one in.
 */
object BrowserData {

    /**
     * The names of the cookies the engine currently holds for [url], read
     * from its own store. Values are never returned: the names are evidence
     * enough that a wall visit earned something, and the values are exactly
     * what this app promises not to handle more than it must.
     */
    fun cookieNames(url: String): List<String> =
        cookieLine(url).split(';')
            .mapNotNull { it.substringBefore('=').trim().takeIf(String::isNotEmpty) }
            .sorted()

    /**
     * The raw line the engine would send to [url], in the shape of a `Cookie:`
     * header. Read by [GuestCookies.fromHeader] so what the engine earned at
     * the wall can be handed to the native client, which is what turns a
     * second reading into one plain request instead of two page loads.
     */
    fun cookieLine(url: String): String = runCatching {
        CookieManager.getInstance().getCookie(url).orEmpty()
    }.getOrDefault("")

    /** Best effort by design: a phone with no WebView installed throws here. */
    fun clear() {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        runCatching { WebStorage.getInstance().deleteAllData() }
    }
}
