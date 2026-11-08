package com.linkedout.app.feature.timeline

import com.linkedout.app.data.marks.PostMarks
import androidx.compose.runtime.saveable.rememberSaveable
import com.linkedout.app.ui.glass.LocalGlassBackdrop
import com.linkedout.app.ui.glass.LocalGlass
import com.linkedout.app.ui.glass.glassSource
import com.linkedout.app.ui.glass.rememberGlassBackdrop
import com.linkedout.app.ui.component.ZoneAlertDialog
import com.linkedout.app.ui.component.rememberRefreshHaptics
import com.linkedout.app.ui.component.LoadingMark
import com.linkedout.app.ui.component.PullIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.linkedout.app.ui.component.navigationBarBottom
import com.linkedout.app.ui.component.statusBarTop
import com.linkedout.app.ui.component.EmptyZone
import com.linkedout.app.ui.component.DropdownMenuGroup
import com.linkedout.app.ui.component.GlassMenu
import com.linkedout.app.ui.component.MenuPill
import com.linkedout.app.ui.component.SelectableDropdownMenuItem
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.FloatingPane
import com.linkedout.app.ui.component.ScrollUpButton
import com.linkedout.app.ui.component.LocalDockPadding
import com.linkedout.app.ui.component.LocalInlinePlaying
import com.linkedout.app.ui.component.rememberHaptics
import com.linkedout.app.ui.component.rememberInlineTarget
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.linkedout.app.ui.component.QuietButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.linkedout.app.core.common.AppError
import com.linkedout.app.core.common.Blame
import com.linkedout.app.ui.component.ZoneShape
import androidx.compose.material3.Icon
import com.linkedout.app.core.media.MediaDownloader
import com.linkedout.app.data.settings.AutoDownload
import com.linkedout.app.sync.NewPostNotifier
import com.linkedout.app.data.read.ReadPosts
import com.linkedout.app.data.settings.SettingsStore
import com.linkedout.app.core.model.Post
import com.linkedout.app.feature.media.MediaViewer
import com.linkedout.app.ui.component.PostCard
import com.linkedout.app.ui.component.relativeTime
import com.linkedout.app.ui.icon.LinkedOutIcons
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Home: everything you follow in one stream, newest first.
 *
 * Pull down to refresh. The banner is the first item of the list, so it goes
 * away while reading and is back when the reader is back at the top.
 *
 * Profiles that could not be read are a triangle in that banner rather than a
 * line of text in the stream. A partial failure is the normal case with this
 * upstream, and a sentence about it between two posts is read once and then
 * becomes furniture.
 *
 * What arrived since the last visit carries a thick outline that goes away as
 * the reader scrolls past it. There is no counter and no "jump to the top"
 * pill: the reader scrolls up and the outlines say where the new part ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onOpenAccounts: () -> Unit,
    onOpenPost: (Post) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: TimelineViewModel = koinViewModel()
) {
    val loaded by viewModel.state.collectAsState()
    // Liked and Archived are views picked from Home's folder menu, not
    // folders: their posts come from PostMarks, and archived posts leave the
    // other views.
    val postMarks: PostMarks = koinInject()
    val marked by postMarks.marks.collectAsState()
    var special by rememberSaveable { mutableStateOf<String?>(null) }
    val state = remember(loaded, marked, special) {
        when (special) {
            LIKED -> loaded.copy(allPosts = marked.liked.sortedByDescending { it.publishedAtMillis }, folder = LIKED, canLoadMore = false)
            ARCHIVED -> loaded.copy(allPosts = marked.archived.sortedByDescending { it.publishedAtMillis }, folder = ARCHIVED, canLoadMore = false)
            else -> if (marked.archived.isEmpty()) loaded else loaded.copy(allPosts = loaded.allPosts.filterNot { marked.isArchived(it.id) })
        }
    }
    // The threshold is felt in the indicator itself; this answers the end of
    // a refresh the reader pulled: done, or a refusal when nothing came.
    val askedRefresh = rememberRefreshHaptics(
        refreshing = state.loading,
        failed = state.errors.isNotEmpty() && (state.posts.isEmpty() || state.errors.size >= state.followedCount)
    )
    val uriHandler = LocalUriHandler.current
    val downloader: MediaDownloader = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val readPosts: ReadPosts = koinInject()
    val read by readPosts.state.collectAsState()
    var viewing by remember { mutableStateOf<Pair<Post, Int>?>(null) }
    var showFailures by remember { mutableStateOf(false) }

    // Saving media posts a progress notification, and Android can refuse it.
    // A download that runs with no way to say how far it is belongs in the
    // same place as a profile that would not load: behind the triangle.
    val context = LocalContext.current
    val notifier = remember { NewPostNotifier(context) }
    var notificationsAllowed by remember { mutableStateOf(notifier.canNotify()) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = notifier.canNotify()
        onPauseOrDispose { }
    }
    val silentDownloads = settings.autoDownloadMedia != AutoDownload.NEVER && !notificationsAllowed
    val somethingWrong = state.errors.isNotEmpty() || silentDownloads

    viewing?.let { (post, index) ->
        MediaViewer(
            postId = post.id,
            media = post.media,
            startIndex = index,
            onDownload = { downloader.download(it, post.authorHandle, post.permalink) },
            onDismiss = { viewing = null }
        )
    }

    if (showFailures) {
        FailureDialog(
            failed = state.errors.values.toList(),
            followed = state.followedCount,
            silentDownloads = silentDownloads,
            onRetry = {
                showFailures = false
                viewModel.refresh()
            },
            onDismiss = { showFailures = false }
        )
    }

    // Read means passed, nothing else. Everything sitting before the first
    // item still on screen has left by the top, so one pass of the list is one
    // write and not one per card. The banner is item 0, so the post at list
    // index n is posts[n - 1].
    //
    // Being above the viewport is not enough on its own. A refresh inserts new
    // posts above the reader and the list stays anchored on the post they were
    // reading, which pushes those new ones above the top edge without anyone
    // ever seeing them. So a post is only recorded once it has actually been
    // on screen in this session.
    val seen = remember { mutableSetOf<String>() }
    LaunchedEffect(listState, state.posts) {
        // Only the index is watched. Watching the list of visible keys built
        // that list, and a pair around it, on every frame of every scroll and
        // every transition, for a set that changes a handful of times a
        // second. The keys are read once here instead, when the index moves.
        snapshotFlow { listState.firstVisibleItemIndex }.collect { first ->
            listState.layoutInfo.visibleItemsInfo.forEach { item ->
                (item.key as? String)?.let(seen::add)
            }
            val passed = first - 1
            if (passed > 0) {
                val gone = state.posts.take(passed).map { it.id }.filter { it in seen }
                if (gone.isNotEmpty()) readPosts.mark(gone)
            }
        }
    }

    // The feed choice, in the banner's middle (requests.md points 10 and 11): one pill with the folder icon, the feed's name and a
    // chevron, which opens a menu under it: All and the folders, then Liked and Archived, the shown one checked
    // (Material 3 Expressive's grouped menu in glass, GlassMenu). Picking does what a tap on a chip did.
    // The pill is an item of the list that records listBackdrop: floating on it, it would draw the recording that holds
    // it, a layer inside itself, and Android's renderer overflows its stack at the first frame. So MenuPill draws it with
    // no backdrop, as a zone, and the menu lives in a window of its own. SMS's FloatingFrame provides its backdrop only
    // outside what records it:
    // https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/ui/component/FloatingPane.kt#FloatingFrame
    // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/timeline/TimelineScreen.kt#TimelineScreen
    // not ported: FloatingFrame (SMS's frame; here the pill draws itself with no backdrop)
    // not ported: onPostScroll (SMS's nested scroll of the frame; Home's list has its own)
    // not ported: TimelineScreen (the screen itself is this file's, only the feed choice is rebuilt here)
    var feedsOpen by remember { mutableStateOf(false) }
    val showFeed: (String?) -> Unit = { name ->
        feedsOpen = false
        if (name == LIKED || name == ARCHIVED) {
            special = name
        } else {
            special = null
            viewModel.showFolder(name)
        }
    }
    val feedPill: @Composable () -> Unit = {
        // The screen keeps its heading for TalkBack: this wrapper is a heading named "Home", and the pill inside it
        // stays its own node, a button that says the feed and whether its menu is open.
        Box(Modifier.semantics { heading(); contentDescription = "Home" }) {
            Box {
                MenuPill(
                    icon = LinkedOutIcons.Folder,
                    label = state.folder ?: "All",
                    expanded = feedsOpen,
                    trailingIcon = LinkedOutIcons.ExpandMore,
                    onClick = { feedsOpen = true }
                )
                GlassMenu(expanded = feedsOpen, onDismissRequest = { feedsOpen = false }) {
                    // No Clear row: a feed is chosen, not cleared.
                    DropdownMenuGroup(index = 0, count = 2) {
                        SelectableDropdownMenuItem(selected = state.folder == null, onClick = { showFeed(null) }, text = "All")
                        state.folders.forEach { name ->
                            SelectableDropdownMenuItem(selected = state.folder == name, onClick = { showFeed(name) }, text = name)
                        }
                    }
                    DropdownMenuGroup(index = 1, count = 2) {
                        listOf(LIKED, ARCHIVED).forEach { name ->
                            SelectableDropdownMenuItem(selected = state.folder == name, onClick = { showFeed(name) }, text = name)
                        }
                    }
                }
            }
        }
    }

    val banner: @Composable () -> Unit = {
        HomeBanner(
            // Nobody followed: no feed to choose, the plain title keeps the row from being empty.
            centre = if (state.followedCount > 0) {
                feedPill
            } else {
                {
                    Text(
                        "Home",
                        style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.primary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                }
            },
            subtitle = subtitle(state),
            leading = if (somethingWrong) {
                {
                    BannerAction(
                        icon = LinkedOutIcons.Warning,
                        label = "What went wrong",
                        onClick = { showFailures = true },
                        container = MaterialTheme.colorScheme.errorContainer,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                null
            },
            trailing = if (state.followedCount > 0) {
                {
                    BannerAction(
                        icon = LinkedOutIcons.Search,
                        label = "Search saved posts",
                        onClick = onOpenSearch
                    )
                }
            } else {
                null
            }
        )
    }

    val pull = rememberPullToRefreshState()
    // In glass, the list is recorded as it scrolls, for the controls that
    // float over it (the refresh disc and the back to top button)
    // to bend it.
    val look = LocalGlass.current
    val listBackdrop = rememberGlassBackdrop()
    when {
        state.followedCount == 0 -> Column(
            Modifier.fillMaxSize().padding(start = LocalReadableInset.current, end = LocalReadableInset.current, top = statusBarTop())
        ) {
            banner()
            // The weight gives it the rest of the screen, so it sits in the
            // middle of what is left under the banner rather than against it.
            EmptyState(
                title = "Nothing followed yet",
                message = "Find a few accounts and they will all appear here in one stream.",
                actionLabel = "Open Accounts",
                onAction = onOpenAccounts,
                modifier = Modifier.weight(1f)
            )
        }

        // Pull works here too. Before 1.3.2 this screen was a dead end: a
        // failed refresh at launch left Home stuck until a restart.
        state.isEmpty && state.errors.isNotEmpty() -> PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = {
                askedRefresh()
                viewModel.refresh()
            },
            modifier = Modifier.fillMaxSize(),
            state = pull,
            // Under the status bar, where the list starts, not behind it.
            indicator = {
                PullIndicator(
                    state = pull,
                    isRefreshing = state.loading,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = statusBarTop())
                )
            }
        ) {
            // A list, because the pull gesture needs something scrollable.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = LocalReadableInset.current,
                    end = LocalReadableInset.current,
                    top = statusBarTop(),
                    bottom = LocalDockPadding.current
                )
            ) {
                item(key = "banner") { banner() }
                item(key = "nothing") {
                    EmptyState(
                        title = "Nothing could be loaded",
                        message = "None of the profiles you follow could be read just now. " +
                            "Pull down to try again.",
                        // Centred in what is left under the banner.
                        modifier = Modifier.fillParentMaxHeight(0.7f)
                    )
                }
            }
        }

        else -> CompositionLocalProvider(LocalGlassBackdrop provides listBackdrop.takeIf { look != null }) { PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = {
                askedRefresh()
                viewModel.refresh()
            },
            modifier = Modifier.fillMaxSize(),
            state = pull,
            // Under the status bar, where the list starts, not behind it.
            indicator = {
                PullIndicator(
                    state = pull,
                    isRefreshing = state.loading,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = statusBarTop())
                )
            }
        ) {
            // Prefetch a page before the reader actually hits the bottom,
            // so scrolling stays continuous instead of stalling.
            val shouldLoadMore by remember {
                derivedStateOf {
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
                }
            }
            LaunchedEffect(shouldLoadMore, state.canLoadMore, state.pagingFailed) {
                if (shouldLoadMore) viewModel.loadMore()
            }

            val inline = rememberInlineTarget(
                listState = listState,
                posts = state.posts,
                keyOf = { it.id },
                paused = viewing != null
            )
            CompositionLocalProvider(LocalInlinePlaying provides inline) {
                LazyColumn(
                    state = listState,
                    // Full width with the rows pushed in, so the margins of a
                    // tablet scroll too. See ReadableScroll.
                    // The list starts under the status bar and scrolls beneath it.
                    contentPadding = PaddingValues(
                        start = LocalReadableInset.current,
                        end = LocalReadableInset.current,
                        top = statusBarTop(),
                        bottom = LocalDockPadding.current
                    ),
                    modifier = Modifier.fillMaxSize().then(if (look != null) Modifier.glassSource(listBackdrop, look) else Modifier)
                ) {
                    item(key = "banner") { banner() }

                    items(state.posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onClick = { onOpenPost(post) },
                            onOpenLink = { uriHandler.openUri(it) },
                            onDownload = { downloader.download(it, post.authorHandle, post.permalink) },
                            showStats = settings.showCounts,
                            unread = read.isUnread(post.id),
                            onOpenMedia = { index -> viewing = post to index }
                        )
                    }

                    item(key = "footer") {
                        TimelineFooter(state = state, onLoadMore = { viewModel.loadMore(manual = true) })
                    }
                }
            }

            // The way back to the top shows only once it is a real chore, placed as FeedScreen places its own.
            val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
            ScrollUpButton(
                visible = scrolled,
                icon = LinkedOutIcons.ArrowUp,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp + LocalReadableInset.current, bottom = 16.dp + navigationBarBottom())
            )
        } }
    }
}

