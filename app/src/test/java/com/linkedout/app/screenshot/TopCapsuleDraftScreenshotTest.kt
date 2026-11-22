package com.linkedout.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostStats
import com.linkedout.app.di.appModule
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.ui.component.AppBarWithSearch
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.BoldIconButton
import com.linkedout.app.ui.component.FolderSwitch
import com.linkedout.app.ui.component.PostCard
import com.linkedout.app.ui.component.SearchCapsuleField
import com.linkedout.app.ui.component.SearchCapsuleHint
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.relativeTime
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
 * DRAFT for the user's yes (requests.md, MTGA's point 41, variant A2 chosen by the user), nothing of app/src/main
 * changes: Home's and Accounts' top as one glass capsule the width of the page, nothing under it, drawn with the kit
 * pieces already in main (ui/component/FeedBanner.kt: AppBarWithSearch, FolderSwitch, SearchCapsuleHint,
 * SearchCapsuleField; BannerAction in ScreenBanner.kt), as MTGA's draft and screens build it:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/TopArrangementDraftScreenshotTest.kt#TopArrangementDraftScreenshotTest
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#banner
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/accounts/AccountsScreen.kt#AccountsScreen
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SearchBar.kt#AppBarWithSearch
 * Home: the folder switch at the start, "Search saved posts" in the middle, the alert trailing on a failure only; no
 * "Updated" line (the pull disc is the app's own and has no place for it). Accounts: the folder switch, the field, then
 * Clear (typed text only) and Paste.
 * Pictures (prefix top_capsule): home_capsule, home_capsule_alert, home_capsule_folder, accounts_capsule,
 * accounts_capsule_typed, each on phone and tablet, light and dark, plus the 200 % text and 360 dp edges. Today's tops are
 * home_cards_* and accounts_filled_*.
 * not ported: TopArrangementDraftScreenshotTest's today, A, B arrangements, the pull and blur drafts (blurFeed, FeatherRing,
 * PullScreen, blur), the menu pictures (HomeMenu, AccountsMenu, FolderRows, CountedMenuItem: Robolectric draws no
 * Popup), LargeTitleTop, its local FolderSwitch, AppBarWithSearch and LargeFlexibleTopAppBar copies (the app's are used)
 * not ported: TimelineScreen's feedMenu and its GlassMenu, showFailures and FailureDialog, onOpenSearch, the AnimatedVisibility
 * of the alert, FeedBanner for nobody followed, the list, ReadMarks; AccountsScreen's viewModel, GlassMenu with its
 * counts and Manage folders, paste() and its clipboard read, open(), the candidate card, the notice, HoldMenu, EmptyState,
 * the nobody-followed GlassSearchField
 * not ported: AppBarWithSearch's SearchBarState, the expanded search, the colours of the scroll, elevation, scrollBehavior
 * and windowInsets (the app's AppBarWithSearch is its port)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class TopCapsuleDraftScreenshotTest : ScreenshotBase() {

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

    // HomeDraftScreenshotTest's first two posts.
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
        )
    )

    // AccountsTopDraftScreenshotTest's samples.
    private class Sample(val name: String, val headline: String, val hoursAgo: Long, val unread: Int)

    private val rows = listOf(
        Sample("Satya Nadella", "Chairman and CEO at Microsoft", 2, 3),
        Sample("Microsoft", "Software Development", 36, 5),
        Sample("Sundar Pichai", "CEO at Google and Alphabet", 84, 0),
        Sample("Google DeepMind", "Research Services", 120, 2)
    )

    private val homeHints = listOf("Search saved posts")
    private val accountsHints = listOf("Search 12 accounts or open an address", "Search 12 accounts")
    private val longFolder = "Tech leaders and recruiters in Europe"
    private val typed = "Micro"

    @Test
    fun homeA2() = capture("top_capsule_home_capsule") { HomeScreen { A2Capsule("All", alert = false) } }

    @Test
    fun homeA2Alert() = capture("top_capsule_home_capsule_alert") { HomeScreen { A2Capsule("All", alert = true) } }

    @Test
    fun homeA2Folder() = capture("top_capsule_home_capsule_folder") { HomeScreen { A2Capsule(longFolder, alert = false) } }

    @Test
    fun accountsA2() = capture("top_capsule_accounts_capsule") { AccountsScreen("") }

    @Test
    fun accountsA2Typed() = capture("top_capsule_accounts_capsule_typed") { AccountsScreen(typed) }

    @Test
    fun edgeHomeA2() = captureEdge("top_capsule_home_capsule") { HomeScreen { A2Capsule("All", alert = false) } }

    @Test
    fun edgeHomeA2Alert() = captureEdge("top_capsule_home_capsule_alert") { HomeScreen { A2Capsule("All", alert = true) } }

    @Test
    fun edgeHomeA2Folder() = captureEdge("top_capsule_home_capsule_folder") { HomeScreen { A2Capsule(longFolder, alert = false) } }

    @Test
    fun edgeAccountsA2() = captureEdge("top_capsule_accounts_capsule") { AccountsScreen("") }

    @Test
    fun edgeAccountsA2Typed() = captureEdge("top_capsule_accounts_capsule_typed") { AccountsScreen(typed) }

    // The screen under the top: the capsule, then the sample posts straight under it, in the list's side inset.
    @Composable
    private fun HomeScreen(top: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = LocalReadableInset.current)) {
            top()
            posts.forEach { PostCard(post = it, onClick = {}, onOpenLink = {}, onDownload = {}, unread = it === posts.first()) }
        }
    }

    // The screen under the top: the capsule, then the account cards, as AccountsScreen's list pads them.
    @Composable
    private fun AccountsScreen(query: String) {
        val visible = rows.filter { it.name.contains(query, ignoreCase = true) }
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.padding(horizontal = 16.dp + LocalReadableInset.current, vertical = 8.dp)) {
                AccountsCapsule(query)
            }
            Column(
                Modifier.padding(horizontal = 16.dp + LocalReadableInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visible.forEach { AccountCard(it) }
            }
        }
    }

    // TimelineScreen's banner: one glass capsule, nothing under it: the folder switch, "Search saved posts", the alert on a
    // failure only (BannerAction in errorContainer and error, trailing).
    @Composable
    private fun A2Capsule(folder: String, alert: Boolean) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            AppBarWithSearch(
                inputField = { SearchCapsuleHint(LinkedOutIcons.Search, homeHints, onClick = {}) },
                navigationIcon = { FolderHeading("Home", folder) },
                actions = if (alert) ({ AlertAction() }) else null
            )
        }
    }

    @Composable
    private fun AlertAction() {
        BannerAction(
            icon = LinkedOutIcons.Warning,
            label = "What went wrong",
            onClick = {},
            container = MaterialTheme.colorScheme.errorContainer,
            tint = MaterialTheme.colorScheme.error
        )
    }

    // AccountsScreen's capsule: the folder switch, the real field, Clear when text is typed, Paste always (the clipboard is
    // read only on a tap).
    @Composable
    private fun AccountsCapsule(query: String) {
        AppBarWithSearch(
            inputField = {
                SearchCapsuleField(
                    searchIcon = LinkedOutIcons.Search,
                    value = query,
                    onValueChange = {},
                    hints = accountsHints,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions.Default
                )
            },
            actions = {
                if (query.isNotEmpty()) {
                    BoldIconButton(onClick = {}) { Icon(LinkedOutIcons.Close, contentDescription = "Clear") }
                }
                BoldIconButton(onClick = {}) { Icon(LinkedOutIcons.Paste, contentDescription = "Paste a profile address") }
            },
            navigationIcon = { FolderHeading("Accounts", "All") }
        )
    }

    // The wrapper is the screen's heading for TalkBack, the switch inside stays its own button (the menu is not drawn:
    // Robolectric draws no Popup).
    @Composable
    private fun FolderHeading(screen: String, folder: String) {
        Box(Modifier.semantics { heading(); contentDescription = screen }) {
            FolderSwitch(
                icon = LinkedOutIcons.Folder,
                trailingIcon = LinkedOutIcons.ExpandMore,
                label = folder,
                expanded = false,
                onClick = {}
            )
        }
    }

    // AccountCard's zone, without its hold menu (AccountsTopDraftScreenshotTest's copy).
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
