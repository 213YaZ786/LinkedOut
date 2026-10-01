package com.linkedout.app.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cooldown after a refusal. Read through [HostThrottle.cooldownRemainingMs],
 * which counts down from the moment of the call, hence the ranges.
 */
class HostThrottleTest {

    private val host = "www.linkedin.com"

    @Test
    fun `a first refusal is short enough to wait out`() = runBlocking {
        val throttle = HostThrottle()
        throttle.penalise(host, null)

        assertTrue(throttle.cooldownRemainingMs(host) in 3_000L..4_000L)
        assertTrue(throttle.acquire(host))
    }

    @Test
    fun `refusals in a row lengthen the cooldown`() = runBlocking {
        val throttle = HostThrottle()
        throttle.penalise(host, null)
        throttle.penalise(host, null)

        assertTrue(throttle.cooldownRemainingMs(host) in 59_000L..60_000L)
        assertFalse(throttle.acquire(host))

        throttle.penalise(host, null)
        assertTrue(throttle.cooldownRemainingMs(host) in 299_000L..300_000L)
    }

    @Test
    fun `a clean page starts the count again`() {
        val throttle = HostThrottle()
        throttle.penalise(host, null)
        throttle.penalise(host, null)
        throttle.clear(host)

        assertEquals(0L, throttle.cooldownRemainingMs(host))
        throttle.penalise(host, null)
        assertTrue(throttle.cooldownRemainingMs(host) in 3_000L..4_000L)
    }

    @Test
    fun `retry after is honoured over the count`() {
        val throttle = HostThrottle()
        throttle.penalise(host, 30)

        assertTrue(throttle.cooldownRemainingMs(host) in 29_000L..30_000L)
    }

    @Test
    fun `hosts are counted apart`() {
        val throttle = HostThrottle()
        throttle.penalise(host, null)
        throttle.penalise(host, null)
        throttle.penalise("media.licdn.com", null)

        assertTrue(throttle.cooldownRemainingMs("media.licdn.com") in 3_000L..4_000L)
    }
}
