package com.linkedout.app.core.link

import com.linkedout.app.core.network.ResponseLimit
import com.linkedout.app.core.network.SizeLimits
import com.linkedout.app.core.network.isTooLarge
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where a Google link leads, for the links whose address Google keeps to
 * itself (see LinkCleaner.needsResolving): the /goto links of its search
 * results and share.google short links.
 *
 * One request, to Google only, as a click would make it, and nothing more:
 * redirects are not followed and the page is never opened, only the address
 * Google answers with is read. A client of its own, without cookies, so
 * nothing Google sets is kept or sent back, and nothing of the app's own
 * sessions goes to Google.
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.sh.
 */
class RedirectResolver {

    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = TIMEOUT_MS
            requestTimeoutMillis = TIMEOUT_MS
        }
        // The notice is a few kB: anything near the cap is not one.
        engine { addInterceptor(ResponseLimit(SizeLimits.NOTICE)) }
    }

    /** The cleaned address [url] leads to, or null when Google did not say. */
    suspend fun resolve(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = client.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }
            val target = response.headers[HttpHeaders.Location]
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                // A /url link with a coded address answers a page saying
                // where it leads, the address written out as the link's text.
                ?: noticeTarget(response.bodyAsText())
            target?.let(LinkCleaner::clean)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            null
        }
    }

    /**
     * Where [url] ends after its redirects, for the link sheet's Expand, asked on a tap: the shortener
     * is contacted and nobody else (no unshortening service). Followed one hop at a time as URLCheck's
     * StatusModule does (https://github.com/TrianguloY/URLCheck/blob/master/app/src/main/java/com/trianguloy/urlchecker/modules/list/StatusModule.java,
     * CC BY 4.0): a GET that follows nothing, the Location header read and resolved against the address
     * it came from (relative ones too), at most [MAX_HOPS]. Only the headers are read, never the page.
     * Each hop must be a web address a person could open (SafeLinks) on a host of the internet (never
     * this phone, the home network or a link-local address, as SMS's LinkPreview.public checks before
     * it fetches). Null when a hop is refused, fails or the chain is too long.
     */
    suspend fun expand(url: String): String? = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(EXPAND_TIMEOUT_MS) {
                var at = url
                repeat(MAX_HOPS) {
                    if (!SafeLinks.isOpenable(at) || !isPublicHost(java.net.URL(at).host)) return@withTimeoutOrNull null
                    val location = try {
                        client.prepareGet(at) { header(HttpHeaders.UserAgent, USER_AGENT) }.execute { response ->
                            if (response.status.value in 300..399) response.headers[HttpHeaders.Location] ?: "" else null
                        }
                    } catch (failure: Throwable) {
                        if (failure is CancellationException) throw failure
                        // A page over the cap (ResponseLimit) is a page, not a redirect: the end.
                        if (failure.isTooLarge()) null else return@withTimeoutOrNull null
                    }
                    if (location == null) return@withTimeoutOrNull at
                    if (location.isEmpty()) return@withTimeoutOrNull null
                    at = java.net.URL(java.net.URL(at), location).toExternalForm()
                }
                null
            }
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            null
        }
    }

    companion object {
        /** A host on the internet: never this phone, the home network or a link-local address, which a link could aim at. */
        fun isPublicHost(host: String): Boolean = runCatching {
            java.net.InetAddress.getAllByName(host).none {
                it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || it.isAnyLocalAddress ||
                    it.isMulticastAddress || (it is java.net.Inet6Address && (it.address[0].toInt() and 0xfe) == 0xfc)
            }
        }.getOrDefault(false)

        private const val MAX_HOPS = 8
        private const val EXPAND_TIMEOUT_MS = 20_000L

        /** The address Google's redirect notice names, or null on any other page. */
        fun noticeTarget(html: String): String? =
            Regex("""<a href="/goto\?url=[^"]*">(https?://[^<\s]+)</a>""").find(html)?.groupValues?.get(1)
                ?.replace("&amp;", "&")

        private const val TIMEOUT_MS = 8_000L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36"
    }
}
