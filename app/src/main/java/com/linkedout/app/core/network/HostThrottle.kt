package com.linkedout.app.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Paces every request LinkedOut makes, per host.
 *
 * The instances that survive are small volunteer servers under legal pressure,
 * and they rate limit hard. Evidence from the request log: page one of a
 * profile succeeded, page two three seconds later returned 429, and the retry
 * loop then made it worse. The scarce resource is not bandwidth, it is the
 * host's patience.
 *
 * Two rules. A minimum gap between consecutive requests to the same host, and a
 * hard cooldown whenever that host answers 429, honouring Retry-After when it
 * sends one.
 */
class HostThrottle {

    private val mutex = Mutex()
    private val lastRequestAt = mutableMapOf<String, Long>()
    private val cooldownUntil = mutableMapOf<String, Long>()

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

    /** Called when a host answers 429. */
    fun penalise(host: String, retryAfterSeconds: Long?) {
        val cooldown = retryAfterSeconds?.times(1_000L) ?: DEFAULT_COOLDOWN_MS
        synchronized(cooldownUntil) {
            cooldownUntil[host] = System.currentTimeMillis() + cooldown.coerceAtMost(MAX_COOLDOWN_MS)
        }
    }

    /** Called after a clean response, so a recovered host is not punished forever. */
    fun clear(host: String) {
        synchronized(cooldownUntil) { cooldownUntil.remove(host) }
    }

    fun cooldownRemainingMs(host: String): Long =
        ((cooldownUntil[host] ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    private companion object {
        /** Roughly one request per second per host, which no instance objects to. */
        const val MIN_INTERVAL_MS = 1_100L
        const val DEFAULT_COOLDOWN_MS = 60_000L
        const val MAX_COOLDOWN_MS = 15 * 60_000L

        /** Beyond this, fail over rather than make the reader wait. */
        const val MAX_INLINE_WAIT_MS = 2_500L
    }
}
