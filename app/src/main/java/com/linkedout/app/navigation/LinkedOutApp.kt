package com.linkedout.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigation.NavHostController
import com.linkedout.app.core.link.LinkRouter
import com.linkedout.app.core.link.LinkedInLink
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.linkedout.app.core.model.Post
import com.linkedout.app.data.accounts.AccountStore
import com.linkedout.app.data.settings.SettingsStore
import com.linkedout.app.feature.welcome.WelcomeScreen
import com.linkedout.app.data.settings.StartTab
import com.linkedout.app.core.model.PostKind
import com.linkedout.app.feature.accounts.AccountsScreen
import com.linkedout.app.feature.debug.DebugLogScreen
import com.linkedout.app.feature.connection.ConnectionScreen
import com.linkedout.app.feature.feed.FeedScreen
import com.linkedout.app.feature.post.PostDetailScreen
import com.linkedout.app.feature.search.SearchScreen
import com.linkedout.app.feature.settings.SettingsScreen
import com.linkedout.app.feature.timeline.TimelineScreen
import com.linkedout.app.ui.component.DockClearance
import com.linkedout.app.ui.component.DockItem
import com.linkedout.app.ui.component.FloatingDock
import com.linkedout.app.ui.component.LocalDockPadding
import com.linkedout.app.ui.component.LocalInlinePlaybackAllowed
import com.linkedout.app.ui.component.SideDockClearance
import kotlinx.coroutines.launch

@Composable
fun LinkedOutApp() {
    val navController = rememberNavController()
    val links: LinkRouter = koinInject()
    val pending by links.pending.collectAsState()
    val platformUris = LocalUriHandler.current

    fun show(link: LinkedInLink) {
        when (link) {
            is LinkedInLink.Profile -> navController.navigate(Routes.feed(link.handle))
            // A company is followed and read exactly like a person. Its posts
            // come off the same kind of page, so it lands on the same screen.
            is LinkedInLink.Company -> navController.navigate(Routes.feed(link.slug))
            is LinkedInLink.Post -> navController.navigate(Routes.post(link.id, link.handle.orEmpty()))
        }
    }

    // Links from other apps, dropped off by the activity.
    LaunchedEffect(pending) {
        pending?.let {
            show(it)
            links.consume()
        }
    }

    // Every tap on a link inside the app goes through here. LinkedIn profiles,
    // companies and posts open in LinkedOut, including country subdomains and
    // lnkd.in short links. Anything else goes to the browser.
    val uris = remember(platformUris) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val link = links.parse(uri)
                if (link != null) show(link) else platformUris.openUri(uri)
            }
        }
    }

    CompositionLocalProvider(LocalUriHandler provides uris) {
        LinkedOutNavHost(navController)
    }
}

