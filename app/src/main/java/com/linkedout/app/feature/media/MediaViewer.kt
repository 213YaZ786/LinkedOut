package com.linkedout.app.feature.media

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.linkedout.app.core.debug.RequestLog
import com.linkedout.app.core.media.OfflineMedia
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.data.repository.VideoSources
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.BoldIconButton
import com.linkedout.app.ui.component.FloatingRoundButton
import com.linkedout.app.ui.component.GlassVideo
import com.linkedout.app.ui.component.MediaViewerFrame
import com.linkedout.app.ui.component.SafePlayer
import com.linkedout.app.ui.component.ZoomableImage
import com.linkedout.app.ui.component.rememberMediaPolicy
import com.linkedout.app.ui.icon.LinkedOutIcons
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.media3.common.MediaItem as PlayableItem

/**
 * Full screen viewer for the media of one post.
 *
 * Swipe sideways between attachments, pinch or double tap to zoom a photo,
 * swipe down (or back) to close, tap to hide the buttons and the player
 * controls. Videos play through Media3 ExoPlayer, GIFs loop muted like on X.
 * The download button saves the current attachment.
 *
 * The frame (dialog, pager, close, tap to hide) is the reader kit's
 * MediaViewerFrame; the pages and the download are this app's.
 */
@Composable
fun MediaViewer(
    media: List<MediaItem>,
    startIndex: Int,
    onDownload: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    if (media.isEmpty()) return

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // The save button needs the same address the player needed, and for a
    // video opened from a profile that address is not on the item: the
    // profile page names the video and carries only its cover. Resolving
    // it here rather than inside the page means the button no longer
    // depends on whether the player happened to resolve it first.
    val sources: VideoSources = koinInject()
    var resolving by remember { mutableStateOf(false) }

    MediaViewerFrame(
        count = media.size,
        startIndex = startIndex,
        onDismiss = onDismiss,
        actions = { current ->
            FloatingRoundButton(
                icon = LinkedOutIcons.Download,
                label = "Save to Downloads",
                onClick = {
                    if (resolving) return@FloatingRoundButton
                    val item = media[current]
                    if (item.playable || item.sourcePostId == null) {
                        onDownload(item)
                    } else {
                        resolving = true
                        scope.launch {
                            // Cached for the life of the process, so a
                            // video already watched costs no request here.
                            val found = sources.sourceFor(item.sourcePostId)
                            resolving = false
                            if (found == null) {
                                // Saving the cover under a video's name is
                                // worse than saying no: it looks like it
                                // worked until the file is opened.
                                Toast.makeText(
                                    context,
                                    "LinkedIn did not give this video's address",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onDownload(item.copy(downloadUrl = found, playable = true))
                            }
                        }
                    }
                }
            )
        }
    ) { index, active, onZoomChanged ->
        val item = media[index]
        when (item.type) {
            MediaType.PHOTO -> SavedPhoto(url = item.downloadUrl, onZoomChanged = onZoomChanged)
            MediaType.VIDEO, MediaType.GIF -> VideoPage(item = item, active = active)
        }
    }
}

/** A photo, from the saved copy when there is one. */
@Composable
private fun SavedPhoto(url: String, onZoomChanged: (Boolean) -> Unit) {
    val offline: OfflineMedia = koinInject()
    val saved by offline.names.collectAsState()
    val model = remember(url, saved) { offline.modelFor(url) }
    // The saved copy when there is one, so a picture already on the
    // phone opens with no network at all.
    ZoomableImage(model = model, onZoomChanged = onZoomChanged)
}

/**
 * One player per page, created when the page is composed and released when
 * it leaves. Plays only while its page is current and the app is in front.
 */
@Composable
private fun VideoPage(item: MediaItem, active: Boolean) {
    val context = LocalContext.current
    val log: RequestLog = koinInject()
    val sources: VideoSources = koinInject()
    val uriHandler = LocalUriHandler.current
    val policy = rememberMediaPolicy()
    val isGif = item.type == MediaType.GIF

    // A video the profile page named without carrying its address. The post's
    // own page has it, so it is fetched here, once, and only because the reader
    // opened this video. Everything below then behaves as if the address had
    // been there all along.
    var fetched by remember(item.downloadUrl) { mutableStateOf<String?>(null) }
    var looking by remember(item.downloadUrl) { mutableStateOf(false) }
    var lost by remember(item.downloadUrl) { mutableStateOf(false) }
    val source = if (item.playable) item.downloadUrl else fetched

    LaunchedEffect(item.downloadUrl, item.sourcePostId, item.playable) {
        if (item.playable || item.sourcePostId == null || fetched != null) return@LaunchedEffect
        looking = true
        val found = sources.sourceFor(item.sourcePostId)
        looking = false
        if (found == null) lost = true else fetched = found
    }

    if (source == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = item.previewUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                when {
                    looking -> "Fetching the video from its post."
                    lost || item.sourcePostId == null ->
                        "LinkedIn did not give this video's address, on the profile page or " +
                            "on the post's own."
                    else -> "Fetching the video from its post."
                },
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    var failed by remember(source) { mutableStateOf(false) }

    // On mobile data with Wi-Fi only on, nothing is fetched until the reader
    // taps play. Otherwise the player prepares as soon as the page exists.
    var started by remember(source) { mutableStateOf(!policy.hold) }
    var tappedPlay by remember(source) { mutableStateOf(false) }
    // GIFs have no sound. Videos follow the setting, and the button below.
    var muted by remember(source) { mutableStateOf(isGif || policy.startMuted) }

    val exo = remember(source) {
        SafePlayer.build(context).apply {
            setMediaItem(PlayableItem.fromUri(source))
            if (isGif) repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
                // Recorded with its address. Until now the failure was a
                // boolean and the reason died with it, so "the video does not
                // play" could be a refused request, an unsupported container
                // or a poster image handed to a player. The log now says which.
                log.record(
                    kind = RequestLog.Kind.MEDIA,
                    url = source,
                    outcome = "failed",
                    detail = "${error.errorCodeName}: ${error.message ?: "no message"}" +
                        (error.cause?.let { " / ${it::class.java.simpleName}: ${it.message}" } ?: "")
                )
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    LaunchedEffect(exo, started) {
        if (started && exo.playbackState == Player.STATE_IDLE) exo.prepare()
    }

    LaunchedEffect(exo, muted) {
        exo.volume = if (muted) 0f else 1f
    }

    // GIFs always loop. Videos start alone only when autoplay is on, or when
    // the reader just tapped play. With autoplay off the controls' own play
    // button does the rest.
    val playsAlone = isGif || policy.autoplay || tappedPlay
    LifecycleResumeEffect(exo, active, started, playsAlone) {
        if (started && exo.playbackState == Player.STATE_IDLE) exo.prepare()
        if (active && started && playsAlone) exo.play()
        onPauseOrDispose { exo.pause() }
    }
    // Out of sight it stops loading too (a paused player goes on downloading); it goes on from the same place.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { exo.stop() }

    if (failed) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("This video could not be played here.", color = Color.White)
            BoldButton(onClick = { uriHandler.openUri(source) }, filled = true) {
                Text("Open in browser")
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        GlassVideo(
            player = exo,
            muted = muted,
            onMutedChange = { muted = it },
            controls = item.type == MediaType.VIDEO && started
        )

        if (!started) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BoldIconButton(
                    onClick = {
                        started = true
                        tappedPlay = true
                    },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        LinkedOutIcons.Play,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Text(
                    "Mobile data, Wi-Fi only is on",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

    }
}
