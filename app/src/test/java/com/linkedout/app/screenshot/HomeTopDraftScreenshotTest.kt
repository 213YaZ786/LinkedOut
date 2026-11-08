package com.linkedout.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostStats
import com.linkedout.app.di.appModule
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.FloatingPane
import com.linkedout.app.ui.component.GlassTarget
import com.linkedout.app.ui.component.MenuPill
import com.linkedout.app.ui.component.PostCard
import com.linkedout.app.ui.glass.LocalGlass
import com.linkedout.app.ui.glass.LocalGlassBackdrop
import com.linkedout.app.ui.glass.glassZone
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
 * DRAFT for the user's yes (requests.md point 11), nothing of app/src/main changes: Home's top with every button shown
 * (the alert at the left, Search at the right), in three layouts:
 * home_top_now (as built), home_top_a (one row, the feed pill is the title, the age under it), home_top_b (the title
 * row, then the pill and the age side by side). The row is the shape of Bluesky's header, a slot at each side and the
 * centre between them (Layout.Header.Slot, a flex_1 centred View, Layout.Header.Slot):
 * https://github.com/bluesky-social/social-app/blob/main/src/view/com/home/HomeHeaderLayoutMobile.tsx#HomeHeaderLayoutMobile
 * and the current banner is Home's own, drawn as TimelineScreen draws it (alert in errorContainer/error, Search):
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/timeline/TimelineScreen.kt#HomeBanner
 * not ported: HomeHeaderLayoutMobile's MenuButton, the Logo and its PressableScale, the Feeds link, the haptics and soft
 * reset, the hide on scroll (headerMinimalShellTransform), the tablet width style, StatusBarFade and
 * STATUS_BAR_FADE_STOPS (the app's own ground and status bar padding stay)
 * not ported: TimelineScreen's HomeBanner Layout that wraps the age under the title when the pair is too long (one line
 * here, fixed data), FailureDialog, TimelineFooter, EmptyState, the pull to refresh, the feed menu itself (the pill is
 * drawn closed), subtitle(state), the viewModel
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class HomeTopDraftScreenshotTest : ScreenshotBase() {

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

    @Test
    fun now() = capture("home_top_now") {
        Screen {
            HeaderRow(centre = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HomeTitle(Modifier.alignByBaseline())
                    Age(Modifier.alignByBaseline())
                }
            })
            FeedRow { FeedPill() }
        }
    }

    @Test
    fun a() = capture("home_top_a") {
        Screen {
            HeaderRow(centre = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // MenuPill asks for an icon, and A drops it: the same capsule, drawn with FloatingPane and a
                    // tighter padding so the pill and the age fit the row's height as far as possible.
                    CompositionLocalProvider(LocalGlassBackdrop provides null) {
                        FloatingPane(shape = CircleShape, onClick = {}) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("All", style = MaterialTheme.typography.labelLarge)
                                Icon(LinkedOutIcons.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Age()
                }
            })
        }
    }

    @Test
    fun b() = capture("home_top_b") {
        Screen {
            HeaderRow(centre = { HomeTitle() })
            FeedRow {
                FeedPill()
                Spacer(Modifier.size(12.dp))
                Age()
            }
        }
    }

    // C: b without its "Home" row and without a second row. One header row as HomeHeaderLayoutMobile draws it (a slot,
    // the centre taking the rest, a slot; the alert at the left, Search at the right), the feed pill with the age beside
    // it in the centre, the pair centred; when the centre is too narrow the age drops under the pill (FlowRow).
    // https://github.com/bluesky-social/social-app/blob/main/src/view/com/home/HomeHeaderLayoutMobile.tsx#HomeHeaderLayoutMobile
    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun c() = capture("home_top_c") {
        Screen {
            HeaderRow(alertInError = true, centre = {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    FeedPill()
                    Age()
                }
            })
        }
    }

    // D: c with the Material Symbols Rounded "warning" (fill 1) in place of the sharp Material Icons triangle. Built as
    // LinkedOutIcons.build does, a 960 viewport; the path is the symbol's own, unchanged.
    // https://github.com/google/material-design-icons/blob/master/symbols/android/warning/materialsymbolsrounded/warning_fill1_24px.xml#warning
    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun d() = capture("home_top_d") {
        Screen {
            HeaderRow(alertInError = true, alertIcon = WarningRounded, centre = {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    FeedPill()
                    Age()
                }
            })
        }
    }

    private val WarningRounded: ImageVector by lazy {
        ImageVector.Builder(
            name = "WarningRounded",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M109,840Q98,840 89,834.5Q80,829 75,820Q70,811 69.5,800.5Q69,790 75,780L445,140Q451,130 460.5,125Q470,120 480,120Q490,120 499.5,125Q509,130 515,140L885,780Q891,790 890.5,800.5Q890,811 885,820Q880,829 871,834.5Q862,840 851,840L109,840Z" +
                        "M480,720Q497,720 508.5,708.5Q520,697 520,680Q520,663 508.5,651.5Q497,640 480,640Q463,640 451.5,651.5Q440,663 440,680Q440,697 451.5,708.5Q463,720 480,720Z" +
                        "M480,600Q497,600 508.5,588.5Q520,577 520,560L520,440Q520,423 508.5,411.5Q497,400 480,400Q463,400 451.5,411.5Q440,423 440,440L440,560Q440,577 451.5,588.5Q463,600 480,600Z"
                ).toNodes(),
                fill = SolidColor(Color.Black)
            )
        }.build()
    }

    // BannerAction in glass draws its icon in the accent and ignores [tint] (`tint = if (glass == null) tint else
    // primary`), so for the draft the alert is a faithful copy of BannerAction's glass branch (GlassTarget, then a Surface
    // with glassZone, the same 44 dp box and 22 dp icon) whose icon takes the error colour. In the app this means a
    // `tint` that glass honours, in ScreenBanner.kt.
    // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/ui/component/ScreenBanner.kt#BannerAction
    @Composable
    private fun AlertAction(onClick: () -> Unit, icon: ImageVector = LinkedOutIcons.Warning) {
        val glass = LocalGlass.current
        GlassTarget(Modifier) {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = if (glass == null) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
                modifier = if (glass == null) Modifier else Modifier.glassZone(CircleShape, glass, lens = 1f)
            ) {
                Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = "What went wrong",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    // The screen under the top: the same sample cards as HomeDraftScreenshotTest.
    @Composable
    private fun Screen(top: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            top()
            Spacer(Modifier.height(8.dp))
            PostCard(post = unread, onClick = {}, onOpenLink = {}, onDownload = {}, unread = true)
            PostCard(post = read, onClick = {}, onOpenLink = {}, onDownload = {})
            PostCard(post = third, onClick = {}, onOpenLink = {}, onDownload = {})
        }
    }

    // HomeHeaderLayoutMobile's header: a slot, the centre taking the rest (flex_1, align_center), a slot. The slots are
    // the banner's 44 dp ones, both always drawn here: the alert at the left, Search at the right.
    @Composable
    private fun HeaderRow(
        alertInError: Boolean = false,
        alertIcon: ImageVector = LinkedOutIcons.Warning,
        centre: @Composable () -> Unit
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = BannerActionSize + 16.dp)
                .padding(horizontal = 16.dp, vertical = BannerGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) {
                if (alertInError) {
                    AlertAction(onClick = {}, icon = alertIcon)
                } else {
                    BannerAction(
                        icon = LinkedOutIcons.Warning,
                        label = "What went wrong",
                        onClick = {},
                        container = MaterialTheme.colorScheme.errorContainer,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { centre() }
            Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) {
                BannerAction(icon = LinkedOutIcons.Search, label = "Search saved posts", onClick = {})
            }
        }
    }

    // The row under the title, as TimelineScreen's chips item.
    @Composable
    private fun FeedRow(content: @Composable () -> Unit) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) { content() }
    }

    @Composable
    private fun FeedPill() = MenuPill(
        icon = LinkedOutIcons.Folder,
        label = "All",
        expanded = false,
        trailingIcon = LinkedOutIcons.ExpandMore,
        onClick = {}
    )

    @Composable
    private fun HomeTitle(modifier: Modifier = Modifier) = Text(
        "Home",
        style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.primary),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )

    @Composable
    private fun Age(modifier: Modifier = Modifier) = Text(
        "Updated 1h ago",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )

    // ScreenBanner's BannerGap and BannerActionSize (private there).
    private val BannerGap = 8.dp
    private val BannerActionSize = 44.dp
}
