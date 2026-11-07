package com.linkedout.app.core.media

import android.content.Context
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where saved media lives, so a post can be watched with no network.
 *
 * Ported from MTGA's OfflineMedia, which keeps what is saved automatically in
 * the app's own external files directory, not Downloads:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/OfflineMedia.kt#OfflineMedia
 * No permission to ask for, nothing offered to the gallery, and the folder
 * disappears with the app. Android 11 and later hide it from the Files app,
 * which is why Settings has a screen listing what is really there.
 *
 * The index is the file name and there is no database. A folder per account
 * (AntennaPod's folder per feed) holds files named `<post id>.<position>.<ext>`,
 * and `<post id>.<position>.still.<ext>` for the cover of a video: the folder,
 * the post id and the position of the media within that post are everything
 * needed to go from a post on screen to a file on disk and back. The name
 * never depends on LinkedIn's address, which is signed, expires and differs
 * between a profile page and the post's own page for the same picture.
 *
 * not ported: ManualSaves (X's saves by hand into Downloads, here the manual
 * save is MediaDownloader.download with SaveOrigin)
 * not ported: parseLegacy (MTGA's own earlier names, LinkedOut never wrote them)
 * not ported: getExtension, indexOfExtension, guessFileName (they read the
 * extension from the address, LinkedIn's CDN addresses carry none: the media
 * type gives it, see [name])
 * not ported: getMediafilename as such (its place is taken by [name] and [stillName])
 */
class OfflineMedia(context: Context) {

    /**
     * getExternalFilesDir can be null when external storage is not mounted.
     * filesDir then, which is smaller but always there: a reader with no SD
     * card should still get offline playback.
     */
    private val directory =
        File(context.getExternalFilesDir(null) ?: context.filesDir, FOLDER).apply { mkdirs() }

    private val _version = MutableStateFlow(0)

    /**
     * Counts every change of what is saved, a file landing or going, so a
     * screen that is open reads the index again at once instead of at its next
     * resume. Compose keys on it, see rememberMediaSource.
     */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** Where [item] of [postId] is, or would be. Never null, may not exist. */
    fun fileFor(postId: String, authorHandle: String, index: Int, item: MediaItem): File =
        File(File(directory, getMediafilePath(authorHandle)), name(postId, authorHandle, index, item))

    /** Where the still of a video of [postId] is, or would be. Same scheme as [fileFor]. */
    fun stillFor(postId: String, authorHandle: String, index: Int, item: MediaItem): File =
        File(File(directory, getMediafilePath(authorHandle)), stillName(postId, authorHandle, index, item))

    /**
     * postId and position to absolute path, for everything on disk.
     *
     * Held in memory because the lookup happens once per picture per frame of
     * a scrolling list, and listing a directory there would cost more than
     * the picture. Rebuilt by [refresh], never guessed.
     */
    @Volatile
    private var index: Map<String, String> = emptyMap()

    /** The same for the stills of videos. */
    @Volatile
    private var stills: Map<String, String> = emptyMap()

    /**
     * Re-reads the folder, one listing. Bumps [version] only when something
     * moved, so a pass that found nothing new recomposes nothing.
     *
     * The files this app wrote before the account folders sit flat in the
     * folder, named by the MD5 of their signed address. Nothing in such a name
     * says which post or position it belongs to, so none can be matched to
     * anything and they are deleted here: the next pass saves the media again
     * under the new names.
     */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().filter { it.isFile }.forEach { runCatching { it.delete() } }
        val main = HashMap<String, String>()
        val still = HashMap<String, String>()
        files().forEach { file ->
            val parsed = parseFile(file) ?: return@forEach
            // A file DownloadManager has not finished writing is no copy yet.
            if (file.length() == 0L) return@forEach
            (if (parsed.still) still else main)[key(parsed.postId, parsed.index)] = file.absolutePath
        }
        if (main != index || still != stills) {
            index = main
            stills = still
            _version.update { it + 1 }
        }
    }

    /**
     * Where this media is on disk, or null when it was never saved.
     *
     * Not suspending on purpose: it is read during composition, and a photo
     * that is already on the phone should never wait on a coroutine to be
     * shown.
     */
    fun localPath(postId: String, position: Int): String? = index[key(postId, position)]

    /** The saved still of a video, or null. Same index as [localPath]. */
    fun localStill(postId: String, position: Int): String? = stills[key(postId, position)]

    private fun key(postId: String, position: Int) = "$postId/$position"

    /** The copies on disk, stills left out, in no order: the screens sort what they need. */
    suspend fun entries(): List<Entry> = withContext(Dispatchers.IO) {
        files().mapNotNull { file ->
            parseFile(file)?.takeIf { !it.still }?.let { Entry(file, it.handle, it.postId, it.index) }
        }
    }

    /** The saved still of each video of the folder, by post id and position. Same listing as [refresh]. */
    suspend fun stillPaths(): Map<String, String> = withContext(Dispatchers.IO) {
        refresh()
        stills
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        files().sumOf { it.length() }
    }

    suspend fun delete(file: File): Boolean {
        val gone = withContext(Dispatchers.IO) { runCatching { file.delete() }.getOrDefault(false) }
        if (gone) refresh()
        return gone
    }

    /**
     * Deletes every file of the folder, stills too, that [matches] says so for,
     * with one listing and one refresh: a long list is never listed again for
     * each file it removes. Same walk as [keepOnly].
     */
    suspend fun deleteWhere(matches: (Parsed) -> Boolean): Int = withContext(Dispatchers.IO) {
        var removed = 0
        files().forEach { file ->
            val parsed = parseFile(file)
            if (parsed != null && matches(parsed) && runCatching { file.delete() }.getOrDefault(false)) removed++
        }
        if (removed > 0) refresh()
        removed
    }

    /** Every file of the account folders in one listing. */
    private fun files(): List<File> = directory.listFiles().orEmpty().flatMap { child ->
        if (child.isDirectory) child.listFiles().orEmpty().filter { it.isFile } else emptyList()
    }

    /** What [file] says about its media, by its account folder and its name. */
    private fun parseFile(file: File): Parsed? =
        file.parentFile?.name?.let { parse(file.name, it) }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            directory.listFiles().orEmpty().forEach { runCatching { it.deleteRecursively() } }
        }
        refresh()
    }

    /**
     * Deletes the media of posts the cache no longer holds.
     *
     * Retention is set once, in "Keep posts", and it governs this too: a
     * media file outlives nothing. A second size budget to tune would be a
     * second thing to get wrong.
     */
    suspend fun keepOnly(postIds: Set<String>): Int = withContext(Dispatchers.IO) {
        var removed = 0
        files().forEach { file ->
            val parsed = parseFile(file)
            if (parsed == null || parsed.postId !in postIds) {
                if (runCatching { file.delete() }.getOrDefault(false)) removed++
            }
        }
        if (removed > 0) refresh()
        removed
    }

    /** One saved file, with the post it came from. */
    data class Entry(val file: File, val handle: String, val postId: String, val index: Int)

    /** What a file name says about the media it holds. A [still] is the picture of a saved video. */
    data class Parsed(val handle: String, val postId: String, val index: Int, val still: Boolean = false)

    companion object {
        /** The sub directory of the app's files folder, shared with the downloader. */
        const val FOLDER = "media"

        private const val STILL = "still"

        /**
         * The account's folder under the media folder, one for every account
         * (handles are case blind, hence the lower case).
         * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/DownloadRequestCreator.java#getMediafilePath
         */
        internal fun getMediafilePath(authorHandle: String): String =
            FileNameGenerator.generateFileName(authorHandle.lowercase())

        /**
         * post id, then position within the post, then extension. AntennaPod
         * takes the extension from the address; LinkedIn's CDN addresses carry
         * none, so it comes from the type: jpg for a photo, mp4 for a video or
         * a GIF (LinkedIn serves its animations as video). The handle is the
         * folder's business ([getMediafilePath]), not the name's.
         *
         * The folder and this name are the whole index, the only thing
         * standing between a saved file and the post that owns it.
         * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/DownloadRequestCreator.java#getMediafilename
         */
        @Suppress("UNUSED_PARAMETER")
        internal fun name(postId: String, authorHandle: String, index: Int, item: MediaItem): String =
            baseName(postId) + "." + index + "." + if (item.type == MediaType.PHOTO) "jpg" else "mp4"

        /**
         * The name of the still of a video: [name]'s base with a "still" part
         * and a picture extension.
         * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/DownloadRequestCreator.java#getMediafilename
         */
        @Suppress("UNUSED_PARAMETER")
        internal fun stillName(postId: String, authorHandle: String, index: Int, item: MediaItem): String =
            baseName(postId) + "." + index + "." + STILL + ".jpg"

        private fun baseName(postId: String): String {
            var baseFilename = FileNameGenerator.generateFileName(postId)
            val filenameMaxLength = 220
            if (baseFilename.length > filenameMaxLength) {
                baseFilename = baseFilename.substring(0, filenameMaxLength)
            }
            return baseFilename
        }

        /**
         * What a file name says about its media. [folder] is the account
         * folder the file sits in.
         */
        internal fun parse(fileName: String, folder: String): Parsed? {
            val parts = fileName.substringBeforeLast('.').split('.')
            if (parts.size != 2 && !(parts.size == 3 && parts[2] == STILL)) return null
            val index = parts[1].toIntOrNull() ?: return null
            if (folder.isEmpty() || parts[0].isEmpty()) return null
            return Parsed(folder, parts[0], index, still = parts.size == 3)
        }
    }
}
