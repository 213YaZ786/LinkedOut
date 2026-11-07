package com.linkedout.app.data.linkedin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The badges of a card and the count of a results page, on snippets made of the selectors that are cited:
 * `.job-posting-benefits__text` of
 * https://github.com/Averyy/fetchaller-mcp/blob/main/src/fetchaller/linkedin/parse.py#parse_search_fragment
 * and `.results-context-header__job-count` of
 * https://github.com/joaosilvalopes/linkedin-easy-apply-bot/blob/master/selectors/index.ts#jobCount
 */
class JobsBadgesAndCountTest {

    @Test
    fun `a card reads its badges in order, once each, at most four of forty characters`() {
        val card = parse_search_fragment_of(
            badges = listOf(
                "Actively Hiring",
                "Be an early applicant",
                "Actively Hiring",
                "  Easy   Apply \n",
                "A badge whose words run on past the forty characters that are kept",
                "A fifth one"
            )
        )

        assertEquals(
            listOf(
                "Actively Hiring",
                "Be an early applicant",
                "Easy Apply",
                "A badge whose words run on past the fort"
            ),
            card.badges
        )
    }

    @Test
    fun `a card without a badge has none`() {
        assertEquals(emptyList<String>(), parse_search_fragment_of(badges = emptyList()).badges)
    }

    @Test
    fun `the badges of one card never go to the next`() {
        val cards = JobsParser.parse_search_fragment(
            card("1", listOf("Actively Hiring")) + card("2", emptyList())
        )

        assertEquals(listOf("Actively Hiring"), cards[0].badges)
        assertEquals(emptyList<String>(), cards[1].badges)
    }

    @Test
    fun `the count is the text LinkedIn heads the page with, trimmed`() {
        val page = """<h1><span class="results-context-header__job-count">
            Plus de 2 000 offres
        </span><span class="results-context-header__context"> ingénieur</span></h1>"""

        assertEquals("Plus de 2 000 offres", JobsParser.jobCount(page))
    }

    // The wording of jobCount (selector `.results-context-header__job-count`, joaosilvalopes/linkedin-easy-apply-bot
    // https://github.com/joaosilvalopes/linkedin-easy-apply-bot/blob/master/selectors/index.ts#jobCount): the span was
    // not seen live, so a text of digits only gets the word "offers" and a text with words is kept.
    @Test
    fun `a count of digits only is worded as offers`() {
        fun page(text: String) = """<h1><span class="results-context-header__job-count">$text</span></h1>"""

        assertEquals("2,000+ offers", JobsParser.jobCount(page("2,000+")))
        assertEquals("1 234 offers", JobsParser.jobCount(page(" 1 234 ")))
        assertEquals("7.5 offers", JobsParser.jobCount(page("7.5")))
        assertEquals("Over 2,000 offers", JobsParser.jobCount(page("Over 2,000 offers")))
    }

    @Test
    fun `a page with no count and a page with no card give nothing`() {
        assertNull(JobsParser.jobCount("<html><body>No header here</body></html>"))
        assertEquals(emptyList<Any>(), JobsParser.parse_search_fragment("<html><body>No card</body></html>"))
        assertEquals(emptyList<Any>(), JobsParser.parse_search_fragment(""))
    }

    private fun parse_search_fragment_of(badges: List<String>) =
        JobsParser.parse_search_fragment(card("42", badges)).single()

    private fun card(id: String, badges: List<String>): String = """
        <li><div class="base-card base-search-card job-search-card" data-entity-urn="urn:li:jobPosting:$id">
        <a class="base-card__full-link" href="https://www.linkedin.com/jobs/view/title-at-company-$id?position=1"></a>
        <div class="base-search-card__info"><h3 class="base-search-card__title">Title $id</h3>
        <h4 class="base-search-card__subtitle">Company</h4>
        <div class="base-search-card__metadata"><span class="job-search-card__location">Lyon</span></div>
        <div class="job-posting-benefits">${badges.joinToString("") { """<span class="job-posting-benefits__text">$it</span>""" }}</div>
        </div></div></li>
    """.trimIndent()
}
