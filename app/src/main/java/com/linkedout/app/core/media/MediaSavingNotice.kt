package com.linkedout.app.core.media

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.linkedout.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The download error notification of AntennaPod's sendErrorNotification, as a
 * pure function: a subscriber for the event (the screen is on) takes the
 * message and nothing is sent; else the download progress notification, then
 * the error notification at the last attempt, only when a download failed.
 * Source: https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/episode/EpisodeDownloadWorker.java#sendErrorNotification
 * not ported: sendMessage, getDownloadLogsIntent, generateProgressNotification, retry3times (see show)
 */
internal fun sendErrorNotification(
    hasSubscriberForEvent: Boolean,
    notificationProgress: MediaSavingNotice.Tally,
    isLastRunAttempt: Boolean
): MediaSavingNotice.Shade = when {
    hasSubscriberForEvent -> MediaSavingNotice.Shade.NOTHING
    !isLastRunAttempt -> MediaSavingNotice.Shade.BAR
    notificationProgress.failed > 0 -> MediaSavingNotice.Shade.LINE
    else -> MediaSavingNotice.Shade.NOTHING
}

/**
 * One notification for every automatic save in progress, instead of one per
 * file.
 *
 * The files themselves are hidden from the shade by whoever enqueues them
 * (VISIBILITY_HIDDEN, which needs DOWNLOAD_WITHOUT_NOTIFICATION in the
 * manifest), since twenty pictures would be twenty lines. This
 * line says how many are left, so nobody has to guess whether it is safe to
 * leave the app, then what was saved and what failed.
 *
 * Batches are pooled. Following each batch on its own let two close
 * refreshes write over each other's counts, and following it under a lock
 * held the next batch back for up to ten minutes. Here [track] only adds ids,
 * and a single watcher reports on all of them.
 *
 * Polling rather than a broadcast receiver: a receiver would have to be
 * declared in the manifest and be woken with the app closed, which
 * LinkedOut does not do.
 */
