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
import androidx.compose.animation.Crossfade
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.linkedout.app.R
import com.linkedout.app.ui.component.AppBarWithSearch
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.AvatarGhost
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.BoldIconButton
import com.linkedout.app.ui.component.FolderSwitch
import com.linkedout.app.ui.component.GlassSearchField
import com.linkedout.app.ui.component.GlassTarget
import com.linkedout.app.ui.component.InnerZoneShape
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
 * DRAFT of requests.md points 29 and 30 (every animation smooth, MTGA's point 32: point 29 the core cases follow, alert,
 * footer, folders; point 30 the other screens rows, clear, state, count, pill, viewer, placeholder): a filmstrip per case,
 * motion shown in a still. Top row, "today": the app's piece as it is; bottom row, "proposed": the same piece with the
 * fix (the cases with two changes, state, pill and viewer, have a today and a proposed row per change). Frames are taken with the test clock stopped (mainClock.autoAdvance = false), advanced to each instant, then
 * captured with captureToImage, as the compose testing guide does for animations
 * https://developer.android.com/develop/ui/compose/testing/time-manipulation
 * and joined in one bitmap saved with Roborazzi's Bitmap capture. Nothing of app/src/main changes.
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/AnimationFilmstripDraftScreenshotTest.kt#AnimationFilmstripDraftScreenshotTest
 *
 * Ports (the sample of each mechanism the proposals use, names kept: visible, enter, exit, contentState):
 * https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedVisibilitySamples.kt#AnimatedVisibilityWithBooleanVisibleParamNoReceiver
 * (fadeIn, fadeOut + scaleOut on a `visible` flag: alert)
 * https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
 * (AnimatedContent on a state: follow's label, footer, folders' count, label and check, and the state, count cases)
 * https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/samples/src/main/java/androidx/compose/foundation/samples/LazyDslSamples.kt#AnimateItemSample
 * (items(list, key) with Modifier.animateItem(): rows)
 * https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/CrossfadeSample.kt#CrossfadeSample
 * (Crossfade(targetState) { screen -> }: viewer, placeholder)
 * not ported: AnimatedVisibility's receiver variants and the Transition animateDp of the sample, the sample's
 * Foo/Bar/Baz contents and its pointerInput (the test drives the state instead).
 * not ported: AnimateItemSample's two Buttons ("Add new item", "Shuffle") and its shuffled list of numbers (the test
 * drives one removal and one addition of accounts), CrossfadeSample's "A" and "B" pages (the cases' own screens).
 * not drafted (the same mechanism as a case above): DebugLogScreen's items (keys and animateItem), the Share action of
 * PostDetailScreen, the Save action of JobDetailScreen and the Delete all action of SavedMediaScreen (AnimatedVisibility),
 * the link card's and the document's held image (PostCard.kt:475-505, :545-553, as the held photo of placeholder),
 * JobDetailScreen.kt:115-135 (as state's list), the Accounts field's Paste button (it never leaves).
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
 * Point 30: rows redraws AccountsScreen.kt:394's card (Zone, Avatar, two lines) in a LazyColumn with the same key; clear is
 * the app's own GlassSearchField (SearchScreen.kt:132, the Close icon without its tap); state redraws the Jobs list
 * (JobsScreen.kt:221-291: the loading mark, two cards, the hint) and PostDetailScreen.kt:128-165 as the same swap; count
 * redraws the unread badge (AccountsScreen.kt:455-474), the "Filters, N" pill (JobsScreen.kt:179, a glass pane like
 * Follow's, not MenuPill, whose label is a plain String), "N files" (SavedMediaScreen.kt:152) and the Connection's status
 * (ConnectionScreen.kt:94-98); pill redraws ChallengeHost.kt:71-100 (its Surface and CircularProgressIndicator are Material
 * controls: a plain capsule and a ring stand in); viewer redraws MediaViewer.kt:174-198, :267-285 (the preview image of
 * the fetching text, the video and its controls are plain stand-ins); placeholder redraws PostCard.kt's HeldMedia, the
 * picture and the avatar's circle (:176-184, :288-298). The pill sits at the bottom of the screen in the app
 * (BottomCenter, 96 dp above the bar), so it slides up from below and not down from the top.
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

    // ---- Point 30: the other screens ----

    // One sheet of several rows, a today and a proposed row per change: [film]'s two rows are not enough where a piece
    // has two changes (state, pill, viewer).
    private fun case(label: String, content: @Composable (Boolean) -> Unit) = label to content

    private fun filmRows(name: String, width: Int, height: Int, times: List<Int> = standard,
                         cases: List<Pair<String, @Composable (Boolean) -> Unit>>) {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
        listOf(false, true).forEach { night ->
            sheet(name, night, times, cases.map { (label, content) -> label to row(night, times, width, height, content) })
        }
    }

    // LoadingMark's two bitmaps at the size the screens give it (96 dp), [MarkStandIn]'s at any size.
    @Composable
    private fun MarkStandInAt(size: Dp) {
        val body = ImageBitmap.imageResource(R.drawable.lo_mark_body)
        val shine = ImageBitmap.imageResource(R.drawable.lo_mark_shine)
        Box(Modifier.size(size)) {
            Image(body, contentDescription = null, modifier = Modifier.fillMaxSize())
            Image(shine, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }

    // 1. AccountsScreen.kt:394 items(visible, key = { it.handle }): an unfollow or a filter removes a row and another comes
    // in, the rows around snap into their new places. Proposed: Modifier.animateItem() on the row, as AnimateItemSample.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/samples/src/main/java/androidx/compose/foundation/samples/LazyDslSamples.kt#AnimateItemSample
    private class Account(val handle: String, val name: String)

    private val mary = Account("maryb", "Mary Barra")
    private val satya = Account("satyanadella", "Satya Nadella")
    private val bill = Account("billgates", "Bill Gates")
    private val sundar = Account("sundarpichai", "Sundar Pichai")

    @Composable
    private fun Rows(unfollowed: Boolean, proposed: Boolean) {
        val visible = if (unfollowed) listOf(mary, satya, sundar) else listOf(satya, bill, sundar)
        LazyColumn(Modifier.fillMaxSize()) {
            items(visible, key = { it.handle }) { row ->
                Zone(
                    (if (proposed) Modifier.animateItem() else Modifier)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = ZoneGap)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Avatar(url = null, name = row.name, size = 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(row.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "in/${row.handle}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun rows() = film(
        "rows", 360, 250,
        today = { Rows(it, proposed = false) }, proposed = { Rows(it, proposed = true) }
    )

    // 2. AccountsScreen.kt:249-255 fieldActions and SearchScreen.kt:132: the Clear icon is an `if (typed.isNotEmpty())`
    // in the field's end slot, so the field's end width changes at once. Proposed: AnimatedVisibility, fadeIn +
    // expandHorizontally in, fadeOut + shrinkHorizontally out. The field is the app's own GlassSearchField.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedVisibilitySamples.kt#AnimatedVisibilityWithBooleanVisibleParamNoReceiver
    @Composable
    private fun ClearField(typed: Boolean, proposed: Boolean) {
        val text = if (typed) "Satya Nadella" else ""
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassSearchField(
                value = text,
                onValueChange = {},
                placeholder = "Search saved posts",
                trailingIcon = {
                    val clear: @Composable () -> Unit = {
                        Icon(LinkedOutIcons.Close, contentDescription = "Clear", modifier = Modifier.clip(CircleShape).padding(12.dp))
                    }
                    if (proposed) {
                        AnimatedVisibility(
                            visible = text.isNotEmpty(),
                            enter = fadeIn(tween(200)) + expandHorizontally(tween(200)),
                            exit = fadeOut(tween(200)) + shrinkHorizontally(tween(200))
                        ) { clear() }
                    } else if (text.isNotEmpty()) {
                        clear()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
    }

    @Test
    fun clear() = film(
        "clear", 360, 80,
        today = { ClearField(it, proposed = false) }, proposed = { ClearField(it, proposed = true) }
    )

    // 3. JobsScreen.kt:221-291 (the loading mark, the cards, "No offer matches"), PostDetailScreen.kt:128-165 (the mark,
    // "This post can't be shown", the post), SavedMediaScreen.kt:124-137 (the empty text, the grid), JobDetailScreen.kt:
    // 115-135: `if` and `when` swap them at once. Proposed: AnimatedContent, fade through, the height animated by
    // animateContentSize (the Footer case's way). Two changes: loading to the cards, loading to nothing.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
    private enum class ListState { Loading, Cards, None }

    @Composable
    private fun ListContent(contentState: ListState) {
        when (contentState) {
            ListState.Loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                MarkStandInAt(96.dp)
            }
            ListState.Cards -> Column {
                listOf("Product designer" to "Glass Studio, Paris", "Android developer" to "Tamazight Labs, Lyon").forEach { (title, company) ->
                    Zone(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = ZoneGap)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                company,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            ListState.None -> Text(
                "No offer matches. Fewer words or a wider place usually help.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp)
            )
        }
    }

    @Composable
    private fun ListSwap(result: ListState, ready: Boolean, proposed: Boolean) {
        val contentState = if (ready) result else ListState.Loading
        if (!proposed) {
            Box(Modifier.fillMaxWidth()) { ListContent(contentState) }
        } else {
            Box(Modifier.fillMaxWidth().animateContentSize()) {
                AnimatedContent(
                    targetState = contentState,
                    transitionSpec = {
                        (fadeIn(tween(110, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                            .using(SizeTransform(clip = false) { _, _ -> snap() })
                    },
                    label = "listState"
                ) { ListContent(it) }
            }
        }
    }

    @Test
    fun state() = filmRows(
        "state", 360, 260,
        cases = listOf(
            case("today, cards") { ListSwap(ListState.Cards, it, proposed = false) },
            case("proposed, cards") { ListSwap(ListState.Cards, it, proposed = true) },
            case("today, none") { ListSwap(ListState.None, it, proposed = false) },
            case("proposed, none") { ListSwap(ListState.None, it, proposed = true) }
        )
    )

    // 4. Small texts that swap: AccountsScreen.kt:455-474 the unread badge (appears, then its number changes), JobsScreen.kt:179
    // "Filters, N", SavedMediaScreen.kt:152-170 "N files", ConnectionScreen.kt:94-98 "Ready" and "Pausing for N more
    // seconds". Today a plain swap; proposed AnimatedContent (fade through, SizeTransform(clip = false)) on each text, the
    // badge itself fading and scaling in (AnimatedVisibility), the pill's accent fill animated as Follow's.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
    @Composable
    private fun AccountLine(name: String, unread: Int, proposed: Boolean) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            val badge: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .widthIn(min = 28.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Swap(unread, proposed, "unreadCount") { n ->
                        Text(
                            n.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            if (!proposed) {
                if (unread > 0) badge()
            } else {
                AnimatedVisibility(
                    visible = unread > 0,
                    enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.6f),
                    exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.6f)
                ) { badge() }
            }
        }
    }

    // JobsScreen.kt:179 redrawn as a glass pane like Follow's (MenuPill's label is a plain String, so the proposal would
    // need a slot there): both rows are this pane, only the label and the fill differ.
    @Composable
    private fun FiltersPill(count: Int, proposed: Boolean) {
        val glass = LocalGlass.current!!
        val target = if (count > 0) 0.45f else 0f
        val fill = if (proposed) animateFloatAsState(target, tween(200), label = "filtersFill").value else target
        GlassTarget(Modifier) {
            Row(
                modifier = Modifier
                    .glassZone(CircleShape, glass, lens = 1f)
                    .background(glass.accentTint.copy(alpha = fill), CircleShape)
                    .heightIn(min = ButtonDefaults.MinHeight)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(LinkedOutIcons.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Swap(count, proposed, "filtersLabel") { n ->
                    Text(
                        if (n > 0) "Filters, $n" else "Filters",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    @Composable
    private fun Counts(go: Boolean, proposed: Boolean) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AccountLine("Satya Nadella", if (go) 3 else 0, proposed)
            AccountLine("Bill Gates", if (go) 4 else 3, proposed)
            FiltersPill(if (go) 2 else 0, proposed)
            Swap(if (go) 13 else 12, proposed, "filesCount") { files ->
                Text("$files files", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Zone(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("www.linkedin.com", style = MaterialTheme.typography.titleMedium)
                    Swap(if (go) 5000 else 0, proposed, "cooldown") { cooldownMs ->
                        Text(
                            if (cooldownMs > 0) "Pausing for ${cooldownMs / 1000} more seconds after a rate limit" else "Ready",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    @Test
    fun count() = film(
        "count", 360, 340,
        today = { Counts(it, proposed = false) }, proposed = { Counts(it, proposed = true) }
    )

    // 5. ChallengeHost.kt:71-100: `val current = task ?: return`, the "Checking access" pill pops in and out. Proposed:
    // AnimatedVisibility (fade and slide), the last task remembered so the exit still has its text. The pill is at the
    // bottom of the screen in the app (BottomCenter above the bar), so it slides up from the edge it sits on. Two changes:
    // the pill coming, the pill going. Its Surface and ring are Material controls: a capsule and a drawn ring stand in.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedVisibilitySamples.kt#AnimatedVisibilityWithBooleanVisibleParamNoReceiver
    @Composable
    private fun CheckPill(task: String?, proposed: Boolean) {
        val pill: @Composable (String) -> Unit = { host ->
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(16.dp).border(2.dp, MaterialTheme.colorScheme.inverseOnSurface, CircleShape))
                Text(
                    "Checking access to $host",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
        Box(Modifier.fillMaxSize().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
            if (!proposed) {
                task?.let { pill(it) }
            } else {
                var last by remember { mutableStateOf(task) }
                if (task != null) last = task
                AnimatedVisibility(
                    visible = task != null,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
                    exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it }
                ) { last?.let { pill(it) } }
            }
        }
    }

    @Test
    fun pill() = filmRows(
        "pill", 360, 140,
        cases = listOf(
            case("today, comes") { CheckPill(if (it) "www.linkedin.com" else null, proposed = false) },
            case("proposed, comes") { CheckPill(if (it) "www.linkedin.com" else null, proposed = true) },
            case("today, goes") { CheckPill(if (it) null else "www.linkedin.com", proposed = false) },
            case("proposed, goes") { CheckPill(if (it) null else "www.linkedin.com", proposed = true) }
        )
    )

    // 6. MediaViewer.kt:174-198 (the "Fetching" text with the preview), the failed pane (:267-278), the Play block
    // with "Mobile data" shown `if (!started)` (:285-312): they pop. Proposed: AnimatedVisibility for the Play block, Crossfade for
    // fetching, the player and the failed pane. The video and the preview image are plain stand-ins (no player on the JVM).
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/CrossfadeSample.kt#CrossfadeSample
    private enum class ViewerState { Fetching, Player, Failed }

    @Composable
    private fun VideoStandIn() {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerLowest)
                )
            )
        )
    }

    @Composable
    private fun ViewerContent(screen: ViewerState) {
        when (screen) {
            ViewerState.Fetching -> Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(2f).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                Text("Fetching the video from its post.", color = Color.White, textAlign = TextAlign.Center)
            }
            ViewerState.Player -> VideoStandIn()
            ViewerState.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("This video could not be played here.", color = Color.White)
                BoldButton(onClick = {}, filled = true) { Text("Open in browser") }
            }
        }
    }

    @Composable
    private fun Viewer(from: ViewerState, to: ViewerState, go: Boolean, proposed: Boolean) {
        val screen = if (go) to else from
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (!proposed) {
                ViewerContent(screen)
            } else {
                Crossfade(targetState = screen, animationSpec = tween(200), label = "viewerState") { ViewerContent(it) }
            }
        }
    }

    @Composable
    private fun PlayBlock(started: Boolean, proposed: Boolean) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoStandIn()
            val block: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoldIconButton(onClick = {}, modifier = Modifier.size(72.dp)) {
                        Icon(LinkedOutIcons.Play, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    Text("Mobile data, Wi-Fi only is on", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (!proposed) {
                if (!started) Box(Modifier.align(Alignment.Center)) { block() }
            } else {
                AnimatedVisibility(
                    visible = !started,
                    modifier = Modifier.align(Alignment.Center),
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.9f)
                ) { block() }
            }
        }
    }

    @Test
    fun viewer() = filmRows(
        "viewer", 360, 260,
        cases = listOf(
            case("today, play") { PlayBlock(it, proposed = false) },
            case("proposed, play") { PlayBlock(it, proposed = true) },
            case("today, found") { Viewer(ViewerState.Fetching, ViewerState.Player, it, proposed = false) },
            case("proposed, found") { Viewer(ViewerState.Fetching, ViewerState.Player, it, proposed = true) },
            case("today, failed") { Viewer(ViewerState.Player, ViewerState.Failed, it, proposed = false) },
            case("proposed, failed") { Viewer(ViewerState.Player, ViewerState.Failed, it, proposed = true) }
        )
    )

    // 7. PostCard.kt:288-298 (the held photo, "tap to load", replaced by the picture when tapped; the link card :475-505
    // and the document :545-553 do the same with HeldCardImage) and :176-184 (the avatar's empty circle replaced by the
    // ghost when its address is refused): they swap at once. Proposed: Crossfade. The picture and the tap are stand-ins.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/CrossfadeSample.kt#CrossfadeSample
    @Composable
    private fun HeldPhoto() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(LinkedOutIcons.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Photo, tap to load", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Mobile data, Wi-Fi only is on",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    private fun PhotoStandIn() {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))
            )
        )
    }

    @Composable
    private fun Placeholder(revealed: Boolean, proposed: Boolean) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (!proposed) {
                        if (revealed) AvatarGhost(44.dp)
                    } else {
                        Crossfade(targetState = revealed, animationSpec = tween(200), label = "avatarGhost") { screen ->
                            if (screen) AvatarGhost(44.dp)
                        }
                    }
                }
                Column {
                    Text("Satya Nadella", style = MaterialTheme.typography.titleMedium)
                    Text("2 h", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f)
                    .clip(InnerZoneShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (!proposed) {
                    if (revealed) PhotoStandIn() else HeldPhoto()
                } else {
                    Crossfade(targetState = revealed, animationSpec = tween(200), label = "heldPhoto") { screen ->
                        if (screen) PhotoStandIn() else HeldPhoto()
                    }
                }
            }
        }
    }

    @Test
    fun placeholder() = film(
        "placeholder", 360, 260,
        today = { Placeholder(it, proposed = false) }, proposed = { Placeholder(it, proposed = true) }
    )
}
