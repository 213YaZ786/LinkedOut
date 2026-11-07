package com.linkedout.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostStats
import com.linkedout.app.di.appModule
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.FloatingPane
import com.linkedout.app.ui.component.PostCard
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
 * DRAFT for the user's yes (requests.md points 9 and 10), nothing of app/src/main changes: Home's top as one line,
 * "Home" and "Updated 1h ago" side by side on the same baseline between the two round action slots, and under it one
 * pill in place of the feed chips, which opens the Jobs menu (the same pieces, JobsDraftScreenshotTest.kt) with the feeds:
 * All and the folders, then Liked and Archived. The pieces are the screens' own:
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/ui/component/ScreenBanner.kt#ScreenBanner
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/timeline/TimelineScreen.kt#TimelineScreen
 * and the menu's selected row
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#SelectableDropdownMenuItem
 * (drawn by JobsDraftScreenshotTest's SelectableDropdownMenuItem). The chevron:
 * https://github.com/google/material-design-icons/blob/master/symbols/android/expand_more/materialsymbolsoutlined/expand_more_24px.xml
 * not ported: TimelineScreen's FailureDialog, TimelineFooter, EmptyState, LOAD_MORE_THRESHOLD, FAILURES_SHOWN, the pull to
 * refresh, the list's recording of posts read, the empty and loading states (fixed data here), and the viewModel
 * not ported: ScreenBanner's BannerAction (used as it is), its two lines title with TextAutoSize (one line here)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class HomeDraftScreenshotTest : ScreenshotBase() {

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

    private val unread = Post(
        id = "7392000000000000001",
        authorHandle = "sundarpichai",
        authorName = "Sundar Pichai",
        text = "Excited to share what the team has been building this year. More soon.",
        publishedAtMillis = now - 2 * 60 * 60 * 1000,
        permalink = "https://www.linkedin.com/posts/sundarpichai_activity-7392000000000000001-abcd",
        stats = PostStats(replies = 128, reposts = 412, likes = 9_300)
    )

    private val read = Post(
        id = "7392000000000000002",
        authorHandle = "microsoft",
        authorName = "Microsoft",
        text = "Three things we learned shipping accessibility features to a billion people.",
        publishedAtMillis = now - 26 * 60 * 60 * 1000,
        permalink = "https://www.linkedin.com/posts/microsoft_activity-7392000000000000002-efgh",
        stats = PostStats(replies = 41, reposts = 97, likes = 2_100)
    )

    private val third = Post(
        id = "7392000000000000003",
        authorHandle = "satyanadella",
        authorName = "Satya Nadella",
        text = "A good week for the people building with our tools. Thank you to every team.",
        publishedAtMillis = now - 3 * 24 * 60 * 60 * 1000,
        permalink = "https://www.linkedin.com/posts/satyanadella_activity-7392000000000000003-ijkl",
        stats = PostStats(replies = 20, reposts = 64, likes = 1_450)
    )

    private val folders = listOf("Main", "test")

    @Test
    fun home() = capture("home_draft") { HomeWithMenu(open = false) }

    @Test
    fun homeFeeds() = capture("home_feeds_draft") { HomeWithMenu(open = true) }

    // The screen with the feed menu over it, shared with the Jobs draft: a scrim, then the menu under the pill.
    @Composable
    private fun HomeWithMenu(open: Boolean) {
        var anchor by remember { mutableStateOf(IntOffset.Zero) }
        MenuOverScreen(open = open, anchor = anchor, screen = { HomeDraft(onFeedPill = { anchor = it }) }) {
            // No Clear row: a feed is chosen, not cleared.
            DropdownMenuGroup(index = 0, count = 2) {
                SelectableDropdownMenuItem(selected = true, onClick = {}, text = "All")
                folders.forEach { SelectableDropdownMenuItem(selected = false, onClick = {}, text = it) }
            }
            DropdownMenuGroup(index = 1, count = 2) {
                listOf("Liked", "Archived").forEach { SelectableDropdownMenuItem(selected = false, onClick = {}, text = it) }
            }
        }
    }

    @Composable
    private fun HomeDraft(onFeedPill: (IntOffset) -> Unit) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            HomeBanner(title = "Home", subtitle = "Updated 1h ago")
            FeedPill(selected = "All", onFeedPill = onFeedPill)
            Spacer(Modifier.height(8.dp))
            PostCard(post = unread, onClick = {}, onOpenLink = {}, onDownload = {}, unread = true)
            PostCard(post = read, onClick = {}, onOpenLink = {}, onDownload = {})
            PostCard(post = third, onClick = {}, onOpenLink = {}, onDownload = {})
        }
    }

    // ScreenBanner's row, the title and the subtitle no longer in a Column but side by side on one line, 8 dp apart, aligned
    // on their baseline, the pair centred between the two action slots (both slots keep their room, Search at the right as
    // Home has it).
    @Composable
    private fun HomeBanner(title: String, subtitle: String) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = BannerActionSize + 16.dp)
                .padding(horizontal = 16.dp, vertical = BannerGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) {}
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.primary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline()
                )
            }
            Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) {
                BannerAction(icon = LinkedOutIcons.Search, label = "Search saved posts", onClick = {})
            }
        }
    }

    // One pill in place of FeedChips: the folder icon, the feed shown and a small down chevron, as the Jobs "Filters" pill.
    // [onFeedPill] gives the point under the pill's middle, where the menu opens.
    @Composable
    private fun FeedPill(selected: String, onFeedPill: (IntOffset) -> Unit) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            FloatingPane(
                shape = CircleShape,
                onClick = {},
                modifier = Modifier.onGloballyPositioned {
                    val at = it.positionInRoot()
                    onFeedPill(IntOffset((at.x + it.size.width / 2).toInt(), (at.y + it.size.height).toInt()))
                }
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(LinkedOutIcons.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(selected, style = MaterialTheme.typography.labelLarge)
                    Icon(ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    // ScreenBanner's BannerGap and BannerActionSize (private there).
    private val BannerGap = 8.dp
    private val BannerActionSize = 44.dp

    // expand_more_24px.xml, Material Symbols outlined: viewport 960, the path as the file gives it.
    private val ExpandMore: ImageVector = ImageVector.Builder(
        name = "ExpandMore",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).addPath(
        pathData = PathParser().parsePathString("M480,615L240,375L296,319L480,503L664,319L720,375L480,615Z").toNodes(),
        fill = SolidColor(Color.White)
    ).build()
}
