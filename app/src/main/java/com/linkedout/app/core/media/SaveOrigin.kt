package com.linkedout.app.core.media

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.linkedout.app.core.common.writeTextAtomically
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.util.concurrent.Executors

/**
 * Keeps the post a saved file came from in the media store, as Chrome keeps
 * the page of a download: MediaStore.DownloadColumns.REFERER_URI
 * (https://developer.android.com/reference/android/provider/MediaStore.DownloadColumns#REFERER_URI).
 *
 * A Referer request header does not get there: the download provider copies
 * the referer column only from values a public DownloadManager.Request cannot
 * set (AOSP, DownloadProvider.java,
 * https://android.googlesource.com/platform/packages/providers/DownloadProvider/+/refs/heads/main/src/com/android/providers/downloads/DownloadProvider.java).
 * But REFERER_URI is a mutable column of the media store, and the row of a
 * file the app asked for belongs to the app (AOSP, MediaProvider.java,
 * sMutableColumns,
 * https://android.googlesource.com/platform/packages/providers/MediaProvider/+/refs/heads/main/src/com/android/providers/media/MediaProvider.java),
 * so the app writes it once the download is done.
 *
 * [remember] notes the download id with the post's clean address, [settle]
 * writes what is finished: after DownloadManager's ACTION_DOWNLOAD_COMPLETE
 * (SaveOriginReceiver) and at every start of the app, for the saves that
 * finished while it was not running. Nothing else is touched in the row.
 */
object SaveOrigin {

    private val io = Executors.newSingleThreadExecutor { task ->
        Thread(task, "save-origin").apply { isDaemon = true }
    }

    /**
     * Notes that download [downloadId] is a save of the post at [permalink],
     * the clean address of the post. Returns at once, the note is written on
     * a thread of its own. Nothing is noted for no address or no id.
     */
    fun remember(context: Context, downloadId: Long, permalink: String?) {
        if (downloadId < 0 || permalink.isNullOrEmpty()) return
        val store = SaveOriginStore.of(context)
        io.execute { store.add(downloadId, permalink) }
    }

    /**
     * Writes the post address of every finished save into its media store row,
     * off the main thread only. A save still running stays noted, a failed or
     * vanished one is forgotten. A finished one whose row the media scanner has
     * not made yet stays noted too: [retries] more looks, [pauseMillis] apart,
     * are taken before leaving it to the next call.
     */
    fun settle(context: Context, retries: Int = 0, pauseMillis: Long = 1_500) {
        val store = SaveOriginStore.of(context)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
        var attempt = 0
        while (true) {
            val waiting = store.pending()
            if (waiting.isEmpty()) return
            val done = HashSet<Long>()
            var unsettled = 0
            for ((id, permalink) in waiting) {
                when (val state = check(manager, id)) {
                    Check.Gone -> done += id
                    Check.Running -> Unit
                    is Check.Saved -> {
                        val path = localPath(state.localUri, downloads)
                        when {
                            path == null -> done += id
                            write(context, path, permalink) -> done += id
                            else -> unsettled++
                        }
                    }
                }
            }
            store.remove(done)
            if (unsettled == 0 || attempt >= retries) return
            attempt++
            Thread.sleep(pauseMillis)
        }
    }

    private sealed interface Check {
        data object Gone : Check
        data object Running : Check
        data class Saved(val localUri: String?) : Check
    }

