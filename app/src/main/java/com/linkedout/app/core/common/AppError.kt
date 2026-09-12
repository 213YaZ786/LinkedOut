package com.linkedout.app.core.common

/**
 * Every failure the app can surface, named precisely.
 *
 * Design rule: no generic "something went wrong". Each case carries enough
 * context for the Diagnostics screen to explain why LinkedOut cannot show content,
 * and whether the user, the network, the instance or the app is at fault.
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

    /** Host name did not resolve. Instance gone, or DNS blocked upstream. */
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
        override val blame = Blame.INSTANCE
        override val retryable = true
    }

    // ---- instance side -----------------------------------------------------

    /** The instance actively refused our client, for example a bot challenge. */
    data class ClientRefused(val host: String, val status: Int) : AppError {
        override val blame = Blame.INSTANCE
        override val retryable = false
    }

    /**
     * The instance put a bot check in front of the page: a proof of work, a
     * JavaScript interstitial, or a firewall that refuses non browsers. The
     * instance itself is usually healthy, which is why this is not
     * [ClientRefused] and why the pool must not treat it as dead.
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
        override val blame = Blame.INSTANCE
        override val retryable = true
    }

    /** Rate limited. retryAfterSeconds comes from the Retry-After header. */
    data class RateLimited(val host: String, val retryAfterSeconds: Long?) : AppError {
        override val blame = Blame.INSTANCE
        override val retryable = true
    }

    /** The instance is up but broken, a 5xx. */
    data class InstanceError(val host: String, val status: Int) : AppError {
        override val blame = Blame.INSTANCE
        override val retryable = true
    }

    /** Every instance in the pool has been tried and none answered. */
    data class NoHealthyInstance(val tried: List<String>) : AppError {
        override val blame = Blame.INSTANCE
        override val retryable = true
    }

    // ---- upstream account side ---------------------------------------------

    /** Handle does not exist, was renamed, or the profile is gone. */
    data class AccountNotFound(val handle: String) : AppError {
        override val blame = Blame.UPSTREAM
        override val retryable = false
    }

    /** The account exists but X suspended or protected it. */
    data class AccountUnavailable(val handle: String, val reason: String?) : AppError {
        override val blame = Blame.UPSTREAM
        override val retryable = false
    }

    /**
     * The account is fine but this one post is not there: deleted, from a
     * suspended or protected account, or withheld. [reason] is the server's
     * own wording when it gave one, for example X's tombstone text.
     */
    data class PostUnavailable(val host: String, val reason: String?) : AppError {
        override val blame = Blame.UPSTREAM
        override val retryable = false
    }

    /**
     * A post link that carries only the number, x.com/i/web/status/<id>, and
     * nothing on the phone names its author. Nitter servers need the author
     * in the address. [askedX] says whether X was asked to name the author
     * ("Newest posts from X" on) and did not answer, or was not asked because
     * the reader keeps X out.
     */
    data class AuthorUnknown(val postId: String, val askedX: Boolean) : AppError {
        override val blame = Blame.UPSTREAM
        override val retryable get() = askedX
    }

    // ---- our side ----------------------------------------------------------

    /**
     * HTTP 200 arrived but the parser found nothing it recognised. This almost
     * always means the instance changed its markup and LinkedOut needs an update.
     */
    data class ParseFailure(
        val host: String,
        val selectorSetVersion: Int,
        val snippet: String?
    ) : AppError {
        override val blame = Blame.APP
        override val retryable = false
    }

    /**
     * The instance answered with well formed RSS whose only content is a notice
     * that this client is not on its allow list. xcancel does this. It is not a
     * network failure and not a parse failure, it is a door with a lock on it,
     * so it gets its own case and carries the ID the operator asks for.
     */
    data class FeedGated(val host: String, val requestId: String?) : AppError {
        override val blame = Blame.INSTANCE
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

enum class Blame { DEVICE, NETWORK, INSTANCE, UPSTREAM, APP }

/**
 * What kind of wall a host put up. The first two can be passed by a real
 * browser engine. The last cannot, it refuses the client before any check.
 */
enum class ChallengeKind { PROOF_OF_WORK, JS_INTERSTITIAL, WAF_BLOCK }
