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

    /** Best effort by design: a phone with no WebView installed throws here. */
    fun clear() {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        runCatching { WebStorage.getInstance().deleteAllData() }
    }
}
