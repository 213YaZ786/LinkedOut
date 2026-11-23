package com.linkedout.app.data.linkedin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A company's website, read from the About block of its guest page. The resource is the top of CHU de Nantes'
 * page and its About block whole (Website, Industry, Company size, Headquarters, Type, Founded, Specialties) as
 * LinkedIn served it in English, public organisation data.
 * Test shape: ProfilePageParserTest's, a saved page run through the parser
 * (https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/data/linkedin/ProfilePageParserTest.kt);
 * the resource read as in CompanyCardCountsTest.
 */
class CompanyWebsiteTest {

    private val html: String = checkNotNull(javaClass.classLoader?.getResourceAsStream("linkedin/company_about_block.html"))
        .use { it.readBytes().toString(Charsets.UTF_8) }

    @Test
    fun `the website is the target of the About block's link`() {
        val feed = CompanyPageParser().parse(html, "chu-nantes")!!

        assertEquals("http://www.chu-nantes.fr/", feed.website)
        assertEquals("Hospitals and Health Care", feed.currentCompany)
    }

    @Test
    fun `a page whose About block has no Website row gives none`() {
        val without = html.replace("about-us__website", "about-us__other")
            .replace("href=\"https://www.linkedin.com/redir/redirect", "data-x=\"https://www.linkedin.com/redir/redirect")

        assertNull(CompanyPageParser().parse(without, "chu-nantes")!!.website)
    }

    @Test
    fun `LinkedIn's redirect gives the address it carries`() {
        val parser = CompanyPageParser()

        assertEquals(
            "https://example.org/x",
            parser.sanitize("https://www.linkedin.com/redir/redirect?url=https%3A%2F%2Fexample.org%2Fx")
        )
        assertEquals(
            "http://www.chu-nantes.fr/",
            parser.sanitize("https://www.linkedin.com/redir/redirect?url=http%3A%2F%2Fwww%2Echu-nantes%2Efr%2F&urlhash=FM56&trk=about_website")
        )
    }

    @Test
    fun `a target that is not a web address gives none`() {
        val parser = CompanyPageParser()

        assertNull(parser.sanitize("https://www.linkedin.com/redir/redirect?url=javascript%3Aalert%281%29"))
        assertNull(parser.sanitize("https://www.linkedin.com/redir/redirect?url=data%3Atext%2Fhtml%2Cx"))
        assertNull(parser.sanitize("https://www.linkedin.com/redir/redirect?url=https%3A%2F%2Fwww.linkedin.com%2Fredir%2Fredirect%3Furl%3Dhttps%253A%252F%252Fa.org"))
        assertNull(parser.sanitize("https://www.linkedin.com/redir/redirect?urlhash=FM56"))
        assertNull(parser.sanitize("https://www.linkedin.com/redir/redirect?url=https%3A%2F%2Fexample.org%2Fa%20b"))
        assertNull(parser.sanitize("javascript:alert(1)"))
    }
}
