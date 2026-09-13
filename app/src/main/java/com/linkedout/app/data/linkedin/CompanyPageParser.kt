package com.linkedout.app.data.linkedin

import com.linkedout.app.core.link.LinkedInLink
import com.linkedout.app.core.model.Feed
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostId
import com.linkedout.app.core.model.PostKind
import com.linkedout.app.core.model.PostStats
import com.linkedout.app.core.model.ProfileStats
import com.linkedout.app.data.linkedin.Markup.attributeAfter
import com.linkedout.app.data.linkedin.Markup.jsonLdBlocks
import com.linkedout.app.data.linkedin.Markup.objectsOfType
import com.linkedout.app.data.linkedin.Markup.ownString
import com.linkedout.app.data.linkedin.Markup.textAfter

/**
 * Selector set 3, the organisation page. A third parser, not a variant of the
 * other two.
 *
 * `/company/`, `/school/` and `/showcase/` are the same document with a
 * different vanity in the address, which is why one parser covers all three.
 * It shares no class name with the profile page and none with the post page
 * either: an organisation renders `main-feed-activity-card`, a person renders
 * the activity cards selector set 2 knows, and a post page renders its own.
 *
 * Two things this page has that a profile does not.
 *
 * Its graph is a `@graph` array holding one `DiscussionForumPosting` per post
 * plus one `Organization` for the page itself, and every post carries its exact
 * `datePublished`. On a profile only some posts appear in the graph, so there
 * the rounded card age matters. Here it is a fallback that almost never fires.
 *
 * It offers paging. `feedUpdatesBaseUrl` at the end of the list is a real
 * continuation token, unlike a profile, which serves one fixed slice and stops.
 * It is read here but not published, because following it is a second request
 * shape this parser cannot read yet. See [nextCursor].
 */
class CompanyPageParser {

    companion object {
        /** Selector set version, reported in the log and in a parse failure. */
        const val SELECTOR_SET = 3

        /**
         * The page's own identity, in a meta tag. Chosen over a class name
         * because LinkedIn reshuffles classes far more often than page keys.
         *
         * Matched as a family and not as one string, which is the correction
         * here. A company answers `d_org_guest_company_overview`, and the
         * comment above used to claim a school and a showcase answer the same.
         * They do not: your showcase page came back 200 with 272 kB and left
         * no PARSE line at all, which is this test returning null on the very
         * first line. `org_guest` is the part they share, and it appears on no
         * profile, post or wall.
         */
        const val PAGE_MARKER = "d_org_guest"

        private const val UPDATES = "data-test-id=\"updates\""
        private const val CARD = "main-feed-activity-card\n"
        private const val COMMENTARY = "data-test-id=\"main-feed-activity-card__commentary\""
        private const val LOCKUP = "main-feed-activity-card__entity-lockup"

        /**
         * The line above a card that says the page did something to someone
         * else's post, "a republié ceci" on the capture. Matched on the class
         * and not on the words, because the words are in the reader's language
         * and the class is not.
         */
        private const val HEADER = "main-feed-activity-card__header"
        private const val IMAGES = "data-test-id=\"feed-images-content\""
            private const val DOCUMENT = "data-id=\"feed-paginated-document-content\""
        private const val REACTIONS = "data-test-id=\"social-actions__reactions\""
        private const val COMMENTS = "data-test-id=\"social-actions__comments\""

        /**
         * Everything after this is not the page. The sign in modal carries the
         * same logo and the same name, the right rail carries other companies
         * in cards of its own, and both sit after the updates list. This is the
         * "More Relevant Posts" trap in a new costume: cut first, look after.
         */
        private val TAIL = listOf(
            "class=\"right-rail",
            "contextual-sign-in-modal",
            "<footer"
        )

        private val FOLLOWERS = Regex("""([\d  \u202f.,]+)\s*(?:abonn|follower|seguidor|Follower|volgers)""")
        private val DIGITS = Regex("""\d+""")
    }