@Composable
private fun LinkedOutNavHost(navController: NavHostController) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MAIN,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            composable(Routes.MAIN) {
                MainTabs(
                    onOpenDiagnostics = { navController.navigate(Routes.CONNECTION) },
                    onOpenDebugLog = { navController.navigate(Routes.DEBUG_LOG) },
                    onOpenFeed = { handle -> navController.navigate(Routes.feed(handle)) },
                    onOpenPost = { post -> navController.navigate(Routes.post(post.id, post.cacheOwner())) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) }
                )
            }
            composable(Routes.SEARCH) {
                Readable {
                    SearchScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPost = { post -> navController.navigate(Routes.post(post.id, post.cacheOwner())) }
                    )
                }
            }
            composable(Routes.DEBUG_LOG) {
                Readable {
                    DebugLogScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(
                route = Routes.FEED_PATTERN,
                arguments = listOf(navArgument("handle") { type = NavType.StringType })
            ) { entry ->
                Readable {
                    FeedScreen(
                        handle = entry.arguments?.getString("handle").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenDiagnostics = { navController.navigate(Routes.CONNECTION) },
                        onOpenPost = { post ->
                            navController.navigate(Routes.post(post.id, entry.arguments?.getString("handle").orEmpty()))
                        }
                    )
                }
            }
            composable(
                route = Routes.POST_PATTERN,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("from") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                Readable {
                    PostDetailScreen(
                        id = entry.arguments?.getString("id").orEmpty(),
                        from = entry.arguments?.getString("from"),
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { handle -> navController.navigate(Routes.feed(handle)) },
                        onOpenPost = { post -> navController.navigate(Routes.post(post.id, post.authorHandle)) }
                    )
                }
            }
            composable(Routes.CONNECTION) {
                Readable {
                    ConnectionScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

/**
 * The account whose cache file holds this post. A repost is stored with the
 * account that reposted it, everything else with its author.
 */
private fun Post.cacheOwner(): String =
    if (kind == PostKind.REPOST) relatedHandle ?: authorHandle else authorHandle

/**
 * The three tabs side by side in one pager. On a phone a swipe moves between
 * them, with the floating dock on top. From 600 dp wide, a tablet, a foldable
 * or a phone on its side, the same dock stands upright on the left edge,
 * vertically centred, and the content is centred at a readable width. Back from Accounts or Settings
 * returns to Home before leaving the app, which is what people expect from tabs.
 */
@Composable
private fun MainTabs(
    onOpenDiagnostics: () -> Unit,
    onOpenDebugLog: () -> Unit,
    onOpenFeed: (String) -> Unit,
    onOpenPost: (Post) -> Unit,
    onOpenSearch: () -> Unit
) {
    val tabs = TopDestination.entries
    val store: SettingsStore = koinInject()
    // Read once: the start tab only matters when the app opens. After that
    // the pager state is saved, and coming back from a post keeps the tab.
    val initialPage = remember {
        val settings = store.current
        when (settings.startTab) {
            StartTab.HOME -> TopDestination.TIMELINE.ordinal
            StartTab.ACCOUNTS -> TopDestination.ACCOUNTS.ordinal
            StartTab.LAST -> settings.lastTab.coerceIn(0, tabs.size - 1)
        }
    }
    // Outside the width check, so turning a tablet or unfolding a phone keeps
    // the current tab and every scroll position.
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // The guide opens by itself only for someone who follows nobody yet and
    // never closed it. Saveable, so turning the device keeps it open.
    val accounts: AccountStore = koinInject()
    var showWelcome by rememberSaveable {
        mutableStateOf(!store.current.welcomeSeen && accounts.accounts.value.isEmpty())
    }

    // Remembered on every settled switch, so choosing "Last tab" later in
    // Settings already knows where the reader was.
    LaunchedEffect(pager.settledPage) {
        val page = pager.settledPage
        if (store.current.lastTab != page) store.update { it.copy(lastTab = page) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = WidthClass.of(maxWidth).usesSideDock

        fun go(index: Int) {
            scope.launch {
                // On the side, tabs switch in place, like a navigation rail.
                // At the bottom they slide, because there pages are also swiped.
                if (side) pager.scrollToPage(index) else pager.animateScrollToPage(index)
            }
        }

        BackHandler(enabled = pager.currentPage != 0) { go(0) }

        fun closeWelcome(openAccounts: Boolean) {
            showWelcome = false
            if (!store.current.welcomeSeen) store.update { it.copy(welcomeSeen = true) }
            if (openAccounts) go(TopDestination.ACCOUNTS.ordinal)
        }
        // Declared after the tab one, so back closes the guide first.
        BackHandler(enabled = showWelcome) { closeWelcome(openAccounts = false) }

        val pages: @Composable (Modifier) -> Unit = { modifier ->
            HorizontalPager(
                state = pager,
                // All three stay alive, so switching tabs never reloads or
                // loses the scroll position.
                beyondViewportPageCount = tabs.size - 1,
                // With the side dock a sideways swipe would only fight
                // horizontal gestures in the wide content.
                userScrollEnabled = !side,
                modifier = modifier
            ) { page ->
                // Videos in a list play only while that list is the tab in
                // sight. The pager keeps the others alive next to it.
                val inSight = pager.settledPage == page && !showWelcome
                CompositionLocalProvider(LocalInlinePlaybackAllowed provides inSight) {
                Readable {
                    when (tabs[page]) {
                        TopDestination.TIMELINE -> TimelineScreen(
                            onOpenDiagnostics = onOpenDiagnostics,
                            onOpenAccounts = { go(TopDestination.ACCOUNTS.ordinal) },
                            onOpenPost = onOpenPost,
                            onOpenSearch = onOpenSearch
                        )
                        TopDestination.ACCOUNTS -> AccountsScreen(onOpenFeed = onOpenFeed)
                        TopDestination.SETTINGS -> SettingsScreen(
                            onOpenDiagnostics = onOpenDiagnostics,
                            onOpenDebugLog = onOpenDebugLog,
                            onOpenWelcome = { showWelcome = true }
                        )
                    }
                }
                }
            }
        }

        if (side) {
            // Where the margins around the 720 dp column are wide enough, the
            // pill sits in the left one and the column stays centred on the
            // screen. In a narrower window the content moves right to clear it.
            val clearsPill = maxWidth - ReadableWidth >= SideDockClearance * 2
            Box(Modifier.fillMaxSize()) {
                // No dock at the bottom, so nothing to clear there.
                CompositionLocalProvider(LocalDockPadding provides 0.dp) {
                    pages(
                        Modifier
                            .fillMaxSize()
                            .padding(start = if (clearsPill) 0.dp else SideDockClearance)
                    )
                }

                FloatingDock(
                    items = tabs.map { DockItem(it.icon, it.label) },
                    position = pager.currentPage + pager.currentPageOffsetFraction,
                    onSelect = ::go,
                    vertical = true,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                )
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalDockPadding provides DockClearance) {
                    pages(Modifier.fillMaxSize())
                }

                FloatingDock(
                    items = tabs.map { DockItem(it.icon, it.label) },
                    position = pager.currentPage + pager.currentPageOffsetFraction,
                    onSelect = ::go,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                )
            }
        }

        // Above the tabs and the dock. A Surface also stops touches from
        // reaching the screen underneath.
        if (showWelcome) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Readable { WelcomeScreen(onFinish = ::closeWelcome) }
            }
        }
    }
}
