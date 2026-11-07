package com.linkedout.app.data.linkedin

import com.linkedout.app.core.model.JobCard
import com.linkedout.app.core.model.JobDetail
import com.linkedout.app.data.linkedin.Markup.attributeAfter
import com.linkedout.app.data.linkedin.Markup.textAfter

/**
 * Reads LinkedIn's public job search, which a logged out visitor may use.
 *
 * Two documents, both HTML fragments served to the guest jobs pages:
 * the search answers a list of `<li>` cards, and a posting answers the top
 * card, the criteria and the description. Pure, so it is tested on fixtures
 * of the real markup (September 2026).
 */
internal object JobsParser {

    /**
     * Job cards from a search page or fragment, in the order LinkedIn returned them, each once.
     * https://github.com/Averyy/fetchaller-mcp/blob/main/src/fetchaller/linkedin/parse.py#parse_search_fragment
     * Badges: the texts of `.job-posting-benefits__text` ("Actively Hiring"), at most four of forty characters.
     * not ported: company_url (the card opens the offer, not the company)
     * not ported: posted_label (the age is written by the app, in the phone's date format)
     * not ported: BeautifulSoup and lxml (the markup is scanned by string, as every parser of the app)
     */
    fun parse_search_fragment(html: String): List<JobCard> {
        if (html.isBlank()) return listOf()
        val cards = LinkedHashMap<String, JobCard>()
        var from = 0
        while (true) {
            val at = html.indexOf(CARD_URN, from)
            if (at < 0) break
            from = at + CARD_URN.length
            // One card, up to the next one, so a field can never be read from
            // the card after it.
            val next = html.indexOf(CARD_URN, from).takeIf { it > 0 } ?: html.length
            val container = html.substring(at, next)
            val href = container.attributeAfter("base-card__full-link", "href", window = 400)
                ?.let(Markup::decodeEntities).orEmpty()
            val job_id = _job_id_from(container, href)
            val title = _text(container, "base-search-card__title", "</h3>")
            if (job_id.isEmpty() || title.isEmpty()) continue

            val badges = mutableListOf<String>()
            var badge = container.indexOf(BADGE)
            while (badge >= 0) {
                val label = _text(container, badge, "</span>", _MAX_BADGE_CHARS)
                if (label.isNotEmpty() && label !in badges) badges.add(label)
                if (badges.size >= _MAX_BADGES) break
                badge = container.indexOf(BADGE, badge + BADGE.length)
            }

            cards.putIfAbsent(
                job_id,
                JobCard(
                    badges = badges,
                    id = job_id,
                    title = title,
                    company = _text(container, "base-search-card__subtitle", "</h4>"),
                    location = _text(container, "job-search-card__location", "</span>"),
                    postedDate = container.attributeAfter("job-search-card__listdate", "datetime", window = 200),
                    logoUrl = container.attributeAfter("artdeco-entity-image", "data-delayed-url", window = 400)
                        ?.let(Markup::decodeEntities),
                    url = strip_tracking(href).ifEmpty { jobUrl(job_id) }
                )
            )
        }
        return cards.values.toList()
    }

    /** The text of the element whose opening tag holds [marker], whitespace collapsed, at most [limit] characters. */
    private fun _text(container: String, marker: String, closeTag: String, limit: Int = _MAX_FIELD_CHARS): String =
        _text(container, container.indexOf(marker), closeTag, limit)

    private fun _text(container: String, markerAt: Int, closeTag: String, limit: Int = _MAX_FIELD_CHARS): String =
        container.textAfter(markerAt, closeTag)?.split(WHITESPACE)?.filter { it.isNotEmpty() }?.joinToString(" ")
            ?.take(limit).orEmpty()

    /**
     * The numeric id of an offer: from the card's `data-entity-urn`, else from its link.
     * https://github.com/Averyy/fetchaller-mcp/blob/main/src/fetchaller/linkedin/parse.py#_job_id_from
     */
    private fun _job_id_from(container: String, link_href: String): String =
        _JOB_URN_RE.find(container)?.groupValues?.get(1) ?: _JOB_PATH_ID_RE.find(link_href)?.groupValues?.get(1).orEmpty()

    /**
     * An offer's link without LinkedIn's per-response tracking query, or "" when it is not a LinkedIn address.
     * https://github.com/Averyy/fetchaller-mcp/blob/main/src/fetchaller/linkedin/parse.py#strip_tracking
     */
    private fun strip_tracking(url: String): String {
        if (url.isEmpty()) return ""
        val parts = runCatching { java.net.URI(url) }.getOrNull() ?: return ""
        if (parts.scheme != "http" && parts.scheme != "https") return ""
        // These are rendered as links, and every one we emit comes from a LinkedIn card. Anything else is either markup
        // we misread or content someone placed to be followed.
        val host = parts.host?.trimEnd('.')?.lowercase().orEmpty()
        if (!(host == "linkedin.com" || host.endsWith(".linkedin.com"))) return ""
        val cleaned = "${parts.scheme}://${parts.rawAuthority}${parts.rawPath.orEmpty()}"
        if (cleaned.any { it.code < 32 || it.code == 127 }) return ""
        return cleaned
    }

