package com.linkedout.app.data.linkedin

import com.linkedout.app.core.link.LinkedInLink
import com.linkedout.app.core.model.Conversation
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostId
import com.linkedout.app.core.model.PostKind
import com.linkedout.app.core.model.PostStats
import com.linkedout.app.core.model.QuotedPost
import com.linkedout.app.data.linkedin.Markup.attributeAfter
import com.linkedout.app.data.linkedin.Markup.jsonLdBlocks
import com.linkedout.app.data.linkedin.Markup.objectsOfType
import com.linkedout.app.data.linkedin.Markup.ownNumber
import com.linkedout.app.data.linkedin.Markup.ownString
import com.linkedout.app.data.linkedin.Markup.textAfter

/**
 * Parses a LinkedIn guest post page into a [Conversation].
 *
 * A different page from the profile, and a different template: none of the
 * profile's class names appear here. The two parsers share [Markup] and
 * nothing else, which is why this is a separate file rather than a branch.
 *
 * Same merge as the profile, for the same reason. The JSON-LD graph in the
 * head carries the exact time, the untruncated text and the real counts. The
 * rendered card carries the media, the author's handle and the commenter
 * avatars. Neither is complete, so both are read.
 *
 * Three traps live on this page:
 *
 * - The graph's root type follows the media. A plain, image or carousel post
 *   is a SocialMediaPosting, a video post is a VideoObject, and the two spell
 *   the same fields differently. Both are accepted, and a post whose graph is
 *   missing entirely still parses from the card alone.
 * - "More Relevant Posts" below the fold uses the same card markup as the post
 *   itself. Parsing the whole page would return six strangers' posts as if
 *   they were part of the conversation, so the page is cut at that section
 *   before anything is read.
 * - A carousel renders five images and a "+7" badge, while the graph lists all
 *   twelve. The graph wins for media, which is the one place the card is not
 *   the richer copy.
 *
 * What a guest cannot have: the comment list stops at ten and the rest is
 * behind a sign in, so [Conversation.replies] is a first page with no way to
 * ask for a second. The count from the graph is the honest total and is kept
 * in the main post's stats, so the UI can say how many are not shown.
 */
class PostPageParser {

    companion object {
        /** Bumped whenever the markers below change, so a failure is dated. */
        const val SELECTOR_SET_VERSION = 1

        /** Present on a real post page, absent on the authwall stub. */
        const val PAGE_MARKER = "main-feed-activity-card"

        /** Everything from here down is other people's posts, not this one. */
        private const val RELATED = "related-posts"

        private const val CARD = "main-feed-activity-card"
        private const val COMMENTARY = "main-feed-activity-card__commentary"
        private const val RESHARE = "feed-reshare-content"
        private const val RESHARE_COMMENTARY = "feed-reshare-content__commentary"
        private const val MEDIA_LIST = "feed-images-content"
        private const val DOCUMENT = "data-native-document-config"
        private const val COMMENT = "<section class=\"comment "
    }

    fun parse(html: String, id: String): Conversation? {
        if (PAGE_MARKER !in html) return null
        val page = html.beforeRelatedPosts()
        val graph = jsonLdBlocks(html).firstOrNull().orEmpty()

        val main = page.parseMain(graph, id) ?: return null
        return Conversation(
            ancestors = emptyList(),
            main = main,
            continuation = emptyList(),
            // One chain per comment. LinkedIn nests replies under a comment,
            // but a guest is never shown them, so every chain has one post.
            replies = page.parseComments(main).map(::listOf),
            host = LinkedInHost.HOST
        )
    }

    /**
     * The page down to "More Relevant Posts". Those cards carry the same class
     * names as the post itself, so the cut has to happen before any marker is
     * looked for, not after.
     */
    private fun String.beforeRelatedPosts(): String =
        indexOf(RELATED).takeIf { it > 0 }?.let { substring(0, it) } ?: this

