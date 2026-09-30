package com.linkedout.app.ui.component

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.onGloballyPositioned
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
import kotlin.math.abs
import kotlin.math.cos

/**
 * A long press on a post opens a small pill of glass where the finger is,
 * with what can be done to the post: like, archive, share, and save its
 * media when it has some. Likes and archives stay on the phone, see
 * PostMarks. Each button answers with its own small motion; a like spins
 * the star and pops one glass star above it, spinning on the spot.
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
    /** Where the popped star shows, in the card's coordinates, while it plays. */
    internal var starAt by mutableStateOf<Offset?>(null)
    /**
     * The centre of the pill's star button and the card's corner, both on
     * the screen: the pill is a popup, a window of its own, so only the
     * screen relates the button to the card.
     */
    internal var starButton = Offset.Zero
    internal var cardCorner = Offset.Zero

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
        if (!marks.marks.value.isLiked(post.id)) starAt = starButton - cardCorner
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

private enum class Motion { FLIP, DROP, WIGGLE, BOUNCE }

@Composable
fun PostActionsOverlay(actions: PostActions) {
    val marks: PostMarks = koinInject()
    val state by marks.marks.collectAsState()
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.roundToPx() }

    // Sits at the card's corner, the popups' anchor, to place the star.
    Spacer(Modifier.onGloballyPositioned { actions.cardCorner = it.positionOnScreen() })

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
                        // The star pops on the tap itself, the pill closes
                        // once its own star has spun round.
                        PillButton(
                            ActionIcons.star, if (liked) "Unlike" else "Like", if (liked) STAR_GOLD else null, Motion.FLIP,
                            modifier = Modifier.onGloballyPositioned {
                                actions.starButton = it.positionOnScreen() + Offset(it.size.width / 2f, it.size.height / 2f)
                            },
                            onPress = actions::like
                        ) {
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

    actions.starAt?.let { from ->
        // The bottom of the star's canvas, half a star below the button's centre.
        val foot = with(density) { (STAR_SIDE / 2).roundToPx() }
        val at = IntOffset(from.x.toInt(), from.y.toInt() + foot)
        Popup(
            popupPositionProvider = remember(at) { AtPoint(at, 0.5f, 1f, 0) },
            properties = PopupProperties(focusable = false, clippingEnabled = false)
        ) {
            PoppedStar(onEnd = { actions.starAt = null })
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
    modifier: Modifier = Modifier,
    onPress: () -> Unit = {},
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val drop = remember { Animatable(0f) }
    val turn = remember { Animatable(0f) }
    val turnY = remember { Animatable(0f) }
    var busy by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            if (busy) return@IconButton
            busy = true
            onPress()
            scope.launch {
                when (motion) {
                    // A full turn about its upright axis, like a coin spun.
                    Motion.FLIP -> turnY.animateTo(360f, tween(520))
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
        modifier = modifier.size(48.dp)
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
                rotationY = turnY.value
                cameraDistance = 12 * density
                if (motion == Motion.DROP) alpha = 1f - drop.value * 0.8f
            }
        )
    }
}

/**
 * One glass star popping out of the star button: it swells out of the
 * button with a little overshoot, rises just above it and spins on the spot
 * about its upright axis, twice, like a coin, then fades. Its back, seen
 * while it turns, is a shade deeper, as in the reference animation.
 */
@Composable
private fun PoppedStar(onEnd: () -> Unit) {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(1f, tween(STAR_MS, easing = LinearEasing))
        delay(16)
        onEnd()
    }
    val star = ImageBitmap.imageResource(R.drawable.star_glass)
    val back = remember { ColorFilter.tint(Color(0x40B34700), BlendMode.SrcAtop) }
    Canvas(Modifier.size(width = STAR_SIDE * 2, height = STAR_SIDE + STAR_RISE)) {
        val p = clock.value
        // Swells out of the button, overshooting a touch.
        val grow = OvershootEasing.transform((p / 0.28f).coerceAtMost(1f))
        val side = STAR_SIDE.toPx() * (0.25f + 0.75f * grow)
        // Rises above the button and stays there while it spins.
        val rise = FastOutSlowInEasing.transform((p / 0.4f).coerceAtMost(1f)) * STAR_RISE.toPx()
        // Two full turns, starting slow and ending slow.
        val spin = FastOutSlowInEasing.transform(((p - 0.15f) / 0.7f).coerceIn(0f, 1f)) * 2f * 2f * PI.toFloat()
        val face = cos(spin)
        val width = (side * abs(face)).coerceAtLeast(side * 0.06f)
        val alpha = if (p > 0.82f) (1f - p) / 0.18f else 1f
        val cx = size.width / 2
        val cy = size.height - STAR_SIDE.toPx() / 2 - rise
        drawImage(
            star,
            dstOffset = IntOffset((cx - width / 2).toInt(), (cy - side / 2).toInt()),
            dstSize = IntSize(width.toInt(), side.toInt()),
            alpha = alpha,
            colorFilter = if (face < 0f) back else null,
            filterQuality = FilterQuality.High
        )
    }
}

private val OvershootEasing = Easing { t -> val u = t - 1f; 1f + u * u * (2.6f * u + 1.6f) }

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

private const val STAR_MS = 1300
private val STAR_SIDE = 56.dp
private val STAR_RISE = 64.dp
private val STAR_GOLD = Color(0xFFFFB300)

/** The pill's icons, drawn here so every app has the same ones. */
private object ActionIcons {
    val star by lazy { icon("Star", "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z") }
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
