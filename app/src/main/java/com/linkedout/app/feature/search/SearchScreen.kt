package com.linkedout.app.feature.search

import com.linkedout.app.ui.component.GlassSearchField
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.ScreenBanner
import com.linkedout.app.ui.component.navigationBarBottom
import com.linkedout.app.ui.component.statusBarTop
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

/** Search through every post saved on this phone, instantly and offline. */
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

    Scaffold(
        // The page's ground is painted under the whole app, see MainActivity.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        // The NavHost's own Scaffold already stands clear of the status and
        // navigation bars. A nested Scaffold applies them a second time, which
        // is where the empty band above and below the content came from. The
        // list below owns them once, in its content padding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        val hint = when {
            !state.loaded -> null
            state.savedCount == 0 -> "Nothing saved yet. Posts you read are kept here and can be searched offline."
            typed.isBlank() -> "Search through ${state.savedCount} posts saved on this phone, offline. " +
                "Words can be in any order."
            state.results.isEmpty() -> "No saved post matches."
            else -> null
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = LocalReadableInset.current,
                end = LocalReadableInset.current,
                top = statusBarTop(),
                bottom = navigationBarBottom()
            )
        ) {
            // The banner of the profile page, the first row of the list so the whole page scrolls with it
            // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/feed/FeedScreen.kt#L178-L209
            item(key = "banner") {
                ScreenBanner(
                    title = "Search",
                    leading = {
                        BannerAction(
                            icon = LinkedOutIcons.ArrowBack,
                            label = "Back",
                            onClick = onBack
                        )
                    }
                )
            }

            // Just under the banner, as Accounts sets its field
            // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/accounts/AccountsScreen.kt#L224-L256
            item(key = "field") {
                GlassSearchField(
                    value = typed,
                    onValueChange = viewModel::setQuery,
                    placeholder = "Search saved posts",
                    trailingIcon = {
                        if (typed.isNotEmpty()) {
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

            if (hint != null) {
                item(key = "hint") {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp)
                    )
                }
            } else if (state.results.isNotEmpty()) {
                item(key = "count") {
                    Text(
                        if (state.results.size == 1) "1 post" else "${state.results.size} posts",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            items(state.results, key = { it.id }) { post ->
                PostCard(
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
