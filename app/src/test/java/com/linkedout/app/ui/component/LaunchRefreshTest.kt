package com.linkedout.app.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The launch test of the setting, as Feeder's RssLocalSyncKtTest checks minFeedAgeMinutes (feedsSyncedWithin15MinAreIgnored:
 * a feed read 14 minutes ago is not read again at 15 minutes, an older one is), through MTGA's LaunchRefreshTest:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/ui/component/LaunchRefreshTest.kt#LaunchRefreshTest
 * https://github.com/spacecowboy/Feeder/blob/master/app/src/androidTest/java/com/nononsenseapps/feeder/model/RssLocalSyncKtTest.kt#feedsSyncedWithin15MinAreIgnored
 * not ported: the database, the mock server and the request count (the test reads the pure decision)
 */
class LaunchRefreshTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    private fun due(frequency: SyncFrequency, minutesAgo: Long?) =
        feedsToSync(mapOf("nasa" to minutesAgo?.let { now - it * minute }), frequency, now)

    @Test
    fun stopsAreInOrderWithTheDefaultAtOneHour() {
        assertEquals(
            listOf(0L, 15L, 30L, 60L, 180L, 360L, 720L, 1440L, -1L),
            SyncFrequency.entries.map { it.minutes }
        )
        assertEquals(SyncFrequency.EVERY_LAUNCH, SyncFrequency.entries.first())
        assertEquals(SyncFrequency.NEVER, SyncFrequency.entries.last())
        assertEquals(SyncFrequency.EVERY_1_HOURS, defaultSyncFrequency)
    }

    @Test
    fun anAccountNeverReadIsAlwaysDue() {
        SyncFrequency.entries.forEach { assertEquals(setOf("nasa"), due(it, null)) }
    }

    @Test
    fun everyLaunchReadsEvenAFreshAccount() {
        assertEquals(setOf("nasa"), due(SyncFrequency.EVERY_LAUNCH, 0L))
    }

    @Test
    fun neverReadsNothingThatWasRead() {
        assertEquals(emptySet<String>(), due(SyncFrequency.NEVER, 100_000L))
        assertNull(staleTime(SyncFrequency.NEVER, now))
    }

    @Test
    fun anAccountReadJustUnderTheAgeIsIgnored() {
        assertEquals(emptySet<String>(), due(SyncFrequency.EVERY_15_MIN, 14L))
        assertEquals(emptySet<String>(), due(SyncFrequency.EVERY_1_HOURS, 59L))
    }

    @Test
    fun anAccountReadJustOverTheAgeIsDue() {
        assertEquals(setOf("nasa"), due(SyncFrequency.EVERY_15_MIN, 16L))
        assertEquals(setOf("nasa"), due(SyncFrequency.EVERY_1_HOURS, 61L))
        assertEquals(setOf("nasa"), due(SyncFrequency.EVERY_DAY, 1441L))
    }

    @Test
    fun onlyTheStaleAccountsAreChosen() {
        val fetchedAt = mapOf(
            "fresh" to now - 5 * minute,
            "stale" to now - 90 * minute,
            "never" to null
        )
        assertEquals(setOf("stale", "never"), feedsToSync(fetchedAt, SyncFrequency.EVERY_1_HOURS, now))
    }

    @Test
    fun staleTimeIsNowMinusTheChosenAge() {
        assertEquals(now - 180 * minute, staleTime(SyncFrequency.EVERY_3_HOURS, now))
    }
}
