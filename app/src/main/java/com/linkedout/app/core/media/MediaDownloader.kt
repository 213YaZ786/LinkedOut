package com.linkedout.app.core.media

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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

    fun download(item: MediaItem, authorHandle: String) {
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
                    enqueue(item.copy(downloadUrl = found, playable = true), authorHandle)
                }
            }
            return
        }
        enqueue(item, authorHandle)
    }

    private fun enqueue(item: MediaItem, authorHandle: String) {
        val url = item.downloadUrl
        // A playlist is a few lines of text naming the real segments, and
        // DownloadManager would happily save it as an .mp4 that no player can
        // open. Refusing is the honest answer, and this is the one rendition
        // shape the parser can hand over without a source of its own.
        if (item.type != MediaType.PHOTO && url.looksLikePlaylist()) {
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
            .onSuccess { toast("Saving $fileName") }
            .onFailure { toast("Could not start the download") }
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

    private fun String.looksLikePlaylist(): Boolean {
        val lower = lowercase()
        return lower.contains(".m3u8") || lower.contains("%2em3u8") || lower.contains(".mpd")
    }

    /** Always from the main thread, since a save can now finish on another. */
    private fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
