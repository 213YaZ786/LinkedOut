package com.linkedout.app.core.media

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.linkedout.app.R
import com.linkedout.app.core.debug.RequestLog
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.core.model.Post
import com.linkedout.app.data.settings.AutoDownload
import com.linkedout.app.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Saves the media of freshly read posts so they can be looked at offline.
 *
 * Runs after a refresh, wherever that refresh came from, the reader pulling
 * down or the background check, so the archive grows with its pictures and
 * not only with its words.
 *
 * DownloadManager does the work. It is a system service, so a download that
 * has been handed over finishes whether or not LinkedOut is still open, and
 * its own per file notifications are turned off here: thirty pictures would
 * be thirty lines in the shade. One notification is posted instead, which
 * says how many are left, so nobody has to guess whether it is safe to leave.
 *
 * Every pass writes one MEDIA line in the activity log with three numbers:
 * queued, already there, refused. A count of zero on its own says nothing,
 * and the first version of this instrument in MTGA had exactly that flaw.
 */
class MediaPrefetch(
    private val context: Context,
    private val settings: SettingsStore,
    private val offline: OfflineMedia,
    private val log: RequestLog,
    private val scope: CoroutineScope
) {

    /** One pass at a time, so two refreshes cannot queue the same file twice. */
    private val work = Mutex()

    fun queue(posts: List<Post>) {
        val choice = settings.current.autoDownloadMedia
        if (choice == AutoDownload.NEVER || posts.isEmpty()) return

        scope.launch {
            work.withLock {
                offline.refresh()
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (manager == null) {
                    log.record(
                        kind = RequestLog.Kind.MEDIA,
                        url = "auto-download",
                        outcome = "no DownloadManager on this device"
                    )
                    return@withLock
                }

                var queued = 0
                var present = 0
                var unsaveable = 0
                var rejected = 0
                var firstRejection: String? = null
                val ids = mutableListOf<Long>()

                for (post in posts) {
                    for (item in post.media) {
                        val url = item.downloadUrl
                        when {
                            // A video the page named without carrying has no
                            // address yet. Finding it costs a request per post
                            // and belongs to a tap, not to a background pass.
                            !item.playable && item.type != MediaType.PHOTO -> unsaveable++
                            url.isBlank() || url.looksLikePlaylist() -> unsaveable++
                            offline.has(url) -> present++
                            else -> when (val result = enqueue(manager, url, choice)) {
                                is Enqueued.Accepted -> {
                                    queued++
                                    ids.add(result.id)
                                }
                                is Enqueued.Rejected -> {
                                    rejected++
                                    if (firstRejection == null) firstRejection = result.reason
                                }
                            }
                        }
                    }
                }

                // Four numbers, not three. "Refused" on its own was the same
                // dead end as a count of zero: it did not say whether the app
                // declined the file or whether DownloadManager threw the
                // request back, and in 0.6.30 it was the second, every time.
                val canNotify = NotificationManagerCompat.from(context).areNotificationsEnabled()
                log.record(
                    kind = RequestLog.Kind.MEDIA,
                    url = "auto-download",
                    outcome = "$queued queued, $present already there, " +
                        "$unsaveable not saveable, $rejected rejected",
                    detail = listOfNotNull(
                        "into /Android/data/${context.packageName}/files/${OfflineMedia.DIRECTORY}",
                        // Said here because a batch that finishes in a second
                        // leaves nothing on screen to look at, and then the
                        // only question left is whether it could have shown
                        // anything at all.
                        if (canNotify) "notifications allowed" else "notifications off in Android",
                        firstRejection?.let { "first rejection: $it" }
                    ).joinToString(" | ")
                )

                if (ids.isNotEmpty()) follow(manager, ids)
            }
        }
    }

    /** Either an id to follow, or the reason the system gave back. */
    private sealed interface Enqueued {
        data class Accepted(val id: Long) : Enqueued
        data class Rejected(val reason: String) : Enqueued
    }

    private fun enqueue(manager: DownloadManager, url: String, choice: AutoDownload): Enqueued =
        runCatching {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("LinkedOut media")
                // Hiding the per file notification needs
                // DOWNLOAD_WITHOUT_NOTIFICATION in the manifest. Without it
                // this line is what throws, not the address or the folder.
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                .setDestinationInExternalFilesDir(context, OfflineMedia.DIRECTORY, offline.nameFor(url))
                .setAllowedOverMetered(choice == AutoDownload.ALWAYS)
                .setAllowedOverRoaming(choice == AutoDownload.ALWAYS)
            manager.enqueue(request)
        }.fold(
            onSuccess = { Enqueued.Accepted(it) },
            onFailure = { error ->
                Enqueued.Rejected("${error::class.simpleName}: ${error.message.orEmpty().take(120)}")
            }
        )

    /**
     * Watches the batch and keeps one notification up to date until it is
     * done. Polling rather than a broadcast receiver: a receiver would have to
     * be declared in the manifest and live outside the app to be woken, which
     * is exactly the sort of thing that is not wanted here.
     */
    private suspend fun follow(manager: DownloadManager, ids: List<Long>) {
        val total = ids.size
        notify(left = total, total = total, done = false)
        var left = total
        var rounds = 0
        while (left > 0 && rounds < MAX_ROUNDS) {
            delay(POLL_MILLIS)
            rounds++
            left = runCatching { stillRunning(manager, ids) }.getOrDefault(0)
            if (left > 0) notify(left = left, total = total, done = false)
        }
        offline.refresh()
        // Not a bare cancel. Seven pictures land in about a second, so the
        // progress bar existed for one poll and was gone before anyone could
        // look at it, which is exactly what happened in 0.6.33. What stays is
        // a line saying what was saved, which Android clears by itself.
        notify(left = 0, total = total, done = true)
    }

    private fun stillRunning(manager: DownloadManager, ids: List<Long>): Int {
        val query = DownloadManager.Query().setFilterById(*ids.toLongArray())
        manager.query(query)?.use { cursor ->
            val column = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (column < 0) return 0
            var running = 0
            while (cursor.moveToNext()) {
                val status = cursor.getInt(column)
                if (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING) {
                    running++
                }
            }
            return running
        }
        return 0
    }

    private fun notify(left: Int, total: Int, done: Boolean) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel()
        val finished = total - left
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_linkedout)
            .setSilent(true)
            .setOngoing(false)
            // If the process dies mid batch nobody is left to clear this, so
            // Android is told to do it rather than leaving a stuck bar.
            .setTimeoutAfter(if (done) DONE_TIMEOUT_MILLIS else TIMEOUT_MILLIS)
        if (done) {
            builder
                .setContentTitle("Media saved for offline reading")
                .setContentText(if (total == 1) "1 file" else "$total files")
                .setAutoCancel(true)
        } else {
            builder
                .setContentTitle("Saving media for offline reading")
                .setContentText("$finished of $total")
                .setProgress(total, finished, false)
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Saving media", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while pictures and videos are saved for offline reading."
                setShowBadge(false)
            }
        )
    }

    private fun String.looksLikePlaylist(): Boolean {
        val lower = lowercase()
        return lower.contains(".m3u8") || lower.contains("%2em3u8") || lower.contains(".mpd")
    }

    companion object {
        private const val CHANNEL = "media-saving"
        private const val NOTIFICATION_ID = 4201
        private const val POLL_MILLIS = 1_000L

        /** Ten minutes of polling, then the batch is left to the system. */
        private const val MAX_ROUNDS = 600
        private const val TIMEOUT_MILLIS = 10 * 60 * 1000L

        /** How long the "saved" line stays before Android takes it away. */
        private const val DONE_TIMEOUT_MILLIS = 60 * 1000L
    }
}
