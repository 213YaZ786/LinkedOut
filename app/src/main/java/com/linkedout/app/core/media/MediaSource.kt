package com.linkedout.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType

/**
 * The saved media folder, for the composables that show media.
 *
 * Null when nothing provided it, which keeps previews and tests working: the
 * helper below then simply answers with the network address, which is what
 * the app did before any of this existed.
 *
 * Ported from MTGA, https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/MediaSource.kt#LocalOfflineMedia
 */
val LocalOfflineMedia = compositionLocalOf<OfflineMedia?> { null }

/**
 * Where a picture should be loaded from and where a video should be played
 * from, saved copy first.
 *
 * [preview] and [playback] differ for a video: the file on disk is the video,
 * the preview is its still, saved beside it when automatic saving ran, else
 * the network address, which Coil's own picture cache answers offline for a
 * post that was seen. For a photo both point at the same saved file.
 *
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/MediaSource.kt#MediaSource
 */
data class MediaSource(val preview: String, val playback: String, val offline: Boolean)

/**
 * Reads the in memory index, so this costs a map lookup and is safe to call
 * for every attachment of every visible post. Keyed on the store's version, so
 * a file that lands while the screen is open is used at once.
 *
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/MediaSource.kt#rememberMediaSource
 */
@Composable
fun rememberMediaSource(postId: String, position: Int, item: MediaItem): MediaSource {
    val store = LocalOfflineMedia.current
    val version = store?.version?.collectAsState()?.value ?: 0
    return remember(store, version, postId, position, item.downloadUrl, item.previewUrl) {
        resolve(store, postId, position, item)
    }
}

/**
 * The lookup of [rememberMediaSource] out of Compose, so it can be tested.
 *
 * The rule is AntennaPod's: the local file when it is available, else the
 * stream (MediaItemAdapter.fromPlayable, `if (playable.localFileAvailable())
 * localPlaybackUri = playable.getLocalFileUrl() else playable.getStreamUrl()`):
 * https://github.com/AntennaPod/AntennaPod/blob/develop/playback/base/src/main/java/de/danoeh/antennapod/playback/base/MediaItemAdapter.java#L85-L90
 * Extended with the still, as MTGA's resolve:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/MediaSource.kt#resolve
 */
internal fun resolve(store: OfflineMedia?, postId: String, position: Int, item: MediaItem): MediaSource {
    val local = store?.localPath(postId, position)
    val isPicture = item.type == MediaType.PHOTO
    // A saved photo is its own preview. A saved video is not: its still when
    // one was saved, else the network address, which fails quietly offline
    // exactly as it would have anyway.
    val still = if (isPicture) null else store?.localStill(postId, position)
    return when {
        local == null -> MediaSource(preview = still ?: item.previewUrl, playback = item.downloadUrl, offline = false)
        isPicture -> MediaSource(preview = local, playback = local, offline = true)
        else -> MediaSource(preview = still ?: item.previewUrl, playback = local, offline = true)
    }
}
