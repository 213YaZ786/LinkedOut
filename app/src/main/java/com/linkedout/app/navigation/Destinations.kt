package com.linkedout.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.linkedout.app.ui.icon.LinkedOutIcons

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
    const val CONNECTION = "connection"
    const val FEED_PATTERN = "feed/{handle}"
    const val DEBUG_LOG = "debuglog"
    const val SEARCH = "search"

    const val POST_PATTERN = "post/{id}?from={from}"

    fun feed(handle: String): String = "feed/$handle"

    /** [from] is the account whose cache holds the post, a lookup hint. */
    fun post(id: String, from: String?): String =
        if (from.isNullOrBlank()) "post/$id" else "post/$id?from=$from"

    val diagnosticsIcon: ImageVector = LinkedOutIcons.Pulse
}