class MediaSavingNotice(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val lock = Any()
    private val tracked = LinkedHashSet<Long>()
    private var watcher: Job? = null

    /** Whether the bar is in the shade, so a pass that shows nothing cancels it and only it. */
    private var barShown = false

    /** Adds a batch to what is being reported on. Returns at once. */
    fun track(ids: List<Long>) {
        if (ids.isEmpty()) return
        synchronized(lock) {
            tracked += ids
            if (watcher?.isActive != true) watcher = scope.launch { watch() }
        }
    }

    private suspend fun watch() {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        var rounds = 0
        while (true) {
            val ids = synchronized(lock) { tracked.toList() }
            val tally = runCatching { tally(manager, ids) }.getOrDefault(Tally(done = ids.size))
            if (tally.running == 0 || rounds >= MAX_ROUNDS) {
                synchronized(lock) {
                    // A batch added while this pass was reading is still to
                    // report on, so the watcher only stops when nothing new
                    // came in.
                    if (tracked.size == ids.size) {
                        tracked.clear()
                        watcher = null
                        show(sendErrorNotification(onScreen(), tally, isLastRunAttempt = true), Progress.finished(tally))
                        return
                    }
                }
            } else {
                show(sendErrorNotification(onScreen(), tally, isLastRunAttempt = false), Progress.running(tally))
            }
            delay(POLL_MILLIS)
            rounds++
        }
    }

    /**
     * Where each download stands. Paused counts as still running: it is
     * DownloadManager waiting for the network or for a retry, and calling
     * that finished announced files as saved while they were still coming.
     */
    private fun tally(manager: DownloadManager, ids: List<Long>): Tally {
        if (ids.isEmpty()) return Tally()
        var running = 0
        var saved = 0
        var failed = 0
        manager.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))?.use { cursor ->
            val column = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (column < 0) return Tally(done = ids.size)
            while (cursor.moveToNext()) {
                when (cursor.getInt(column)) {
                    DownloadManager.STATUS_SUCCESSFUL -> saved++
                    DownloadManager.STATUS_FAILED -> failed++
                    else -> running++
                }
            }
        }
        // An id the system no longer knows was removed, from the Downloads
        // app for instance. It is over, and it was not saved by us.
        val missing = ids.size - running - saved - failed
        return Tally(running = running, saved = saved, failed = failed + missing.coerceAtLeast(0))
    }

    /**
     * Whether the reader is on screen, as UpdateGate reads it: the process
     * lifecycle at least STARTED (ON_STOP comes a moment after the last
     * screen goes, ON_START when one returns).
     * Source: https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/update/UpdateGate.kt#UpdateGate
     * (https://developer.android.com/reference/androidx/lifecycle/ProcessLifecycleOwner).
     * not ported: staged (no update here)
     * not ported: dropped (no update here)
     * not ported: installNow (no update here)
     * not ported: storeLeaves (no Store call here)
     * not ported: pendingUserAction (no confirmation here)
     * not ported: started (no held confirmation here)
     * not ported: sendErrorNotification (see show)
     */
    private fun onScreen(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    /**
     * Posts, replaces or takes away the line for [shade]. Each pass of the
     * watcher decides again, so leaving the app turns the bar on within a
     * second and coming back cancels it.
     *
     * Source: AntennaPod's EpisodeDownloadWorker.sendErrorNotification, which
     * tells the app's own screen when one listens (here: nothing, [Shade.NOTHING])
     * and otherwise posts "download failed" with the public visibility, an
     * intent into the app and auto cancel
     * (https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/episode/EpisodeDownloadWorker.java#sendErrorNotification).
     * not ported: sendMessage (the in-app message; the screen shows its own state)
     * not ported: getDownloadLogsIntent (the app has no download log screen)
     * not ported: generateProgressNotification (per episode percentages; here one bar of files done)
     * not ported: isLastRunAttempt (WorkManager retries; DownloadManager retries itself)
     * not ported: retry3times (same)
     * not ported: storeLeaves, pendingUserAction, started (UpdateGate, see onScreen)
     */
    private fun show(shade: Shade, progress: Progress) {
        val notifications = NotificationManagerCompat.from(context)
        if (shade == Shade.NOTHING) {
            if (barShown) notifications.cancel(NOTIFICATION_ID)
            barShown = false
            return
        }
        if (!notifications.areNotificationsEnabled()) return
        ensureChannel()
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_linkedout)
            .setSilent(true)
            .setOngoing(false)
            .setContentTitle(progress.title)
            .setContentText(progress.text)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(downloadsIntent())
            // If the process dies mid batch nobody is left to clear this, so
            // Android is told to do it rather than leaving a stuck bar.
            .setTimeoutAfter(if (shade == Shade.LINE) DONE_TIMEOUT_MILLIS else TIMEOUT_MILLIS)
        if (shade == Shade.LINE) {
            builder.setAutoCancel(true)
        } else {
            builder.setProgress(progress.total, progress.finished, false)
        }
        barShown = shade == Shade.BAR
        runCatching { notifications.notify(NOTIFICATION_ID, builder.build()) }
    }

    /**
     * The tap opens the app.
     * Source: AntennaPod's EpisodeDownloadWorker.getDownloadsIntent, a
     * PendingIntent.getActivity with FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
     * (https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/episode/EpisodeDownloadWorker.java#getDownloadsIntent),
     * the launch intent standing for its MainActivityStarter.
     * not ported: sendErrorNotification, storeLeaves, pendingUserAction, started (see show and onScreen)
     */
    private fun downloadsIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            // Default rather than low. At low importance Android folds the
            // line to the bottom of the shade, and a batch of a few seconds
            // is then never seen. Silence comes from setSilent, not from here.
            NotificationChannel(CHANNEL, "Saving media", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Progress while pictures and videos are saved for offline reading."
                setShowBadge(false)
            }
        )
    }

    /**
     * What the shade shows for a pass: nothing, the bar of files done, or the
     * one line of failures. The three states of AntennaPod's EpisodeDownloadWorker:
     * the app's own screen shows the failure (nothing), generateProgressNotification
     * (the bar), sendErrorNotification (the line)
     * (https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/episode/EpisodeDownloadWorker.java#sendErrorNotification).
     */
    internal enum class Shade { NOTHING, BAR, LINE }

    /** How a set of downloads stands. [done] is for a read that could not tell. */
    internal data class Tally(
        val running: Int = 0,
        val saved: Int = 0,
        val failed: Int = 0,
        val done: Int = 0
    ) {
        val total: Int get() = running + saved + failed + done
    }

    /** What the notification says, pure so the wording can be tested. */
    internal data class Progress(
        val title: String,
        val text: String,
        val finished: Int,
        val total: Int,
        val done: Boolean
    ) {
        companion object {
            fun running(tally: Tally) = Progress(
                title = "Saving media for offline reading",
                text = "${tally.total - tally.running} of ${tally.total}",
                finished = tally.total - tally.running,
                total = tally.total,
                done = false
            )

            /** Says what failed. The first version counted a failed file as saved. */
            fun finished(tally: Tally): Progress {
                val saved = tally.saved + tally.done
                val failed = tally.failed
                return Progress(
                    title = if (saved == 0 && failed > 0) "Media could not be saved" else "Media saved for offline reading",
                    text = listOfNotNull(
                        files(saved).takeIf { saved > 0 }?.let { "$it saved" },
                        files(failed).takeIf { failed > 0 }?.let { "$it failed" },
                        // Only after ten minutes of watching, then left to the system.
                        files(tally.running).takeIf { tally.running > 0 }?.let { "$it still downloading" }
                    ).joinToString(", ").ifEmpty { "Nothing to save" },
                    finished = tally.total,
                    total = tally.total,
                    done = true
                )
            }

            private fun files(count: Int) = if (count == 1) "1 file" else "$count files"
        }
    }

    private companion object {
        const val CHANNEL = "media-saving"
        const val NOTIFICATION_ID = 4201
        const val POLL_MILLIS = 1_000L

        /** Ten minutes of polling, then the batch is left to the system. */
        const val MAX_ROUNDS = 600
        const val TIMEOUT_MILLIS = 10 * 60 * 1000L

        /** How long the "saved" line stays before Android takes it away. */
        const val DONE_TIMEOUT_MILLIS = 60 * 1000L
    }
}
