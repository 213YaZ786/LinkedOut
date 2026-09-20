package com.linkedout.app.feature.post

import com.linkedout.app.ui.component.PostCard
import com.linkedout.app.core.common.present
import com.linkedout.app.core.common.AppError
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkedout.app.core.media.MediaDownloader
import com.linkedout.app.data.settings.SettingsStore
import com.linkedout.app.core.link.LinkRouter
import com.linkedout.app.core.link.ShareLink
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostStats
import com.linkedout.app.feature.media.MediaViewer
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.ContextLine
import com.linkedout.app.ui.component.LinkCardBlock
import com.linkedout.app.ui.component.NoteBlock
import com.linkedout.app.ui.component.PollBlock
import com.linkedout.app.ui.component.MediaBlock
import com.linkedout.app.ui.component.QuoteBlock
import com.linkedout.app.ui.component.InnerZoneShape
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.component.compactCount
import com.linkedout.app.ui.component.contextLine
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.foundation.layout.WindowInsets

/**
 * One post, read in full. Everything comes from the local cache, so opening a
 * post is instant and costs no request. Text is selectable, mentions open the
 * profile in the app, links open the real URL, and sharing uses the x.com
 * address so the recipient needs nothing special to open it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    id: String,
    from: String?,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenPost: (Post) -> Unit,
    viewModel: PostDetailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(id) { viewModel.load(id, from) }

    val post = state.post

    Scaffold(
        // The NavHost's own Scaffold already stands clear of the status and
        // navigation bars. A nested Scaffold applies them a second time, and a
        // TopAppBar a third, which is where the empty band above and below the
        // content came from. Insets are owned once, up there.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Post") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(LinkedOutIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (post != null) {
                        IconButton(onClick = { share(context, viewModel.shareLink(post)) }) {
                            Icon(LinkedOutIcons.Share, contentDescription = "Share")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                post != null -> ConversationView(
                    post = post,
                    thread = state.thread,
                    onOpenProfile = onOpenProfile,
                    onOpenPost = onOpenPost,
                    onRetry = viewModel::retryThread,
                    shareLink = viewModel.shareLink(post)
                )
                state.missing -> {
                    val error = (state.thread as? ThreadState.Failed)?.error
                    Text(
                        // LinkedIn's own reason when the page gave one, a deletion
                        // or a profile gone private, rather than a guess.
                        (error as? AppError.PostUnavailable)
                            ?.present()?.let { "${it.headline}. ${it.explanation}" }
                            ?: "This post can't be shown. It may have been deleted, and it is not saved on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                }
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

/**
 * The post in the middle of its conversation: what it answers above, then
 * the post itself in full, the author's own thread, and the replies.
 */
