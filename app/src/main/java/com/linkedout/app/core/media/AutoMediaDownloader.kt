package com.linkedout.app.core.media

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.linkedout.app.core.debug.RequestLog
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.network.ConnectivityMonitor
import com.linkedout.app.data.cache.FeedCache
import com.linkedout.app.data.repository.VideoSources
import com.linkedout.app.data.settings.AutoDownload
import com.linkedout.app.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves the media of posts, without being asked, so they can be read with no
 * network: first the posts that have just arrived, then the stored posts whose
 * media is still missing.
 *
 * Ported from MTGA, and replaces MediaPrefetch:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/AutoMediaDownloader.kt#AutoMediaDownloader
 *
 * Into the app's own folder through [OfflineMedia], never into Downloads: the
 * point is offline reading, not filling the reader's drawer. A video is saved
 * with its still (its cover), so the post shows its picture offline too.
 *
 * Driven by a refresh, a page of older posts or a profile's refresh, and by
 * nothing else. There is no service of its own behind this: with the app off
 * screen nothing is asked for, except by the opt-in background check, which
 * goes through the same refresh. The catch-up pass runs inside the caller's
 * call, on the app's scope, ends with it, and the files it did not reach wait
 * for the next pass. DownloadManager does the transfers.
 *
 * What it saves is bounded three ways. A watermark, so only what is newer
 * than the moment the option was switched on is ever considered for the first
 * pass. A cap per pass, so one refresh after a week away does not queue a
 * thousand files at once, the rest following on later passes. And the
 * network rule the reader chose, for both passes.
 *
 * The catch-up pass follows AntennaPod's automatic download algorithm step by
 * step: the network and power gate, the candidates, dropping what is already
 * downloaded or has no media, the cap on what is left, then queueing:
 * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/episode/autodownload/AutomaticDownloadAlgorithm.java#autoDownloadUndownloadedItems
 * not ported: autoDownloadUndownloadedItems (by name: its steps are catchUp,
 * autoDownloadCandidates, dropDownloaded and episodeSpaceLeft below)
 *
 * LinkedIn's addresses are signed and run out (the `e=` part), so a file is
 * asked for with the address of the post as it was just read, the fresh pass
 * first; [SaveAttempts] keeps a file that keeps failing from being asked for
 * at every pass. A profile page's video carries no address of its own (it is
 * not playable until the post's page was read): its address is used once
 * [VideoSources] or the stored post knows it, and it is never fetched here.
 */
