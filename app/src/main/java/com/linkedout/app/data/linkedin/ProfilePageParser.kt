package com.linkedout.app.data.linkedin

import com.linkedout.app.core.model.Feed
import com.linkedout.app.core.model.LinkCard
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostId
import com.linkedout.app.core.model.PostKind
import com.linkedout.app.core.model.PostStats
import com.linkedout.app.core.model.ProfileStats
import com.linkedout.app.core.model.QuotedPost
import com.linkedout.app.data.linkedin.Markup.attributeAfter
import com.linkedout.app.data.linkedin.Markup.jsonLdBlocks
import com.linkedout.app.data.linkedin.Markup.jsonNumber
import com.linkedout.app.data.linkedin.Markup.jsonString
import com.linkedout.app.data.linkedin.Markup.objectEnd
import com.linkedout.app.data.linkedin.Markup.ownKey
import com.linkedout.app.data.linkedin.Markup.objectsOfType
import com.linkedout.app.data.linkedin.Markup.ownNumber
import com.linkedout.app.data.linkedin.Markup.ownString
import com.linkedout.app.data.linkedin.Markup.textAfter

/**
 * Parses a LinkedIn guest profile page into the domain model.
 *
 * The page carries the same content twice and neither copy is complete, so
 * both are read and merged:
 *
 * - A JSON-LD graph in the head, with the person's own posts: the full text,
 *   an exact ISO timestamp, the reaction count and the permalink. Clean, but
 *   it leaves out everything the person only reshared or reacted to, and it
 *   carries no media and no comment counts.
 * - The rendered Activity section, with every card including reshares, their
 *   media, their comment counts and who the original author was. Its only
 *   timestamp is a rounded "1w", which is useless for ordering a merged
 *   timeline of several people.
 *
 * So the cards are the list, and the graph supplies the real time and the
 * untruncated text for the ones it knows, matched by permalink. A card the
 * graph does not cover keeps its rounded time, which [Post.publishedAtMillis]
 * marks by landing on a whole hour boundary.
 *
 * What LinkedIn refuses to a guest is refused everywhere on the page, not just
 * in one copy: job titles and employers are replaced by runs of asterisks, and
 * the experience list is served blurred. Those fields are left null rather than
 * shown as asterisks.
 */
class ProfilePageParser {

    /** Bumped whenever the markers below change, so a parse failure is dated. */
    companion object {
        const val SELECTOR_SET_VERSION = 2

        /** Present on a real profile page, absent on the authwall stub. */
        const val PAGE_MARKER = "public_profile_v3"

        private const val ACTIVITY_CARD = "slide-list__item"
        private const val POSTS_PANEL = "tab__panel  w-full pt-1 posts"
        private const val REACTIONS_PANEL = "tab__panel  w-full pt-1 reactions"
        private const val COMMENTS_PANEL = "tab__panel  pt-1"

        private val AGE = Regex("""\b\d+\s*(?:mo|[smhdwy])\b""")
    }

    fun parse(html: String, handle: String): Feed? {
        if (PAGE_MARKER !in html) return null
        val graph = jsonLdBlocks(html).firstOrNull().orEmpty()
        val person = graph.personObject()

        val name = extractName(html) ?: person?.let { graph.ownString("name", it) } ?: return null
        val fromGraph = graph.postsFromGraph(person)
        val posts = extractCards(html, handle)
            .map { card -> fromGraph[card.id]?.let { exact -> card.withExact(exact) } ?: card }
            .sortedByDescending { it.publishedAtMillis }

        return Feed(
            handle = handle,
            displayName = name,
            posts = posts,
            fetchedFromHost = LinkedInHost.HOST,
            fetchedAtMillis = System.currentTimeMillis(),
            avatarUrl = extractAvatar(html),
            bio = person?.let { graph.ownString("description", it) },
            nextCursor = null,
            bannerUrl = html.url("cover-img__image", "src"),
            location = person?.let { graph.addressLocality(it) },
            currentCompany = extractCurrentCompany(html),
            school = extractSchool(html),
            stats = ProfileStats(followers = extractFollowers(graph, person))
        )
    }

    /**
     * The card, corrected by the graph. Deliberately not [Post.mergedWith],
     * which keeps the receiver's text and time: here those are exactly the two
     * fields the card gets wrong. The card is cut off at roughly 200 characters
     * with an ellipsis, and its time is rounded to the week. Everything else,
     * media, reshares, comment counts and who the author is, exists only on the
     * card, so it is the card that is kept and the graph that is folded in.
     */
    private fun Post.withExact(exact: Post) = copy(
        text = exact.text.ifBlank { text },
        links = exact.links.ifEmpty { links },
        publishedAtMillis = exact.publishedAtMillis,
        authorName = authorName.ifBlank { exact.authorName },
        stats = stats?.let { own -> exact.stats?.let(own::mergedWith) ?: own } ?: exact.stats
    )

