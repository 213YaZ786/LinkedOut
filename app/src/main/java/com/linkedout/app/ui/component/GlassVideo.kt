package com.linkedout.app.ui.component

import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.linkedout.app.ui.glass.LocalGlass
import com.linkedout.app.ui.glass.LocalGlassBackdrop
import com.linkedout.app.ui.glass.glassFloating
import com.linkedout.app.ui.glass.glassSource
import com.linkedout.app.ui.glass.rememberGlassBackdrop
import com.linkedout.app.ui.glass.rememberGlassLook
import kotlinx.coroutines.delay

/**
 * A video in the app's own player: the picture at its own shape on black,
 * and over it the controls as panes of glass in the theme's colours, the
 * picture showing through them as the lists do under the dock. Media3's
 * own controls were a grey bar and menus of another app.
 *
 * A tap shows or hides the controls, a double tap on either half goes back
 * or forward ten seconds. While it plays they leave after a few seconds.
 * [controls] false (a GIF) leaves the bare picture.
 *
 * The picture is a TextureView, not a SurfaceView: a surface is drawn by
 * the system beside the window, so the glass would find nothing to bend.
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.sh.
 */
@Composable
fun GlassVideo(
    player: ExoPlayer,
    muted: Boolean,
    onMutedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    controls: Boolean = true
) {
    val look = LocalGlass.current
    val backdrop = rememberGlassBackdrop()
    val haptics = rememberHaptics()

    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    var state by remember(player) { mutableIntStateOf(player.playbackState) }
    var ratio by remember(player) { mutableFloatStateOf(0f) }
    var speed by remember(player) { mutableFloatStateOf(player.playbackParameters.speed) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                state = playbackState
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                speed = playbackParameters.speed
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    var shown by remember(player) { mutableStateOf(true) }
    var menu by remember(player) { mutableStateOf(false) }
    // Each touch restarts the wait before the controls leave.
    var touches by remember(player) { mutableIntStateOf(0) }
    val ended = state == Player.STATE_ENDED
    LaunchedEffect(shown, playing, menu, touches) {
        if (shown && playing && !menu) {
            delay(HIDE_AFTER_MS)
            shown = false
        }
    }
    LaunchedEffect(ended) { if (ended) shown = true }

    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var dragging by remember(player) { mutableStateOf<Float?>(null) }
    LaunchedEffect(player, shown, playing, state) {
        do {
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            delay(POSITION_EVERY_MS)
        } while (shown && playing)
    }

    // Which way the last double tap went, for its bubble: -1, 1, or 0.
    var jumped by remember(player) { mutableIntStateOf(0) }
    var jumps by remember(player) { mutableIntStateOf(0) }
    LaunchedEffect(jumps) {
        if (jumped != 0) {
            delay(JUMP_BUBBLE_MS)
            jumped = 0
        }
    }

    fun jump(by: Long) {
        val target = (player.currentPosition + by).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        player.seekTo(target)
        position = target
        touches++
    }

    fun playOrPause() {
        when {
            ended -> {
                player.seekTo(0L)
                player.play()
            }
            player.isPlaying -> player.pause()
            else -> {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        }
        touches++
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (look != null) Modifier.glassSource(backdrop, look) else Modifier)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            key(player) {
                AndroidView(
                    factory = { context -> TextureView(context).also(player::setVideoTextureView) },
                    onRelease = player::clearVideoTextureView,
                    modifier = if (ratio > 0f) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize()
                )
            }
        }

        if (!controls) return@Box

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(player) {
                    detectTapGestures(
                        onTap = {
                            shown = !shown
                            menu = false
                            touches++
                        },
                        onDoubleTap = { at ->
                            val back = at.x < size.width / 2f
                            haptics.tick()
                            jump(if (back) -JUMP_MS else JUMP_MS)
                            jumped = if (back) -1 else 1
                            jumps++
                        }
                    )
                }
        )

        CompositionLocalProvider(LocalGlassBackdrop provides backdrop.takeIf { look != null }) {
            // The bubble of a double tap, on the side that was tapped.
            AnimatedVisibility(
                visible = jumped != 0,
                enter = fadeIn() + scaleIn(initialScale = 0.7f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier
                    .align(if (jumped < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 40.dp)
            ) {
                GlassPane(shape = CircleShape) {
                    Text(
                        if (jumped < 0) "−10 s" else "+10 s",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = shown,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundGlass(PlayerIcons.Replay10, "Back 10 seconds", 52.dp) {
                        haptics.tick()
                        jump(-JUMP_MS)
                    }
                    RoundGlass(
                        icon = when {
                            ended -> PlayerIcons.Replay
                            playing -> PlayerIcons.Pause
                            else -> PlayerIcons.Play
                        },
                        label = when {
                            ended -> "Play again"
                            playing -> "Pause"
                            else -> "Play"
                        },
                        size = 76.dp,
                        accent = true
                    ) {
                        haptics.tick()
                        playOrPause()
                    }
                    RoundGlass(PlayerIcons.Forward10, "Forward 10 seconds", 52.dp) {
                        haptics.tick()
                        jump(JUMP_MS)
                    }
                }
            }

            AnimatedVisibility(
                visible = shown,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedVisibility(
                        visible = menu,
                        enter = fadeIn() + scaleIn(initialScale = 0.85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
                        exit = fadeOut() + scaleOut(targetScale = 0.9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f))
                    ) {
                        GlassPane(shape = RoundedCornerShape(22.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                SPEEDS.forEach { choice ->
                                    val chosen = choice == speed
                                    Text(
                                        "${speedLabel(choice)}×",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                                        color = if (chosen) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .clickable(role = Role.Button) {
                                                haptics.tick()
                                                player.setPlaybackSpeed(choice)
                                                menu = false
                                                touches++
                                            }
                                            .padding(horizontal = 26.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                    GlassPane(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            val shownAt = dragging?.let { (it * duration).toLong() } ?: position
                            Text(clock(shownAt), style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                                onValueChange = {
                                    dragging = it
                                    touches++
                                },
                                onValueChangeFinished = {
                                    dragging?.let { fraction ->
                                        val target = (fraction * duration).toLong()
                                        player.seekTo(target)
                                        position = target
                                    }
                                    dragging = null
                                    touches++
                                },
                                enabled = duration > 0,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = LocalContentColor.current.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                            )
                            Text(clock(duration), style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${speedLabel(speed)}×",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button, onClickLabel = "Playback speed") {
                                        haptics.tick()
                                        menu = !menu
                                        touches++
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button, onClickLabel = if (muted) "Turn sound on" else "Mute") {
                                        haptics.tick()
                                        onMutedChange(!muted)
                                        touches++
                                    }
                            ) {
                                Icon(
                                    if (muted) PlayerIcons.VolumeOff else PlayerIcons.VolumeOn,
                                    contentDescription = if (muted) "Sound off" else "Sound on",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The viewer's colours. A picture or a film is looked at on black, so the
 * night of the same Material You scheme is used whatever the hour, and the
 * glass is made from it: light text and accent on dark panes, as a player is
 * expected to look, instead of light panes greyed by the black around them.
 */
@Composable
fun DarkGround(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val night = remember(scheme) { scheme.onDarkGround() }
    val look = rememberGlassLook(night, enabled = LocalGlass.current != null)
    MaterialTheme(colorScheme = night) {
        CompositionLocalProvider(LocalGlass provides look, content = content)
    }
}

/** A day scheme turned to its night through its own inverse roles; a night one as it is. */
private fun ColorScheme.onDarkGround(): ColorScheme =
    if (background.luminance() < 0.5f) {
        this
    } else {
        copy(
            primary = inversePrimary,
            onPrimary = onPrimaryContainer,
            primaryContainer = onPrimaryContainer,
            onPrimaryContainer = primaryContainer,
            secondaryContainer = onSecondaryContainer,
            onSecondaryContainer = secondaryContainer,
            background = inverseSurface,
            onBackground = inverseOnSurface,
            surface = inverseSurface,
            onSurface = inverseOnSurface,
            onSurfaceVariant = inverseOnSurface.copy(alpha = 0.8f),
            surfaceContainerLowest = inverseSurface,
            surfaceContainerLow = inverseSurface,
            surfaceContainer = inverseSurface,
            surfaceContainerHigh = inverseSurface,
            surfaceContainerHighest = inverseSurface,
            inverseSurface = surface,
            inverseOnSurface = onSurface,
            inversePrimary = primary
        )
    }

/** A round pane of glass with one icon, pressed in when touched. */
@Composable
private fun RoundGlass(icon: ImageVector, label: String, size: Dp, accent: Boolean = false, onClick: () -> Unit) {
    GlassPane(shape = CircleShape, accent = accent, onClick = onClick, modifier = Modifier.size(size)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(size * 0.46f))
    }
}

/**
 * A pane of the player's glass: the picture bent and frosted under it, so
 * its text stays readable on any frame. Without glass, a plain zone.
 */
@Composable
private fun GlassPane(
    shape: Shape,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val look = LocalGlass.current
    val backdrop = LocalGlassBackdrop.current
    val scheme = MaterialTheme.colorScheme
    val dark = look?.dark ?: (scheme.background.red + scheme.background.green + scheme.background.blue < 1.5f)
    val color = if (accent && !dark) scheme.onPrimaryContainer else scheme.onSurface
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    CompositionLocalProvider(LocalContentColor provides color) {
        Box(
            contentAlignment = Alignment.Center,
            content = content,
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .then(
                    if (look != null && backdrop != null) {
                        // The accent dense enough that its icon reads on a bright frame too.
                        Modifier.glassFloating(backdrop, shape, look, tint = if (accent) scheme.primaryContainer.copy(alpha = 0.8f) else look.zoneTint, lens = 1.3f)
                    } else {
                        Modifier.background(if (accent) scheme.primaryContainer else scheme.surfaceContainerHigh.copy(alpha = 0.92f), shape)
                    }
                )
                .then(
                    if (onClick != null) {
                        Modifier.clickable(interactionSource = presses, indication = null, role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    }
                )
        )
    }
}

private fun clock(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private const val HIDE_AFTER_MS = 3_000L
private const val POSITION_EVERY_MS = 250L
private const val JUMP_MS = 10_000L
private const val JUMP_BUBBLE_MS = 700L

/** Material icons (Apache 2.0), from google/material-design-icons, 24 dp grid. */
private object PlayerIcons {
    val Play by lazy { build("M8 5v14l11-7z") }
    val Pause by lazy { build("M6 19h4V5H6v14zm8-14v14h4V5h-4z") }
    val Replay by lazy { build("M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z") }
    val Replay10 by lazy {
        build(
            "M11.99,5V1l-5,5l5,5V7c3.31,0,6,2.69,6,6s-2.69,6-6,6s-6-2.69-6-6h-2c0,4.42,3.58,8,8,8s8-3.58,8-8S16.41,5,11.99,5z",
            "M10.89,16h-0.85v-3.26l-1.01,0.31v-0.69l1.77-0.63h0.09V16z",
            "M15.17,14.24c0,0.32-0.03,0.6-0.1,0.82s-0.17,0.42-0.29,0.57s-0.28,0.26-0.45,0.33s-0.37,0.1-0.59,0.1 " +
                "s-0.41-0.03-0.59-0.1s-0.33-0.18-0.46-0.33s-0.23-0.34-0.3-0.57s-0.11-0.5-0.11-0.82V13.5c0-0.32,0.03-0.6,0.1-0.82 " +
                "s0.17-0.42,0.29-0.57s0.28-0.26,0.45-0.33s0.37-0.1,0.59-0.1s0.41,0.03,0.59,0.1c0.18,0.07,0.33,0.18,0.46,0.33 " +
                "s0.23,0.34,0.3,0.57s0.11,0.5,0.11,0.82V14.24z M14.32,13.38c0-0.19-0.01-0.35-0.04-0.48s-0.07-0.23-0.12-0.31 " +
                "s-0.11-0.14-0.19-0.17s-0.16-0.05-0.25-0.05s-0.18,0.02-0.25,0.05s-0.14,0.09-0.19,0.17s-0.09,0.18-0.12,0.31 " +
                "s-0.04,0.29-0.04,0.48v0.97c0,0.19,0.01,0.35,0.04,0.48s0.07,0.24,0.12,0.32s0.11,0.14,0.19,0.17s0.16,0.05,0.25,0.05 " +
                "s0.18-0.02,0.25-0.05s0.14-0.09,0.19-0.17s0.09-0.19,0.11-0.32s0.04-0.29,0.04-0.48V13.38z"
        )
    }
    val Forward10 by lazy {
        build(
            "M18,13c0,3.31-2.69,6-6,6s-6-2.69-6-6s2.69-6,6-6v4l5-5l-5-5v4c-4.42,0-8,3.58-8,8c0,4.42,3.58,8,8,8s8-3.58,8-8H18z",
            "M12.25,13.44v0.74c0,1.9,1.31,1.82,1.44,1.82c0.14,0,1.44,0.09,1.44-1.82v-0.74c0-1.9-1.31-1.82-1.44-1.82 " +
                "C13.55,11.62,12.25,11.53,12.25,13.44z M14.29,13.32v0.97c0,0.77-0.21,1.03-0.59,1.03c-0.38,0-0.6-0.26-0.6-1.03v-0.97 " +
                "c0-0.75,0.22-1.01,0.59-1.01C14.07,12.3,14.29,12.57,14.29,13.32z",
            // The "1", a polygon in the source file.
            "M10.86,15.94L10.86,11.67L10.77,11.67L9,12.3L9,12.99L10.01,12.68L10.01,15.94z"
        )
    }
    val VolumeOn by lazy {
        build("M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z")
    }
    val VolumeOff by lazy {
        build("M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z")
    }

    private fun build(vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        paths.forEach { builder.addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) }
        return builder.build()
    }
}
