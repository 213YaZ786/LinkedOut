package com.linkedout.app.data.linkedin

/**
 * Turning LinkedIn's notions of time into one instant, the most exact first.
 *
 * First the post's own id: LinkedIn builds an activity id from its creation
 * time, the first 41 bits of the 63-bit id being the Unix time in milliseconds,
 * so [fromActivityId] reads the exact instant from the id alone. A comment's id
 * is built the same way. Then the JSON-LD graph's real instant, [parseIso],
 * where the graph covers the post. The rounded label comes last: the rendered
 * cards give "1w", "3d", "23h", and for anything older than a year "1y". A
 * rounded age cannot be made exact, so [fromRelative] does not pretend: it
 * returns the start of the period the label describes, which keeps posts in the
 * right order relative to each other and puts them in roughly the right place
 * in a merged timeline.
 */
internal object Timestamps {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    // https://github.com/Ollie-Boyd/Linkedin-post-timestamp-extractor/blob/main/extractor.py#get_date_from_linkedin_activity
    // not ported: format_timestamp (the formatting; the app keeps the instant), urllib.parse.unquote
    // (the ids reach us from decoded attributes)
    fun get_date_from_linkedin_activity(post_url: String?): Long? {
        val post_url_unquote = post_url ?: return null
        val match = ACTIVITY.find(post_url_unquote)
        val linkedin_id = match?.groupValues?.get(1) ?: return null

        // Extract the first 41 bits directly
        val timestamp_ms = extractUnixTimestamp(linkedin_id) ?: return null
        if (timestamp_ms < 1_041_379_200_000L || timestamp_ms > System.currentTimeMillis() + DAY) return null
        return timestamp_ms
    }

    // https://github.com/Ollie-Boyd/Linkedin-post-timestamp-extractor/blob/main/extractor.py#format_timestamp
    // not ported: format_timestamp (the formatting; the app keeps the instant). The two functions of
    // extractor.py are ported above and below, this one only tries the activity's, then the comment's.
    // The instant of an id (the number alone, urn:li:activity:<id>, ugcPost, share, the profile card's
    // <author>_activity-<id>-<code>), or of a comment's id; null when none, or when the instant is
    // before 2003 or more than a day ahead.
    fun fromActivityId(post_url: String?): Long? =
        get_date_from_linkedin_activity(post_url) ?: get_date_from_linkedin_comment(post_url)

    // https://github.com/Ollie-Boyd/Linkedin-post-timestamp-extractor/blob/main/extractor.py#get_date_from_linkedin_comment
    // not ported: format_timestamp (the formatting; the app keeps the instant), urllib.parse.unquote
    // (the ids reach us from decoded attributes)
    fun get_date_from_linkedin_comment(post_url: String?): Long? {
        val post_url_unquote = post_url ?: return null
        val match = COMMENT.find(post_url_unquote)
        val linkedin_id = match?.groupValues?.get(1) ?: return null

        // Extract the first 41 bits directly
        val timestamp_ms = extractUnixTimestamp(linkedin_id) ?: return null
        if (timestamp_ms < 1_041_379_200_000L || timestamp_ms > System.currentTimeMillis() + DAY) return null
        return timestamp_ms
    }

    /** ISO 8601 as LinkedIn writes it, for example 2026-09-04T14:02:11.000Z. */
    fun parseIso(raw: String): Long? = runCatching {
        java.time.Instant.parse(raw.trim()).toEpochMilli()
    }.getOrElse {
        runCatching {
            java.time.OffsetDateTime.parse(raw.trim()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    // The id alone, or after activity:, ugcPost:, share: or activity- (the profile card's slug form).
    private val ACTIVITY = Regex("""(?:(?:activity|ugcPost|share)[:\-]|^)(\d{19})(?!\d)""")

    // A comment's id alone, or the first number of fsd_comment:(<id>,urn:li:activity:...).
    private val COMMENT = Regex("""(?:fsd_comment:\(|^)(\d{19})(?:,|$)""")

    /**
     * "2w" and friends, relative to [now]. An unreadable or missing label
     * yields [now], which sorts the post to the top of a profile, where the
     * page itself put it.
     */
    fun fromRelative(label: String?, now: Long = System.currentTimeMillis()): Long {
        val cleaned = label?.trim()?.lowercase() ?: return now
        val match = PATTERN.find(cleaned) ?: return now
        val amount = match.groupValues[1].toLongOrNull() ?: return now
        val span = when (match.groupValues[2]) {
            "mo" -> 30 * DAY
            "m" -> MINUTE
            "s" -> 1_000L
            "h" -> HOUR
            "d" -> DAY
            "w" -> 7 * DAY
            "y" -> 365 * DAY
            else -> return now
        }
        return now - amount * span
    }

    // https://github.com/Ollie-Boyd/Linkedin-post-timestamp-extractor/blob/main/extractor.js#extractUnixTimestamp
    // not ported: getPostId, getCommentId's URL parsing, unixTimestampToHumanDate, unixTimestampToLocalDate,
    // getDate, clearUrlField (the browser page's UI; the app keeps the instant).
    private fun extractUnixTimestamp(postId: String): Long? {
        val asBinary = java.math.BigInteger(postId).toString(2)
        val first41Chars = asBinary.take(41)
        val timestamp = first41Chars.toLongOrNull(2)
        return timestamp
    }

    // "mo" before "m", or every month is read as a minute.
    private val PATTERN = Regex("""(\d+)\s*(mo|[smhdwy])\b""")
}
