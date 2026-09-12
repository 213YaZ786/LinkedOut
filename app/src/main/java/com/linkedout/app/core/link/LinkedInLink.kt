package com.linkedout.app.core.link

/**
 * What a LinkedIn link points at, when LinkedOut can show it. Pure, no Android.
 *
 * LinkedIn serves the same pages from a country subdomain as from www, so
 * fr.linkedin.com/in/x and www.linkedin.com/in/x are the same profile. The
 * subdomain is dropped here rather than carried, since the app always asks
 * www and lets LinkedIn redirect if it wants to.
 *
 * Anything not a profile, a company or a post is left to the browser by
 * returning null. Jobs, learning, groups and events have no guest markup
 * worth parsing.
 */
sealed interface LinkedInLink {

    /** A person. [handle] is the vanity name, the part after /in/. */
    data class Profile(val handle: String) : LinkedInLink

    /** An organisation. [slug] is the part after /company/. */
    data class Company(val slug: String) : LinkedInLink

    /**
     * One post. [id] is the numeric activity id, which is the only part that
     * identifies it. [handle] is the author when the URL carries it, which
     * /posts/ links do and /feed/update/ links do not. [permalink] is kept
     * whole because the long /posts/ form is what LinkedIn answers fastest,
     * and rebuilding it from the id alone is not possible.
     */
    data class Post(
        val handle: String?,
        val id: String,
        val permalink: String
    ) : LinkedInLink

    companion object {

        const val HOST = "www.linkedin.com"

        fun parse(url: String): LinkedInLink? {
            val trimmed = url.trim()
            val scheme = trimmed.substringBefore("://", "").lowercase()
            if (scheme != "https" && scheme != "http") return null

            val rest = trimmed.substringAfter("://")
            val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
                .substringBefore(':').lowercase()
            if (!isLinkedInHost(host)) return null

            val path = rest.substringAfter('/', "").substringBefore('?').substringBefore('#')
            val segments = path.split('/').filter { it.isNotEmpty() }
            if (segments.isEmpty()) return null

            return when (segments[0].lowercase()) {
                "in" -> segments.getOrNull(1)
                    ?.let(::decode)
                    ?.takeIf(::isVanity)
                    ?.let(::Profile)

                "company", "school", "showcase" -> segments.getOrNull(1)
                    ?.let(::decode)
                    ?.takeIf(::isVanity)
                    ?.let(::Company)

                // /posts/<author>_<words>-activity-<id>-<code>
                "posts" -> segments.getOrNull(1)?.let { slug ->
                    val id = activityIdIn(slug) ?: return@let null
                    val handle = slug.substringBefore('_').takeIf(::isVanity)
                    Post(handle, id, canonical(path))
                }

                // /feed/update/urn:li:activity:<id>
                "feed" -> segments.getOrNull(2)?.let { urn ->
                    val id = urn.substringAfterLast(':').takeIf(::isId) ?: return@let null
                    Post(null, id, canonical("feed/update/urn:li:activity:$id"))
                }

                else -> null
            }
        }

        /**
         * The activity id inside a /posts/ slug. The id sits between the last
         * "-activity-" and the trailing share code, and LinkedIn has used
         * "-ugcPost-" for the same place, so both are accepted.
         */
        fun activityIdIn(slug: String): String? {
            val marker = listOf("-activity-", "-ugcPost-", "-ugcpost-")
                .map { slug.lastIndexOf(it) to it }
                .filter { it.first >= 0 }
                .maxByOrNull { it.first }
                ?: return null
            val after = slug.substring(marker.first + marker.second.length)
            return after.substringBefore('-').takeIf(::isId)
        }

        /** The first http or https URL inside shared text, for the share sheet. */
        fun firstUrlIn(text: String): String? =
            URL.find(text)?.value?.trimEnd('.', ',', ')', '!', '?')

        /**
         * True for linkedin.com and every country subdomain of it. Written as a
         * suffix test rather than a list because LinkedIn has one per country
         * and a missing entry would silently send a good link to the browser.
         */
        fun isLinkedInHost(host: String): Boolean {
            val lower = host.lowercase()
            return lower == "linkedin.com" || lower.endsWith(".linkedin.com") ||
                lower == "lnkd.in"
        }

        /** Rebuilds an absolute URL on www, dropping any country subdomain. */
        fun canonical(path: String): String = "https://$HOST/${path.trimStart('/')}"

        fun profileUrl(handle: String): String = canonical("in/$handle")

        fun companyUrl(slug: String): String = canonical("company/$slug")

        private fun isId(value: String) = value.length in 1..25 && value.all(Char::isDigit)

        /**
         * Vanity names are lowercase letters, digits and hyphens. LinkedIn
         * appends a hash to duplicates, so they can be long, and older ones
         * can carry unicode, which percent decoding above turns back into
         * letters. Reserved first segments are handled by the caller matching
         * on /in/ and /company/ rather than by a deny list.
         */
        private fun isVanity(value: String) =
            value.length in 1..120 && value.none { it == '/' || it == '?' || it.isWhitespace() }

        private fun decode(segment: String): String =
            runCatching { java.net.URLDecoder.decode(segment, "UTF-8") }.getOrDefault(segment)

        private val URL = Regex("""https?://\S+""")
    }
}
