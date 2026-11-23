package com.linkedout.app.feature.feed

import com.linkedout.app.ui.glass.rememberGlassBackdrop
import com.linkedout.app.ui.glass.glassSource
import com.linkedout.app.ui.glass.LocalGlassBackdrop
import com.linkedout.app.ui.glass.LocalGlass
import com.linkedout.app.ui.component.ZoneAlertDialog
import com.linkedout.app.ui.component.QuietButton
import com.linkedout.app.ui.component.RejectOnFailure
import com.linkedout.app.ui.component.LoadingMark
import com.linkedout.app.ui.component.navigationBarBottom
import com.linkedout.app.ui.component.statusBarTop
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.GlassTarget
import com.linkedout.app.ui.glass.glassZone
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.PaddingValues
import com.linkedout.app.navigation.LocalReadableInset
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
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
import com.linkedout.app.feature.BarAction
import com.linkedout.app.ui.component.BarTitle
import com.linkedout.app.ui.component.FloatingTopBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.linkedout.app.ui.component.ScrollUpButton
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.component.rememberHaptics
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    handle: String,
    kind: AccountKind = AccountKind.PERSON,
    onBack: () -> Unit,
    onOpenPost: (Post) -> Unit,
    viewModel: FeedViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    RejectOnFailure(state.error)
    val followed by viewModel.followed.collectAsState()
    val isFollowing = followed.any { it.handle.equals(handle, ignoreCase = true) }
    val uriHandler = LocalUriHandler.current
    val downloader: MediaDownloader = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var confirmUnfollow by remember { mutableStateOf(false) }
    if (confirmUnfollow) {
        ZoneAlertDialog(
            onDismissRequest = { confirmUnfollow = false },
            title = { Text("Unfollow?") },
            confirmButton = {
                QuietButton(onClick = {
                    confirmUnfollow = false
                    haptics.done()
                    viewModel.toggleFollow()
                }) { Text("Unfollow") }
            },
            dismissButton = { QuietButton(onClick = { confirmUnfollow = false }) { Text("Cancel") } }
        )
    }
    var viewing by remember { mutableStateOf<Pair<List<MediaItem>, Int>?>(null) }

    val feed = state.feed
    val name = feed?.displayName?.takeIf { it.isNotBlank() && it != handle }

    viewing?.let { (media, index) ->
        // The viewer holds the pictures only (the avatar belongs to no
        // post): the post carrying them gives its address and its id, which
        // finds the saved copy. The avatar has none and so no saved copy.
        val owner = feed?.posts?.firstOrNull { post -> post.media == media }
        MediaViewer(
            postId = owner?.id.orEmpty(),
            media = media,
            startIndex = index,
            onDownload = { downloader.download(it, handle, owner?.permalink) },
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
    // In glass, the list is recorded as it scrolls, for the way back to the
    // top to bend it as Home's does.
    val look = LocalGlass.current
    val listBackdrop = rememberGlassBackdrop()
    // Ported from MTGA's FeedScreen: the Scaffold whose topBar is the FloatingTopBar, the list full screen with no
    // fade under it, the bar folding by enterAlwaysScrollBehavior
    // (https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/FeedScreen.kt#FeedScreen).
    // LinkedOut's change: the profile card is the list's first item. While it is on screen it carries Back and
    // Refresh itself and no bar is drawn, so the room the bar took goes to the profile. Once it has left, the bar
    // comes in with the name as title (MTGA's headerGone).
    val headerGone by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    // Material's behaviour consumes every upward drag until its bar exists, so with no bar drawn the list never
    // scrolled and the bar never came. canScroll lets it handle scroll only once the bar is on screen.
    // Ported from https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AppBar.kt#enterAlwaysScrollBehavior
    // and https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AppBar.kt#EnterAlwaysScrollBehavior
    // not ported: flingAnimationSpec, snapAnimationSpec, isScrollingContentAtStart (the app keeps Material's defaults)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = { headerGone })
    CompositionLocalProvider(LocalInlinePlaying provides inline) {
        Scaffold(
            // The page's ground is painted under the whole app, see MainActivity.
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                if (headerGone) {
                    FloatingTopBar(
                        scrollBehavior = scrollBehavior,
                        title = { BarTitle(name ?: "@$handle") },
                        navigationIcon = { BarAction(LinkedOutIcons.ArrowBack, "Back", onBack) },
                        actions = {
                            // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/diagnostics/DiagnosticsScreen.kt#DiagnosticsScreen
                            // The Refresh icon stays, disabled while the profile loads, the page under the card shows the work.
                            // BarAction draws no dimmed state of its own, so the dimming (alpha 0.38) is set here.
                            // not ported: OverallBanner, FeatherRing, the checking count (a profile has no servers list; LinkedOut keeps its own mark)
                            BarAction(
                                LinkedOutIcons.Refresh,
                                "Refresh",
                                viewModel::refresh,
                                modifier = Modifier.alpha(if (state.loading) 0.38f else 1f),
                                enabled = !state.loading
                            )
                        }
                    )
                }
            }
        ) { _ ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (look != null) Modifier.glassSource(listBackdrop, look) else Modifier),
                // The top is the list's start only: the profile card begins under the status bar, where the bar
                // would be, and what scrolls up goes under the bar whatever its height.
                contentPadding = PaddingValues(
                    start = LocalReadableInset.current,
                    end = LocalReadableInset.current,
                    top = statusBarTop() + 2.dp,
                    bottom = navigationBarBottom()
                )
            ) {
                item(key = "header") {
                    ProfileZone(
                        handle = handle,
                        name = name,
                        feed = feed,
                        loading = state.loading,
                        onBack = onBack,
                        onRefresh = viewModel::refresh,
                        isFollowing = isFollowing,
                        onToggleFollow = {
                        // Following answers at once; unfollowing asks first, a stray
                        // tap on "Following" must not drop an account.
                        if (isFollowing) {
                            confirmUnfollow = true
                        } else {
                            haptics.done()
                            viewModel.toggleFollow()
                        }
                    },
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

                // The profile's one load mark: in the page under the card, whenever it loads (a refresh of a profile that
                // already has posts too, there is no pull on a profile). Nothing else in the card or the bar moves.
                if (state.loading) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp).animateItem(), contentAlignment = Alignment.Center) {
                            LoadingMark(size = 72.dp)
                        }
                    }
                }

                state.error?.let {
                    item(key = "error") {
                        ErrorPanel(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = ZoneGap).animateItem(),
                            error = it,
                            onRetry = viewModel::refresh
                        )
                    }
                }

                if (shown.isEmpty()) {
                    // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/feed/FeedScreen.kt#FeedScreen
                    // not ported: FeedScreen (the screen is this file's own, only the empty branch is changed)
                    // The page was read and LinkedIn shows a visitor no post: one line on the page, no zone (it does
                    // nothing). Not while loading, not with an error, as the draft shows.
                    if (feed != null && !state.loading && state.error == null) {
                        item(key = "noPosts") {
                            Text(
                                "LinkedIn shows no posts to visitors who are not signed in",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp).animateItem()
                            )
                        }
                    }
                    return@LazyColumn
                }

                items(shown, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        onClick = { onOpenPost(post) },
                        onOpenLink = { uriHandler.openUri(it) },
                        onDownload = { downloader.download(it, post.authorHandle, post.permalink) },
                        showStats = settings.showCounts,
                        onOpenMedia = { index -> viewing = post.media to index },
                        // Posts arrive and merge while the list is on screen: a card slides into its row, as MTGA's.
                        modifier = Modifier.animateItem()
                    )
                }

                item(key = "footer") {
                    // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/FeedScreen.kt#FeedScreen
                    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
                    // The footer's states (mark, button, text) cross through nothing, as the draft shows (MTGA's Crossfade, here
                    // the fade through of the other swaps): animateContentSize is the one animator of the height, so
                    // AnimatedContent's own size change snaps.
                    // not ported: FeedScreen (the screen is this file's own, only the footer and the animated items are taken)
                    // not ported: simpleAnimatedContentSample (the sample's function; its AnimatedContent is used here, inline)
                    // not ported: foo, bar, baz (the sample's own demo functions)
                    val footer = when {
                        state.loadingMore -> FeedFooter.Loading
                        state.canLoadMore -> if (state.pagingFailed) FeedFooter.Retry else FeedFooter.More
                        else -> FeedFooter.End
                    }
                    Box(
                        modifier = Modifier.animateItem().fillMaxWidth().animateContentSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = footer,
                            transitionSpec = {
                                (fadeIn(tween(110, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                                    .using(SizeTransform(clip = false) { _, _ -> snap() })
                            },
                            label = "feedFooter"
                        ) { shown ->
                            when (shown) {
                                FeedFooter.Loading -> LoadingMark(size = 28.dp)
                                FeedFooter.Retry, FeedFooter.More -> BoldButton(onClick = { viewModel.loadMore(manual = true) }) {
                                    Text(if (shown == FeedFooter.Retry) "Try again" else "Load older posts")
                                }
                                FeedFooter.End -> Text(
                                    "No older posts available.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
            CompositionLocalProvider(LocalGlassBackdrop provides listBackdrop.takeIf { look != null }) {
                ScrollUpButton(
                    visible = scrolled,
                    icon = LinkedOutIcons.ArrowUp,
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp + LocalReadableInset.current, bottom = 16.dp + navigationBarBottom())
                )
            }
        }
        }
    }
}

/**
 * Who this account is, in one zone, with its Back and Refresh in the card's top corners: the same round glass
 * actions as a banner's, inset 12 dp, so the avatar's top sits on their line and no bar is needed above the card.
 * They are drawn inside the card as BannerAction draws them, on the card's own glass and not on the backdrop of
 * the list this card is an item of.
 */
@Composable
private fun ProfileZone(
    handle: String,
    name: String?,
    feed: Feed?,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
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
        Box {
        Box(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp)) {
            BannerAction(icon = LinkedOutIcons.ArrowBack, label = "Back", onClick = onBack)
        }
        Box(Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 12.dp)) {
            // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/diagnostics/DiagnosticsScreen.kt#DiagnosticsScreen
            // The Refresh icon stays, dimmed and not tappable while the profile loads (MTGA's Re-check, disabled while
            // checking); the page under the card shows the work.
            // not ported: OverallBanner, FeatherRing, the checking count (a profile has no servers list; LinkedOut keeps its own mark)
            Box(Modifier.alpha(if (loading) 0.38f else 1f)) {
                BannerAction(icon = LinkedOutIcons.Refresh, label = "Refresh", onClick = { if (!loading) onRefresh() })
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
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

            // The bar has no title at rest, so the name is the card's first line (MTGA's ProfileHeader).
            if (name != null) {
                Text(
                    name,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
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

            // Joined with a comma, as AOSP Dialer's CallLogEntryText.java joinSecondaryTextComponents (https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calllogutils/CallLogEntryText.java).
            val meta = listOfNotNull(feed?.location, feed?.school).joinToString(", ")
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
                        parts.joinToString(", "), // as AOSP Dialer's CallLogEntryText.java joinSecondaryTextComponents (https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calllogutils/CallLogEntryText.java)
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            FollowButton(following = isFollowing, onClick = onToggleFollow, modifier = Modifier.padding(top = 8.dp))
        }
        }
    }
}

private enum class FeedFooter { Loading, Retry, More, End }

// Follow and Following are one button: the accent fill goes in and out by alpha only (a fade to Color.Transparent
// would pass through grey), the label crosses through nothing and the width follows without clipping. In glass it is
// BoldButton's own zone (GlassTarget, glassZone, the outlined shape) drawn here because BoldButton cannot animate its
// fill; with no glass, BoldButton as before.
// https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/ProfileHeader.kt#FollowButton
// https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
// not ported: ContainerShapeRound's press morph (the corner spring of Material 3 Expressive's medium button; LinkedOut's button is BoldButton's glass pane, its shape and sizes)
// not ported: the leading icon and the outline (the glass pane has neither)
// not ported: simpleAnimatedContentSample (the sample's function; its AnimatedContent is used here, inline)
// not ported: foo, bar, baz (the sample's own demo functions)
@Composable
private fun FollowButton(following: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    if (glass == null) {
        BoldButton(onClick = onClick, modifier = modifier, filled = !following) {
            Text(if (following) "Following" else "Follow")
        }
        return
    }
    val shape = ButtonDefaults.outlinedShape
    // The fill is read in the draw block: no recomposition per frame.
    val fill = animateFloatAsState(if (following) 0f else 0.45f, tween(200), label = "followFill")
    val filledContent = if (glass.dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
    val contentColor by animateColorAsState(
        if (following) MaterialTheme.colorScheme.primary else filledContent, tween(200), label = "followContent"
    )
    GlassTarget(modifier) {
        Box(
            Modifier
                .glassZone(shape, glass, lens = 1f)
                .drawBehind {
                    drawOutline(shape.createOutline(size, layoutDirection, this), glass.accentTint, alpha = fill.value)
                }
                .clip(shape)
                .clickable(role = Role.Button, onClick = onClick)
                .widthIn(min = ButtonDefaults.MinWidth)
                .heightIn(min = ButtonDefaults.MinHeight)
                .padding(ButtonDefaults.ContentPadding),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = following,
                transitionSpec = {
                    (fadeIn(tween(110, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                        .using(SizeTransform(clip = false))
                },
                label = "followLabel"
            ) { isFollowing ->
                Text(
                    if (isFollowing) "Following" else "Follow",
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor
                )
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

/**
 * X serves avatars in several sizes behind the same name: "_normal" is 48 px,
 * "_400x400" is the large one. Unknown formats are returned unchanged.
 */
private fun largeAvatar(url: String): String =
    url.replace("_normal.", "_400x400.").replace("_bigger.", "_400x400.")
