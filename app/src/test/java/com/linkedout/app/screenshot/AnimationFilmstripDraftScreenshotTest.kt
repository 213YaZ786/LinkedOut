package com.linkedout.app.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.linkedout.app.R
import com.linkedout.app.ui.component.AppBarWithSearch
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.FolderSwitch
import com.linkedout.app.ui.component.GlassTarget
import com.linkedout.app.ui.component.SearchCapsuleHint
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.glass.LocalGlass
import com.linkedout.app.ui.glass.glassGround
import com.linkedout.app.ui.glass.glassZone
import com.linkedout.app.ui.glass.rememberGlassLook
import com.linkedout.app.ui.icon.LinkedOutIcons
import com.linkedout.app.ui.theme.LinkedOutTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT of requests.md point 29 (every animation smooth, MTGA's point 32: the core cases only): a filmstrip per case,
 * motion shown in a still. Top row, "today": the app's piece as it is; bottom row, "proposed": the same piece with the
 * fix. Frames are taken with the test clock stopped (mainClock.autoAdvance = false), advanced to each instant, then
 * captured with captureToImage, as the compose testing guide does for animations
 * https://developer.android.com/develop/ui/compose/testing/time-manipulation
 * and joined in one bitmap saved with Roborazzi's Bitmap capture. Nothing of app/src/main changes.
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/AnimationFilmstripDraftScreenshotTest.kt#AnimationFilmstripDraftScreenshotTest
 *
 * Ports (the sample of each mechanism the proposals use, names kept: visible, enter, exit, contentState):
 * https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedVisibilitySamples.kt#AnimatedVisibilityWithBooleanVisibleParamNoReceiver
 * (fadeIn, fadeOut + scaleOut on a `visible` flag: alert)
 * https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
 * (AnimatedContent on a state: follow's label, footer, folders' count, label and check)
 * not ported: AnimatedVisibility's receiver variants and the Transition animateDp of the sample, the sample's
 * Foo/Bar/Baz contents and its pointerInput (the test drives the state instead).
 * not ported: AnimationFilmstripDraftScreenshotTest's own cases like_end, holdmenu_close, newdot, zoom, video_bubble and
 * their functions and tests LikeBurst, HoldClose, NameRow, Photo, Zoom, Bubble, CardMini, likeEnd, holdmenuClose, newdot,
 * zoom, videoBubble (kit pieces fixed in Modules: SparkButton, HoldMenu, NewDot, the media viewer and the video player), its
 * private PersonAdd icon (LinkedOut's Follow button has no icon), the AnimatableAnimateToGenericsType port that served
 * zoom and the held card's rise, and its own MtgaTheme, rule and glass set-up (ScreenshotBase's rule, LinkedOutTheme and
 * rememberGlassLook are used).
 *
 * Not driven as in the app: the Follow button (an inline `if` of FeedScreen.kt:452-460, redrawn: today's row is the app's
 * own BoldButton, the proposed one a GlassTarget with the same glass zone and an animated accent fill, as BoldButton draws
 * them in glass), the list footers (TimelineScreen.kt:467-493 and FeedScreen.kt:290-307, redrawn from their lines; LoadingMark
 * does not draw on Robolectric, so [MarkStandIn] shows the mark's two bitmaps as WelcomeModelDraftScreenshotTest does), the
 * folder row (FoldersScreen.kt:177-302, redrawn without its swipe, hold menu and Delete). The alert is the app's own
 * AppBarWithSearch, SearchCapsuleHint, FolderSwitch and BannerAction, as TopCapsuleDraftScreenshotTest draws them.
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class AnimationFilmstripDraftScreenshotTest : ScreenshotBase() {

    private val standard = listOf(0, 50, 100, 150, 200, 300, 450)

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f)
    )

    private var pictures = 0

    // One row: the piece composed on a fixed stage, the change triggered ([go] true), a frame at each instant.
    private fun row(
        night: Boolean,
        times: List<Int>,
        width: Int,
        height: Int,
        content: @Composable (go: Boolean) -> Unit
    ): List<Bitmap> {
        val go = mutableStateOf(false)
        val picture = pictures++
        compose.mainClock.autoAdvance = false
        compose.activity.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                LinkedOutTheme(darkTheme = night, dynamicColor = false) {
                    // The app's own look, quiet glass, as MainActivity and ScreenshotBase set it.
                    val look = rememberGlassLook(MaterialTheme.colorScheme, true, quiet = true)
                    CompositionLocalProvider(
                        LocalGlass provides look,
                        LocalContentColor provides MaterialTheme.colorScheme.onBackground
                    ) {
                        androidx.compose.runtime.key(picture) {
                            Box(
                                Modifier
                                    .testTag("stage")
                                    .size(width.dp, height.dp)
                                    .clipToBounds()
                                    .glassGround(look, MaterialTheme.colorScheme.background)
                            ) { content(go.value) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        // The first composition and its enter animations settle on the stopped clock before the change.
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.runOnIdle { go.value = true }
        val frames = mutableListOf<Bitmap>()
        var elapsed = 0
        times.forEach { t ->
            compose.mainClock.advanceTimeBy((t - elapsed).toLong())
            elapsed = t
            frames += compose.onNodeWithTag("stage").captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        }
        compose.mainClock.autoAdvance = true
        return frames
    }

    // The two rows joined, a label per row, the ms under each frame.
    private fun sheet(name: String, night: Boolean, times: List<Int>, today: List<Bitmap>, proposed: List<Bitmap>) =
        sheet(name, night, times, listOf("today" to today, "proposed" to proposed))

    private fun sheet(name: String, night: Boolean, times: List<Int>, rows: List<Pair<String, List<Bitmap>>>) {
        val scale = 0.6f
        val w = (rows[0].second[0].width * scale).toInt()
        val h = (rows[0].second[0].height * scale).toInt()
        val labelW = 230
        val ms = 40
        val gap = 12
        val sheet = Bitmap.createBitmap(labelW + (w + gap) * times.size + gap, (h + ms) * rows.size + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(if (night) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        val ink = if (night) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 26f }
        val smooth = Paint(Paint.FILTER_BITMAP_FLAG)
        rows.forEachIndexed { r, (label, frames) ->
            val top = r * (h + ms) + gap
            canvas.drawText(label, 10f, top + h / 2f, text)
            frames.forEachIndexed { i, frame ->
                val left = labelW + gap + i * (w + gap)
                canvas.drawBitmap(frame, null, AndroidRect(left, top, left + w, top + h), smooth)
                canvas.drawText("${times[i]} ms", left + 4f, top + h + 28f, text)
            }
        }
        sheet.captureRoboImage("src/test/screenshots/anim_${name}_draft_${if (night) "dark" else "light"}.png", roborazziOptions = options)
    }

    private fun film(name: String, width: Int, height: Int, times: List<Int> = standard,
                     today: @Composable (Boolean) -> Unit, proposed: @Composable (Boolean) -> Unit) {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
        listOf(false, true).forEach { night ->
            val a = row(night, times, width, height, today)
            val b = row(night, times, width, height, proposed)
            sheet(name, night, times, a, b)
        }
    }

    // A swap through nothing: the old content out first (90 ms), the new one in after it (110 ms, 90 ms later), never
    // clipped while the size changes (SizeTransform, AnimatedContentSamples.kt). Material 3's fade through.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
    private fun <S> fadeThrough(): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        (fadeIn(tween(110, delayMillis = 90)) togetherWith fadeOut(tween(90))).using(SizeTransform(clip = false))
    }

    // The proposed swap of a small piece: today the content changes at once, proposed it goes through AnimatedContent.
    @Composable
    private fun <S> Swap(contentState: S, proposed: Boolean, label: String, content: @Composable (S) -> Unit) {
        if (proposed) {
            AnimatedContent(targetState = contentState, transitionSpec = fadeThrough(), label = label) { content(it) }
        } else {
            content(contentState)
        }
    }

    // LoadingMark does not draw on Robolectric (its modulated bitmaps): its two bitmaps as plain images, at the footer's size.
    @Composable
    private fun MarkStandIn() {
        val body = ImageBitmap.imageResource(R.drawable.lo_mark_body)
        val shine = ImageBitmap.imageResource(R.drawable.lo_mark_shine)
        Box(Modifier.size(28.dp)) {
            Image(body, contentDescription = null, modifier = Modifier.fillMaxSize())
            Image(shine, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }

    // 1. FeedScreen.kt:452-460, the profile's button. Today: an `if` between an outlined BoldButton "Following" and a filled
    // "Follow" button, so label, fill and width jump. Proposed: one button, the accent fill animated by alpha only (a
    // fade to Color.Transparent would pass through grey), the label crossing in AnimatedContent, the width animated.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
    @Composable
    private fun Follow(following: Boolean, proposed: Boolean) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!proposed) {
                if (following) {
                    BoldButton(onClick = {}) { Text("Following") }
                } else {
                    BoldButton(filled = true, onClick = {}) { Text("Follow") }
                }
            } else {
                val glass = LocalGlass.current!!
                val shape = ButtonDefaults.outlinedShape
                val fill by animateFloatAsState(if (following) 0f else 0.45f, tween(200), label = "followFill")
                val filledContent = if (glass.dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
                val contentColor by animateColorAsState(
                    if (following) MaterialTheme.colorScheme.primary else filledContent, tween(200), label = "followText"
                )
                GlassTarget(Modifier) {
                    Box(
                        Modifier
                            .glassZone(shape, glass, lens = 1f)
                            .background(glass.accentTint.copy(alpha = fill), shape)
                            .heightIn(min = ButtonDefaults.MinHeight)
                            .padding(ButtonDefaults.ContentPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = following,
                            transitionSpec = fadeThrough(),
                            label = "followContent"
                        ) { isFollowing ->
                            Text(
                                if (isFollowing) "Following" else "Follow",
                                style = MaterialTheme.typography.labelLarge,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun follow() = film(
        "follow", 200, 80,
        today = { Follow(it, proposed = false) }, proposed = { Follow(it, proposed = true) }
    )

    // 2. TimelineScreen.kt:283-317: the alert in the capsule's actions, `if (somethingWrong)`. Today it pops in; proposed
    // AnimatedVisibility, fadeIn + scaleIn in, fadeOut + scaleOut out, with the room opening and closing so the hint does
    // not jump (expandHorizontally and shrinkHorizontally, as the MTGA filmstrip's dot slot does).
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedVisibilitySamples.kt#AnimatedVisibilityWithBooleanVisibleParamNoReceiver
    @Composable
    private fun Alert(visible: Boolean, proposed: Boolean) {
        val alert: @Composable () -> Unit = {
            BannerAction(
                icon = LinkedOutIcons.Warning,
                label = "What went wrong",
                onClick = {},
                container = MaterialTheme.colorScheme.errorContainer,
                tint = MaterialTheme.colorScheme.error
            )
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            AppBarWithSearch(
                inputField = { SearchCapsuleHint(LinkedOutIcons.Search, listOf("Search saved posts"), onClick = {}) },
                navigationIcon = {
                    FolderSwitch(
                        icon = LinkedOutIcons.Folder,
                        trailingIcon = LinkedOutIcons.ExpandMore,
                        label = "All",
                        expanded = false,
                        onClick = {}
                    )
                },
                actions = if (proposed) ({
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.6f) + expandHorizontally(tween(200)),
                        exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.6f) + shrinkHorizontally(tween(200))
                    ) { alert() }
                }) else if (visible) ({ alert() }) else null
            )
        }
    }

    @Test
    fun alert() = film(
        "alert", 360, 72,
        today = { Alert(it, proposed = false) }, proposed = { Alert(it, proposed = true) }
    )

    // 3. TimelineScreen.kt:467-493 TimelineFooter and FeedScreen.kt:290-307: LoadingMark, the "Load older posts" button and
    // "No older posts available." replace one another at once. Proposed: AnimatedContent keyed on the footer state, the
    // Box's height animated by animateContentSize (the one animation of the size: AnimatedContent's own snaps).
    // The change shown: the page arrived, the mark gives way to the button.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
    private enum class FooterState { Loading, More, End }

    @Composable
    private fun FooterContent(contentState: FooterState) {
        when (contentState) {
            FooterState.Loading -> MarkStandIn()
            FooterState.More -> BoldButton(onClick = {}) { Text("Load older posts") }
            FooterState.End -> Text(
                "No older posts available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    private fun Footer(loaded: Boolean, proposed: Boolean) {
        val contentState = if (loaded) FooterState.More else FooterState.Loading
        Column(Modifier.fillMaxWidth()) {
            Zone(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = ZoneGap)) {
                Text("The last post of the page", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            if (!proposed) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    FooterContent(contentState)
                }
            } else {
                Box(Modifier.fillMaxWidth().animateContentSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = contentState,
                        transitionSpec = {
                            (fadeIn(tween(110, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                                .using(SizeTransform(clip = false) { _, _ -> snap() })
                        },
                        label = "footerContent"
                    ) { FooterContent(it) }
                }
            }
        }
    }

    @Test
    fun footer() = film(
        "footer", 320, 150,
        today = { Footer(it, proposed = false) }, proposed = { Footer(it, proposed = true) }
    )

    // 4. FoldersScreen.kt:177-302, a folder's row open on one account: the count text ("Empty" to "1 account"), the
    // "Open/Close" label and the check (the folder's name to Filed here) change at once. Proposed: AnimatedContent, fade
    // through, on those three. The panel's own AnimatedVisibility is the app's and is shown open in both rows.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
    @Composable
    private fun FolderRow(filed: Boolean, proposed: Boolean) {
        val count = if (filed) 1 else 0
        Zone(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = ZoneGap)) {
            Column {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Recruiters",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Swap(count, proposed, "folderCount") { shown ->
                            Text(
                                when (shown) {
                                    0 -> "Empty"
                                    1 -> "1 account"
                                    else -> "$shown accounts"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    BoldButton(onClick = {}) { Text("Rename") }
                    BoldButton(onClick = {}) {
                        Swap(filed, proposed, "folderOpen") { open -> Text(if (open) "Close" else "Open") }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = ButtonDefaults.MinHeight)
                            .padding(ButtonDefaults.TextButtonContentPadding),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(url = null, name = "Satya Nadella", size = 28.dp)
                        Text(
                            "Satya Nadella",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 10.dp)
                        )
                        Swap(filed, proposed, "folderCheck") { here ->
                            if (here) {
                                Icon(
                                    LinkedOutIcons.Check,
                                    contentDescription = "Filed here",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Text(
                                    "Main",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun folders() = film(
        "folders", 360, 150,
        today = { FolderRow(it, proposed = false) }, proposed = { FolderRow(it, proposed = true) }
    )
}
