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
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import com.linkedout.app.ui.component.PullIndicator
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.PaddingValues
import com.linkedout.app.navigation.LocalReadableInset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import com.linkedout.app.ui.component.ErrorPanel
import com.linkedout.app.ui.component.LocalInlinePlaying
import com.linkedout.app.ui.component.PostCard
import com.linkedout.app.ui.component.BarTitle
import com.linkedout.app.ui.component.FloatingTopBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.linkedout.app.ui.component.ScrollUpButton
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
    // LinkedOut's change: the profile header is the list's first item, its banner under the status bar. While it is
    // on screen no bar is drawn. Once it has left, the bar comes in with the name as title alone, the gesture goes
    // back and the pull refreshes (MTGA's headerGone).
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
                        title = { BarTitle(name ?: "@$handle") }
                    )
                }
            }
        ) { _ ->
        Box(Modifier.fillMaxSize()) {
            // The pull as Home's: LinkedOut's own mark (PullIndicator) under the status bar, as TimelineScreen wires it.
            // The mark is Home's own: while the first load runs (no feed yet) the page's load mark shows alone.
            // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/timeline/TimelineScreen.kt#TimelineScreen
            // not ported: TimelineScreen (the screen is Home's; only its pull box and indicator are taken)
            val pull = rememberPullToRefreshState()
            val pulling = state.loading && feed != null
            PullToRefreshBox(
                isRefreshing = pulling,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
                state = pull,
                indicator = {
                    PullIndicator(
                        state = pull,
                        isRefreshing = pulling,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = statusBarTop())
                    )
                }
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (look != null) Modifier.glassSource(listBackdrop, look) else Modifier),
                // The header starts at the window's top, its banner under the status bar, so the top is not padded.
                // The readable inset is on the rows, not the list, so a drag in the margins of a wide window scrolls
                // too and the banner stays edge to edge (MTGA's FeedScreen).
                contentPadding = PaddingValues(bottom = navigationBarBottom())
            ) {
                item(key = "header") {
                    ProfileHeader(
                        handle = handle,
                        name = name,
                        kind = kind,
                        feed = feed,
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
                        // LinkedIn signs its picture addresses: a larger size in the path answers 403 (checked with
                        // curl -sI on a cached avatar and banner), so the viewer gets the address as given.
                        onOpenAvatar = { url ->
                            viewing = listOf(MediaItem(previewUrl = url, downloadUrl = url, type = MediaType.PHOTO)) to 0
                        },
                        // Shell.tsx onPressBanner opens the lightbox: the viewer gets the banner.
                        // https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Shell.tsx#onPressBanner
                        // not ported: the lightbox's own moderation and alt text (LinkedIn banners carry none)
                        onOpenBanner = { url ->
                            viewing = listOf(MediaItem(previewUrl = url, downloadUrl = url, type = MediaType.PHOTO)) to 0
                        }
                    )
                }

                // The profile's load mark for the first load, in the page under the header; a refresh of a profile
                // already shown has the pull's own mark.
                if (state.loading && feed == null) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp).animateItem(), contentAlignment = Alignment.Center) {
                            LoadingMark(size = 72.dp)
                        }
                    }
                }

                state.error?.let {
                    item(key = "error") {
                        ErrorPanel(
                            modifier = Modifier.padding(horizontal = LocalReadableInset.current).padding(horizontal = 12.dp, vertical = ZoneGap).animateItem(),
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
                                modifier = Modifier.fillMaxWidth().padding(horizontal = LocalReadableInset.current).padding(horizontal = 16.dp, vertical = 32.dp).animateItem()
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
                        modifier = Modifier.padding(horizontal = LocalReadableInset.current).animateItem()
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
                        modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = LocalReadableInset.current).animateContentSize().padding(24.dp),
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
 * A profile's header, compact as Bluesky's and MTGA's: the banner edge to edge from the window's top (status bar
 * shadow kept), the avatar at the start overlapping it in a ring of the page's colour, Follow at the end below the
 * banner, then at the start the name, the headline, the followers, the about text, and the location, company and
 * school (a company: its location and its website) on wrapping lines. The banner obeys the media policy (held: its
 * ground only). [LocalReadableInset] narrows the text part only.
 * Ports:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/ProfileHeader.kt#ProfileHeader
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/ProfileHeaderStandard.tsx#ProfileHeaderStandard
 * not ported: joined, following and posts counts, the verified mark, the X avatar and banner suffix rewrites, the X
 * script names, compactCount (LinkedIn's guest page has none of these; numbers are formatted by the app's own)
 * not ported: ProfileHeaderStandard's labeler, block, mute, subscribe and message buttons (a reader has no account)
 * not ported: onPressFollow (the follow runs through onToggleFollow)
 * not ported: onPressUnfollow (the existing Unfollow dialog in FeedScreen asks first)
 * not ported: onRequestHide (moderation, LinkedIn has no labels)
 * not ported: headerStandardButtons (FollowButton is its one button, a reader has no account for the others)
 * not ported: unblockAccount (a reader has no account)
 * not ported: pin (a reader has no account)
 * not ported: pinInitialHash (web page hash)
 * not ported: measureScrollMarginTop, watchScrollMarginTop (web scroll offset)
 * not ported: shouldUseProfileLightStatusBar (the status bar's icons are the app's, set in MainActivity)
 * not ported: profileBannerSafeAreaCoverHeight (web safe area, the inset is read from WindowInsets.statusBars)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHeader(
    handle: String,
    name: String?,
    kind: AccountKind,
    feed: Feed?,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onOpenAvatar: (String) -> Unit,
    onOpenBanner: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val hold = rememberMediaPolicy().hold
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val readable = LocalReadableInset.current
    val banner = feed?.bannerUrl
    val avatar = feed?.avatarUrl
    val company = kind == AccountKind.COMPANY
    Box(Modifier.fillMaxWidth()) {
        // Shell.tsx onPressBanner: only a banner that exists opens the lightbox (here MediaViewer). Without one, a
        // gradient of the theme's surface colours stands in.
        Box(
            Modifier.fillMaxWidth().height(ProfileBannerHeight)
                .semantics { contentDescription = "Banner" }
                .clickable(enabled = banner != null, role = Role.Image) { banner?.let(onOpenBanner) }
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                )
        ) {
            // The banner is a picture like any other, so Wi-Fi only holds it too.
            if (banner != null && !hold) {
                AsyncImage(
                    model = banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(ProfileBannerHeight)
                )
            }
        }
        // Bluesky's StatusBarShadow: rgba(0,0,0,0.5) to transparent, the height of the inset.
        Box(
            Modifier.fillMaxWidth().height(inset).background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))
            )
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
                // GrowableAvatar: top 104, left 10, width 94, size 90: a ring of the page's colour around the avatar.
                Box(
                    Modifier.padding(top = AvatarTop, start = AvatarLeft + readable)
                        .size(AvatarSize + AvatarRing * 2)
                        .background(MaterialTheme.colorScheme.background, CircleShape)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Profile picture" }
                        // Shell.tsx onPressAvi: only an avatar that exists opens the lightbox.
                        .clickable(enabled = avatar != null, role = Role.Image) { avatar?.let(onOpenAvatar) },
                    contentAlignment = Alignment.Center
                ) {
                    Avatar(url = avatar, name = name ?: handle, size = AvatarSize)
                }
                // The buttons row: justify_end, px_lg 16, pt_md 12 below the banner.
                Row(
                    Modifier.align(Alignment.TopEnd)
                        .padding(top = ProfileBannerHeight + 8.dp, end = 16.dp + readable),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FollowButton(following = isFollowing, onClick = onToggleFollow)
                }
            }
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier.padding(start = 16.dp + readable, end = 16.dp + readable),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // DisplayName.tsx: the name wraps, capped here at 2 lines; a company's the same.
                    Text(
                        name ?: "@$handle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    // The headline: one or two lines; a company's industry (its currentCompany) in the same place.
                    (if (company) feed?.currentCompany else feed?.headline)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                feed?.stats?.followers?.let { ProfileHeaderMetrics(String.format(Locale.getDefault(), "%,d", it)) }
                // The about text as the bio, four lines at most.
                feed?.bio?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis) }
                val place = feed?.location
                val employer = if (company) null else feed?.currentCompany
                val school = feed?.school
                val site = if (company) feed?.website else null
                if (place != null || employer != null || school != null || site != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (place != null || employer != null || school != null) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                place?.let { ProfileItem(LinkedOutIcons.LocationOnRounded, "Location", it) }
                                employer?.let { ProfileItem(LinkedOutIcons.DomainRounded, "Company", it) }
                                school?.let { ProfileItem(LinkedOutIcons.SchoolRounded, "School", it) }
                            }
                        }
                        // A company's website, on its own row: one line, in the primary, opened through the link sheet.
                        site?.let {
                            ProfileItem(
                                LinkedOutIcons.LinkRounded, "Website",
                                it.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/'),
                                MaterialTheme.colorScheme.primary,
                                Modifier.clickable { uriHandler.openUri(it) },
                                singleLine = true
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// https://github.com/TwidereProject/TwidereX-Android/blob/develop/common/src/commonMain/kotlin/com/twidere/twiderex/component/UserComponent.kt#ProfileItem
// an icon (16 dp here) then the text; one wrapping line.
@Composable
private fun ProfileItem(
    icon: ImageVector,
    description: String,
    text: String,
    textColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(4.dp))
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = textColor,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

// https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Metrics.tsx#ProfileHeaderMetrics
// the followers on one line (LinkedIn has no following or posts count on the guest card), a flow so 200 % wraps.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHeaderMetrics(followers: String) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MetricsItem(followers, "followers")
    }
}