    /** The person's own name, from the top card. */
    private fun extractName(html: String): String? =
        html.textAfter("top-card-layout__title", "</h1>")?.takeIf { it.isNotBlank() }

    private fun extractAvatar(html: String): String? =
        html.url("top-card__profile-image", "data-delayed-url")

    /**
     * An attribute holding a URL. LinkedIn writes them with the ampersands
     * escaped, and a media.licdn.com link carries three query parameters, so
     * an undecoded one is rejected by the CDN rather than merely ugly.
     */
    private fun String.url(marker: String, attribute: String): String? =
        attributeAfter(marker, attribute)?.let(Markup::decodeEntities)

    /** The person's town, which sits inside their nested address record. */
    private fun String.addressLocality(person: Int): String? {
        val address = ownKey("address", person)
        if (address < 0) return null
        return jsonString("addressLocality", address)?.takeIf { indexOf(it, address) < objectEnd(person) }
    }

    /**
     * The employer shown beside the top card. Only the organisation name is
     * given to guests, never the role, which arrives as asterisks.
     */
    private fun extractCurrentCompany(html: String): String? =
        html.textAfter(html.indexOf("top-card-link__description", html.indexOf("data-section=\"currentPositionsDetails\"")), "</span>")
            ?.takeIf { it.isNotBlank() && '*' !in it }

    private fun extractSchool(html: String): String? {
        val section = html.indexOf("data-section=\"educationsDetails\"")
        if (section < 0) return null
        return html.textAfter(html.indexOf("top-card-link__description", section), "</span>")
            ?.takeIf { it.isNotBlank() && '*' !in it }
    }

    /** The follower count, which the graph gives exactly and the page rounds. */
    private fun extractFollowers(graph: String, person: Int?): Long? {
        if (person == null) return null
        val follows = graph.indexOf("\"name\":\"Follows\"", person)
        if (follows < 0 || follows > graph.objectEnd(person)) return null
        return graph.jsonNumber("userInteractionCount", follows)
    }

    private fun String.personObject(): Int? =
        objectsOfType("Person").firstOrNull { at ->
            // The graph also carries a Person for each article author. The
            // profile's own record is the one that states where they live.
            indexOf("\"addressLocality\"", at) in at..objectEnd(at)
        } ?: objectsOfType("Person").lastOrNull()

    /**
     * The person's own posts out of the graph, keyed by permalink so a card can
     * claim the matching one. Only DiscussionForumPosting records are taken:
     * Article records describe LinkedIn articles, which are a separate list on
     * the page and are not activity.
     */
    private fun String.postsFromGraph(person: Int?): Map<String, Post> {
        val authorName = person?.let { ownString("name", it) }.orEmpty()
        return objectsOfType("DiscussionForumPosting").mapNotNull { at ->
            val end = objectEnd(at)
            val url = ownString("url", at) ?: ownString("mainEntityOfPage", at) ?: return@mapNotNull null
            val text = ownString("text", at).orEmpty()
            val published = ownString("datePublished", at)?.let(Timestamps::parseIso) ?: return@mapNotNull null
            // The graph writes the interaction type as a schema.org URL, so the
            // marker is the tail of the value and not the whole of it.
            val likes = indexOf("LikeAction\"", at)
                .takeIf { it in at..end }
                ?.let { jsonNumber("userInteractionCount", it) }
            val permalink = url.substringBefore('?')
            PostId.normalize(permalink) to Post(
                id = PostId.normalize(permalink),
                authorHandle = "",
                authorName = authorName,
                text = text,
                links = Markup.links(text),
                publishedAtMillis = published,
                permalink = permalink,
                stats = PostStats(likes = likes?.toInt())
            )
        }.toMap()
    }

    /**
     * One [Post] per activity card. The Posts and Reactions tabs are both
     * rendered as slide lists of the same card, so the panels are sliced apart
     * first: a card in the Reactions tab says what the person reacted to, not
     * what they wrote, and merging the two into one stream would misattribute
     * every reaction.
     */
    private fun extractCards(html: String, handle: String): List<Post> {
        val posts = html.panel(POSTS_PANEL).cardsIn(handle, PostKind.ORIGINAL)
        val reactions = html.panel(REACTIONS_PANEL).cardsIn(handle, PostKind.REPLY)
        return (posts + reactions).distinctBy { it.id }
    }