    // DownloadManager.query by id, https://developer.android.com/reference/android/app/DownloadManager#query(android.app.DownloadManager.Query)
    private fun check(manager: DownloadManager, id: Long): Check {
        val cursor = runCatching { manager.query(DownloadManager.Query().setFilterById(id)) }.getOrNull()
            ?: return Check.Running
        cursor.use {
            if (!it.moveToFirst()) return Check.Gone
            return when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL ->
                    Check.Saved(it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)))
                DownloadManager.STATUS_FAILED -> Check.Gone
                else -> Check.Running
            }
        }
    }

    /** True when the app's own row of the file at [path] now holds [permalink]. */
    private fun write(context: Context, path: String, permalink: String): Boolean {
        val filter = rowFilter(path, context.packageName)
        val values = ContentValues().apply { put(MediaStore.DownloadColumns.REFERER_URI, permalink) }
        // The Downloads collection: MediaStore.Files refuses the column ("Invalid column referer_uri", seen on Android 16),
        // the Downloads table carries it (https://developer.android.com/reference/android/provider/MediaStore.Downloads).
        val downloads = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
        return runCatching {
            context.contentResolver.update(downloads, values, filter.first, filter.second) > 0
        }.getOrDefault(false)
    }

    /**
     * The file path inside [downloadsDir] that a download's local address
     * (DownloadManager.COLUMN_LOCAL_URI, a file: address) names, or null for
     * anything else: no address, another scheme, a path outside the public
     * Downloads folder or going up out of it.
     */
    internal fun localPath(localUri: String?, downloadsDir: String): String? {
        if (localUri.isNullOrEmpty()) return null
        val uri = runCatching { URI(localUri) }.getOrNull() ?: return null
        if (!"file".equals(uri.scheme, ignoreCase = true)) return null
        val path = uri.path ?: return null
        val root = downloadsDir.trimEnd('/') + "/"
        if (!path.startsWith(root) || path.length == root.length) return null
        if (path.split('/').any { it == ".." || it == "." }) return null
        return path
    }

    /**
     * The media store's WHERE and arguments for the one row this update may
     * touch: the file at [path], owned by [packageName] (MediaStore.MediaColumns
     * DATA and OWNER_PACKAGE_NAME,
     * https://developer.android.com/reference/android/provider/MediaStore.MediaColumns).
     * Another app's row, even for the same path, is never matched.
     */
    internal fun rowFilter(path: String, packageName: String): Pair<String, Array<String>> =
        "${MediaStore.MediaColumns.DATA} = ? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?" to
            arrayOf(path, packageName)
}

/**
 * The notes of [SaveOrigin], one small JSON file in the app's no backup
 * folder (the download ids mean nothing on another phone). Written in one
 * step (writeTextAtomically), at most [MAX_ENTRIES] kept, the oldest first to
 * go, and a note older than [MAX_AGE_MILLIS] is dropped: a save that never
 * finished must not stay for ever.
 */
class SaveOriginStore(
    private val file: File,
    private val clock: () -> Long = System::currentTimeMillis
) {

    @Serializable
    private data class Entry(val id: Long, val permalink: String, val at: Long)

    @Serializable
    private data class Notes(val entries: List<Entry> = emptyList())

    /** Adds the note for [id], replacing an older one for the same id. */
    fun add(id: Long, permalink: String) {
        synchronized(LOCK) {
            val kept = load().filterNot { it.id == id } + Entry(id, permalink, clock())
            save(kept.takeLast(MAX_ENTRIES))
        }
    }

    /** The notes still worth keeping, oldest first: (download id, post address). */
    fun pending(): List<Pair<Long, String>> = synchronized(LOCK) {
        val fresh = load()
        // Expired notes leave the file with the next change.
        fresh.map { it.id to it.permalink }
    }

    /** Forgets the notes of [ids]. */
    fun remove(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        synchronized(LOCK) {
            val before = load()
            val after = before.filterNot { it.id in ids }
            if (after.size != before.size) save(after)
        }
    }

    private fun load(): List<Entry> {
        val notes = runCatching { JSON.decodeFromString<Notes>(file.readText()) }.getOrNull() ?: return emptyList()
        val now = clock()
        return notes.entries.filter { now - it.at in 0..MAX_AGE_MILLIS }
    }

    private fun save(entries: List<Entry>) {
        runCatching { file.writeTextAtomically(JSON.encodeToString(Notes(entries))) }
    }

    companion object {
        const val MAX_ENTRIES = 500
        const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000

        private val LOCK = Any()
        private val JSON = Json { ignoreUnknownKeys = true }

        fun of(context: Context): SaveOriginStore =
            SaveOriginStore(File(context.noBackupFilesDir, "save-origin.json"))
    }
}