    private fun String.parseMain(graph: String, id: String): Post? {
        val at = indexOf(CARD).takeIf { it >= 0 } ?: return null
        val card = substring(at, indexOf(COMMENT).takeIf { it > at } ?: length)

        val urn = card.attributeAfter(0, "data-activity-urn", window = 400)
        val activityId = urn?.substringAfterLast(':')?.takeIf { it.isNotEmpty() } ?: id

        val post = graph.rootObject()
        val permalink = post?.let { graph.ownString("@id", it) }
            ?: canonical(html = this)
            ?: LinkedInLink.canonical("feed/update/urn:li:activity:$activityId")

        val handle = card.authorHandle()
        val fromGraph = post?.let { graph.ownString("articleBody", it) ?: graph.ownString("description", it) }
        val fromCard = card.textAfter("data-test-id=\"$COMMENTARY\"", "</p>")

        return Post(
            id = PostId.normalize(permalink).takeIf { it != permalink } ?: activityId,
            authorHandle = handle.orEmpty(),
            authorName = card.authorName().orEmpty(),
            // The card is tried first because it is the picture actually on
            // screen, then the graph. A company post is why the fallback
            // exists: its author is an Organization whose logo the rendered
            // lockup does not expose under the class the card reader knows.
            avatarUrl = card.avatar() ?: post?.let { graph.authorImage(it) },
            // The graph is the untruncated copy, but a card with no graph at
            // all is still worth showing, so neither is required.
            text = fromGraph?.takeIf { it.isNotBlank() } ?: fromCard.orEmpty(),
            links = Markup.links(card.commentaryHtml().orEmpty()),
            publishedAtMillis = post?.let { graph.ownString("datePublished", it) }
                ?.let(Timestamps::parseIso)
                ?: Timestamps.fromRelative(card.relativeAge()),
            permalink = permalink.substringBefore('?'),
            kind = if (RESHARE in card) PostKind.REPOST else PostKind.ORIGINAL,
            media = graph.mediaFromGraph(post).ifEmpty { card.mediaFromCard() },
            quoted = card.parseReshare(),
            stats = graph.statsFromGraph(post) ?: card.statsFromCard()
        )
    }

    /**
     * The author's picture out of the graph: a person's photo or a company's
     * logo, both written as an ImageObject under the author's own "image".
     *
     * Bounded twice on purpose. Every record in this graph owns an ImageObject
     * somewhere, the post's own pictures among them, so a loose search would
     * hand back the first slide of a carousel as the author's face. The walk
     * is author, then that object's image, then that object's url, and each
     * step stays inside the braces of the one before.
     */
    private fun String.authorImage(post: Int): String? {
        val author = ownKey("author", post).takeIf { it >= 0 } ?: return null
        val authorBody = indexOf('{', author).takeIf { it >= 0 } ?: return null
        val image = ownKey("image", authorBody + 1).takeIf { it >= 0 } ?: return null
        val imageBody = indexOf('{', image).takeIf { it >= 0 } ?: return null
        return ownString("url", imageBody + 1)?.let(Markup::decodeEntities)
    }

    /**
     * The record describing this post. A video post is a VideoObject, anything
     * else is a SocialMediaPosting, and LinkedIn has also served Article and
     * DiscussionForumPosting for the same place, so all four are accepted in
     * the order they are most likely to be right.
     */
    private fun String.rootObject(): Int? =
        listOf("SocialMediaPosting", "VideoObject", "DiscussionForumPosting", "Article")
            .firstNotNullOfOrNull { type -> objectsOfType(type).firstOrNull() }

    /** The canonical link the head declares, which survives a country subdomain. */
    private fun canonical(html: String): String? =
        html.attributeAfter("rel=\"canonical\"", "href", window = 200)
            ?.let(Markup::decodeEntities)
            ?.takeIf { LinkedInLink.parse(it) != null }
            ?.let { LinkedInLink.parse(it) }
            ?.let { link -> (link as? LinkedInLink.Post)?.permalink ?: return null }

