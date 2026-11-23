package com.linkedout.app.data.linkedin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A long card keeps its counts. The Maison des Enfants post of CHU Nantes (activity 7529428497375711232, five
 * photos, 2 000 characters of text) carries `data-num-reactions` more than 12 000 characters after its card's
 * marker, and the parser's fixed window of 12 000 lost the counts. The resource is that one card whole, in the
 * minimal page the parser needs (its pageKey, the title and the updates marker), as LinkedIn served it.
 * Test shape: ProfilePageParserTest's, a saved page run through the parser
 * (https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/data/linkedin/ProfilePageParserTest.kt);
 * the resource read as in JetBrains/intellij-community's FoundationFfmTest (getResourceAsStream(...).use { it.readBytes() }).
 */
class CompanyCardCountsTest {

    private val html: String = checkNotNull(javaClass.classLoader?.getResourceAsStream("linkedin/company_card_long.html"))
        .use { it.readBytes().toString(Charsets.UTF_8) }

    @Test
    fun `the fixture is long enough to be the cause`() {
        val card = html.substringAfter("main-feed-activity-card\n")
        assertTrue("the counts must sit past the old 12 000 window", card.indexOf("data-num-reactions") > 12_000)
        assertTrue(card.indexOf("data-num-comments") > 12_000)
    }

    @Test
    fun `a long card keeps its reactions and comments`() {
        val feed = CompanyPageParser().parse(html, "chu-nantes")!!

        val post = feed.posts.single()
        assertEquals(377, post.stats?.likes)
        assertEquals(9, post.stats?.replies)
    }
}
