package com.linkedout.app.feature.timeline

import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.FolderDialog
import com.linkedout.app.ui.component.LocalDockPadding
import com.linkedout.app.ui.component.LocalInlinePlaying
import com.linkedout.app.ui.component.ScreenBanner
import com.linkedout.app.ui.component.FloatingRoundButton
import com.linkedout.app.ui.component.ScrollUpButton
import com.linkedout.app.ui.component.rememberHaptics
import com.linkedout.app.ui.component.rememberInlineTarget
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val downloader: MediaDownloader = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val readPosts: ReadPosts = koinInject()
    val read by readPosts.state.collectAsState()
    var viewing by remember { mutableStateOf<Pair<Post, Int>?>(null) }
    var showFailures by remember { mutableStateOf(false) }
    var choosingFolder by remember { mutableStateOf(false) }

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
            media = post.media,
            startIndex = index,
            onDownload = { downloader.download(it, post.authorHandle) },
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

    if (choosingFolder) {
        FolderDialog(
            title = "Show",
            folders = state.folders,
            selected = state.folder,
            everything = "Everything",
            onSelect = { name ->
                choosingFolder = false
                viewModel.showFolder(name)
            },
            onDismiss = { choosingFolder = false }
        )
    }

    val banner: @Composable () -> Unit = {
        ScreenBanner(
            title = state.folder ?: "Everything",
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

    when {
        state.followedCount == 0 -> Column(Modifier.fillMaxSize()) {
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
                // Firm: a pull is a thing begun, not a button pressed.
                haptics.firm()
                viewModel.refresh()
            },
            modifier = Modifier.fillMaxSize()
        ) {
            // A list, because the pull gesture needs something scrollable.
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "banner") { banner() }
                item(key = "nothing") {
                    EmptyState(
                        title = "Nothing could be loaded",
                        message = "None of the profiles you follow could be read just now. " +
                            "Pull down to try again.",
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp)
                    )
                }
            }
        }

        else -> PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = {
                // Firm: a pull is a thing begun, not a button pressed.
                haptics.firm()
                viewModel.refresh()
            },
            modifier = Modifier.fillMaxSize()
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
                    contentPadding = PaddingValues(bottom = LocalDockPadding.current),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "banner") { banner() }

                    items(state.posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onClick = { onOpenPost(post) },
                            onOpenLink = { uriHandler.openUri(it) },
                            onDownload = { downloader.download(it, post.authorHandle) },
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

            // Both at the bottom right, in one column, above the dock. The
            // folder switch never leaves: it is how the reader moves from one
            // stream to another, and a control that only appears once you have
            // scrolled is a control nobody finds. The way back to the top
            // stacks above it and comes and goes.
            val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = LocalDockPadding.current.coerceAtLeast(16.dp)
                    )
            ) {
                ScrollUpButton(
                    visible = scrolled,
                    onClick = { scope.launch { listState.animateScrollToItem(0) } }
                )
                FloatingRoundButton(
                    icon = LinkedOutIcons.Folder,
                    label = "Choose which folder to read",
                    onClick = { choosingFolder = true }
                )
            }
        }
    }
}

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
    AlertDialog(
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
                TextButton(onClick = onDismiss) { Text("Close") }
            } else {
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        },
        dismissButton = {
            if (failed.isNotEmpty()) TextButton(onClick = onDismiss) { Text("Close") }
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
            state.loadingMore -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            state.pagingFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Couldn't load older posts. The server is busy, " +
                        "which usually clears in a minute.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onLoadMore) { Text("Try again") }
            }
            state.canLoadMore -> TextButton(onClick = onLoadMore) { Text("Load older posts") }
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
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
