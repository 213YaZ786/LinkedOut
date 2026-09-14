package com.linkedout.app.core.web

/**
 * The cookies LinkedIn hands an anonymous visitor, kept and presented back.
 *
 * A browser passes where this app was refused, and the difference is not the
 * Referer. On the very first visit LinkedIn sets `bcookie` and `lidc` on any
 * client, and a request carrying them is a visitor it has seen before. A
 * request without them is a stranger, and some targets answer a stranger with
 * 999. That is the refusal the log kept showing.
 *
 * These are not an account. Nothing here names the reader, nothing is sent to
 * anyone but linkedin.com, and no other host gets a cookie from this app at
 * all. But a guest cookie is a resumption identifier: it does follow one
 * request to the next, and it is persisted so a restart does not start cold.
 * So it is stated in Settings and it can be erased in one tap.
 *
 * Deliberately not Android and not okhttp, so every rule below runs in a
 * harness against real Set-Cookie values. [GuestCookieJar] does the bridging.
 */
class GuestCookies(private val storage: Storage = Storage.None) {

    /** Where the persistent cookies are kept between runs. */
    interface Storage {
        fun read(): String
        fun write(text: String)

        object None : Storage {
            override fun read(): String = ""
            override fun write(text: String) = Unit
        }
    }

    private val lock = Any()
    private val jar = LinkedHashMap<String, GuestCookie>()

    @Volatile
    private var warmedAtMillis: Long = 0L

    /** What the last store kept and dropped, for the log line. */
    class Kept(val kept: List<String>, val dropped: List<String>) {
        override fun toString(): String {
            val keptPart = if (kept.isEmpty()) "nothing kept" else "kept ${kept.joinToString(", ")}"
            val droppedPart = if (dropped.isEmpty()) "" else ", dropped ${dropped.size} advertising"
            return keptPart + droppedPart
        }
    }

    init {
        val now = System.currentTimeMillis()
        decode(storage.read(), now).forEach { jar[it.key()] = it }
    }

    /**
     * Stores what a response set. Returns the names kept and how many were
     * refused, because the log should be able to say which cookie was present
     * when a request still came back 999.
     */
    fun put(cookies: List<GuestCookie>, nowMillis: Long = System.currentTimeMillis()): Kept {
        val kept = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        synchronized(lock) {
            for (cookie in cookies) {
                if (cookie.name.isBlank()) continue
                if (isAdvertising(cookie.name)) {
                    dropped += cookie.name
                    continue
                }
                if (cookie.expiresAtMillis in 1 until nowMillis || cookie.value.isBlank()) {
                    jar.remove(cookie.key())
                    continue
                }
                jar[cookie.key()] = cookie
                kept += cookie.name
            }
            purge(nowMillis)
            save()
        }
        return Kept(kept, dropped)
    }

    /** Everything that should ride on a request to this address. */
    fun matching(
        host: String,
        path: String = "/",
        nowMillis: Long = System.currentTimeMillis()
    ): List<GuestCookie> = synchronized(lock) {
        purge(nowMillis)
        jar.values.filter { it.matches(host, path) }
    }

    /** The names only, for a log line. */
    fun names(host: String, nowMillis: Long = System.currentTimeMillis()): List<String> =
        matching(host, "/", nowMillis).map { it.name }.sorted()