    /** The slice of the page between one tab panel and the next. */
    private fun String.panel(marker: String): String {
        val at = indexOf(marker)
        if (at < 0) return ""
        val next = listOf(POSTS_PANEL, REACTIONS_PANEL, COMMENTS_PANEL, "</main>")
            .mapNotNull { other -> indexOf(other, at + marker.length).takeIf { it > 0 } }
            .minOrNull() ?: length
        return substring(at, next)
    }

    private fun String.cardsIn(handle: String, fallbackKind: PostKind): List<Post> =
        split(ACTIVITY_CARD).drop(1).mapNotNull { chunk -> parseCard(chunk, handle, fallbackKind) }

    private fun parseCard(chunk: String, handle: String, fallbackKind: PostKind): Post? {
        val permalink = chunk.postPermalink() ?: return null
        val id = PostId.normalize(permalink)
        if (id == permalink) return null

        // The card announces what the person did with it in its own header.
        val kind = when {
            "reposted this" in chunk -> PostKind.REPOST
            "liked this" in chunk || "reacted on this" in chunk -> PostKind.REPLY
            "shared this" in chunk || "posted this" in chunk -> PostKind.ORIGINAL
            else -> fallbackKind
        }

        val reshared = chunk.parseReshare()
        // A reshare card shows the original author's words in the nested card
        // and the resharer's own comment, if any, in the outer one. Taking the
        // first see-more-text would put the original's text on the wrong author.
        val text = chunk.commentaryText(outerOnly = reshared != null).orEmpty()

        return Post(
            id = id,
            authorHandle = chunk.cardAuthorHandle() ?: handle,
            authorName = chunk.cardAuthorName().orEmpty(),
            // url(), not attributeAfter(). This was the only address in this
            // file read without decoding, so it kept its &amp; and reached the
            // CDN as "?e=...&amp;v=beta&amp;t=<signature>", where the query
            // names become amp;v and amp;t, the signature is lost and the image
            // is refused. It looked like a missing avatar, not like a bug.
            avatarUrl = chunk.url("hue-web-entity__image", "data-delayed-url"),
            text = text,
            links = Markup.links(chunk.commentaryHtml().orEmpty()),
            publishedAtMillis = Timestamps.fromRelative(chunk.relativeAge()),
            permalink = permalink,
            kind = kind,
            relatedHandle = reshared?.handle,
            media = chunk.parseMedia(id),
            quoted = reshared,
            card = chunk.parseCard(),
            stats = chunk.parseStats()
        )
    }

    private fun String.postPermalink(): String? {
        var from = 0
        while (true) {
            val at = indexOf("https://www.linkedin.com/posts/", from)
            if (at < 0) return null
            val end = indexOfFirst(at, '"', '&')
            val candidate = substring(at, end)
            if (PostId.normalize(candidate) != candidate) return candidate.substringBefore('?')
            from = at + 1
        }
    }

    private fun String.indexOfFirst(from: Int, vararg characters: Char): Int =
        characters.toList()
            .mapNotNull { character -> indexOf(character, from).takeIf { it >= 0 } }
            .minOrNull() ?: length

    /**
     * The card's own age label, "1w" or "3d". LinkedIn puts it in a plain div
     * with no marker of its own beside the author's name, and it changes class
     * between card shapes, so it is read as text out of the header rather than
     * from a selector: the header is everything before the post's own words,
     * and the first age-shaped token in it is the age. A wrong match would need
     * a number followed by exactly one of those letters to appear in a name.
     */
    private fun String.relativeAge(): String? {
        val end = listOf("see-more-text", "profile-activity-content-card")
            .mapNotNull { marker -> indexOf(marker).takeIf { it > 0 } }
            .minOrNull() ?: minOf(length, 4000)
        return AGE.find(Markup.plainText(substring(0, end)))?.value
    }

    private fun String.cardAuthorName(): String? =
        textAfter("base-main-card__title--link", "</h3>")?.takeIf { it.isNotBlank() }
            ?: textAfter("font-bold\">", "</span>")?.takeIf { it.isNotBlank() }

    /**
     * The handle of the person whose post the card carries.
     *
     * The author lockup is a div, and its link is the first one inside it, so
     * the href has to be read forward from the marker. Reading backwards finds
     * the card's own full-link, which points at the post and not at a profile,
     * and every card then falls back to the profile being read. On a card the
     * page owner only reacted to, the author is someone else entirely, so that
     * fallback silently reassigns other people's posts to them.
     */
    private fun String.cardAuthorHandle(): String? {
        val at = indexOf("publisher-author-card")
        if (at < 0) return null
        val href = attributeAfter(at, "href", window = 600) ?: return null
        return href.substringAfter("/in/", "").substringBefore('?').takeIf { it.isNotEmpty() }
    }

