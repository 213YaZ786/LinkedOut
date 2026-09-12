package com.linkedout.app.core.model

/**
 * One canonical form for post ids: the numeric activity id, for example "123".
 *
 * Every source must agree on this, because deduplication is by id. The same
 * post reaches the app as a /posts/<author>_<words>-activity-<id>-<code> link
 * from a profile, as urn:li:activity:<id> from a share button, and as a bare
 * id from the cache, so without one canonical form it would appear three
 * times in a merged timeline.
 *
 * Anything that does not contain a recognisable activity id is returned
 * unchanged, so an unexpected format degrades to "no deduplication" rather
 * than to two different posts collapsing into one.
 */
object PostId {

    fun normalize(raw: String): String {
        if (raw.isNotEmpty() && raw.all(Char::isDigit)) return raw
        val path = raw.substringBefore('#').substringBefore('?').trimEnd('/')

        // urn:li:activity:123 and urn:li:ugcPost:123
        if (path.startsWith("urn:")) {
            val tail = path.substringAfterLast(':')
            return if (tail.isNotEmpty() && tail.all(Char::isDigit)) tail else raw
        }

        // .../posts/<author>_<words>-activity-<id>-<code>
        val slug = path.substringAfterLast('/')
        for (marker in listOf("-activity-", "-ugcPost-", "-ugcpost-")) {
            val at = slug.lastIndexOf(marker)
            if (at < 0) continue
            val candidate = slug.substring(at + marker.length).substringBefore('-')
            if (candidate.isNotEmpty() && candidate.all(Char::isDigit)) return candidate
        }
        return raw
    }
}
