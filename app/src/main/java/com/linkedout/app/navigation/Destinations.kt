package com.linkedout.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.linkedout.app.ui.icon.LinkedOutIcons
import com.linkedout.app.core.model.AccountKind

/** The tabs, in dock order. */
enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    TIMELINE("timeline", "Home", LinkedOutIcons.Home),
    ACCOUNTS("accounts", "Accounts", LinkedOutIcons.Person),
    SETTINGS("settings", "Settings", LinkedOutIcons.Settings)
}

/** Destinations pushed on top, not part of the bar. */
object Routes {
    /** The three tabs, hosted together in one pager. */
    const val MAIN = "main"
    const val FEED_PATTERN = "feed/{handle}?kind={kind}"
    const val DEBUG_LOG = "debuglog"
    const val SEARCH = "search"
    const val SAVED_MEDIA = "savedmedia"

    const val POST_PATTERN = "post/{id}?from={from}"

    /**
     * [kind] rides along because a name alone cannot say whether it is a
     * person or a company, and the screen has to know before it asks.
     */
    fun feed(handle: String, kind: AccountKind = AccountKind.PERSON): String =
        "feed/$handle?kind=${kind.name}"

    /** [from] is the account whose cache holds the post, a lookup hint. */
    fun post(id: String, from: String?): String =
        if (from.isNullOrBlank()) "post/$id" else "post/$id?from=$from"
}
