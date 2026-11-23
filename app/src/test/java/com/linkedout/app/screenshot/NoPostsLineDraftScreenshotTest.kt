package com.linkedout.app.screenshot

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.R
import com.linkedout.app.di.appModule
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.component.statusBarTop
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

/**
 * DRAFT for the user's yes (requests.md point 36), nothing of app/src/main changes: a person's page that LinkedIn shows
 * no post of to a visitor (the log reads "PARSE ok, 0 posts"; in/yanis).
 * profile_no_posts_today: FeedScreen's list is the header item, then, with no post, only the loading item while it
 * loads and `return@LazyColumn`: the card and nothing under it, an empty page.
 * profile_no_posts_proposed: under the card, centred on the page, one line drawn on the page with no zone (it does
 * nothing, so it has none): bodyMedium, onSurfaceVariant, 32 dp above and below, 16 dp at the sides, wrapping on two
 * lines when narrow or at 200 % text. Shown only when the page was read (feed != null), not loading, no error, no
 * post. No icon, no button, no red: it is not an error.
 * profile_no_posts_loading_proposed: while loading the line is not there, the 72 dp mark stand-in under the card, as
 * PullLoadDraftScreenshotTest's profile_load_proposed (the Refresh icon disabled, alpha 0.38).
 * Edge pictures (200 % text, 360 dp phone): the first frame as asked, and the proposed one, the only one that shows the
 * line wrapping.
 * Ports (the frame structure: the Screen, the frames, capture and captureEdge, Koin started for the page):
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/ProfileHeaderDraftScreenshotTest.kt#ProfileHeaderDraftScreenshotTest
 * The profile card is the local draft's redraw of ProfileZone and its mark stand-in (LoadingMark does not draw on
 * Robolectric):
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/feed/FeedScreen.kt#ProfileZone
 * not ported: HeaderContent, ShortContent and LongContent (one person here, no long case), DraftHeader (the banner,
 * its brush and the avatar ring: LinkedOut's card is redrawn instead), ProfileItem, FollowButton and its icons
 * PersonAddRounded and CheckRounded (LinkedOut's BoldButton is used), ProfileHeaderMetrics and MetricsItem (no counts
 * here), the symbols LocationOnRounded, CalendarMonthRounded, LinkRounded, the size constants, the posts under the
 * header (the point is the page with none), followingState and long and longEdge (no such frames here).
 * Robolectric holds the clock still: each frame is one instant.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class NoPostsLineDraftScreenshotTest : ScreenshotBase() {

    // The shared theme and look read the app's Koin module, as the other drafts do.
    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    @Test
    fun today() = capture("profile_no_posts_today") { Screen(line = false, loading = false) }

    @Test
    fun proposed() = capture("profile_no_posts_proposed") { Screen(line = true, loading = false) }

    @Test
    fun loadingProposed() = capture("profile_no_posts_loading_proposed") { Screen(line = false, loading = true) }

    // Edge: 200 % text and a 360 dp phone, the first frame, then the proposed one where the line wraps.
    @Test
    fun edge() = captureEdge("profile_no_posts_today") { Screen(line = false, loading = false) }

    @Test
    fun proposedEdge() = captureEdge("profile_no_posts_proposed") { Screen(line = true, loading = false) }

    // The page: the card at the top, then what FeedScreen would put under it.
    @Composable
    private fun Screen(line: Boolean, loading: Boolean) {
        Column(Modifier.fillMaxSize().padding(top = statusBarTop())) {
            ProfileCard(loading)
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { MarkStandIn(72.dp) }
            } else if (line) {
                NoPostsLine()
            }
        }
    }

    // The proposed line: on the page, no zone, centred, 16 dp at the sides and at least 32 dp above and below.
    @Composable
    private fun NoPostsLine() {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp), contentAlignment = Alignment.Center) {
            Text(
                "LinkedIn shows no posts to visitors who are not signed in",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    /**
     * FeedScreen.kt's ProfileZone with fixed data, as PullLoadDraftScreenshotTest redraws it: Back top left, Refresh top
     * right (disabled, alpha 0.38, while loading), the avatar, the name, the handle and Follow, inset 12 dp in the card.
     * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/feed/FeedScreen.kt#ProfileZone
     */
    @Composable
    private fun ProfileCard(loading: Boolean) {
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
                    Box(Modifier.alpha(if (loading) 0.38f else 1f)) {
                        BannerAction(icon = LinkedOutIcons.Refresh, label = "Refresh", onClick = {})
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Avatar(url = null, name = "Yanis", size = 88.dp)
                    Text("Yanis", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(
                        "@yanis",
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
