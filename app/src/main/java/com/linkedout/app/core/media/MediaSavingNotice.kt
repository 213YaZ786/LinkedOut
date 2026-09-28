package com.linkedout.app.core.media

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.linkedout.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One notification for every automatic save in progress, instead of one per
 * file.
 *
 * The files themselves are hidden from the shade (see [MediaPrefetch]),
 * since twenty pictures would be twenty lines. This
 * line says how many are left, so nobody has to guess whether it is safe to
 * leave the app, then what was saved and what failed.
 *
 * Batches are pooled. Up to 0.6.52 each batch was followed inside
 * MediaPrefetch while it held its lock, for up to ten minutes, so the next
 * batch was not even handed to DownloadManager until the previous one had
 * landed. Here [track] only adds ids, and a single watcher reports on all of
 * them. The same class runs in MTGA.
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
                        show(Progress.finished(tally))
                        return
                    }
                }
            } else {
                show(Progress.running(tally))
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

    private fun show(progress: Progress) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel()
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_linkedout)
            .setSilent(true)
            .setOngoing(false)
            .setContentTitle(progress.title)
            .setContentText(progress.text)
            // If the process dies mid batch nobody is left to clear this, so
            // Android is told to do it rather than leaving a stuck bar.
            .setTimeoutAfter(if (progress.done) DONE_TIMEOUT_MILLIS else TIMEOUT_MILLIS)
        if (progress.done) {
            builder.setAutoCancel(true)
        } else {
            builder.setProgress(progress.total, progress.finished, false)
        }
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build()) }
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