    /**
     * The count LinkedIn heads the first page of results with, as it words it ("Over 2,000 offers"), or null.
     * https://github.com/joaosilvalopes/linkedin-easy-apply-bot/blob/master/selectors/index.ts#jobCount
     * (`.results-context-header__job-count`)
     */
    // The span was not seen live: LinkedIn's guest page may give only the number ("2,000+"), so a text with no letter
    // is worded here ("2,000+ offers") and a text with words is kept as given.
    fun jobCount(html: String): String? = _text(html, JOB_COUNT, "</span>").takeIf { it.isNotEmpty() }
        ?.let { text -> if (text.any(Char::isLetter)) text else "$text offers" }

    /**
     * Public detail for one posting, or null if the fragment carries none.
     * https://github.com/Averyy/fetchaller-mcp/blob/main/src/fetchaller/linkedin/parse.py#parse_job_detail
     * Added to what the app read already: applicants (`.num-applicants__caption`, at most 60 characters) and company_url
     * (the href of `a.topcard__org-name-link`, through strip_tracking, "" when the offer has no company page).
     * not ported: the company's fallback to the first `span.topcard__flavor` without the bullet class (the flavor list
     * below already gives the company when no link holds it), the id found in the title link (the id is given), the
     * criteria cap of 12 and the description cap of 20,000 characters (ResponseLimit bounds the page), the markdown
     * escaping (the texts are drawn as text, never as markdown), BeautifulSoup and lxml (string scans)
     */
    fun parse_job_detail(id: String, html: String): JobDetail? {
        val title = html.textAfter("topcard__title", "</h2>")?.takeIf { it.isNotBlank() } ?: return null
        val flavors = FLAVOR.findAll(html).map { Markup.plainText(it.groupValues[1]) }.filter { it.isNotBlank() }.toList()
        val description = html.indexOf(DESCRIPTION).takeIf { it >= 0 }
            ?.let { at -> html.indexOf('>', at).takeIf { it > 0 }?.let { open -> open to html.indexOf("</div>", open) } }
            ?.takeIf { (_, close) -> close > 0 }
            ?.let { (open, close) -> Markup.plainText(withLineBreaks(html.substring(open + 1, close))) }
            .orEmpty()
        val criteria = CRITERION.findAll(html).map {
            Markup.plainText(it.groupValues[1]) to Markup.plainText(it.groupValues[2])
        }.filter { it.first.isNotBlank() && it.second.isNotBlank() }.toList()
        // company_link = soup.select_one("a.topcard__org-name-link"): the tag that holds the class, read for its href.
        val company_link = html.indexOf(COMPANY_LINK).takeIf { it >= 0 }?.let { at ->
            html.substring(html.lastIndexOf('<', at).coerceAtLeast(0), html.indexOf('>', at).takeIf { it > 0 } ?: html.length)
        }
        val company_url = company_link
            ?.let { it.attributeAfter(0, "href", window = it.length) }
            ?.let(Markup::decodeEntities)
            ?.let(::strip_tracking)
            .orEmpty()
        return JobDetail(
            id = id,
            title = title,
            company = flavors.getOrNull(0).orEmpty(),
            companyUrl = company_url,
            location = flavors.getOrNull(1).orEmpty(),
            postedAgo = html.textAfter("posted-time-ago__text", "</span>")?.takeIf { it.isNotBlank() },
            // The caption sits in a figcaption or a span: read to the first closing tag.
            applicants = _text(html, APPLICANTS, "</", _MAX_APPLICANTS_CHARS),
            description = description,
            criteria = criteria,
            url = jobUrl(id)
        )
    }

    fun jobUrl(id: String): String = "https://www.linkedin.com/jobs/view/$id"

    /**
     * Line breaks the markup expresses with tags. Stripping tags alone joined
     * two paragraphs into "des Médecins ?Le Centre", so breaks, paragraph
     * ends and list items become newlines first, and an item gets a hyphen
     * as a list is written in plain text (Reddit's markdown list, https://www.reddit.com/wiki/markdown), no bullet.
     */
    internal fun withLineBreaks(html: String): String =
        html.replace(BREAK, "\n").replace(ITEM, "\n- ")

    private val BREAK = Regex("""(?i)<br\s*/?>|</p>|</li>|</ul>|</ol>""")
    private val ITEM = Regex("(?i)<li[^>]*>")
    private const val CARD_URN = "data-entity-urn=\"urn:li:jobPosting:"
    private const val BADGE = "job-posting-benefits__text"
    private const val COMPANY_LINK = "topcard__org-name-link"
    private const val APPLICANTS = "num-applicants__caption"
    private const val _MAX_APPLICANTS_CHARS = 60
    private const val JOB_COUNT ="results-context-header__job-count"
    private const val _MAX_FIELD_CHARS = 300
    private const val _MAX_BADGES = 4
    private const val _MAX_BADGE_CHARS = 40
    private val WHITESPACE = Regex("""\s+""")
    private val _JOB_URN_RE = Regex("""urn:li:jobPosting:(\d+)""")
    private val _JOB_PATH_ID_RE = Regex("""/jobs/view/(?:[^/?#]*-)?(\d+)""")
    private const val DESCRIPTION = "show-more-less-html__markup"
    private val FLAVOR = Regex("""class="topcard__flavor(?:\s[^"]*)?"[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val CRITERION = Regex(
        """description__job-criteria-subheader"[^>]*>(.*?)</h3>.*?description__job-criteria-text[^>]*>(.*?)</span>""",
        RegexOption.DOT_MATCHES_ALL
    )
}