/**
 * Home's own top row (requests.md points 9 and 11), one header row as Bluesky's HomeHeaderLayoutMobile: a slot, the centre
 * taking the rest (flex_1, align_center), a slot. The centre is the feed pill (or the plain title when nobody is
 * followed) with the subtitle beside it, 12 dp apart, the pair centred between the two action slots (both slots keep
 * their room, so the pair is centred on the screen). A pair too long for the line drops the subtitle under the centre
 * instead of cutting it.
 * https://github.com/bluesky-social/social-app/blob/main/src/view/com/home/HomeHeaderLayoutMobile.tsx#HomeHeaderLayoutMobile
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/ui/component/ScreenBanner.kt#ScreenBanner
 * not ported: BannerAction (used as it is), the title's two lines with TextAutoSize (one line, ellipsis), BannerGap and
 * BannerActionSize (private there, copied below), StatusBarFade, the logo and the hide on scroll (Bluesky's header only)
 */
@Composable
private fun HomeBanner(
    centre: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = BannerActionSize + 16.dp)
            .padding(horizontal = 16.dp, vertical = BannerGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Both action slots keep their room, so the pair stays centred.
        Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) { leading?.invoke() }
        Layout(
            content = {
                centre()
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        ) { measurables, constraints ->
            val gap = 12.dp.roundToPx()
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val centreItem = measurables[0].measure(loose)
            val subtitleText = measurables.getOrNull(1)?.measure(loose)
            if (subtitleText == null || centreItem.width + gap + subtitleText.width <= constraints.maxWidth) {
                // One line: the pill and the age centred on each other (a pill has no text baseline to share), the pair centred.
                val height = maxOf(centreItem.height, subtitleText?.height ?: 0)
                val width = centreItem.width + (subtitleText?.let { gap + it.width } ?: 0)
                layout(constraints.maxWidth, height) {
                    val start = (constraints.maxWidth - width) / 2
                    centreItem.place(start, (height - centreItem.height) / 2)
                    subtitleText?.place(start + centreItem.width + gap, (height - subtitleText.height) / 2)
                }
            } else {
                // Too narrow: the subtitle under the centre, each centred.
                val under = 4.dp.roundToPx()
                layout(constraints.maxWidth, centreItem.height + under + subtitleText.height) {
                    centreItem.place((constraints.maxWidth - centreItem.width) / 2, 0)
                    subtitleText.place((constraints.maxWidth - subtitleText.width) / 2, centreItem.height + under)
                }
            }
        }
        Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) { trailing?.invoke() }
    }
}

