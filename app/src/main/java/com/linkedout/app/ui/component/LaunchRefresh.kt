package com.linkedout.app.ui.component

import kotlinx.serialization.Serializable

/**
 * How old an account's last read may be at launch before it is read again, one setting for every reader, nothing of one
 * network in it. The stops are Feeder's SyncFrequency with EVERY_LAUNCH added first, and NEVER where Feeder has MANUAL
 * (the feed is then read by a pull only). Saved by name, so a reordering never changes what a user chose.
 * https://github.com/spacecowboy/Feeder/blob/master/app/src/main/java/com/nononsenseapps/feeder/archmodel/SettingsStore.kt#SyncFrequency
 * not ported: SyncFrequency's stringId (the tile and the line are plain text here), setSyncFrequency's storage and
 * schedulePeriodicRssSync (the settings store keeps the field, nothing runs in the background), the lookup from a stored
 * minutes string that falls back to MANUAL (the store reads the name and falls back to the default)
 */
@Serializable
enum class SyncFrequency(
    val minutes: Long,
    val tile: String,
    val line: String,
) {
    EVERY_LAUNCH(0L, "Every launch", "Every launch"),
    EVERY_15_MIN(15L, "15 min", "When older than 15 min"),
    EVERY_30_MIN(30L, "30 min", "When older than 30 min"),
    EVERY_1_HOURS(60L, "1 h", "When older than 1 h"),
    EVERY_3_HOURS(180L, "3 h", "When older than 3 h"),
    EVERY_6_HOURS(360L, "6 h", "When older than 6 h"),
    EVERY_12_HOURS(720L, "12 h", "When older than 12 h"),
    EVERY_DAY(1440L, "1 day", "When older than 1 day"),
    NEVER(-1L, "Never", "Never, pull to refresh"),
}

/** The stop a reader starts on. */
val defaultSyncFrequency = SyncFrequency.EVERY_1_HOURS

/**
 * The staleTime of Feeder's syncFeeds: now minus the chosen age, an account read before it is stale. Null when no account
 * can be stale (NEVER). EVERY_LAUNCH is stale at once, so its staleTime is now.
 * https://github.com/spacecowboy/Feeder/blob/master/app/src/main/java/com/nononsenseapps/feeder/model/RssLocalSync.kt#syncFeeds
 * not ported: syncFeeds's forceNetwork and debugReallyForceNetwork branches (a pull is not this test), the sync client,
 * the mutex, the single thread and the full text pass (the view model reads the accounts itself)
 */
fun staleTime(frequency: SyncFrequency, now: Long): Long? = when (frequency) {
    SyncFrequency.NEVER -> null
    else -> now - frequency.minutes * 60_000L
}

/**
 * The accounts to read at launch: those never read (their time is null) always, then those read before the staleTime.
 * [fetchedAt] maps each followed handle to the time of its last read.
 * https://github.com/spacecowboy/Feeder/blob/master/app/src/main/java/com/nononsenseapps/feeder/model/RssLocalSync.kt#feedsToSync
 * not ported: feedsToSync's feedId and tag branches (the launch asks for every followed account, a pull reads the folder
 * shown), syncLoadFeedIfStale's and syncLoadFeedsIfStale's database queries (the caller passes the cached read times),
 * retryAfter (no account is blocked from here)
 */
fun feedsToSync(
    fetchedAt: Map<String, Long?>,
    frequency: SyncFrequency,
    now: Long,
): Set<String> {
    val staleTime = staleTime(frequency, now)
    return fetchedAt
        .filter { (_, fetched) ->
            fetched == null || frequency == SyncFrequency.EVERY_LAUNCH || (staleTime != null && fetched < staleTime)
        }
        .keys
}
