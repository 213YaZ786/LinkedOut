package com.linkedout.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostStats
import com.linkedout.app.di.appModule
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.ui.component.AppBarWithSearch
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.BoldIconButton
import com.linkedout.app.ui.component.CapsuleMenuOffset
import com.linkedout.app.ui.component.DropdownMenuGroup
import com.linkedout.app.ui.component.DropdownMenuItem
import com.linkedout.app.ui.component.FolderSwitch
import com.linkedout.app.ui.component.PostCard
import com.linkedout.app.ui.component.SearchCapsuleField
import com.linkedout.app.ui.component.SearchCapsuleHint
import com.linkedout.app.ui.component.SelectableDropdownMenuItem
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.relativeTime
import com.linkedout.app.ui.glass.LocalGlassBackdrop
import com.linkedout.app.ui.icon.LinkedOutIcons
import com.linkedout.app.ui.theme.zone
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
 * DRAFT for the user's yes (requests.md point 43, MTGA's point 47), nothing of app/src/main changes: the folder menu of
 * Accounts and the feed menu of Home open 8 dp under the capsule, not on its edge. Two pictures of each screen, the same
 * screen with the menu open: menu_under_capsule_now (the menu's top edge 8 dp under the FolderSwitch pill, GlassMenu's
 * default MenuContentOffset, which is the capsule's own bottom edge since the pill sits 8 dp inside it) and
 * menu_under_capsule_draft (the menu's top edge CapsuleMenuOffset.y under the pill, which is 8 dp under the capsule: the
 * constant of ui/component/FeedBanner.kt is read, not retyped); Home's are menu_under_capsule_home_now and
 * menu_under_capsule_home_draft. Each on phone and tablet, light and dark, plus the 200 % text and 360 dp edges.
 *
 * GlassMenu is a Popup and Robolectric draws no Popup, so the menu is drawn inline over a scrim, as MenuOverScreen
 * (JobsDraftScreenshotTest.kt) does for the other drafts, here with the gap as a parameter (MenuOverScreen keeps its 8 dp);
 * the groups and rows are the app's own (ui/component/GlassMenu.kt: DropdownMenuGroup, SelectableDropdownMenuItem with its
 * counts, DropdownMenuItem), the capsule is the app's own AppBarWithSearch with its FolderSwitch. The anchor is the pill's
 * bottom edge and middle, where GlassMenu's AnchorPositionProvider gives it.
 *
 * Ports:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/AccountsTopDraftScreenshotTest.kt#AccountsTopDraftScreenshotTest
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/screenshot/HomeTopDraftScreenshotTest.kt#HomeTopDraftScreenshotTest
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/screenshot/AccountsTopDraftScreenshotTest.kt#AccountsTopDraftScreenshotTest
 * Compared: https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SearchBar.kt#AppBarWithSearch
 * not ported: MTGA's AccountsTopDraftScreenshotTest now() and draft() (the capsule is the app's own, built in
 * TopCapsuleDraftScreenshotTest), its FeedBanner, MenuPill, ZoneSurface, FolderLabel, AccountRow and the Tune and ChevronRight
 * copies (the app's LinkedOutIcons has both)
 * not ported: LinkedOut's HomeTopDraftScreenshotTest a, b, c, d and now (the title, age and alert layouts the capsule replaced),
 * its AlertAction, WarningRounded, HeaderRow, FeedRow, Age and HomeTitle; AccountsTopDraftScreenshotTest's c, empty, d, TopRow,
 * its paste Field and MenuPill (MenuOverScreen with the pill's menu: replaced by the capsule's switch)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class MenuUnderCapsuleDraftScreenshotTest : ScreenshotBase() {

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

    // The cards write the age against the clock, so the data is dated from it.
    private val now = System.currentTimeMillis()

    private class Sample(val name: String, val headline: String, val hoursAgo: Long, val unread: Int)

    private val samples = listOf(
        Sample("Satya Nadella", "Chairman and CEO at Microsoft", 2, 3),
        Sample("Microsoft", "Software Development", 36, 5),
        Sample("Sundar Pichai", "CEO at Google and Alphabet", 84, 0),
        Sample("Google DeepMind", "Research Services", 120, 2)
    )

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
            text = "A good week for the people building with our tools. Thank you to every team.",
            publishedAtMillis = now - 3 * 24 * 60 * 60 * 1000,
            permalink = "https://www.linkedin.com/posts/satyanadella_activity-7392000000000000003-ijkl",
            stats = PostStats(replies = 20, reposts = 64, likes = 1_450)
        )
    )

    private val folders = listOf("Main" to 5, "Clients" to 4, "Tech leaders" to 3)

    // GlassMenu's MenuContentOffset (private there): the menu's top edge 8 dp under the pill, on the capsule's edge.
    private val nowGap = 8.dp

    // The kit's constant: 8 dp under the capsule.
    private val draftGap = CapsuleMenuOffset.y

    @Test
    fun accountsNow() = capture("menu_under_capsule_now") { AccountsMenu(nowGap) }

    @Test
    fun accountsDraft() = capture("menu_under_capsule_draft") { AccountsMenu(draftGap) }

    @Test
    fun homeNow() = capture("menu_under_capsule_home_now") { HomeMenu(nowGap) }

    @Test
    fun homeDraft() = capture("menu_under_capsule_home_draft") { HomeMenu(draftGap) }

    // Edge: the pictures at 200 % text and on a 360 dp phone (ScreenshotBase#captureEdge).
    @Test
    fun accountsNowEdge() = captureEdge("menu_under_capsule_now") { AccountsMenu(nowGap) }

    @Test
    fun accountsDraftEdge() = captureEdge("menu_under_capsule_draft") { AccountsMenu(draftGap) }

    @Test
    fun homeNowEdge() = captureEdge("menu_under_capsule_home_now") { HomeMenu(nowGap) }

    @Test
    fun homeDraftEdge() = captureEdge("menu_under_capsule_home_draft") { HomeMenu(draftGap) }

    // AccountsScreen's capsule and its folders menu (AccountsScreen.kt: All with its count, the folders, then in a group of
    // its own "Manage folders"), open.
    @Composable
    private fun AccountsMenu(gap: Dp) {
        var anchor by remember { mutableStateOf(IntOffset.Zero) }
        MenuUnderScreen(
            anchor = anchor,
            gap = gap,
            screen = {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Box(Modifier.padding(horizontal = 16.dp + LocalReadableInset.current, vertical = 8.dp)) {
                        AppBarWithSearch(
                            inputField = {
                                SearchCapsuleField(
                                    searchIcon = LinkedOutIcons.Search,
                                    value = "",
                                    onValueChange = {},
                                    hints = listOf("Search 12 accounts or open an address", "Search 12 accounts"),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions.Default
                                )
                            },
                            actions = {
                                BoldIconButton(onClick = {}) { Icon(LinkedOutIcons.Paste, contentDescription = "Paste a profile address") }
                            },
                            navigationIcon = { CapsuleFolder("Accounts", onFolder = { anchor = it }) }
                        )
                    }
                    Column(
                        Modifier.padding(horizontal = 16.dp + LocalReadableInset.current),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        samples.forEach { AccountCard(it) }
                    }
                }
            }
        ) {
            DropdownMenuGroup(index = 0, count = 2) {
                SelectableDropdownMenuItem(selected = true, onClick = {}, text = "All", count = 12)
                folders.forEach { (name, count) -> SelectableDropdownMenuItem(selected = false, onClick = {}, text = name, count = count) }
            }
            DropdownMenuGroup(index = 1, count = 2) {
                DropdownMenuItem(
                    text = "Manage folders",
                    onClick = {},
                    leadingIcon = { Icon(LinkedOutIcons.Tune, contentDescription = null, modifier = Modifier.size(24.dp)) },
                    trailingIcon = {
                        Icon(
                            LinkedOutIcons.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
    }

    // TimelineScreen's capsule and its feed menu (the folders with their counts, then Liked and Archived), open.
    @Composable
    private fun HomeMenu(gap: Dp) {
        var anchor by remember { mutableStateOf(IntOffset.Zero) }
        MenuUnderScreen(
            anchor = anchor,
            gap = gap,
            screen = {
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = LocalReadableInset.current)) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        AppBarWithSearch(
                            inputField = { SearchCapsuleHint(LinkedOutIcons.Search, listOf("Search saved posts"), onClick = {}) },
                            navigationIcon = { CapsuleFolder("Home", onFolder = { anchor = it }) }
                        )
                    }
                    posts.forEachIndexed { i, post ->
                        PostCard(post = post, onClick = {}, onOpenLink = {}, onDownload = {}, unread = i == 0)
                    }
                }
            }
        ) {
            DropdownMenuGroup(index = 0, count = 2) {
                SelectableDropdownMenuItem(selected = true, onClick = {}, text = "All", count = 12)
                folders.forEach { (name, count) -> SelectableDropdownMenuItem(selected = false, onClick = {}, text = name, count = count) }
            }
            DropdownMenuGroup(index = 1, count = 2) {
                listOf("Liked", "Archived").forEach { SelectableDropdownMenuItem(selected = false, onClick = {}, text = it) }
            }
        }
    }

    // The capsule's navigation icon as the screens build it: the heading wrapper, the folder pill inside it; [onFolder] gets
    // the point under the pill's middle, where the menu opens.
    @Composable
    private fun CapsuleFolder(screen: String, onFolder: (IntOffset) -> Unit) {
        Box(Modifier.semantics { heading(); contentDescription = screen }) {
            FolderSwitch(
                icon = LinkedOutIcons.Folder,
                trailingIcon = LinkedOutIcons.ExpandMore,
                label = "All",
                expanded = true,
                onClick = {},
                modifier = Modifier.onGloballyPositioned {
                    val at = it.positionInRoot()
                    onFolder(IntOffset((at.x + it.size.width / 2).toInt(), (at.y + it.size.height).toInt()))
                }
            )
        }
    }

    // MenuOverScreen's body (JobsDraftScreenshotTest.kt) with [gap] as a parameter: a scrim on the screen, then the menu under
    // the anchor, kept 12 dp from the screen's sides as GlassMenu's MenuHorizontalMargin, in no glass backdrop as GlassMenu
    // provides. Menu.kt's DropdownMenu `offset` is the gap.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#DropdownMenu
    @Composable
    private fun MenuUnderScreen(
        anchor: IntOffset,
        gap: Dp,
        screen: @Composable () -> Unit,
        menu: @Composable ColumnScope.() -> Unit
    ) {
        var origin by remember { mutableStateOf(IntOffset.Zero) }
        var width by remember { mutableStateOf(0) }
        val density = LocalDensity.current
        Box(
            Modifier.fillMaxSize().onGloballyPositioned {
                val at = it.positionInRoot()
                origin = IntOffset(at.x.toInt(), at.y.toInt())
                width = it.size.width
            }
        ) {
            screen()
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)))
            val menuWidth = with(density) { MenuWidth.roundToPx() }
            val margin = with(density) { 12.dp.roundToPx() }
            val below = with(density) { gap.roundToPx() }
            // The menu is centred under the pill: [anchor].x is the pill's middle.
            val x = (anchor.x - origin.x - menuWidth / 2).coerceIn(margin, (width - menuWidth - margin).coerceAtLeast(margin))
            CompositionLocalProvider(LocalGlassBackdrop provides null) {
                Column(
                    Modifier.offset { IntOffset(x, anchor.y - origin.y + below) }.width(MenuWidth),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    content = menu
                )
            }
        }
    }

    // AccountCard's zone, without its hold menu (TopCapsuleDraftScreenshotTest's copy).
    @Composable
    private fun AccountCard(sample: Sample) {
        Zone(color = MaterialTheme.colorScheme.zone, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Avatar(url = null, name = sample.name, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(sample.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        sample.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        relativeTime(now - sample.hoursAgo * 60 * 60 * 1000 - 30 * 60 * 1000),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sample.unread > 0) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 28.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                sample.unread.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
