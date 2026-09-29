package com.linkedout.app.core.link

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    }

    /** The cleaned address [url] leads to, or null when Google did not say. */
    suspend fun resolve(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = client.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }
            response.headers[HttpHeaders.Location]
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?.let(LinkCleaner::clean)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            null
        }
    }

    private companion object {
        const val TIMEOUT_MS = 8_000L
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36"
    }
}
