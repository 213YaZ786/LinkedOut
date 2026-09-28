package com.linkedout.app.data.linkedin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shapes of LinkedIn's guest job search and job posting fragments of
 * September 2026, reduced to the classes the parser reads, with neutral text.
 */
class JobsParserTest {

    @Test
    fun `reads every card of a search page`() {
        val cards = JobsParser.cards(SEARCH)

        assertEquals(listOf("4455683589", "4311721135"), cards.map { it.id })
        val first = cards.first()
        assertEquals("Medical physicist (M/F)", first.title)
        assertEquals("Example Hospital", first.company)
        assertEquals("Lyon", first.location)
        assertEquals("2026-07-26", first.postedDate)
        assertEquals("https://media.licdn.com/dms/image/v2/X/company-logo_100_100/Y?e=2147483647&v=beta&t=z", first.logoUrl)
        assertEquals("https://fr.linkedin.com/jobs/view/medical-physicist-at-example-hospital-4455683589", first.url)
    }

    @Test
    fun `a card without a date or a logo still reads`() {
        val second = JobsParser.cards(SEARCH)[1]

        assertNull(second.postedDate)
        assertNull(second.logoUrl)
        assertEquals("Paris", second.location)
    }

    @Test
    fun `reads a posting with its criteria and paragraphs`() {
        val detail = JobsParser.detail("4455683589", POSTING)!!

        assertEquals("Medical physicist (M/F)", detail.title)
        assertEquals("Example Hospital", detail.company)
        assertEquals("Lyon, Auvergne-Rhone-Alpes, France", detail.location)
        assertEquals("2 months ago", detail.postedAgo)
        assertEquals(listOf("Seniority level" to "Entry level", "Employment type" to "Full-time"), detail.criteria)
        // Two breaks are a paragraph, kept as one blank line. List items get a bullet.
        assertTrue(detail.description.startsWith("You hold a master's degree?\n\nJoin a modern department."))
        assertTrue(detail.description.contains("• Dosimetry"))
        assertEquals("https://www.linkedin.com/jobs/view/4455683589", detail.url)
    }

    @Test
    fun `a page without a title is not a posting`() {
        assertNull(JobsParser.detail("1", "<html><body>Sign in to see this job</body></html>"))
    }

    private companion object {
        val SEARCH = """
            <li><div class="base-card relative base-search-card job-search-card" data-entity-urn="urn:li:jobPosting:4455683589" data-row="1">
            <a class="base-card__full-link absolute" href="https://fr.linkedin.com/jobs/view/medical-physicist-at-example-hospital-4455683589?position=1&amp;pageNum=0">
            <span class="sr-only">Medical physicist (M/F)</span></a>
            <div class="search-entity-media"><img class="artdeco-entity-image artdeco-entity-image--square-4" data-delayed-url="https://media.licdn.com/dms/image/v2/X/company-logo_100_100/Y?e=2147483647&amp;v=beta&amp;t=z" alt></div>
            <div class="base-search-card__info"><h3 class="base-search-card__title">
                Medical physicist (M/F)
            </h3><h4 class="base-search-card__subtitle"><a class="hidden-nested-link" href="https://fr.linkedin.com/company/example">
                Example Hospital
            </a></h4><div class="base-search-card__metadata"><span class="job-search-card__location">
                Lyon
            </span><time class="job-search-card__listdate" datetime="2026-07-26">2 months ago</time></div></div></div></li>
            <li><div class="base-card base-search-card job-search-card" data-entity-urn="urn:li:jobPosting:4311721135" data-row="2">
            <a class="base-card__full-link" href="https://www.linkedin.com/jobs/view/other-4311721135?position=2">x</a>
            <div class="base-search-card__info"><h3 class="base-search-card__title">Radiotherapy technician</h3>
            <h4 class="base-search-card__subtitle"><a class="hidden-nested-link" href="#">Other Centre</a></h4>
            <div class="base-search-card__metadata"><span class="job-search-card__location">Paris</span></div></div></div></li>
        """.trimIndent()

        val POSTING = """
            <section class="top-card-layout"><h2 class="top-card-layout__title font-sans topcard__title">Medical physicist (M/F)</h2>
            <h4 class="top-card-layout__second-subline"><div class="topcard__flavor-row">
            <span class="topcard__flavor"><a class="topcard__org-name-link" href="#">
              Example Hospital
            </a></span>
            <span class="topcard__flavor topcard__flavor--bullet">
              Lyon, Auvergne-Rhone-Alpes, France
            </span></div>
            <div class="topcard__flavor-row"><span class="posted-time-ago__text topcard__flavor--metadata">
              2 months ago
            </span></div></h4></section>
            <div class="description__text"><div class="show-more-less-html__markup relative">
            You hold a master's degree?<br><br>Join a modern department.<ul><li>Dosimetry</li><li>Quality control</li></ul>
            </div></div>
            <ul class="description__job-criteria-list">
            <li class="description__job-criteria-item"><h3 class="description__job-criteria-subheader">Seniority level</h3>
            <span class="description__job-criteria-text description__job-criteria-text--criteria">Entry level</span></li>
            <li class="description__job-criteria-item"><h3 class="description__job-criteria-subheader">Employment type</h3>
            <span class="description__job-criteria-text description__job-criteria-text--criteria">Full-time</span></li>
            </ul>
        """.trimIndent()
    }
}