    fun parse(html: String, slug: String): Feed? {
        // Read from the pageKey meta when it can be read, so a link to an
        // organisation inside another page cannot pass for one. The plain
        // search is kept as a fallback rather than a preference: if LinkedIn
        // ever writes that meta with its attributes the other way round, this
        // parser degrades to what it did before instead of refusing every
        // organisation at once.
        val key = LinkedInHost.pageKey(html)
        val recognised = if (key != null) key.startsWith(PAGE_MARKER) else PAGE_MARKER in html
        if (!recognised) return null
        val page = html.beforeTail()
        val graph = jsonLdBlocks(page).firstOrNull().orEmpty()
        val org = graph.organisationObject()

        val name = page.textAfter("top-card-layout__title", "</h1>")?.takeIf { it.isNotBlank() }
            ?: org?.let { graph.ownString("name", it) }
            ?: return null

        val fromGraph = graph.postsFromGraph()
        val posts = page.cards(slug)
            .map { card -> fromGraph[card.id]?.let { exact -> card.withExact(exact) } ?: card }
            .sortedByDescending { it.publishedAtMillis }

        return Feed(
            handle = slug,
            displayName = name,
            posts = posts,
            fetchedFromHost = LinkedInHost.HOST,
            fetchedAtMillis = System.currentTimeMillis(),
            avatarUrl = page.attributeAfter("top-card-layout__entity-image", "data-delayed-url")
                ?.let(Markup::decodeEntities)
                ?: org?.logo(graph),
            bio = org?.let { graph.ownString("description", it) },
            // Read below but deliberately not published. Publishing a cursor
            // the app cannot follow makes every screen believe there is more,
            // so scrolling to the end spends a request that returns the same
            // ten posts and is then marked as a failed page. A wasted request
            // against a host that answers 999 when it has had enough is worse
            // than no paging at all.
            nextCursor = null,
            bannerUrl = page.attributeAfter("cover-img__image", "src")?.let(Markup::decodeEntities),
            location = page.textAfter("top-card-layout__first-subline", "</h3>")?.location(),
            // An organisation has no employer and no school. The industry line
            // is the closest thing it has to a person's headline, so it goes in
            // the field the top card already shows rather than in a new one.
            currentCompany = page.textAfter("top-card-layout__headline", "</h2>")?.takeIf { it.isNotBlank() },
            school = null,
            stats = ProfileStats(followers = page.followers())
        )
    }

    // ---- the graph ---------------------------------------------------------

    /**
     * The record for the page itself. There is exactly one Organization in the
     * graph of an organisation page, but every post names its author as an
     * Organization too, in the post's own record. Those are nested and so are
     * not returned by [objectsOfType], which only walks the top level of the
     * array, but the page's record is taken as the last one rather than the
     * first out of the same caution the profile parser uses for Person.
     */
    private fun String.organisationObject(): Int? =
        objectsOfType("Organization").lastOrNull()

    /** The logo out of the graph, when the rendered top card had none. */
    private fun Int.logo(graph: String): String? {
        val key = Markup.run { graph.ownKey("logo", this@logo) }.takeIf { it >= 0 } ?: return null
        val body = graph.indexOf('{', key).takeIf { it >= 0 } ?: return null
        return Markup.run { graph.ownString("contentUrl", body + 1) }?.let(Markup::decodeEntities)
    }

    /**
     * The posts the graph describes, keyed by normalised id. Their text is the
     * untruncated copy and their time is exact, which is what the cards get
     * wrong, so these two fields are folded into the card afterwards.
     */
    private fun String.postsFromGraph(): Map<String, Post> =
        objectsOfType("DiscussionForumPosting").mapNotNull { at ->
            val url = ownString("mainEntityOfPage", at) ?: ownString("url", at) ?: return@mapNotNull null
            val permalink = Markup.decodeEntities(url).substringBefore('?')
            val id = PostId.normalize(permalink)
            if (id == permalink) return@mapNotNull null
            val published = ownString("datePublished", at)?.let(Timestamps::parseIso) ?: return@mapNotNull null
            id to Post(
                id = id,
                authorHandle = "",
                authorName = ownString("name", at).orEmpty(),
                text = ownString("text", at).orEmpty(),
                publishedAtMillis = published,
                permalink = permalink
            )
        }.toMap()

