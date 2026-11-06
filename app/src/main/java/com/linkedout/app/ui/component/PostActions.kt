package com.linkedout.app.ui.component

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.Post
import com.linkedout.app.data.marks.PostMarks
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * A long press on a post opens the suite's long press menu (HoldMenu, SMS's
 * message menu): the post lifts out of the feed over the blurred screen and
 * a pane of glass under it offers like, archive, share, and save media when
 * the post has some. Likes and archives stay on the phone, see PostMarks. A
 * new like plays SparkButton's star over the card once the menu has closed.
 *
 * The post card puts [tracker] on the zone it draws, calls [open] on a long
 * press, and places [PostActionsOverlay] in the same box, giving it the
 * zone again without its click handlers.
 */
@Stable
class PostActions internal constructor(
    val post: Post,
    private val marks: PostMarks,
    private val onDownload: (MediaItem) -> Unit,
    private val context: Context,
    private val haptics: Haptics,
    internal val sparkState: SparkButtonState
) {
    internal var menuOpen by mutableStateOf(false)
    /** The zone's place in its window, which HoldMenu lifts the copy from. */
    internal var bounds by mutableStateOf(Rect.Zero)
    /** Set when the post becomes liked, cleared when the star has finished. */
    internal var burst by mutableStateOf(false)

    // The way SMS's ThreadScreen.kt hides a held message while its menu is open (onGloballyPositioned,
    // graphicsLayer alpha): https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/feature/thread/ThreadScreen.kt
    val tracker: Modifier = Modifier
        .onGloballyPositioned { bounds = it.boundsInWindow() }
        .graphicsLayer { alpha = if (menuOpen) 0f else 1f }

    fun open() {
        haptics.firm()
        menuOpen = true
    }

    // HoldMenu closes before it runs an action, so the burst starts on a card that is back in place.
    internal fun like() {
        if (!marks.marks.value.isLiked(post.id)) {
            burst = true
            sparkState.animate()
        }
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
    val sparkState = rememberSparkButtonState()
    return remember(post.id, post) { PostActions(post, marks, onDownload, context, haptics, sparkState) }
}

// How a caller opens HoldMenu, from SMS's ThreadScreen.kt around line 1969:
// https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/feature/thread/ThreadScreen.kt
// The star is Tusky's favourite button, connyduck/sparkbutton through SparkButton.
@Composable
fun BoxScope.PostActionsOverlay(actions: PostActions, held: @Composable () -> Unit) {
    val marks: PostMarks = koinInject()
    val state by marks.marks.collectAsState()

    if (actions.menuOpen) {
        val liked = state.isLiked(actions.post.id)
        val archived = state.isArchived(actions.post.id)
        HoldMenu(
            bounds = actions.bounds,
            actions = listOfNotNull(
                HoldAction(ActionIcons.star, if (liked) "Unlike" else "Like", run = actions::like),
                HoldAction(ActionIcons.archive, if (archived) "Unarchive" else "Archive", run = actions::archive),
                HoldAction(ActionIcons.share, "Share", run = actions::share),
                if (actions.canSave) HoldAction(ActionIcons.save, "Save media", run = actions::save) else null
            ),
            onDismiss = { actions.menuOpen = false },
            held = held
        )
    }

    // SparkButton has no end callback, so the star is removed after the length of its keyframes,
    // https://github.com/connyduck/sparkbutton/blob/main/sparkbutton-compose/src/main/kotlin/at/connyduck/sparkbutton/compose/SparkButton.kt#SparkButton
    LaunchedEffect(actions.burst) {
        if (actions.burst) {
            delay(SPARK_DURATION_MILLIS)
            actions.burst = false
        }
    }

    // Instagram's double tap heart plays over the post itself, centred, so the star does too.
    // The state's animate() starts the animation from code, as the library's SparkButtonState allows
    // (same file, #SparkButtonState). The library's own colours are kept: STAR_GOLD (#FFB300) is neither
    // its amber (#FFC107) nor its deep orange (#FF5722), it only tints the star. It stays enabled: the
    // library draws a disabled button at 0.38 alpha, star and sparks included, and it is only on screen
    // for the burst's second, its click doing nothing.
    if (actions.burst) {
        SparkButton(
            onClick = {},
            modifier = Modifier.align(Alignment.Center),
            animateOnClick = false,
            enabled = true,
            state = actions.sparkState
        ) {
            Image(
                imageVector = ActionIcons.star,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                colorFilter = ColorFilter.tint(STAR_GOLD)
            )
        }
    }
}

private val STAR_GOLD = Color(0xFFFFB300)

/** The menu's icons, drawn here so every app has the same ones. */
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
