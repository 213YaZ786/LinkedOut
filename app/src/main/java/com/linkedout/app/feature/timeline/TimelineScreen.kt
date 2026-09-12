package com.linkedout.app.feature.timeline

import com.linkedout.app.ui.component.LocalDockPadding
import com.linkedout.app.ui.component.LocalInlinePlaying
import com.linkedout.app.ui.component.rememberInlineTarget
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.linkedout.app.core.media.MediaDownloader
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
 * Pull down to refresh. Posts that arrive while you are further down stay out
 * of your way, and a pill offers to jump up to them. Filters sit at the top of
 * the list and are remembered. The pulse icon turns red when a source fails
 * and opens Diagnostics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onOpenDiagnostics: () -> Unit,
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
    var viewing by remember { mutableStateOf<Pair<Post, Int>?>(null) }

    viewing?.let { (post, index) ->
        MediaViewer(
            media = post.media,
            startIndex = index,
            onDownload = { downloader.download(it, post.authorHandle) },
            onDismiss = { viewing = null }
        )
    }

    // Once the top of the list is on screen, the new posts have been seen.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 }
            .collect { atTop -> if (atTop) viewModel.clearNewPosts() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Home")
                        subtitle(state)?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (state.followedCount > 0) {
                        IconButton(onClick = onOpenSearch) {
                            Icon(LinkedOutIcons.Search, contentDescription = "Search saved posts")
                        }
                        IconButton(onClick = onOpenDiagnostics) {
                            Icon(
                                LinkedOutIcons.Pulse,
                                contentDescription = if (state.errors.isEmpty()) {
                                    "Sources working"
                                } else {
                                    "Some sources failed"
                                },
                                tint = if (state.errors.isEmpty()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.followedCount == 0 -> EmptyState(
                title = "Nothing followed yet",
                message = "Find a few accounts and they will all appear here in one stream.",
                actionLabel = "Open Accounts",
                onAction = onOpenAccounts,
                modifier = Modifier.padding(padding)
            )

            // Pull works here too. Before 1.3.2 this screen was a dead end: a
            // failed refresh at launch left Home stuck until a restart.
            state.isEmpty && state.errors.isNotEmpty() -> PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                // A list, because the pull gesture needs something scrollable.
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "nothing") {
                        EmptyState(
                            title = "Nothing could be loaded",
                            message = "None of your accounts could be loaded. Pull down to try " +
                                "again. The connection check shows which servers are down.",
                            actionLabel = "Check connection",
                            onAction = onOpenDiagnostics,
                            modifier = Modifier.fillParentMaxSize()
                        )
                    }
                }
            }

            else -> PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding)
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
                    item(key = "filters") {
                        FilterRow(filters = state.filters, onChange = viewModel::setFilters)
                    }

                    if (state.errors.isNotEmpty()) {
                        item(key = "failures") {
                            PartialFailureNotice(
                                failed = state.errors.keys.toList(),
                                onOpenDiagnostics = onOpenDiagnostics
                            )
                        }
                    }

                    if (state.posts.isEmpty() && state.filters.active && state.allPosts.isNotEmpty()) {
                        item(key = "nomatch") {
                            Text(
                                "No stored post matches these filters yet. Older posts load as you " +
                                    "scroll, or clear a filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(32.dp)
                            )
                        }
                    }

                    items(state.posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onClick = { onOpenPost(post) },
                            onOpenLink = { uriHandler.openUri(it) },
                            onDownload = { downloader.download(it, post.authorHandle) },
                            showStats = settings.showCounts,
                            onOpenMedia = { index -> viewing = post to index }
                        )
                    }

                    item(key = "footer") {
                        TimelineFooter(state = state, onLoadMore = { viewModel.loadMore(manual = true) })
                    }
                }
                }

                val showPill by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > 0 }
                }
                if (showPill && state.newPostCount > 0) {
                    NewPostsPill(
                        count = state.newPostCount,
                        onClick = {
                            scope.launch { listState.animateScrollToItem(0) }
                            viewModel.clearNewPosts()
                        },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    )
                }
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

@Composable
private fun FilterRow(filters: HomeFilters, onChange: (HomeFilters) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = filters.mediaOnly,
            onClick = { onChange(filters.copy(mediaOnly = !filters.mediaOnly)) },
            label = { Text("Media only") }
        )
        FilterChip(
            selected = filters.hideReplies,
            onClick = { onChange(filters.copy(hideReplies = !filters.hideReplies)) },
            label = { Text("Hide replies") }
        )
        FilterChip(
            selected = filters.hideReposts,
            onClick = { onChange(filters.copy(hideReposts = !filters.hideReposts)) },
            label = { Text("Hide reposts") }
        )
    }
}

@Composable
private fun NewPostsPill(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(LinkedOutIcons.ArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                if (count == 1) "1 new post" else "$count new posts",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
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

/**
 * Partial failure is the normal case with a fragile upstream, so it gets a
 * quiet line rather than a blocking error. The posts that did load stay
 * readable above everything.
 */
@Composable
private fun PartialFailureNotice(failed: List<String>, onOpenDiagnostics: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = if (failed.size == 1) {
                "@${failed.first()} couldn't be updated. Showing saved posts."
            } else {
                "${failed.size} accounts couldn't be updated. Showing saved posts."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onOpenDiagnostics) { Text("Details") }
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
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
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
