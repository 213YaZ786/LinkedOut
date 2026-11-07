package com.linkedout.app.data.linkedin

import com.linkedout.app.core.link.LinkedInLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The applicants and the company link of an offer, read from small snippets of the selectors of
 * https://github.com/Averyy/fetchaller-mcp/blob/main/src/fetchaller/linkedin/parse.py#parse_job_detail
 * (`.num-applicants__caption`, `a.topcard__org-name-link`).
 */
class JobDetailParserTest {

    @Test
    fun `reads the applicants and the company link, without the tracking query`() {
        val detail = JobsParser.parse_job_detail("42", page(link = LINK, applicants = APPLICANTS))!!

        assertEquals("75 applicants", detail.applicants)
        assertEquals("https://fr.linkedin.com/company/inserm", detail.companyUrl)
        assertEquals("Inserm", detail.company)
        // The address is one the app opens in its own feed, as a company.
        assertEquals(LinkedInLink.Company("inserm", "company"), LinkedInLink.parse(detail.companyUrl))
    }

    @Test
    fun `an offer with neither has both empty`() {
        val detail = JobsParser.parse_job_detail("42", page(link = PLAIN_FLAVOR, applicants = ""))!!

        assertEquals("", detail.applicants)
        assertEquals("", detail.companyUrl)
        assertEquals("Inserm", detail.company)
        assertNull(LinkedInLink.parse(detail.companyUrl))
    }

    @Test
    fun `a company link that is not LinkedIn's is dropped, a link to nothing too`() {
        val outside = JobsParser.parse_job_detail("42", page(link = link("https://evil.example/company/inserm"), applicants = ""))!!
        val anchor = JobsParser.parse_job_detail("42", page(link = link("#"), applicants = ""))!!

        assertEquals("", outside.companyUrl)
        assertEquals("", anchor.companyUrl)
    }

    @Test
    fun `the applicants are cut at sixty characters`() {
        val detail = JobsParser.parse_job_detail("42", page(link = LINK, applicants = caption("x".repeat(200))))!!

        assertEquals(60, detail.applicants.length)
        assertTrue(detail.applicants.all { it == 'x' })
    }

    private fun page(link: String, applicants: String) = """
        <section class="top-card-layout"><h2 class="top-card-layout__title topcard__title">Engineer</h2>
        <div class="topcard__flavor-row">$link
        <span class="topcard__flavor topcard__flavor--bullet">Marseille</span></div>
        <div class="topcard__flavor-row"><span class="posted-time-ago__text topcard__flavor--metadata">5 days ago</span>
        $applicants</div></section>
    """.trimIndent()

    private fun link(href: String) = """<span class="topcard__flavor"><a class="topcard__org-name-link topcard__flavor--black-link" data-tracking-control-name="public_jobs_topcard-org-name" href="$href">
          Inserm
        </a></span>"""

    private fun caption(text: String) = """<figcaption class="num-applicants__caption">
          $text
        </figcaption>"""

    private companion object {
        val LINK = """<span class="topcard__flavor"><a class="topcard__org-name-link topcard__flavor--black-link" data-tracking-control-name="public_jobs_topcard-org-name" href="https://fr.linkedin.com/company/inserm?trk=public_jobs_topcard-org-name&amp;refId=abc">
          Inserm
        </a></span>"""
        val PLAIN_FLAVOR = """<span class="topcard__flavor">Inserm</span>"""
        val APPLICANTS = """<figcaption class="num-applicants__caption">
          75 applicants
        </figcaption>"""
    }
}