    /**
     * The card corrected by the graph, the same way selector set 2 does it.
     * The card is cut at roughly 200 characters with a "…plus" button and its
     * age is rounded to the day or the week. Everything else, the media, the
     * counts and the author, exists only on the card.
     */
    private fun Post.withExact(exact: Post): Post = copy(
        text = exact.text.takeIf { it.isNotBlank() } ?: text,
        publishedAtMillis = exact.publishedAtMillis
    )

    // ---- the rendered cards ------------------------------------------------

    /**
     * Cut to the updates section before splitting. The tail is already gone,
     * but the top card and the About block sit above and carry entity images
     * of their own.
     */
    private fun String.cards(slug: String): List<Post> {
        val at = indexOf(UPDATES).takeIf { it >= 0 } ?: return emptyList()
        return substring(at).split(CARD).drop(1).mapNotNull { chunk -> chunk.parseCard(slug) }
    }

    private fun String.parseCard(slug: String): Post? {
        val urn = attributeAfter(0, "data-activity-urn", window = 400) ?: return null
        val id = urn.substringAfterLast(':').takeIf { it.isNotEmpty() } ?: return null

        // The permalink is on the overlay anchor, which sits before the article
        // and therefore before this chunk. Rebuilding it from the id gives the
        // short form, which resolves, unlike a handmade /posts/ address.
        val permalink = LinkedInLink.canonical("feed/update/urn:li:activity:$id")

        return Post(
            id = PostId.normalize(permalink).takeIf { it != permalink } ?: id,
            // The page's own slug only when the card carries no actor of its
            // own. A reposted card names the original organisation in its
            // lockup, and calling it the page being read attributes someone
            // else's post to them.
            authorHandle = actorHandle() ?: slug,
            authorName = textAfter("feed-actor-name", "</a>").orEmpty(),
            avatarUrl = attributeAfter("hue-web-entity__image", "data-delayed-url")
                ?.let(Markup::decodeEntities),
            text = commentary().orEmpty(),
            links = Markup.links(commentaryHtml().orEmpty()),
            publishedAtMillis = Timestamps.fromRelative(age()),
            permalink = permalink,
            // Every card used to be ORIGINAL. A card with the header above it
            // is the page passing on someone else's post, which is a repost
            // and not something it wrote.
            kind = if (HEADER in this) PostKind.REPOST else PostKind.ORIGINAL,
            media = media(),
            stats = PostStats(
                likes = number("data-num-reactions"),
                replies = number("data-num-comments")
            )
        )
    }

    /**
     * Who the card is by, read from its lockup rather than from the page.
     *
     * Forward from the lockup marker, because the lockup is a div and the
     * actor's link is the first one inside it. Reading backwards would find
     * the card's own overlay link, which points at the post.
     */
    private fun String.actorHandle(): String? {
        val at = indexOf(LOCKUP).takeIf { it >= 0 } ?: return null
        val href = attributeAfter(at, "href", window = 600) ?: return null
        return Markup.handleIn(href)
    }

    /**
     * The visible text of the post. Bounded to `</p>`: the commentary is one
     * paragraph, and reading to the first closing div would swallow the media
     * list and the counters underneath it.
     */
    private fun String.commentary(): String? = textAfter(COMMENTARY, "</p>")

    private fun String.commentaryHtml(): String? {
        val at = indexOf(COMMENTARY).takeIf { it >= 0 } ?: return null
        val open = indexOf('>', at).takeIf { it >= 0 } ?: return null
        val close = indexOf("</p>", open).takeIf { it > open } ?: return null
        return substring(open + 1, close)
    }

    /**
     * The rounded age in the card's own `<time>`. Localised, "1 j." in French
     * and "1d" in English, and [Timestamps.fromRelative] only reads the English
     * forms. That is deliberate rather than an oversight: the app asks for
     * English, and every post on this page carries an exact time in the graph
     * anyway, so this is a fallback for a fallback.
     */
    private fun String.age(): String? = textAfter("<time", "</time>")

    private fun String.number(attribute: String): Int? =
        attributeAfter(0, attribute, window = 12_000)?.let(DIGITS::find)?.value?.toIntOrNull()

    // ---- media -------------------------------------------------------------

