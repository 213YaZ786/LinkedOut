package com.linkedout.app.feature.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkedout.app.core.media.OfflineMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Ported from MTGA's SavedMediaViewModel
// (https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/media/SavedMediaViewModel.kt#SavedMediaViewModel).
// LinkedOut has no ManualSaves: its media only land in the app's own folder, so what served the saves made by
// hand is left out, and without it the model needs no Application, it is a plain ViewModel.
// not ported: onCleared (it only unregistered the Downloads observer, there is none here)
// not ported: registerObserver (the ContentObserver on Downloads, for the saves made by hand)
// not ported: ManualSaves (no saves made by hand in LinkedOut: offline.manual.removePost, rescan and all)

/** One line of the screen's grid: an account's heading or one saved media. */
sealed interface SavedRow {
    val key: String
}

/** An account's heading. [appFiles] are the copies in the app's own folder, the only ones this screen deletes. */
data class SavedHeader(
    val handle: String,
    val files: Int,
    val appFiles: Int,
    val appBytes: Long
) : SavedRow {
    override val key: String get() = "h/$handle"
}

/**
 * A post's line above its cells: how many files, to open it or delete it. The [handle] is the account folder the
 * copies sit in, the post screen reads that account's cache first.
 */
data class SavedPostBar(
    val postId: String,
    val handle: String,
    val files: Int,
    val appBytes: Long
) : SavedRow {
    override val key: String get() = "p/$postId"
}

/**
 * One saved media. [thumb] is a picture to draw at the cell's size: the file of
 * a photo, the still of a video, null when a video has no still (the cell then
 * draws a plain play mark, no video frame is ever decoded for it).
 */
data class SavedCell(
    val postId: String,
    val handle: String,
    val position: Int,
    val thumb: String?,
    val video: Boolean
) : SavedRow {
    override val key: String get() = "c/$postId/$position"
}

data class SavedMediaUiState(
    val loading: Boolean = true,
    val rows: List<SavedRow> = emptyList(),
    val fileCount: Int = 0,
    val appBytes: Long = 0
)

/**
 * Reads the index and the folder and builds the rows of a lazy grid: thousands
 * of saved videos are one list of small rows, built off the main thread, and
 * only the cells in view ever draw anything.
 */
class SavedMediaViewModel(
    private val offline: OfflineMedia
) : ViewModel() {

    private val _state = MutableStateFlow(SavedMediaUiState())
    val state: StateFlow<SavedMediaUiState> = _state.asStateFlow()

    init {
        loadImages()
        // An automatic save lands in the app's folder: the index says so by its version, and the grid reads
        // again at each new value after the first.
        viewModelScope.launch {
            offline.version.drop(1).collect { loadImages() }
        }
    }

    /** The post's copies in the app's folder, stills too. */
    fun deletePost(postId: String) {
        viewModelScope.launch {
            offline.deleteWhere { it.postId == postId }
            reload()
        }
    }

    fun deleteAccount(handle: String) {
        viewModelScope.launch {
            offline.deleteWhere { it.handle == handle }
            reload()
        }
    }

    /**
     * Every copy and still of the folder, then the grid read again: the confirm button's body of LinkedOut's previous
     * screen (`offline.clear()` then `reload()`),
     * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/storage/SavedMediaScreen.kt#L88-L92
     */
    fun deleteAll() {
        viewModelScope.launch {
            offline.clear()
            reload()
        }
    }

    /** Loads the saved media into [state]. */
    fun loadImages() {
        viewModelScope.launch { reload() }
    }

    /** The folder and the index read once and built into the rows. */
    private suspend fun reload() {
        val auto = offline.entries()
        // Reads the folder again too.
        val stills = offline.stillPaths()
        val built = withContext(Dispatchers.Default) {
            val sized = auto.map { Auto(it.handle, it.postId, it.index, it.file.path, it.file.length()) }
            build(sized, stills)
        }
        _state.value = built.copy(loading = false)
    }

    /** One file of the app's folder with its size, read once. */
    internal data class Auto(val handle: String, val postId: String, val position: Int, val path: String, val bytes: Long)

    internal companion object {
        private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "webm", "mkv")

        /**
         * The rows of the grid, pure. Accounts with the most files first, then
         * each account's posts newest first (post ids grow with time, so a
         * bigger id is a later post), the media of a post in order. [stills] are
         * keyed "postId/position" like the store's own index.
         */
        fun build(auto: List<Auto>, stills: Map<String, String>): SavedMediaUiState {
            class Item(val handle: String, val cell: SavedCell, val appBytes: Long)

            val items = LinkedHashMap<String, Item>()
            for (file in auto) {
                val video = file.path.substringAfterLast('.').lowercase() in VIDEO_EXTENSIONS
                val thumb = if (video) stills["${file.postId}/${file.position}"] else file.path
                val cell = SavedCell(file.postId, file.handle, file.position, thumb, video)
                items[cell.key] = Item(file.handle, cell, file.bytes)
            }

            val rows = ArrayList<SavedRow>(items.size + 16)
            items.values.groupBy { it.handle }
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, List<Item>>> { it.value.size }.thenBy { it.key })
                .forEach { (handle, ofAccount) ->
                    rows += SavedHeader(handle, ofAccount.size, ofAccount.size, ofAccount.sumOf { it.appBytes })
                    ofAccount
                        .sortedWith(
                            compareByDescending<Item> { it.cell.postId.length }
                                .thenByDescending { it.cell.postId }
                                .thenBy { it.cell.position }
                        )
                        .groupBy { it.cell.postId }
                        .forEach { (postId, ofPost) ->
                            rows += SavedPostBar(postId, handle, ofPost.size, ofPost.sumOf { it.appBytes })
                            ofPost.forEach { rows += it.cell }
                        }
                }
            return SavedMediaUiState(
                loading = false,
                rows = rows,
                fileCount = items.size,
                appBytes = items.values.sumOf { it.appBytes }
            )
        }
    }
}
