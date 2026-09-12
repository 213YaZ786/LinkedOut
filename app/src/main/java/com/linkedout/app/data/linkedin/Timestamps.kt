package com.linkedout.app.data.linkedin

/**
 * Turning LinkedIn's two notions of time into one.
 *
 * The JSON-LD graph gives a real instant. The rendered cards give "1w", "3d",
 * "23h", and for anything older than a year "1y". A rounded age cannot be made
 * exact, so [fromRelative] does not pretend: it returns the start of the period
 * the label describes, which keeps posts in the right order relative to each
 * other and puts them in roughly the right place in a merged timeline. The
 * graph's exact value replaces it wherever the graph covers the post.
 */
internal object Timestamps {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** ISO 8601 as LinkedIn writes it, for example 2026-09-04T14:02:11.000Z. */
    fun parseIso(raw: String): Long? = runCatching {
        java.time.Instant.parse(raw.trim()).toEpochMilli()
    }.getOrElse {
        runCatching {
            java.time.OffsetDateTime.parse(raw.trim()).toInstant().toEpochMilli()
        }.getOrNull()
    }

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

    // "mo" before "m", or every month is read as a minute.
    private val PATTERN = Regex("""(\d+)\s*(mo|[smhdwy])\b""")
}
