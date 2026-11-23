package com.linkedout.app.feature.search

import com.linkedout.app.ui.component.GlassSearchField
import com.linkedout.app.feature.BarAction
import com.linkedout.app.ui.component.BarTitle
import com.linkedout.app.ui.component.FloatingTopBar
import com.linkedout.app.ui.component.plus
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import com.linkedout.app.ui.component.navigationBarBottom
import androidx.compose.foundation.layout.PaddingValues
import com.linkedout.app.navigation.LocalReadableInset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.linkedout.app.core.media.MediaDownloader
import com.linkedout.app.core.model.Post
import com.linkedout.app.data.settings.SettingsStore
import com.linkedout.app.feature.media.MediaViewer
import com.linkedout.app.ui.component.PostCard
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally

/** Search through every post saved on this phone, instantly and offline. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenPost: (Post) -> Unit,
    viewModel: SearchViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val typed by viewModel.typed.collectAsState()
    val uriHandler = LocalUriHandler.current
    val focus = LocalFocusManager.current
    val downloader: MediaDownloader = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()
    val focusRequester = remember { FocusRequester() }
    var viewing by remember { mutableStateOf<Pair<Post, Int>?>(null) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    viewing?.let { (post, index) ->
        MediaViewer(
            postId = post.id,
            media = post.media,
            startIndex = index,
            onDownload = { downloader.download(it, post.authorHandle, post.permalink) },
            onDismiss = { viewing = null }
        )
    }

    // Ported from MTGA's DiagnosticsScreen: the bar folds away going down and returns going up, the list full screen
    // (https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/diagnostics/DiagnosticsScreen.kt#DiagnosticsScreen).
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The page's ground is painted under the whole app, see MainActivity.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        // The NavHost's own Scaffold already stands clear of the status and
        // navigation bars. A nested Scaffold applies them a second time, which
        // is where the empty band above and below the content came from. The
        // list below owns them once, in its content padding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FloatingTopBar(
                scrollBehavior = scrollBehavior,
                title = { BarTitle("Search") },
                navigationIcon = { BarAction(LinkedOutIcons.ArrowBack, "Back", onBack) }
            )
        }
    ) { padding ->
        val hint = when {
            !state.loaded -> null
            state.savedCount == 0 -> "Nothing saved yet. Posts you read are kept here and can be searched offline."
            typed.isBlank() -> "Search through ${state.savedCount} posts saved on this phone, offline. " +
                "Words can be in any order."
            state.results.isEmpty() -> "No saved post matches."
            else -> null
        }

        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = LocalReadableInset.current,
                end = LocalReadableInset.current,
                bottom = navigationBarBottom()
            ).plus(padding)
        ) {
            // The list's first row, under the bar, as Accounts sets its field
            // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/accounts/AccountsScreen.kt#L224-L256
            item(key = "field") {
                GlassSearchField(
                    value = typed,
                    onValueChange = viewModel::setQuery,
                    placeholder = "Search saved posts",
                    // The Clear icon grows in and out with the field's end, so the field does not jump at the first letter.
                    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedVisibilitySamples.kt#AnimatedVisibilityWithBooleanVisibleParamNoReceiver
                    // not ported: AnimatedVisibilityWithBooleanVisibleParamNoReceiver (the sample's clickable Box toggling `visible`, its corner radius transition; the icon shows by the typed text)
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = typed.isNotEmpty(),
                            enter = fadeIn(tween(200)) + expandHorizontally(tween(200)),
                            exit = fadeOut(tween(200)) + shrinkHorizontally(tween(200))
                        ) {
                            // A tap target of 48 dp in the field's glass, without a Material icon button.
                            Icon(
                                LinkedOutIcons.Close,
                                contentDescription = "Clear",
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button) { viewModel.setQuery("") }
                                    .padding(12.dp)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    focusRequester = focusRequester,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            // The hint and the count are one item whose content crossfades, its height gliding, instead of two items
            // swapped at once.
            // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/CrossfadeSample.kt#CrossfadeSample
            // not ported: CrossfadeSample (the sample's "A" and "B" pages; the pages here are the hint, the count and nothing)
            item(key = "status") {
                // The text and whether it is a hint (centred, large) or the count (small, at the start).
                val status: Pair<String, Boolean>? = when {
                    hint != null -> hint to true
                    state.results.isNotEmpty() ->
                        (if (state.results.size == 1) "1 post" else "${state.results.size} posts") to false
                    else -> null
                }
                Box(Modifier.animateItem().animateContentSize()) {
                    Crossfade(targetState = status, label = "searchStatus") { shown ->
                        when {
                            shown == null -> Unit
                            shown.second -> Text(
                                shown.first,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(32.dp)
                            )
                            else -> Text(
                                shown.first,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Each result keeps its key and glides to its new place when the query changes the list.
            // https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/samples/src/main/java/androidx/compose/foundation/samples/LazyDslSamples.kt#AnimateItemSample
            // not ported: AnimateItemSample (its "Add new item" and "Shuffle" Buttons, the list of strings and Column, the rows here are the posts and the query moves them)
            items(state.results, key = { it.id }) { post ->
                PostCard(
                    modifier = Modifier.animateItem(),
                    post = post,
                    onClick = {
                        focus.clearFocus()
                        onOpenPost(post)
                    },
                    onOpenLink = { uriHandler.openUri(it) },
                    onDownload = { downloader.download(it, post.authorHandle, post.permalink) },
                    showStats = settings.showCounts,
                    onOpenMedia = { index -> viewing = post to index }
                )
            }
        }
    }
}
