package com.linkedout.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.core.model.Post
import androidx.media3.common.MediaItem as PlayableItem

/**
 * False while a list is out of sight: another tab, or the guide on top.
 * Set by the tab shell, true everywhere else.
 */
val LocalInlinePlaybackAllowed = compositionLocalOf { true }

/**
 * Id of the post whose first video or GIF plays inside the list, muted and
 * looping. Null plays nothing. One at a time, so scrolling never costs more
 * than one stream.
 */
val LocalInlinePlaying = compositionLocalOf<String?> { null }

/** The attachment that plays inline for this post, if any. */
fun Post.inlinePlayable(): MediaItem? = media.firstOrNull { it.type != MediaType.PHOTO }

/**
 * Picks the post to play: the first one with a video or GIF that is mostly
 * on screen. Nothing plays when autoplay is off, when Wi-Fi only holds media
 * on a metered network, when the list is out of sight, or while [paused]
 * (the full screen viewer is open).
 */
@Composable
fun rememberInlineTarget(
    listState: LazyListState,
    posts: List<Post>,
    keyOf: (Post) -> Any,
    paused: Boolean,
    /** Changes whenever [keyOf] would give different keys, a profile tab for example. */
    keySpace: Any? = null
): String? {
    val policy = rememberMediaPolicy()
    val allowed = LocalInlinePlaybackAllowed.current && !paused && policy.autoplay && !policy.hold
    val currentKeyOf by rememberUpdatedState(keyOf)
    val playable = remember(posts, keySpace) {
        posts.filter { it.inlinePlayable() != null }.associateBy { currentKeyOf(it) }
    }
    val target by remember(listState, playable) {
        derivedStateOf {
            val info = listState.layoutInfo
            val top = info.viewportStartOffset
            // The bottom padding is where the dock floats, not visible content.
            val bottom = info.viewportEndOffset - info.afterContentPadding
            val needed = (bottom - top) / 2
            info.visibleItemsInfo.firstOrNull { item ->
                if (item.key !in playable) return@firstOrNull false
                val shown = minOf(item.offset + item.size, bottom) - maxOf(item.offset, top)
                // Mostly visible, or at least half the screen for a tall post.
                shown >= minOf(item.size * 6 / 10, needed)
            }?.let { playable[it.key]?.id }
        }
    }
    return if (allowed) target else null
}

/**
 * A silent, looping player laid over the preview picture. The picture stays
 * underneath until the first frame arrives, and a tap anywhere opens the full
 * screen viewer as before. It never takes audio focus, so music keeps playing.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun InlineVideo(url: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var failed by remember(url) { mutableStateOf(false) }
    val exo = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayableItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
        }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Paused with the app in the background or a screen pushed on top.
    LifecycleResumeEffect(exo) {
        exo.play()
        onPauseOrDispose { exo.pause() }
    }

    // A failed stream simply leaves the preview picture, as before.
    if (failed) return

    Box(modifier) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    player = exo
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )
        // Taps belong to the post, not to the video view.
        Box(Modifier.fillMaxSize().clickable(onClick = onClick))
    }
}
