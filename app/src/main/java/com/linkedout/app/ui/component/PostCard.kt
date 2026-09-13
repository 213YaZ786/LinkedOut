package com.linkedout.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import com.linkedout.app.ui.theme.LocalDisplayPrefs
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.linkedout.app.core.model.CommunityNote
import com.linkedout.app.core.model.LinkCard
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.core.model.Poll
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostKind
import com.linkedout.app.ui.icon.LinkedOutIcons
import java.util.concurrent.TimeUnit

/**
 * A post, laid out the way a reader expects one: who, when, what, then the
 * media, then the numbers. Media carries its own download control, since
 * saving a picture is the single most common thing people want from a client
 * like this and burying it in a long press is hostile.
 */
@Composable
fun PostCard(
    post: Post,
    onClick: () -> Unit,
    onOpenLink: (String) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onOpenMedia: (index: Int) -> Unit = {},
    showStats: Boolean = true,
    modifier: Modifier = Modifier
) {
    val compact = LocalDisplayPrefs.current.compact
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = if (compact) 7.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
        ) {
            post.contextLine()?.let { ContextLine(it) }

            Row(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Avatar(post.avatarUrl, post.authorName, size = if (compact) 36.dp else 44.dp)

                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)
                ) {
                    NameRow(post)

                    if (post.text.isNotBlank()) {
                        Text(post.text, style = MaterialTheme.typography.bodyLarge)
                    }

                    if (post.media.isNotEmpty()) {
                        MediaBlock(post = post, onDownload = onDownload, onOpen = onOpenMedia)
                    }

                    // Nitter's order: poll, link card, quote, then the note.
                    post.poll?.let { PollBlock(it) }

                    post.card?.let { card ->
                        LinkCardBlock(card = card, onClick = { card.url?.let(onOpenLink) })
                    }

                    post.quoted?.let { quote ->
                        QuoteBlock(
                            handle = quote.handle,
                            name = quote.name,
                            text = quote.text,
                            note = quote.note,
                            onClick = { onOpenLink(quote.permalink) }
                        )
                    }

                    post.note?.let { NoteBlock(it, maxLines = 6) }

                    if (showStats) post.stats?.let { StatsRow(it) }
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
}