/** Space the banner leaves above and below itself, as ScreenBanner's. */
private val BannerGap = 8.dp

/** Both slots of the banner, as ScreenBanner's. */
private val BannerActionSize = 44.dp

private fun subtitle(state: TimelineUiState): String? = when {
    state.followedCount == 0 -> null
    state.loading -> "Updating"
    state.lastUpdatedMillis != null -> relativeTime(state.lastUpdatedMillis).let { age ->
        if (age.isEmpty() || age == "now") "Updated just now" else "Updated $age ago"
    }
    else -> null
}

/**
 * One message, centred over Home blurred behind it, naming where the trouble comes from. No
 * profile is listed: with many follows a list helps nobody, and nothing can be done per profile.
 * The dialog is ZoneAlertDialog's (Material's BasicAlertDialog, `onDismissRequest`, `content`:
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AlertDialog.kt#BasicAlertDialog ),
 * its pane in the post card's shape.
 */
@Composable
private fun FailureDialog(
    failed: List<AppError>,
    followed: Int,
    silentDownloads: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, text) = failureMessage(failed, followed) ?: (SILENT_DOWNLOADS_TITLE to SILENT_DOWNLOADS_TEXT)
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        shape = ZoneShape,
        icon = if (failed.isEmpty()) null else ({ FailureIcon() }),
        title = { Text(title) },
        text = { FailureContent(text, silentDownloads && failed.isNotEmpty()) },
        confirmButton = {
            if (failed.isEmpty()) {
                QuietButton(onClick = onDismiss) { Text("Close") }
            } else {
                BoldButton(onClick = onRetry, filled = true) { Text("Try again") }
            }
        },
        dismissButton = {
            if (failed.isNotEmpty()) QuietButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// The icon slot of Material's AlertDialog (icon, `iconContentColor`):
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AlertDialog.kt#AlertDialogContent
@Composable
private fun FailureIcon() {
    Icon(
        LinkedOutIcons.Warning,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(24.dp)
    )
}

// The text slot of Material's AlertDialog (`text`, `textContentColor`; ZoneAlertContent centres it):
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AlertDialog.kt#AlertDialogContent
@Composable
private fun FailureContent(text: String, silentDownloads: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text)
        if (silentDownloads) Text(SILENT_DOWNLOADS_TEXT)
    }
}

