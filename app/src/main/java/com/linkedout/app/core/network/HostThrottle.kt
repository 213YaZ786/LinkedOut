package com.linkedout.app.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Paces every request LinkedOut makes, per host.
 *
 * LinkedIn rate limits readers with no account hard, and a guest has no way to
 * ask for more. Evidence from MTGA's request log, where the shape of the
 * failure was the same: one page succeeded, the next three seconds later
 * returned 429, and the retry loop then made it worse. The scarce resource is
 * not bandwidth, it is the host's patience.
 *
 * Two rules. A minimum gap between consecutive requests to the same host, and a
 * cooldown whenever that host refuses, honouring Retry-After when it sends one.
 *
 * Without Retry-After the cooldown grows with the refusals in a row instead of
 * starting at a minute. Measured on 2026-10-01: a lone 429 was followed four
 * seconds later by a clean page, and a profile refused with 999 was followed at
 * once by another profile served, so one refusal is about one address and not
 * about the host. A flat minute on the first one failed every other account of
 * the same refresh. A second refusal before any page gets through is the host
 * itself, and that one still waits a minute, then five.
 */
class HostThrottle {

    private val mutex = Mutex()
    private val lastRequestAt = mutableMapOf<String, Long>()
    private val cooldownUntil = mutableMapOf<String, Long>()
    private val strikes = mutableMapOf<String, Strike>()

    /**
     * Suspends until this host may be called again. Returns false when the host
     * is in a cooldown longer than the caller should reasonably wait, so the
     * caller can fail over instead of blocking the UI.
     */
    suspend fun acquire(host: String): Boolean {
        val waitFor: Long
        mutex.withLock {
            val now = System.currentTimeMillis()

            val cooldown = cooldownUntil[host] ?: 0L
            if (now < cooldown) {
                val remaining = cooldown - now
                if (remaining > MAX_INLINE_WAIT_MS) return false
                lastRequestAt[host] = cooldown
                waitFor = remaining
            } else {
                val elapsed = now - (lastRequestAt[host] ?: 0L)
                waitFor = (MIN_INTERVAL_MS - elapsed).coerceAtLeast(0L)
                lastRequestAt[host] = now + waitFor
            }
        }

        if (waitFor > 0) delay(waitFor)
        return true
    }

    /** Called when a host refuses, with 429 or LinkedIn's 999. */
    fun penalise(host: String, retryAfterSeconds: Long?) {
        synchronized(cooldownUntil) {
            val now = System.currentTimeMillis()
            // A refusal long after the last one starts the count again, so a
            // bad minute this morning does not cost a minute this evening.
            val before = strikes[host]?.takeIf { now - it.atMillis < STRIKE_MEMORY_MS }?.count ?: 0
            val count = before + 1
            strikes[host] = Strike(count, now)
            val cooldown = retryAfterSeconds?.times(1_000L)
                ?: STRIKE_COOLDOWNS_MS[(count - 1).coerceAtMost(STRIKE_COOLDOWNS_MS.lastIndex)]
            cooldownUntil[host] = now + cooldown.coerceAtMost(MAX_COOLDOWN_MS)
        }
    }

    /**
     * Called after a clean page, so a recovered host is not punished forever
     * and the next refusal counts as a first one again.
     */
    fun clear(host: String) {
        synchronized(cooldownUntil) {
            cooldownUntil.remove(host)
            strikes.remove(host)
        }
    }

    fun cooldownRemainingMs(host: String): Long =
        ((cooldownUntil[host] ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    private companion object {
        /** Roughly one request per second, the pace a person reading would set. */
        const val MIN_INTERVAL_MS = 1_100L
        const val MAX_COOLDOWN_MS = 15 * 60_000L

        /**
         * The cooldown for the first, second, third and later refusals in a
         * row. The first is short enough to be waited out inline, so the rest
         * of a refresh goes on after it instead of failing.
         */
        val STRIKE_COOLDOWNS_MS = listOf(4_000L, 60_000L, 5 * 60_000L, 15 * 60_000L)

        /** How long a refusal still counts towards the next one. */
        const val STRIKE_MEMORY_MS = 10 * 60_000L

        /**
         * Beyond this, fail over rather than make the reader wait. Above the
         * first strike on purpose, see [STRIKE_COOLDOWNS_MS].
         */
        const val MAX_INLINE_WAIT_MS = 5_000L
    }

    private class Strike(val count: Int, val atMillis: Long)
}
