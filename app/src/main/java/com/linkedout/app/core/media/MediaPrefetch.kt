package com.linkedout.app.core.media

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import com.linkedout.app.core.debug.RequestLog
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.core.model.Post
import com.linkedout.app.data.settings.AutoDownload
import com.linkedout.app.data.repository.VideoSources
import com.linkedout.app.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
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
 * be thirty lines in the shade. [MediaSavingNotice] posts one instead, which
 * says how many are left, then what was saved and what failed.
 *
 * Every pass writes one MEDIA line in the activity log with three numbers:
 * queued, already there, refused. A count of zero on its own says nothing,
 * and the first version of this instrument in MTGA had exactly that flaw.
 */
class MediaPrefetch(
    private val context: Context,
    private val settings: SettingsStore,
    private val offline: OfflineMedia,
    private val videos: VideoSources,
    private val log: RequestLog,
    private val scope: CoroutineScope,
    private val notice: MediaSavingNotice
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
                        // A video the page named without carrying has an
                        // address only once it has been played or opened. That
                        // address is taken if it is already known and never
                        // asked for here: one request per video on every
                        // refresh is exactly what the reader cannot afford.
                        val url = if (!item.playable && item.type == MediaType.VIDEO) {
                            videos.knownSource(post.id).orEmpty()
                        } else {
                            item.downloadUrl
                        }
                        when {
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

                // Returns at once, so the lock is free for the next batch.
                notice.track(ids)
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

    private fun String.looksLikePlaylist(): Boolean {
        val lower = lowercase()
        return lower.contains(".m3u8") || lower.contains("%2em3u8") || lower.contains(".mpd")
    }
}