    // ---- the card ----------------------------------------------------------

    private fun String.authorHandle(): String? {
        val href = attributeAfter(indexOf("public_post_feed-actor-name"), "href", window = 1) ?: linkBeforeActorName()
        return href?.let(::handleIn)
    }

    /**
     * The actor link, read backwards from its tracking name.
     *
     * LinkedIn writes href before data-tracking-control-name inside the same
     * tag, so reading forward from the marker finds the next anchor, which is
     * the author of the reshared post on a repost card. Wrong author on every
     * repost, which is exactly the mistake the profile parser made in reverse.
     */
    private fun String.linkBeforeActorName(): String? {
        val at = indexOf("public_post_feed-actor-name").takeIf { it > 0 } ?: return null
        val open = lastIndexOf("<a", at).coerceAtLeast(0)
        return attributeAfter(open, "href", window = 400)
    }

    private fun handleIn(href: String): String? {
        val clean = Markup.decodeEntities(href).substringBefore('?')
        return clean.substringAfter("/in/", "").takeIf { it.isNotEmpty() }
            ?: clean.substringAfter("/company/", "").takeIf { it.isNotEmpty() }
            ?: clean.substringAfter("/showcase/", "").trimEnd('/').takeIf { it.isNotEmpty() }
    }

    /**
     * The author's display name.
     *
     * Read from the name anchor rather than from the lockup, because the
     * avatar is sometimes an img and sometimes a div with an aria-label, and
     * the lockup's first closing tag is then the image link rather than the
     * name. Both of the avatar's own labels are kept as a fallback for the
     * same reason.
     */
    private fun String.authorName(): String? =
        textAfter("public_post_feed-actor-name", "</a>")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: listOf("alt", "aria-label")
                .firstNotNullOfOrNull { attributeAfter("public_post_feed-actor-image", it, window = 900) }
                ?.removePrefix("View profile for ")
                ?.removePrefix("View organization page for ")
                ?.takeIf { it.isNotBlank() }

    private fun String.avatar(): String? =
        attributeAfter("hue-web-entity__image", "data-delayed-url")?.let(Markup::decodeEntities)

    private fun String.commentaryHtml(): String? {
        val at = indexOf("data-test-id=\"$COMMENTARY\"").takeIf { it >= 0 } ?: return null
        val open = indexOf('>', at)
        val close = indexOf("</p>", open)
        return if (open < 0 || close < 0) null else substring(open + 1, close)
    }

    /** The card's own "2w" label, which only matters when the graph is absent. */
    private fun String.relativeAge(): String? {
        val end = indexOf(COMMENTARY).takeIf { it > 0 } ?: minOf(length, 6000)
        return AGE.find(Markup.plainText(substring(0, end)))?.value
    }

    // ---- media -------------------------------------------------------------

    /**
     * Every image the graph lists. A carousel renders five thumbnails and a
     * "+7" badge, so the card is short by however many the badge hides. The
     * graph has them all, which is the one field where it beats the card.
     */
    private fun String.mediaFromGraph(post: Int?): List<MediaItem> {
        if (post == null) return emptyList()
        val video = ownString("contentUrl", post)
        if (video != null) {
            val cover = ownString("thumbnailUrl", post)
            return listOf(MediaItem(previewUrl = cover ?: video, downloadUrl = video, type = MediaType.VIDEO))
        }
        // Bounded to the post's own image property. Every record in the graph
        // has an ImageObject somewhere, the author's photo among them, so a
        // search across the post would attach the writer's avatar to a post
        // that carries no picture at all.
        val images = Markup.run { ownKey("image", post) }.takeIf { it >= 0 } ?: return emptyList()
        val range = images..valueEnd(images + "\"image\":".length)
        return objectsOfType("ImageObject")
            .filter { it in range }
            .mapNotNull { ownString("url", it) }
            .ifEmpty { listOfNotNull(ownString("url", images)) }
            .distinct()
            .map { MediaItem(previewUrl = it, downloadUrl = it, type = MediaType.PHOTO) }
    }

