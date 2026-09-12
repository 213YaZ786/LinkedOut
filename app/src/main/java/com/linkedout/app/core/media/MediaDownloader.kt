package com.linkedout.app.core.media

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType

/**
 * Saves media to the device's public Downloads folder.
 *
 * Uses the system DownloadManager rather than fetching bytes ourselves: it
 * survives the app being backgrounded, shows progress in the notification
 * shade, and needs no storage permission on modern Android because the file
 * lands in a public collection it owns.
 */
class MediaDownloader(private val context: Context) {

    fun download(item: MediaItem, authorHandle: String) {
        val url = item.downloadUrl
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

    private fun toast(message: String) =
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
