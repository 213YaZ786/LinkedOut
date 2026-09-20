package com.linkedout.app.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import kotlinx.coroutines.CancellationException
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
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import com.linkedout.app.core.link.LinkRouter
import com.linkedout.app.core.link.LinkedInLink
import com.linkedout.app.core.model.AccountKind
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
import com.linkedout.app.feature.accounts.FoldersScreen
import com.linkedout.app.feature.debug.DebugLogScreen
import com.linkedout.app.feature.feed.FeedScreen
import com.linkedout.app.feature.post.PostDetailScreen
import com.linkedout.app.feature.search.SearchScreen
import com.linkedout.app.feature.settings.SettingsScreen
import com.linkedout.app.feature.storage.SavedMediaScreen
import com.linkedout.app.feature.timeline.TimelineScreen
import com.linkedout.app.ui.component.DockClearance
import com.linkedout.app.ui.component.DockItem
import com.linkedout.app.ui.component.FloatingDock
import com.linkedout.app.ui.component.LocalDockPadding
import androidx.compose.animation.EnterExitState
import com.linkedout.app.ui.component.DismissableScreen
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
            is LinkedInLink.Profile -> navController.open(Routes.feed(link.handle))
            // A company is followed and read exactly like a person. Its posts
            // come off the same kind of page, so it lands on the same screen.
            is LinkedInLink.Company -> navController.open(
                Routes.feed(link.slug, AccountKind.ofSegment(link.segment))
            )
            is LinkedInLink.Post -> navController.open(Routes.post(link.id, link.handle.orEmpty()))
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

/**
 * One screen per tap.
 *
 * A tap while the previous one is still arriving used to push the same screen
 * again, and two copies of a post need two taps of back to leave. The screen
 * being left is also still on top of the stack during its own animation, so
 * the second copy looked like a back gesture that had not worked.
 *
 * The entry only reaches RESUMED once it has arrived, which is exactly the
 * moment another navigation becomes a deliberate one rather than a stutter.
 */
private fun NavHostController.open(route: String) {
    val entry = currentBackStackEntry
    if (entry != null && !entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
    // The guard above only covers the arrival. Once the screen had settled,
    // tapping the same name again pushed a second identical copy, and leaving
    // then took one back gesture per copy, which reads as a back that is not
    // responding. A name that will not load is exactly where people tap twice.
    if (currentPath() == route.substringBefore('?')) return
    navigate(route)
}

/**
 * The address of the screen on top, its pattern filled in with the values it
 * was opened with, so it can be compared with one about to be opened.
 *
 * Only the path is compared. "feed/{handle}" filled in is what makes two taps
 * on the same name the same screen, while the query part carries hints, the
 * account kind or which cache to look in, and two of those are still one
 * screen. Rebuilt from the live back stack rather than from a list kept here,
 * because a system back gesture never passes through this file and any list
 * of our own would drift out of step with the real stack.
 */
private fun NavHostController.currentPath(): String? {
    val entry = currentBackStackEntry ?: return null
    val pattern = entry.destination.route?.substringBefore('?') ?: return null
    val arguments = entry.arguments
    return pattern.split('/').joinToString("/") { segment ->
        if (segment.startsWith("{") && segment.endsWith("}")) {
            arguments?.getString(segment.removeSurrounding("{", "}")).orEmpty()
        } else {
            segment
        }
    }
}

/**
 * One step back per tap, for the same reason as [open]. Two quick taps on the
 * arrow used to leave two screens, which from the reader's side is a back that
 * skipped one.
 */
private fun NavHostController.back() {
    val entry = currentBackStackEntry ?: return
    if (!entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
    popBackStack()
}

private const val ENTER_MS = 260
private const val EXIT_MS = 180

/**
 * How a screen arrives and leaves.
 *
 * The library's own default is a 700 ms cross fade, which is most of a second
 * of the old screen still being there after a back gesture, and reads as a
 * stuck app rather than as an animation. These are the Material durations: a
 * short slide with the fade, quicker on the way out than on the way in, and
 * the popped screen slides back out the side it came in from so the gesture
 * and the picture agree.
 */

@Composable
private fun LinkedOutNavHost(navController: NavHostController) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MAIN,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            enterTransition = {
                slideInHorizontally(tween(ENTER_MS)) { full -> full / 4 } +
                    fadeIn(tween(ENTER_MS))
            },
            exitTransition = {
                slideOutHorizontally(tween(EXIT_MS)) { full -> -full / 8 } +
                    fadeOut(tween(EXIT_MS))
            },
            // No fade on the way back. A back gesture is seeked frame by frame
            // under the thumb, and a fade over a whole screen makes the
            // compositor render that screen into an off screen layer for every
            // one of those frames. A slide moves a layer that already exists.
            popEnterTransition = {
                slideInHorizontally(tween(ENTER_MS)) { full -> -full / 8 }
            },
            popExitTransition = {
                slideOutHorizontally(tween(EXIT_MS)) { full -> full }
            }
        ) {
            composable(Routes.MAIN) {
                // True only when this screen is fully arrived and nothing is
                // animating over it. It is what stops the inline video from
                // going on playing under a screen that is sliding, and a
                // back gesture is seeked frame by frame under the thumb: a
                // video surface being composited inside a layer that moves
                // every frame is the most expensive thing this app can do,
                // and it is what made the dragged back feel heavy while the
                // arrow, which animates on its own, did not.
                val settled = transition.currentState == transition.targetState &&
                    transition.targetState == EnterExitState.Visible
                MainTabs(
                    settled = settled,
                    onOpenDebugLog = { navController.open(Routes.DEBUG_LOG) },
                    onOpenSavedMedia = { navController.open(Routes.SAVED_MEDIA) },
                    onOpenFeed = { handle, kind -> navController.open(Routes.feed(handle, kind)) },
                    onOpenFolders = { navController.open(Routes.FOLDERS) },
                    onOpenPost = { post -> navController.open(Routes.post(post.id, post.cacheOwner())) },
                    onOpenSearch = { navController.open(Routes.SEARCH) }
                )
            }
            composable(Routes.SEARCH) {
                DismissableScreen(onBack = { navController.back() }) {
                Readable {
                    SearchScreen(
                        onBack = { navController.back() },
                        onOpenPost = { post -> navController.open(Routes.post(post.id, post.cacheOwner())) }
                    )
                }
                }
            }
            composable(Routes.DEBUG_LOG) {
                DismissableScreen(onBack = { navController.back() }) {
                Readable {
                    DebugLogScreen(onBack = { navController.back() })
                }
                }
            }
            composable(Routes.SAVED_MEDIA) {
                DismissableScreen(onBack = { navController.back() }) {
                Readable {
                    SavedMediaScreen(onBack = { navController.back() })
                }
                }
            }
            composable(Routes.FOLDERS) {
                DismissableScreen(onBack = { navController.back() }) {
                Readable {
                    FoldersScreen(onBack = { navController.back() })
                }
                }
            }
            composable(
                route = Routes.FEED_PATTERN,
                arguments = listOf(
                    navArgument("handle") { type = NavType.StringType },
                    navArgument("kind") {
                        type = NavType.StringType
                        defaultValue = AccountKind.PERSON.name
                    }
                )
            ) { entry ->
                DismissableScreen(onBack = { navController.back() }) {
                Readable {
                    FeedScreen(
                        handle = entry.arguments?.getString("handle").orEmpty(),
                        kind = runCatching {
                            AccountKind.valueOf(entry.arguments?.getString("kind").orEmpty())
                        }.getOrDefault(AccountKind.PERSON),
                        onBack = { navController.back() },
                        onOpenPost = { post ->
                            navController.open(Routes.post(post.id, entry.arguments?.getString("handle").orEmpty()))
                        }
                    )
                }
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
                DismissableScreen(onBack = { navController.back() }) {
                Readable {
                    PostDetailScreen(
                        id = entry.arguments?.getString("id").orEmpty(),
                        from = entry.arguments?.getString("from"),
                        onBack = { navController.back() },
                        onOpenProfile = { handle -> navController.open(Routes.feed(handle)) },
                        onOpenPost = { post -> navController.open(Routes.post(post.id, post.authorHandle)) }
                    )
                }
                }
            }
        }
    }
}