    /** The rendered images, for a post whose graph said nothing about them. */
    private fun String.mediaFromCard(): List<MediaItem> {
        document()?.let { return listOf(it) }
        val at = indexOf(MEDIA_LIST).takeIf { it >= 0 } ?: return emptyList()
        val list = substring(at, indexOf("</ul>", at).takeIf { it > at } ?: length)
        return IMAGE.findAll(list)
            .map { Markup.decodeEntities(it.groupValues[1]) }
            .filter { it.startsWith("http") }
            .distinct()
            .map { MediaItem(previewUrl = it, downloadUrl = it, type = MediaType.PHOTO) }
            .toList()
    }

    /**
     * A shared PDF or slide deck. LinkedIn renders it as an iframe whose
     * config attribute is a JSON blob with the cover pages in it, so the first
     * cover stands in as the preview rather than showing nothing at all.
     */
    private fun String.document(): MediaItem? {
        val at = indexOf(DOCUMENT).takeIf { it >= 0 } ?: return null
        val config = Markup.decodeEntities(substring(at, minOf(at + 6000, length)))
        val cover = COVER.find(config)?.groupValues?.get(1) ?: return null
        return MediaItem(previewUrl = cover, downloadUrl = cover, type = MediaType.PHOTO)
    }

    // ---- reshare -----------------------------------------------------------

    private fun String.parseReshare(): QuotedPost? {
        val at = indexOf(RESHARE).takeIf { it >= 0 } ?: return null
        val inner = substring(at)
        val href = inner.attributeAfter("public_post_reshare_feed-actor-image", "href", window = 1)
            ?: inner.lastIndexOf("<a", inner.indexOf("public_post_reshare_feed-actor-image").coerceAtLeast(0))
                .coerceAtLeast(0)
                .let { inner.attributeAfter(it, "href", window = 400) }
        val urn = inner.attributeAfter(0, "data-activity-urn", window = 600)
        val id = urn?.substringAfterLast(':').orEmpty()
        return QuotedPost(
            handle = href?.let(::handleIn).orEmpty(),
            name = inner.attributeAfter("public_post_reshare_feed-actor-image", "aria-label", window = 400)
                ?.removePrefix("View profile for ")
                ?.removePrefix("View organization page for ")
                .orEmpty(),
            text = inner.textAfter("data-test-id=\"$RESHARE_COMMENTARY\"", "</p>").orEmpty(),
            permalink = if (id.isNotEmpty()) {
                LinkedInLink.canonical("feed/update/urn:li:activity:$id")
            } else {
                ""
            }
        )
    }

    // ---- stats -------------------------------------------------------------

    /**
     * Reactions and comments from the graph, which states the real totals. The
     * card agrees for reactions and for the comment count, but only ten
     * comments are actually rendered, so the count is the only honest source
     * for how many exist.
     */
    private fun String.statsFromGraph(post: Int?): PostStats? {
        if (post == null) return null
        // Bounded to the post's own interactionStatistic. Scanning from the
        // post for "LikeAction" finds the first comment's like count instead,
        // because the comment array is written before the totals.
        val counters = Markup.run { ownKey("interactionStatistic", post) }
            .takeIf { it >= 0 }
            ?.let { it..valueEnd(it + "\"interactionStatistic\":".length) }
        val likes = counters
            ?.let { range -> indexOf("LikeAction\"", range.first).takeIf { it in range } }
            ?.let { Markup.run { jsonNumber("userInteractionCount", it) } }
            ?.toInt()
        val comments = ownNumber("commentCount", post)?.toInt()
            ?: counters
                ?.let { range -> indexOf("CommentAction\"", range.first).takeIf { it in range } }
                ?.let { Markup.run { jsonNumber("userInteractionCount", it) } }
                ?.toInt()
        return if (likes == null && comments == null) null else PostStats(replies = comments, likes = likes)
    }

