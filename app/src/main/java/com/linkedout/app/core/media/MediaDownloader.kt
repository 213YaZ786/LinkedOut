package com.linkedout.app.core.media

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.util.Util
import java.io.File
import java.net.URI
import java.net.URLDecoder
import com.linkedout.app.core.link.SafeLinks
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Saves media to the device's public Downloads folder.
 *
 * Uses the system DownloadManager rather than fetching bytes ourselves: it
 * survives the app being backgrounded, shows progress in the notification
 * shade, and needs no storage permission on modern Android because the file
 * lands in a public collection it owns.
 *
 * [resolveVideo] finds the address of a video a profile page named without
 * carrying. Saving used to work on the item as the card holds it, so the save
 * button on a profile video wrote its cover picture to Downloads under a video
 * name: it looked like it had worked until the file was opened. The viewer
 * already resolved the address for its own button, which meant the same tap
 * did two different things depending on whether the video had been opened
 * first. The resolution lives here now, so every save goes through it.
 */
class MediaDownloader(
    private val context: Context,
    private val scope: CoroutineScope,
    private val resolveVideo: suspend (postId: String) -> String?
) {

    /**
     * [permalink] is the post the file comes from, kept by the media store as
     * the file's REFERER_URI, see [SaveOrigin].
     */
    fun download(item: MediaItem, authorHandle: String, permalink: String?) {
        // A profile page renders a cover and a play badge and no source at
        // all. The address is on the post's own page, one request away, and
        // kept for the life of the process once found.
        if (!item.playable && item.sourcePostId != null) {
            val postId = item.sourcePostId
            toast("Finding the video")
            scope.launch {
                val found = resolveVideo(postId)
                if (found.isNullOrBlank()) {
                    toast("LinkedIn did not give this video's address")
                } else {
                    enqueue(item.copy(downloadUrl = found, playable = true), authorHandle, permalink)
                }
            }
            return
        }
        enqueue(item, authorHandle, permalink)
    }

    private fun enqueue(item: MediaItem, authorHandle: String, permalink: String?) {
        // DownloadManager.Request throws on anything but http and https
        // (AOSP, frameworks/base/core/java/android/app/DownloadManager.java),
        // and the address comes from a post: checked as every link is.
        val url = SafeLinks.openable(item.downloadUrl)
        if (url == null) {
            toast("Could not start the download")
            return
        }
        // A playlist is a few lines of text naming the real segments, and
        // DownloadManager would happily save it as an .mp4 that no player can
        // open. Refusing is the honest answer, and this is the one rendition
        // shape the parser can hand over without a source of its own.
        if (item.type != MediaType.PHOTO && isPlaylist(url)) {
            toast("This video is streamed in pieces and cannot be saved whole")
            return
        }
        val fileName = buildFileName(item, authorHandle, url)

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Saving from LinkedOut")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (manager == null) {
            toast("Downloads are unavailable on this device")
            return
        }

        runCatching { manager.enqueue(request) }
            // The post's address goes into the file's media store row when the
            // download is done, see [SaveOrigin].
            .onSuccess { id ->
                SaveOrigin.remember(context, id, PostReferer.forPermalink(permalink))
                toast("Saving $fileName")
            }
            .onFailure { toast("Could not start the download") }
    }

    /**
     * A file saved without being asked, for offline reading.
     *
     * No toast and no line of its own in the shade: a reader who turned on
     * automatic saving does not want forty lines for one refresh. The batch
     * gets one line instead, see [MediaSavingNotice]. Hiding the per file line
     * needs DOWNLOAD_WITHOUT_NOTIFICATION in the manifest, without it the
     * enqueue itself throws.
     *
     * Returns the download id to follow, or null when the file was already
     * there or could not even be queued, so the caller can say so in the log
     * rather than guess.
     *
     * Ported from MTGA, https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/MediaDownloader.kt#cache
     * [allowMetered] is LinkedOut's addition: "Wi-Fi only" must hold for the
     * transfer too, which DownloadManager may run later on another network.
     */
    fun cache(target: File, item: MediaItem, allowMetered: Boolean = true): Long? =
        cache(target, item.downloadUrl, allowMetered)

    /** The same for any address: the file of a video, or its still. */
    fun cache(target: File, address: String, allowMetered: Boolean = true): Long? {
        if (target.exists()) return null
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return null
        // Same check as download.
        val url = SafeLinks.openable(address) ?: return null
        // A playlist is a list of pieces, not a file: nothing to keep as one.
        if (isPlaylist(url)) return null
        // The account's folder, made before the download lands in it.
        target.parentFile?.mkdirs()
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(target.name)
            .setDescription("Saving from LinkedOut")
            .setAllowedOverMetered(allowMetered)
            .setAllowedOverRoaming(allowMetered)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            .setDestinationInExternalFilesDir(context, OfflineMedia.FOLDER, "${target.parentFile?.name}/${target.name}")
        return runCatching { manager.enqueue(request) }.getOrNull()
    }

    /**
     * Nitter proxies media through URL encoded paths, so the tail is rarely a
     * usable file name. Build a predictable one instead.
     */
    private fun buildFileName(item: MediaItem, authorHandle: String, url: String): String {
        val extension = when {
            item.type == MediaType.PHOTO -> guessExtension(url, "jpg")
            item.type == MediaType.GIF -> "mp4"
            else -> guessExtension(url, "mp4")
        }
        val stamp = System.currentTimeMillis()
        return "linkedout_${authorHandle}_$stamp.$extension"
    }

    private fun guessExtension(url: String, fallback: String): String {
        val candidates = listOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "m3u8")
        val lower = url.lowercase()
        return candidates.firstOrNull { lower.contains(".$it") || lower.contains("%2e$it") }
            ?.takeIf { it != "m3u8" }
            ?: fallback
    }

    /** Always from the main thread, since a save can now finish on another. */
    private fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        /**
         * Whether [url] is an HLS playlist, told by Media3's own rule: the
         * extension of the path's last segment, after the last dot, through
         * `Util.inferContentTypeForExtension` (m3u8 is HLS), see
         * https://github.com/androidx/media/blob/release/libraries/common/src/main/java/androidx/media3/common/util/Util.java#inferContentType
         *
         * `inferContentType(Uri)` itself needs android.net.Uri, which unit tests
         * on the JVM cannot run, so the last segment is cut with java.net.URI
         * the same way `Uri.getLastPathSegment` does and only the extension
         * goes to Media3.
         *
         * One shape Media3 does not cover: a proxy can carry a file as
         * `/video/<sig>/<whole address, percent encoded>`, so the last segment
         * decodes to a full address with its own `?tag=` query and the
         * extension then reads `m3u8?tag=1`. That segment is judged again as
         * an address of its own.
         *
         * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/MediaDownloader.kt#isHls
         */
        internal fun isHls(url: String): Boolean {
            val segment = runCatching {
                // Uri.getLastPathSegment: the encoded path cut at its last slash, then decoded.
                val last = URI(url).rawPath.orEmpty().substringAfterLast('/')
                URLDecoder.decode(last.replace("+", "%2B"), Charsets.UTF_8)
            }.getOrNull() ?: return false
            val extension = segment.substringAfterLast('.', "")
            if (Util.inferContentTypeForExtension(extension) == C.CONTENT_TYPE_HLS) return true
            // The proxied shape, see above. The decoded segment is shorter
            // than the address it came from, so this ends.
            return segment.contains("://") && isHls(segment)
        }

        /** The same rule for a DASH manifest (mpd), which no save can keep whole either. */
        internal fun isDash(url: String): Boolean {
            val segment = runCatching {
                val last = URI(url).rawPath.orEmpty().substringAfterLast('/')
                URLDecoder.decode(last.replace("+", "%2B"), Charsets.UTF_8)
            }.getOrNull() ?: return false
            val extension = segment.substringAfterLast('.', "")
            if (Util.inferContentTypeForExtension(extension) == C.CONTENT_TYPE_DASH) return true
            return segment.contains("://") && isDash(segment)
        }

        /** A rendition that is a list of pieces, not a file. */
        internal fun isPlaylist(url: String): Boolean = isHls(url) || isDash(url)
    }
}
