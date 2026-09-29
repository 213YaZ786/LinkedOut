package com.linkedout.app.ui.component

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.linkedout.app.R
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.Post
import com.linkedout.app.data.marks.PostMarks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * A long press on a post opens a small pill of glass where the finger is,
 * with what can be done to the post: like, archive, share, and save its
 * media when it has some. Likes and archives stay on the phone, see
 * PostMarks. Each button answers with its own small motion; a like sends
 * hearts floating up out of the pill.
 *
 * The post card puts [tracker] on its outer box, calls [open] on a long
 * press, and places [PostActionsOverlay] inside that box.
 */
@Stable
class PostActions internal constructor(
    val post: Post,
    private val marks: PostMarks,
    private val onDownload: (MediaItem) -> Unit,
    private val context: Context,
    private val haptics: Haptics
) {
    /** Where the finger last went down, in the card's own coordinates. */
    internal var pressAt by mutableStateOf(Offset.Zero)
    internal var shown by mutableStateOf(false)
    internal var heartsAt by mutableStateOf<Offset?>(null)

    val tracker: Modifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            pressAt = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position
        }
    }

    fun open() {
        haptics.firm()
        shown = true
    }

    internal fun like() {
        if (!marks.marks.value.isLiked(post.id)) heartsAt = pressAt
        marks.toggleLike(post)
    }

    internal fun archive() = marks.toggleArchive(post)

    internal fun share() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, post.permalink)
        }
        runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    internal val canSave: Boolean get() = post.media.isNotEmpty()

    internal fun save() = post.media.forEach(onDownload)
}

@Composable
fun rememberPostActions(post: Post, onDownload: (MediaItem) -> Unit): PostActions {
    val marks: PostMarks = koinInject()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    return remember(post.id, post) { PostActions(post, marks, onDownload, context, haptics) }
}

private enum class Motion { POP, DROP, WIGGLE, BOUNCE }

@Composable
fun PostActionsOverlay(actions: PostActions) {
    val marks: PostMarks = koinInject()
    val state by marks.marks.collectAsState()
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.roundToPx() }

    if (actions.shown) {
        val at = IntOffset(actions.pressAt.x.toInt(), actions.pressAt.y.toInt())
        Popup(
            popupPositionProvider = remember(at) { AtPoint(at, 0.5f, 0.5f, margin) },
            onDismissRequest = { actions.shown = false },
            properties = PopupProperties(focusable = true)
        ) {
            val appear = remember { MutableTransitionState(false) }.apply { targetState = true }
            AnimatedVisibility(
                visibleState = appear,
                enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.5f) +
                    expandVertically(tween(180), expandFrom = Alignment.CenterVertically)
            ) {
                val liked = state.isLiked(actions.post.id)
                val archived = state.isArchived(actions.post.id)
                val close = { actions.shown = false }
                ZoneSurface(shape = RoundedCornerShape(50), shadowElevation = 6.dp) {
                    Column(
                        modifier = Modifier.padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // The hearts leave the pill on the tap itself, the pill
                        // closes once the heart has popped.
                        PillButton(ActionIcons.heart, if (liked) "Unlike" else "Like", if (liked) HEART_PINK else null, Motion.POP, onPress = actions::like) {
                            close()
                        }
                        PillButton(ActionIcons.archive, if (archived) "Unarchive" else "Archive", if (archived) MaterialTheme.colorScheme.primary else null, Motion.DROP) {
                            actions.archive()
                            close()
                        }
                        PillButton(ActionIcons.share, "Share", null, Motion.WIGGLE) {
                            close()
                            actions.share()
                        }
                        if (actions.canSave) {
                            PillButton(ActionIcons.save, "Save media", null, Motion.BOUNCE) {
                                actions.save()
                                close()
                            }
                        }
                    }
                }
            }
        }
    }

    actions.heartsAt?.let { from ->
        val at = IntOffset(from.x.toInt(), from.y.toInt())
        Popup(
            popupPositionProvider = remember(at) { AtPoint(at, 0.5f, 0.92f, 0) },
            properties = PopupProperties(focusable = false, clippingEnabled = false)
        ) {
            FloatingHearts(onEnd = { actions.heartsAt = null })
        }
    }
}

