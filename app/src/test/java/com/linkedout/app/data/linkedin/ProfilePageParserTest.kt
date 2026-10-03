package com.linkedout.app.data.linkedin

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shape of a guest profile page from September 2026 that shows no
 * Activity section, reduced to what is read, with neutral content.
 */
class ProfilePageParserTest {

    @Test
    fun `a card cut short keeps one ellipsis, not the more button`() {
        val parser = Markup
        assertEquals("Imagerie Cardiaque et Vasculaire\u2026", parser.withoutMoreButton("Imagerie Cardiaque et Vasculaire...more"))
        assertEquals("dans le suivi des patients\u2026", parser.withoutMoreButton("dans le suivi des patients....more...more"))
        assertEquals("Lire la suite\u2026", parser.withoutMoreButton("Lire la suite \u2026plus"))
        assertEquals("Nothing more to say", parser.withoutMoreButton("Nothing more to say"))
    }

    @Test
    fun `a profile without an Activity section still gives its own posts from the graph`() {
        val feed = ProfilePageParser().parse(PAGE, "someone-a1b2")!!
        assertEquals("Some One", feed.displayName)
        assertEquals(listOf("7507123671249879040", "7496640558342324224"), feed.posts.map { it.id })
        val newest = feed.posts[0]
        assertEquals("A delivery on Saturday", newest.text)
        assertEquals("someone-a1b2", newest.authorHandle)
        assertEquals(162, newest.stats?.likes)
        assertEquals("https://fr.linkedin.com/posts/someone-a1b2_delivery-activity-7507123671249879040-HLW2", newest.permalink)
    }

    private companion object {
        val PAGE = """
            <html><head><script type="application/ld+json">{"@context":"http://schema.org","@graph":[
            {"@type":"DiscussionForumPosting","datePublished":"2026-09-19T17:09:18.838Z","interactionStatistic":[{"@type":"InteractionCounter","interactionType":"http://schema.org/LikeAction","userInteractionCount":162}],"mainEntityOfPage":"https://fr.linkedin.com/posts/someone-a1b2_delivery-activity-7507123671249879040-HLW2","text":"A delivery on Saturday","url":"https://fr.linkedin.com/posts/someone-a1b2_delivery-activity-7507123671249879040-HLW2"},
            {"@type":"DiscussionForumPosting","datePublished":"2026-08-21T18:53:09.908Z","interactionStatistic":[{"@type":"InteractionCounter","interactionType":"http://schema.org/LikeAction","userInteractionCount":221}],"mainEntityOfPage":"https://fr.linkedin.com/posts/someone-a1b2_extension-activity-7496640558342324224-KdqW","text":"An extension of the platform","url":"https://fr.linkedin.com/posts/someone-a1b2_extension-activity-7496640558342324224-KdqW"},
            {"@type":"Person","name":"Some One","address":{"@type":"PostalAddress","addressLocality":"Paris"}}
            ]}</script></head><body class="public_profile_v3"></body></html>
        """.trimIndent()
    }
}