    /**
     * True when this host has already given us the cookies a returning
     * visitor carries. Only the session markers count: `lang` alone is set by
     * anything and would make a cold client look warm.
     */
    fun isWarm(host: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        matching(host, "/", nowMillis).any { it.name in SESSION_MARKERS }

    fun markWarmed(nowMillis: Long = System.currentTimeMillis()) {
        warmedAtMillis = nowMillis
    }

    /** Null when never warmed this run. */
    fun warmedMillisAgo(nowMillis: Long = System.currentTimeMillis()): Long? =
        if (warmedAtMillis == 0L) null else nowMillis - warmedAtMillis

    /** Forgets everything, on disk too. The next request starts as a stranger. */
    fun clear() {
        synchronized(lock) {
            jar.clear()
            warmedAtMillis = 0L
            save()
        }
    }

    fun count(nowMillis: Long = System.currentTimeMillis()): Int =
        synchronized(lock) {
            purge(nowMillis)
            jar.size
        }

    // ---- storage -----------------------------------------------------------

    /**
     * Only cookies with an expiry are written down, which is what a browser
     * does: a session cookie belongs to the run that received it. Tab
     * separated rather than JSON so this class needs no serializer and can be
     * exercised outside Android.
     */
    fun encode(): String = synchronized(lock) {
        jar.values.filter { it.expiresAtMillis > 0 }.joinToString("\n") { cookie ->
            listOf(
                escape(cookie.name),
                escape(cookie.value),
                cookie.domain,
                cookie.path,
                cookie.expiresAtMillis.toString(),
                if (cookie.secure) "1" else "0",
                if (cookie.hostOnly) "1" else "0"
            ).joinToString("\t")
        }
    }

    private fun save() {
        runCatching { storage.write(encode()) }
    }

    private fun purge(nowMillis: Long) {
        val stale = jar.entries.filter { it.value.expiresAtMillis in 1 until nowMillis }
        stale.forEach { jar.remove(it.key) }
    }

    private fun isAdvertising(name: String): Boolean =
        ADVERTISING.any { name.equals(it, ignoreCase = true) || name.startsWith(it) }

    companion object {

        /**
         * Set on the first visit by LinkedIn itself, and what a returning
         * visitor is recognised by. `bcookie` is the browser identifier,
         * `lidc` the datacentre hint, `JSESSIONID` the guest session.
         */
        val SESSION_MARKERS = setOf("bcookie", "bscookie", "lidc", "JSESSIONID", "li_gc")

        /**
         * Refused even though LinkedIn offers them. These are the advertising
         * and audience matching identifiers, and none of them is needed to be
         * served a page. Dropping them is the part of the promise that can be
         * kept while still holding a session.
         */
        val ADVERTISING = listOf(
            "UserMatchHistory",
            "AnalyticsSyncHistory",
            "li_sugr",
            "lms_ads",
            "lms_analytics",
            "li_fat_id",
            "li_giant",
            "aam_uuid",
            "_gcl_au",
            "_ga",
            "_gid",
            "_gat",
            "_fbp"
        )

        /**
         * Reads the one line a browser hands out for an address, the same
         * shape a `Cookie:` header carries, into records this jar can hold.
         *
         * That line says nothing about domains, paths or expiry, so the
         * caller names the domain and everything is kept as a session cookie.
         * That is the honest reading rather than a cautious one: a value
         * copied out of the engine is only known to be good now, and holding
         * it for the run means it is never written to disk.
         */
        fun fromHeader(text: String, domain: String): List<GuestCookie> =
            text.split(';').mapNotNull { part ->
                val pair = part.trim()
                if (pair.isEmpty()) return@mapNotNull null
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isEmpty() || value.isEmpty()) return@mapNotNull null
                GuestCookie(name = name, value = value, domain = domain)
            }

        /**
         * A tab or a newline inside a value would split the line it is written
         * on and the cookie would come back as something else, or not at all.
         * No LinkedIn cookie carries one today, which is exactly why it would
         * never be noticed until one did.
         */
        private fun escape(text: String): String = text
            .replace("\\", "\\\\")
            .replace("\t", "\\t")
            .replace("\n", "\\n")

        private fun unescape(text: String): String = buildString {
            var index = 0
            while (index < text.length) {
                val char = text[index]
                if (char != '\\' || index == text.lastIndex) {
                    append(char)
                    index++
                    continue
                }
                when (val next = text[index + 1]) {
                    't' -> append('\t')
                    'n' -> append('\n')
                    '\\' -> append('\\')
                    else -> { append(char); append(next) }
                }
                index += 2
            }
        }

        fun decode(text: String, nowMillis: Long = System.currentTimeMillis()): List<GuestCookie> =
            text.lineSequence().mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 7) return@mapNotNull null
                val expires = parts[4].toLongOrNull() ?: return@mapNotNull null
                if (expires in 1 until nowMillis) return@mapNotNull null
                GuestCookie(
                    name = unescape(parts[0]),
                    value = unescape(parts[1]),
                    domain = parts[2],
                    path = parts[3],
                    expiresAtMillis = expires,
                    secure = parts[5] == "1",
                    hostOnly = parts[6] == "1"
                ).takeIf { it.name.isNotBlank() }
            }.toList()
    }
}

/**
 * One cookie, reduced to what deciding to send it needs.
 *
 * [expiresAtMillis] is 0 for a session cookie, which is held for this run and
 * never written to disk.
 */
data class GuestCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val expiresAtMillis: Long = 0L,
    val secure: Boolean = true,
    val hostOnly: Boolean = false
) {

    fun key(): String = "$name|$domain|$path"

    /**
     * The two rules a cookie is sent under. A host only cookie goes to exactly
     * the host that set it, otherwise the domain is a suffix, so a cookie set
     * on `.linkedin.com` rides on `www.linkedin.com`. The path must be a
     * prefix at a segment boundary, so `/in` does not match `/india`.
     */
    fun matches(host: String, path: String): Boolean {
        val target = host.lowercase().removePrefix(".")
        val own = domain.lowercase().removePrefix(".")
        val domainOk = if (hostOnly) target == own else target == own || target.endsWith(".$own")
        if (!domainOk) return false
        if (this.path == "/" || this.path.isEmpty()) return true
        if (path == this.path) return true
        val prefix = if (this.path.endsWith("/")) this.path else this.path + "/"
        return path.startsWith(prefix)
    }
}
