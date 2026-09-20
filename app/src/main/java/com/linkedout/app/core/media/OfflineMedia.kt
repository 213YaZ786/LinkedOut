package com.linkedout.app.core.media

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest

/**
 * Media kept on the phone so a saved post can still be looked at with no
 * network.
 *
 * Everything lives in the app's own external folder,
 * /Android/data/com.linkedout.app/files/media, which needs no permission and
 * is deleted with the app. Android 11 and later hide that folder from the
 * Files app and from the picker, so a file manager will show it empty even
 * when it is full. That is why there is a screen in Settings that lists what
 * is really there, and why a missing file in a file manager proves nothing.
 *
 * A name is the fingerprint of its address, so the same picture is never
 * downloaded twice and any screen can ask "do I already have this" without
 * keeping a second index of its own.
 */
class OfflineMedia(private val context: Context) {

    /** Null when the external storage is unavailable, which is rare and temporary. */
    val directory: File? get() = context.getExternalFilesDir(DIRECTORY)

    private val _names = MutableStateFlow<Set<String>>(emptySet())

    /** File names present, so a composable can ask without touching the disk. */
    val names: StateFlow<Set<String>> = _names.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads the folder. Cheap, a few hundred names at most. */
    fun refresh() {
        _names.value = runCatching {
            directory?.listFiles()?.map { it.name }?.toSet().orEmpty()
        }.getOrDefault(emptySet())
    }

    /** The name this address is stored under, present or not. */
    fun nameFor(url: String): String = "${fingerprint(url)}.${extensionOf(url)}"

    fun has(url: String): Boolean = nameFor(url) in _names.value

    fun fileFor(url: String): File? {
        val name = nameFor(url)
        if (name !in _names.value) return null
        val file = directory?.resolve(name) ?: return null
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    /**
     * What an image loader should be given: the local file when there is one,
     * the address otherwise. Coil takes either.
     */
    fun modelFor(url: String): Any = fileFor(url) ?: url

    fun saved(): List<File> =
        runCatching {
            directory?.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
        }.getOrDefault(emptyList())

    fun totalBytes(): Long = saved().sumOf { it.length() }

    fun delete(name: String) {
        runCatching { directory?.resolve(name)?.delete() }
        refresh()
    }

    fun deleteAll() {
        runCatching { directory?.listFiles()?.forEach { it.delete() } }
        refresh()
    }

    /**
     * The extension is guessed from the address, because the file has to be
     * named before anything is downloaded. A playlist is not saveable and is
     * refused before it gets here.
     */
    private fun extensionOf(url: String): String {
        val lower = url.lowercase()
        val known = listOf("jpg", "jpeg", "png", "webp", "gif", "mp4")
        // LinkedIn's CDN names nothing: an address is a long identifier and a
        // signed query, with no extension anywhere in it. Falling back to
        // "jpg" rather than to "bin" because that is what these files are, and
        // because a gallery or a player asked to open a .bin gives up before
        // looking at the bytes.
        return known.firstOrNull { lower.contains(".$it") || lower.contains("%2e$it") } ?: "jpg"
    }

    private fun fingerprint(url: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(url.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        /**
         * Passed to getExternalFilesDir, so the folder is
         * /Android/data/com.linkedout.app/files/media. DownloadManager is given
         * the same string, which is how the two agree on one place.
         */
        const val DIRECTORY = "media"
    }
}