/** A button of the pill: its motion plays, then the action runs. */
@Composable
private fun PillButton(
    icon: ImageVector,
    label: String,
    tint: Color?,
    motion: Motion,
    onPress: () -> Unit = {},
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val drop = remember { Animatable(0f) }
    val turn = remember { Animatable(0f) }
    var busy by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            if (busy) return@IconButton
            busy = true
            onPress()
            scope.launch {
                when (motion) {
                    Motion.POP -> {
                        scale.animateTo(1.4f, tween(110))
                        scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
                    }
                    Motion.DROP -> drop.animateTo(1f, tween(180))
                    Motion.WIGGLE -> {
                        turn.animateTo(-20f, tween(70))
                        turn.animateTo(20f, tween(110))
                        turn.animateTo(0f, tween(70))
                    }
                    Motion.BOUNCE -> {
                        drop.animateTo(0.6f, tween(110))
                        drop.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
                    }
                }
                onDone()
            }
        },
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp).graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationY = drop.value * 12.dp.toPx()
                rotationZ = turn.value
                if (motion == Motion.DROP) alpha = 1f - drop.value * 0.8f
            }
        )
    }
}

/**
 * Glass hearts rising from where the pill was, each on its own sway, a
 * little larger as they rise and fading at the top.
 */
@Composable
private fun FloatingHearts(onEnd: () -> Unit) {
    val clock = remember { Animatable(0f) }
    val hearts = remember {
        List(HEARTS) {
            Heart(
                delay = Random.nextFloat() * 0.3f,
                span = 0.6f + Random.nextFloat() * 0.25f,
                size = 22f + Random.nextFloat() * 20f,
                startX = (Random.nextFloat() - 0.5f) * 56f,
                sway = 10f + Random.nextFloat() * 20f,
                turns = 0.6f + Random.nextFloat() * 0.8f,
                phase = Random.nextFloat() * 2f * PI.toFloat()
            )
        }
    }
    LaunchedEffect(Unit) {
        clock.animateTo(1f, tween(HEARTS_MS, easing = LinearEasing))
        delay(16)
        onEnd()
    }
    val heart = ImageBitmap.imageResource(R.drawable.heart_glass)
    Canvas(Modifier.size(width = 200.dp, height = 300.dp)) {
        val unit = 1.dp.toPx()
        hearts.forEach { h ->
            val p = ((clock.value - h.delay) / h.span).coerceIn(0f, 1f)
            if (p <= 0f || p >= 1f) return@forEach
            val side = h.size * unit * (0.6f + 0.4f * (p * 3f).coerceAtMost(1f))
            val x = size.width / 2 + (h.startX + sin(p * 2f * PI.toFloat() * h.turns + h.phase) * h.sway) * unit
            val y = size.height - p * (size.height - side)
            val alpha = when {
                p < 0.08f -> p / 0.08f
                p > 0.65f -> (1f - p) / 0.35f
                else -> 1f
            }
            drawImage(
                heart,
                dstOffset = IntOffset((x - side / 2).toInt(), (y - side).toInt()),
                dstSize = IntSize(side.toInt(), side.toInt()),
                alpha = alpha,
                filterQuality = FilterQuality.High
            )
        }
    }
}

private class Heart(
    val delay: Float,
    val span: Float,
    val size: Float,
    val startX: Float,
    val sway: Float,
    val turns: Float,
    val phase: Float
)

/** Places a popup's point ([fx], [fy] of its size) on [point] of its anchor, kept on screen. */
private class AtPoint(val point: IntOffset, val fx: Float, val fy: Float, val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = anchorBounds.left + point.x - (popupContentSize.width * fx).toInt()
        val y = anchorBounds.top + point.y - (popupContentSize.height * fy).toInt()
        if (margin == 0) return IntOffset(x, y)
        return IntOffset(
            x.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
            y.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin))
        )
    }
}

private const val HEARTS = 9
private const val HEARTS_MS = 1800
private val HEART_PINK = Color(0xFFEC4F86)
private const val HEART_PATH =
    "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09" +
        "C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z"

/** The pill's icons, drawn here so every app has the same ones. */
private object ActionIcons {
    val heart by lazy { icon("Heart", HEART_PATH) }
    val archive by lazy {
        icon(
            "Archive",
            "M20.54,5.23l-1.39,-1.68C18.88,3.21 18.47,3 18,3H6c-0.47,0 -0.88,0.21 -1.16,0.55L3.46,5.23" +
                "C3.17,5.57 3,6.02 3,6.5V19c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V6.5c0,-0.48 -0.17,-0.93 -0.46,-1.27z" +
                "M12,17.5L6.5,12H10v-2h4v2h3.5L12,17.5zM5.12,5l0.81,-1h12l0.94,1H5.12z"
        )
    }
    val share by lazy {
        icon(
            "Share",
            "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7" +
                "s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3" +
                "s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9" +
                "c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16" +
                "c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92 1.61,0 2.92,-1.31 2.92,-2.92" +
                "s-1.31,-2.92 -2.92,-2.92z"
        )
    }
    val save by lazy { icon("Save", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z") }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        }.build()
}
