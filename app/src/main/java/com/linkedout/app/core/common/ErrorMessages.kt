package com.linkedout.app.core.common

/**
 * Turns an AppError into something a human reads, plus the action that
 * actually helps. Kept out of the UI so it can be unit tested and localised.
 *
 * Every text here assumes one host. There is no pool to fall back to and no
 * volunteer running a server, so nothing promises that LinkedOut will try
 * somewhere else: it cannot.
 */
data class ErrorPresentation(
    val headline: String,
    val explanation: String,
    val action: ErrorAction
)

/**
 * Try again, or nothing to do but wait. An error with nothing to offer says
 * so in words rather than showing a button that leads nowhere.
 *
 * There used to be a third, a check to pass by hand. LinkedIn never asks the
 * reader for one: the wall it puts up is passed by the offscreen engine or
 * not at all, so the button only ever offered work that was not theirs.
 */
enum class ErrorAction { RETRY, NONE }

/**
 * A vanity name fit to print, or null. Blank when a post page hit the wall and
 * no profile was involved, and a host name when the mapper had nothing better
 * to pass, which is why the dot is tested.
 */
private fun named(handle: String): String? =
    handle.takeIf { it.isNotBlank() && !it.contains('.') }

fun AppError.present(): ErrorPresentation = when (this) {
    AppError.Offline -> ErrorPresentation(
        headline = "No internet connection",
        explanation = "Saved posts are still readable. New ones load as soon as you are back online.",
        action = ErrorAction.RETRY
    )

    is AppError.DnsFailure -> ErrorPresentation(
        headline = "Can't reach $host",
        explanation = "The name does not resolve from this network. A work or school " +
            "connection, or a filter on the phone, can do that.",
        action = ErrorAction.NONE
    )

    is AppError.TlsFailure -> ErrorPresentation(
        headline = "Connection to $host is not secure",
        explanation = "The connection could not be verified, so LinkedOut stopped rather than take a risk. " +
            "This can happen on public or work Wi-Fi.",
        action = ErrorAction.NONE
    )

    is AppError.Timeout -> ErrorPresentation(
        headline = "$host is too slow",
        explanation = "It did not answer within ${millis / 1000} seconds. Try again in a moment.",
        action = ErrorAction.RETRY
    )

    is AppError.ClientRefused -> if (status == 999) {
        // LinkedIn's denial code. It is aimed at the address you are reading
        // from, not at this profile, so there is no spelling to check and no
        // setting to change. Asking again is what makes it last longer, which
        // is why this offers nothing to tap.
        ErrorPresentation(
            headline = "LinkedIn is refusing requests",
            explanation = "It answers this way when a reader with no account asks for too much. " +
                "It clears by itself, usually within a few minutes. LinkedOut has stopped asking " +
                "in the meantime, and asking again would only make it last longer.",
            action = ErrorAction.NONE
        )
    } else {
        ErrorPresentation(
            headline = "$host turned LinkedOut away",
            explanation = "LinkedIn refused the request, which it does to readers with no account. " +
                "Waiting a while is the only thing that helps.",
            action = ErrorAction.NONE
        )
    }

    is AppError.ChallengeRequired -> when (kind) {
        ChallengeKind.WAF_BLOCK -> ErrorPresentation(
            headline = "$host is blocking LinkedOut",
            explanation = "The request was stopped before the page. There is nothing to do on your " +
                "side except try later.",
            action = ErrorAction.NONE
        )
        else -> ErrorPresentation(
            headline = "$host put up its sign in wall",
            explanation = "LinkedOut reads the page behind it on its own and does not always get " +
                "through on the first try. Trying again often works.",
            action = ErrorAction.RETRY
        )
    }

    is AppError.RateLimited -> ErrorPresentation(
        headline = "Too many requests",
        // No automatic retry exists, so this must not promise one. What is
        // true is that the throttle refuses to send anything until the
        // cooldown is over.
        explanation = retryAfterSeconds
            ?.let { "$host asked LinkedOut to wait $it seconds. Nothing is sent until then." }
            ?: "$host asked LinkedOut to slow down. Nothing is sent until it lifts.",
        action = ErrorAction.NONE
    )

    is AppError.ServerError -> ErrorPresentation(
        headline = "$host has a problem",
        explanation = "LinkedIn itself failed, not your phone or your connection. Try again later.",
        action = ErrorAction.RETRY
    )

    is AppError.AccountNotFound -> ErrorPresentation(
        // The mapper falls back to the host when a 404 arrives with no handle
        // in hand, and a host has a dot in it, so "No profile at
        // in/www.linkedin.com" is what a naive template would print.
        headline = named(handle)?.let { "No profile at in/$it" } ?: "That page does not exist",
        explanation = "Check the address. The part after /in/ has to match exactly, hyphens and " +
            "trailing characters included. The profile may also have been renamed or deleted.",
        action = ErrorAction.NONE
    )

    is AppError.AccountUnavailable -> ErrorPresentation(
        headline = named(handle)?.let { "Can't show in/$it" } ?: "Can't show this page",
        explanation = reason ?: "This profile shows nothing to a reader without an account.",
        action = ErrorAction.NONE
    )

    is AppError.PostUnavailable -> ErrorPresentation(
        headline = "This post can't be shown",
        explanation = reason?.let { "$host says: $it" }
            ?: "It may have been deleted, or its author may have made the profile private.",
        action = ErrorAction.NONE
    )

    is AppError.ParseFailure -> ErrorPresentation(
        headline = "This page can't be read",
        explanation = "$host changed its layout and LinkedOut can't read it yet, with selector " +
            "set $selectorSetVersion. An app update will fix it.",
        action = ErrorAction.NONE
    )

    is AppError.StorageFailure -> ErrorPresentation(
        headline = "Couldn't save on this phone",
        explanation = "LinkedOut could not read or write its saved posts. Check that the phone has free space.",
        action = ErrorAction.RETRY
    )

    is AppError.Unknown -> ErrorPresentation(
        headline = "Something went wrong",
        explanation = "LinkedOut ran into something unexpected. The Activity log in Settings has details you can share.",
        action = ErrorAction.NONE
    )
}
