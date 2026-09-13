package com.linkedout.app.feature.media

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.size
import com.linkedout.app.ui.component.rememberMediaPolicy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.linkedout.app.core.debug.RequestLog
import com.linkedout.app.data.repository.VideoSources
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.ui.icon.LinkedOutIcons
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.media3.common.MediaItem as PlayableItem
import org.koin.compose.koinInject

/**
 * Full screen viewer for the media of one post.
 *
 * Swipe sideways between attachments, pinch or double tap to zoom a photo,
 * swipe down (or back) to close. Videos play through Media3 ExoPlayer, GIFs
 * loop muted like on X. The download button saves the current attachment.
 *
 * Always dark, whatever the theme: media is judged against black, and a light
 * frame around a photo changes how it looks.
 */
@Composable
fun MediaViewer(
    media: List<MediaItem>,
    startIndex: Int,
    onDownload: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    if (media.isEmpty()) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val pager = rememberPagerState(
            initialPage = startIndex.coerceIn(0, media.lastIndex),
            pageCount = { media.size }
        )
        var zoomed by remember { mutableStateOf(false) }
        val dragY = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        // The save button needs the same address the player needed, and for a
        // video opened from a profile that address is not on the item: the
        // profile page names the video and carries only its cover. Resolving
        // it here rather than inside the page means the button no longer
        // depends on whether the player happened to resolve it first.
        val sources: VideoSources = koinInject()
        var resolving by remember { mutableStateOf(false) }
        val fade = (1f - abs(dragY.value) / 1200f).coerceIn(0.3f, 1f)

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = fade))
        ) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, dragY.value.roundToInt()) }
                    .draggable(
                        orientation = Orientation.Vertical,
                        enabled = !zoomed,
                        state = rememberDraggableState { delta ->
                            scope.launch { dragY.snapTo(dragY.value + delta) }
                        },
                        onDragStopped = { velocity ->
                            if (abs(dragY.value) > DISMISS_DISTANCE || abs(velocity) > DISMISS_VELOCITY) {
                                onDismiss()
                            } else {
                                dragY.animateTo(0f)
                            }
                        }
                    )
            ) { page ->
                val item = media[page]
                val active = pager.currentPage == page
                when (item.type) {
                    MediaType.PHOTO -> ZoomableImage(
                        url = item.downloadUrl,
                        onZoomChanged = { if (active) zoomed = it }
                    )
                    MediaType.VIDEO, MediaType.GIF -> VideoPage(item = item, active = active)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(LinkedOutIcons.Close, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    if (media.size > 1) "${pager.currentPage + 1} / ${media.size}" else "",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    enabled = !resolving,
                    onClick = {
                        val item = media[pager.currentPage]
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
                ) {
                    Icon(LinkedOutIcons.Download, contentDescription = "Save to Downloads", tint = Color.White)
                }
            }
        }
    }
}

/**
 * Pinch to zoom, pan while zoomed, double tap to toggle. At rest a single
 * finger is left alone, so the pager and swipe to dismiss still get it.
 */
@Composable
private fun ZoomableImage(url: String, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(candidate: Offset): Offset {
        val maxX = size.width * (scale - 1f) / 2f
        val maxY = size.height * (scale - 1f) / 2f
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun update(newScale: Float, newOffset: Offset) {
        val wasZoomed = scale > 1f
        scale = newScale
        offset = if (newScale <= 1f) Offset.Zero else clamp(newOffset)
        if (wasZoomed != scale > 1f) onZoomChanged(scale > 1f)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            update(1f, Offset.Zero)
                        } else {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            update(DOUBLE_TAP_SCALE, (center - tap) * (DOUBLE_TAP_SCALE - 1f))
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers > 1 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            update((scale * zoom).coerceIn(1f, MAX_SCALE), offset + pan)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
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
        ExoPlayer.Builder(context).build().apply {
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
        if (active && started && playsAlone) exo.play()
        onPauseOrDispose { exo.pause() }
    }

    if (failed) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("This video could not be played here.", color = Color.White)
            TextButton(onClick = { uriHandler.openUri(source) }) {
                Text("Open in browser")
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    player = exo
                    useController = item.type == MediaType.VIDEO
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!started) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        started = true
                        tappedPlay = true
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
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

        if (!isGif) {
            // Below the viewer's own top bar, clear of the player controls.
            IconButton(
                onClick = { muted = !muted },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .systemBarsPadding()
                    .padding(top = 52.dp, end = 8.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    if (muted) LinkedOutIcons.VolumeOff else LinkedOutIcons.VolumeOn,
                    contentDescription = if (muted) "Turn sound on" else "Mute",
                    tint = Color.White
                )
            }
        }
    }
}

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val DISMISS_DISTANCE = 300f
private const val DISMISS_VELOCITY = 2500f