internal const val SILENT_DOWNLOADS_TITLE = "Downloads have no progress bar"
internal const val SILENT_DOWNLOADS_TEXT =
    "Media is being saved for offline reading, but Android is not letting " +
        "LinkedOut show the progress. The files still arrive. Allow " +
        "notifications for LinkedOut in Android to see how far they are."

// Bluesky's PostFeedErrorMessage reads an error, picks a KnownError, then maps each KnownError to
// its message: https://github.com/bluesky-social/social-app/blob/main/src/view/com/posts/PostFeedErrorMessage.tsx#detectKnownError
// (MIT). Here the KnownError is the origin (AppError.blame) of one failed profile.
// Not ported: Block (no counterpart), FeedgenErrorMessage and the feedgen parsing,
// safeParseFeedgenUri, the remove feed prompt, onViewProfile, PostFeedErrorMessage's ErrorMessage.
enum class KnownError {
    Device,
    Network,
    Host,
    Upstream,
    App
}

internal fun detectKnownError(error: AppError?): KnownError? {
    if (error == null) {
        return null
    }
    // check the blame, as detectKnownError checks the name and the status code
    if (error.blame == Blame.DEVICE) {
        return KnownError.Device
    }
    if (error.blame == Blame.NETWORK) {
        return KnownError.Network
    }
    if (error.blame == Blame.HOST) {
        return KnownError.Host
    }
    if (error.blame == Blame.UPSTREAM) {
        return KnownError.Upstream
    }
    return KnownError.App
}

