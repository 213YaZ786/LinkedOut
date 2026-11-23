package com.linkedout.app.screenshot

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.R
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostStats
import com.linkedout.app.di.appModule
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.PostCard
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.component.statusBarTop
import com.linkedout.app.ui.glass.LocalGlass
import com.linkedout.app.ui.glass.LocalGlassBackdrop
import com.linkedout.app.ui.glass.glassFloating
import com.linkedout.app.ui.glass.glassSource
import com.linkedout.app.ui.glass.rememberGlassBackdrop
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.max

/**
 * DRAFT for the user's yes (requests.md points 32 and 33), nothing of app/src/main changes: the loading of the pull to
 * refresh and of a profile, with no small animation and no bubble.
 * pull_today_*: the pull as ui/component/PullIndicator.kt draws it now, the 44 dp mark in a 56 dp circular glass disc
 * (the plain surfaceContainerHigh disc with a shadow when there is no glass) sliding down over the list, at 0.5 of the
 * threshold (pulled), at 1.0 (over) and refreshing at the threshold.
 * pull_proposed_*: the same instants with no disc at all (no Surface, no glass, no shadow), the mark 72 dp, drawn straight
 * over the list, centred at the top, the "out" mark kept as it is.
 * profile_load_today: ProfileZone (feature/feed/FeedScreen.kt:373-497) while it loads, the Refresh icon replaced by a
 * 24 dp mark in a 44 dp slot at the top right (:398), and under the card the page's 96 dp mark (:289) of a profile with
 * nothing yet. profile_load_proposed: the Refresh icon stays, disabled (alpha 0.38), nothing moves in the card, and the
 * load shows as the same 72 dp unbubbled mark centred in the page under the card.
 * Ported from MTGA's drafts, the frame structure (a page with the indicator over it at fixed pull fractions, a profile
 * header with the load over its page), through LinkedOut's own pull and mark:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/PullRingDraftScreenshotTest.kt#PullRingDraftScreenshotTest
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/ProfilePullCountDraftScreenshotTest.kt#ProfilePullCountDraftScreenshotTest
 * The indicator's layer is PullIndicator's own, redrawn here because LoadingMark does not draw on Robolectric (its
 * modulated bitmaps): [MarkStandIn] shows the mark's two bitmaps as plain images at the size asked, as the Welcome drafts do.
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/ui/component/PullIndicator.kt#PullIndicator
 * not ported: MTGA's FeatherRing, its counts ("2 of 4", "sources") and the blurred page (LinkedOut keeps its own mark and
 * no count, the user's word 2026-11-22); FeatherArt, FeatherInk, drawFeather and SLOTS; blurFeed, Blurred, Size and
 * FastBlur's blur (nothing is blurred here); Screen's progress and count parameters; Profile's banner brush, bio and
 * posts under the header (ProfileZone is redrawn instead); the haptic tick of PullIndicator and its snapshotFlow (a
 * picture feels nothing); PullIndicator's animateFloatAsState hold (the refreshing frame holds alpha 1 at once).
 * Robolectric holds the clock still: nothing here moves, each frame is one instant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class PullLoadDraftScreenshotTest : ScreenshotBase() {

    // PostCard reads the post marks through Koin.
    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    // The cards write the age against the clock, so the data is dated from it, as the other drafts do.
    private val now = System.currentTimeMillis()

    private val posts = listOf(
        Post(
            id = "7392000000000000001",
            authorHandle = "sundarpichai",
            authorName = "Sundar Pichai",
            text = "Excited to share what the team has been building this year. More soon.",
            publishedAtMillis = now - 2 * 60 * 60 * 1000,
            permalink = "https://www.linkedin.com/posts/sundarpichai_activity-7392000000000000001-abcd",
            stats = PostStats(replies = 128, reposts = 412, likes = 9_300)
        ),
        Post(
            id = "7392000000000000002",
            authorHandle = "microsoft",
            authorName = "Microsoft",
            text = "Three things we learned shipping accessibility features to a billion people.",
            publishedAtMillis = now - 26 * 60 * 60 * 1000,
            permalink = "https://www.linkedin.com/posts/microsoft_activity-7392000000000000002-efgh",
            stats = PostStats(replies = 41, reposts = 97, likes = 2_100)
        ),
        Post(
            id = "7392000000000000003",
            authorHandle = "satyanadella",
            authorName = "Satya Nadella",
            text = "A good week for the people building in the open. Thank you to every maintainer.",
            publishedAtMillis = now - 3 * 24 * 60 * 60 * 1000,
            permalink = "https://www.linkedin.com/posts/satyanadella_activity-7392000000000000003-ijkl",
            stats = PostStats(replies = 20, reposts = 64, likes = 1_450)
        )
    )

    // Today: the disc at half the threshold, at the threshold, and refreshing at the threshold.
    @Test
    fun todayPulled() = capture("pull_today_pulled") { Screen(fraction = 0.5f, refreshing = false, proposed = false) }

    @Test
    fun todayOver() = capture("pull_today_over") { Screen(fraction = 1f, refreshing = false, proposed = false) }

    @Test
    fun todayRefreshing() = capture("pull_today_refreshing") { Screen(fraction = 1f, refreshing = true, proposed = false) }

    // Proposed: the same instants, the mark alone and bigger.
    @Test
    fun proposedPulled() = capture("pull_proposed_pulled") { Screen(fraction = 0.5f, refreshing = false, proposed = true) }

    @Test
    fun proposedOver() = capture("pull_proposed_over") { Screen(fraction = 1f, refreshing = false, proposed = true) }

    @Test
    fun proposedRefreshing() = capture("pull_proposed_refreshing") { Screen(fraction = 1f, refreshing = true, proposed = true) }

    // A profile loading: today's mark at the top right, then the Refresh icon kept and the load under the card.
    @Test
    fun profileToday() = capture("profile_load_today") { Profile(proposed = false) }

    @Test
    fun profileProposed() = capture("profile_load_proposed") { Profile(proposed = true) }

    // Edge: today's first frame at 200 % text and on a 360 dp phone.
    @Test
    fun edge() = captureEdge("pull_today_pulled") { Screen(fraction = 0.5f, refreshing = false, proposed = false) }

    // The page and the indicator over it, placed as TimelineScreen places PullIndicator: top centre, under the status bar.
    @Composable
    private fun Screen(fraction: Float, refreshing: Boolean, proposed: Boolean) {
        val look = LocalGlass.current
        val backdrop = rememberGlassBackdrop()
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop.takeIf { look != null }) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().then(if (look != null) Modifier.glassSource(backdrop, look) else Modifier)) {
                    Feed()
                }
                val place = Modifier.align(Alignment.TopCenter).padding(top = statusBarTop())
                if (proposed) {
                    ProposedIndicator(fraction, refreshing, place)
                } else {
                    TodayIndicator(fraction, refreshing, place)
                }
            }
        }
    }

    // A few Home cards, as the other drafts' HomeScreen lists them, in the list's side inset.
    @Composable
    private fun Feed() {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = LocalReadableInset.current)) {
            posts.forEach { PostCard(post = it, onClick = {}, onOpenLink = {}, onDownload = {}, unread = it === posts.first()) }
        }
    }

    /**
     * PullIndicator's own layer and disc: the mark slides down from the top edge by fraction x threshold, its alpha the
     * pull's fade-in or 1 while refreshing; in glass the disc bends the list under it with no shadow, else a
     * surfaceContainerHigh disc with a 3 dp shadow. 44 dp mark in a 56 dp disc.
     * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/ui/component/PullIndicator.kt#PullIndicator
     */
    @Composable
    private fun TodayIndicator(fraction: Float, refreshing: Boolean, modifier: Modifier) {
        val threshold = PullToRefreshDefaults.PositionalThreshold
        Box(
            modifier.graphicsLayer {
                translationY = fraction * threshold.toPx() - this.size.height
                alpha = max((fraction * 2f).coerceIn(0f, 1f), if (refreshing) 1f else 0f)
            }
        ) {
            val look = LocalGlass.current
            val backdrop = LocalGlassBackdrop.current
            val glass = look != null && backdrop != null
            Surface(
                shape = CircleShape,
                color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = if (glass) 0.dp else 3.dp,
                modifier = if (glass) Modifier.size(56.dp).glassFloating(backdrop!!, CircleShape, look!!) else Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { MarkStandIn(44.dp) }
            }
        }
    }

    // The proposal: the same layer and instants, no Surface, no glass, no shadow; the mark alone at 72 dp.
    @Composable
    private fun ProposedIndicator(fraction: Float, refreshing: Boolean, modifier: Modifier) {
        val threshold = PullToRefreshDefaults.PositionalThreshold
        Box(
            modifier.graphicsLayer {
                translationY = fraction * threshold.toPx() - this.size.height
                alpha = max((fraction * 2f).coerceIn(0f, 1f), if (refreshing) 1f else 0f)
            }
        ) {
            MarkStandIn(72.dp)
        }
    }

    // The profile: its card at the top, the page's own load under it, a profile with nothing yet.
    @Composable
    private fun Profile(proposed: Boolean) {
        Column(Modifier.fillMaxSize().padding(top = statusBarTop())) {
            ProfileCard(proposed)
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                MarkStandIn(if (proposed) 72.dp else 96.dp)
            }
        }
    }

    /**
     * FeedScreen.kt's ProfileZone with fixed data: Back top left, the load top right, the avatar, the name, the handle
     * and Follow, inset 12 dp in the card. Today: the 24 dp mark in a 44 dp slot (:398). Proposed: the Refresh icon
     * stays, disabled (alpha 0.38). Follow is BoldButton, which FollowButton draws in glass as its own zone.
     * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/feed/FeedScreen.kt#ProfileZone
     */
    @Composable
    private fun ProfileCard(proposed: Boolean) {
        Zone(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = ZoneGap)
        ) {
            Box {
                Box(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp)) {
                    BannerAction(icon = LinkedOutIcons.ArrowBack, label = "Back", onClick = {})
                }
                Box(Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 12.dp)) {
                    if (proposed) {
                        Box(Modifier.alpha(0.38f)) {
                            BannerAction(icon = LinkedOutIcons.Refresh, label = "Refresh", onClick = {})
                        }
                    } else {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { MarkStandIn(24.dp) }
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Avatar(url = null, name = "CHU Nantes", size = 88.dp)
                    Text("CHU Nantes", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(
                        "@chu-nantes",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    BoldButton(onClick = {}, filled = true, modifier = Modifier.padding(top = 8.dp)) { Text("Follow") }
                }
            }
        }
    }

    // LoadingMark's two bitmaps as plain images, at any size: the running mark's place in a picture.
    @Composable
    private fun MarkStandIn(size: Dp) {
        val body = ImageBitmap.imageResource(R.drawable.lo_mark_body)
        val shine = ImageBitmap.imageResource(R.drawable.lo_mark_shine)
        Box(Modifier.size(size)) {
            Image(body, contentDescription = null, modifier = Modifier.fillMaxSize())
            Image(shine, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}