@Composable
private fun ConversationView(
    post: Post,
    thread: ThreadState,
    onOpenProfile: (String) -> Unit,
    onOpenPost: (Post) -> Unit,
    onRetry: () -> Unit,
    shareLink: String
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val downloader: MediaDownloader = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()
    var viewing by remember { mutableStateOf<Pair<Post, Int>?>(null) }
    val conversation = (thread as? ThreadState.Ready)?.conversation

    viewing?.let { (shown, index) ->
        MediaViewer(
            media = shown.media,
            startIndex = index,
            onDownload = { downloader.download(it, shown.authorHandle) },
            onDismiss = { viewing = null }
        )
    }

    @Composable
    fun ReplyCard(item: Post, modifier: Modifier = Modifier) {
        PostCard(
            post = item,
            onClick = { onOpenPost(item) },
            onOpenLink = { uriHandler.openUri(it) },
            onDownload = { downloader.download(it, item.authorHandle) },
            showStats = settings.showCounts,
            onOpenMedia = { index -> viewing = item to index },
            modifier = modifier
        )
    }

    LazyColumn(Modifier.fillMaxSize()) {
        val ancestors = conversation?.ancestors.orEmpty()
        if (ancestors.isNotEmpty()) {
            item(key = "earlier") { SectionLabel("Earlier in the conversation") }
            items(ancestors, key = { "a" + it.id }) { ReplyCard(it) }
        }

        item(key = "main") {
            PostBody(
                post = post,
                showCounts = settings.showCounts,
                onOpenProfile = onOpenProfile,
                onOpenMedia = { index -> viewing = post to index },
                shareLink = shareLink
            )
        }

        val continuation = conversation?.continuation.orEmpty()
        if (continuation.isNotEmpty()) {
            item(key = "threadlabel") { SectionLabel("Thread") }
            items(continuation, key = { "t" + it.id }) { ReplyCard(it) }
        }

        item(key = "replieslabel") { SectionLabel("Replies") }

        when (thread) {
            ThreadState.Loading -> item(key = "loading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Loading replies", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            is ThreadState.Failed -> item(key = "failed") {
                val presentation = thread.error.present()
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // A saved post that is gone from X: say so plainly. There
                    // are no replies to fetch and retrying changes nothing.
                    val gone = thread.error as? AppError.PostUnavailable
                    Text(
                        if (gone != null) "No longer available on LinkedIn" else "Replies couldn't be loaded",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (gone != null) presentation.explanation else presentation.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    // The wall is passed by the engine or not at all, so the
                    // only thing left for a reader to do is ask again.
                    if (gone == null) {
                        TextButton(onClick = onRetry) { Text("Try again") }
                    }
                }
            }

            is ThreadState.Ready -> {
                val chains = thread.conversation.replies
                if (chains.isEmpty()) {
                    item(key = "noreplies") {
                        Text(
                            "No replies yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        )
                    }
                } else {
                    chains.forEachIndexed { chainIndex, chain ->
                        chain.forEachIndexed { index, reply ->
                            item(key = "r$chainIndex-${reply.id}") {
                                // Answers inside a chain sit slightly in, so the
                                // exchange reads as one conversation.
                                ReplyCard(reply, if (index > 0) Modifier.padding(start = 24.dp) else Modifier)
                            }
                        }
                    }
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { LinkRouter.openOutside(context, postUrl(post)) }) {
                                Text("See all comments on LinkedIn")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostBody(
    post: Post,
    showCounts: Boolean,
    onOpenProfile: (String) -> Unit,
    onOpenMedia: (Int) -> Unit,
    shareLink: String
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val downloader: MediaDownloader = koinInject()

    Zone(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = ZoneGap)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            post.contextLine()?.let { ContextLine(it) }

            // Same fill as the zone it sits in, so the author row reads as a
            // tappable line and not as a second box drawn inside the first.
            Surface(
                onClick = { onOpenProfile(post.authorHandle) },
                shape = InnerZoneShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Avatar(url = post.avatarUrl, name = post.authorName, size = 48.dp)
                    Column {
                        Text(
                            post.authorName.ifBlank { "@${post.authorHandle}" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "@${post.authorHandle}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (post.text.isNotBlank()) {
                val linkColor = MaterialTheme.colorScheme.primary
                val annotated = remember(post.id, linkColor) {
                    linkify(post.text, post.links, linkColor, onOpenProfile)
                }
                SelectionContainer {
                    Text(
                        annotated,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 27.sp)
                    )
                }
            }

            if (post.media.isNotEmpty()) {
                MediaBlock(
                    post = post,
                    onDownload = { downloader.download(it, post.authorHandle) },
                    onOpen = onOpenMedia
                )
            }

            // Nitter's order: poll, link card, quote, then the note.
            post.poll?.let { PollBlock(it) }

            post.card?.let { card ->
                LinkCardBlock(card = card, onClick = { card.url?.let(uriHandler::openUri) })
            }

            post.quoted?.let { quote ->
                QuoteBlock(
                    handle = quote.handle,
                    name = quote.name,
                    text = quote.text,
                    avatarUrl = quote.avatarUrl,
                    note = quote.note,
                    onClick = { uriHandler.openUri(quote.permalink) }
                )
            }

            post.note?.let { note ->
                val noteLinkColor = MaterialTheme.colorScheme.primary
                val noteText = remember(post.id, note, noteLinkColor) {
                    linkify(note.text, note.links, noteLinkColor, onOpenProfile)
                }
                SelectionContainer { NoteBlock(note, text = noteText) }
            }

            fullDate(post.publishedAtMillis)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            post.stats?.takeIf { showCounts }?.let { stats ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StatsLine(stats)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            val url = postUrl(post)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Straight to X or a browser. Through the app's own link handler
                // this would land back on this screen.
                FilledTonalButton(onClick = { LinkRouter.openOutside(context, url) }) { Text("Open on LinkedIn") }
                // Share and copy follow the reader's choice, x.com or Nitter.
                OutlinedButton(onClick = { share(context, shareLink) }) { Text("Share") }
                OutlinedButton(onClick = { copy(context, shareLink) }) { Text("Copy link") }
            }
        }
    }
}

/** Every count the source gave, spelled out. Counts it did not give are not guessed. */
@Composable
private fun StatsLine(stats: PostStats) {
    val parts = listOfNotNull(
        stats.replies?.let { "${compactCount(it)} replies" },
        stats.reposts?.let { "${compactCount(it)} reposts" },
        stats.likes?.let { "${compactCount(it)} likes" },
        stats.views?.let { "${compactCount(it)} views" }
    )
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("   "),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/** The post's own address, for "Open on LinkedIn". */
private fun postUrl(post: Post): String =
    ShareLink.forPost(post.authorHandle, post.id, post.permalink)

private fun fullDate(millis: Long): String? {
    if (millis <= 0L) return null
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
}

private fun share(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

/** Android 13 and later confirm the copy themselves, so no toast is added. */
private fun copy(context: Context, url: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("Post link", url))
}
