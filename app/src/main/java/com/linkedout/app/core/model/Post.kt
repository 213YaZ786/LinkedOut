package com.linkedout.app.core.model

import kotlinx.serialization.Serializable

/**
 * One post, normalised.
 *
 * Every source produces this exact type, which is what lets a source be swapped
 * without the UI knowing. Fields a given source cannot supply stay null rather
 * than being faked, so the UI can hide what it does not have instead of showing
 * a plausible lie.
 */
@Serializable
data class Post(
    val id: String,
    val authorHandle: String,
    val authorName: String,
    val avatarUrl: String? = null,
    val text: String,
    val links: List<String> = emptyList(),
    val publishedAtMillis: Long,
    val permalink: String,
    val kind: PostKind = PostKind.ORIGINAL,
    val relatedHandle: String? = null,
    val isPinned: Boolean = false,
    val media: List<MediaItem> = emptyList(),
    val quoted: QuotedPost? = null,
    val card: LinkCard? = null,
    val poll: Poll? = null,
    val note: CommunityNote? = null,
    val stats: PostStats? = null
) {
    /**
     * The same post seen again, possibly from a richer source. x.com gives the
     * head of a feed first, without cards or polls, and Nitter brings them a
     * moment later for the same id. The stored post keeps its identity and
     * text, and takes whatever [fresh] knows that it does not, plus the newer
     * counts, since votes and likes move.
     */
    fun mergedWith(fresh: Post): Post {
        if (fresh.id != id) return this
        val merged = copy(
            avatarUrl = avatarUrl ?: fresh.avatarUrl,
            media = media.ifEmpty { fresh.media },
            quoted = quoted?.let { q ->
                q.copy(
                    avatarUrl = q.avatarUrl ?: fresh.quoted?.avatarUrl,
                    note = fresh.quoted?.note ?: q.note
                )
            } ?: fresh.quoted,
            card = fresh.card ?: card,
            poll = fresh.poll ?: poll,
            note = fresh.note ?: note,
            stats = stats?.let { old -> fresh.stats?.let(old::mergedWith) ?: old } ?: fresh.stats
        )
        return if (merged == this) this else merged
    }
}

@Serializable
/**
 * REACTION is a card from the Reactions panel of a profile: a post by someone
 * else that this person liked. It used to be filed as REPLY, so the card said
 * "Replying to" about someone who had not written a word.
 */
enum class PostKind { ORIGINAL, REPOST, REPLY, QUOTE, REACTION }

@Serializable
enum class MediaType { PHOTO, VIDEO, GIF }

/**
 * [previewUrl] is what gets shown, [downloadUrl] is the full resolution
 * original. Nitter serves both, and conflating them means either a blurry
 * gallery or a very slow timeline.
 */
@Serializable
data class MediaItem(
    val previewUrl: String,
    val downloadUrl: String,
    val type: MediaType,
    val durationLabel: String? = null,
    /**
     * False when this is a video LinkedIn named but gave no source for, which
     * is every video on a profile page: that page renders a cover picture and
     * a play badge and nothing else, with no `<video>` element and no address
     * anywhere in the document. [downloadUrl] is then the cover, and handing it
     * to a player produced an unreadable container error eight times per
     * scroll. The post's own page does carry the address, so the way to watch
     * it is to open the post.
     *
     * Defaults to true so everything already cached keeps working.
     */
    val playable: Boolean = true,
    /**
     * The post this video belongs to, when [playable] is false. Its own page
     * does carry the address, so the player can go and fetch it on the first
     * tap rather than asking for seventeen post pages the moment a profile
     * opens.
     */
    val sourcePostId: String? = null
)

/**
 * The post a card carries rather than writes: what was reposted, or what was
 * reacted to.
 *
 * [avatarUrl] is the original author's picture, not the resharer's. A card
 * drew one picture until now, the account being read, so ten of seventeen
 * cards on a profile showed the reader a face that had nothing to do with the
 * words underneath. Null when the page names no picture, which the card draws
 * as a silhouette rather than as a guess.
 */
@Serializable
data class QuotedPost(
    val handle: String,
    val name: String,
    val text: String,
    val permalink: String,
    val avatarUrl: String? = null,
    val note: CommunityNote? = null
)

/**
 * Context written by X's community notes, as the source shows it under a post.
 * [links] are the sources the note cites, in order of appearance.
 */
@Serializable
data class CommunityNote(
    val text: String,
    val links: List<String> = emptyList()
)

/**
 * [large] mirrors Nitter's own split: summary, app and player cards are small,
 * with a thumbnail beside the text, the others put a wide picture on top.
 * [isArticle] marks an X article preview, which Nitter draws with a badge.
 */
@Serializable
data class LinkCard(
    val title: String,
    val description: String?,
    val destination: String?,
    val imageUrl: String?,
    val url: String?,
    val large: Boolean = true,
    val isArticle: Boolean = false
)

/**
 * A poll as the server last showed it. Percentages come rounded from the
 * source, so they may not add up to exactly 100. [status] is the source's own
 * wording, for example "Final results" or "2 days left".
 */
@Serializable
data class Poll(
    val options: List<PollOption>,
    val votes: Long? = null,
    val status: String? = null
)

@Serializable
data class PollOption(
    val label: String,
    val percent: Int,
    val leader: Boolean = false
)

@Serializable
data class PostStats(
    val replies: Int? = null,
    val reposts: Int? = null,
    val likes: Int? = null,
    val views: Int? = null
) {
    /** Newer counts win, a count the fresh source does not carry is kept. */
    fun mergedWith(fresh: PostStats) = PostStats(
        replies = fresh.replies ?: replies,
        reposts = fresh.reposts ?: reposts,
        likes = fresh.likes ?: likes,
        views = fresh.views ?: views
    )
}

/** A single account's feed as fetched from one instance. */
@Serializable
data class Feed(
    val handle: String,
    val displayName: String,
    val posts: List<Post>,
    val fetchedFromHost: String,
    val fetchedAtMillis: Long,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val nextCursor: String? = null,
    val bannerUrl: String? = null,
    val location: String? = null,
    /**
     * The organisation on the top card. Only its name: LinkedIn replaces the
     * role itself with asterisks for anyone not signed in, and an asterisk run
     * is not worth showing, so it is dropped rather than displayed.
     */
    val currentCompany: String? = null,
    /** The most recent school on the top card, under the same restriction. */
    val school: String? = null,
    val stats: ProfileStats? = null
)

/** The numbers on a profile card. Any may be missing. */
@Serializable
data class ProfileStats(
    val posts: Long? = null,
    val following: Long? = null,
    val followers: Long? = null,
    val likes: Long? = null
)

/**
 * A post with its surroundings, as read from its own page. Not cached: replies
 * change constantly and are only worth reading fresh.
 *
 * [ancestors] are the posts it answers, oldest first. [continuation] is the
 * author's own thread under it. [replies] are grouped in the small chains the
 * server shows, a reply followed by the answers to it.
 */
data class Conversation(
    val ancestors: List<Post>,
    val main: Post?,
    val continuation: List<Post>,
    val replies: List<List<Post>>,
    val host: String
)
