package com.linkedout.app.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linkedout.app.core.media.MediaDownloader
import com.linkedout.app.data.settings.SettingsStore
import com.linkedout.app.core.model.AccountKind
import com.linkedout.app.core.model.Feed
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.core.model.Post
import com.linkedout.app.feature.media.MediaViewer
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.ErrorPanel
import com.linkedout.app.ui.component.InnerZoneShape
import com.linkedout.app.ui.component.LocalInlinePlaying
import com.linkedout.app.ui.component.PostCard
import com.linkedout.app.ui.component.ScreenBanner
import com.linkedout.app.ui.component.ScrollUpButton
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.component.rememberInlineTarget
import com.linkedout.app.ui.component.relativeTime
import com.linkedout.app.ui.icon.LinkedOutIcons
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * One account: a banner, the profile in its own zone, then its posts.
 *
 * The Replies and Media tabs are gone. They were a Nitter shape that never
 * meant anything here: the guest profile page is one document, and the tab
 * read the same page again through the same call, so the two extra tabs cost
 * a request each to show a subset of what was already on screen.
 *
 * The profile zone is always there, even before anything loaded, so you can
 * follow an account whose page is momentarily unreachable.
 */
@Composable
fun FeedScreen(
    handle: String,
    kind: AccountKind = AccountKind.PERSON,
    onBack: () -> Unit,
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
    val scope = rememberCoroutineScope()
    var viewing by remember { mutableStateOf<Pair<List<MediaItem>, Int>?>(null) }

    val feed = state.feed
    val name = feed?.displayName?.takeIf { it.isNotBlank() && it != handle }

    viewing?.let { (media, index) ->
        MediaViewer(
            media = media,
            startIndex = index,
            onDownload = { downloader.download(it, handle) },
            onDismiss = { viewing = null }
        )
    }

    LaunchedEffect(handle, kind) { viewModel.load(handle, kind) }

    val shown = feed?.posts.orEmpty()

    val shouldLoadMore by remember(shown.size) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            shown.isNotEmpty() && last >= shown.size - 5
        }
    }
    LaunchedEffect(shouldLoadMore, state.canLoadMore, state.pagingFailed) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    val inline = rememberInlineTarget(
        listState = listState,
        posts = shown,
        keyOf = { it.id },
        paused = viewing != null
    )
    CompositionLocalProvider(LocalInlinePlaying provides inline) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "banner") {
                    ScreenBanner(
                        title = name ?: "@$handle",
                        subtitle = feed?.let(::sourceLine),
                        leading = {
                            BannerAction(
                                icon = LinkedOutIcons.ArrowBack,
                                label = "Back",
                                onClick = onBack
                            )
                        },
                        trailing = {
                            if (state.loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                BannerAction(
                                    icon = LinkedOutIcons.Refresh,
                                    label = "Refresh",
                                    onClick = viewModel::refresh
                                )
                            }
                        }
                    )
                }

                item(key = "header") {
                    ProfileZone(
                        handle = handle,
                        name = name,
                        feed = feed,
                        isFollowing = isFollowing,
                        onToggleFollow = viewModel::toggleFollow,
                        onOpenAvatar = { small ->
                            viewing = listOf(
                                MediaItem(
                                    previewUrl = small,
                                    downloadUrl = largeAvatar(small),
                                    type = MediaType.PHOTO
                                )
                            ) to 0
                        }
                    )
                }

                state.error?.let {
                    item(key = "error") {
                        ErrorPanel(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = ZoneGap),
                            error = it,
                            onRetry = viewModel::refresh
                        )
                    }
                }

                if (shown.isEmpty()) {
                    if (feed == null && state.loading) {
                        item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    return@LazyColumn
                }

                items(shown, key = { it.id }) { post ->
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
                            state.loadingMore -> CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            state.canLoadMore -> TextButton(onClick = { viewModel.loadMore(manual = true) }) {
                                Text(if (state.pagingFailed) "Try again" else "Load older posts")
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

            val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
            ScrollUpButton(
                visible = scrolled,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            )
        }
    }
}

/**
 * Who this account is, in one zone. The name is in the banner above, so it is
 * not repeated here.
 */
@Composable
private fun ProfileZone(
    handle: String,
    name: String?,
    feed: Feed?,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onOpenAvatar: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val hold = rememberMediaPolicy().hold
    Zone(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = ZoneGap)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
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
                        .clip(InnerZoneShape)
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
                "@$handle",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

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
        }
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