    /**
     * One shape per card. A video is a `<video>` with its sources inline, which
     * is the only place the address appears: unlike a post page, this card's
     * graph record is a DiscussionForumPosting and carries no contentUrl at
     * all. A document is an iframe with the whole viewer's configuration in one
     * escaped JSON attribute, of which only the cover is usable without
     * signing in. Anything else is the image grid.
     */
    private fun String.media(): List<MediaItem> =
        NativeVideo.read(this)?.let(::listOf) ?: document()?.let(::listOf) ?: images()

    private fun String.document(): MediaItem? {
        val at = indexOf(DOCUMENT).takeIf { it >= 0 } ?: return null
        val config = attributeAfter(at, "data-native-document-config", window = 2_000) ?: return null
        val decoded = Markup.decodeEntities(config)
        val cover = decoded.indexOf("\"src\":").takeIf { it >= 0 }
            ?.let { Markup.run { decoded.jsonStringAt(it + "\"src\":".length + 1) } }
            ?: return null
        val clean = Markup.decodeEntities(cover)
        return MediaItem(previewUrl = clean, downloadUrl = clean, type = MediaType.PHOTO)
    }

    /**
     * The grid. It shows at most five thumbnails and hides the rest behind a
     * "+7" badge, and unlike a post page there is no graph list to fall back
     * on, so what is shown is all there is. The badge is not read as a picture.
     */
    private fun String.images(): List<MediaItem> {
        val at = indexOf(IMAGES).takeIf { it >= 0 } ?: return emptyList()
        val list = substring(at, indexOf("</ul>", at).takeIf { it > at } ?: length)
        return Regex("""data-delayed-url="([^"]+)"""").findAll(list)
            .map { Markup.decodeEntities(it.groupValues[1]) }
            .filter { "media.licdn.com" in it }
            .distinct()
            .map { MediaItem(previewUrl = it, downloadUrl = it, type = MediaType.PHOTO) }
            .toList()
    }

    // ---- the top card ------------------------------------------------------

    /**
     * "Nantes, Pays de la Loire · 48 869 abonnés" in one line. The separator is
     * drawn by CSS, not written in the text, so the two halves are separated by
     * the follower count and not by a character that can be split on.
     */
    private fun String.location(): String? =
        FOLLOWERS.find(this)?.let { substring(0, it.range.first) }
            ?.trim()?.trimEnd(',', '·', '•')?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: takeIf { it.isNotBlank() && FOLLOWERS.find(it) == null }?.trim()

    private fun String.followers(): Long? {
        val line = textAfter("top-card-layout__first-subline", "</h3>") ?: return null
        val digits = FOLLOWERS.find(line)?.groupValues?.get(1) ?: return null
        return digits.filter(Char::isDigit).toLongOrNull()
    }

    /**
     * The continuation the page hands out for its own list, inside an HTML
     * comment in a hidden `<code>`. Kept whole, path and query, because it is
     * an app route and not a public address.
     *
     * Not wired to anything yet. It is kept because it is the one thing an
     * organisation page has that a profile does not, and because reading it
     * costs nothing: the page is already in memory. Following it needs a
     * capture of what that route answers, which is an HTML fragment and not
     * a page this parser could read as it stands.
     */
    @Suppress("unused")
    private fun String.nextCursor(): String? {
        val at = indexOf("id=\"feedUpdatesBaseUrl\"").takeIf { it >= 0 } ?: return null
        val open = indexOf("<!--", at).takeIf { it >= 0 } ?: return null
        val close = indexOf("-->", open).takeIf { it > open } ?: return null
        return substring(open + 4, close).trim().trim('"').takeIf { it.isNotBlank() }
    }

    /**
     * Everything after the posts is cut, and the search starts at the posts.
     *
     * It used to start at the top of the document, and that is why a showcase
     * page parsed to zero posts while a company page parsed to ten. The sign
     * in modal is not always below the list: on the showcase capture it sits
     * in the guest upsells near the top of the body, before the header, so the
     * cut landed above everything and the parser was handed the first few
     * hundred bytes of the page.
     */
    private fun String.beforeTail(): String {
        val from = indexOf(UPDATES).takeIf { it >= 0 } ?: 0
        val cut = TAIL.mapNotNull { marker -> indexOf(marker, from).takeIf { it > 0 } }.minOrNull()
        return if (cut == null) this else substring(0, cut)
    }
}
