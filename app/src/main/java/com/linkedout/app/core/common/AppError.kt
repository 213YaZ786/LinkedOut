package com.linkedout.app.core.common

/**
 * Every failure the app can surface, named precisely.
 *
 * Design rule: no generic "something went wrong". Each case carries enough
 * context for the Connection screen to explain why LinkedOut cannot show
 * content, and whether the reader, the network, LinkedIn or the app is at
 * fault.
 */
sealed interface AppError {

    /** Who or what is responsible. Drives the tone of the message shown. */
    val blame: Blame

    /** Whether retrying the exact same call could plausibly succeed. */
    val retryable: Boolean

    // ---- device side -------------------------------------------------------

    /** No network transport available at all. */
    data object Offline : AppError {
        override val blame = Blame.DEVICE
        override val retryable = true
    }

    /** Host name did not resolve. DNS blocked or filtered upstream. */
    data class DnsFailure(val host: String) : AppError {
        override val blame = Blame.NETWORK
        override val retryable = true
    }

    /** TLS handshake failed. Possible interception or an expired certificate. */
    data class TlsFailure(val host: String, val detail: String?) : AppError {
        override val blame = Blame.NETWORK
        override val retryable = false
    }

    /** Connected but no answer in time. */
    data class Timeout(val host: String, val millis: Long) : AppError {
        override val blame = Blame.HOST
        override val retryable = true
    }

    // ---- host side ---------------------------------------------------------

    /** LinkedIn refused this client outright, 401, 403 or 406. */
    data class ClientRefused(val host: String, val status: Int) : AppError {
        override val blame = Blame.HOST
        override val retryable = false
    }

    /**
     * A bot check in front of the page: a JavaScript interstitial or a firewall
     * that refuses non browsers. The host itself is healthy, which is why this
     * is not [ClientRefused] and why it is worth passing rather than reporting.
     *
     * This is not the guest authwall. The wall answers 200 with a readable body
     * and is handled by the Referer ladder in LinkedInSource, never here.
     *
     * [url] is kept so the check can be completed in app for exactly the page
     * that was refused.
     */
    data class ChallengeRequired(
        val host: String,
        val url: String,
        val kind: ChallengeKind,
        val status: Int
    ) : AppError {
        override val blame = Blame.HOST
        override val retryable = true
    }

    /** Rate limited. retryAfterSeconds comes from the Retry-After header. */
    data class RateLimited(val host: String, val retryAfterSeconds: Long?) : AppError {
        override val blame = Blame.HOST
        override val retryable = true
    }

    /** The host is up but broken, a 5xx. */
    data class ServerError(val host: String, val status: Int) : AppError {
        override val blame = Blame.HOST
        override val retryable = true
    }

    // ---- upstream account side ---------------------------------------------

    /** No such profile: renamed, deleted, or the address is wrong. */
    data class AccountNotFound(val handle: String) : AppError {
        override val blame = Blame.UPSTREAM
        override val retryable = false
    }

    /** The profile exists but nothing of it is public to a guest. */
    data class AccountUnavailable(val handle: String, val reason: String?) : AppError {
        override val blame = Blame.UPSTREAM
        override val retryable = false
    }

    /**
     * The account is fine but this one post is not there: deleted, restricted,
     * or from a profile that has gone private. [reason] is LinkedIn's own
     * wording when the page gave one.
     */
    data class PostUnavailable(val host: String, val reason: String?) : AppError {
        override val blame = Blame.UPSTREAM
        override val retryable = false
    }

    // ---- our side ----------------------------------------------------------

    /**
     * HTTP 200 arrived but the parser found nothing it recognised. This almost
     * always means LinkedIn changed its markup and LinkedOut needs an update.
     * [selectorSetVersion] says which set failed, since the profile page and
     * the post page have separate ones.
     */
    data class ParseFailure(
        val host: String,
        val selectorSetVersion: Int,
        val snippet: String?
    ) : AppError {
        override val blame = Blame.APP
        override val retryable = false
    }

    /** Local storage failed. */
    data class StorageFailure(val detail: String?) : AppError {
        override val blame = Blame.APP
        override val retryable = true
    }

    /** Genuinely unclassified. Should stay empty in practice. */
    data class Unknown(val detail: String?) : AppError {
        override val blame = Blame.APP
        override val retryable = true
    }
}

enum class Blame { DEVICE, NETWORK, HOST, UPSTREAM, APP }

/**
 * What kind of check a host put up. The first two can be passed by a real
 * browser engine. The last cannot, it refuses the client before any check.
 */
enum class ChallengeKind { PROOF_OF_WORK, JS_INTERSTITIAL, WAF_BLOCK }
