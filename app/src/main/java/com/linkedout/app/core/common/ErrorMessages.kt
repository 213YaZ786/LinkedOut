package com.linkedout.app.core.common

/**
 * Turns an AppError into something a human reads, plus the action that
 * actually helps. Kept out of the UI so it can be unit tested and localised.
 */
data class ErrorPresentation(
    val headline: String,
    val explanation: String,
    val action: ErrorAction
)

enum class ErrorAction { RETRY, OPEN_DIAGNOSTICS, CHANGE_INSTANCE, OPEN_FALLBACK_VIEWER, NONE }

fun AppError.present(): ErrorPresentation = when (this) {
    AppError.Offline -> ErrorPresentation(
        headline = "No internet connection",
        explanation = "Saved posts are still readable. New ones load as soon as you are back online.",
        action = ErrorAction.RETRY
    )

    is AppError.DnsFailure -> ErrorPresentation(
        headline = "Can't reach $host",
        explanation = "This server can't be found from your network right now. LinkedOut uses the other servers meanwhile.",
        action = ErrorAction.CHANGE_INSTANCE
    )

    is AppError.TlsFailure -> ErrorPresentation(
        headline = "Connection to $host is not secure",
        explanation = "The connection could not be verified, so LinkedOut stopped rather than take a risk. " +
            "This can happen on public or work Wi-Fi.",
        action = ErrorAction.OPEN_DIAGNOSTICS
    )

    is AppError.Timeout -> ErrorPresentation(
        headline = "$host is too slow",
        explanation = "It did not answer within ${millis / 1000} seconds, probably because it is busy. Try again in a moment.",
        action = ErrorAction.RETRY
    )

    is AppError.ClientRefused -> ErrorPresentation(
        headline = "$host turned LinkedOut away",
        explanation = "This server is refusing requests right now. LinkedOut uses the other servers meanwhile.",
        action = ErrorAction.CHANGE_INSTANCE
    )

    is AppError.ChallengeRequired -> when (kind) {
        ChallengeKind.WAF_BLOCK -> ErrorPresentation(
            headline = "$host is blocking LinkedOut",
            explanation = "There is nothing to do on your side. LinkedOut uses the other servers meanwhile.",
            action = ErrorAction.CHANGE_INSTANCE
        )
        else -> ErrorPresentation(
            headline = "$host asks for a quick check",
            explanation = "It wants to make sure a real person is reading. Do it once and LinkedOut remembers it.",
            action = ErrorAction.OPEN_FALLBACK_VIEWER
        )
    }

    is AppError.RateLimited -> ErrorPresentation(
        headline = "Too many requests",
        explanation = retryAfterSeconds
            ?.let { "$host asked LinkedOut to wait $it seconds. It will try again on its own." }
            ?: "$host asked LinkedOut to slow down. It will try again on its own.",
        action = ErrorAction.RETRY
    )

    is AppError.InstanceError -> ErrorPresentation(
        headline = "$host has a problem",
        explanation = "The server itself failed, not your phone or your connection. Try again later.",
        action = ErrorAction.RETRY
    )

    is AppError.NoHealthyInstance -> ErrorPresentation(
        headline = "No server available right now",
        explanation = "LinkedOut tried ${tried.size} server(s) and none answered. They are run by volunteers " +
            "and sometimes go offline. Saved posts are still readable.",
        action = ErrorAction.OPEN_DIAGNOSTICS
    )

    is AppError.AccountNotFound -> ErrorPresentation(
        headline = "@$handle doesn't exist",
        explanation = "Check the spelling. The account may also have been renamed or deleted.",
        action = ErrorAction.NONE
    )

    is AppError.AccountUnavailable -> ErrorPresentation(
        headline = "Can't show @$handle",
        explanation = reason ?: "This account is private or suspended, so its posts are not public.",
        action = ErrorAction.NONE
    )

    is AppError.PostUnavailable -> ErrorPresentation(
        headline = "This post can't be shown",
        explanation = reason?.let { "$host says: $it" }
            ?: "It may have been deleted, or its account made private or suspended.",
        action = ErrorAction.NONE
    )

    is AppError.AuthorUnknown -> ErrorPresentation(
        headline = "This link doesn't name the author",
        explanation = if (askedX) {
            "It only carries the post number, and the servers LinkedOut reads need the author's name. " +
                "X was asked for it but did not answer. Try again later, or open the post on X."
        } else {
            "It only carries the post number, and the servers LinkedOut reads need the author's name. " +
                "Turn on \"Newest posts from X\" in Settings, Reading, so X can name the author, " +
                "or open the post on X."
        },
        action = if (askedX) ErrorAction.RETRY else ErrorAction.NONE
    )

    is AppError.ParseFailure -> ErrorPresentation(
        headline = "This page can't be read",
        explanation = "$host changed its layout and LinkedOut can't read it yet. An app update will fix it. " +
            "Other servers may still work.",
        action = ErrorAction.OPEN_DIAGNOSTICS
    )

    is AppError.FeedGated -> ErrorPresentation(
        headline = "$host limits access",
        explanation = "Its fast feed is reserved for approved apps. LinkedOut reads its normal pages instead, " +
            "so there is nothing to do.",
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
        action = ErrorAction.OPEN_DIAGNOSTICS
    )
}