    /**
     * The end of the value starting at [from], which is an object, an array or
     * a scalar. Needed because interactionStatistic is one counter on some
     * post shapes and a list of them on others.
     */
    private fun String.valueEnd(from: Int): Int {
        var index = from
        while (index < length && this[index] == ' ') index++
        val opener = getOrNull(index) ?: return length
        if (opener != '{' && opener != '[') return indexOf(',', index).takeIf { it > 0 } ?: length
        val closer = if (opener == '{') '}' else ']'
        var depth = 0
        var inString = false
        while (index < length) {
            val character = this[index]
            when {
                inString && character == '\\' -> index++
                character == '"' -> inString = !inString
                inString -> Unit
                character == opener -> depth++
                character == closer -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return length
    }

    private fun String.statsFromCard(): PostStats? {
        val likes = attributeAfter("data-test-id=\"social-actions__reactions\"", "data-num-reactions", window = 600)
            ?.toIntOrNull()
        val comments = attributeAfter("data-test-id=\"social-actions__comments\"", "data-num-comments", window = 600)
            ?.toIntOrNull()
        return if (likes == null && comments == null) null else PostStats(replies = comments, likes = likes)
    }

    // ---- comments ----------------------------------------------------------

    /**
     * The comments LinkedIn shows a guest, at most ten. Each is turned into a
     * [Post] so the thread screen can render them like any other, with
     * [Post.kind] marking them as replies and [Post.relatedHandle] pointing
     * back at the post they answer.
     */
    private fun String.parseComments(main: Post): List<Post> =
        split(COMMENT).drop(1).mapNotNull { chunk -> chunk.parseComment(main) }

    private fun String.parseComment(main: Post): Post? {
        val name = textAfter("public_post_comment_actor-name", "</a>")
            ?.substringAfterLast('>')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val href = lastIndexOf("<a", indexOf("public_post_comment_actor-name").coerceAtLeast(0))
            .coerceAtLeast(0)
            .let { attributeAfter(it, "href", window = 400) }

        val urn = attributeAfter(0, "data-semaphore-content-urn", window = 8000)
        val id = urn?.substringAfterLast(',')?.trimEnd(')')?.takeIf { it.isNotEmpty() }
            ?: return null

        val text = textAfter("comment__text", "</p>").orEmpty()
        val reactions = textAfter("public_post_comment_reactions", "</a>")
            ?.let { REACTIONS.find(it)?.value?.replace(",", "")?.toIntOrNull() }

        return Post(
            id = id,
            authorHandle = href?.let(::handleIn).orEmpty(),
            authorName = name,
            avatarUrl = attributeAfter("hue-web-entity__image", "data-delayed-url")?.let(Markup::decodeEntities),
            text = text,
            links = Markup.links(this),
            publishedAtMillis = Timestamps.fromRelative(commentAge()),
            permalink = main.permalink,
            kind = PostKind.REPLY,
            relatedHandle = main.authorHandle.takeIf { it.isNotEmpty() },
            stats = reactions?.let { PostStats(likes = it) }
        )
    }

    /**
     * A comment's age, which the page only ever gives rounded. The graph has
     * the exact time for each comment too, but matching them up needs a key
     * the HTML does not carry, so the rounded label stands.
     */
    private fun String.commentAge(): String? {
        val at = indexOf("comment__duration-since").takeIf { it >= 0 } ?: return null
        val close = indexOf("</span>", at).takeIf { it > at } ?: return null
        return AGE.find(Markup.plainText(substring(at, close)))?.value
    }

    private val AGE = Regex("""\b\d+\s*(?:mo|[smhdwy])\b""")
    private val REACTIONS = Regex("""[\d,]+""")
    private val IMAGE = Regex("""data-delayed-url="([^"]+)"""")
    private val COVER = Regex(""""src"\s*:\s*"([^"]+)"""")
}
