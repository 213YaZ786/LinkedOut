package com.linkedout.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostStats
import com.linkedout.app.di.appModule
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.BarTitle
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.FloatingTopBar
import com.linkedout.app.ui.component.PostCard
import com.linkedout.app.ui.component.ScreenBanner
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.component.plus
import com.linkedout.app.ui.component.scrolledFromTop
import com.linkedout.app.ui.component.statusBarTop
import com.linkedout.app.ui.component.underFloatingTopBar
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
 * DRAFT for the user's yes (requests.md point 17), nothing of app/src/main changes: the reader kit's FloatingTopBar on
 * a profile's Feed and on PostDetail, as MTGA builds both. Pictures: feed_scrolled_now (today: the ScreenBanner is the
 * list's first row, so once it has scrolled away nothing is at the top), feed_scrolled_draft (the bar with Back, the
 * account's name and Refresh, the list under it with underFloatingTopBar), feed_top_draft (the same screen at rest:
 * the profile header in view and no bar: the header carries Back and Refresh while it is on screen, after the user's
 * 2026-11-08 answer), post_detail_draft (PostDetail scrolled, title "Post").
 *
 * Ports: MTGA's FeedScreen, the Scaffold with the FloatingTopBar (title only once the header has left, Back and
 * Refresh) and the LazyColumn with contentPadding plus the Scaffold's padding and underFloatingTopBar
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/FeedScreen.kt#FeedScreen
 * and its ProfileHeader (the name above the handle, since the bar no longer carries it at rest)
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/FeedScreen.kt#ProfileHeader
 * with PostDetail's own Scaffold and "Post" bar
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/post/PostDetailScreen.kt#PostDetailScreen
 * The current screen is LinkedOut's FeedScreen, whose ProfileZone is private and so copied here:
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/feed/FeedScreen.kt#ProfileZone
 *
 * not ported: FeedScreen's viewModel and its load, follow state and unfollow dialog, MediaViewer, rememberInlineTarget
 * and LocalInlinePlaying, the load more effect and footer, ErrorPanel, the loading mark, the Refresh spinner
 * (a Refresh icon is drawn), enterAlwaysScrollBehavior and its nestedScroll (a bar folding as the reader goes down is
 * motion, a picture cannot show it, so the bar is drawn unfolded), LocalDockPadding (LinkedOut has no dock), the
 * banner image and the real avatar (Robolectric loads no network picture), ProfileZone's company line, its meta line
 * joining location and school and compactCount (the draft header is short and fixed), PostDetail's viewModel, share action wiring, PostBody (private,
 * a PostCard with the post's text stands in), the replies chains and their "See all comments" button, ScrollUpButton.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class FloatingTopDraftScreenshotTest : ScreenshotBase() {

    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    // The card writes the age against the clock, so the posts are dated from it.
    private val now = System.currentTimeMillis()
    private val hour = 60 * 60 * 1000L

    private fun post(n: Int, text: String, ageHours: Long, replies: Int, reposts: Int, likes: Int) = Post(
        id = "739200000000000000$n",
        authorHandle = "sundarpichai",
        authorName = "Sundar Pichai",
        text = text,
        publishedAtMillis = now - ageHours * hour,
        permalink = "https://www.linkedin.com/posts/sundarpichai_activity-739200000000000000$n-abcd",
        stats = PostStats(replies = replies, reposts = reposts, likes = likes)
    )

    private val posts = listOf(
        post(1, "Excited to share what the team has been building this year. More soon.", 2, 128, 412, 9_300),
        post(2, "Three things we learned shipping accessibility features to a billion people.", 26, 41, 97, 2_100),
        post(3, "A good week for the people building with our tools. Thank you to every team.", 3 * 24, 20, 64, 1_450),
        post(4, "Notes from the keynote: smaller models, longer context, and a lot more on-device work.", 5 * 24, 77, 130, 4_800),
        post(5, "Proud of the engineers who stayed up through the night to ship this on time.", 7 * 24, 12, 33, 990)
    )

    private val name = "Sundar Pichai"
    private val handle = "sundarpichai"

    @Test
    fun feedScrolledNow() = capture("feed_scrolled_now") {
        // FeedScreen today: the banner is the list's first row, then the profile zone, then the posts. Scrolled
        // into the first post, the banner has left and nothing is at the top.
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = 2, initialFirstVisibleItemScrollOffset = 160)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = LocalReadableInset.current,
                end = LocalReadableInset.current,
                top = statusBarTop()
            )
        ) {
            item(key = "banner") {
                ScreenBanner(
                    title = name,
                    subtitle = "Read via www.linkedin.com, updated 4m ago",
                    leading = { BannerAction(icon = LinkedOutIcons.ArrowBack, label = "Back", onClick = {}) },
                    trailing = { BannerAction(icon = LinkedOutIcons.Refresh, label = "Refresh", onClick = {}) }
                )
            }
            item(key = "header") { ProfileZoneNow() }
            items(posts, key = { it.id }) { PostCard(post = it, onClick = {}, onOpenLink = {}, onDownload = {}) }
        }
    }

    @Test
    fun feedScrolledDraft() = capture("feed_scrolled_draft") { FeedDraft(scrolled = true) }

    @Test
    fun feedTopDraft() = capture("feed_top_draft") {
        // At rest there is no bar: the header card carries Back and Refresh, and starts at the status bar plus 8 dp (the
        // list's 2 dp and the zone's 6 dp margin), so the room the bar took goes to the profile. The list is MTGA's
        // FeedScreen LazyColumn without its Scaffold and bar:
        // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/FeedScreen.kt#FeedScreen
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = LocalReadableInset.current,
                end = LocalReadableInset.current,
                top = statusBarTop() + 2.dp
            )
        ) {
            item(key = "header") { ProfileZoneBody(showName = true, actions = true) }
            items(posts, key = { it.id }) { PostCard(post = it, onClick = {}, onOpenLink = {}, onDownload = {}) }
        }
    }

    @Test
    fun postDetailDraft() = capture("post_detail_draft") { PostDetailDraft() }

    // MTGA's FeedScreen: a Scaffold whose topBar is the FloatingTopBar, the list's contentPadding the Scaffold's
    // padding, underFloatingTopBar on the list. The title shows only once the header has left, as `headerGone`.
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun FeedDraft(scrolled: Boolean) {
        // Scrolled: into the first post, the header gone. At rest: the top.
        val listState = if (scrolled) {
            rememberLazyListState(initialFirstVisibleItemIndex = 1, initialFirstVisibleItemScrollOffset = 160)
        } else {
            rememberLazyListState()
        }
        // MTGA reads it from the list state (firstVisibleItemIndex > 0). The picture is taken before that state follows
        // the list's first measure, so the draft says what the state would say.
        val headerGone = scrolled
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                FloatingTopBar(
                    title = { if (headerGone) BarTitle(name) },
                    navigationIcon = { BarAction(LinkedOutIcons.ArrowBack, "Back") },
                    actions = { BarAction(LinkedOutIcons.Refresh, "Refresh") }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    // Horizontal padding rather than a narrower list, so a drag in the margins of a wide window
                    // scrolls too (MTGA's comment).
                    contentPadding = PaddingValues(
                        start = LocalReadableInset.current,
                        end = LocalReadableInset.current
                    ).plus(padding),
                    modifier = Modifier.fillMaxSize().underFloatingTopBar(
                        barHeight = padding.calculateTopPadding(),
                        scrolled = listState::scrolledFromTop
                    )
                ) {
                    item(key = "header") { ProfileHeaderDraft() }
                    items(posts, key = { it.id }) { PostCard(post = it, onClick = {}, onOpenLink = {}, onDownload = {}) }
                }
            }
        }
    }

    // MTGA's PostDetailScreen: the same Scaffold with BarTitle("Post"), Back and Share, the list scrolled under it.
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PostDetailDraft() {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = 0, initialFirstVisibleItemScrollOffset = 120)
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                FloatingTopBar(
                    title = { BarTitle("Post") },
                    navigationIcon = { BarAction(LinkedOutIcons.ArrowBack, "Back") },
                    actions = { BarAction(LinkedOutIcons.Share, "Share") }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().underFloatingTopBar(
                        barHeight = padding.calculateTopPadding(),
                        scrolled = listState::scrolledFromTop
                    ),
                    contentPadding = PaddingValues(
                        start = LocalReadableInset.current,
                        end = LocalReadableInset.current
                    ).plus(padding)
                ) {
                    item(key = "main") {
                        // Stands in for PostBody, which is private: the post in full, then its replies.
                        PostCard(
                            post = posts[0].copy(
                                text = posts[0].text + " Thank you to everyone who tested early versions and sent us " +
                                    "their notes. We read every one of them, and many of them changed what we shipped."
                            ),
                            onClick = {}, onOpenLink = {}, onDownload = {}
                        )
                    }
                    item(key = "replieslabel") { SectionLabel("Replies") }
                    items(posts.drop(1), key = { "r" + it.id }) { PostCard(post = it, onClick = {}, onOpenLink = {}, onDownload = {}) }
                }
            }
        }
    }

    // The bar's round actions: a transparent tap target inside the glass zone, which is what IconButton is in MTGA
    // (the guard refuses a Material IconButton in app code, and a button inside a glass zone stays transparent).
    @Composable
    private fun BarAction(icon: ImageVector, label: String) {
        Surface(onClick = {}, shape = CircleShape, color = Color.Transparent) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            }
        }
    }

    // PostDetailScreen's SectionLabel (private there).
    @Composable
    private fun SectionLabel(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
        )
    }

    // LinkedOut's ProfileZone as built today (the name is in the banner above it, so not repeated), fixed data.
    @Composable
    private fun ProfileZoneNow() {
        ProfileZoneBody(showName = false)
    }

    // MTGA's ProfileHeader: the bar has no title at rest, so the name is the header's first line.
    @Composable
    private fun ProfileHeaderDraft() {
        ProfileZoneBody(showName = true)
    }

    @Composable
    private fun ProfileZoneBody(showName: Boolean, actions: Boolean = false) {
        Zone(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = ZoneGap)
        ) {
            Box {
            // The account capsule carries Back and Refresh while it is on screen (the user, 2026-11-08): the same
            // round glass actions as BannerAction, 44 dp, inset 12 dp from the card's corners, so the avatar's top sits
            // on their line and no bar is needed above the card.
            if (actions) {
                Box(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp)) {
                    BannerAction(icon = LinkedOutIcons.ArrowBack, label = "Back", onClick = {})
                }
                Box(Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 12.dp)) {
                    BannerAction(icon = LinkedOutIcons.Refresh, label = "Refresh", onClick = {})
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (actions) 12.dp else 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Avatar(url = null, name = name, size = 88.dp)
                if (showName) {
                    Text(
                        name,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    "@$handle",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    "CEO of Google and Alphabet.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 520.dp).padding(top = 4.dp)
                )
                Text(
                    "Mountain View, California",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    "12,345 posts, 5 following, 7.2M followers",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
                BoldButton(filled = true, onClick = {}, modifier = Modifier.padding(top = 8.dp)) { Text("Follow") }
            }
            }
        }
    }
}