/**
 * The account whose cache file holds this post. A post someone passed on or
 * reacted to is stored with that someone, everything else with its author.
 */
private fun Post.cacheOwner(): String =
    if (kind == PostKind.REPOST || kind == PostKind.REACTION) {
        relatedHandle ?: authorHandle
    } else {
        authorHandle
    }

/**
 * The three tabs side by side in one pager. On a phone a swipe moves between
 * them, with the floating dock on top. From 600 dp wide, a tablet, a foldable
 * or a phone on its side, the same dock stands upright on the left edge,
 * vertically centred, and the content is centred at a readable width. Back from Accounts or Settings
 * returns to Home before leaving the app, which is what people expect from tabs.
 */
@Composable
private fun MainTabs(
    settled: Boolean,
    onOpenDebugLog: () -> Unit,
    onOpenSavedMedia: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenFeed: (String, AccountKind) -> Unit,
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

        // Predictive, not plain. A plain BackHandler sitting above the
        // navigation host answers the gesture without ever telling the system
        // how far it has come, and a gesture with no progress is a gesture
        // that animates nothing until it ends.
        PredictiveBackHandler(enabled = pager.currentPage != 0) { progress ->
            try {
                progress.collect { }
                go(0)
            } catch (cancelled: CancellationException) {
                // The thumb came back. Nothing to undo, nothing to report.
            }
        }

        fun closeWelcome(openAccounts: Boolean) {
            showWelcome = false
            if (!store.current.welcomeSeen) store.update { it.copy(welcomeSeen = true) }
            if (openAccounts) go(TopDestination.ACCOUNTS.ordinal)
        }
        // Declared after the tab one, so back closes the guide first.
        PredictiveBackHandler(enabled = showWelcome) { progress ->
            try {
                progress.collect { }
                closeWelcome(openAccounts = false)
            } catch (cancelled: CancellationException) {
                // The thumb came back.
            }
        }

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
                val inSight = pager.settledPage == page && !showWelcome && settled
                CompositionLocalProvider(LocalInlinePlaybackAllowed provides inSight) {
                Readable {
                    when (tabs[page]) {
                        TopDestination.TIMELINE -> TimelineScreen(
                            onOpenAccounts = { go(TopDestination.ACCOUNTS.ordinal) },
                            onOpenPost = onOpenPost,
                            onOpenSearch = onOpenSearch
                        )
                        TopDestination.ACCOUNTS -> AccountsScreen(
                            onOpenFeed = onOpenFeed,
                            onOpenFolders = onOpenFolders
                        )
                        TopDestination.SETTINGS -> SettingsScreen(
                            onOpenDebugLog = onOpenDebugLog,
                            onOpenSavedMedia = onOpenSavedMedia,
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
