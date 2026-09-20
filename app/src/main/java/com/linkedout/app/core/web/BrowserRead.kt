package com.linkedout.app.core.web

/**
 * How the offscreen browser should load a page when it is used as a reader
 * rather than as a way past a bot check.
 *
 * The wall changed what this class means between 0.6.13 and now. It used to
 * refuse the wall's navigation outright, and that protected a document when
 * there was one while preventing the only thing that unblocks the account
 * when there was not. The evidence came from a phone browser: the first click
 * on a refused profile lands on the wall, going back and clicking again lands
 * on the page. The visit to the wall is itself what earns the cookies the
 * second ask carries, `fid` from the wall and `__cf_bm` from the Cloudflare
 * tier in front of the country hosts. So the engine now takes the detour once,
 * lets it settle, and asks for the page a second time. [blockedPaths] only
 * refuses the second wall in a row, which is where a loop would start.
 *
 * [hostSuffix] widens the main frame beyond the exact task host. LinkedIn
 * serves a profile from the country subdomain of its owner, and a read that
 * dies on a redirect to fr.linkedin.com is a page lost to a formality.
 *
 * [looksBlocked] is the caller's own test for a wall served directly as the
 * document, which never navigates and so never touches [blockedPaths]. The
 * engine cannot know one host's wall from another's page, the caller can.
 *
 * [userAgent] overrides the engine's own string. The WebView says it is
 * Chrome on Android, and LinkedIn answers a phone with a stripped page that
 * has no activity section at all.
 *
 * [clientHints] is the other half of that override, and it is what a browser
 * built on this same engine gets wrong. Chromium sends Sec-CH-UA,
 * Sec-CH-UA-Mobile and Sec-CH-UA-Platform on every request, and a WebView
 * fills them from the real device: brand "Android WebView", mobile yes,
 * platform Android, whatever the string above claims. A server that compares
 * the two sees a desktop Chrome string beside an Android WebView that says it
 * is a phone, and that is not a browser. It is also the difference the user
 * found: a WebView based browser meets the wall every time where Firefox and
 * Chromium, whose hints agree with their string, get through. So the hints
 * are rewritten to match the string, field by field.
 *
 * [headers] ride on the main request, the Referer above all, since it is what
 * decides between the page and the wall in the first place.
 *
 * [preludeUrl] is loaded before the target, and it is the difference between
 * this engine and the browser on a desk. A browser arrives at a post from
 * somewhere: it has a page behind it, a history entry to go back to, and a
 * session that has already been greeted. The engine used to open cold on the
 * target, which is the one arrival LinkedIn treats worst.
 *
 * [awaitCookies] are the names the wall is visited for. Leaving the wall as
 * soon as any cookie appeared was a mistake made for speed in 0.6.15: the
 * first ones are set by the response, while `fid` comes from the page's own
 * abuse-features script seconds later, and cutting the load early threw away
 * the reason for being there.
 */
/**
 * The client hints a page should see, to be kept in step with the user agent
 * string they sit beside. Every field maps to one Sec-CH-UA header.
 */
class ClientHints(
    val brands: List<Brand>,
    val fullVersion: String,
    val platform: String,
    val platformVersion: String,
    val architecture: String,
    val bitness: Int,
    /** Empty on a desktop, which is the whole point of claiming to be one. */
    val model: String,
    val mobile: Boolean
) {
    class Brand(val name: String, val majorVersion: String, val fullVersion: String)
}

class BrowserRead(
    val blockedPaths: List<String> = emptyList(),
    val hostSuffix: String? = null,
    val userAgent: String? = null,
    val clientHints: ClientHints? = null,
    val headers: Map<String, String> = emptyMap(),
    val preludeUrl: String? = null,
    val awaitCookies: List<String> = emptyList(),
    val looksBlocked: (String) -> Boolean = { false }
) {

    /**
     * True when this document is the page the engine was greeted on rather
     * than the page it was sent for. Compares paths, because LinkedIn answers
     * from the country subdomain of whoever owns the page and the host is
     * therefore not a reliable difference.
     *
     * Without this, a read of the greeting that was already in flight when
     * the engine moved on came back afterwards and was taken for the answer.
     * On the device that meant a profile request returning the guest home
     * page, 144 kB of it, parsed to nothing.
     */
    fun isGreeting(currentUrl: String, targetUrl: String): Boolean {
        if (preludeUrl == null) return false
        return pathOf(currentUrl).isEmpty() && pathOf(targetUrl).isNotEmpty()
    }

    private fun pathOf(url: String): String =
        url.substringAfter("://", url)
            .substringAfter('/', "")
            .substringBefore('?')
            .substringBefore('#')
            .trim('/')

    /** True when [held] contains something the wall was visited for. */
    fun hasWhatWasWanted(held: Set<String>): Boolean =
        awaitCookies.isNotEmpty() && awaitCookies.any { it in held }

    /** True when this main frame path is one of the wall's own addresses. */
    fun refuses(path: String): Boolean {
        if (blockedPaths.isEmpty()) return false
        val lower = path.lowercase()
        return blockedPaths.any { lower == it || lower.startsWith("$it/") }
    }

    /** True when a main frame on [host] may load at all. */
    fun allowsHost(host: String): Boolean {
        val suffix = hostSuffix?.lowercase() ?: return false
        val lower = host.lowercase()
        return lower == suffix || lower.endsWith(".$suffix")
    }

    /**
     * True when what the engine is looking at is the wall rather than the
     * page, whether it navigated there or was served it in place. This is the
     * condition under which the detour and the second ask are worth spending.
     */
    fun isDetour(path: String, html: String): Boolean =
        refuses(path) || looksBlocked(html)
}