// The KnownError to message map of FeedgenErrorMessage (`msg`, one message per KnownError, indexed by
// the knownError), here a title and a text; [rawError] is the sentence counting the profiles:
// https://github.com/bluesky-social/social-app/blob/main/src/view/com/posts/PostFeedErrorMessage.tsx#detectKnownError
internal fun FeedgenErrorMessage(knownError: KnownError, rawError: String): Pair<String, String> {
    val msg = mapOf(
        KnownError.Device to ("You are offline" to
            "Saved posts stay readable. New ones load when you are back online."),
        KnownError.Network to ("Your connection does not reach LinkedIn" to
            "$rawError Their saved posts stay readable."),
        KnownError.Host to ("LinkedIn is holding back guests" to
            "$rawError Their saved posts stay readable. Try again in a few minutes."),
        KnownError.Upstream to ("Some profiles are not public" to
            "$rawError Their saved posts stay readable."),
        KnownError.App to ("LinkedOut could not read some pages" to
            "$rawError Their saved posts stay readable.")
    )[knownError]
    return msg!!
}

// PostFeedErrorMessage's detectKnownError over every error, the knownError shared by most winning,
// then its msg ([feedDesc] is how many profiles are followed):
// https://github.com/bluesky-social/social-app/blob/main/src/view/com/posts/PostFeedErrorMessage.tsx#detectKnownError
internal fun failureMessage(error: List<AppError>, feedDesc: Int): Pair<String, String>? {
    val knownError = KnownError.entries.maxByOrNull { kind -> error.count { detectKnownError(it) == kind } }
    if (error.isEmpty() || knownError == null) {
        return null
    }
    val msg = if (knownError == KnownError.Upstream) {
        if (error.size == 1) "shows nothing to a guest." else "show nothing to a guest."
    } else {
        if (error.size == 1) "was not updated." else "were not updated."
    }
    val rawError = if (feedDesc >= error.size) {
        "${error.size} of $feedDesc profiles $msg"
    } else if (error.size == 1) {
        "1 profile $msg"
    } else {
        "${error.size} profiles $msg"
    }
    return FeedgenErrorMessage(knownError, rawError)
}

@Composable
private fun TimelineFooter(state: TimelineUiState, onLoadMore: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.loadingMore -> LoadingMark(size = 28.dp)
            state.pagingFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Couldn't load older posts. The server is busy, " +
                        "which usually clears in a minute.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                BoldButton(onClick = onLoadMore) { Text("Try again") }
            }
            state.canLoadMore -> BoldButton(onClick = onLoadMore) { Text("Load older posts") }
            else -> Text(
                "No older posts available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 5

@Composable
private fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    // A zone centred in the space, like every empty screen, so an empty
    // Home still carries the wallpaper's colour.
    EmptyZone(
        title = title,
        message = message,
        actionLabel = actionLabel,
        onAction = onAction,
        modifier = modifier
    )
}

private const val LIKED = "Liked"
private const val ARCHIVED = "Archived"