class AutoMediaDownloader(
    private val context: Context,
    private val downloader: MediaDownloader,
    private val store: OfflineMedia,
    private val settings: SettingsStore,
    private val connectivity: ConnectivityMonitor,
    private val log: RequestLog,
    private val notice: MediaSavingNotice,
    private val cache: FeedCache,
    private val attempts: SaveAttempts,
    private val videos: VideoSources,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /**
     * Returns how many files were queued, for the log. [posts] is the whole
     * timeline, newest first, exactly as the repository returns it.
     */
    suspend fun consider(posts: List<Post>): Int = withContext(Dispatchers.Main) {
        val current = settings.current
        val mode = current.autoDownloadMedia
        if (mode == AutoDownload.NEVER) return@withContext 0
        // DownloadManager finishes in its own time, so the files the last
        // pass queued are only on disk now. Re-read before deciding anything.
        store.refresh()
        if (!allows(mode, connectivity.metered.value)) {
            note("skipped", "Wi-Fi only and this network is metered")
            return@withContext 0
        }

        val ids = mutableListOf<Long>()
        val names = mutableListOf<String>()
        saveFresh(posts, current.autoDownloadedUntilMillis, mode, ids, names)
        val fresh = ids.size
        catchUp(mode, current.keepPostsDays, ids, names)
        if (ids.isNotEmpty()) {
            attempts.add(names)
            // Returns at once: the batch outlives this call, and the timeline
            // must not wait for the files to land before it shows what it read.
            notice.track(ids)
        }
        note("queued ${ids.size} files", "new posts: $fresh | catch-up: ${ids.size - fresh}")
        ids.size
    }

    /** The first pass, for the posts newer than the watermark. Same pass as before, now with stills. */
    private fun saveFresh(
        posts: List<Post>,
        since: Long,
        mode: AutoDownload,
        ids: MutableList<Long>,
        names: MutableList<String>
    ) {
        val fresh = selectFresh(posts, since, MAX_POSTS_PER_PASS)
        if (fresh.isEmpty()) {
            note(
                "nothing new to save",
                "mode: $mode | posts seen: ${posts.size}" +
                    " | with media: ${posts.count { it.media.isNotEmpty() }}" +
                    " | watermark: ${if (since == 0L) "first pass" else since.toString()}"
            )
            return
        }
        fresh.forEach { post ->
            post.media.forEachIndexed { index, item -> queue(post, index, item, mode, ids, names) }
        }
        val watermark = fresh.maxOf { it.publishedAtMillis }
        // The watermark moves with the posts handled, files queued or not.
        settings.update { it.copy(autoDownloadedUntilMillis = watermark) }
    }

    /** The still of a video, then the video itself, each when it is not on disk yet. */
    private fun queue(
        post: Post,
        index: Int,
        item: MediaItem,
        mode: AutoDownload,
        ids: MutableList<Long>,
        names: MutableList<String>
    ) {
        val metered = mode == AutoDownload.ALWAYS
        if (item.type != MediaType.PHOTO && item.previewUrl.isNotBlank()) {
            val still = store.stillFor(post.id, post.authorHandle, index, item)
            downloader.cache(still, item.previewUrl, metered)?.let { ids += it; names += still.name }
        }
        // The cover stands in for the address of a video the page named
        // without carrying it, and is no video: never saved under a video's name.
        val address = addressOf(post, item) ?: return
        val target = store.fileFor(post.id, post.authorHandle, index, item)
        downloader.cache(target, address, metered)?.let { ids += it; names += target.name }
    }

    /**
     * What to fetch for [item]: its address, or for a video the profile page
     * named without carrying, the address read once from the post's own page
     * ([VideoSources.knownSource], never asked for here: one request per video
     * on every refresh is what the host cannot afford). Null when there is none.
     */
    private fun addressOf(post: Post, item: MediaItem): String? =
        if (item.type != MediaType.PHOTO && !item.playable) videos.knownSource(post.id) else item.downloadUrl

    /**
     * The second pass, AntennaPod's autoDownloadUndownloadedItems:
     * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/episode/autodownload/AutomaticDownloadAlgorithm.java#autoDownloadUndownloadedItems
     * Files of the posts the retention keeps, newest first, whose media is
     * not on the phone, at most [MAX_CATCH_UP_FILES] counting what the first
     * pass just queued.
     */
    private suspend fun catchUp(mode: AutoDownload, keepDays: Int, ids: MutableList<Long>, names: MutableList<String>) {
        val metered = connectivity.metered.value
        // True if we should auto download based on network status.
        val networkShouldAutoDl = allows(mode, metered)
        // True if we should auto download based on power status.
        val powerShouldAutoDl = powerAllows(deviceCharging(context), metered)
        // We should only auto download if both network AND power are happy.
        if (!(networkShouldAutoDl && powerShouldAutoDl)) {
            note("catch-up skipped", "mobile data and the phone is not charging")
            return
        }
        val wanted = withContext(Dispatchers.Default) {
            val candidates = autoDownloadCandidates(cache.allPosts(), clock(), keepDays)
            val missing = dropDownloaded(
                candidates = candidates,
                have = { postId, position, still ->
                    if (still) store.localStill(postId, position) != null else store.localPath(postId, position) != null
                },
                // Also what the first pass just queued, not yet in the notes.
                asked = { name -> attempts.recently(name) || name in names },
                known = videos::knownSource
            )
            val episodeSpaceLeft = episodeSpaceLeft(MAX_CATCH_UP_FILES, ids.size, missing.size)
            missing.subList(0, episodeSpaceLeft)
        }
        wanted.forEach { want ->
            val target = if (want.still) {
                store.stillFor(want.post.id, want.post.authorHandle, want.position, want.item)
            } else {
                store.fileFor(want.post.id, want.post.authorHandle, want.position, want.item)
            }
            val address = if (want.still) want.item.previewUrl else addressOf(want.post, want.item) ?: return@forEach
            downloader.cache(target, address, mode == AutoDownload.ALWAYS)?.let { ids += it; names += target.name }
        }
    }

    /**
     * Every pass says what it did, including the passes that did nothing.
     * "It does not download" is otherwise unanswerable from the device, and
     * the reasons it can do nothing are all invisible: wrong network, nothing
     * newer than the watermark, no media on what arrived.
     */
    private fun note(outcome: String, detail: String) = log.record(
        kind = RequestLog.Kind.MEDIA,
        url = "auto-download",
        outcome = outcome,
        detail = detail
    )

    /** One file the catch-up wants: [still] is the picture of a video. */
    internal data class Want(val post: Post, val position: Int, val item: MediaItem, val still: Boolean)

    companion object {
        /** One refresh after a long absence should not queue the whole gap. */
        const val MAX_POSTS_PER_PASS = 25

        /** Files one catch-up pass queues at most, the rest waits for the next pass. */
        const val MAX_CATCH_UP_FILES = 40

        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /**
         * The reader's "Save media automatically" rule for the network in use:
         * never saves nothing, Wi-Fi only (unmetered) saves unless the network
         * is metered, always saves always. One function for both passes.
         */
        internal fun allows(mode: AutoDownload, metered: Boolean): Boolean = when (mode) {
            AutoDownload.NEVER -> false
            AutoDownload.WIFI -> !metered
            AutoDownload.ALWAYS -> true
        }

        /**
         * The device is charging or full, AntennaPod's check with its code:
         * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/episode/autodownload/AutomaticDownloadAlgorithm.java#deviceCharging
         */
        internal fun deviceCharging(context: Context): Boolean {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter) ?: return false

            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }

        /**
         * AntennaPod's `deviceCharging(context) || isEnableAutodownloadOnBattery()`.
         * LinkedOut has no battery choice and runs the catch-up only while the
         * app is open or in the opt-in check, so the battery is allowed on an
         * unmetered network and the phone must be charging on mobile data.
         * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/episode/autodownload/AutomaticDownloadAlgorithm.java#autoDownloadUndownloadedItems
         */
        internal fun powerAllows(charging: Boolean, metered: Boolean): Boolean = charging || !metered

        /**
         * The candidates: every file of the posts the retention keeps, newest
         * post first. A video's still comes before the video, small and what
         * the post shows first. A post older than the retention ([keepDays],
         * zero keeps everything) is not kept and so not saved.
         *
         * Newest first, unlike [selectFresh]: there is no watermark to move,
         * what is on the phone answers for what was done, and the newest posts
         * are the ones read again.
         * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/episode/autodownload/AutomaticDownloadAlgorithm.java#autoDownloadUndownloadedItems
         */
        internal fun autoDownloadCandidates(posts: List<Post>, nowMillis: Long, keepDays: Int): List<Want> {
            val oldest = if (keepDays > 0) nowMillis - keepDays * DAY_MILLIS else Long.MIN_VALUE
            val candidates = ArrayList<Want>()
            for (post in posts.sortedByDescending { it.publishedAtMillis }) {
                if (post.publishedAtMillis < oldest) continue
                for ((position, item) in post.media.withIndex()) {
                    if (item.type != MediaType.PHOTO && item.previewUrl.isNotBlank()) {
                        candidates += Want(post, position, item, still = true)
                    }
                    candidates += Want(post, position, item, still = false)
                }
            }
            return candidates
        }

        /**
         * Filters the candidates that are not auto downloadable: already on
         * the phone ([have], AntennaPod's isDownloaded), without a file to
         * fetch (a playlist is no file: AntennaPod's !hasMedia; a video the
         * profile page named without its address, until [known] has it), or
         * queued less than a week ago ([asked], so a file still coming or one
         * that always fails is not queued at every opening).
         * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/episode/autodownload/AutomaticDownloadAlgorithm.java#autoDownloadUndownloadedItems
         */
        internal fun dropDownloaded(
            candidates: List<Want>,
            have: (postId: String, position: Int, still: Boolean) -> Boolean,
            asked: (fileName: String) -> Boolean,
            known: (postId: String) -> String? = { null }
        ): List<Want> {
            val kept = ArrayList(candidates)
            val it = kept.iterator()
            while (it.hasNext()) {
                val want = it.next()
                val post = want.post
                val name = if (want.still) {
                    OfflineMedia.stillName(post.id, post.authorHandle, want.position, want.item)
                } else {
                    OfflineMedia.name(post.id, post.authorHandle, want.position, want.item)
                }
                val unplayable = !want.still && want.item.type != MediaType.PHOTO && !want.item.playable
                if (!want.still && !unplayable && MediaDownloader.isPlaylist(want.item.downloadUrl) ||
                    unplayable && known(post.id) == null ||
                    have(post.id, want.position, want.still) ||
                    asked(name)
                ) {
                    it.remove()
                }
            }
            return kept
        }

        /**
         * How many of [autoDownloadableEpisodes] fit under [episodeCacheSize],
         * AntennaPod's episodeSpaceLeft. [downloadedEpisodes] is what the first
         * pass already queued (AntennaPod's active downloads). LinkedOut keeps
         * no count of saved files, so the cache size is the per pass cap of 40.
         * AntennaPod's subList throws when the space left is negative, here
         * it stops at zero.
         * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/episode/autodownload/AutomaticDownloadAlgorithm.java#autoDownloadUndownloadedItems
         */
        internal fun episodeSpaceLeft(
            episodeCacheSize: Int,
            downloadedEpisodes: Int,
            autoDownloadableEpisodes: Int
        ): Int {
            val episodeSpaceLeft = if (episodeCacheSize >= downloadedEpisodes + autoDownloadableEpisodes) {
                autoDownloadableEpisodes
            } else {
                episodeCacheSize - downloadedEpisodes
            }
            return episodeSpaceLeft.coerceAtLeast(0)
        }

        /**
         * The posts of [posts] whose media is still to save, oldest first and
         * at most [max] of them.
         *
         * Oldest first so that a capped pass leaves the newest for the next
         * one and the watermark can move to exactly what was handled. Newest
         * first would strand the middle of the gap forever. A pin rides at the
         * top of a timeline whatever its age, so it is never something that
         * arrived and is skipped.
         *
         * [since] of zero is the first pass after the option was switched on.
         * It takes the newest [max] instead of the oldest, so switching the
         * option on saves what is on screen now rather than nothing at all
         * until the next post happens to be published.
         *
         * A watermark newer than every post is read as a first pass too. A
         * real pass can only leave the watermark on a post it handled, so it
         * is never ahead of the feed. Ahead means it came from the clock, and
         * a reader would otherwise keep getting nothing with no way to tell
         * why short of switching the option off and on again.
         */
        internal fun selectFresh(posts: List<Post>, since: Long, max: Int): List<Post> {
            val candidates = posts.filter { !it.isPinned && it.media.isNotEmpty() }
            val newest = candidates.maxOfOrNull { it.publishedAtMillis } ?: return emptyList()
            if (since == 0L || since > newest) {
                return candidates.sortedByDescending { it.publishedAtMillis }
                    .take(max)
                    .sortedBy { it.publishedAtMillis }
            }
            return candidates
                .filter { it.publishedAtMillis > since }
                .sortedBy { it.publishedAtMillis }
                .take(max)
        }
    }
}
