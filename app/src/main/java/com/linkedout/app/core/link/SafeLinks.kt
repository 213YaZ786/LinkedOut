package com.linkedout.app.core.link

import androidx.compose.ui.platform.UriHandler

/**
 * Which addresses may leave the app. A post can carry any scheme (intent:,
 * file:, content:, javascript:, market:, an app's own), so only http and
 * https are handed to Android; the rest stays text, nothing opens it.
 *
 * Allow-list as Android's guidance on unsafe deep links asks for
 * (https://developer.android.com/privacy-and-security/risks/unsafe-use-of-deeplinks)
 * and as Signal-Android checks a link before using it
 * (https://github.com/signalapp/Signal-Android,
 * app/src/main/java/org/thoughtcrime/securesms/util/LinkUtil.kt,
 * isValidPreviewUrl: the scheme read from the parsed address, a legal
 * address only). An intent: address is never parsed with Intent.parseUri.
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.sh.
 */
object SafeLinks {

    /**
     * [raw] ready to open, or null when it must not leave the app: the
     * scheme is not http or https (any case, written back in lower case
     * since Android matches schemes as written), the host is missing, or it
     * holds a space, a control or an invisible formatting character.
     * Signal's isValidPreviewUrl refuses the same hiding places (bidi
     * overrides, a scheme or host written to look like another).
     */
    fun openable(raw: String?): String? {
        val url = raw?.trim().orEmpty()
        if (url.isEmpty()) return null
        if (url.any { it.isWhitespace() || it.isISOControl() || it.category == CharCategory.FORMAT }) return null
        val colon = url.indexOf(':')
        if (colon <= 0) return null
        val scheme = url.substring(0, colon)
        if (!scheme.equals("https", ignoreCase = true) && !scheme.equals("http", ignoreCase = true)) return null
        if (!url.startsWith("//", colon + 1)) return null
        val authority = url.substring(colon + 3).takeWhile { it != '/' && it != '?' && it != '#' }
        val host = authority.substringAfterLast('@')
        if (host.isEmpty() || host.startsWith(":")) return null
        return scheme.lowercase() + url.substring(colon)
    }

    fun isOpenable(raw: String?): Boolean = openable(raw) != null
}

/**
 * The handler every link tap in the app goes through (LocalUriHandler):
 * [inApp] first, for the addresses the app shows itself (a profile, a
 * post), then only what [SafeLinks.openable] lets out goes to [platform].
 * [onRefused] says nothing opened, for an address of another kind or one no
 * app can open.
 *
 * Wrapping Compose's handler as the apps already did for their own links:
 * https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/UriHandler
 */
class SafeUriHandler(
    private val platform: UriHandler,
    private val inApp: (String) -> Boolean,
    private val onRefused: () -> Unit
) : UriHandler {

    override fun openUri(uri: String) {
        if (inApp(uri)) return
        val url = SafeLinks.openable(uri)
        if (url == null) {
            onRefused()
            return
        }
        // Compose's handler throws when no app can open the address.
        try {
            platform.openUri(url)
        } catch (_: IllegalArgumentException) {
            onRefused()
        }
    }
}
