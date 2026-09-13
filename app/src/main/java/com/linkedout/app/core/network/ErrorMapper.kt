package com.linkedout.app.core.network

import com.linkedout.app.core.common.AppError
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns transport exceptions and HTTP statuses into the named cases in
 * AppError. This is the only place in LinkedOut that is allowed to look at raw
 * exceptions, everything above it deals in Outcome and AppError.
 */
object ErrorMapper {

    /**
     * LinkedIn's own denial code. It is not an HTTP status: the standard has
     * nothing above 599, and LinkedIn sends 999 with a tiny body when it
     * decides a client is asking for too much. Read as a number it looks like
     * a 5xx, which would mean "LinkedIn is broken, try again", the opposite of
     * what it says.
     */
    const val LINKEDIN_DENIED = 999

    fun fromThrowable(host: String, t: Throwable): AppError = when (t) {
        is UnknownHostException -> AppError.DnsFailure(host)
        is SSLException -> AppError.TlsFailure(host, t.message)
        is HttpRequestTimeoutException -> AppError.Timeout(host, HttpClientFactory.REQUEST_TIMEOUT_MS)
        is SocketTimeoutException -> AppError.Timeout(host, HttpClientFactory.REQUEST_TIMEOUT_MS)
        is ConnectException -> AppError.ServerError(host, 0)
        else -> AppError.Unknown("${t::class.java.simpleName}: ${t.message}")
    }

    /**
     * Returns null when the response is genuinely usable. A 200 that carries a
     * bot challenge is not usable, so [bodyHint] lets the caller pass the body
     * for inspection.
     */
    fun fromResponse(
        host: String,
        response: HttpResponse,
        bodyHint: String? = null,
        handle: String? = null
    ): AppError? = fromStatus(
        host = host,
        url = response.call.request.url.toString(),
        code = response.status.value,
        retryAfterSeconds = response.headers["Retry-After"]?.toLongOrNull(),
        bodyHint = bodyHint,
        handle = handle
    )

    /**
     * Same classification without a Ktor response, for pages that arrived
     * through the WebView and so have a status and a body but no HttpResponse.
     */
    fun fromStatus(
        host: String,
        url: String,
        code: Int,
        retryAfterSeconds: Long?,
        bodyHint: String? = null,
        handle: String? = null
    ): AppError? {
        if (code == 429) return AppError.RateLimited(host, retryAfterSeconds)

        // Before the status checks, because the WAF answers 403 and Anubis
        // answers 200, and neither means what those codes usually mean.
        if (bodyHint != null) {
            ChallengeDetector.detect(code, bodyHint)?.let { kind ->
                return AppError.ChallengeRequired(host, url, kind, code)
            }
        }

        if (code == 403 || code == 401 || code == 406) return AppError.ClientRefused(host, code)
        if (code == 404 || code == 410) return AppError.AccountNotFound(handle ?: host)
        // Before the 5xx test, which would otherwise swallow it.
        if (code == LINKEDIN_DENIED) return AppError.ClientRefused(host, code)
        if (code >= 500) return AppError.ServerError(host, code)
        if (code >= 400) return AppError.ClientRefused(host, code)
        return null
    }
}
