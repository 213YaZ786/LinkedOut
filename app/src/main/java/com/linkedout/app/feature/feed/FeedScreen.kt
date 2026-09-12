package com.linkedout.app.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.linkedout.app.ui.component.rememberMediaPolicy
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linkedout.app.core.media.MediaDownloader
import com.linkedout.app.data.settings.SettingsStore
import com.linkedout.app.core.model.Feed
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.ProfileTab
import com.linkedout.app.feature.media.MediaViewer
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.ErrorPanel
import com.linkedout.app.ui.component.LocalInlinePlaying
import com.linkedout.app.ui.component.PostCard
import com.linkedout.app.ui.component.rememberInlineTarget
import com.linkedout.app.ui.component.relativeTime
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * One account: a centred profile header, then its posts.
 *
 * The header is always there, even before anything loaded, so you can follow
 * an account whose feed is momentarily unreachable. The top bar stays quiet
 * and only shows the name once the header has scrolled away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    handle: String,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenPost: (Post) -> Unit,
    viewModel: FeedViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val followed by viewModel.followed.collectAsState()
    val isFollowing = followed.any { it.handle.equals(handle, ignoreCase = true) }
    val uriHandler = LocalUriHandler.current
    val downloader: MediaDownloader = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()
    val listState = rememberLazyListState()
    var viewing by remember { mutableStateOf<Pair<List<MediaItem>, Int>?>(null) }

    val feed = state.feed
    val name = feed?.displayName?.takeIf { it.isNotBlank() && it != handle }
    val headerGone by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    viewing?.let { (media, index) ->
        MediaViewer(
            media = media,
            startIndex = index,
            onDownload = { downloader.download(it, handle) },
            onDismiss = { viewing = null }
        )
    }

    LaunchedEffect(handle) { viewModel.load(handle) }

    val tab = state.tab
    val tabFeed = state.tabFeed(tab)
    val shown = if (tab == ProfileTab.POSTS) feed?.posts.orEmpty() else tabFeed.posts
    val canMore = if (tab == ProfileTab.POSTS) state.canLoadMore else tabFeed.cursor != null
    val pagingFailed = if (tab == ProfileTab.POSTS) state.pagingFailed else tabFeed.pagingFailed
    val loadingMore = if (tab == ProfileTab.POSTS) state.loadingMore else tabFeed.loadingMore

    val shouldLoadMore by remember(shown.size, tab) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            shown.isNotEmpty() && last >= shown.size - 5
        }
    }
    LaunchedEffect(shouldLoadMore, canMore, pagingFailed, tab) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (headerGone) {
                        Text(name ?: "@$handle", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(LinkedOutIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                        if (state.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(LinkedOutIcons.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val inline = rememberInlineTarget(
            listState = listState,
            posts = shown,
            keyOf = { "${tab.name}-${it.id}" },
            paused = viewing != null,
            keySpace = tab
        )
        CompositionLocalProvider(LocalInlinePlaying provides inline) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item(key = "header") {
                ProfileHeader(
                    handle = handle,
                    name = name,
                    feed = feed,
                    isFollowing = isFollowing,
                    onToggleFollow = viewModel::toggleFollow,
                    onOpenAvatar = { small ->
                        viewing = listOf(
                            MediaItem(previewUrl = small, downloadUrl = largeAvatar(small), type = MediaType.PHOTO)
                        ) to 0
                    }
                )
            }

            item(key = "tabs") {
                SecondaryTabRow(selectedTabIndex = tab.ordinal) {
                    ProfileTab.entries.forEach { entry ->
                        Tab(
                            selected = entry == tab,
                            onClick = { viewModel.selectTab(entry) },
                            text = { Text(entry.label) }
                        )
                    }
                }
            }

            val error = if (tab == ProfileTab.POSTS) state.error else tabFeed.error
            error?.let {
                item(key = "error-${tab.name}") {
                    ErrorPanel(
                        modifier = Modifier.padding(16.dp),
                        error = it,
                        onRetry = viewModel::refresh,
                        onOpenDiagnostics = onOpenDiagnostics,
                        onVerify = viewModel::verify
                    )
                }
            }

            val firstLoad = if (tab == ProfileTab.POSTS) feed == null && state.loading else tabFeed.loading
            if (shown.isEmpty()) {
                if (firstLoad) {
                    item(key = "loading-${tab.name}") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (tab != ProfileTab.POSTS && tabFeed.loaded && error == null) {
                    item(key = "empty-${tab.name}") {
                        Text(
                            if (tab == ProfileTab.MEDIA) "No photos or videos." else "No replies.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp)
                        )
                    }
                }
                return@LazyColumn
            }

            // Keys carry the tab, the same post can sit in two tabs.
            items(shown, key = { "${tab.name}-${it.id}" }) { post ->
                PostCard(
                    post = post,
                    onClick = { onOpenPost(post) },
                    onOpenLink = { uriHandler.openUri(it) },
                    onDownload = { downloader.download(it, post.authorHandle) },
                    showStats = settings.showCounts,
                    onOpenMedia = { index -> viewing = post.media to index }
                )
            }

            item(key = "footer") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        loadingMore -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        canMore -> TextButton(onClick = { viewModel.loadMore(manual = true) }) {
                            Text(if (pagingFailed) "Try again" else "Load older posts")
                        }
                        else -> Text(
                            "No older posts available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ProfileHeader(
    handle: String,
    name: String?,
    feed: Feed?,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onOpenAvatar: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val hold = rememberMediaPolicy().hold
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // The banner is a picture like any other, so Wi-Fi only holds it too.
        val banner = feed?.bannerUrl
        if (banner != null && !hold) {
            AsyncImage(
                model = banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }

        val avatar = feed?.avatarUrl
        Box(
            Modifier
                .clip(CircleShape)
                .clickable(enabled = avatar != null) { avatar?.let(onOpenAvatar) }
        ) {
            Avatar(url = avatar, name = name ?: handle, size = 88.dp)
        }

        Text(
            name ?: "@$handle",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (name != null) {
            Text(
                "@$handle",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        feed?.bio?.let { bio ->
            Text(
                bio,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 520.dp).padding(top = 4.dp)
            )
        }

        val meta = listOfNotNull(feed?.location, feed?.school).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        feed?.currentCompany?.let { site ->
            Text(
                site.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/'),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { uriHandler.openUri(site) }
            )
        }
        feed?.stats?.let { stats ->
            val parts = listOfNotNull(
                stats.posts?.let { "${compactCount(it)} posts" },
                stats.following?.let { "${compactCount(it)} following" },
                stats.followers?.let { "${compactCount(it)} followers" }
            )
            if (parts.isNotEmpty()) {
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (isFollowing) {
            OutlinedButton(onClick = onToggleFollow, modifier = Modifier.padding(top = 8.dp)) {
                Text("Following")
            }
        } else {
            FilledTonalButton(onClick = onToggleFollow, modifier = Modifier.padding(top = 8.dp)) {
                Text("Follow")
            }
        }

        feed?.let {
            Text(
                sourceLine(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // The tab row right under the header draws its own line.
        Spacer(Modifier.height(4.dp))
    }
}

/** 870, 12,345, 566K, 53.2M: exact while short, rounded once it would not fit. */
private fun compactCount(value: Long): String = when {
    value < 10_000 -> String.format(Locale.getDefault(), "%,d", value)
    value < 1_000_000 -> "${value / 1_000}K"
    else -> {
        val millions = value / 100_000 / 10.0
        String.format(Locale.getDefault(), "%.1fM", millions).replace(".0M", "M").replace(",0M", "M")
    }
}

/** Which server answered and how fresh it is. LinkedOut always names its source. */
private fun sourceLine(feed: Feed): String {
    val age = relativeTime(feed.fetchedAtMillis)
    val freshness = when {
        age.isEmpty() -> ""
        age == "now" -> ", updated just now"
        else -> ", updated $age ago"
    }
    return "Read via ${feed.fetchedFromHost}$freshness"
}

/**
 * X serves avatars in several sizes behind the same name: "_normal" is 48 px,
 * "_400x400" is the large one. Unknown formats are returned unchanged.
 */
private fun largeAvatar(url: String): String =
    url.replace("_normal.", "_400x400.").replace("_bigger.", "_400x400.")
