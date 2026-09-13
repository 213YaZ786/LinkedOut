package com.linkedout.app.feature.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
@OptIn(ExperimentalMaterial3Api::class)
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
            media = post.media,
            startIndex = index,
            onDownload = { downloader.download(it, post.authorHandle) },
            onDismiss = { viewing = null }
        )
    }

    Scaffold(
        // The NavHost's own Scaffold already stands clear of the status and
        // navigation bars. A nested Scaffold applies them a second time, and a
        // TopAppBar a third, which is where the empty band above and below the
        // content came from. Insets are owned once, up there.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(LinkedOutIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    TextField(
                        value = typed,
                        onValueChange = viewModel::setQuery,
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        placeholder = { Text("Search saved posts") },
                        trailingIcon = {
                            if (typed.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(LinkedOutIcons.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp)
                            .focusRequester(focusRequester)
                    )
                }
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

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
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
                    onDownload = { downloader.download(it, post.authorHandle) },
                    showStats = settings.showCounts,
                    onOpenMedia = { index -> viewing = post to index }
                )
            }
        }
    }
}