@Composable
internal fun Avatar(url: String?, name: String, size: Dp = 44.dp) {
    val shape = if (LocalDisplayPrefs.current.squareAvatars) RoundedCornerShape(size * 0.22f) else CircleShape
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NameRow(post: Post) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            post.authorName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            " @${post.authorHandle}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            " · ${relativeTime(post.publishedAtMillis)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
internal fun ContextLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

/** Stands in for a picture or video that waits for a tap on mobile data. */
@Composable
private fun HeldMedia(type: MediaType, modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (type == MediaType.PHOTO) LinkedOutIcons.Download else LinkedOutIcons.Play,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            when (type) {
                MediaType.PHOTO -> "Photo, tap to load"
                MediaType.GIF -> "GIF, tap to load"
                MediaType.VIDEO -> "Video, tap to load"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Mobile data, Wi-Fi only is on",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun MediaBlock(post: Post, onDownload: (MediaItem) -> Unit, onOpen: (Int) -> Unit) {
    // Compact trades some picture for a list that moves faster.
    val ratio = if (LocalDisplayPrefs.current.compact) 2f else 16f / 9f
    val hold = rememberMediaPolicy().hold
    // Only the first video or GIF of the post chosen by the list plays inline.
    val inlineIndex = if (LocalInlinePlaying.current == post.id) {
        post.media.indexOfFirst { it.type != MediaType.PHOTO }
    } else {
        -1
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        post.media.forEachIndexed { index, item ->
            // Wi-Fi only on a metered network: nothing is fetched until the
            // reader taps. One tap loads the preview, the next opens it.
            var revealed by remember(item.previewUrl) { mutableStateOf(false) }
            val waiting = hold && !revealed
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { if (waiting) revealed = true else onOpen(index) }
            ) {
                if (waiting) {
                    HeldMedia(item.type, Modifier.fillMaxWidth().aspectRatio(ratio))
                } else {
                    AsyncImage(
                        model = item.previewUrl,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().aspectRatio(ratio)
                    )
                    // A video with no source keeps its cover and its badge and
                    // starts no player. Tapping it opens the post, where the
                    // address exists.
                    if (index == inlineIndex && item.playable) {
                        InlineVideo(
                            url = item.downloadUrl,
                            onClick = { onOpen(index) },
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }

                if (item.type != MediaType.PHOTO) {
                    Text(
                        item.durationLabel ?: if (item.type == MediaType.GIF) "GIF" else "Video",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = { onDownload(item) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .size(32.dp)
                ) {
                    Icon(
                        LinkedOutIcons.Download,
                        contentDescription = "Save to Downloads",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuoteBlock(
    handle: String,
    name: String,
    text: String,
    onClick: () -> Unit,
    note: CommunityNote? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (name.isBlank()) "@$handle" else "$name  @$handle",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (text.isNotBlank()) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            note?.let { NoteBlock(it, maxLines = 3) }
        }
    }
}

/**
 * A community note, set apart from the post it qualifies. [text] replaces the
 * plain note text when the caller has made its links tappable. The block has
 * no click of its own, so a tap on it in a list still opens the post.
 */
@Composable
internal fun NoteBlock(note: CommunityNote, text: AnnotatedString? = null, maxLines: Int = Int.MAX_VALUE) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(LinkedOutIcons.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Community note", style = MaterialTheme.typography.labelLarge)
            }
            if (text != null) {
                Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
            } else {
                Text(note.text, style = MaterialTheme.typography.bodyMedium, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A link preview. Large cards put the picture on top, small ones beside the
 * text, as Nitter lays them out. The picture follows the media policy: on a
 * metered network with Wi-Fi only on, nothing is fetched until the reader
 * taps the placeholder, and a tap on the text still opens the link.
 */
@Composable
internal fun LinkCardBlock(card: LinkCard, onClick: () -> Unit) {
    val hold = rememberMediaPolicy().hold
    var revealed by remember(card.imageUrl) { mutableStateOf(false) }
    val image = card.imageUrl
    val waiting = hold && !revealed

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (card.large || image == null) {
            Column {
                if (image != null) {
                    val ratio = if (LocalDisplayPrefs.current.compact) 2.4f else 1.91f
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        if (waiting) {
                            HeldCardImage(Modifier.matchParentSize().clickable { revealed = true })
                        } else {
                            AsyncImage(
                                model = image,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                        }
                        if (card.isArticle) ArticleBadge(Modifier.align(Alignment.BottomStart).padding(8.dp))
                    }
                }
                LinkCardText(card, Modifier.padding(12.dp))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(84.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    if (waiting) {
                        HeldCardImage(Modifier.matchParentSize().clickable { revealed = true }, short = true)
                    } else {
                        AsyncImage(
                            model = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
                LinkCardText(card, Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun LinkCardText(card: LinkCard, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        card.destination?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (card.isArticle && card.imageUrl == null) {
            ArticleBadge(Modifier)
        }
        Text(
            card.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        card.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HeldCardImage(modifier: Modifier, short: Boolean = false) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            LinkedOutIcons.Download,
            contentDescription = "Load the preview image",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        if (!short) {
            Text(
                "Preview, tap to load",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArticleBadge(modifier: Modifier) {
    Text(
        "Article",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * A poll, read only. Each choice is a bar filled to its share, the leading
 * one in the accent colour. Voting needs an account, so there is nothing to tap.
 */
@Composable
internal fun PollBlock(poll: Poll) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        poll.options.forEach { option ->
            val fill = if (option.leader) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                // The fill sits in its own box: under matchParentSize the width
                // is fixed, and a fraction would be clamped back to full width.
                Box(Modifier.matchParentSize()) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(option.percent / 100f)
                            .background(fill)
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (option.leader) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (option.leader) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${option.percent}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (option.leader) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (option.leader) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
        pollInfo(poll)?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun pollInfo(poll: Poll): String? {
    val votes = poll.votes?.let { if (it == 1L) "1 vote" else "%,d votes".format(it) }
    return listOfNotNull(votes, poll.status).joinToString(" · ").ifBlank { null }
}

@Composable
internal fun StatsRow(stats: com.linkedout.app.core.model.PostStats) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        stats.replies?.let { Stat(LinkedOutIcons.Comment, it) }
        stats.reposts?.let { Stat(LinkedOutIcons.Repost, it) }
        stats.likes?.let { Stat(LinkedOutIcons.Heart, it) }
    }
}

@Composable
private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            compactCount(value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun Post.contextLine(): String? = when {
    isPinned -> "Pinned"
    kind == PostKind.REPOST -> relatedHandle?.let { "Reposted by $it" } ?: "Repost"
    kind == PostKind.REPLY -> relatedHandle?.let { "Replying to @$it" } ?: "Reply"
    else -> null
}

internal fun compactCount(value: Int): String = when {
    value >= 1_000_000 -> "${value / 100_000 / 10.0}M"
    value >= 1_000 -> "${value / 100 / 10.0}K"
    else -> value.toString()
}

/** Compact relative time. Absolute weeks take over past a week. */
internal fun relativeTime(millis: Long): String {
    if (millis <= 0L) return ""
    val delta = System.currentTimeMillis() - millis
    if (delta < 0) return "now"

    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)

    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> "${days / 7}w"
    }
}
