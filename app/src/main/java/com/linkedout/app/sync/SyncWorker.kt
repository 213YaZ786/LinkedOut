package com.linkedout.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.linkedout.app.data.accounts.AccountStore
import com.linkedout.app.data.cache.FeedCache
import com.linkedout.app.data.repository.TimelineRepository
import com.linkedout.app.data.settings.SettingsStore
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * Polls followed accounts in the background.
 *
 * This is what turns a five post window into real history. X only renders the
 * head of a profile for logged out visitors, and no cursor exists to go
 * further back. But if LinkedOut keeps checking and keeps everything it sees, the
 * archive grows forward from the day you start. You cannot recover last month,
 * you can own next month.
 *
 * Each run reuses the normal refresh path, so results land in the same cache
 * the timeline reads, deduplicated by post id. When notifications are on,
 * the cache is compared before and after the run, and what is genuinely new
 * is announced (see NewPosts for the rules).
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val koin = GlobalContext.getOrNull() ?: return Result.retry()
        val settings = koin.get<SettingsStore>()
        if (!settings.current.backgroundSync) return Result.success()

        val repository = koin.get<TimelineRepository>()
        val cache = koin.get<FeedCache>()
        val accounts = koin.get<AccountStore>()
        val notifier = NewPostNotifier(applicationContext)

        // What was stored before this check, per account. Only read when a
        // notification could actually be shown, it costs a file per account.
        val watching = settings.current.notifyNewPosts && notifier.canNotify()
        val handles = accounts.accounts.value.map { it.handle.lowercase() }
        val before = if (watching) snapshot(cache, handles) else emptyMap()

        return runCatching { repository.refresh() }
            .fold(
                onSuccess = { merged ->
                    if (watching) announce(cache, handles, before, settings, notifier)
                    // A partial failure is normal with fragile upstreams and is
                    // not worth a retry storm. The next scheduled run covers it.
                    if (merged.posts.isEmpty() && merged.errors.isNotEmpty()) {
                        Result.retry()
                    } else {
                        Result.success()
                    }
                },
                onFailure = { Result.retry() }
            )
    }

    private suspend fun snapshot(cache: FeedCache, handles: List<String>): Map<String, NewPosts.Before> =
        handles.associateWith { handle -> NewPosts.snapshot(cache.read(handle)?.posts.orEmpty()) }

    /** Never lets a notification problem fail the sync itself. */
    private suspend fun announce(
        cache: FeedCache,
        handles: List<String>,
        before: Map<String, NewPosts.Before>,
        settings: SettingsStore,
        notifier: NewPostNotifier
    ) {
        runCatching {
            val after = handles.associateWith { handle -> cache.read(handle)?.posts.orEmpty() }
            val current = settings.current
            val fresh = NewPosts.detect(
                before = before,
                after = after,
                sinceMillis = current.notifySinceMillis,
                filters = NewPosts.Filters(
                    hideReplies = current.homeHideReplies,
                    hideReposts = current.homeHideReposts,
                    mediaOnly = current.homeMediaOnly
                )
            )
            notifier.show(fresh)
        }
    }

    companion object {
        private const val NAME = "linkedout-sync"

        fun schedule(context: Context, intervalMinutes: Int, wifiOnly: Boolean) {
            // WorkManager's floor is 15 minutes, and anything shorter would be
            // rude to instances that are already rate limiting us.
            val interval = intervalMinutes.coerceAtLeast(15).toLong()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                        )
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
