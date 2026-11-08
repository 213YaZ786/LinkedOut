package com.linkedout.app.data.linkedin

import com.linkedout.app.core.model.AccountKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A post's author opens as what its link says: /company/ is a page, /in/ a person. The page fixture is
 * PostPageDocumentTest's, reduced to the card and its actor link
 * (https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/data/linkedin/PostPageDocumentTest.kt#page).
 */
class PostPageAuthorKindTest {

    @Test
    fun `a company link makes the author a company`() {
        val post = PostPageParser().parse(page("https://www.linkedin.com/company/acme?trk=public_post_feed-actor-name"), ID)!!.main!!

        assertEquals("acme", post.authorHandle)
        assertEquals(AccountKind.COMPANY, post.authorKind)
    }

    @Test
    fun `a person link makes the author a person`() {
        val post = PostPageParser().parse(page("https://fr.linkedin.com/in/jane?trk=public_post_feed-actor-name"), ID)!!.main!!

        assertEquals("jane", post.authorHandle)
        assertEquals(AccountKind.PERSON, post.authorKind)
    }

    @Test
    fun `a school link makes the author a school`() {
        val post = PostPageParser().parse(page("https://www.linkedin.com/school/uni?trk=public_post_feed-actor-name"), ID)!!.main!!

        assertEquals(AccountKind.SCHOOL, post.authorKind)
    }

    private companion object {
        const val ID = "7508102288889098240"

        fun page(actorHref: String) = """
            <html><head><link rel="canonical" href="https://fr.linkedin.com/posts/example_cv-activity-$ID-grq-"></head><body>
            <article class="main-feed-activity-card" data-activity-urn="urn:li:activity:$ID">
            <a href="$actorHref">Example Author</a>
            <p class="attributed-text-segment-list__content" data-test-id="main-feed-activity-card__commentary">Hello.</p>
            </article></body></html>
        """.trimIndent()
    }
}
