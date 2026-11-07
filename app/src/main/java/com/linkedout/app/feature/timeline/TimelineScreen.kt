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
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
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
import com.linkedout.app.core.common.present
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
            failed = state.errors.toList(),
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
    // write and not one per card. The banner is item 0 and the chips item 1, so the post at list
    // index n is posts[n - 2].
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
            val passed = first - 2
            if (passed > 0) {
                val gone = state.posts.take(passed).map { it.id }.filter { it in seen }
                if (gone.isNotEmpty()) readPosts.mark(gone)
            }
        }
    }

    // The feed choice, under the banner (requests.md point 10): one pill with the folder icon, the feed's name and a
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
    val chips: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
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
            title = "Home",
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
    // float over it (the chips, the refresh disc and the back to top button)
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
                item(key = "chips") { chips() }
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
                    item(key = "chips") { chips() }

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
 * Home's own top row (requests.md point 9): ScreenBanner's row, the title and the subtitle no longer in a Column but side
 * by side on one line, 8 dp apart, aligned on their baseline, the pair centred between the two action slots (both slots
 * keep their room, so the pair is centred on the screen). A pair too long for the line wraps the subtitle under the title
 * instead of cutting the title.
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/ui/component/ScreenBanner.kt#ScreenBanner
 * not ported: BannerAction (used as it is), the title's two lines with TextAutoSize (one line, ellipsis), BannerGap and
 * BannerActionSize (private there, copied below)
 */
@Composable
private fun HomeBanner(
    title: String,
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
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.primary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
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
            val gap = 8.dp.roundToPx()
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val titleText = measurables[0].measure(loose)
            val subtitleText = measurables.getOrNull(1)?.measure(loose)
            if (subtitleText == null || titleText.width + gap + subtitleText.width <= constraints.maxWidth) {
                // One line: the baselines on one level, the pair centred.
                val titleBase = titleText[FirstBaseline]
                val subtitleBase = subtitleText?.get(FirstBaseline) ?: 0
                val above = maxOf(titleBase, subtitleBase)
                val below = maxOf(titleText.height - titleBase, (subtitleText?.height ?: 0) - subtitleBase)
                val width = titleText.width + (subtitleText?.let { gap + it.width } ?: 0)
                layout(constraints.maxWidth, above + below) {
                    val start = (constraints.maxWidth - width) / 2
                    titleText.place(start, above - titleBase)
                    subtitleText?.place(start + titleText.width + gap, above - subtitleBase)
                }
            } else {
                // Too long: the subtitle under the title, each centred.
                val under = 1.dp.roundToPx()
                layout(constraints.maxWidth, titleText.height + under + subtitleText.height) {
                    titleText.place((constraints.maxWidth - titleText.width) / 2, 0)
                    subtitleText.place((constraints.maxWidth - subtitleText.width) / 2, titleText.height + under)
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
 * What went wrong, in words, and the one thing that helps.
 *
 * The reason itself is here rather than a count. "in/somebody could not be
 * updated" says nothing a reader can act on, while "LinkedIn put up its sign
 * in wall" and "you are offline" call for two different things, and one of
 * them is to do nothing at all.
 */
@Composable
private fun FailureDialog(
    failed: List<Pair<String, AppError>>,
    silentDownloads: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    failed.isEmpty() -> "Downloads have no progress bar"
                    failed.size == 1 -> "1 profile could not be updated"
                    else -> "${failed.size} profiles could not be updated"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (failed.isNotEmpty()) {
                    Text(
                        "Their saved posts are still in the stream.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                failed.take(FAILURES_SHOWN).forEach { (handle, error) ->
                    val reason = error.present()
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("in/$handle", style = MaterialTheme.typography.titleSmall)
                        Text(
                            reason.explanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (failed.size > FAILURES_SHOWN) {
                    Text(
                        "and ${failed.size - FAILURES_SHOWN} more",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (silentDownloads) {
                    Text(
                        "Media is being saved for offline reading, but Android is not letting " +
                            "LinkedOut show the progress. The files still arrive. Allow " +
                            "notifications for LinkedOut in Android to see how far they are.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
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

/** How many failed profiles the dialog names before it starts counting. */
private const val FAILURES_SHOWN = 8

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
