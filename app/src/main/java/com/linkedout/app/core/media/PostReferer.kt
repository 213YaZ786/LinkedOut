package com.linkedout.app.core.media

import com.linkedout.app.core.link.LinkCleaner
import com.linkedout.app.core.link.SafeLinks
import java.net.URI

/**
 * The clean address of the post a save came from, the one [SaveOrigin] writes
 * into the file's media store row (REFERER_URI, as Chrome's downloads do).
 *
 * Only a clean https address on LinkedIn's own hosts (linkedin.com and its
 * subdomains, as the manifest's VIEW intent filter) is kept: the permalink
 * comes from a post and may name any server. Pure, no Android.
 */
object PostReferer {
    private const val OWN_HOST = "linkedin.com"

    /** A permalink longer than this is not a post address: none kept. */
    internal const val MAX_LENGTH = 2048

    /** The Referer to send for a save of the post at [permalink], or null for none. */
    fun forPermalink(permalink: String?): String? {
        val url = SafeLinks.openable(permalink) ?: return null
        if (url.length > MAX_LENGTH || !url.startsWith("https://")) return null
        // Tracking parameters off, so only the clean post address is kept. The
        // cleaned address is the one checked: a redirect wrapper is unwrapped
        // by the cleaner and its target must be ours too.
        val clean = SafeLinks.openable(LinkCleaner.clean(url).substringBefore('#')) ?: return null
        if (clean.length > MAX_LENGTH || !clean.startsWith("https://")) return null
        val uri = runCatching { URI(clean) }.getOrNull() ?: return null
        if (uri.userInfo != null) return null
        val host = uri.host?.lowercase() ?: return null
        return clean.takeIf { host == OWN_HOST || host.endsWith(".$OWN_HOST") }
    }
}
