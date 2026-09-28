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

    fun cards(html: String): List<JobCard> {
        val found = LinkedHashMap<String, JobCard>()
        var from = 0
        while (true) {
            val at = html.indexOf(CARD_URN, from)
            if (at < 0) break
            val idStart = at + CARD_URN.length
            val idEnd = html.indexOf('"', idStart)
            if (idEnd < 0) break
            from = idEnd
            val id = html.substring(idStart, idEnd).takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: continue
            // One card, up to the next one, so a field can never be read from
            // the card after it.
            val next = html.indexOf(CARD_URN, idEnd).takeIf { it > 0 } ?: html.length
            val card = html.substring(at, next)
            val title = card.textAfter("base-search-card__title", "</h3>")?.takeIf { it.isNotBlank() } ?: continue
            found.putIfAbsent(
                id,
                JobCard(
                    id = id,
                    title = title,
                    company = card.textAfter("base-search-card__subtitle", "</h4>").orEmpty(),
                    location = card.textAfter("job-search-card__location", "</span>").orEmpty(),
                    postedDate = card.attributeAfter("job-search-card__listdate", "datetime", window = 200),
                    logoUrl = card.attributeAfter("artdeco-entity-image", "data-delayed-url", window = 400)
                        ?.let(Markup::decodeEntities),
                    url = card.attributeAfter("base-card__full-link", "href", window = 400)
                        ?.let(Markup::decodeEntities)
                        ?.substringBefore('?')
                        ?: jobUrl(id)
                )
            )
        }
        return found.values.toList()
    }

    fun detail(id: String, html: String): JobDetail? {
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
        return JobDetail(
            id = id,
            title = title,
            company = flavors.getOrNull(0).orEmpty(),
            location = flavors.getOrNull(1).orEmpty(),
            postedAgo = html.textAfter("posted-time-ago__text", "</span>")?.takeIf { it.isNotBlank() },
            description = description,
            criteria = criteria,
            url = jobUrl(id)
        )
    }

    fun jobUrl(id: String): String = "https://www.linkedin.com/jobs/view/$id"

    /**
     * Line breaks the markup expresses with tags. Stripping tags alone joined
     * two paragraphs into "des Médecins ?Le Centre", so breaks, paragraph
     * ends and list items become newlines first, and an item gets a bullet.
     */
    internal fun withLineBreaks(html: String): String =
        html.replace(BREAK, "\n").replace(ITEM, "\n• ")

    private val BREAK = Regex("""(?i)<br\s*/?>|</p>|</li>|</ul>|</ol>""")
    private val ITEM = Regex("(?i)<li[^>]*>")
    private const val CARD_URN = "data-entity-urn=\"urn:li:jobPosting:"
    private const val DESCRIPTION = "show-more-less-html__markup"
    private val FLAVOR = Regex("""class="topcard__flavor(?:\s[^"]*)?"[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val CRITERION = Regex(
        """description__job-criteria-subheader"[^>]*>(.*?)</h3>.*?description__job-criteria-text[^>]*>(.*?)</span>""",
        RegexOption.DOT_MATCHES_ALL
    )
}
