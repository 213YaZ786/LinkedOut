package com.linkedout.app.feature.timeline

import com.linkedout.app.core.common.AppError
import com.linkedout.app.ui.component.failureMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Local unit tests on the JVM, Android's recipe for logic without a device:
// https://developer.android.com/training/testing/local-tests (JUnit 4 @Test, assertEquals).
class FailureMessageTest {
    private val offline = AppError.Offline
    private val dns = AppError.DnsFailure("www.linkedin.com")
    private val rate = AppError.RateLimited("www.linkedin.com", null)
    private val gone = AppError.AccountNotFound("someone")
    private val parse = AppError.ParseFailure("www.linkedin.com", 1, null)

    @Test
    fun deviceIgnoresCounts() {
        assertEquals(
            "You are offline" to "Saved posts stay readable. New ones load when you are back online.",
            message(listOf(offline, offline), 10)
        )
    }

    @Test
    fun networkCountsOutOfFollowed() {
        assertEquals(
            "Your connection does not reach LinkedIn" to
                "2 of 10 profiles were not updated. Their saved posts stay readable.",
            message(listOf(dns, dns), 10)
        )
    }

    @Test
    fun hostAdvisesWaiting() {
        assertEquals(
            "LinkedIn is holding back guests" to
                "3 of 40 profiles were not updated. Their saved posts stay readable. Try again in a few minutes.",
            message(listOf(rate, rate, rate), 40)
        )
    }

    @Test
    fun upstreamSaysNothingToAGuest() {
        assertEquals(
            "Some profiles are not public" to
                "2 of 5 profiles show nothing to a guest. Their saved posts stay readable.",
            message(listOf(gone, gone), 5)
        )
    }

    @Test
    fun appCouldNotRead() {
        assertEquals(
            "LinkedOut could not read some pages" to
                "2 of 5 profiles were not updated. Their saved posts stay readable.",
            message(listOf(parse, parse), 5)
        )
    }

    @Test
    fun majorityWins() {
        assertEquals("LinkedIn is holding back guests", message(listOf(dns, rate, rate), 9).first)
    }

    @Test
    fun tieGoesToTheFirstInOrder() {
        assertEquals("You are offline", message(listOf(parse, offline), 9).first)
        assertEquals(
            "Your connection does not reach LinkedIn",
            message(listOf(gone, dns, rate, parse), 9).first
        )
        assertEquals("LinkedIn is holding back guests", message(listOf(gone, rate), 9).first)
        assertEquals("Some profiles are not public", message(listOf(parse, gone), 9).first)
    }

    @Test
    fun singular() {
        assertEquals(
            "1 of 8 profiles was not updated. Their saved posts stay readable.",
            message(listOf(dns), 8).second
        )
        assertEquals(
            "1 of 8 profiles shows nothing to a guest. Their saved posts stay readable.",
            message(listOf(gone), 8).second
        )
    }

    @Test
    fun unknownFollowedCount() {
        assertEquals(
            "3 profiles were not updated. Their saved posts stay readable.",
            message(listOf(dns, dns, dns), 0).second
        )
        assertEquals(
            "2 profiles show nothing to a guest. Their saved posts stay readable.",
            message(listOf(gone, gone), 1).second
        )
        assertEquals(
            "1 profile was not updated. Their saved posts stay readable.",
            message(listOf(dns), 0).second
        )
    }

    @Test
    fun nothingFailedIsTheDownloadsNotice() {
        assertNull(failureMessage(emptyList(), 3, "LinkedIn"))
    }

    // Local unit test helper, https://developer.android.com/training/testing/local-tests
    private fun message(failed: List<AppError>, followed: Int) = failureMessage(failed, followed, site = "LinkedIn")!!
}