// https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Metrics.tsx#ProfileHeaderMetrics
// the count bold, its word in the contrast medium colour.
@Composable
private fun MetricsItem(primaryText: String, secondaryText: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$primaryText ") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(secondaryText) }
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

// layout.ts PROFILE_BANNER_HEIGHT, Shell.tsx GrowableAvatar (top 104, left 10, width 94, size 90)
private val ProfileBannerHeight: Dp = 150.dp
private val AvatarSize: Dp = 90.dp
private val AvatarRing: Dp = 2.dp
private val AvatarTop: Dp = 104.dp
private val AvatarLeft: Dp = 10.dp

private enum class FeedFooter { Loading, Retry, More, End }

// Follow as Material 3 Expressive's medium button with a leading icon, a clickable Row, no Material control, as MTGA's:
// 56 dp high, its container a glass pane (BoldButton's own zone: GlassTarget, glassZone), the primary filling it for
// Follow and going out by alpha only for Following (a fade to Color.Transparent would pass through grey).
// https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/ProfileHeader.kt#FollowButton
// https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
// not ported: ContainerShapeSquare and the Selected shapes (no toggle here), disabled and elevation tokens.
// not ported: textStyleFor (its one case here, titleMedium from the medium height up, is the Text's style below)
// not ported: the outline (the glass pane draws its own rim)
// not ported: simpleAnimatedContentSample (the sample's function; its AnimatedContent is used here, inline)
// not ported: foo, bar, baz (the sample's own demo functions)
@Composable
private fun FollowButton(following: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current
    val height = ButtonMediumContainerHeight
    // ContainerShapeRound (CornerFull) morphs to PressedContainerShape (CornerMedium = ShapeDefaults.Medium) with a spring.
    val full = with(density) { height.toPx() / 2 }
    val medium = ShapeDefaults.Medium.topStart.toPx(Size(full * 2, full * 2), density)
    val corner by animateFloatAsState(
        targetValue = if (pressed) medium else full,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
        label = "followCorner"
    )
    // The fill is read in the draw block: no recomposition per frame. Same hue, alpha only.
    val container = animateFloatAsState(if (following) 0f else 1f, tween(200), label = "followContainer")
    val contentColor by animateColorAsState(if (following) colors.primary else colors.onPrimary, tween(200), label = "followContent")
    GlassTarget(Modifier) {
        Row(
            Modifier
                .heightIn(min = height)
                .then(if (glass != null) Modifier.glassZone(CircleShape, glass, lens = 1f) else Modifier)
                .graphicsLayer {
                    this.shape = RoundedCornerShape(corner)
                    clip = true
                }
                .drawBehind {
                    val outline = RoundedCornerShape(corner).createOutline(size, layoutDirection, this)
                    drawOutline(outline, colors.primary, alpha = container.value)
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = contentColor),
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(horizontal = ButtonMediumLeadingSpace),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon and label fade through, the width follows without clipping (SizeTransform(clip = false)).
            AnimatedContent(
                targetState = following,
                transitionSpec = {
                    (fadeIn(tween(110, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                        .using(SizeTransform(clip = false))
                },
                label = "followLabel"
            ) { isFollowing ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isFollowing) LinkedOutIcons.Check else LinkedOutIcons.PersonAddRounded,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonMediumIconSize),
                        tint = contentColor
                    )
                    Spacer(Modifier.width(ButtonMediumIconLabelSpace))
                    // textStyleFor: titleMedium from the medium height up.
                    Text(if (isFollowing) "Following" else "Follow", style = MaterialTheme.typography.titleMedium, color = contentColor)
                }
            }
        }
    }
}

// ButtonMediumTokens
private val ButtonMediumContainerHeight: Dp = 56.dp
private val ButtonMediumIconLabelSpace: Dp = 8.dp
private val ButtonMediumIconSize: Dp = 24.dp
private val ButtonMediumLeadingSpace: Dp = 24.dp