    /**
     * The card's own commentary. [outerOnly] stops before the nested reshare so
     * the resharer's words are not confused with the original author's.
     */
    private fun String.commentaryText(outerOnly: Boolean): String? =
        commentaryHtml(outerOnly)?.let(Markup::plainText)

    private fun String.commentaryHtml(outerOnly: Boolean = false): String? {
        val limit = if (outerOnly) indexOf("profile-activity-root-author").takeIf { it > 0 } ?: length else length
        val at = indexOf("see-more-text")
        if (at < 0 || at > limit) return null
        val open = indexOf('>', at)
        val close = indexOf("</div>", open)
        return if (open < 0 || close < 0) null else substring(open + 1, close)
    }

    /**
     * The post this card reshares, when there is one. LinkedIn marks the
     * original author's lockup inside the nested card with its own class, which
     * is what separates a reshare from an ordinary post.
     */
    private fun String.parseReshare(): QuotedPost? {
        val at = indexOf("profile-activity-root-author")
        if (at < 0) return null
        val inner = substring(at)
        val href = inner.attributeAfter("base-card__full-link", "href").orEmpty()
        return QuotedPost(
            handle = href.substringAfter("/in/", "").substringBefore('?')
                .ifEmpty { href.substringAfter("/company/", "").substringBefore('?') },
            name = inner.cardAuthorName().orEmpty(),
            text = inner.commentaryText(outerOnly = false).orEmpty(),
            permalink = inner.postPermalink() ?: postPermalink().orEmpty()
        )
    }

    /**
     * The picture or video attached to the card. LinkedIn renders a video as a
     * still with a play overlay and never exposes the stream to a guest on this
     * page, so a video is carried as its cover with the type set, and the post
     * page is where a stream can be found.
     */
    /**
     * The card's media.
     *
     * The player is looked for first. Until now a video card yielded its poster
     * image, marked VIDEO, with the same address for preview and download: the
     * player was handed a JPEG and could not play it. The real addresses are on
     * the `<video>` element, which the profile card embeds exactly as the
     * organisation card does, so one reader serves both.
     *
     * The poster path stays underneath for a card whose player is absent, and
     * for every picture.
     */
    private fun String.parseMedia(postId: String): List<MediaItem> {
        NativeVideo.read(this)?.let { return listOf(it) }
        val at = indexOf("profile-activity-content-card")
        if (at < 0) return emptyList()
        val section = substring(at, indexOfFirstMarker(at))
        val url = section.url("object-center object-contain", "data-delayed-url")
            ?: return emptyList()
        val type = if ("activity-video-play" in section) MediaType.VIDEO else MediaType.PHOTO
        // Reached only when the card held no player, which on this page is
        // always: the profile document names its videos with a play badge and
        // a cover and carries no source at all. Saying so is better than
        // passing a JPEG off as a film.
        return listOf(
            MediaItem(
                previewUrl = url,
                downloadUrl = url,
                type = type,
                playable = type != MediaType.VIDEO,
                sourcePostId = postId.takeIf { type == MediaType.VIDEO }
            )
        )
    }

    private fun String.indexOfFirstMarker(from: Int): Int =
        listOf("social-action-bar", "social-actions__reactions")
            .mapNotNull { marker -> indexOf(marker, from).takeIf { it > 0 } }
            .minOrNull() ?: length

    /** An external article the post links to, with its title and image. */
    private fun String.parseCard(): LinkCard? {
        val title = textAfter("content-title font-semibold", "</div>")?.takeIf { it.isNotBlank() }
            ?: return null
        val destination = indexOf("/redir/redirect?url=")
            .takeIf { it > 0 }
            ?.let { attributeAfter(lastIndexOf("<a", it).coerceAtLeast(0), "href") }
        return LinkCard(
            title = title,
            description = null,
            destination = destination?.let(Markup::decodeEntities),
            imageUrl = url("object-center object-contain", "data-delayed-url"),
            url = destination?.let(Markup::decodeEntities),
            large = true
        )
    }

    /**
     * Reactions and comments, which LinkedIn helpfully writes as data
     * attributes rather than only as rounded text like "1,104".
     */
    private fun String.parseStats(): PostStats? {
        val reactions = attributeAfter("data-test-id=\"social-actions__reactions\"", "data-num-reactions", window = 600)
            ?.toIntOrNull()
        val comments = attributeAfter("data-test-id=\"social-actions__comments\"", "data-num-comments", window = 600)
            ?.toIntOrNull()
        return if (reactions == null && comments == null) null
        else PostStats(replies = comments, likes = reactions)
    }
}
